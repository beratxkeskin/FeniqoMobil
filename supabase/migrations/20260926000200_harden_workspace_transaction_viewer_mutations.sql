-- ============================================================================
-- Migration: 20260926000200_harden_workspace_transaction_viewer_mutations.sql
-- Sorumluluk: Ortak çalışma alanında (workspace) VIEWER rolündeki kullanıcıların
--             finansal işlem oluşturma (INSERT), güncelleme (UPDATE) ve silme
--             (soft DELETE - UPDATE deleted_at) yetkisini veritabanı trigger
--             katmanında savunma derinliği (defense-in-depth) ile engellemek.
-- İlkeler:
--   1. RLS açık tutulur; tablo yetkileri (ACL) veya RLS politikaları değiştirilmez.
--   2. sync_write_v2 fonksiyonu bütünüyle yeniden tanımlanmaz; işlem tablosu
--      düzeyinde bağımsız bir BEFORE INSERT OR UPDATE trigger uygulanır.
--   3. Kişisel işlemler (workspace_id IS NULL) akıştan tamamen muaftır.
--   4. Yalnızca OWNER ve EDITOR rolleri workspace transaction mutasyonuna yetkilidir.
--   5. Yetkilendirme için istemci/payload user_id değerine asla güvenilmez;
--      yalnızca JWT oturumundaki auth.uid() kullanılır.
--   6. Hata mesajlarında hiçbir hassas kullanıcı, çalışma alanı veya finansal veri sızdırılmaz.
--   7. Migration baştan sona tek transaction bloğu (begin/commit) içinde atomik yürütülür.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. PRE-CHECK KONTROLLERİ
-- ----------------------------------------------------------------------------
do $$
begin
    -- 1.1 public.transactions tablosu mevcut mu?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check failed: public.transactions tablosu bulunamadı.';
    end if;

    -- 1.2 public.transactions üzerinde RLS aktif mi?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relrowsecurity = true
    ) then
        raise exception 'Pre-check failed: public.transactions üzerinde RLS etkin değil.';
    end if;

    -- 1.3 public.workspace_members tablosu ve role_code kolonu mevcut mu?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'workspace_members' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check failed: public.workspace_members tablosu bulunamadı.';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'workspace_members' and column_name = 'role_code'
    ) then
        raise exception 'Pre-check failed: public.workspace_members tablosunda role_code kolonu bulunamadı.';
    end if;

    -- 1.4 auth.uid() ve auth.role() fonksiyonları kesin imzalarıyla mevcut mu?
    if to_regprocedure('auth.uid()') is null then
        raise exception 'Pre-check failed: auth.uid() kesin imzası bulunamadı.';
    end if;

    if to_regprocedure('auth.role()') is null then
        raise exception 'Pre-check failed: auth.role() kesin imzası bulunamadı.';
    end if;

    -- 1.5 Hedef trigger veya fonksiyonun çakışan beklenmeyen tanımı var mı?
    if exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions'
          and t.tgname = 'transactions_enforce_workspace_mutation_role_v1'
    ) then
        raise exception 'Pre-check failed: transactions_enforce_workspace_mutation_role_v1 trigger zaten tanımlı.';
    end if;

    if to_regprocedure('public.enforce_workspace_transaction_mutation_role_v1()') is not null then
        raise exception 'Pre-check failed: public.enforce_workspace_transaction_mutation_role_v1() fonksiyonu zaten tanımlı.';
    end if;

    -- 1.6 Mevcut temel transaction trigger'ları yerinde mi?
    if not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_protect_identity_v1'
    ) or not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_set_server_metadata'
    ) or not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_money_compat_before_write'
    ) then
        raise exception 'Pre-check failed: public.transactions üzerindeki beklenen temel triggerlar eksik.';
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 2. TRIGGER FONKSİYONU TANIMI
-- ----------------------------------------------------------------------------
create function public.enforce_workspace_transaction_mutation_role_v1()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_actor_id uuid;
    v_role_code text;
    v_current_role text;
