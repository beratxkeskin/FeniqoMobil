-- TASLAK: baseVersion kontrollü V1 yazma sözleşmesi. Yalnız yerel/staging için.
begin;

create or replace function public.sync_set_server_metadata()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at := timezone('utc'::text, now());
    new.version := old.version + 1;
    return new;
end
$$;

create trigger profiles_set_server_metadata
before update on public.profiles
for each row execute function public.sync_set_server_metadata();

create trigger categories_set_server_metadata
before update on public.categories
for each row execute function public.sync_set_server_metadata();

create trigger transactions_set_server_metadata
before update on public.transactions
for each row execute function public.sync_set_server_metadata();

create or replace function public.sync_write_v1(
    p_entity_type text,
    p_operation text,
    p_base_version bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
    actor_id uuid := auth.uid();
    written_row jsonb;
    current_row jsonb;
    entity_id uuid;
begin
    if actor_id is null then
        raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.';
    end if;

    if p_entity_type not in ('PROFILE', 'CATEGORY', 'TRANSACTION') then
        raise exception 'Desteklenmeyen entity türü: %', p_entity_type;
    end if;

    if p_operation not in ('CREATE', 'UPDATE', 'DELETE') then
        raise exception 'Desteklenmeyen senkronizasyon işlemi: %', p_operation;
    end if;

    entity_id := nullif(p_payload ->> 'id', '')::uuid;
    if entity_id is null then
        raise exception 'Payload id alanı zorunludur.';
    end if;

    if p_entity_type = 'PROFILE' then
        if entity_id <> actor_id or p_operation = 'DELETE' then
            raise insufficient_privilege using message = 'Profil işlemi reddedildi.';
        end if;

        if p_operation = 'CREATE' and p_base_version is null then
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
                coalesce((p_payload ->> 'created_at')::timestamptz, timezone('utc'::text, now()))
            )
            on conflict (id) do nothing
            returning to_jsonb(profiles.*) into written_row;
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
            returning to_jsonb(profiles.*) into written_row;
        end if;

        if written_row is null then
            select to_jsonb(p.*) into current_row
            from public.profiles as p
            where p.id = actor_id;
        end if;

    elsif p_entity_type = 'CATEGORY' then
        if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
           or nullif(p_payload ->> 'workspace_id', '') is not null
           or coalesce((p_payload ->> 'is_default')::boolean, false) then
            raise insufficient_privilege using message = 'V1 kategori sahipliği reddedildi.';
        end if;

        if p_operation = 'CREATE' and p_base_version is null then
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
                coalesce((p_payload ->> 'created_at')::timestamptz, timezone('utc'::text, now()))
            )
            on conflict (id) do nothing
            returning to_jsonb(categories.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.categories
            set deleted_at = timezone('utc'::text, now())
            where id = entity_id
              and user_id = actor_id
              and workspace_id is null
              and is_default = false
              and version = p_base_version
            returning to_jsonb(categories.*) into written_row;
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
            returning to_jsonb(categories.*) into written_row;
        end if;

        if written_row is null then
            select to_jsonb(c.*) into current_row
            from public.categories as c
            where c.id = entity_id
              and c.user_id = actor_id
              and c.workspace_id is null;
        end if;

    else
        if nullif(p_payload ->> 'user_id', '')::uuid <> actor_id
           or nullif(p_payload ->> 'workspace_id', '') is not null then
            raise insufficient_privilege using message = 'V1 işlem sahipliği reddedildi.';
        end if;

        if p_operation = 'CREATE' and p_base_version is null then
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
                coalesce((p_payload ->> 'created_at')::timestamptz, timezone('utc'::text, now()))
            )
            on conflict (id) do nothing
            returning to_jsonb(transactions.*) into written_row;
        elsif p_operation = 'DELETE' then
            update public.transactions
            set deleted_at = timezone('utc'::text, now())
            where id = entity_id
              and user_id = actor_id
              and workspace_id is null
              and version = p_base_version
            returning to_jsonb(transactions.*) into written_row;
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
            returning to_jsonb(transactions.*) into written_row;
        end if;

        if written_row is null then
            select to_jsonb(t.*) into current_row
            from public.transactions as t
            where t.id = entity_id
              and t.user_id = actor_id
              and t.workspace_id is null;
        end if;
    end if;

    if written_row is not null then
        return jsonb_build_object('status', 'APPLIED', 'record', written_row);
    end if;

    if current_row is not null then
        return jsonb_build_object('status', 'CONFLICT', 'record', current_row);
    end if;

    return jsonb_build_object('status', 'NOT_FOUND', 'record', null);
end
$$;

revoke execute on function public.sync_write_v1(text, text, bigint, jsonb) from public;
revoke execute on function public.sync_write_v1(text, text, bigint, jsonb) from anon;
grant execute on function public.sync_write_v1(text, text, bigint, jsonb) to authenticated;

commit;
