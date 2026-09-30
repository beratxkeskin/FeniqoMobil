-- ============================================================================
-- Migration: 20260926000300_harden_workspace_table_privileges.sql
-- Sorumluluk: public.workspaces ve public.workspace_members tablolarındaki
--             aşırı geniş tablo yetkilerini (TRUNCATE, TRIGGER, INSERT, UPDATE,
--             DELETE, REFERENCES) authenticated rolünden tamamen kaldırmak;
--             yalnızca SELECT yetkisi vermek ve RLS üyelik korumasını sürdürmek.
-- İlkeler:
--   1. RLS açık tutulur; mevcut RLS politikalarına dokunulmaz.
--   2. Yalnızca public.workspaces ve public.workspace_members tabloları hedeflenir.
--   3. PUBLIC, anon ve authenticated rollerinden ALL yetkileri revoke edilir.
--   4. authenticated rolüne yalnızca açıkça SELECT yetkisi grant edilir.
--   5. service_role ACL'sine kesinlikle dokunulmaz.
--   6. Policy, trigger, constraint, index veya function değiştirilmez.
--   7. Hiçbir veri mutation'ı (DML/backfill) yapılmaz.
--   8. Migration baştan sona tek transaction bloğu (begin/commit) içinde atomik yürütülür.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. PRE-CHECK KONTROLLERİ (Fail-Closed Ön Koşul Doğrulaması)
-- ----------------------------------------------------------------------------
do $$
declare
    v_rls boolean;
begin
    -- 1.1 Tabloların varlığı
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'workspaces' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check FAIL: public.workspaces tablosu bulunamadı.';
    end if;

    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'workspace_members' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check FAIL: public.workspace_members tablosu bulunamadı.';
    end if;

    -- 1.2 RLS aktifliği
    select relrowsecurity into v_rls from pg_catalog.pg_class where oid = 'public.workspaces'::regclass;
    if v_rls is not true then
        raise exception 'Pre-check FAIL: public.workspaces üzerinde RLS aktif değil.';
    end if;

    select relrowsecurity into v_rls from pg_catalog.pg_class where oid = 'public.workspace_members'::regclass;
    if v_rls is not true then
        raise exception 'Pre-check FAIL: public.workspace_members üzerinde RLS aktif değil.';
    end if;

    -- 1.3 Beklenen SELECT politikaları
    if not exists (
        select 1 from pg_catalog.pg_policy
        where polrelid = 'public.workspaces'::regclass
          and polname = 'workspaces_select_member_v1'
          and polcmd = 'r'
    ) then
        raise exception 'Pre-check FAIL: public.workspaces üzerinde workspaces_select_member_v1 politikası bulunamadı.';
    end if;

    if not exists (
        select 1 from pg_catalog.pg_policy
        where polrelid = 'public.workspace_members'::regclass
          and polname = 'workspace_members_select_v2'
          and polcmd = 'r'
    ) then
        raise exception 'Pre-check FAIL: public.workspace_members üzerinde workspace_members_select_v2 politikası bulunamadı.';
    end if;

    -- 1.4 Gerekli rollerin varlığı
    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'authenticated') then
        raise exception 'Pre-check FAIL: authenticated rolü bulunamadı.';
    end if;

    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'anon') then
        raise exception 'Pre-check FAIL: anon rolü bulunamadı.';
    end if;

    if not exists (select 1 from pg_catalog.pg_roles where rolname = 'service_role') then
        raise exception 'Pre-check FAIL: service_role rolü bulunamadı.';
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 2. TABLO ACL DÜZELTMESİ (Revoke All + Grant Select)
-- ----------------------------------------------------------------------------

-- public.workspaces
revoke all on table public.workspaces from public;
revoke all on table public.workspaces from anon;
revoke all on table public.workspaces from authenticated;
grant select on table public.workspaces to authenticated;

-- public.workspace_members
revoke all on table public.workspace_members from public;
revoke all on table public.workspace_members from anon;
revoke all on table public.workspace_members from authenticated;
grant select on table public.workspace_members to authenticated;

-- ----------------------------------------------------------------------------
-- 3. POST-CHECK KONTROLLERİ (Fail-Closed Doğrulama)
-- ----------------------------------------------------------------------------
do $$
declare
    v_tbl text;
    v_priv text;
    v_rls boolean;
    v_forbidden_privs text[] := array['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'];
    v_all_privs text[] := array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'];
begin
    foreach v_tbl in array array['workspaces', 'workspace_members'] loop
        -- 3.1 RLS açık kalmalı
        select relrowsecurity into v_rls from pg_catalog.pg_class where oid = ('public.' || v_tbl)::regclass;
        if v_rls is not true then
            raise exception 'Post-check FAIL: public.% üzerinde RLS aktif değil.', v_tbl;
        end if;

        -- 3.2 authenticated yalnızca SELECT yetkisine sahip olmalı
        if not has_table_privilege('authenticated', 'public.' || v_tbl, 'SELECT') then
            raise exception 'Post-check FAIL: authenticated rolünün public.% üzerinde SELECT yetkisi yok.', v_tbl;
        end if;

        -- 3.3 authenticated diğer hiçbir yetkiye sahip olmamalı
        foreach v_priv in array v_forbidden_privs loop
            if has_table_privilege('authenticated', 'public.' || v_tbl, v_priv) then
                raise exception 'Post-check FAIL: authenticated rolünün public.% üzerinde beklenmeyen % yetkisi tespit edildi.', v_tbl, v_priv;
            end if;
        end loop;

        -- 3.4 anon ve public hiçbir tablo yetkisine sahip olmamalı
        foreach v_priv in array v_all_privs loop
            if has_table_privilege('anon', 'public.' || v_tbl, v_priv) then
                raise exception 'Post-check FAIL: anon rolünün public.% üzerinde beklenmeyen % yetkisi tespit edildi.', v_tbl, v_priv;
            end if;
            if has_table_privilege('public', 'public.' || v_tbl, v_priv) then
                raise exception 'Post-check FAIL: PUBLIC rolünün public.% üzerinde beklenmeyen % yetkisi tespit edildi.', v_tbl, v_priv;
            end if;
        end loop;

        -- 3.5 service_role yetkisinin korunduğunu doğrula
        if not has_table_privilege('service_role', 'public.' || v_tbl, 'SELECT') then
            raise exception 'Post-check FAIL: service_role rolünün public.% üzerinde SELECT yetkisi kaybolmuş.', v_tbl;
        end if;
    end loop;

    -- 3.6 Mevcut workspace SELECT politikalarının korunduğunu doğrula
    if not exists (
        select 1 from pg_catalog.pg_policy
        where polrelid = 'public.workspaces'::regclass
          and polname = 'workspaces_select_member_v1'
          and polcmd = 'r'
    ) then
        raise exception 'Post-check FAIL: workspaces_select_member_v1 politikası korunmadı.';
    end if;

    if not exists (
        select 1 from pg_catalog.pg_policy
        where polrelid = 'public.workspace_members'::regclass
          and polname = 'workspace_members_select_v2'
          and polcmd = 'r'
    ) then
        raise exception 'Post-check FAIL: workspace_members_select_v2 politikası korunmadı.';
    end if;
end $$;

commit;
