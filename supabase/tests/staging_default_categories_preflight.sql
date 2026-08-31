-- ==============================================================================
-- FENIQOMOBIL-STAGING: Varsayılan Kategori Seed Öncesi Salt Okunur Preflight Denetimi
-- Dosya: supabase/tests/staging_default_categories_preflight.sql
-- Kapsam: Yalnızca SELECT sorguları içerir. Hiçbir veri veya şema değişikliği yapmaz.
-- ==============================================================================

-- 1. TABLO VE KOLON ŞEMA DENETİMİ (Varoluş, Veri Tipi, Nullability)
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'categories'
order by ordinal_position;

-- 2. STAGING CATEGORIES TABLOSUNDA KULLANILAN MEVCUT 'type' DEĞERLERİ
select
    type,
    count(*) as row_count
from public.categories
group by type
order by type;

-- 3. RLS (ROW LEVEL SECURITY) VE MEVCUT POLİTİKALAR
select
    relname as table_name,
    relrowsecurity as rls_enabled,
    relforcerowsecurity as rls_forced
from pg_class
where relnamespace = 'public'::regnamespace
  and relname = 'categories';

select
    policyname,
    cmd,
    roles,
    qual,
    with_check
from pg_policies
where schemaname = 'public'
  and tablename = 'categories'
order by policyname;

-- 4. MEVCUT AKTİF GLOBAL VARSAYILAN KATEGORİLER VE GELİR/GİDER DAĞILIMI
select
    count(*) as total_active_default_count,
    count(*) filter (where type = 'income') as default_income_count,
    count(*) filter (where type = 'expense') as default_expense_count
from public.categories
where is_default = true
  and user_id is null
  and workspace_id is null
  and deleted_at is null;

-- 5. CANONICAL 17 UUID ÇAKIŞMA / VARLIK DENETİMİ
with canonical_catalog(id, name, slug, type, color, icon) as (
    values
        ('11111111-1111-4111-8111-111111111101'::uuid, 'Maaş', 'maas', 'income', '#10B981', 'briefcase'),
        ('11111111-1111-4111-8111-111111111102'::uuid, 'Freelance', 'freelance', 'income', '#34D399', 'laptop'),
        ('11111111-1111-4111-8111-111111111103'::uuid, 'Burs', 'burs', 'income', '#6EE7B7', 'graduation-cap'),
        ('11111111-1111-4111-8111-111111111104'::uuid, 'Yatırım', 'yatirim', 'income', '#059669', 'trending-up'),
        ('11111111-1111-4111-8111-111111111105'::uuid, 'Diğer Gelir', 'diger-gelir', 'income', '#A7F3D0', 'dollar-sign'),
        ('11111111-1111-4111-8111-111111111111'::uuid, 'Yemek', 'yemek', 'expense', '#FBBF24', 'utensils'),
        ('11111111-1111-4111-8111-111111111112'::uuid, 'Market', 'market', 'expense', '#EF4444', 'shopping-cart'),
        ('11111111-1111-4111-8111-111111111113'::uuid, 'Ulaşım', 'ulasim', 'expense', '#F59E0B', 'car'),
        ('11111111-1111-4111-8111-111111111114'::uuid, 'Kira', 'kira', 'expense', '#3B82F6', 'home'),
        ('11111111-1111-4111-8111-111111111115'::uuid, 'Fatura', 'fatura', 'expense', '#10B981', 'file-text'),
        ('11111111-1111-4111-8111-111111111116'::uuid, 'Eğlence', 'eglence', 'expense', '#EC4899', 'music'),
        ('11111111-1111-4111-8111-111111111117'::uuid, 'Eğitim', 'egitim', 'expense', '#8B5CF6', 'book-open'),
        ('11111111-1111-4111-8111-111111111118'::uuid, 'Sağlık', 'saglik', 'expense', '#EF4444', 'heart-pulse'),
        ('11111111-1111-4111-8111-111111111119'::uuid, 'Abonelik', 'abonelik', 'expense', '#6366F1', 'credit-card'),
        ('11111111-1111-4111-8111-111111111120'::uuid, 'Tasarruf & Yatırım', 'tasarruf-yatirim', 'expense', '#10B981', 'trending-up'),
        ('11111111-1111-4111-8111-111111111121'::uuid, 'Kredi Ödemeleri', 'kredi-odemeleri', 'expense', '#4F46E5', 'percent'),
        ('11111111-1111-4111-8111-111111111122'::uuid, 'Diğer Gider', 'diger-gider', 'expense', '#6B7280', 'help-circle')
)
select
    c.id as canonical_id,
    c.slug as canonical_slug,
    c.name as canonical_name,
    case when db.id is not null then 'MEVCUT' else 'YOK' end as db_status,
    case
        when db.id is null then 'Eklenebilir'
        when db.user_id is not null or db.workspace_id is not null or db.is_default is distinct from true or db.deleted_at is not null
            then 'HATA: Kullanıcı/özel satırı veya silinmiş kayıt ile çakışma!'
        when db.slug is distinct from c.slug
          or db.type is distinct from c.type
          or db.name is distinct from c.name
          or db.color is distinct from c.color
          or db.icon is distinct from c.icon
            then 'HATA: Canonical alan uyuşmazlığı!'
        else 'Tam Eşleşen Sistem Kaydı'
    end as preflight_verdict
