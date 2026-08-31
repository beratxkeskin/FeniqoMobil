-- TASLAK: Sync Write V2 Tekrarlayan İşlem Şeması ve RECURRING_TRANSACTION RPC Genişletmesi. Yalnız yerel/staging için.
begin;

-- 1. Kişisel V2 Tekrarlayan İşlem Tablosu (public.recurring_transactions)
-- Fail-closed: Önceden tanımlı veya eski uyumsuz tablo varsa migration açıkça hata vermelidir.
create table public.recurring_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    workspace_id uuid,
    amount_minor bigint not null,
    currency text not null default 'TRY',
    type text not null,
    category_id uuid not null references public.categories(id) on delete restrict,
    description text,
    payment_method text not null,
    frequency text not null,
    interval integer not null default 1,
    start_date date not null,
    end_date date,
    last_generated_date date,
    is_active boolean not null default true,
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    deleted_at timestamptz,
    version bigint not null default 1,

    constraint recurring_transactions_workspace_personal_check
        check (workspace_id is null),

    constraint recurring_transactions_amount_minor_positive
        check (amount_minor > 0),

    constraint recurring_transactions_currency_check
        check (currency in ('TRY', 'USD', 'EUR')),

    constraint recurring_transactions_type_check
        check (type in ('income', 'expense')),

    constraint recurring_transactions_payment_method_check
        check (payment_method in ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'OTHER')),

    constraint recurring_transactions_frequency_check
        check (frequency in ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),

    constraint recurring_transactions_interval_positive
        check (interval > 0),

    constraint recurring_transactions_date_order_check
        check (end_date is null or end_date >= start_date),

    constraint recurring_transactions_last_generated_order_check
        check (last_generated_date is null or last_generated_date >= start_date),

    constraint recurring_transactions_description_check
        check (description is null or (length(trim(description)) > 0 and length(description) <= 500)),

    constraint recurring_transactions_version_positive
        check (version > 0)
);

-- Artımlı senkronizasyon cursor ve sorgu indeksleri
create index idx_recurring_transactions_sync_cursor
    on public.recurring_transactions (updated_at, id);

create index idx_recurring_transactions_active_owner
    on public.recurring_transactions (user_id, updated_at, id)
    where deleted_at is null;

create index idx_recurring_transactions_category
    on public.recurring_transactions (category_id);

-- sync_set_server_metadata tetikleyicisi bağlama (update'lerde updated_at = now(), version = old.version + 1)
create trigger recurring_transactions_set_server_metadata
    before update on public.recurring_transactions
    for each row execute function public.sync_set_server_metadata();

-- RLS ve yetkilendirme: public, anon ve authenticated rollerinden doğrudan mutation tamamen kaldırılır
alter table public.recurring_transactions enable row level security;

revoke all on table public.recurring_transactions from public;
revoke all on table public.recurring_transactions from anon;
revoke all on table public.recurring_transactions from authenticated;

-- Yalnız authenticated kullanıcılara SELECT izni verilir (okuma ve incremental sync için).
-- INSERT, UPDATE, DELETE yalnız SECURITY DEFINER sync_write_v2 RPC üzerinden yürütülür.
grant select on table public.recurring_transactions to authenticated;

create policy recurring_transactions_select_personal_v1
    on public.recurring_transactions for select to authenticated
    using (user_id = (select auth.uid()) and workspace_id is null);


-- 2. sync_operations_receipts entity_type kısıtlamasını RECURRING_TRANSACTION kabul edecek şekilde güncelle
alter table public.sync_operations_receipts
    drop constraint if exists sync_operations_receipts_entity_type_check;

alter table public.sync_operations_receipts
    add constraint sync_operations_receipts_entity_type_check
    check (entity_type in ('PROFILE', 'CATEGORY', 'TRANSACTION', 'BUDGET', 'RECURRING_TRANSACTION'));


-- 3. sync_write_v2 RPC fonksiyonunu RECURRING_TRANSACTION destekleyecek şekilde yeniden tanımla
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
    v_budget_category_id uuid;
    v_budget_month text;
    v_rec_category_id uuid;
    v_rec_type text;
    v_rec_interval integer;
    v_rec_start_date date;
    v_rec_end_date date;
    v_rec_last_generated_date date;
    v_existing_last_generated date;
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
    if p_entity_type not in ('PROFILE', 'CATEGORY', 'TRANSACTION', 'BUDGET', 'RECURRING_TRANSACTION') then
        raise exception using message = 'Desteklenmeyen entity türü.';
    end if;

    -- 4. operation doğrulaması
    if p_operation not in ('CREATE', 'UPDATE', 'DELETE') then
        raise exception using message = 'Desteklenmeyen senkronizasyon işlemi.';
    end if;

    -- 5. base_version doğrulaması
    if p_operation = 'CREATE' and p_base_version is not null then
        raise exception using message = 'CREATE işlemi için base_version null olmalıdır.';
    end if;

    if p_operation in ('UPDATE', 'DELETE') and (p_base_version is null or p_base_version < 1) then
        raise exception using message = 'UPDATE ve DELETE işlemleri için base_version en az 1 olmalıdır.';
    end if;

    -- 6. payload ve entity_id doğrulaması
    if p_payload is null or pg_catalog.jsonb_typeof(p_payload) <> 'object' then
        raise exception using message = 'Payload geçerli bir JSON nesnesi olmalıdır.';
    end if;

    if (p_payload ->> 'id') is null or not ((p_payload ->> 'id') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then
        raise exception using message = 'Payload id geçerli bir UUID olmalıdır.';
    end if;
    entity_id := (p_payload ->> 'id')::uuid;

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
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'İşlem sahipliği reddedildi.';
            end if;

            v_tx_category_id := nullif(p_payload ->> 'category_id', '')::uuid;
            v_tx_type := p_payload ->> 'type';

            if v_tx_category_id is null then
                raise exception using message = 'İşlem için category_id zorunludur.';
            end if;

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
        end if;

        if p_operation = 'CREATE' then
            insert into public.transactions (
                id, user_id, workspace_id, amount_minor, currency, type, category_id,
                description, payment_method, transaction_date, receipt_path,
                installment_number, total_installments, installment_group_id, created_at
            ) values (
                entity_id,
                actor_id,
                null,
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
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(transactions.*) into written_row;
        else
            update public.transactions
               set amount_minor = (p_payload ->> 'amount_minor')::bigint,
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
               and workspace_id is null
               and version = p_base_version
            returning pg_catalog.to_jsonb(transactions.*) into written_row;
        end if;

        if written_row is null then
            select pg_catalog.to_jsonb(t.*) into current_row
              from public.transactions as t
             where t.id = entity_id
               and t.user_id = actor_id
               and t.workspace_id is null;
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

    else -- RECURRING_TRANSACTION
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
            -- Mevcut last_generated_date kontrolü (regresyon engeli)
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
            (written_row ->> 'version')::bigint,
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

-- İzinleri yapılandır
revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from public;
revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from anon;
grant execute on function public.sync_write_v2(text, text, text, bigint, jsonb) to authenticated;

commit;
