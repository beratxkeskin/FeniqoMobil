-- =============================================================================
-- FeniqoMobil - Read-Only PostgreSQL Schema and Security Metadata Audit
-- =============================================================================
-- Bu dosya, Staging ve Production veritabanlarında salt-okunur metadata denetimi
-- yapmak üzere tasarlanmıştır.
--
-- GÜVENLİK VE GİZLİLİK GARANTİLERİ:
-- 1. "begin transaction read only;" ile başlar; işlem düzeyinde veri veya şema
--    değişikliğini PostgreSQL motoru seviyesinde engeller.
-- 2. "statement_timeout" ve "lock_timeout" ile uzun kilit veya yük oluşturmaz.
-- 3. Kesinlikle kullanıcı tablolarından (profiles, transactions, budgets,
--    auth.users, storage.objects vb.) veri satırı seçmez; kişisel veya
--    finansal payload sızdırmaz.
-- 4. Yalnızca PostgreSQL sistem katalogları (pg_catalog), information_schema
--    ve Supabase metadata tablolarını (schema_migrations, storage.buckets) okur.
-- 5. "rollback;" ile sonlanır; oturumda hiçbir kalıcı yan etki bırakmaz.
-- 6. psql meta-komutu (\d vb.) içermez; Supabase Dashboard SQL Editor'da
--    veya psql üzerinde doğrudan çalıştırılabilir.
-- =============================================================================

begin transaction read only;

set local statement_timeout = '15s';
set local lock_timeout = '2s';

-- =============================================================================
-- BÖLÜM 1: PostgreSQL Sürümü ve Veritabanı Oturum Metadata
-- =============================================================================
select
    '1. DATABASE_ENVIRONMENT'::text as audit_section,
    version() as postgres_version,
    current_database() as current_database,
    current_user as current_user,
    current_setting('transaction_read_only', true) as transaction_read_only,
    current_setting('server_version_num', true) as server_version_num,
    timezone('utc'::text, now()) as audit_timestamp_utc;

-- =============================================================================
-- BÖLÜM 2: Yüklü Extension Listesi
-- =============================================================================
select
    '2. INSTALLED_EXTENSIONS'::text as audit_section,
    e.extname as extension_name,
    e.extversion as installed_version,
    n.nspname as schema_name,
    c.description as description
from pg_catalog.pg_extension e
join pg_catalog.pg_namespace n on n.oid = e.extnamespace
left join pg_catalog.pg_description c on c.objoid = e.oid
    and c.classoid = 'pg_catalog.pg_extension'::regclass
    and c.objsubid = 0
order by e.extname;

-- =============================================================================
-- BÖLÜM 3: Supabase Migration Geçmişi (Bilinçli Fail-Closed)
-- =============================================================================
-- NOT: supabase_migrations.schema_migrations tablosu mevcut değilse sorgu
-- bilinçli olarak FAIL-CLOSED biçimde durur (relation does not exist).
-- Kolon adı uyumsuzluklarını (bazı sürümlerde name kolonu bulunmayabilir) önlemek
-- için satır to_jsonb(sm) üzerinden okunur ve version değerine göre sıralanır.
select
    '3. MIGRATION_HISTORY'::text as audit_section,
    (to_jsonb(sm)->>'version')::text as migration_version,
    coalesce(to_jsonb(sm)->>'name', '') as migration_name
from supabase_migrations.schema_migrations sm
order by to_jsonb(sm)->>'version';

-- =============================================================================
-- BÖLÜM 4: Public Şema Tabloları ve Kolonları
-- =============================================================================
select
    '4. PUBLIC_TABLES_AND_COLUMNS'::text as audit_section,
    c.table_name,
    c.column_name,
    c.ordinal_position,
    c.data_type,
    c.udt_name,
    c.is_nullable,
    c.column_default
from information_schema.columns c
where c.table_schema = 'public'
order by c.table_name, c.ordinal_position;

-- =============================================================================
-- BÖLÜM 5: Constraint'ler (PK / FK / UNIQUE / CHECK)
-- =============================================================================
select
    '5. CONSTRAINTS'::text as audit_section,
    rel.relname as table_name,
    con.conname as constraint_name,
    case con.contype
        when 'p' then 'PRIMARY KEY'
        when 'f' then 'FOREIGN KEY'
        when 'u' then 'UNIQUE'
        when 'c' then 'CHECK'
        when 'x' then 'EXCLUSION'
        else con.contype::text
    end as constraint_type,
    con.condeferrable as is_deferrable,
    con.condeferred as is_deferred,
    pg_catalog.pg_get_constraintdef(con.oid, true) as constraint_definition
