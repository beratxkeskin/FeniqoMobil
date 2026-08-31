-- FeniqoMobil V1 Sistem Varsayılan Kategorileri Post-Migration Sözleşme Doğrulaması
-- Bu dosya, 20260816000100_seed_default_categories_v1.sql uygulandıktan sonra veritabanı durumunu
-- doğrulamak amacıyla çalıştırılan post-migration sözleşme kontrolüdür.
-- İşlem sonunda veritabanında geçici durum bırakmamak için ROLLBACK ile sonlanır.
begin;

do $$
declare
    v_total_count integer;
    v_income_count integer;
    v_expense_count integer;
    v_missing_or_mismatched_count integer;
    v_index_exists boolean;
begin
    -- 1. TOPLAM VE TÜR DAĞILIMI DOĞRULAMASI
    select count(*) into v_total_count
    from public.categories
    where is_default and user_id is null and workspace_id is null and deleted_at is null;

    select count(*) into v_income_count
    from public.categories
    where is_default and user_id is null and workspace_id is null and deleted_at is null and type = 'income';

    select count(*) into v_expense_count
    from public.categories
    where is_default and user_id is null and workspace_id is null and deleted_at is null and type = 'expense';

    assert v_total_count = 17, 'Sözleşme Hatası: Toplam 17 varsayılan kategori bulunmalıdır. Bulunan: ' || v_total_count;
    assert v_income_count = 5, 'Sözleşme Hatası: 5 gelir kategorisi bulunmalıdır. Bulunan: ' || v_income_count;
    assert v_expense_count = 12, 'Sözleşme Hatası: 12 gider kategorisi bulunmalıdır. Bulunan: ' || v_expense_count;

    -- 2. CANONICAL 17 KATEGORİ ALANLARININ TAM KARŞILAŞTIRILMASI
    create temporary table temp_expected_canonical_categories (
        id uuid primary key,
        name text not null,
        slug text not null,
        type text not null,
        color text not null,
        icon text not null
    ) on commit drop;

    insert into temp_expected_canonical_categories (id, name, slug, type, color, icon)
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

    select count(*) into v_missing_or_mismatched_count
    from temp_expected_canonical_categories e
    left join public.categories c on e.id = c.id
    where c.id is null
       or c.name is distinct from e.name
       or c.slug is distinct from e.slug
       or c.type is distinct from e.type
       or c.color is distinct from e.color
       or c.icon is distinct from e.icon
       or c.user_id is not null
       or c.workspace_id is not null
       or c.is_default is distinct from true
       or c.deleted_at is not null
       or c.version <> 1;

    assert v_missing_or_mismatched_count = 0,
        'Sözleşme Hatası: Canonical varsayılan kategori tanımlarından ' || v_missing_or_mismatched_count || ' tanesi eksik veya uyumsuz.';

    -- 3. PARTIAL UNIQUE INDEX VARLIĞI VE PREDICATE DOĞRULAMASI
    select exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
          and tablename = 'categories'
          and indexname = 'categories_default_slug_idx'
          and indexdef like '%UNIQUE%'
          and indexdef like '%(slug)%'
          and indexdef like '%WHERE (is_default AND (user_id IS NULL) AND (workspace_id IS NULL) AND (deleted_at IS NULL))%'
    ) into v_index_exists;

    assert v_index_exists, 'Sözleşme Hatası: categories_default_slug_idx partial unique index beklenen predicate ile mevcut değil.';
end $$;

rollback;
