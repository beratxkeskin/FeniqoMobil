-- Reconciliation Migration: Goals & Debts V2 Doğrulama ve RPC Kanonikleştirme. Yalnız yerel/staging için.
begin;

-- 1. PostgreSQL Catalog üzerinden Fail-Closed Şema ve Kısıtlama Doğrulaması
do $$
declare
    v_target_date_type text;
    v_target_date_nullable text;
    v_hex_check_def text;
    v_entity_check_def text;
    v_base_ver_check_def text;
begin
    -- A. goals.target_date kolonunun varlığı, data_type ve NOT NULL kontrolü
    select data_type, is_nullable
      into v_target_date_type, v_target_date_nullable
      from information_schema.columns
     where table_schema = 'public'
       and table_name = 'goals'
       and column_name = 'target_date';

    if v_target_date_type is null then
        raise exception 'Reconciliation Hatası: public.goals tablosunda target_date kolonu bulunamadı.';
    end if;

    if v_target_date_type <> 'date' then
        raise exception 'Reconciliation Hatası: public.goals.target_date kolonu date tipinde değil (mevcut: %).', v_target_date_type;
    end if;

    if v_target_date_nullable <> 'NO' then
        raise exception 'Reconciliation Hatası: public.goals.target_date kolonu NOT NULL kısıtlamasına sahip değil.';
    end if;

    -- B. goals_color_hex_format_check kısıtlaması ve regex format doğrulaması
    select pg_catalog.pg_get_constraintdef(oid)
      into v_hex_check_def
      from pg_catalog.pg_constraint
     where conrelid = 'public.goals'::regclass
       and conname = 'goals_color_hex_format_check';

    if v_hex_check_def is null then
        raise exception 'Reconciliation Hatası: public.goals tablosunda goals_color_hex_format_check kısıtlaması bulunamadı.';
    end if;

    if v_hex_check_def not like '%color_hex ~* ''^#[0-9a-f]{6}$''%' then
        raise exception 'Reconciliation Hatası: goals_color_hex_format_check tanımı beklenen #RRGGBB regex formatıyla eşleşmiyor (tanım: %).', v_hex_check_def;
    end if;

    -- C. sync_operations_receipts_entity_type_check kısıtlaması ve tüm entity türleri doğrulaması
    select pg_catalog.pg_get_constraintdef(oid)
      into v_entity_check_def
      from pg_catalog.pg_constraint
     where conrelid = 'public.sync_operations_receipts'::regclass
       and conname = 'sync_operations_receipts_entity_type_check';

    if v_entity_check_def is null then
        raise exception 'Reconciliation Hatası: public.sync_operations_receipts_entity_type_check kısıtlaması bulunamadı.';
    end if;

    if v_entity_check_def not like '%PROFILE%'
       or v_entity_check_def not like '%CATEGORY%'
       or v_entity_check_def not like '%TRANSACTION%'
       or v_entity_check_def not like '%BUDGET%'
       or v_entity_check_def not like '%RECURRING_TRANSACTION%'
       or v_entity_check_def not like '%SUBSCRIPTION%'
       or v_entity_check_def not like '%GOAL%'
       or v_entity_check_def not like '%GOAL_CONTRIBUTION%'
       or v_entity_check_def not like '%DEBT%'
       or v_entity_check_def not like '%DEBT_PAYMENT%' then
        raise exception 'Reconciliation Hatası: sync_operations_receipts_entity_type_check tanımı beklenen 10 entity türünü içermiyor (tanım: %).', v_entity_check_def;
    end if;

    -- D. sync_operations_receipts_base_version_check kısıtlaması ve child CREATE istisnası doğrulaması
    select pg_catalog.pg_get_constraintdef(oid)
      into v_base_ver_check_def
      from pg_catalog.pg_constraint
     where conrelid = 'public.sync_operations_receipts'::regclass
       and conname = 'sync_operations_receipts_base_version_check';

    if v_base_ver_check_def is null then
        raise exception 'Reconciliation Hatası: public.sync_operations_receipts_base_version_check kısıtlaması bulunamadı.';
    end if;

    if v_base_ver_check_def not like '%GOAL_CONTRIBUTION%'
       or v_base_ver_check_def not like '%DEBT_PAYMENT%'
       or v_base_ver_check_def not like '%base_version%' then
        raise exception 'Reconciliation Hatası: sync_operations_receipts_base_version_check tanımı child CREATE base_version kuralını içermiyor (tanım: %).', v_base_ver_check_def;
    end if;
end
$$;