from pg_catalog.pg_constraint con
join pg_catalog.pg_class rel on rel.oid = con.conrelid
join pg_catalog.pg_namespace nsp on nsp.oid = rel.relnamespace
where nsp.nspname = 'public'
order by rel.relname, con.conname;

-- =============================================================================
-- BÖLÜM 6: İndeksler ve Geçerlilik Durumları
-- =============================================================================
select
    '6. INDEXES'::text as audit_section,
    t.relname as table_name,
    i.relname as index_name,
    ix.indisunique as is_unique,
    ix.indisvalid as is_valid,
    ix.indisready as is_ready,
    pg_catalog.pg_get_indexdef(ix.indexrelid) as index_definition
from pg_catalog.pg_index ix
join pg_catalog.pg_class t on t.oid = ix.indrelid
join pg_catalog.pg_class i on i.oid = ix.indexrelid
join pg_catalog.pg_namespace n on n.oid = t.relnamespace
where n.nspname = 'public'
order by t.relname, i.relname;

-- =============================================================================
-- BÖLÜM 7: Tetikleyiciler (Triggers - Internal Hariç)
-- =============================================================================
select
    '7. TRIGGERS'::text as audit_section,
    c.relname as table_name,
    trig.tgname as trigger_name,
    case trig.tgenabled
        when 'O' then 'ENABLED'
        when 'D' then 'DISABLED'
        when 'R' then 'REPLICA_ONLY'
        when 'A' then 'ALWAYS'
        else trig.tgenabled::text
    end as enabled_status,
    pg_catalog.pg_get_triggerdef(trig.oid, true) as trigger_definition
from pg_catalog.pg_trigger trig
join pg_catalog.pg_class c on c.oid = trig.tgrelid
join pg_catalog.pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and not trig.tgisinternal
order by c.relname, trig.tgname;

-- =============================================================================
-- BÖLÜM 8: Public Şema Fonksiyon ve RPC İmzaları
-- =============================================================================
select
    '8. FUNCTIONS_AND_RPCS'::text as audit_section,
    n.nspname as schema_name,
    p.proname as function_name,
    pg_catalog.pg_get_function_identity_arguments(p.oid) as argument_signature,
    pg_catalog.pg_get_function_result(p.oid) as result_type,
    l.lanname as language_name,
    case p.provolatile
        when 'i' then 'IMMUTABLE'
        when 's' then 'STABLE'
        when 'v' then 'VOLATILE'
        else p.provolatile::text
    end as volatility,
    p.prosecdef as is_security_definer,
    r.rolname as owner_role,
    coalesce(array_to_string(p.proconfig, ', '), 'NONE') as search_path_and_config,
    pg_catalog.pg_get_functiondef(p.oid) as function_definition
from pg_catalog.pg_proc p
join pg_catalog.pg_namespace n on n.oid = p.pronamespace
join pg_catalog.pg_language l on l.oid = p.prolang
join pg_catalog.pg_roles r on r.oid = p.proowner
where n.nspname = 'public'
order by p.proname, argument_signature;

-- =============================================================================
-- BÖLÜM 9: Row-Level Security (RLS) Durumu (Public ve Storage Şemaları)
-- =============================================================================
select
    '9. RLS_STATUS'::text as audit_section,
    n.nspname as schema_name,
    c.relname as table_name,
    c.relrowsecurity as rls_enabled,
    c.relforcerowsecurity as rls_forced
from pg_catalog.pg_class c
join pg_catalog.pg_namespace n on n.oid = c.relnamespace
where n.nspname in ('public', 'storage')
  and c.relkind in ('r', 'p')
order by n.nspname, c.relname;

-- =============================================================================
-- BÖLÜM 10: RLS Güvenlik Politikaları (Public ve Storage Şemaları)
-- =============================================================================
select
    '10. RLS_POLICIES'::text as audit_section,
    pol.schemaname,
    pol.tablename,
    pol.policyname,
    array_to_string(pol.roles, ', ') as applicable_roles,
    pol.cmd as command_type,
    pol.qual as using_expression,
    pol.with_check as with_check_expression
from pg_catalog.pg_policies pol
where pol.schemaname in ('public', 'storage')
order by pol.schemaname, pol.tablename, pol.policyname;

-- =============================================================================
-- BÖLÜM 11: Tablo, Fonksiyon ve Sequence İzinleri (Grants)
-- =============================================================================
select
    '11. TABLE_GRANTS'::text as audit_section,
    table_schema,
    table_name,
    grantee,
    privilege_type,
    is_grantable
from information_schema.table_privileges
where table_schema in ('public', 'storage')
  and grantee in ('anon', 'authenticated', 'service_role', 'PUBLIC')
order by table_schema, table_name, grantee, privilege_type;

select
    '11. ROUTINE_GRANTS'::text as audit_section,
    routine_schema,
    routine_name,
    grantee,
    privilege_type,
    is_grantable
