-- TASLAK: Kişisel Asset şeması ve sync_write_v2 genişletmesi. Yalnız yerel/staging için.
begin;

create table public.assets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    type text not null,
    current_value_minor bigint not null,
    currency text not null,
    quantity_unscaled bigint,
    quantity_scale integer,
    purchase_unit_price_minor bigint,
    tracking_symbol text,
    auto_track boolean not null default false,
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    deleted_at timestamptz,
    version bigint not null default 1,
    constraint assets_name_check check (length(trim(name)) between 1 and 500),
    constraint assets_type_check check (type in ('CASH', 'CRYPTO', 'STOCKS', 'REAL_ESTATE', 'PRECIOUS_METALS', 'OTHER')),
    constraint assets_current_value_check check (current_value_minor >= 0),
    constraint assets_currency_check check (currency in ('TRY', 'USD', 'EUR')),
    constraint assets_quantity_pair_check check ((quantity_unscaled is null) = (quantity_scale is null)),
    constraint assets_quantity_check check (quantity_unscaled is null or (quantity_unscaled >= 0 and quantity_scale between 0 and 12)),
    constraint assets_purchase_price_check check (purchase_unit_price_minor is null or purchase_unit_price_minor >= 0),
    constraint assets_tracking_check check (not auto_track or length(trim(coalesce(tracking_symbol, ''))) > 0),
    constraint assets_tracking_symbol_length_check check (tracking_symbol is null or length(tracking_symbol) <= 64),
    constraint assets_version_positive check (version > 0)
);

create index idx_assets_sync_cursor on public.assets (updated_at, id);
create index idx_assets_active_owner on public.assets (user_id, updated_at, id) where deleted_at is null;
create trigger assets_set_server_metadata before update on public.assets
    for each row execute function public.sync_set_server_metadata();

alter table public.assets enable row level security;
revoke all on table public.assets from public, anon, authenticated;
grant select on table public.assets to authenticated;
create policy assets_select_personal_v1 on public.assets for select to authenticated
    using (user_id = (select auth.uid()));

alter table public.sync_operations_receipts drop constraint if exists sync_operations_receipts_entity_type_check;
alter table public.sync_operations_receipts add constraint sync_operations_receipts_entity_type_check
    check (entity_type in ('PROFILE','CATEGORY','TRANSACTION','BUDGET','RECURRING_TRANSACTION','SUBSCRIPTION','GOAL','GOAL_CONTRIBUTION','DEBT','DEBT_PAYMENT','WORKSPACE','WORKSPACE_MEMBER','WORKSPACE_INVITATION','ASSET'));

alter function public.sync_write_v2(text, text, text, bigint, jsonb) rename to sync_write_v2_before_assets;