-- 2. sync_write_v2 RPC fonksiyonunu kanonik ve eksiksiz sözleşmeyle yeniden tanımla
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
        'GOAL', 'GOAL_CONTRIBUTION', 'DEBT', 'DEBT_PAYMENT'
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

            if v_sub_next_renewal_date is not null and v_sub_next_renewal_date < v_sub_start_date then
                raise exception using message = 'Sonraki yenileme tarihi başlangıç tarihinden önce olamaz.';
            end if;

            if v_sub_end_date is not null and v_sub_next_renewal_date is not null and v_sub_next_renewal_date > v_sub_end_date then
                raise exception using message = 'Sonraki yenileme tarihi bitiş tarihinden sonra olamaz.';
            end if;
        end if;

        if p_operation = 'CREATE' then
            if v_sub_next_renewal_date is null then
                raise exception using message = 'CREATE işleminde next_renewal_date zorunludur.';
            end if;

            insert into public.subscriptions (
                id, user_id, workspace_id, name, amount_minor, currency, category_id,
                frequency, interval, start_date, end_date, next_renewal_date, is_active, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_sub_name,
                (p_payload ->> 'amount_minor')::bigint,
                coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                v_sub_category_id,
                p_payload ->> 'frequency',
                v_sub_interval,
                v_sub_start_date,
                v_sub_end_date,
                v_sub_next_renewal_date,
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
        else
            select s.next_renewal_date into v_existing_next_renewal
              from public.subscriptions as s
             where s.id = entity_id
               and s.user_id = actor_id
               and s.workspace_id is null
               and s.deleted_at is null;

            if found then
                if v_sub_next_renewal_date is not null and v_sub_next_renewal_date < v_existing_next_renewal then
                    raise exception using message = 'next_renewal_date geriye taşınamaz.';
                end if;
                if v_sub_next_renewal_date is null then
                    v_sub_next_renewal_date := v_existing_next_renewal;
                end if;
                if v_sub_start_date > v_sub_next_renewal_date then
                    raise exception using message = 'start_date sonraki yenileme tarihinin sonrasına taşınamaz.';
                end if;
                if v_sub_end_date is not null and v_sub_end_date < v_sub_next_renewal_date then
                    raise exception using message = 'end_date sonraki yenileme tarihinin öncesine çekilemez.';
                end if;
            end if;

            update public.subscriptions
               set name = v_sub_name,
                   amount_minor = (p_payload ->> 'amount_minor')::bigint,
                   currency = coalesce(nullif(p_payload ->> 'currency', ''), 'TRY'),
                   category_id = v_sub_category_id,
                   frequency = p_payload ->> 'frequency',
                   interval = v_sub_interval,
                   start_date = v_sub_start_date,
                   end_date = v_sub_end_date,
                   next_renewal_date = v_sub_next_renewal_date,
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

            v_goal_target_amount := (p_payload ->> 'target_amount_minor')::bigint;
            if v_goal_target_amount is null or v_goal_target_amount <= 0 then
                raise exception using message = 'Hedef tutarı sıfırdan büyük olmalıdır.';
            end if;

            v_goal_currency := coalesce(nullif(p_payload ->> 'currency', ''), 'TRY');
            if v_goal_currency not in ('TRY', 'USD', 'EUR') then
                raise exception using message = 'Desteklenmeyen para birimi.';
            end if;

            v_goal_target_date := (p_payload ->> 'target_date')::date;
            if v_goal_target_date is null then
                raise exception using message = 'Hedef tarihi zorunludur.';
            end if;

            v_goal_color_hex := p_payload ->> 'color_hex';
            if v_goal_color_hex is null or not (v_goal_color_hex ~* '^#[0-9a-f]{6}$') then
                raise exception using message = 'Geçersiz hedef rengi formatı (#RRGGBB formatında olmalıdır).';
            end if;

            v_goal_icon_key := nullif(p_payload ->> 'icon_key', '');
        end if;

        if p_operation = 'CREATE' then
            v_goal_current_amount := coalesce((p_payload ->> 'current_amount_minor')::bigint, 0);
            if v_goal_current_amount < 0 then
                raise exception using message = 'Mevcut birikim tutarı negatif olamaz.';
            end if;

            insert into public.goals (
                id, user_id, workspace_id, name, target_amount_minor, current_amount_minor,
                currency, target_date, color_hex, icon_key, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_goal_name,
                v_goal_target_amount,
                v_goal_current_amount,
                v_goal_currency,
                v_goal_target_date,
                v_goal_color_hex,
                v_goal_icon_key,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(goals.*) into written_row;
        elsif p_operation = 'DELETE' then
            select * into v_goal
              from public.goals
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               for update;

            if not found or v_goal.deleted_at is not null then
                -- not found
            elsif v_goal.version <> p_base_version then
                current_row := pg_catalog.to_jsonb(v_goal);
            else
                update public.goals
                   set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                 where id = entity_id
                returning pg_catalog.to_jsonb(goals.*) into written_row;

                -- Canlı katkıları da tombstone yap
                update public.goal_contributions
                   set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                 where goal_id = entity_id
                   and deleted_at is null;
            end if;
        else -- UPDATE
            select * into v_goal
              from public.goals
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and deleted_at is null
               for update;

            if not found then
                -- not found
            elsif v_goal.version <> p_base_version then
                current_row := pg_catalog.to_jsonb(v_goal);
            else
                if v_goal_currency <> v_goal.currency then
                    raise exception using message = 'Hedef para birimi değiştirilemez.';
                end if;

                if (p_payload ? 'current_amount_minor') and ((p_payload ->> 'current_amount_minor')::bigint <> v_goal.current_amount_minor) then
                    raise exception using message = 'Mevcut birikim doğrudan güncellenemez; katkı işlemi gereklidir.';
                end if;

                update public.goals
                   set name = v_goal_name,
                       target_amount_minor = v_goal_target_amount,
                       target_date = v_goal_target_date,
                       color_hex = v_goal_color_hex,
                       icon_key = v_goal_icon_key,
                       deleted_at = null
                 where id = entity_id
                returning pg_catalog.to_jsonb(goals.*) into written_row;
            end if;
        end if;

        if written_row is null and current_row is null then
            select pg_catalog.to_jsonb(g.*) into current_row
              from public.goals as g
             where g.id = entity_id
               and g.user_id = actor_id
               and g.workspace_id is null;
        end if;

    elsif p_entity_type = 'GOAL_CONTRIBUTION' then
        if p_operation <> 'CREATE' then
            raise exception using message = 'GOAL_CONTRIBUTION için yalnız CREATE işlemi desteklenir.';
        end if;

        v_contrib_goal_id := (p_payload ->> 'goal_id')::uuid;
        if v_contrib_goal_id is null then
            raise exception using message = 'Hedef ID (goal_id) zorunludur.';
        end if;

        v_contrib_amount := (p_payload ->> 'amount_minor')::bigint;
        if v_contrib_amount is null or v_contrib_amount <= 0 then
            raise exception using message = 'Katkı tutarı sıfırdan büyük olmalıdır.';
        end if;

        v_contrib_currency := coalesce(nullif(p_payload ->> 'currency', ''), 'TRY');
        if v_contrib_currency not in ('TRY', 'USD', 'EUR') then
            raise exception using message = 'Desteklenmeyen para birimi.';
        end if;

        v_contrib_direction := p_payload ->> 'direction';
        if v_contrib_direction not in ('ADD', 'REMOVE') then
            raise exception using message = 'Katkı yönü yalnız ADD veya REMOVE olabilir.';
        end if;

        v_contrib_occurred_on := (p_payload ->> 'occurred_on')::date;
        if v_contrib_occurred_on is null then
            raise exception using message = 'Katkı tarihi zorunludur.';
        end if;

        if (p_payload ? 'note') and (p_payload ->> 'note') is not null then
            v_contrib_note := trim(p_payload ->> 'note');
            if length(v_contrib_note) = 0 or length(v_contrib_note) > 500 then
                raise exception using message = 'Katkı notu boş olamaz ve en fazla 500 karakter olabilir.';
            end if;
        else
            v_contrib_note := null;
        end if;

        -- Parent Goal kilitleme ve doğrulama
        select * into v_goal
          from public.goals
         where id = v_contrib_goal_id
           and user_id = actor_id
           and workspace_id is null
           and deleted_at is null
           for update;

        if not found then
            select pg_catalog.to_jsonb(g.*) into current_row
              from public.goals as g
             where g.id = v_contrib_goal_id
               and g.user_id = actor_id
               and g.workspace_id is null;
        elsif v_goal.version <> p_base_version then
            current_row := pg_catalog.to_jsonb(v_goal);
        else
            if v_goal.currency <> v_contrib_currency then
                raise exception using message = 'Katkı para birimi hedef para birimi ile eşleşmelidir.';
            end if;

            if v_contrib_direction = 'ADD' then
                v_new_goal_amount := v_goal.current_amount_minor + v_contrib_amount;
            else -- REMOVE
                if v_goal.current_amount_minor < v_contrib_amount then
                    raise exception using message = 'Hedef birikim bakiyesi eksiye düşemez.';
                end if;
                v_new_goal_amount := v_goal.current_amount_minor - v_contrib_amount;
            end if;

            insert into public.goal_contributions (
                id, goal_id, amount_minor, currency, direction, occurred_on, note, created_at
            ) values (
                entity_id,
                v_contrib_goal_id,
                v_contrib_amount,
                v_contrib_currency,
                v_contrib_direction,
                v_contrib_occurred_on,
                v_contrib_note,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning * into v_inserted_contrib;

            if found then
                update public.goals
                   set current_amount_minor = v_new_goal_amount
                 where id = v_contrib_goal_id
                returning * into v_updated_goal;

                written_row := pg_catalog.jsonb_build_object(
                    'contribution', pg_catalog.to_jsonb(v_inserted_contrib),
                    'goal', pg_catalog.to_jsonb(v_updated_goal)
                );
            end if;
        end if;

    elsif p_entity_type = 'DEBT' then
        if p_operation in ('CREATE', 'UPDATE') then
            if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
               or nullif(p_payload ->> 'workspace_id', '') is not null then
                raise insufficient_privilege using message = 'Borç/alacak sahipliği reddedildi.';
            end if;

            v_debt_title := nullif(trim(p_payload ->> 'title'), '');
            if v_debt_title is null or length(v_debt_title) > 500 then
                raise exception using message = 'Başlık geçerli olmalıdır (1-500 karakter).';
            end if;

            v_debt_amount := (p_payload ->> 'amount_minor')::bigint;
            if v_debt_amount is null or v_debt_amount <= 0 then
                raise exception using message = 'Borç/alacak tutarı sıfırdan büyük olmalıdır.';
            end if;

            v_debt_currency := coalesce(nullif(p_payload ->> 'currency', ''), 'TRY');
            if v_debt_currency not in ('TRY', 'USD', 'EUR') then
                raise exception using message = 'Desteklenmeyen para birimi.';
            end if;

            v_debt_type := p_payload ->> 'type';
            if v_debt_type not in ('DEBT', 'RECEIVABLE') then
                raise exception using message = 'Tür yalnız DEBT veya RECEIVABLE olabilir.';
            end if;

            v_debt_due_date := (p_payload ->> 'due_date')::date;
            if v_debt_due_date is null then
                raise exception using message = 'Vade tarihi zorunludur.';
            end if;

            if (p_payload ? 'description') and (p_payload ->> 'description') is not null then
                v_debt_description := trim(p_payload ->> 'description');
                if length(v_debt_description) = 0 or length(v_debt_description) > 500 then
                    raise exception using message = 'Açıklama boş olamaz ve en fazla 500 karakter olabilir.';
                end if;
            else
                v_debt_description := null;
            end if;
        end if;

        if p_operation = 'CREATE' then
            v_debt_status := coalesce(nullif(p_payload ->> 'status', ''), 'OPEN');
            if v_debt_status <> 'OPEN' then
                raise exception using message = 'Yeni borç/alacak durumu yalnız OPEN olabilir.';
            end if;

            insert into public.debts (
                id, user_id, workspace_id, title, amount_minor, currency,
                type, due_date, status, description, created_at
            ) values (
                entity_id,
                actor_id,
                null,
                v_debt_title,
                v_debt_amount,
                v_debt_currency,
                v_debt_type,
                v_debt_due_date,
                'OPEN',
                v_debt_description,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(debts.*) into written_row;
        elsif p_operation = 'DELETE' then
            select * into v_debt
              from public.debts
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               for update;

            if not found or v_debt.deleted_at is not null then
                -- not found
            elsif v_debt.version <> p_base_version then
                current_row := pg_catalog.to_jsonb(v_debt);
            else
                update public.debts
                   set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                 where id = entity_id
                returning pg_catalog.to_jsonb(debts.*) into written_row;

                -- Canlı ödemeleri de tombstone yap
                update public.debt_payments
                   set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now())
                 where debt_id = entity_id
                   and deleted_at is null;
            end if;
        else -- UPDATE
            select * into v_debt
              from public.debts
             where id = entity_id
               and user_id = actor_id
               and workspace_id is null
               and deleted_at is null
               for update;

            if not found then
                -- not found
            elsif v_debt.version <> p_base_version then
                current_row := pg_catalog.to_jsonb(v_debt);
            else
                if v_debt_currency <> v_debt.currency then
                    raise exception using message = 'Borç/alacak para birimi değiştirilemez.';
                end if;

                if (p_payload ? 'status') and ((p_payload ->> 'status') <> v_debt.status) then
                    raise exception using message = 'Borç/alacak durumu doğrudan değiştirilemez; ödemelere göre türetilir.';
                end if;

                select coalesce(sum(amount_minor), 0) into v_total_paid
                  from public.debt_payments
                 where debt_id = entity_id
                   and deleted_at is null;

                if v_debt_amount < v_total_paid then
                    raise exception using message = 'Borç/alacak anapara tutarı mevcut canlı ödemelerin toplamından küçük olamaz.';
                end if;

                v_new_debt_status := case when v_total_paid = v_debt_amount then 'SETTLED' else 'OPEN' end;

                update public.debts
                   set title = v_debt_title,
                       amount_minor = v_debt_amount,
                       type = v_debt_type,
                       due_date = v_debt_due_date,
                       status = v_new_debt_status,
                       description = v_debt_description,
                       deleted_at = null
                 where id = entity_id
                returning pg_catalog.to_jsonb(debts.*) into written_row;
            end if;
        end if;

        if written_row is null and current_row is null then
            select pg_catalog.to_jsonb(d.*) into current_row
              from public.debts as d
             where d.id = entity_id
               and d.user_id = actor_id
               and d.workspace_id is null;
        end if;

    else -- DEBT_PAYMENT
        if p_operation <> 'CREATE' then
            raise exception using message = 'DEBT_PAYMENT için yalnız CREATE işlemi desteklenir.';
        end if;

        v_payment_debt_id := (p_payload ->> 'debt_id')::uuid;
        if v_payment_debt_id is null then
            raise exception using message = 'Borç/alacak ID (debt_id) zorunludur.';
        end if;

        v_payment_amount := (p_payload ->> 'amount_minor')::bigint;
        if v_payment_amount is null or v_payment_amount <= 0 then
            raise exception using message = 'Ödeme tutarı sıfırdan büyük olmalıdır.';
        end if;

        v_payment_currency := coalesce(nullif(p_payload ->> 'currency', ''), 'TRY');
        if v_payment_currency not in ('TRY', 'USD', 'EUR') then
            raise exception using message = 'Desteklenmeyen para birimi.';
        end if;

        v_payment_paid_on := (p_payload ->> 'paid_on')::date;
        if v_payment_paid_on is null then
            raise exception using message = 'Ödeme tarihi zorunludur.';
        end if;

        -- Parent Debt kilitleme ve doğrulama
        select * into v_debt
          from public.debts
         where id = v_payment_debt_id
           and user_id = actor_id
           and workspace_id is null
           and deleted_at is null
           for update;

        if not found then
            select pg_catalog.to_jsonb(d.*) into current_row
              from public.debts as d
             where d.id = v_payment_debt_id
               and d.user_id = actor_id
               and d.workspace_id is null;
        elsif v_debt.version <> p_base_version then
            current_row := pg_catalog.to_jsonb(v_debt);
        else
            if v_debt.currency <> v_payment_currency then
                raise exception using message = 'Ödeme para birimi borç/alacak para birimi ile eşleşmelidir.';
            end if;

            select coalesce(sum(amount_minor), 0) into v_total_paid
              from public.debt_payments
             where debt_id = v_payment_debt_id
               and deleted_at is null;

            if (v_total_paid + v_payment_amount) > v_debt.amount_minor then
                raise exception using message = 'Ödeme toplamı ana borç/alacak tutarını aşamaz.';
            end if;

            insert into public.debt_payments (
                id, debt_id, amount_minor, currency, paid_on, created_at
            ) values (
                entity_id,
                v_payment_debt_id,
                v_payment_amount,
                v_payment_currency,
                v_payment_paid_on,
                coalesce((p_payload ->> 'created_at')::timestamptz, pg_catalog.timezone('utc'::text, pg_catalog.now()))
            )
            on conflict (id) do nothing
            returning * into v_inserted_payment;

            if found then
                v_new_debt_status := case when (v_total_paid + v_payment_amount) = v_debt.amount_minor then 'SETTLED' else 'OPEN' end;

                update public.debts
                   set status = v_new_debt_status
                 where id = v_payment_debt_id
                returning * into v_updated_debt;

                written_row := pg_catalog.jsonb_build_object(
                    'payment', pg_catalog.to_jsonb(v_inserted_payment),
                    'debt', pg_catalog.to_jsonb(v_updated_debt)
                );
            end if;
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

-- İzinleri yapılandır
revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from public;
revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb) from anon;
grant execute on function public.sync_write_v2(text, text, text, bigint, jsonb) to authenticated;

commit;
