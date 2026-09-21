-- FeniqoMobil V1 Genişletilmiş Sistem Varsayılan Kategorileri Seed Migration
-- Fail-closed ve idempotent seed: Preflight ve post-check kontrolleriyle uyuşmazlık durumunda atomik rollback yapar.
--
-- Gerçek Migration Etkisi:
-- 1. 17 legacy varsayılan kategorinin sözleşmesi (tüm alanlar ve sahiplik) doğrulanır.
-- 2. 2 legacy belirsiz kategori (1120 'Tasarruf & Yatırım' ve 1121 'Kredi Ödemeleri') tombstone yapılır.
-- 3. 12 yeni kanonik kategori (4 gelir, 8 gider) eklenir.
-- 4. Net aktif sistem kategorisi: 17 - 2 + 12 = 27 aktif kategori (9 gelir + 18 gider).
-- 5. ON CONFLICT DO UPDATE içermez; mevcut farklı içerik asla sessizce ezilmez.
begin;

-- ============================================================================
-- 1. PREFLIGHT VE GEÇİCİ REFERANS TABLOLARI
-- ============================================================================
do $$
declare
    v_missing_legacy_count integer;
    v_corrupt_legacy_count integer;
    v_corrupt_active_legacy_count integer;
    v_corrupt_legacy_tombstone_count integer;
    v_extended_conflict_count integer;
    v_extended_mismatch_count integer;
    v_extended_slug_conflict_count integer;
    v_extended_pretombstone_count integer;
