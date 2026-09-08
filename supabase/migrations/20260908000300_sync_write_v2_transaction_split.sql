-- Migration: Transaction Split & Workspace Expense Sync (E14-B2)
-- Kapsam:
-- 1. transactions tablosuna paid_by_user_id ve participant_user_ids kolonlarının eklenmesi ve backfill edilmesi.
-- 2. transactions indekslerinin ve RLS SELECT v2 politikasının güncellenmesi.
-- 3. sync_write_v2 TRANSACTION dalının split doğrulaması ve normalizasyonu ile genişletilmesi.

begin;

-- ============================================================================
-- 1. TRANSACTIONS TABLOSU GENİŞLETMESİ VE BACKFILL
-- ============================================================================

alter table public.transactions
    add column if not exists paid_by_user_id uuid references auth.users(id) on delete set null,
    add column if not exists participant_user_ids jsonb not null default '[]'::jsonb;

-- Eski kayıtlar için backfill: ödeyen ve katılımcı işlem sahibidir
update public.transactions
   set paid_by_user_id = user_id
 where paid_by_user_id is null;

update public.transactions
   set participant_user_ids = pg_catalog.jsonb_build_array(user_id::text)
 where participant_user_ids is null or participant_user_ids = '[]'::jsonb;

create index if not exists transactions_paid_by_user_idx
    on public.transactions (paid_by_user_id);

create index if not exists transactions_workspace_date_idx
    on public.transactions (workspace_id, transaction_date desc, id);


-- ============================================================================
-- 2. TRANSACTIONS RLS SELECT V2 POLİTİKASI GÜNCELLEMESİ
-- ============================================================================

drop policy if exists transactions_select_personal_v1 on public.transactions;
drop policy if exists transactions_select_v2 on public.transactions;

create policy transactions_select_v2
    on public.transactions
    for select
    to authenticated
    using (
        (user_id = auth.uid() and workspace_id is null)
        or
        (workspace_id is not null and public.is_workspace_member(workspace_id))
    );


-- ============================================================================
-- 3. SYNC_WRITE_V2 RPC GÜNCELLEMESİ (TRANSACTION SPLIT DESTEĞİ)
-- ============================================================================

