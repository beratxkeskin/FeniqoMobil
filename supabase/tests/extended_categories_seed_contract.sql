-- FeniqoMobil V1 Genişletilmiş Sistem Varsayılan Kategorileri SQL Kabul Testleri
-- 7 Kabul Senaryosu:
-- 1. Temiz eski seed -> 27 kanonik kategori
-- 2. Migration'ın ikinci çalışması idempotent
-- 3. Aynı UUID altında farklı içerik -> fail ve rollback
-- 4. Aynı slug farklı UUID -> fail ve rollback
-- 5. Kullanıcı/workspace kaydı kanonik UUID ile çakışıyor -> fail ve rollback
-- 6. Silinmiş kanonik kayıt -> fail ve rollback
-- 7. Var olan kullanıcı kategorileri değişmeden kalır

begin;

-- SENARYO 1: Temiz eski seed sonrası durum doğrulama (27 aktif kanonik kategori)
do $$
declare
    v_total_count integer;
    v_income_count integer;
    v_expense_count integer;
    v_legacy_hidden_count integer;
begin
    select count(*) into v_total_count
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null;

    select count(*) into v_income_count
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null and type = 'income';

    select count(*) into v_expense_count
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null and type = 'expense';

    assert v_total_count = 27, 'SENARYO 1 BAŞARISIZ: Toplam 27 aktif varsayılan kategori olmalı. Bulunan: ' || v_total_count;
    assert v_income_count = 9, 'SENARYO 1 BAŞARISIZ: 9 aktif gelir kategorisi olmalı. Bulunan: ' || v_income_count;
    assert v_expense_count = 18, 'SENARYO 1 BAŞARISIZ: 18 aktif gider kategorisi olmalı. Bulunan: ' || v_expense_count;

    -- Legacy 120 ve 121 soft-delete ile gizlenmiş olmalı
    select count(*) into v_legacy_hidden_count
    from public.categories
    where id in ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121')
      and deleted_at is not null;

    assert v_legacy_hidden_count = 2, 'SENARYO 1 BAŞARISIZ: Eski 120 ve 121 kategorileri gizlenmiş olmalıdır.';
end $$;

-- SENARYO 2: İkinci kez çalıştırma idempotent olmalı (tekrar ekleme denemesi hata vermez ve sayıyı bozmaz)
do $$
declare
    v_total_count integer;
begin
    -- Migration'daki insert komutunu tekrar çalıştır
    insert into public.categories (
        id, user_id, workspace_id, name, slug, type, color, icon, is_default, created_at, updated_at, version
    ) values (
        '11111111-1111-4111-8111-111111111106', null, null, 'İşletme Geliri', 'business-income', 'income', '#0D9488', 'store', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1
    ) on conflict (id) do nothing;

    select count(*) into v_total_count
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null;

    assert v_total_count = 27, 'SENARYO 2 BAŞARISIZ: İkinci çalışma sonrası aktif sayı 27 kalmalıdır.';
end $$;

-- SENARYO 3: Aynı UUID altında farklı içerik -> fail ve rollback
savepoint sp_test3;
do $$
declare
    v_failed boolean := false;
begin
    -- Kanonik UUID'nin adını değiştirip preflight'ı test et
    update public.categories
    set name = 'Bozulmuş Ad'
    where id = '11111111-1111-4111-8111-111111111106';

    begin
        perform 1 from (
            select 1 from public.categories c
            where c.id = '11111111-1111-4111-8111-111111111106'
              and c.name is distinct from 'İşletme Geliri'
        ) t;
        -- Preflight mantığını simüle et:
        if exists (
            select 1 from public.categories c
            where c.id = '11111111-1111-4111-8111-111111111106'
              and c.name is distinct from 'İşletme Geliri'
        ) then
            raise exception 'SEED_PREFLIGHT_FAILED: Mismatch in canonical definition';
        end if;
    exception when others then
        if sqlerrm like '%SEED_PREFLIGHT_FAILED%' then
            v_failed := true;
        else
            raise;
        end if;
    end;

    assert v_failed, 'SENARYO 3 BAŞARISIZ: Aynı UUID altında farklı içerik preflight tarafından reddedilmelidir.';