begin
    -- Kural 1: Kişisel işlem kontrolü
    -- workspace_id NULL ise bu kişisel bir kayıttır; çalışma alanı rol denetiminden
    -- tamamen muaf tutulur ve kişisel RLS politikalarına bırakılır.
    if new.workspace_id is null then
        return new;
    end if;

    -- Kural 2: Sistem / service-role kontrolü
    -- Arka plan servisleri, sistem senkronizasyonu veya bakım görevleri
    -- service_role ile çalıştığında workspace kısıtlamasına takılmadan geçmesine izin verilir.
    v_current_role := auth.role();
    if v_current_role = 'service_role' then
        return new;
    end if;

    -- Kural 3: Actor kimliğinin auth.uid() ile belirlenmesi
    -- İstemci tarafından gönderilen payload/NEW.user_id değerine asla güvenilmez;
    -- doğrulanan JWT kimliği (auth.uid()) kullanılır.
    v_actor_id := auth.uid();

    -- Kural 4: Migration / Admin ve kimliği doğrulanmamış oturum ayrımı
    -- PostgreSQL süper kullanıcısı veya veritabanı yöneticisi (postgres, supabase_admin)
    -- şema migration'ı, ilk veri tohumlama (seed) veya doğrudan veritabanı bakımı
    -- gerçekleştirirken JWT bağlamı bulunmaz (auth.uid() NULL). Bu yönetimsel işlemlerin
    -- engellenmemesi için JWT parametrelerinin ayarlanmadığı bu açık bağlamda izin verilir.
    -- Ancak authenticated oturumunda veya RPC çağrısında auth.uid() NULL ise bu bir
    -- yetki ihlalidir ve fail-closed ilkesiyle reddedilir.
    if v_actor_id is null then
        if session_user in ('postgres', 'supabase_admin')
           and nullif(pg_catalog.current_setting('request.jwt.claim.sub', true), '') is null
           and nullif(pg_catalog.current_setting('request.jwt.claims', true), '') is null
           and nullif(pg_catalog.current_setting('request.jwt.claim.role', true), '') is null
           and pg_catalog.current_setting('role', true) != 'authenticated' then
            return new;
        end if;
        raise insufficient_privilege using message = 'Access denied: unauthenticated workspace mutation';
    end if;

    -- Kural 5: Workspace üyelik ve rol kontrolü
    -- Yalnızca ilgili workspace'te aktif (deleted_at IS NULL) 'OWNER' veya 'EDITOR'
    -- rolüne sahip kullanıcılar finansal işlem ekleyebilir, güncelleyebilir veya
    -- silebilir (soft delete bir UPDATE işlemidir).
    -- VIEWER veya üye olmayan kullanıcılar için yetki reddedilir.
    select m.role_code into v_role_code
    from public.workspace_members m
    where m.workspace_id = new.workspace_id
      and m.user_id = v_actor_id
      and m.deleted_at is null;

    if v_role_code is null or v_role_code not in ('OWNER', 'EDITOR') then
        -- Güvenlik: Hata mesajında kullanıcı, workspace veya finansal detay ifşa edilmez.
        raise insufficient_privilege using message = 'Access denied: insufficient workspace privileges';
    end if;

    return new;
end;
$$;

-- ----------------------------------------------------------------------------
-- 3. TRIGGER BAĞLANTISI
-- ----------------------------------------------------------------------------
create trigger transactions_enforce_workspace_mutation_role_v1
before insert or update on public.transactions
for each row execute function public.enforce_workspace_transaction_mutation_role_v1();