begin
    -- 1.A: Beklenen 17 Legacy Kategori Referans Tablosu (20260816000100 seed'i ile birebir aynı)
    create temporary table temp_expected_legacy_categories (
        id uuid primary key,
        name text not null,
        slug text not null,
        type text not null,
        color text not null,
        icon text not null
    ) on commit drop;

    insert into temp_expected_legacy_categories (id, name, slug, type, color, icon)
    values
        ('11111111-1111-4111-8111-111111111101', 'Maaş', 'maas', 'income', '#10B981', 'briefcase'),
        ('11111111-1111-4111-8111-111111111102', 'Freelance', 'freelance', 'income', '#34D399', 'laptop'),
        ('11111111-1111-4111-8111-111111111103', 'Burs', 'burs', 'income', '#6EE7B7', 'graduation-cap'),
        ('11111111-1111-4111-8111-111111111104', 'Yatırım', 'yatirim', 'income', '#059669', 'trending-up'),
        ('11111111-1111-4111-8111-111111111105', 'Diğer Gelir', 'diger-gelir', 'income', '#A7F3D0', 'dollar-sign'),
        ('11111111-1111-4111-8111-111111111111', 'Yemek', 'yemek', 'expense', '#FBBF24', 'utensils'),
        ('11111111-1111-4111-8111-111111111112', 'Market', 'market', 'expense', '#EF4444', 'shopping-cart'),
        ('11111111-1111-4111-8111-111111111113', 'Ulaşım', 'ulasim', 'expense', '#F59E0B', 'car'),
        ('11111111-1111-4111-8111-111111111114', 'Kira', 'kira', 'expense', '#3B82F6', 'home'),
        ('11111111-1111-4111-8111-111111111115', 'Fatura', 'fatura', 'expense', '#10B981', 'file-text'),
        ('11111111-1111-4111-8111-111111111116', 'Eğlence', 'eglence', 'expense', '#EC4899', 'music'),
        ('11111111-1111-4111-8111-111111111117', 'Eğitim', 'egitim', 'expense', '#8B5CF6', 'book-open'),
        ('11111111-1111-4111-8111-111111111118', 'Sağlık', 'saglik', 'expense', '#EF4444', 'heart-pulse'),
        ('11111111-1111-4111-8111-111111111119', 'Abonelik', 'abonelik', 'expense', '#6366F1', 'credit-card'),
        ('11111111-1111-4111-8111-111111111120', 'Tasarruf & Yatırım', 'tasarruf-yatirim', 'expense', '#10B981', 'trending-up'),
        ('11111111-1111-4111-8111-111111111121', 'Kredi Ödemeleri', 'kredi-odemeleri', 'expense', '#4F46E5', 'percent'),
        ('11111111-1111-4111-8111-111111111122', 'Diğer Gider', 'diger-gider', 'expense', '#6B7280', 'help-circle');

    -- Kontrol 1.A.1: 17 legacy kategorinin tamamı veritabanında var mı?
    select count(*)
    into v_missing_legacy_count
    from temp_expected_legacy_categories te
    left join public.categories c on te.id = c.id
    where c.id is null;

    if v_missing_legacy_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Beklenen 17 eski kanonik kategori eksik (Eksik sayısı: %)', v_missing_legacy_count;
    end if;

    -- Kontrol 1.A.2: 17 legacy kategorinin alanları (name, slug, type, color, icon, user_id null, workspace_id null, is_default true) tam eşleşiyor mu?
    select count(*)
    into v_corrupt_legacy_count
    from public.categories c
    join temp_expected_legacy_categories te on c.id = te.id
    where c.user_id is not null
       or c.workspace_id is not null
       or c.is_default is distinct from true
       or c.name is distinct from te.name
       or c.slug is distinct from te.slug
       or c.type is distinct from te.type
       or c.color is distinct from te.color
       or c.icon is distinct from te.icon;

    if v_corrupt_legacy_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: 17 eski kanonik kategorinin alan veya sahiplik sözleşmesi bozulmuş (Bozuk sayısı: %)', v_corrupt_legacy_count;
    end if;

    -- Kontrol 1.A.3: 15 aktif kalacak legacy kategori içinde silinmiş (tombstone) olan var mı?
    select count(*)
    into v_corrupt_active_legacy_count
    from public.categories c
    where c.id in (
        '11111111-1111-4111-8111-111111111101', '11111111-1111-4111-8111-111111111102',
        '11111111-1111-4111-8111-111111111103', '11111111-1111-4111-8111-111111111104',
        '11111111-1111-4111-8111-111111111105', '11111111-1111-4111-8111-111111111111',
        '11111111-1111-4111-8111-111111111112', '11111111-1111-4111-8111-111111111113',
        '11111111-1111-4111-8111-111111111114', '11111111-1111-4111-8111-111111111115',
        '11111111-1111-4111-8111-111111111116', '11111111-1111-4111-8111-111111111117',
        '11111111-1111-4111-8111-111111111118', '11111111-1111-4111-8111-111111111119',
        '11111111-1111-4111-8111-111111111122'
    )
    and c.deleted_at is not null;

    if v_corrupt_active_legacy_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Aktif kalması gereken 15 eski kategori içinde silinmiş satır bulundu.';
    end if;

    -- Kontrol 1.A.4: 1120 ve 1121 legacy tombstone adaylarının durum/version invariantı:
    -- İlk çalıştırmada (aktifken) deleted_at IS NULL ve version = 1 olmalı;
    -- İdempotent tekrar çalıştırmada (önceden tombstone edilmişken) deleted_at IS NOT NULL ve version = 2 olmalı.
    select count(*)
    into v_corrupt_legacy_tombstone_count
    from public.categories c
    where c.id in (
        '11111111-1111-4111-8111-111111111120',
        '11111111-1111-4111-8111-111111111121'
    )
    and (
        (c.deleted_at is null and c.version != 1)
        or
        (c.deleted_at is not null and c.version != 2)
    );

    if v_corrupt_legacy_tombstone_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: 1120/1121 legacy kayıtlarının version/tombstone invariantı bozulmuş.';
    end if;

    -- 1.B: Eklenecek 12 Genişletilmiş Kategori Referans Tablosu
    create temporary table temp_canonical_extended_categories (
        id uuid primary key,
        name text not null,
        slug text not null,
        type text not null,
        color text not null,
        icon text not null
    ) on commit drop;

    insert into temp_canonical_extended_categories (id, name, slug, type, color, icon)
    values
        ('11111111-1111-4111-8111-111111111106', 'İşletme Geliri', 'business-income', 'income', '#0D9488', 'store'),
        ('11111111-1111-4111-8111-111111111107', 'Kira Geliri', 'rental-income', 'income', '#22C55E', 'key_home'),
        ('11111111-1111-4111-8111-111111111108', 'İade & Geri Ödeme', 'refund-reimbursement', 'income', '#14B8A6', 'return_arrow'),
        ('11111111-1111-4111-8111-111111111109', 'Hediye Geliri', 'gift-income', 'income', '#2DD4BF', 'gift'),
        ('11111111-1111-4111-8111-111111111123', 'Akaryakıt', 'fuel', 'expense', '#3B82F6', 'fuel_pump'),
        ('11111111-1111-4111-8111-111111111124', 'Kişisel Bakım', 'personal-care', 'expense', '#DB2777', 'person_sparkle'),
        ('11111111-1111-4111-8111-111111111125', 'Alışveriş', 'shopping', 'expense', '#D946EF', 'shopping_bag'),
        ('11111111-1111-4111-8111-111111111126', 'Seyahat', 'travel', 'expense', '#0891B2', 'airplane'),
        ('11111111-1111-4111-8111-111111111127', 'Aile & Evcil Hayvan', 'family-pets', 'expense', '#EA580C', 'heart_paw'),
        ('11111111-1111-4111-8111-111111111128', 'Borç & Finansman', 'financial-expenses', 'expense', '#475569', 'percent'),
        ('11111111-1111-4111-8111-111111111129', 'Vergi & Ücretler', 'taxes-fees', 'expense', '#64748B', 'official_document'),
        ('11111111-1111-4111-8111-111111111130', 'Hediye & Bağış', 'gifts-donations', 'expense', '#C026D3', 'gift');

    -- Kontrol 1.B.1: Kanonik UUID bir kullanıcı veya workspace kaydında kullanılmış mı?
    select count(*)
    into v_extended_conflict_count
    from public.categories c
    join temp_canonical_extended_categories tc on c.id = tc.id
    where c.user_id is not null
       or c.workspace_id is not null
       or c.is_default is distinct from true;

    if v_extended_conflict_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Kanonik UUID kullanıcı/workspace kaydıyla çakışıyor.';
    end if;

    -- Kontrol 1.B.2: Kanonik UUID mevcut fakat alanları uyuşmuyor mu?
    select count(*)
    into v_extended_mismatch_count
    from public.categories c
    join temp_canonical_extended_categories tc on c.id = tc.id
    where c.slug is distinct from tc.slug
       or c.type is distinct from tc.type
       or c.name is distinct from tc.name
       or c.color is distinct from tc.color
       or c.icon is distinct from tc.icon;

    if v_extended_mismatch_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Kanonik UUID mevcut ancak alan içerikleri uyuşmuyor.';
    end if;

    -- Kontrol 1.B.3: Kanonik slug başka bir UUID üzerinde mevcut mu?
    select count(*)
    into v_extended_slug_conflict_count
    from public.categories c
    join temp_canonical_extended_categories tc on c.slug = tc.slug
    where c.id <> tc.id;

    if v_extended_slug_conflict_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Kanonik slug değeri farklı bir UUID kaydında kullanılmış.';
    end if;

    -- Kontrol 1.B.4: Kanonik UUID önceden tombstone edilmiş mi?
    -- (Yalnız migration tekrarında geçerli kanonik kayıt olarak kabul edilir; farklı içerikle silinmiş kayıt yasaktır)
    select count(*)
    into v_extended_pretombstone_count
    from public.categories c
    join temp_canonical_extended_categories tc on c.id = tc.id
    where c.deleted_at is not null;

    if v_extended_pretombstone_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Yeni kanonik kategori kaydı silinmiş (tombstone) durumda.';
    end if;
end $$;

-- ============================================================================
-- 2. ESKİ BELİRSİZ SİSTEM KATEGORİLERİNİN DEVREDEN ÇIKARILMASI (TOMBSTONE)
-- ============================================================================
-- DefaultCategorySeeder.kt ile birebir uyum: 1120 (tasarruf-yatirim) ve 1121 (kredi-odemeleri)
-- yalnızca beklenen kanonik sistem kayıtları oldukları kesinleştirilerek tombstone yapılır.
update public.categories
set deleted_at = coalesce(deleted_at, timezone('utc'::text, now())),
    updated_at = timezone('utc'::text, now()),
    version = version + 1
where id in (
    '11111111-1111-4111-8111-111111111120',
    '11111111-1111-4111-8111-111111111121'
)
and is_default is true
and user_id is null
and workspace_id is null
and deleted_at is null;

-- ============================================================================
-- 3. GÜVENLİ IDEMPOTENT EKLEME (ON CONFLICT DO NOTHING)
-- ============================================================================
insert into public.categories (
    id,
    user_id,
    workspace_id,
    name,
    slug,
    type,
    color,
    icon,
    is_default,
    created_at,
    updated_at,
    version
)
values
    ('11111111-1111-4111-8111-111111111106', null, null, 'İşletme Geliri', 'business-income', 'income', '#0D9488', 'store', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111107', null, null, 'Kira Geliri', 'rental-income', 'income', '#22C55E', 'key_home', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111108', null, null, 'İade & Geri Ödeme', 'refund-reimbursement', 'income', '#14B8A6', 'return_arrow', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111109', null, null, 'Hediye Geliri', 'gift-income', 'income', '#2DD4BF', 'gift', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111123', null, null, 'Akaryakıt', 'fuel', 'expense', '#3B82F6', 'fuel_pump', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111124', null, null, 'Kişisel Bakım', 'personal-care', 'expense', '#DB2777', 'person_sparkle', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111125', null, null, 'Alışveriş', 'shopping', 'expense', '#D946EF', 'shopping_bag', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111126', null, null, 'Seyahat', 'travel', 'expense', '#0891B2', 'airplane', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111127', null, null, 'Aile & Evcil Hayvan', 'family-pets', 'expense', '#EA580C', 'heart_paw', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111128', null, null, 'Borç & Finansman', 'financial-expenses', 'expense', '#475569', 'percent', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111129', null, null, 'Vergi & Ücretler', 'taxes-fees', 'expense', '#64748B', 'official_document', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1),
    ('11111111-1111-4111-8111-111111111130', null, null, 'Hediye & Bağış', 'gifts-donations', 'expense', '#C026D3', 'gift', true, timezone('utc'::text, now()), timezone('utc'::text, now()), 1)
on conflict (id) do nothing;

-- ============================================================================
-- 4. POST-CHECK: 27 AKTİF KANONİK KATEGORİ KATALOG DOĞRULAMASI
-- ============================================================================
do $$
declare
    v_missing_active_count integer;
    v_corrupt_active_count integer;
    v_unexpected_active_count integer;
    v_active_total integer;
    v_active_income integer;
    v_active_expense integer;
    v_tombstoned_legacy_count integer;
begin
    -- 27 Aktif Kanonik Katalog Referans Tablosu (9 Gelir + 18 Gider)
    create temporary table temp_expected_active_catalog (
        id uuid primary key,
        name text not null,
        slug text not null,
        type text not null,
        color text not null,
        icon text not null
    ) on commit drop;

    insert into temp_expected_active_catalog (id, name, slug, type, color, icon)
    values
        -- 9 Gelir Kategorisi
        ('11111111-1111-4111-8111-111111111101', 'Maaş', 'maas', 'income', '#10B981', 'briefcase'),
        ('11111111-1111-4111-8111-111111111102', 'Freelance', 'freelance', 'income', '#34D399', 'laptop'),
        ('11111111-1111-4111-8111-111111111103', 'Burs', 'burs', 'income', '#6EE7B7', 'graduation-cap'),
        ('11111111-1111-4111-8111-111111111104', 'Yatırım', 'yatirim', 'income', '#059669', 'trending-up'),
        ('11111111-1111-4111-8111-111111111105', 'Diğer Gelir', 'diger-gelir', 'income', '#A7F3D0', 'dollar-sign'),
        ('11111111-1111-4111-8111-111111111106', 'İşletme Geliri', 'business-income', 'income', '#0D9488', 'store'),
        ('11111111-1111-4111-8111-111111111107', 'Kira Geliri', 'rental-income', 'income', '#22C55E', 'key_home'),
        ('11111111-1111-4111-8111-111111111108', 'İade & Geri Ödeme', 'refund-reimbursement', 'income', '#14B8A6', 'return_arrow'),
        ('11111111-1111-4111-8111-111111111109', 'Hediye Geliri', 'gift-income', 'income', '#2DD4BF', 'gift'),
        -- 18 Gider Kategorisi
        ('11111111-1111-4111-8111-111111111111', 'Yemek', 'yemek', 'expense', '#FBBF24', 'utensils'),
        ('11111111-1111-4111-8111-111111111112', 'Market', 'market', 'expense', '#EF4444', 'shopping-cart'),
        ('11111111-1111-4111-8111-111111111113', 'Ulaşım', 'ulasim', 'expense', '#F59E0B', 'car'),
        ('11111111-1111-4111-8111-111111111114', 'Kira', 'kira', 'expense', '#3B82F6', 'home'),
        ('11111111-1111-4111-8111-111111111115', 'Fatura', 'fatura', 'expense', '#10B981', 'file-text'),
        ('11111111-1111-4111-8111-111111111116', 'Eğlence', 'eglence', 'expense', '#EC4899', 'music'),
        ('11111111-1111-4111-8111-111111111117', 'Eğitim', 'egitim', 'expense', '#8B5CF6', 'book-open'),
        ('11111111-1111-4111-8111-111111111118', 'Sağlık', 'saglik', 'expense', '#EF4444', 'heart-pulse'),
        ('11111111-1111-4111-8111-111111111119', 'Abonelik', 'abonelik', 'expense', '#6366F1', 'credit-card'),
        ('11111111-1111-4111-8111-111111111122', 'Diğer Gider', 'diger-gider', 'expense', '#6B7280', 'help-circle'),
        ('11111111-1111-4111-8111-111111111123', 'Akaryakıt', 'fuel', 'expense', '#3B82F6', 'fuel_pump'),
        ('11111111-1111-4111-8111-111111111124', 'Kişisel Bakım', 'personal-care', 'expense', '#DB2777', 'person_sparkle'),
        ('11111111-1111-4111-8111-111111111125', 'Alışveriş', 'shopping', 'expense', '#D946EF', 'shopping_bag'),
        ('11111111-1111-4111-8111-111111111126', 'Seyahat', 'travel', 'expense', '#0891B2', 'airplane'),
        ('11111111-1111-4111-8111-111111111127', 'Aile & Evcil Hayvan', 'family-pets', 'expense', '#EA580C', 'heart_paw'),
        ('11111111-1111-4111-8111-111111111128', 'Borç & Finansman', 'financial-expenses', 'expense', '#475569', 'percent'),
        ('11111111-1111-4111-8111-111111111129', 'Vergi & Ücretler', 'taxes-fees', 'expense', '#64748B', 'official_document'),
        ('11111111-1111-4111-8111-111111111130', 'Hediye & Bağış', 'gifts-donations', 'expense', '#C026D3', 'gift');

    -- Kontrol 4.A: 27 aktif kategoriden eksik var mı?
    select count(*)
    into v_missing_active_count
    from temp_expected_active_catalog te
    left join public.categories c on te.id = c.id and c.deleted_at is null
    where c.id is null;

    if v_missing_active_count > 0 then
        raise exception 'SEED_POSTCHECK_FAILED: 27 aktif kanonik kategoriden eksik var (Eksik sayısı: %)', v_missing_active_count;
    end if;

    -- Kontrol 4.B: 27 aktif kategorinin herhangi bir alanında (name, slug, type, color, icon, user_id null, workspace_id null, is_default true) uyuşmazlık var mı?
    select count(*)
    into v_corrupt_active_count
    from public.categories c
    join temp_expected_active_catalog te on c.id = te.id
    where c.user_id is not null
       or c.workspace_id is not null
       or c.is_default is distinct from true
       or c.deleted_at is not null
       or c.name is distinct from te.name
       or c.slug is distinct from te.slug
       or c.type is distinct from te.type
       or c.color is distinct from te.color
       or c.icon is distinct from te.icon;

    if v_corrupt_active_count > 0 then
        raise exception 'SEED_POSTCHECK_FAILED: Aktif kanonik kategorilerin alan veya sahiplik sözleşmesinde uyuşmazlık var (Uyuşmayan sayısı: %)', v_corrupt_active_count;
    end if;

    -- Kontrol 4.C: 27 aktif katalog DIŞINDA fazladan aktif sistem kategorisi var mı?
    select count(*)
    into v_unexpected_active_count
    from public.categories c
    left join temp_expected_active_catalog te on c.id = te.id
    where c.is_default is true
      and c.user_id is null
      and c.workspace_id is null
      and c.deleted_at is null
      and te.id is null;

    if v_unexpected_active_count > 0 then
        raise exception 'SEED_POSTCHECK_FAILED: Katalog dışı beklenmeyen aktif sistem kategorisi bulundu (Fazlalık sayısı: %)', v_unexpected_active_count;
    end if;

    -- Kontrol 4.D: 1120 ve 1121 legacy kategorileri tam olarak tombstoned mı?
    select count(*)
    into v_tombstoned_legacy_count
    from public.categories c
    where c.id in (
        '11111111-1111-4111-8111-111111111120',
        '11111111-1111-4111-8111-111111111121'
    )
    and c.is_default is true
    and c.user_id is null
    and c.workspace_id is null
    and c.deleted_at is not null
    and c.version = 2;

    if v_tombstoned_legacy_count <> 2 then
        raise exception 'SEED_POSTCHECK_FAILED: 1120 ve 1121 legacy kategorilerinin her ikisi de tombstone durumunda olmalıdır (Bulunan: %)', v_tombstoned_legacy_count;
    end if;

    -- Kontrol 4.E: Toplam aktif sistem kategorisi sayıları (27 toplam, 9 gelir, 18 gider)
    select count(*) into v_active_total
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null;

    select count(*) into v_active_income
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null and type = 'income';

    select count(*) into v_active_expense
    from public.categories
    where is_default is true and user_id is null and workspace_id is null and deleted_at is null and type = 'expense';

    if v_active_total <> 27 or v_active_income <> 9 or v_active_expense <> 18 then
        raise exception 'SEED_POSTCHECK_FAILED: Aktif varsayılan kategori sayıları tutarsız (Toplam: %, Gelir: %, Gider: %)',
            v_active_total, v_active_income, v_active_expense;
    end if;
end $$;

commit;