create function public.sync_write_v2(
    p_operation_id text, p_entity_type text, p_operation text, p_base_version bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    actor_id uuid := auth.uid();
    entity_id uuid;
    written_row jsonb;
    current_row jsonb;
    fingerprint text;
    receipt_fingerprint text;
    receipt_record jsonb;
begin
    if p_entity_type <> 'ASSET' then
        return public.sync_write_v2_before_assets(p_operation_id, p_entity_type, p_operation, p_base_version, p_payload);
    end if;
    if actor_id is null then raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.'; end if;
    if p_operation_id is null or not (p_operation_id ~ '^[0-9a-f]{32}$') then raise exception using message = 'Geçersiz operation_id formatı.'; end if;
    if p_operation not in ('CREATE','UPDATE','DELETE') then raise exception using message = 'Desteklenmeyen senkronizasyon işlemi.'; end if;
    if (p_operation = 'CREATE' and p_base_version is not null) or
       (p_operation in ('UPDATE','DELETE') and (p_base_version is null or p_base_version < 1)) then
        raise exception using message = 'Geçersiz base_version.';
    end if;
    if p_payload is null or pg_catalog.jsonb_typeof(p_payload) <> 'object' or
       coalesce(p_payload ->> 'id','') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        raise exception using message = 'Asset payload geçersiz.';
    end if;
    entity_id := (p_payload ->> 'id')::uuid;
    if p_operation in ('CREATE','UPDATE') and nullif(p_payload ->> 'user_id','')::uuid <> actor_id then
        raise insufficient_privilege using message = 'Asset sahipliği reddedildi.';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(actor_id::text), pg_catalog.hashtext(p_operation_id));
    fingerprint := pg_catalog.encode(extensions.digest(actor_id::text || '|' || p_entity_type || '|' || entity_id::text || '|' || p_operation || '|' || coalesce(p_base_version::text,'null') || '|' || p_payload::text, 'sha256'), 'hex');
    select request_fingerprint, response_record into receipt_fingerprint, receipt_record
      from public.sync_operations_receipts where user_id = actor_id and operation_id = p_operation_id;
    if found then
        if receipt_fingerprint <> fingerprint then raise exception using message = 'Idempotency ihlali: Aynı operation_id farklı payload ile tekrarlandı.'; end if;
        return pg_catalog.jsonb_build_object('status','APPLIED','record',receipt_record);
    end if;
    if p_operation = 'CREATE' then
        insert into public.assets(id,user_id,name,type,current_value_minor,currency,quantity_unscaled,quantity_scale,purchase_unit_price_minor,tracking_symbol,auto_track,created_at)
        values(entity_id,actor_id,p_payload->>'name',p_payload->>'type',(p_payload->>'current_value_minor')::bigint,p_payload->>'currency',nullif(p_payload->>'quantity_unscaled','')::bigint,nullif(p_payload->>'quantity_scale','')::integer,nullif(p_payload->>'purchase_unit_price_minor','')::bigint,nullif(p_payload->>'tracking_symbol',''),coalesce((p_payload->>'auto_track')::boolean,false),coalesce((p_payload->>'created_at')::timestamptz,pg_catalog.timezone('utc'::text,pg_catalog.now())))
        on conflict(id) do nothing returning pg_catalog.to_jsonb(assets.*) into written_row;
    elsif p_operation = 'DELETE' then
        update public.assets set deleted_at=pg_catalog.timezone('utc'::text,pg_catalog.now())
         where id=entity_id and user_id=actor_id and version=p_base_version returning pg_catalog.to_jsonb(assets.*) into written_row;
    else
        update public.assets set name=p_payload->>'name',type=p_payload->>'type',current_value_minor=(p_payload->>'current_value_minor')::bigint,currency=p_payload->>'currency',quantity_unscaled=nullif(p_payload->>'quantity_unscaled','')::bigint,quantity_scale=nullif(p_payload->>'quantity_scale','')::integer,purchase_unit_price_minor=nullif(p_payload->>'purchase_unit_price_minor','')::bigint,tracking_symbol=nullif(p_payload->>'tracking_symbol',''),auto_track=coalesce((p_payload->>'auto_track')::boolean,false),deleted_at=null
         where id=entity_id and user_id=actor_id and version=p_base_version returning pg_catalog.to_jsonb(assets.*) into written_row;
    end if;
    if written_row is null then select pg_catalog.to_jsonb(a.*) into current_row from public.assets a where a.id=entity_id and a.user_id=actor_id; end if;
    if written_row is not null then
        insert into public.sync_operations_receipts(operation_id,user_id,entity_type,entity_id,operation_type,base_version,request_fingerprint,result_status,applied_version,response_record,applied_at)
        values(p_operation_id,actor_id,'ASSET',entity_id,p_operation,p_base_version,fingerprint,'APPLIED',(written_row->>'version')::bigint,written_row,pg_catalog.timezone('utc'::text,pg_catalog.now()));
        return pg_catalog.jsonb_build_object('status','APPLIED','record',written_row);
    end if;
    if current_row is not null then return pg_catalog.jsonb_build_object('status','CONFLICT','record',current_row); end if;
    return pg_catalog.jsonb_build_object('status','NOT_FOUND','record',null);
end $$;

revoke execute on function public.sync_write_v2(text,text,text,bigint,jsonb) from public, anon;
grant execute on function public.sync_write_v2(text,text,text,bigint,jsonb) to authenticated;
revoke execute on function public.sync_write_v2_before_assets(text,text,text,bigint,jsonb) from public, anon, authenticated;

commit;
