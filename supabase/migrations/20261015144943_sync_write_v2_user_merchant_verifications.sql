-- Migration: 20261015144943_sync_write_v2_user_merchant_verifications.sql
-- Sorumluluk: Kişisel merchant doğrulama/engelleme tablosu (public.user_merchant_verifications),
--             indeksler, RLS, GRANT, sync_operations_receipts 15. entity genişletmesi ve
--             sync_write_v2 deterministik RFC 9562 UUIDv8 korumalı dar wrapper mimarisi.

begin;

-- ============================================================================
-- 1. PRE-CHECK KONTROLLERİ
-- ============================================================================
do $$
begin
    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then
        raise exception 'Pre-check failed: authenticated rolü bulunamadı.';
    end if;

    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then
        raise exception 'Pre-check failed: anon rolü bulunamadı.';
    end if;

    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'sync_operations_receipts' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check failed: public.sync_operations_receipts tablosu bulunamadı.';
    end if;

    if not exists (
        select 1 from pg_catalog.pg_proc p
        join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and p.proname = 'sync_write_v2'
    ) then
        raise exception 'Pre-check failed: public.sync_write_v2 fonksiyonu bulunamadı.';
    end if;
end $$;

-- ============================================================================
-- 2. PUBLIC.USER_MERCHANT_VERIFICATIONS TABLOSU VE KISITLAMALARI
-- ============================================================================
create table if not exists public.user_merchant_verifications (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    workspace_id uuid null,
    normalized_alias text not null,
    decision_code text not null,
    merchant_id text null,
    scope_code text not null default 'PERSONAL',
    created_at timestamptz not null default pg_catalog.timezone('utc'::text, pg_catalog.now()),
    updated_at timestamptz not null default pg_catalog.timezone('utc'::text, pg_catalog.now()),
    deleted_at timestamptz null,
    version bigint not null default 1,
    constraint user_merchant_verifications_workspace_null check (workspace_id is null),
    constraint user_merchant_verifications_scope_personal check (scope_code = 'PERSONAL'),
    constraint user_merchant_verifications_version_positive check (version >= 1),
    constraint user_merchant_verifications_alias_length check (pg_catalog.length(normalized_alias) between 1 and 500),
    constraint user_merchant_verifications_alias_canonical check (normalized_alias ~ '^[A-Z0-9]+( [A-Z0-9]+)*$'),
    constraint user_merchant_verifications_alias_not_generic check (normalized_alias not in ('MARKET', 'PREMIUM', 'APPLE', 'SHOP', 'STORE', 'YEMEK')),
    constraint user_merchant_verifications_decision_code check (decision_code in ('ASSIGN', 'BLOCK')),
    constraint user_merchant_verifications_decision_invariant check (
        (decision_code = 'ASSIGN' and merchant_id is not null and pg_catalog.length(pg_catalog.btrim(merchant_id)) > 0)
        or
        (decision_code = 'BLOCK' and merchant_id is null)
    )
);

-- İndeksler
create unique index if not exists idx_user_merchant_verifications_user_alias
    on public.user_merchant_verifications(user_id, normalized_alias)
    where workspace_id is null;

create index if not exists idx_user_merchant_verifications_updated_cursor
    on public.user_merchant_verifications(updated_at, id);

create index if not exists idx_user_merchant_verifications_user_deleted
    on public.user_merchant_verifications(user_id, deleted_at);

-- ============================================================================
-- 3. RLS VE DATA API TABLO YETKİLERİ (DEFENSE-IN-DEPTH)
-- ============================================================================
alter table public.user_merchant_verifications enable row level security;

-- PostgREST Data API exposure: Yalnızca SELECT açık, doğrudan DML kapalı
revoke all on table public.user_merchant_verifications from public, anon, authenticated;
grant select on table public.user_merchant_verifications to authenticated;

drop policy if exists user_merchant_verifications_select_owner on public.user_merchant_verifications;
create policy user_merchant_verifications_select_owner
    on public.user_merchant_verifications
    for select
    to authenticated
    using (user_id = (select auth.uid()));