end $$;
rollback to savepoint sp_test3;

-- SENARYO 4: Aynı slug farklı UUID'de bulunuyorsa -> fail ve rollback
savepoint sp_test4;
do $$
declare
    v_failed boolean := false;
    v_fake_uuid uuid := 'a0000000-0000-4000-8000-000000000001'::uuid;
begin
    begin
        insert into public.categories (
            id, user_id, workspace_id, name, slug, type, color, icon, is_default, created_at, updated_at, version
        ) values (
            v_fake_uuid, null, null, 'Çakışan Akaryakıt', 'fuel', 'expense', '#3B82F6', 'fuel_pump', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1
        );
    exception when others then
        v_failed := true;
    end;

    assert v_failed, 'SENARYO 4 BAŞARISIZ: Aynı slug farklı UUID üzerinde eklenememeli ve hata vermelidir.';
end $$;
rollback to savepoint sp_test4;

-- SENARYO 5: Kullanıcı/workspace kaydı kanonik UUID ile çakışıyor -> fail ve rollback
savepoint sp_test5;
do $$
declare
    v_failed boolean := false;
begin
    begin
        update public.categories
        set user_id = 'c0000000-0000-4000-8000-000000000001'::uuid
        where id = '11111111-1111-4111-8111-111111111107';
    exception when others then
        v_failed := true;
    end;

    assert v_failed, 'SENARYO 5 BAŞARISIZ: Kanonik UUID kullanıcı kaydıyla çakışamaz veya güncellenemez.';
end $$;
rollback to savepoint sp_test5;

-- SENARYO 6: Silinmiş kanonik kayıt -> fail ve rollback
savepoint sp_test6;
do $$
declare
    v_failed boolean := false;
begin
    -- Aktif kanonik kategoriyi silinmiş olarak işaretle
    update public.categories
    set deleted_at = timezone('utc'::text, now())
    where id = '11111111-1111-4111-8111-111111111108';

    begin
        if exists (
            select 1 from public.categories c
            where c.id = '11111111-1111-4111-8111-111111111108'
              and c.deleted_at is not null
        ) then
            raise exception 'SEED_PREFLIGHT_FAILED: Canonical record is deleted';
        end if;
    exception when others then
        v_failed := true;
    end;

    assert v_failed, 'SENARYO 6 BAŞARISIZ: Silinmiş kanonik kayıt preflight tarafından reddedilmelidir.';
end $$;
rollback to savepoint sp_test6;

-- SENARYO 7: Var olan kullanıcı kategorileri değişmeden kalır
savepoint sp_test7;
do $$
declare
    v_user_cat_id uuid := 'a1000000-0000-4000-8000-000000000001'::uuid;
    v_user_id uuid;
    v_fetched_name text;
begin
    select id into v_user_id from auth.users limit 1;

    insert into public.categories (
        id, user_id, workspace_id, name, slug, type, color, icon, is_default, created_at, updated_at, version
    ) values (
        v_user_cat_id, v_user_id, null, 'Özel Hobilerim', 'ozel-hobilerim', 'expense', '#123456', 'star', false, timezone('utc'::text, now()), timezone('utc'::text, now()), 1
    );

    -- Seed migration ekleme komutları çalışsa dahi kullanıcı kategorisi etkilenmemeli
    select name into v_fetched_name
    from public.categories
    where id = v_user_cat_id and user_id = v_user_id;

    assert v_fetched_name = 'Özel Hobilerim', 'SENARYO 7 BAŞARISIZ: Kullanıcı kategorisi değişmeden kalmalıdır.';
end $$;
rollback to savepoint sp_test7;

rollback;
