-- FeniqoMobil V1 Sistem Varsayılan Kategorileri Seed Migration
-- Güvenli ve idempotent seed: Preflight kontrolleriyle çakışma durumunda atomik rollback yapar.
-- ON CONFLICT DO UPDATE içermez; mevcut doğru sistem kayıtlarını sessizce bozmaz.
begin;

-- 1. KAT-I PREFLIGHT KONTROLLERİ
do $$
declare
    v_conflict_count integer;
    v_mismatch_count integer;
    v_slug_conflict_count integer;
begin
    -- Geçici canonical referans tablosu oluşturulur
    create temporary table temp_canonical_default_categories (
        id uuid primary key,
        name text not null,
        slug text not null,
        type text not null,
        color text not null,
        icon text not null
    ) on commit drop;

    insert into temp_canonical_default_categories (id, name, slug, type, color, icon)
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

    -- Kontrol 1: Canonical UUID bir kullanıcı kaydında veya silinmiş satırda kullanılmış mı?
    select count(*)
    into v_conflict_count
    from public.categories c
    join temp_canonical_default_categories tc on c.id = tc.id
    where c.user_id is not null
       or c.workspace_id is not null
       or c.is_default is distinct from true
       or c.deleted_at is not null;

    if v_conflict_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Canonical varsayılan kategori UUID değeri kullanıcı/özel satırıyla çakışıyor.';
    end if;

    -- Kontrol 2: Canonical UUID mevcut fakat slug, type, name, color veya icon uyuşmuyor mu?
    select count(*)
    into v_mismatch_count
    from public.categories c
    join temp_canonical_default_categories tc on c.id = tc.id
    where c.slug is distinct from tc.slug
       or c.type is distinct from tc.type
       or c.name is distinct from tc.name
       or c.color is distinct from tc.color
       or c.icon is distinct from tc.icon;

    if v_mismatch_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Canonical varsayılan kategori ID değeri mevcut ancak alan içerikleri uyuşmuyor.';
    end if;

    -- Kontrol 3: Canonical slug başka bir UUID üzerinde mevcut mu?
    select count(*)
    into v_slug_conflict_count
    from public.categories c
    join temp_canonical_default_categories tc on c.slug = tc.slug
    where c.id <> tc.id;

    if v_slug_conflict_count > 0 then
        raise exception 'SEED_PREFLIGHT_FAILED: Canonical varsayılan kategori slug değeri farklı bir UUID kaydında kullanılmış.';
    end if;
end $$;

-- 2. PARTIAL UNIQUE INDEX (Varsayılan aktif kategorilerin slug benzersizliği)
create unique index if not exists categories_default_slug_idx
on public.categories (slug)
where is_default
  and user_id is null
  and workspace_id is null
  and deleted_at is null;

-- 3. GÜVENLİ IDEMPOTENT EKLEME (ON CONFLICT DO NOTHING)
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
    -- GELİR (INCOME) KATEGORİLERİ (5 Adet)
    (
        '11111111-1111-4111-8111-111111111101',
        null,
        null,
        'Maaş',
        'maas',
        'income',
        '#10B981',
        'briefcase',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111102',
        null,
        null,
        'Freelance',
        'freelance',
        'income',
        '#34D399',
        'laptop',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111103',
        null,
        null,
        'Burs',
        'burs',
        'income',
        '#6EE7B7',
        'graduation-cap',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111104',
        null,
        null,
        'Yatırım',
        'yatirim',
        'income',
        '#059669',
        'trending-up',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111105',
        null,
        null,
        'Diğer Gelir',
        'diger-gelir',
        'income',
        '#A7F3D0',
        'dollar-sign',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),

    -- GİDER (EXPENSE) KATEGORİLERİ (12 Adet)
    (
        '11111111-1111-4111-8111-111111111111',
        null,
        null,
        'Yemek',
        'yemek',
        'expense',
        '#FBBF24',
        'utensils',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111112',
        null,
        null,
        'Market',
        'market',
        'expense',
        '#EF4444',
        'shopping-cart',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111113',
        null,
        null,
        'Ulaşım',
        'ulasim',
        'expense',
        '#F59E0B',
        'car',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111114',
        null,
        null,
        'Kira',
        'kira',
        'expense',
        '#3B82F6',
        'home',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111115',
        null,
        null,
        'Fatura',
        'fatura',
        'expense',
        '#10B981',
        'file-text',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111116',
        null,
        null,
        'Eğlence',
        'eglence',
        'expense',
        '#EC4899',
        'music',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111117',
        null,
        null,
        'Eğitim',
        'egitim',
        'expense',
        '#8B5CF6',
        'book-open',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111118',
        null,
        null,
        'Sağlık',
        'saglik',
        'expense',
        '#EF4444',
        'heart-pulse',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111119',
        null,
        null,
        'Abonelik',
        'abonelik',
        'expense',
        '#6366F1',
        'credit-card',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111120',
        null,
        null,
        'Tasarruf & Yatırım',
        'tasarruf-yatirim',
        'expense',
        '#10B981',
        'trending-up',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111121',
        null,
        null,
        'Kredi Ödemeleri',
        'kredi-odemeleri',
        'expense',
        '#4F46E5',
        'percent',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    ),
    (
        '11111111-1111-4111-8111-111111111122',
        null,
        null,
        'Diğer Gider',
        'diger-gider',
        'expense',
        '#6B7280',
        'help-circle',
        true,
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        1
    )
on conflict (id) do nothing;

commit;