from canonical_catalog c
left join public.categories db on c.id = db.id
order by c.id;

-- 6. CANONICAL SLUG'LARIN FARKLI UUID'LER ÜZERİNDE KULLANIMI (Slug Çakışma Denetimi)
with canonical_catalog(id, slug) as (
    values
        ('11111111-1111-4111-8111-111111111101'::uuid, 'maas'),
        ('11111111-1111-4111-8111-111111111102'::uuid, 'freelance'),
        ('11111111-1111-4111-8111-111111111103'::uuid, 'burs'),
        ('11111111-1111-4111-8111-111111111104'::uuid, 'yatirim'),
        ('11111111-1111-4111-8111-111111111105'::uuid, 'diger-gelir'),
        ('11111111-1111-4111-8111-111111111111'::uuid, 'yemek'),
        ('11111111-1111-4111-8111-111111111112'::uuid, 'market'),
        ('11111111-1111-4111-8111-111111111113'::uuid, 'ulasim'),
        ('11111111-1111-4111-8111-111111111114'::uuid, 'kira'),
        ('11111111-1111-4111-8111-111111111115'::uuid, 'fatura'),
        ('11111111-1111-4111-8111-111111111116'::uuid, 'eglence'),
        ('11111111-1111-4111-8111-111111111117'::uuid, 'egitim'),
        ('11111111-1111-4111-8111-111111111118'::uuid, 'saglik'),
        ('11111111-1111-4111-8111-111111111119'::uuid, 'abonelik'),
        ('11111111-1111-4111-8111-111111111120'::uuid, 'tasarruf-yatirim'),
        ('11111111-1111-4111-8111-111111111121'::uuid, 'kredi-odemeleri'),
        ('11111111-1111-4111-8111-111111111122'::uuid, 'diger-gider')
)
select
    c.slug as canonical_slug,
    db.id as conflicting_db_id,
    db.is_default,
    db.user_id is not null as has_user_owner
from canonical_catalog c
join public.categories db on c.slug = db.slug
where c.id <> db.id;

-- 7. PARTIAL UNIQUE INDEX VE MEVCUT İNDEKS DURUMU
select
    indexname,
    indexdef
from pg_indexes
where schemaname = 'public'
  and tablename = 'categories'
order by indexname;

-- 8. PARTIAL UNIQUE INDEX OLUŞTURULMASINI ENGELLEYEBİLECEK ÇİFTLENEN DEFAULT SLUG VAR MI?
select
    slug,
    count(*) as occurrence_count
from public.categories
where is_default = true
  and user_id is null
  and workspace_id is null
  and deleted_at is null
  and slug is not null
group by slug
having count(*) > 1;
