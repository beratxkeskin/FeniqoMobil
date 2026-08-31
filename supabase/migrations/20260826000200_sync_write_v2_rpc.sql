-- TASLAK: Sync Write V2 Idempotent Conditional Write RPC. Yalnız yerel/staging için.
begin;

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
    if p_entity_type not in ('PROFILE', 'CATEGORY', 'TRANSACTION') then
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

    else -- TRANSACTION
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
