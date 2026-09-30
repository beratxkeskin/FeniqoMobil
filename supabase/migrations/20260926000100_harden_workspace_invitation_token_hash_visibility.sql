-- Migration: 20260926000100_harden_workspace_invitation_token_hash_visibility.sql
-- Sorumluluk: workspace_invitations.token_hash kolonunun authenticated rolünce doğrudan
-- okunmasını engellemek; tablo düzeyi SELECT yerine yalnızca açık güvenli metadata kolonlarına
-- column-level SELECT yetkisi vermek ve RLS üyelik filtresini korumak.
-- service_role ACL'sine dokunulmaz (bu migration service_role'a GRANT veya REVOKE uygulamaz).

begin;

-- ============================================================================
-- 1. PRE-CHECKS (Fail-Closed Ön Koşul Doğrulaması)
-- ============================================================================
do $$
declare
    v_rls_enabled boolean;
    v_token_hash_col_exists boolean;
    v_policy_rec record;
    v_canonical_qual text;
begin
    -- A. Tablo ve token_hash kolonunun varlığı
    select exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'workspace_invitations'
          and column_name = 'token_hash'
    ) into v_token_hash_col_exists;

    if not v_token_hash_col_exists then
        raise exception 'Pre-check FAIL: public.workspace_invitations.token_hash kolonu bulunamadı.';
    end if;

    -- B. RLS aktifliği
    select relrowsecurity into v_rls_enabled
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'workspace_invitations';

    if v_rls_enabled is not true then
        raise exception 'Pre-check FAIL: public.workspace_invitations üzerinde RLS aktif değil.';
    end if;

    -- C. workspace_invitations_member_select policy tam sözleşme doğrulaması
    select
        schemaname,
        tablename,
        policyname,
        permissive,
        roles,
        cmd,
        qual,
        with_check
    into v_policy_rec
    from pg_catalog.pg_policies
    where schemaname = 'public'
      and tablename = 'workspace_invitations'
      and policyname = 'workspace_invitations_member_select';

    if v_policy_rec.policyname is null then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select politikası bulunamadı.';
    end if;

    if v_policy_rec.permissive <> 'PERMISSIVE' then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select permissive değeri PERMISSIVE değil: %', v_policy_rec.permissive;
    end if;

    if v_policy_rec.cmd <> 'SELECT' then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select cmd değeri SELECT değil: %', v_policy_rec.cmd;
    end if;

    if v_policy_rec.roles <> '{authenticated}'::name[] and v_policy_rec.roles <> array['authenticated']::name[] then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select roller yalnızca {authenticated} olmalı: %', v_policy_rec.roles;
    end if;

    if v_policy_rec.with_check is not null then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select with_check null olmalı: %', v_policy_rec.with_check;
    end if;

    -- Canonical predicate kontrolü (kesin eşitlik)
    v_canonical_qual := regexp_replace(v_policy_rec.qual, '\s+', '', 'g');
    v_canonical_qual := replace(v_canonical_qual, 'public.', '');
    if v_canonical_qual ~ '^\(.*\)$' then
        v_canonical_qual := regexp_replace(v_canonical_qual, '^\((.*)\)$', '\1');
    end if;

    if v_canonical_qual <> 'is_workspace_member(workspace_id)' then
        raise exception 'Pre-check FAIL: workspace_invitations_member_select qual tam canonical eşleşmedi: beklenen "is_workspace_member(workspace_id)", alınan "%" (ham: "%")',
            v_canonical_qual, v_policy_rec.qual;
    end if;
end $$;

-- ============================================================================
-- 2. PRIVILEGE HARDENING (Tablo Düzeyi Revoke & Güvenli Kolon İzinleri)
-- ============================================================================

-- A. authenticated, anon ve PUBLIC rollerinden tablo düzeyindeki tüm yetkileri kaldır
revoke all on table public.workspace_invitations from public;
revoke all on table public.workspace_invitations from anon;
revoke all on table public.workspace_invitations from authenticated;

-- B. Yalnızca açık güvenli kolon allowlist'ine authenticated SELECT izni ver (token_hash kesinlikle hariç)
grant select (
    id,
    workspace_id,
    inviter_id,
    role_code,
    created_at,
    expires_at,
    max_uses,
    uses_count,
    updated_at,
    deleted_at,
    version
) on table public.workspace_invitations to authenticated;

-- C. service_role ACL'sine dokunulmaz (bu migration service_role'a GRANT veya REVOKE uygulamaz)