create or replace function public.sync_write_v2(
    p_operation_id text,
    p_entity_type text,
    p_operation text,
    p_base_version bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    actor_id uuid := auth.uid();
    written_row jsonb;
    current_row jsonb;
    entity_id uuid;
    v_fingerprint text;
    v_receipt_fingerprint text;
    v_receipt_record jsonb;
    v_cat_type text;
    v_tx_category_id uuid;
    v_tx_type text;
    v_tx_workspace_id uuid;
    v_tx_paid_by_user_id uuid;
    v_tx_participant_user_ids jsonb;
    v_tx_participant_count integer;
    v_tx_distinct_participant_count integer;
    v_tx_valid_member_count integer;
    v_budget_category_id uuid;
    v_budget_month text;
    v_rec_category_id uuid;
    v_rec_type text;
    v_rec_interval integer;
    v_rec_start_date date;
    v_rec_end_date date;
    v_rec_last_generated_date date;
    v_existing_last_generated date;
    v_sub_name text;
    v_sub_category_id uuid;
    v_sub_interval integer;
    v_sub_start_date date;
    v_sub_end_date date;
    v_sub_next_renewal_date date;
    v_existing_next_renewal date;

    -- Goals & Debts değişkenleri
    v_goal_name text;
    v_goal_target_amount bigint;
    v_goal_current_amount bigint;
    v_goal_currency text;
    v_goal_target_date date;
    v_goal_color_hex text;
    v_goal_icon_key text;
    v_goal record;
    v_updated_goal record;
    v_inserted_contrib record;
    v_contrib_goal_id uuid;
    v_contrib_amount bigint;
    v_contrib_currency text;
    v_contrib_direction text;
    v_contrib_occurred_on date;
    v_contrib_note text;
    v_new_goal_amount bigint;

    v_debt_title text;
    v_debt_amount bigint;
    v_debt_currency text;
    v_debt_type text;
    v_debt_due_date date;
    v_debt_status text;
    v_debt_description text;
    v_debt record;
    v_updated_debt record;
    v_inserted_payment record;
    v_payment_debt_id uuid;
    v_payment_amount bigint;
    v_payment_currency text;
    v_payment_paid_on date;
    v_total_paid bigint;
    v_new_debt_status text;

    -- Workspace değişkenleri
    v_ws_name text;
    v_ws_normalized_name text;
    v_ws_type_code text;
    v_ws_currency_code text;
    v_ws_description text;
    v_ws_record record;
    v_member_role text;

    -- Workspace Invitation & Member degiskenleri
    v_inv_workspace_id uuid;
    v_inv_role_code text;
    v_inv_token_hash text;
    v_inv_expires_at timestamptz;
    v_inv_max_uses integer;
    v_inv_record record;
    v_target_workspace_id uuid;
    v_target_user_id uuid;
    v_new_role text;
    v_member_record record;
begin
    -- 1. Kimlik doğrulama
    if actor_id is null then
        raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.';
    end if;

    -- 2. operation_id doğrulaması (32 hex)
    if p_operation_id is null or not (p_operation_id ~ '^[0-9a-f]{32}$') then
        raise exception using message = 'Geçersiz operation_id formatı.';
    end if;

    -- 3. entity_type doğrulaması
    if p_entity_type not in (
        'PROFILE', 'CATEGORY', 'TRANSACTION', 'BUDGET',
        'RECURRING_TRANSACTION', 'SUBSCRIPTION',
        'GOAL', 'GOAL_CONTRIBUTION', 'DEBT', 'DEBT_PAYMENT',
        'WORKSPACE', 'WORKSPACE_MEMBER', 'WORKSPACE_INVITATION'
    ) then
        raise exception using message = 'Desteklenmeyen entity türü.';
    end if;

    -- 4. operation doğrulaması
    if p_operation not in ('CREATE', 'UPDATE', 'DELETE') then
        raise exception using message = 'Desteklenmeyen senkronizasyon işlemi.';
    end if;

    -- 5. base_version doğrulaması
    if p_operation = 'CREATE' then
        if p_entity_type in ('GOAL_CONTRIBUTION', 'DEBT_PAYMENT') then
            if p_base_version is null or p_base_version < 1 then
                raise exception using message = 'Child CREATE işlemi için parent base_version en az 1 olmalıdır.';
            end if;
        elsif p_entity_type = 'WORKSPACE_MEMBER' then
            raise exception using message = 'WORKSPACE_MEMBER için generic outbox CREATE işlemi desteklenmez. Davetle katılım için redeem_workspace_invitation_v1 kullanınız.';
        else
            if p_base_version is not null then
                raise exception using message = 'CREATE işlemi için base_version null olmalıdır.';
            end if;
        end if;
    end if;

    if p_operation in ('UPDATE', 'DELETE') and (p_base_version is null or p_base_version < 1) then
        raise exception using message = 'UPDATE ve DELETE işlemleri için base_version en az 1 olmalıdır.';
    end if;

    -- 6. payload ve entity_id doğrulaması
    if p_payload is null or pg_catalog.jsonb_typeof(p_payload) <> 'object' then
        raise exception using message = 'Payload geçerli bir JSON nesnesi olmalıdır.';
    end if;

    if p_entity_type = 'WORKSPACE_MEMBER' then
        if (p_payload ->> 'workspace_id') is null or not ((p_payload ->> 'workspace_id') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then
            raise exception using message = 'WORKSPACE_MEMBER için workspace_id geçerli bir UUID olmalıdır.';
        end if;
        if (p_payload ->> 'user_id') is null or not ((p_payload ->> 'user_id') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then
            raise exception using message = 'WORKSPACE_MEMBER için user_id geçerli bir UUID olmalıdır.';
        end if;
        entity_id := (md5((p_payload ->> 'workspace_id') || ':' || (p_payload ->> 'user_id')))::uuid;
    else
        if (p_payload ->> 'id') is null or not ((p_payload ->> 'id') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then
            raise exception using message = 'Payload id geçerli bir UUID olmalıdır.';
        end if;
        entity_id := (p_payload ->> 'id')::uuid;
    end if;

    -- 7. Eşzamanlı aynı istekler için Transaction Advisory Lock
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(actor_id::text),
        pg_catalog.hashtext(p_operation_id)
    );

    -- 8. Canonical Request Fingerprint (SHA-256)
    v_fingerprint := pg_catalog.encode(
        extensions.digest(
            actor_id::text || '|' ||
            p_entity_type || '|' ||
            entity_id::text || '|' ||
            p_operation || '|' ||
            coalesce(p_base_version::text, 'null') || '|' ||
            p_payload::text,
            'sha256'
        ),
        'hex'
    );

    -- 9. Idempotency Receipt Kontrolü
    select r.request_fingerprint, r.response_record
      into v_receipt_fingerprint, v_receipt_record
      from public.sync_operations_receipts as r
     where r.user_id = actor_id
       and r.operation_id = p_operation_id;

    if found then
        if v_receipt_fingerprint <> v_fingerprint then
            raise exception using message = 'Idempotency ihlali: Aynı operation_id farklı payload ile tekrarlandı.';
        end if;

        -- Receipt mevcut ve parmak izi eşleşiyor: güncel entity tablosunu okumadan orijinal response ile dön
        return pg_catalog.jsonb_build_object('status', 'APPLIED', 'record', v_receipt_record);
    end if;

    -- 10. Entity Mutation Dalları
    if p_entity_type = 'PROFILE' then
        if entity_id <> actor_id or p_operation = 'DELETE' then
            raise insufficient_privilege using message = 'Profil işlemi reddedildi.';
        end if;

        if p_operation = 'CREATE' then
            insert into public.profiles (
                id, email, full_name, currency, theme, lang, active_workspace_id, created_at
            ) values (
                entity_id,
                p_payload ->> 'email',
                nullif(p_payload ->> 'full_name', ''),
                coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                coalesce(nullif(p_payload ->> 'theme', ''), 'system'),
                coalesce(nullif(p_payload ->> 'lang', ''), 'tr'),
                nullif(p_payload ->> 'active_workspace_id', '')::uuid,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(profiles.*) into written_row;
        else
            update public.profiles
               set email = p_payload ->> 'email',
                   full_name = nullif(p_payload ->> 'full_name', ''),
                   currency = p_payload ->> 'currency',
                   theme = p_payload ->> 'theme',
                   lang = p_payload ->> 'lang',
                   active_workspace_id = nullif(p_payload ->> 'active_workspace_id', '')::uuid
             where id = actor_id
               and version = p_base_version
            returning pg_catalog.to_jsonb(profiles.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(p.*) into current_row
              from public.profiles as p
             where p.id = actor_id;
        end if;

    elsif p_entity_type = 'CATEGORY' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null
               or coalesce((p_payload ->> 'is_default')::boolean, false) then
                raise insufficient_privilege using message = 'Kategori sahipliği reddedildi.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            insert into public.categories (
                id, user_id, workspace_id, name, slug, type, color, icon, is_default, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                p_payload ->> 'name',
                nullif(p_payload ->> 'slug', ''),
                p_payload ->> 'type',
                p_payload ->> 'color',
                nullif(p_payload ->> 'icon', ''),
                false,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(categories.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.categories
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and is_default = false
               and version = p_base_version
            returning pg_catalog.to_jsonb(categories.*) into written_row;
        else
            update public.categories
               set name = p_payload ->> 'name',
                    slug = nullif(p_payload ->> 'slug', ''),
                    type = p_payload ->> 'type',
                    color = p_payload ->> 'color',
                    icon = nullif(p_payload ->> 'icon', ''),
                    deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and is_default = false
               and version = p_base_version
            returning pg_catalog.to_jsonb(categories.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(c.*) into current_row
              from public.categories as c
             where c.id = entity_id
               and c.user_id = actor_id
               and c.workspace_id is null;
        end if;

    elsif p_entity_type = 'TRANSACTION' then
        v_tx_category_id := nullif(p_payload ->> 'category_id', '')::uuid;
        v_tx_type := p_payload ->> 'type';
        v_tx_workspace_id := nullif(p_payload ->> 'workspace_id', '')::uuid;

        if v_tx_category_id is null then
            raise exception using message = 'İşlem için category_id zorunludur.';
        end if;

        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id then
                raise insufficient_privilege using message = 'İşlem sahipliği reddedildi.';
            end if;

            if v_tx_workspace_id is null then
                -- Kişisel işlem: kategori doğrulaması
                select c.type into v_cat_type
                  from public.categories as c
                 where c.id = v_tx_category_id
                   and c.deleted_at is null
                   and (
                       (c.is_default = true and c.user_id is null and c.workspace_id is null)
                       or (c.is_default = false and c.user_id = actor_id and c.workspace_id is null)
                   );

                if v_cat_type is null then
                    raise exception using message = 'Geçersiz, silinmiş veya erişilemeyen kategori.';
                end if;

                if v_cat_type <> v_tx_type then
                    raise exception using message = 'İşlem türü ile kategori türü uyuşmuyor.';
                end if;

                -- Kişisel işlem için split alanları zorunlu olarak actor_id'ye normalize edilir
                v_tx_paid_by_user_id := actor_id;
                v_tx_participant_user_ids := pg_catalog.jsonb_build_array(actor_id::text);
            else
                -- Ortak alan işlemi: actor aktif üye olmalıdır
                if not public.is_workspace_member(v_tx_workspace_id) then
                    raise insufficient_privilege using message = 'Çalışma alanına işlem ekleme yetkisi reddedildi.';
                end if;

                select c.type into v_cat_type
                  from public.categories as c
                 where c.id = v_tx_category_id
                   and c.deleted_at is null
                   and (
                       (c.is_default = true and c.user_id is null and c.workspace_id is null)
                       or (c.is_default = false and c.workspace_id = v_tx_workspace_id)
                   );

                if v_cat_type is null then
                    raise exception using message = 'Geçersiz, silinmiş veya erişilemeyen kategori.';
                end if;

                if v_cat_type <> v_tx_type then
                    raise exception using message = 'İşlem türü ile kategori türü uyuşmuyor.';
                end if;

                if v_tx_type = 'income' then
                    -- Gelir işlemlerinde split normalize edilir
                    v_tx_paid_by_user_id := actor_id;
                    v_tx_participant_user_ids := pg_catalog.jsonb_build_array(actor_id::text);
                else
                    -- Ortak gider işlemi: split alanları doğrulama
                    if (p_payload -> 'participant_user_ids') is not null
                       and pg_catalog.jsonb_typeof(p_payload -> 'participant_user_ids') = 'array'
                       and pg_catalog.jsonb_array_length(p_payload -> 'participant_user_ids') > 0 then

                        v_tx_participant_user_ids := p_payload -> 'participant_user_ids';
                        v_tx_paid_by_user_id := coalesce(nullif(p_payload ->> 'paid_by_user_id', '')::uuid, actor_id);

                        -- Katılımcı sayısı ve tekrarsızlık kontrolü
                        select count(*), count(distinct elem)
                          into v_tx_participant_count, v_tx_distinct_participant_count
                          from pg_catalog.jsonb_array_elements_text(v_tx_participant_user_ids) as elem;

                        if v_tx_participant_count = 0 or v_tx_participant_count <> v_tx_distinct_participant_count then
                            raise exception using message = 'Ortak gider katılımcıları boş veya tekrarlı olamaz.';
                        end if;

                        -- Payer katılımcı listesinde olmalı
                        if not (v_tx_participant_user_ids ? v_tx_paid_by_user_id::text) then
                            raise exception using message = 'Ödeme yapan kişi katılımcı olmalıdır.';
                        end if;

                        -- Tüm katılımcılar (ve payer) ilgili workspace'in aktif üyesi olmalı
                        select count(*)
                          into v_tx_valid_member_count
                          from public.workspace_members as m
                         where m.workspace_id = v_tx_workspace_id
                           and m.deleted_at is null
                           and m.user_id in (
                               select elem::uuid
                                 from pg_catalog.jsonb_array_elements_text(v_tx_participant_user_ids) as elem
                           );

                        if v_tx_valid_member_count <> v_tx_participant_count then
                            raise exception using message = 'Katılımcılar arasında çalışma alanının aktif üyesi olmayan kullanıcılar var.';
                        end if;
                    else
                        v_tx_paid_by_user_id := coalesce(nullif(p_payload ->> 'paid_by_user_id', '')::uuid, actor_id);
                        if v_tx_paid_by_user_id <> actor_id and not exists (
                            select 1 from public.workspace_members
                             where workspace_id = v_tx_workspace_id and user_id = v_tx_paid_by_user_id and deleted_at is null
                        ) then
                            raise exception using message = 'Ödeme yapan kişi çalışma alanının aktif üyesi olmalıdır.';
                        end if;
                        v_tx_participant_user_ids := pg_catalog.jsonb_build_array(v_tx_paid_by_user_id::text);
                    end if;
                end if;
            end if;
        end if;

        if p_operation = 'CREATE' then
            insert into public.transactions (
                id, user_id, workspace_id, paid_by_user_id, participant_user_ids,
                amount_minor, currency, type, category_id,
                description, payment_method, transaction_date, receipt_path,
                installment_number, total_installments, installment_group_id, created_at
            ) values (
                entity_id,
                actor_id,
                v_tx_workspace_id,
                v_tx_paid_by_user_id,
                v_tx_participant_user_ids,
                (p_payload ->> 'amount_minor')::bigint,
                p_payload ->> 'currency',
                p_payload ->> 'type',
                (p_payload ->> 'category_id')::uuid,
                nullif(p_payload ->> 'description', ''),
                p_payload ->> 'payment_method',
                (p_payload ->> 'transaction_date')::date,
                nullif(p_payload ->> 'receipt_path', ''),
                nullif(p_payload ->> 'installment_number', '')::integer,
                nullif(p_payload ->> 'total_installments', '')::integer,
                nullif(p_payload ->> 'installment_group_id', ''),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(transactions.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.transactions
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and version = p_base_version
            returning pg_catalog.to_jsonb(transactions.*) into written_row;
        else
            update public.transactions
               set paid_by_user_id = v_tx_paid_by_user_id,
                   participant_user_ids = v_tx_participant_user_ids,
                   amount_minor = (p_payload ->> 'amount_minor')::bigint,
                   currency = p_payload ->> 'currency',
                   type = p_payload ->> 'type',
                   category_id = (p_payload ->> 'category_id')::uuid,
                   description = nullif(p_payload ->> 'description', ''),
                   payment_method = p_payload ->> 'payment_method',
                   transaction_date = (p_payload ->> 'transaction_date')::date,
                   receipt_path = nullif(p_payload ->> 'receipt_path', ''),
                   installment_number = nullif(p_payload ->> 'installment_number', '')::integer,
                   total_installments = nullif(p_payload ->> 'total_installments', '')::integer,
                   installment_group_id = nullif(p_payload ->> 'installment_group_id', ''),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and version = p_base_version
            returning pg_catalog.to_jsonb(transactions.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(t.*) into current_row
              from public.transactions as t
             where t.id = entity_id
               and t.user_id = actor_id;
        end if;

    elsif p_entity_type = 'BUDGET' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Bütçe sahipliği reddedildi.';
            end if;

            v_budget_category_id := nullif(p_payload ->> 'category_id', '')::uuid;
            v_budget_month := p_payload ->> 'month';

            if v_budget_category_id is null then
                raise exception using message = 'Bütçe için category_id zorunludur.';
            end if;

            if v_budget_month is null or not (v_budget_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$') then
                raise exception using message = 'Geçersiz bütçe ayı formatı (YYYY-MM).';
            end if;

            if (p_payload ->> 'limit_minor') is null or (p_payload ->> 'limit_minor')::bigint <= 0 then
                raise exception using message = 'Bütçe limiti pozitif bir tamsayı olmalıdır.';
            end if;

            select c.type into v_cat_type
              from public.categories as c
             where c.id = v_budget_category_id
               and c.deleted_at is null
               and (
                   (c.is_default = true and c.user_id is null and c.workspace_id is null)
                   or (c.is_default = false and c.user_id = actor_id and c.workspace_id is null)
               );

            if v_cat_type is null then
                raise exception using message = 'Geçersiz, silinmiş veya erişilemeyen kategori.';
            end if;

            -- Invariant: Bütçe yalnız gider ('expense') kategorileri için tanımlanabilir
            if v_cat_type <> 'expense' then
                raise exception using message = 'Bütçe yalnız gider kategorileri için tanımlanabilir.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            insert into public.budgets (
                id, user_id, workspace_id, category_id, month,
                limit_minor, currency, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_budget_category_id,
                v_budget_month,
                (p_payload ->> 'limit_minor')::bigint,
                coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(budgets.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.budgets
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(budgets.*) into written_row;
        else
            update public.budgets
               set category_id = v_budget_category_id,
                   month = v_budget_month,
                   limit_minor = (p_payload ->> 'limit_minor')::bigint,
                   currency = coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(budgets.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(b.*) into current_row
              from public.budgets as b
             where b.id = entity_id
               and b.user_id = actor_id
               and b.workspace_id is null;
        end if;

    elsif p_entity_type = 'RECURRING_TRANSACTION' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Tekrarlayan işlem sahipliği reddedildi.';
            end if;

            v_rec_category_id := nullif(p_payload ->> 'category_id', '')::uuid;
            v_rec_type := p_payload ->> 'type';

            if v_rec_category_id is null then
                raise exception using message = 'Tekrarlayan işlem için category_id zorunludur.';
            end if;

            select c.type into v_cat_type
              from public.categories as c
             where c.id = v_rec_category_id
               and c.deleted_at is null
               and (
                   (c.is_default = true and c.user_id is null and c.workspace_id is null)
                   or (c.is_default = false and c.user_id = actor_id and c.workspace_id is null)
               );

            if v_cat_type is null then
                raise exception using message = 'Geçersiz, silinmiş veya erişilemeyen kategori.';
            end if;

            if v_cat_type <> v_rec_type then
                raise exception using message = 'Tekrarlayan işlem türü ile kategori türü uyuşmuyor.';
            end if;

            v_rec_interval := coalesce(nullif(p_payload ->> 'interval', '')::integer, 1);
            v_rec_start_date := (p_payload ->> 'start_date')::date;
            v_rec_end_date := (p_payload ->> 'end_date')::date;
            v_rec_last_generated_date := (p_payload ->> 'last_generated_date')::date;

            if v_rec_interval <= 0 then
                raise exception using message = 'Tekrar aralığı pozitif bir tamsayı olmalıdır.';
            end if;

            if v_rec_start_date is null then
                raise exception using message = 'Tekrar başlangıç tarihi zorunludur.';
            end if;

            if v_rec_end_date is not null and v_rec_end_date < v_rec_start_date then
                raise exception using message = 'Tekrar bitiş tarihi başlangıç tarihinden önce olamaz.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            if v_rec_last_generated_date is not null then
                raise exception using message = 'CREATE işleminde last_generated_date null olmalıdır.';
            end if;

            insert into public.recurring_transactions (
                id, user_id, workspace_id, amount_minor, currency, type, category_id,
                description, payment_method, frequency, interval, start_date, end_date,
                last_generated_date, is_active, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                (p_payload ->> 'amount_minor')::bigint,
                coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                v_rec_type,
                v_rec_category_id,
                nullif(p_payload ->> 'description', ''),
                p_payload ->> 'payment_method',
                p_payload ->> 'frequency',
                v_rec_interval,
                v_rec_start_date,
                v_rec_end_date,
                null,
                coalesce((p_payload ->> 'is_active')::boolean, true),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(recurring_transactions.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.recurring_transactions
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(recurring_transactions.*) into written_row;
        else -- UPDATE
            select r.last_generated_date into v_existing_last_generated
              from public.recurring_transactions as r
             where r.id = entity_id
               and r.user_id = actor_id
               and r.workspace_id is null
               and r.deleted_at is null;

            if found then
                if v_existing_last_generated is not null then
                    if v_rec_last_generated_date is null or v_rec_last_generated_date < v_existing_last_generated then
                        raise exception using message = 'last_generated_date geriye taşınamaz veya silinemez.';
                    end if;
                    if v_rec_start_date > v_existing_last_generated then
                        raise exception using message = 'start_date son üretilen tarihin sonrasına taşınamaz.';
                    end if;
                    if v_rec_end_date is not null and v_rec_end_date < v_existing_last_generated then
                        raise exception using message = 'end_date son üretilen tarihin öncesine çekilemez.';
                    end if;
                else
                    if v_rec_last_generated_date is not null and v_rec_last_generated_date < v_rec_start_date then
                        raise exception using message = 'last_generated_date start_date öncesinde olamaz.';
                    end if;
                end if;
            end if;

            update public.recurring_transactions
               set amount_minor = (p_payload ->> 'amount_minor')::bigint,
                   currency = coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                   type = v_rec_type,
                   category_id = v_rec_category_id,
                   description = nullif(p_payload ->> 'description', ''),
                   payment_method = p_payload ->> 'payment_method',
                   frequency = p_payload ->> 'frequency',
                   interval = v_rec_interval,
                   start_date = v_rec_start_date,
                   end_date = v_rec_end_date,
                   last_generated_date = v_rec_last_generated_date,
                   is_active = coalesce((p_payload ->> 'is_active')::boolean, is_active),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(recurring_transactions.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(r.*) into current_row
              from public.recurring_transactions as r
             where r.id = entity_id
               and r.user_id = actor_id
               and r.workspace_id is null;
        end if;

    elsif p_entity_type = 'SUBSCRIPTION' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Abonelik sahipliği reddedildi.';
            end if;

            v_sub_name := nullif(trim(p_payload ->> 'name'), '');
            if v_sub_name is null or length(v_sub_name) > 500 then
                raise exception using message = 'Abonelik adı geçerli olmalıdır (1-500 karakter).';
            end if;

            if (p_payload ->> 'amount_minor') is null or (p_payload ->> 'amount_minor')::bigint <= 0 then
                raise exception using message = 'Abonelik tutarı sıfırdan büyük olmalıdır.';
            end if;

            v_sub_category_id := nullif(p_payload ->> 'category_id', '')::uuid;
            if v_sub_category_id is not null then
                select c.type into v_cat_type
                  from public.categories as c
                 where c.id = v_sub_category_id
                   and c.deleted_at is null
                   and (
                       (c.is_default = true and c.user_id is null and c.workspace_id is null)
                       or (c.is_default = false and c.user_id = actor_id and c.workspace_id is null)
                   );

                if v_cat_type is null then
                    raise exception using message = 'Geçersiz, silinmiş veya erişilemeyen kategori.';
                end if;

                if v_cat_type <> 'expense' then
                    raise exception using message = 'Abonelik kategorisi gider türünde olmalıdır.';
                end if;
            end if;

            v_sub_interval := coalesce(nullif(p_payload ->> 'interval', '')::integer, 1);
            v_sub_start_date := (p_payload ->> 'start_date')::date;
            v_sub_end_date := (p_payload ->> 'end_date')::date;
            v_sub_next_renewal_date := (p_payload ->> 'next_renewal_date')::date;

            if v_sub_interval <= 0 then
                raise exception using message = 'Yenileme aralığı pozitif bir tamsayı olmalıdır.';
            end if;

            if v_sub_start_date is null then
                raise exception using message = 'Abonelik başlangıç tarihi zorunludur.';
            end if;

            if v_sub_end_date is not null and v_sub_end_date < v_sub_start_date then
                raise exception using message = 'Abonelik bitiş tarihi başlangıç tarihinden önce olamaz.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            if v_sub_next_renewal_date is not null then
                raise exception using message = 'CREATE işleminde next_renewal_date null olmalıdır.';
            end if;

            insert into public.subscriptions (
                id, user_id, workspace_id, name, amount_minor, currency, category_id,
                billing_cycle, interval, start_date, end_date, next_renewal_date,
                payment_method, cancel_url, reminder_days_before, is_active, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_sub_name,
                (p_payload ->> 'amount_minor')::bigint,
                coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                v_sub_category_id,
                p_payload ->> 'billing_cycle',
                v_sub_interval,
                v_sub_start_date,
                v_sub_end_date,
                null,
                p_payload ->> 'payment_method',
                nullif(trim(p_payload ->> 'cancel_url'), ''),
                coalesce(nullif(p_payload ->> 'reminder_days_before', '')::integer, 1),
                coalesce((p_payload ->> 'is_active')::boolean, true),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(subscriptions.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.subscriptions
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(subscriptions.*) into written_row;
        else -- UPDATE
            select s.next_renewal_date into v_existing_next_renewal
              from public.subscriptions as s
             where s.id = entity_id
               and s.user_id = actor_id
               and s.workspace_id is null
               and s.deleted_at is null;

            if found then
                if v_existing_next_renewal is not null then
                    if v_sub_next_renewal_date is null or v_sub_next_renewal_date < v_existing_next_renewal then
                        raise exception using message = 'next_renewal_date geriye taşınamaz veya silinemez.';
                    end if;
                    if v_sub_start_date > v_existing_next_renewal then
                        raise exception using message = 'start_date sonraki yenileme tarihinin sonrasına taşınamaz.';
                    end if;
                    if v_sub_end_date is not null and v_sub_end_date < v_existing_next_renewal then
                        raise exception using message = 'end_date sonraki yenileme tarihinin öncesine çekilemez.';
                    end if;
                else
                    if v_sub_next_renewal_date is not null and v_sub_next_renewal_date < v_sub_start_date then
                        raise exception using message = 'next_renewal_date start_date öncesinde olamaz.';
                    end if;
                end if;
            end if;

            update public.subscriptions
               set name = v_sub_name,
                   amount_minor = (p_payload ->> 'amount_minor')::bigint,
                   currency = coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                   category_id = v_sub_category_id,
                   billing_cycle = p_payload ->> 'billing_cycle',
                   interval = v_sub_interval,
                   start_date = v_sub_start_date,
                   end_date = v_sub_end_date,
                   next_renewal_date = v_sub_next_renewal_date,
                   payment_method = p_payload ->> 'payment_method',
                   cancel_url = nullif(trim(p_payload ->> 'cancel_url'), ''),
                   reminder_days_before = coalesce(nullif(p_payload ->> 'reminder_days_before', '')::integer, reminder_days_before),
                   is_active = coalesce((p_payload ->> 'is_active')::boolean, is_active),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(subscriptions.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(s.*) into current_row
              from public.subscriptions as s
             where s.id = entity_id
               and s.user_id = actor_id
               and s.workspace_id is null;
        end if;

    elsif p_entity_type = 'GOAL' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Hedef sahipliği reddedildi.';
            end if;

            v_goal_name := nullif(trim(p_payload ->> 'name'), '');
            if v_goal_name is null or length(v_goal_name) > 500 then
                raise exception using message = 'Hedef adı geçerli olmalıdır (1-500 karakter).';
            end if;

            if (p_payload ->> 'target_amount_minor') is null or (p_payload ->> 'target_amount_minor')::bigint <= 0 then
                raise exception using message = 'Hedef tutarı sıfırdan büyük olmalıdır.';
            end if;
            v_goal_target_amount := (p_payload ->> 'target_amount_minor')::bigint;

            v_goal_currency := coalesce(nullif(trim(p_payload ->> 'currency'), ''), 'TRY');
            v_goal_target_date := (p_payload ->> 'target_date')::date;
            v_goal_color_hex := nullif(trim(p_payload ->> 'color_hex'), '');
            v_goal_icon_key := nullif(trim(p_payload ->> 'icon_key'), '');

            if v_goal_color_hex is not null and not (v_goal_color_hex ~* '^#[0-9a-f]{6}$') then
                raise exception using message = 'Hedef renk formatı geçersiz (#RRGGBB).';
            end if;
        end if;

        if p_operation = 'CREATE' then
            insert into public.goals (
                id, user_id, workspace_id, name, target_amount_minor, current_amount_minor,
                currency, target_date, color_hex, icon_key, is_archived, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_goal_name,
                v_goal_target_amount,
                0,
                v_goal_currency,
                v_goal_target_date,
                v_goal_color_hex,
                v_goal_icon_key,
                coalesce((p_payload ->> 'is_archived')::boolean, false),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(goals.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.goals
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(goals.*) into written_row;
        else -- UPDATE (current_amount_minor değiştirilemez)
            select * into v_goal
              from public.goals
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and deleted_at is null;

            if found then
                if v_goal_currency <> v_goal.currency then
                    raise exception using message = 'Hedef para birimi değiştirilemez.';
                end if;
            end if;

            update public.goals
               set name = v_goal_name,
                   target_amount_minor = v_goal_target_amount,
                   target_date = v_goal_target_date,
                   color_hex = v_goal_color_hex,
                   icon_key = v_goal_icon_key,
                   is_archived = coalesce((p_payload ->> 'is_archived')::boolean, is_archived),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(goals.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(g.*) into current_row
              from public.goals as g
             where g.id = entity_id
               and g.user_id = actor_id
               and g.workspace_id is null;
        end if;

    elsif p_entity_type = 'GOAL_CONTRIBUTION' then
        if p_operation <> 'CREATE' then
            raise exception using message = 'GOAL_CONTRIBUTION için UPDATE/DELETE doğrudan desteklenmez; salt-eklenir (append-only) modeldir.';
        end if;

        v_contrib_goal_id := nullif(p_payload ->> 'goal_id', '')::uuid;
        if v_contrib_goal_id is null then
            raise exception using message = 'Hedef katkısı için goal_id zorunludur.';
        end if;

        if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
           or nullif(p_payload ->> 'workspace_id', '') is not null then
            raise insufficient_privilege using message = 'Hedef katkısı sahipliği reddedildi.';
        end if;

        v_contrib_amount := (p_payload ->> 'amount_minor')::bigint;
        if v_contrib_amount is null or v_contrib_amount <= 0 then
            raise exception using message = 'Hedef katkı tutarı sıfırdan büyük olmalıdır.';
        end if;

        v_contrib_currency := coalesce(nullif(trim(p_payload ->> 'currency'), ''), 'TRY');
        v_contrib_direction := coalesce(nullif(trim(p_payload ->> 'direction'), ''), 'add');
        if v_contrib_direction not in ('add', 'remove') then
            raise exception using message = 'Katkı yönü yalnızca add veya remove olabilir.';
        end if;

        v_contrib_occurred_on := coalesce((p_payload ->> 'occurred_on')::date, (pg_catalog.timezone('utc'::text, pg_catalog.now()))::date);
        v_contrib_note := nullif(trim(p_payload ->> 'note'), '');

        -- Parent hedef satırını kilitle ve kontrol et
        select * into v_goal
          from public.goals
         where id = v_contrib_goal_id
           and user_id = actor_id
           and workspace_id is null
           and deleted_at is null
           for update;

        if not found then
            raise exception using message = 'Katkı yapılacak hedef bulunamadı veya silinmiş.';
        end if;

        if v_goal.version <> p_base_version then
            -- Parent versiyon çakışması: CONFLICT dön
            current_row := pg_catalog.to_jsonb(v_goal);
        else
            if v_goal.currency <> v_contrib_currency then
                raise exception using message = 'Katkı para birimi hedef para birimi ile uyuşmuyor.';
            end if;

            if v_contrib_direction = 'add' then
                v_new_goal_amount := v_goal.current_amount_minor + v_contrib_amount;
            else
                v_new_goal_amount := v_goal.current_amount_minor - v_contrib_amount;
                if v_new_goal_amount < 0 then
                    raise exception using message = 'Hedef birikimi sıfırın altına düşemez.';
                end if;
            end if;

            -- Katkıyı ekle
            insert into public.goal_contributions (
                id, goal_id, user_id, workspace_id, amount_minor, currency,
                direction, occurred_on, note, created_at
            ) values (
                entity_id,
                v_contrib_goal_id,
                actor_id,
                null,
                v_contrib_amount,
                v_contrib_currency,
                v_contrib_direction,
                v_contrib_occurred_on,
                v_contrib_note,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning * into v_inserted_contrib;

            -- Hedef güncel tutarını ve sürümünü atomik artır
            update public.goals
               set current_amount_minor = v_new_goal_amount
             where id = v_contrib_goal_id
            returning * into v_updated_goal;

            if v_inserted_contrib.id is not null then
                written_row := pg_catalog.jsonb_build_object(
                    'contribution', pg_catalog.to_jsonb(v_inserted_contrib),
                    'goal', pg_catalog.to_jsonb(v_updated_goal)
                );
            else
                select pg_catalog.to_jsonb(gc.*) into current_row
                  from public.goal_contributions as gc
                 where gc.id = entity_id;
            end if;
        end if;

    elsif p_entity_type = 'DEBT' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Borç sahipliği reddedildi.';
            end if;

            v_debt_title := nullif(trim(p_payload ->> 'title'), '');
            if v_debt_title is null or length(v_debt_title) > 500 then
                raise exception using message = 'Borç başlığı geçerli olmalıdır (1-500 karakter).';
            end if;

            v_debt_amount := (p_payload ->> 'amount_minor')::bigint;
            if v_debt_amount is null or v_debt_amount <= 0 then
                raise exception using message = 'Borç tutarı sıfırdan büyük olmalıdır.';
            end if;

            v_debt_currency := coalesce(nullif(trim(p_payload ->> 'currency'), ''), 'TRY');
            v_debt_type := coalesce(nullif(trim(p_payload ->> 'type'), ''), 'payable');
            if v_debt_type not in ('payable', 'receivable') then
                raise exception using message = 'Borç türü yalnızca payable veya receivable olabilir.';
            end if;

            v_debt_due_date := (p_payload ->> 'due_date')::date;
            v_debt_description := nullif(trim(p_payload ->> 'description'), '');
        end if;

        if p_operation = 'CREATE' then
            insert into public.debts (
                id, user_id, workspace_id, title, amount_minor, currency, type,
                due_date, status, description, is_archived, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_debt_title,
                v_debt_amount,
                v_debt_currency,
                v_debt_type,
                v_debt_due_date,
                'open',
                v_debt_description,
                coalesce((p_payload ->> 'is_archived')::boolean, false),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(debts.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.debts
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(debts.*) into written_row;
        else -- UPDATE (status doğrudan değiştirilemez; ödemelerle türetilir)
            select * into v_debt
              from public.debts
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and deleted_at is null;

            if found then
                if v_debt_currency <> v_debt.currency then
                    raise exception using message = 'Borç para birimi değiştirilemez.';
                end if;
                if v_debt_type <> v_debt.type then
                    raise exception using message = 'Borç yönü (type) değiştirilemez.';
                end if;
                if v_debt_amount < v_debt.amount_minor then
                    -- Mevcut toplam ödemeyi denetle
                    select coalesce(sum(amount_minor), 0) into v_total_paid
                      from public.debt_payments
                     where debt_id = entity_id
                       and deleted_at is null;

                    if v_debt_amount < v_total_paid then
                        raise exception using message = 'Borç anapara tutarı yapılmış ödemelerin toplamından az olamaz.';
                    end if;
                end if;
            end if;

            -- Status yeniden değerlendirmesi
            select coalesce(sum(amount_minor), 0) into v_total_paid
              from public.debt_payments
             where debt_id = entity_id
               and deleted_at is null;

            if v_total_paid >= v_debt_amount then
                v_new_debt_status := 'paid';
            elsif v_total_paid > 0 then
                v_new_debt_status := 'partially_paid';
            else
                v_new_debt_status := 'open';
            end if;

            update public.debts
               set title = v_debt_title,
                   amount_minor = v_debt_amount,
                   due_date = v_debt_due_date,
                   status = v_new_debt_status,
                   description = v_debt_description,
                   is_archived = coalesce((p_payload ->> 'is_archived')::boolean, is_archived),
                   deleted_at = null
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(debts.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(d.*) into current_row
              from public.debts as d
             where d.id = entity_id
               and d.user_id = actor_id
               and d.workspace_id is null;
        end if;

    elsif p_entity_type = 'DEBT_PAYMENT' then
        if p_operation <> 'CREATE' then
            raise exception using message = 'DEBT_PAYMENT için UPDATE/DELETE doğrudan desteklenmez; salt-eklenir (append-only) modeldir.';
        end if;

        v_payment_debt_id := nullif(p_payload ->> 'debt_id', '')::uuid;
        if v_payment_debt_id is null then
            raise exception using message = 'Borç ödemesi için debt_id zorunludur.';
        end if;

        if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
           or nullif(p_payload ->> 'workspace_id', '') is not null then
            raise insufficient_privilege using message = 'Borç ödemesi sahipliği reddedildi.';
        end if;

        v_payment_amount := (p_payload ->> 'amount_minor')::bigint;
        if v_payment_amount is null or v_payment_amount <= 0 then
            raise exception using message = 'Borç ödeme tutarı sıfırdan büyük olmalıdır.';
        end if;

        v_payment_currency := coalesce(nullif(trim(p_payload ->> 'currency'), ''), 'TRY');
        v_payment_paid_on := coalesce((p_payload ->> 'paid_on')::date, (pg_catalog.timezone('utc'::text, pg_catalog.now()))::date);

        -- Parent borç satırını kilitle ve kontrol et
        select * into v_debt
          from public.debts
         where id = v_payment_debt_id
           and user_id = actor_id
           and workspace_id is null
           and deleted_at is null
           for update;

        if not found then
            raise exception using message = 'Ödeme yapılacak borç bulunamadı veya silinmiş.';
        end if;

        if v_debt.version <> p_base_version then
            -- Parent versiyon çakışması: CONFLICT dön
            current_row := pg_catalog.to_jsonb(v_debt);
        else
            if v_debt.currency <> v_payment_currency then
                raise exception using message = 'Ödeme para birimi borç para birimi ile uyuşmuyor.';
            end if;

            select coalesce(sum(amount_minor), 0) into v_total_paid
              from public.debt_payments
             where debt_id = v_payment_debt_id
               and deleted_at is null;

            v_total_paid := v_total_paid + v_payment_amount;
            if v_total_paid > v_debt.amount_minor then
                raise exception using message = 'Toplam ödeme tutarı borç anaparasını aşamaz.';
            end if;

            if v_total_paid = v_debt.amount_minor then
                v_new_debt_status := 'paid';
            else
                v_new_debt_status := 'partially_paid';
            end if;

            -- Ödemeyi ekle
            insert into public.debt_payments (
                id, debt_id, user_id, workspace_id, amount_minor, currency,
                paid_on, note, created_at
            ) values (
                entity_id,
                v_payment_debt_id,
                actor_id,
                null,
                v_payment_amount,
                v_payment_currency,
                v_payment_paid_on,
                nullif(trim(p_payload ->> 'note'), ''),
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning * into v_inserted_payment;

            -- Borcun durumunu ve versiyonunu atomik güncelle
            update public.debts
               set status = v_new_debt_status
             where id = v_payment_debt_id
            returning * into v_updated_debt;

            if v_inserted_payment.id is not null then
                written_row := pg_catalog.jsonb_build_object(
                    'payment', pg_catalog.to_jsonb(v_inserted_payment),
                    'debt', pg_catalog.to_jsonb(v_updated_debt)
                );
            else
                select pg_catalog.to_jsonb(dp.*) into current_row
                  from public.debt_payments as dp
                 where dp.id = entity_id;
            end if;
        end if;

    elsif p_entity_type = 'WORKSPACE' then
        if p_operation in ('CREATE', 'UPDATE') then
            v_ws_name := nullif(trim(p_payload ->> 'name'), '');
            if v_ws_name is null or length(v_ws_name) > 100 then
                raise exception using message = 'Çalışma alanı adı 1-100 karakter arasında olmalıdır.';
            end if;

            v_ws_type_code := p_payload ->> 'type_code';
            if v_ws_type_code is null or v_ws_type_code not in ('PERSONAL', 'FAMILY', 'PROJECT', 'BUSINESS') then
                raise exception using message = 'Geçersiz çalışma alanı türü (type_code).';
            end if;

            v_ws_currency_code := p_payload ->> 'currency_code';
            if v_ws_currency_code is null or length(v_ws_currency_code) <> 3 then
                raise exception using message = 'Geçersiz para birimi kodu (currency_code).';
            end if;

            v_ws_normalized_name := public.feniqo_normalize_text(v_ws_name);
            v_ws_description := nullif(trim(p_payload ->> 'description'), '');
            if v_ws_description is not null and length(v_ws_description) > 500 then
                raise exception using message = 'Çalışma alanı açıklaması 500 karakterden uzun olamaz.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            -- 1. workspaces tablosuna ekle
            insert into public.workspaces (
                id, owner_id, name, normalized_name, type_code, currency_code,
                description, created_at
            ) values (
                entity_id,
                actor_id,
                v_ws_name,
                v_ws_normalized_name,
                v_ws_type_code,
                v_ws_currency_code,
                v_ws_description,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning * into v_ws_record;

            if v_ws_record.id is not null then
                -- 2. Kurucuyu atomik olarak OWNER rolüyle workspace_members tablosuna ekle
                insert into public.workspace_members (
                    workspace_id, user_id, role_code, joined_at
                ) values (
                    entity_id,
                    actor_id,
                    'OWNER',
                    coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
                )
                on conflict (workspace_id, user_id) do nothing;

                written_row := pg_catalog.to_jsonb(v_ws_record);
            end if;

        elsif p_operation = 'DELETE' then
            -- Yalnızca aktif OWNER silebilir
            select role_code into v_member_role
              from public.workspace_members
             where workspace_id = entity_id
               and user_id = actor_id
               and deleted_at is null;

            if v_member_role is null or v_member_role <> 'OWNER' then
                raise insufficient_privilege using message = 'Çalışma alanını yalnızca sahibi silebilir.';
            end if;

            update public.workspaces
               set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
             where id = entity_id
               and owner_id = actor_id
               and version = p_base_version
            returning pg_catalog.to_jsonb(workspaces.*) into written_row;

        else -- UPDATE
            -- Yalnızca aktif OWNER düzenleyebilir
            select role_code into v_member_role
              from public.workspace_members
             where workspace_id = entity_id
               and user_id = actor_id
               and deleted_at is null;

            if v_member_role is null or v_member_role <> 'OWNER' then
                raise insufficient_privilege using message = 'Çalışma alanını yalnızca sahibi düzenleyebilir.';
            end if;

            update public.workspaces
               set name = v_ws_name,
                   normalized_name = v_ws_normalized_name,
                   type_code = v_ws_type_code,
                   currency_code = v_ws_currency_code,
                   description = v_ws_description,
                   deleted_at = null
             where id = entity_id
               and owner_id = actor_id
               and version = p_base_version
            returning pg_catalog.to_jsonb(workspaces.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(w.*) into current_row
              from public.workspaces as w
             where w.id = entity_id;
        end if;

    elsif p_entity_type = 'WORKSPACE_INVITATION' then
        if p_operation = 'CREATE' then
            v_inv_workspace_id := (p_payload ->> 'workspace_id')::uuid;
            v_inv_role_code := p_payload ->> 'role_code';
            v_inv_token_hash := p_payload ->> 'token_hash';
            v_inv_expires_at := (p_payload ->> 'expires_at')::timestamptz;
            v_inv_max_uses := coalesce((p_payload ->> 'max_uses')::integer, 1);

            if v_inv_workspace_id is null then
                raise exception using message = 'Davet için workspace_id zorunludur.';
            end if;

            if v_inv_role_code not in ('EDITOR', 'VIEWER') then
                raise exception using message = 'Davet rolü yalnızca EDITOR veya VIEWER olabilir.';
            end if;

            if v_inv_token_hash is null or length(v_inv_token_hash) <> 64 then
                raise exception using message = 'Geçersiz davet belirteç özeti (token_hash).';
            end if;

            if v_inv_expires_at is null or v_inv_expires_at <= pg_catalog.timezone('utc'::text, pg_catalog.now()) then
                raise exception using message = 'Davet son geçerlilik tarihi gelecekte olmalıdır.';
            end if;

            if v_inv_max_uses <= 0 then
                raise exception using message = 'Maksimum kullanım sayısı en az 1 olmalıdır.';
            end if;

            -- Yalnızca aktif OWNER veya EDITOR davet oluşturabilir
            select role_code into v_member_role
              from public.workspace_members
             where workspace_id = v_inv_workspace_id
               and user_id = actor_id
               and deleted_at is null;

            if v_member_role is null or v_member_role not in ('OWNER', 'EDITOR') then
                raise insufficient_privilege using message = 'Davet oluşturma yetkisi bulunmuyor.';
            end if;

            insert into public.workspace_invitations (
                id, workspace_id, inviter_id, role_code, token_hash,
                expires_at, max_uses, uses_count, created_at
            ) values (
                entity_id,
                v_inv_workspace_id,
                actor_id,
                v_inv_role_code,
                v_inv_token_hash,
                v_inv_expires_at,
                v_inv_max_uses,
                0,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning (pg_catalog.to_jsonb(workspace_invitations.*) - 'token_hash') into written_row;

        elsif p_operation = 'DELETE' then
            select * into v_inv_record
              from public.workspace_invitations
             where id = entity_id
               and deleted_at is null
               for update;

            if not found then
                -- not found
            else
                -- Aktif OWNER veya daveti oluşturan kişi silebilir
                select role_code into v_member_role
                  from public.workspace_members
                 where workspace_id = v_inv_record.workspace_id
                   and user_id = actor_id
                   and deleted_at is null;

                if (v_member_role is null or v_member_role <> 'OWNER') and v_inv_record.inviter_id <> actor_id then
                    raise insufficient_privilege using message = 'Daveti silme yetkisi bulunmuyor.';
                end if;

                if v_inv_record.version <> p_base_version then
                    current_row := pg_catalog.to_jsonb(v_inv_record) - 'token_hash';
                else
                    update public.workspace_invitations
                       set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                     where id = entity_id
                    returning (pg_catalog.to_jsonb(workspace_invitations.*) - 'token_hash') into written_row;
                end if;
            end if;

        else
            raise exception using message = 'WORKSPACE_INVITATION için UPDATE işlemi desteklenmez.';
        end if;

        if written_row is null and current_row is null then
            select (pg_catalog.to_jsonb(wi.*) - 'token_hash') into current_row
              from public.workspace_invitations as wi
             where wi.id = entity_id;
        end if;

    elsif p_entity_type = 'WORKSPACE_MEMBER' then
        v_target_workspace_id := (p_payload ->> 'workspace_id')::uuid;
        v_target_user_id := (p_payload ->> 'user_id')::uuid;

        if p_operation = 'CREATE' then
            raise exception using message = 'WORKSPACE_MEMBER için generic outbox CREATE işlemi desteklenmez. Davetle katılım için redeem_workspace_invitation_v1 kullanınız.';
        elsif p_operation = 'UPDATE' then
            -- Rol güncelleme: Yalnızca aktif OWNER yapabilir
            select role_code into v_member_role
              from public.workspace_members
             where workspace_id = v_target_workspace_id
               and user_id = actor_id
               and deleted_at is null;

            if v_member_role is null or v_member_role <> 'OWNER' then
                raise insufficient_privilege using message = 'Üye rolünü yalnızca aktif çalışma alanı sahibi değiştirebilir.';
            end if;

            v_new_role := p_payload ->> 'role_code';
            if v_new_role not in ('EDITOR', 'VIEWER') then
                raise exception using message = 'Yeni rol yalnızca EDITOR veya VIEWER olabilir.';
            end if;

            select * into v_member_record
              from public.workspace_members
             where workspace_id = v_target_workspace_id
               and user_id = v_target_user_id
               and deleted_at is null
               for update;

            if not found then
                -- not found
            elsif v_member_record.role_code = 'OWNER' then
                raise exception using message = 'Çalışma alanı sahibinin rolü bu işlemle değiştirilemez.';
            elsif v_member_record.version <> p_base_version then
                current_row := pg_catalog.to_jsonb(v_member_record);
            else
                update public.workspace_members
                   set role_code = v_new_role
                 where workspace_id = v_target_workspace_id
                   and user_id = v_target_user_id
                returning pg_catalog.to_jsonb(workspace_members.*) into written_row;
            end if;

        elsif p_operation = 'DELETE' then
            if p_base_version is null or p_base_version <= 0 then
                raise exception using message = 'baseVersion zorunludur ve pozitif bir tamsayı olmalıdır.';
            end if;

            -- Alandan ayrılma veya üye çıkarma
            select * into v_member_record
              from public.workspace_members
             where workspace_id = v_target_workspace_id
               and user_id = v_target_user_id
               for update;

            if not found or v_member_record.deleted_at is not null then
                -- not found
            else
                -- OWNER alandan ayrılamaz (önce devir gerekir)
                if actor_id = v_target_user_id and v_member_record.role_code = 'OWNER' then
                    raise exception using message = 'Çalışma alanı sahibi alandan ayrılamaz.';
                end if;

                if actor_id <> v_target_user_id then
                    -- Başka bir üyeyi çıkarmak istiyorsa actor aktif OWNER olmalıdır
                    select role_code into v_member_role
                      from public.workspace_members
                     where workspace_id = v_target_workspace_id
                       and user_id = actor_id
                       and deleted_at is null;

                    if v_member_role is null or v_member_role <> 'OWNER' then
                        raise insufficient_privilege using message = 'Üyeyi yalnızca aktif çalışma alanı sahibi çıkarabilir.';
                    end if;

                    if v_member_record.role_code not in ('EDITOR', 'VIEWER') then
                        raise exception using message = 'Çalışma alanı sahibi üyelikten çıkarılamaz.';
                    end if;
                end if;

                if v_member_record.version <> p_base_version then
                    current_row := pg_catalog.to_jsonb(v_member_record);
                else
                    update public.workspace_members
                       set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                     where workspace_id = v_target_workspace_id
                       and user_id = v_target_user_id
                    returning pg_catalog.to_jsonb(workspace_members.*) into written_row;
                end if;
            end if;
        end if;

        if written_row is null and current_row is null then
            select pg_catalog.to_jsonb(wm.*) into current_row
              from public.workspace_members as wm
             where wm.workspace_id = v_target_workspace_id
               and wm.user_id = v_target_user_id;
        end if;

    end if;

    -- 11. Başarılı Yazma Sonrası Idempotency Receipt Kaydı
    if written_row is not null then
        insert into public.sync_operations_receipts (
            operation_id,
            user_id,
            entity_type,
            entity_id,
            operation_type,
            base_version,
            request_fingerprint,
            result_status,
            applied_version,
            response_record,
            applied_at
        ) values (
            p_operation_id,
            actor_id,
            p_entity_type,
            entity_id,
            p_operation,
            p_base_version,
            v_fingerprint,
            'APPLIED',
            coalesce(
                (written_row ->> 'version')::bigint,
                (written_row -> 'contribution' ->> 'version')::bigint,
                (written_row -> 'payment' ->> 'version')::bigint,
                1
            ),
            written_row,
            pg_catalog.timezone('utc'::text, pg_catalog.now())
        );

        return pg_catalog.jsonb_build_object('status', 'APPLIED', 'record', written_row);
    end if;

    -- 12. Conflict Durumu (Receipt kaydedilmez)
    if current_row is not null then
        return pg_catalog.jsonb_build_object('status', 'CONFLICT', 'record', current_row);
    end if;

    -- 13. Not Found Durumu (Receipt kaydedilmez)
    return pg_catalog.jsonb_build_object('status', 'NOT_FOUND', 'record', null);
end
$$;

revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from public;
revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from anon;
grant execute on function public.sync_write_v2(text, text, text, bigint, jsonb) to authenticated;

commit;