from information_schema.routine_privileges
where routine_schema = 'public'
  and grantee in ('anon', 'authenticated', 'service_role', 'PUBLIC')
order by routine_schema, routine_name, grantee, privilege_type;

-- Sequence Doğrudan ACL İzin Envanteri (Effective/inherited değil, doğrudan tanımlı ACL envanteridir)
select
    '11. SEQUENCE_GRANTS'::text as audit_section,
    n.nspname as sequence_schema,
    c.relname as sequence_name,
    case when acl.grantee = 0 then 'PUBLIC' else r.rolname end as grantee,
    acl.privilege_type,
    acl.is_grantable
from pg_catalog.pg_class c
join pg_catalog.pg_namespace n on n.oid = c.relnamespace
cross join lateral aclexplode(coalesce(c.relacl, acldefault('s', c.relowner))) acl
left join pg_catalog.pg_roles r on r.oid = acl.grantee
where n.nspname in ('public', 'storage')
  and c.relkind = 'S'
  and acl.privilege_type in ('SELECT', 'UPDATE', 'USAGE')
  and (acl.grantee = 0 or r.rolname in ('anon', 'authenticated', 'service_role', 'PUBLIC'))
order by n.nspname, c.relname, grantee, acl.privilege_type;

-- =============================================================================
-- BÖLÜM 12: Realtime Publication Üyeliği
-- =============================================================================
select
    '12. REALTIME_PUBLICATIONS'::text as audit_section,
    pub.pubname as publication_name,
    pub.puballtables as publishes_all_tables,
    pub.pubinsert as publishes_insert,
    pub.pubupdate as publishes_update,
    pub.pubdelete as publishes_delete
from pg_catalog.pg_publication pub
order by pub.pubname;

select
    '12. REALTIME_TABLE_MEMBERS'::text as audit_section,
    pt.pubname as publication_name,
    pt.schemaname,
    pt.tablename
from pg_catalog.pg_publication_tables pt
where pt.pubname = 'supabase_realtime'
order by pt.schemaname, pt.tablename;

-- =============================================================================
-- BÖLÜM 13: Storage Bucket Metadata (Bilinçli Fail-Closed)
-- =============================================================================
-- NOT: storage.buckets tablosu mevcut değilse sorgu bilinçli olarak FAIL-CLOSED
-- biçimde durur. Yalnızca kova metadata'sı okunur; storage.objects satırları
-- veya kullanıcı yüklemeleri kesinlikle sorgulanmaz.
select
    '13. STORAGE_BUCKETS'::text as audit_section,
    b.id,
    b.name,
    b.public,
    b.file_size_limit,
    b.allowed_mime_types
from storage.buckets b
order by b.name;

-- =============================================================================
-- BÖLÜM 14: Storage Policy Metadata
-- =============================================================================
select
    '14. STORAGE_POLICIES'::text as audit_section,
    pol.tablename,
    pol.policyname,
    array_to_string(pol.roles, ', ') as applicable_roles,
    pol.cmd as command_type,
    pol.qual as using_expression,
    pol.with_check as with_check_expression
from pg_catalog.pg_policies pol
where pol.schemaname = 'storage'
order by pol.tablename, pol.policyname;