-- ============================================================================
-- 3. POST-CHECKS (Fail-Closed Sonuç Doğrulaması)
-- ============================================================================
do $$
declare
    v_table_privs text[] := array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'];
    v_roles text[] := array['authenticated', 'anon', 'public'];
    v_role text;
    v_priv text;
    v_safe_cols text[] := array[
        'id',
        'workspace_id',
        'inviter_id',
        'role_code',
        'created_at',
        'expires_at',
        'max_uses',
        'uses_count',
        'updated_at',
        'deleted_at',
        'version'
    ];
    v_col_name text;
    v_rls_enabled boolean;
    v_policy_rec record;
    v_canonical_qual text;
begin
    -- 1. authenticated, anon ve public rollerinin hiçbir tablo düzeyi yetkisi olmamalı
    foreach v_role in array v_roles loop
        foreach v_priv in array v_table_privs loop
            if has_table_privilege(v_role, 'public.workspace_invitations', v_priv) then
                raise exception 'Post-check FAIL: % rolü public.workspace_invitations üzerinde % yetkisine sahip.', v_role, v_priv;
            end if;
        end loop;
    end loop;

    -- 2. authenticated token_hash SELECT yetkisi kesinlikle FALSE olmalı
    if has_column_privilege('authenticated', 'public.workspace_invitations', 'token_hash', 'SELECT') then
        raise exception 'Post-check FAIL: authenticated rolü token_hash kolonunda SELECT yetkisine sahip.';
    end if;

    -- 3. Güvenli allowlist kolonlarının tamamında SELECT yetkisi TRUE olmalı
    foreach v_col_name in array v_safe_cols loop
        if not has_column_privilege('authenticated', 'public.workspace_invitations', v_col_name, 'SELECT') then
            raise exception 'Post-check FAIL: authenticated rolünün güvenli kolonda (%) SELECT yetkisi eksik.', v_col_name;
        end if;
    end loop;

    -- 4. RLS aktifliği korunmuş olmalı
    select relrowsecurity into v_rls_enabled
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'workspace_invitations';

    if v_rls_enabled is not true then
        raise exception 'Post-check FAIL: workspace_invitations RLS devre dışı.';
    end if;

    -- 5. workspace_invitations_member_select politikası tam sözleşmeyle korunmuş olmalı
    select
        schemaname,
        tablename,
        policyname,
        permissive,
        roles,
        cmd,
        qual,
        with_check
    into v_policy_rec
    from pg_catalog.pg_policies
    where schemaname = 'public'
      and tablename = 'workspace_invitations'
      and policyname = 'workspace_invitations_member_select';

    if v_policy_rec.policyname is null then
        raise exception 'Post-check FAIL: workspace_invitations_member_select politikası kayıp.';
    end if;

    if v_policy_rec.permissive <> 'PERMISSIVE' then
        raise exception 'Post-check FAIL: workspace_invitations_member_select permissive değeri PERMISSIVE değil: %', v_policy_rec.permissive;
    end if;

    if v_policy_rec.cmd <> 'SELECT' then
        raise exception 'Post-check FAIL: workspace_invitations_member_select cmd değeri SELECT değil: %', v_policy_rec.cmd;
    end if;

    if v_policy_rec.roles <> '{authenticated}'::name[] and v_policy_rec.roles <> array['authenticated']::name[] then
        raise exception 'Post-check FAIL: workspace_invitations_member_select roller yalnızca {authenticated} olmalı: %', v_policy_rec.roles;
    end if;

    if v_policy_rec.with_check is not null then
        raise exception 'Post-check FAIL: workspace_invitations_member_select with_check null olmalı: %', v_policy_rec.with_check;
    end if;

    -- Canonical predicate kontrolü (kesin eşitlik)
    v_canonical_qual := regexp_replace(v_policy_rec.qual, '\s+', '', 'g');
    v_canonical_qual := replace(v_canonical_qual, 'public.', '');
    if v_canonical_qual ~ '^\(.*\)$' then
        v_canonical_qual := regexp_replace(v_canonical_qual, '^\((.*)\)$', '\1');
    end if;

    if v_canonical_qual <> 'is_workspace_member(workspace_id)' then
        raise exception 'Post-check FAIL: workspace_invitations_member_select qual tam canonical eşleşmedi: beklenen "is_workspace_member(workspace_id)", alınan "%" (ham: "%")',
            v_canonical_qual, v_policy_rec.qual;
    end if;
end $$;

commit;