-- ============================================================================
-- 4. RECEIPT KISITLAMASININ 15 ENTITY TÜRÜ İLE GENİŞLETİLMESİ
-- ============================================================================
alter table public.sync_operations_receipts
    drop constraint if exists sync_operations_receipts_entity_type_check;

alter table public.sync_operations_receipts
    add constraint sync_operations_receipts_entity_type_check
    check (entity_type in (
        'PROFILE',
        'CATEGORY',
        'TRANSACTION',
        'BUDGET',
        'RECURRING_TRANSACTION',
        'SUBSCRIPTION',
        'GOAL',
        'GOAL_CONTRIBUTION',
        'DEBT',
        'DEBT_PAYMENT',
        'WORKSPACE',
        'WORKSPACE_MEMBER',
        'WORKSPACE_INVITATION',
        'ASSET',
        'USER_MERCHANT_VERIFICATION'
    ));

-- ============================================================================
-- 5. DAR RPC WRAPPER VE SYNC_WRITE_V2 MERCHANT DALI
-- ============================================================================
-- Mevcut wrapper fonksiyonunu rename et (gövdesine dokunulmaz)
alter function public.sync_write_v2(text, text, text, bigint, jsonb)
    rename to sync_write_v2_before_user_merchant_verification;

-- Yeni giriş noktası wrapper'ı tanımla
create function public.sync_write_v2(
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
    entity_id uuid;
    v_normalized_alias text;
    v_decision_code text;
    v_merchant_id text;
    v_hash bytea;
    v_bytes bytea;
    v_hex text;
    v_expected_id uuid;
    written_row jsonb;
    current_row jsonb;
    fingerprint text;
    receipt_fingerprint text;
    receipt_record jsonb;
begin
    -- 1. Merchant dışındaki 14 entity türünü eski wrapper'a delege et
    if p_entity_type <> 'USER_MERCHANT_VERIFICATION' then
        return public.sync_write_v2_before_user_merchant_verification(
            p_operation_id, p_entity_type, p_operation, p_base_version, p_payload
        );
    end if;

    -- 2. Kimlik doğrulama
    if actor_id is null then
        raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.';
    end if;

    -- 3. operation_id formatı (32 hex)
    if p_operation_id is null or not (p_operation_id ~ '^[0-9a-f]{32}$') then
        raise exception using message = 'Geçersiz operation_id formatı.';
    end if;

    -- 4. operation türü
    if p_operation not in ('CREATE', 'UPDATE', 'DELETE') then
        raise exception using message = 'Desteklenmeyen senkronizasyon işlemi.';
    end if;

    -- 5. base_version sözleşmesi
    if (p_operation = 'CREATE' and p_base_version is not null) or
       (p_operation in ('UPDATE', 'DELETE') and (p_base_version is null or p_base_version < 1)) then
        raise exception using message = 'Geçersiz base_version.';
    end if;

    -- 6. Payload temel format kontrolleri
    if p_payload is null or pg_catalog.jsonb_typeof(p_payload) <> 'object' or
       coalesce(p_payload ->> 'id', '') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        raise exception using message = 'Merchant verification payload geçersiz.';
    end if;

    entity_id := (p_payload ->> 'id')::uuid;

    -- user_id canonical UUID formatı kontrolü (lowercase canonical)
    if coalesce(p_payload ->> 'user_id', '') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        raise exception using message = 'Merchant verification user_id geçersiz.';
    end if;

    -- 7. Sahiplik (BOLA) kontrolü - CREATE, UPDATE, DELETE için zorunlu
    if (p_payload ->> 'user_id')::uuid <> actor_id then
        raise insufficient_privilege using message = 'Doğrulama sahipliği reddedildi.';
    end if;

    -- workspace_id zorunlu ve JSON null kontrolü
    if not (p_payload ? 'workspace_id') or
       pg_catalog.jsonb_typeof(p_payload -> 'workspace_id') <> 'null' then
        raise exception using message = 'Kişisel doğrulamada workspace_id null olmalıdır.';
    end if;

    -- scope_code = 'PERSONAL' kontrolü
    if coalesce(p_payload ->> 'scope_code', '') <> 'PERSONAL' then
        raise exception using message = 'Yalnızca PERSONAL scope desteklenir.';
    end if;

    -- 8. Kanonik alias doğrulaması ve Deterministik ID hesabı (CREATE, UPDATE, DELETE tamamında zorunlu)
    v_normalized_alias := p_payload ->> 'normalized_alias';
    if v_normalized_alias is null or
       not (v_normalized_alias ~ '^[A-Z0-9]+( [A-Z0-9]+)*$') or
       pg_catalog.length(v_normalized_alias) > 500 or
       v_normalized_alias in ('MARKET', 'PREMIUM', 'APPLE', 'SHOP', 'STORE', 'YEMEK') then
        raise exception using message = 'Geçersiz kanonik alias biçimi.';
    end if;

    -- Sunucu tarafı deterministik RFC 9562 UUIDv8 hesaplaması (NUL bytea ile)
    v_hash := extensions.digest(
        pg_catalog.convert_to('feniqo:user-merchant-verification:v1', 'UTF8')
        || pg_catalog.decode('00', 'hex')
        || pg_catalog.convert_to(actor_id::text, 'UTF8')
        || pg_catalog.decode('00', 'hex')
        || pg_catalog.convert_to(v_normalized_alias, 'UTF8'),
        'sha256'
    );

    v_bytes := pg_catalog.substring(v_hash, 1, 16);
    -- Octet 6 version = 8 (0b1000xxxx)
    v_bytes := pg_catalog.set_byte(v_bytes, 6, (pg_catalog.get_byte(v_bytes, 6) & 15) | 128);
    -- Octet 8 variant = 1 (0b10xxxxxx)
    v_bytes := pg_catalog.set_byte(v_bytes, 8, (pg_catalog.get_byte(v_bytes, 8) & 63) | 128);

    v_hex := pg_catalog.encode(v_bytes, 'hex');
    v_expected_id := (
        pg_catalog.substring(v_hex, 1, 8) || '-' ||
        pg_catalog.substring(v_hex, 9, 4) || '-' ||
        pg_catalog.substring(v_hex, 13, 4) || '-' ||
        pg_catalog.substring(v_hex, 17, 4) || '-' ||
        pg_catalog.substring(v_hex, 21, 12)
    )::uuid;

    -- Deterministik ID eşleşme doğrulaması (Definitive validation failure)
    if entity_id <> v_expected_id then
        raise exception using message = 'Geçersiz deterministik doğrulama kimliği.';
    end if;

    -- 9. Karar ve invariant kontrolleri (UPDATE veya CREATE için)
    if p_operation in ('CREATE', 'UPDATE') then
        v_decision_code := p_payload ->> 'decision_code';
        v_merchant_id := nullif(p_payload ->> 'merchant_id', '');

        if v_decision_code not in ('ASSIGN', 'BLOCK') then
            raise exception using message = 'Geçersiz karar kodu.';
        end if;

        if v_decision_code = 'ASSIGN' and v_merchant_id is null then
            raise exception using message = 'ASSIGN kararında merchant_id boş olamaz.';
        end if;

        if v_decision_code = 'BLOCK' and v_merchant_id is not null then
            raise exception using message = 'BLOCK kararında merchant_id null olmalıdır.';
        end if;
    end if;

    -- 12. Idempotency kilit ve receipt kontrolü
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(actor_id::text),
        pg_catalog.hashtext(p_operation_id)
    );

    fingerprint := pg_catalog.encode(
        extensions.digest(
            actor_id::text || '|' || p_entity_type || '|' || entity_id::text || '|' ||
            p_operation || '|' || coalesce(p_base_version::text, 'null') || '|' || p_payload::text,
            'sha256'
        ),
        'hex'
    );

    select request_fingerprint, response_record into receipt_fingerprint, receipt_record
      from public.sync_operations_receipts
     where user_id = actor_id and operation_id = p_operation_id;

    if found then
        if receipt_fingerprint <> fingerprint then
            raise exception using message = 'Idempotency ihlali: Aynı operation_id farklı payload ile tekrarlandı.';
        end if;
        return pg_catalog.jsonb_build_object('status', 'APPLIED', 'record', receipt_record);
    end if;

    -- 13. Veritabanı yazma mutasyonu
    if p_operation = 'CREATE' then
        begin
            insert into public.user_merchant_verifications (
                id, user_id, workspace_id, normalized_alias, decision_code, merchant_id, scope_code,
                created_at, updated_at, deleted_at, version
            ) values (
                entity_id, actor_id, null, v_normalized_alias, v_decision_code, v_merchant_id, 'PERSONAL',
                pg_catalog.timezone('utc'::text, pg_catalog.now()),
                pg_catalog.timezone('utc'::text, pg_catalog.now()),
                null, 1
            )
            on conflict (id) do nothing
            returning pg_catalog.to_jsonb(user_merchant_verifications.*) into written_row;
        exception
            when unique_violation then
                -- Eşzamanlı alias yarışında raw 23505 sızdırılmaz; conflict olarak döner
                written_row := null;
        end;
    elsif p_operation = 'DELETE' then
        update public.user_merchant_verifications
           set deleted_at = pg_catalog.timezone('utc'::text, pg_catalog.now()),
               updated_at = pg_catalog.timezone('utc'::text, pg_catalog.now()),
               version = version + 1
         where id = entity_id and user_id = actor_id and version = p_base_version
        returning pg_catalog.to_jsonb(user_merchant_verifications.*) into written_row;
    else
        -- UPDATE (Reactivation durumunda deleted_at null yapılır)
        update public.user_merchant_verifications
           set decision_code = v_decision_code,
               merchant_id = v_merchant_id,
               deleted_at = null,
               updated_at = pg_catalog.timezone('utc'::text, pg_catalog.now()),
               version = version + 1
         where id = entity_id and user_id = actor_id and version = p_base_version
        returning pg_catalog.to_jsonb(user_merchant_verifications.*) into written_row;
    end if;

    -- 14. Durum değerlendirmesi
    if written_row is null then
        select pg_catalog.to_jsonb(u.*) into current_row
          from public.user_merchant_verifications u
         where u.user_id = actor_id
           and (u.id = entity_id or (v_normalized_alias is not null and u.normalized_alias = v_normalized_alias));
    end if;

    if written_row is not null then
        insert into public.sync_operations_receipts (
            operation_id, user_id, entity_type, entity_id, operation_type, base_version,
            request_fingerprint, result_status, applied_version, response_record, applied_at
        ) values (
            p_operation_id, actor_id, 'USER_MERCHANT_VERIFICATION', entity_id, p_operation, p_base_version,
            fingerprint, 'APPLIED', (written_row ->> 'version')::bigint, written_row,
            pg_catalog.timezone('utc'::text, pg_catalog.now())
        );

        return pg_catalog.jsonb_build_object('status', 'APPLIED', 'record', written_row);
    end if;

    if current_row is not null then
        return pg_catalog.jsonb_build_object('status', 'CONFLICT', 'record', current_row);
    end if;

    return pg_catalog.jsonb_build_object('status', 'NOT_FOUND', 'record', null);
end $$;

-- Yetkilendirme izolasyonu
revoke execute on function public.sync_write_v2_before_user_merchant_verification(text, text, text, bigint, jsonb)
    from public, anon, authenticated;

revoke execute on function public.sync_write_v2(text, text, text, bigint, jsonb)
    from public, anon;

grant execute on function public.sync_write_v2(text, text, text, bigint, jsonb)
    to authenticated;

commit;