-- =============================================================================
-- BÖLÜM 15: Beklenen Kritik Nesne Post-Check Özeti (Deterministik ve Fail-Closed)
-- =============================================================================
with expected_tables as (
    select expected_table_name
    from unnest(array[
        'transactions',
        'sync_operations_receipts',
        'budgets',
        'categories',
        'subscriptions',
        'recurring_transactions',
        'goal_contributions',
        'debt_payments',
        'debts',
        'goals',
        'workspace_invitations',
        'profiles',
        'subscription_payments',
        'workspaces',
        'subscription_price_histories',
        'workspace_members',
        'assets',
        'market_prices',
        'market_price_rate_limits'
    ]::text[]) as expected_table_name
),
table_rls_scan as (
    select
        et.expected_table_name,
        c.oid as table_oid,
        coalesce(c.relrowsecurity, false) as rls_enabled
    from expected_tables et
    left join pg_catalog.pg_class c on c.relname = et.expected_table_name
        and c.relnamespace = (select oid from pg_catalog.pg_namespace where nspname = 'public')
        and c.relkind in ('r', 'p')
),
table_rls_summary as (
    select
        count(*)::integer as total_expected_count,
        count(table_oid)::integer as found_table_count,
        count(*) filter (where rls_enabled is true)::integer as rls_enabled_count,
        coalesce(
            array_agg(expected_table_name order by expected_table_name) filter (where table_oid is null),
            '{}'::text[]
        ) as missing_table_names,
        coalesce(
            array_agg(expected_table_name order by expected_table_name) filter (where table_oid is not null and rls_enabled is not true),
            '{}'::text[]
        ) as rls_disabled_table_names
    from table_rls_scan
),
rpc_scan as (
    select
        proc.oid as rpc_oid,
        coalesce(proc.prosecdef, false) as is_security_definer,
        coalesce(array_to_string(proc.proconfig, ', '), 'NONE') as rpc_config,
        coalesce(
            exists (
                select 1 from unnest(proc.proconfig) cfg
                where cfg in ('search_path=""', 'search_path=', 'search_path=''''')
            ),
            false
        ) as has_safe_search_path
    from (
        select to_regprocedure('public.sync_write_v2(text,text,text,bigint,jsonb)') as proc_oid
    ) target
    left join pg_catalog.pg_proc proc on proc.oid = target.proc_oid
),
expected_realtime_tables as (
    select expected_rt_table
    from unnest(array[
        'profiles',
        'categories',
        'transactions'
    ]::text[]) as expected_rt_table
),
realtime_actual as (
    select tablename as rt_tablename
    from pg_catalog.pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public'
),
realtime_actual_count as (
    select count(*)::integer as total_actual_rt_count from realtime_actual
),
realtime_summary as (
    select
        rac.total_actual_rt_count as actual_rt_count,
        coalesce(
            array_agg(ert.expected_rt_table order by ert.expected_rt_table) filter (where ra.rt_tablename is null),
            '{}'::text[]
        ) as missing_realtime_tables,
        coalesce(
            (select array_agg(ra2.rt_tablename order by ra2.rt_tablename)
             from realtime_actual ra2
             where ra2.rt_tablename not in (select expected_rt_table from expected_realtime_tables)),
            '{}'::text[]
        ) as extra_realtime_tables
    from expected_realtime_tables ert
    left join realtime_actual ra on ra.rt_tablename = ert.expected_rt_table
    cross join realtime_actual_count rac
    group by rac.total_actual_rt_count
)
select
    '15. CRITICAL_OBJECTS_POST_CHECK'::text as audit_section,
    ts.total_expected_count,
    ts.found_table_count,
    (ts.found_table_count = ts.total_expected_count) as all_19_tables_exist,
    ts.missing_table_names,
    ts.rls_enabled_count,
    (ts.found_table_count = ts.total_expected_count and ts.rls_enabled_count = ts.total_expected_count) as all_19_tables_have_rls,
    ts.rls_disabled_table_names,
    (rs.rpc_oid is not null) as sync_write_v2_signature_exists,
    rs.is_security_definer as sync_write_v2_is_security_definer,
    rs.has_safe_search_path as sync_write_v2_has_safe_search_path,
    rs.rpc_config as sync_write_v2_config,
    rts.actual_rt_count as realtime_published_table_count,
    (cardinality(rts.missing_realtime_tables) = 0) as has_expected_realtime_tables,
    rts.missing_realtime_tables,
    rts.extra_realtime_tables,
    case
        when ts.found_table_count <> ts.total_expected_count then
            'FAIL: Eksik public tablolar tespit edildi: ' || array_to_string(ts.missing_table_names, ', ')
        when ts.rls_enabled_count <> ts.total_expected_count then
            'FAIL: RLS kapali tablolar tespit edildi: ' || array_to_string(ts.rls_disabled_table_names, ', ')
        when rs.rpc_oid is null then
            'FAIL: public.sync_write_v2(text,text,text,bigint,jsonb) RPC imzasi bulunamadi'
        when rs.is_security_definer is not true then
            'FAIL: sync_write_v2 fonksiyonu SECURITY DEFINER olarak tanimlanmamis'
        when rs.has_safe_search_path is not true then
            'FAIL: sync_write_v2 fonksiyonunda sabit/guvenli search_path (SET search_path = '''') yapilandirmasi eksik'
        when cardinality(rts.missing_realtime_tables) > 0 then
            'FAIL: supabase_realtime yayininda eksik tablolar: ' || array_to_string(rts.missing_realtime_tables, ', ')
        when cardinality(rts.extra_realtime_tables) > 0 then
            'DRIFT: Kritik sozlesmeler tam ancak beklenmeyen ekstra realtime uyeleri mevcut: ' || array_to_string(rts.extra_realtime_tables, ', ')
        else
            'PASS: 19 tablo, RLS, sync_write_v2 guvenligi ve realtime sozlesmesi tam uyumlu'
    end as overall_read_only_health_status
from table_rls_summary ts
cross join rpc_scan rs
cross join realtime_summary rts;

-- =============================================================================
-- GÜVENLİK KAPANIŞI: Koşulsuz ROLLBACK
-- =============================================================================
rollback;