-- ----------------------------------------------------------------------------
-- 4. YETKİ KISITLAMASI (ACL)
-- ----------------------------------------------------------------------------
-- Trigger fonksiyonu doğrudan istemci API'si değildir; PostgREST üzerinden
-- doğrudan EXECUTE edilmemesi için PUBLIC, anon ve authenticated yetkileri geri alınır.
revoke all on function public.enforce_workspace_transaction_mutation_role_v1() from public;
revoke all on function public.enforce_workspace_transaction_mutation_role_v1() from anon;
revoke all on function public.enforce_workspace_transaction_mutation_role_v1() from authenticated;

-- ----------------------------------------------------------------------------
-- 5. POST-CHECK KONTROLLERİ
-- ----------------------------------------------------------------------------
do $$
declare
    v_proc_oid oid;
    v_returns_trigger boolean;
    v_secdef boolean;
    v_config text[];
    v_trg_count int;
begin
    -- 5.1 Fonksiyon kesin imzasıyla mevcut mu?
    v_proc_oid := to_regprocedure('public.enforce_workspace_transaction_mutation_role_v1()');
    if v_proc_oid is null then
        raise exception 'Post-check failed: public.enforce_workspace_transaction_mutation_role_v1() kesin imzalı fonksiyon bulunamadı.';
    end if;

    -- 5.2 Fonksiyonun RETURNS trigger, SECURITY INVOKER ve search_path sözleşmesi
    select (p.prorettype = 'trigger'::regtype), p.prosecdef, p.proconfig
    into v_returns_trigger, v_secdef, v_config
    from pg_catalog.pg_proc p
    where p.oid = v_proc_oid;

    if v_returns_trigger is not true then
        raise exception 'Post-check failed: Fonksiyon RETURNS trigger sözleşmesine uymuyor.';
    end if;

    if v_secdef is not false then
        raise exception 'Post-check failed: Fonksiyon SECURITY INVOKER olmalıdır; SECURITY DEFINER bulundu.';
    end if;

    if not ('search_path=""' = any(v_config) or 'search_path=' = any(v_config)) then
        raise exception 'Post-check failed: Fonksiyon search_path boş olarak ayarlanmamış.';
    end if;

    -- 5.3 Trigger public.transactions üzerinde BEFORE INSERT OR UPDATE ve ROW düzeyinde mi?
    select count(*) into v_trg_count
    from information_schema.triggers
    where event_object_schema = 'public'
      and event_object_table = 'transactions'
      and trigger_name = 'transactions_enforce_workspace_mutation_role_v1'
      and action_timing = 'BEFORE'
      and action_orientation = 'ROW'
      and event_manipulation in ('INSERT', 'UPDATE');

    if v_trg_count != 2 then
        raise exception 'Post-check failed: transactions_enforce_workspace_mutation_role_v1 trigger doğru olaylarla (BEFORE INSERT OR UPDATE FOR EACH ROW) oluşturulamadı (Adet: %).', v_trg_count;
    end if;

    -- 5.4 RLS hâlâ açık mı?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relrowsecurity = true
    ) then
        raise exception 'Post-check failed: public.transactions üzerinde RLS kapatılmış.';
    end if;

    -- 5.5 Mevcut transaction trigger'ları korunmuş mu?
    if not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_protect_identity_v1'
    ) or not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_set_server_metadata'
    ) or not exists (
        select 1 from pg_catalog.pg_trigger t
        join pg_catalog.pg_class c on c.oid = t.tgrelid
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and t.tgname = 'transactions_money_compat_before_write'
    ) then
        raise exception 'Post-check failed: public.transactions üzerindeki eski triggerlar bozulmuş.';
    end if;

    -- 5.6 Trigger fonksiyonunda anon veya authenticated için doğrudan EXECUTE grant'i bulunmamalı
    if has_function_privilege('anon', 'public.enforce_workspace_transaction_mutation_role_v1()', 'EXECUTE')
       or has_function_privilege('authenticated', 'public.enforce_workspace_transaction_mutation_role_v1()', 'EXECUTE') then
        raise exception 'Post-check failed: Trigger fonksiyonunda beklenmeyen doğrudan EXECUTE grant''i bulundu.';
    end if;
end $$;

commit;
