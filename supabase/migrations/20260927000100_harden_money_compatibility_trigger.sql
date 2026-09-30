-- ============================================================================
-- Migration: 20260927000100_harden_money_compatibility_trigger.sql
-- Sorumluluk: transactions_money_compat_before_write tetikleyicisi ve
--             public.sync_transactions_money_compat() fonksiyonundaki
--             üç doğrulanmış uyumluluk kusurunu forward-only olarak düzeltmek:
--             1. Eski web UPDATE (yalnız amount değiştiğinde amount_minor senkronizasyonu)
--             2. Yeni mobil UPDATE (yalnız amount_minor değiştiğinde amount senkronizasyonu)
--             3. Tutarsız amount ve amount_minor yazımlarının fail-closed reddi
-- Güvenlik ve Doğrulama İlkeleri:
--   - Out-of-range amount üzerinde ::bigint cast çalıştırmayan güvenli pre/post check.
--   - pg_trigger, pg_attribute ve tgfoid ile kesin katalog düzeyinde trigger doğrulaması.
--   - Kesin 'search_path=""' post-check denetimi.
--   - Trigger fonksiyonu doğrudan RPC olmadığından public, anon ve authenticated
--     rollerinden doğrudan EXECUTE yetkisinin kaldırılması.
--   - SECURITY INVOKER korunur.
--   - Pre-check ve Post-check ile atomik (begin/commit) yürütülür.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. PRE-CHECK KONTROLLERİ
-- ----------------------------------------------------------------------------
do $$
declare
    v_col_count integer;
    v_basic_drift integer;
    v_mismatch_drift integer;
    v_trg_total integer;
    v_trg_valid integer;
    v_trg_cols text[];
begin
    -- 1.1 public.transactions tablosu mevcut mu?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relkind = 'r'
    ) then
        raise exception 'Pre-check failed: public.transactions tablosu bulunamadı.';
    end if;

    -- 1.2 Kolonlar ve tipleri beklenen yapıda mı?
    select count(*)
    into v_col_count
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'transactions'
      and (
          (column_name = 'amount' and data_type = 'numeric') or
          (column_name = 'amount_minor' and data_type = 'bigint') or
          (column_name = 'currency' and data_type = 'text') or
          (column_name = 'user_id' and data_type = 'uuid')
      );

    if v_col_count <> 4 then
        raise exception 'Pre-check failed: transactions tablosunda gerekli para kolonları (amount, amount_minor, currency, user_id) eksik veya geçersiz tipte.';
    end if;

    -- 1.3 Eski fonksiyon tam imzasıyla mevcut mu?
    if to_regprocedure('public.sync_transactions_money_compat()') is null then
        raise exception 'Pre-check failed: public.sync_transactions_money_compat() fonksiyonu bulunamadı.';
    end if;

    -- 1.4 Hedef trigger katalog düzeyinde tam olarak beklenen yapıda mı?
    select
        count(*),
        count(*) filter (
            where t.tgenabled = 'O'
              and not t.tgisinternal
              and (t.tgtype & 1) != 0   -- row-level
              and (t.tgtype & 2) != 0   -- BEFORE
              and (t.tgtype & 4) != 0   -- INSERT
              and (t.tgtype & 16) != 0  -- UPDATE
              and (t.tgtype & 8) = 0    -- NOT DELETE
              and (t.tgtype & 32) = 0   -- NOT TRUNCATE
              and t.tgfoid = to_regprocedure('public.sync_transactions_money_compat()')
        ),
        (
            select coalesce(array_agg(a.attname::text order by a.attname), array[]::text[])
            from unnest(t.tgattr) as col_attnum
            join pg_catalog.pg_attribute a
              on a.attrelid = c.oid and a.attnum = col_attnum
        )
    into v_trg_total, v_trg_valid, v_trg_cols
    from pg_catalog.pg_trigger t
    join pg_catalog.pg_class c on c.oid = t.tgrelid
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname = 'transactions'
      and t.tgname = 'transactions_money_compat_before_write'
    group by t.tgattr, c.oid;

    if v_trg_total is null or v_trg_total <> 1 or v_trg_valid <> 1 then
        raise exception 'Pre-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz.';
    end if;

    if v_trg_cols <> array['amount', 'amount_minor', 'currency', 'user_id'] then
        raise exception 'Pre-check failed: transactions_money_compat_before_write trigger UPDATE OF kolonları beklenen küme değil: %', v_trg_cols;
    end if;

    -- 1.5 RLS açık mı?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relrowsecurity = true
    ) then
        raise exception 'Pre-check failed: public.transactions tablosunda RLS etkin değil.';
    end if;

    -- 1.6 Güvenli para veri doğrulaması (Out-of-range ::bigint cast hatası üretmeden):
    -- Adım 1.6.A: NULL, sıfır/negatif, ondalık hassasiyet (>2), BIGINT taşması, currency drift kontrolü:
    select count(*)
    into v_basic_drift
    from public.transactions
    where amount is null
       or amount_minor is null
       or amount <= 0
       or amount_minor <= 0
       or amount <> round(amount, 2)
       or amount > 92233720368547758.07
       or currency is null
       or currency not in ('TRY', 'USD', 'EUR');

    if v_basic_drift > 0 then
        raise exception 'Pre-check failed: public.transactions içinde para sözleşmesine uymayan (drift: %) adet satır bulundu.', v_basic_drift;
    end if;

    -- Adım 1.6.B: Yalnızca güvenli aralıktaki satırlarda cast korumalı tutarlılık kontrolü:
    select count(*)
    into v_mismatch_drift
    from public.transactions
    where case
        when amount is not null
         and amount > 0
         and amount = round(amount, 2)
         and amount <= 92233720368547758.07
        then round(amount * 100)::bigint <> amount_minor
        else false
    end;

    if v_mismatch_drift > 0 then
        raise exception 'Pre-check failed: public.transactions içinde para sözleşmesine uymayan (tutarsız: %) adet satır bulundu.', v_mismatch_drift;
    end if;
end;
$$;

-- ----------------------------------------------------------------------------
-- 2. GÜÇLENDİRİLMİŞ UYUMLULUK FONKSİYONU VE TETİKLEYİCİ
-- ----------------------------------------------------------------------------
create or replace function public.sync_transactions_money_compat()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_profile_currency text;
    v_amount_changed boolean;
    v_minor_changed boolean;
begin
    if tg_op = 'INSERT' then
        -- 1. Currency tespiti ve doğrulanması
        if new.currency is null then
            select p.currency
            into v_profile_currency
            from public.profiles as p
            where p.id = new.user_id;

            new.currency := v_profile_currency;
        end if;

        if new.currency is null or new.currency not in ('TRY', 'USD', 'EUR') then
            raise exception using
                errcode = '22000',
                message = 'Desteklenmeyen veya NULL currency değeri: ' || coalesce(new.currency, 'NULL');
        end if;

        -- 2. INSERT Alanlarının türetilmesi ve doğrulanması
        if new.amount is not null and new.amount_minor is null then
            -- Yalnız legacy amount verildi
            if new.amount <= 0 then
                raise exception using errcode = '22000', message = 'amount sıfırdan büyük olmalıdır.';
            end if;
            if new.amount <> round(new.amount, 2) then
                raise exception using errcode = '22000', message = 'amount en fazla iki ondalık basamağa sahip olabilir.';
            end if;
            if new.amount > 92233720368547758.07 then
                raise exception using errcode = '22003', message = 'amount BIGINT sınırını aşıyor.';
            end if;
            new.amount_minor := round(new.amount * 100)::bigint;

        elsif new.amount is null and new.amount_minor is not null then
            -- Yalnız mobil amount_minor verildi
            if new.amount_minor <= 0 then
                raise exception using errcode = '22000', message = 'amount_minor sıfırdan büyük olmalıdır.';
            end if;
            new.amount := new.amount_minor::numeric / 100;

        elsif new.amount is not null and new.amount_minor is not null then
            -- İki alan birlikte verildi (tutarlılık kontrolü)
            if new.amount <= 0 or new.amount_minor <= 0 then
                raise exception using errcode = '22000', message = 'amount ve amount_minor sıfırdan büyük olmalıdır.';
            end if;
            if new.amount <> round(new.amount, 2) then
                raise exception using errcode = '22000', message = 'amount en fazla iki ondalık basamağa sahip olabilir.';
            end if;
            if new.amount > 92233720368547758.07 then
                raise exception using errcode = '22003', message = 'amount BIGINT sınırını aşıyor.';
            end if;
            if round(new.amount * 100)::bigint <> new.amount_minor then
                raise exception using
                    errcode = '22000',
                    message = 'amount ve amount_minor tutarsız: amount=' || new.amount::text || ', amount_minor=' || new.amount_minor::text;
            end if;

        else
            -- İkisi de NULL
            raise exception using errcode = '22004', message = 'amount veya amount_minor belirtilmelidir.';
        end if;

    elsif tg_op = 'UPDATE' then
        -- 1. NULL yapma girişimlerini engelle
        if new.amount is null or new.amount_minor is null then
            raise exception using errcode = '22004', message = 'amount ve amount_minor NULL yapılamaz.';
        end if;

        if new.currency is null then
            raise exception using errcode = '22004', message = 'currency NULL yapılamaz.';
        end if;

        if new.currency not in ('TRY', 'USD', 'EUR') then
            raise exception using
                errcode = '22000',
                message = 'Desteklenmeyen currency değeri: ' || new.currency;
        end if;

        -- 2. Değişiklik tespiti (IS DISTINCT FROM OLD)
        v_amount_changed := (new.amount is distinct from old.amount);
        v_minor_changed := (new.amount_minor is distinct from old.amount_minor);

        if v_amount_changed and not v_minor_changed then
            -- Yalnız amount değişti (Eski web UPDATE) -> amount_minor türetilir
            if new.amount <= 0 then
                raise exception using errcode = '22000', message = 'amount sıfırdan büyük olmalıdır.';
            end if;
            if new.amount <> round(new.amount, 2) then
                raise exception using errcode = '22000', message = 'amount en fazla iki ondalık basamağa sahip olabilir.';
            end if;
            if new.amount > 92233720368547758.07 then
                raise exception using errcode = '22003', message = 'amount BIGINT sınırını aşıyor.';
            end if;
            new.amount_minor := round(new.amount * 100)::bigint;

        elsif v_minor_changed and not v_amount_changed then
            -- Yalnız amount_minor değişti (Yeni mobil UPDATE) -> amount türetilir
            if new.amount_minor <= 0 then
                raise exception using errcode = '22000', message = 'amount_minor sıfırdan büyük olmalıdır.';
            end if;
            new.amount := new.amount_minor::numeric / 100;

        elsif v_amount_changed and v_minor_changed then
            -- İkisi de değişti -> tam olarak tutarlı olmalı
            if new.amount <= 0 or new.amount_minor <= 0 then
                raise exception using errcode = '22000', message = 'amount ve amount_minor sıfırdan büyük olmalıdır.';
            end if;
            if new.amount <> round(new.amount, 2) then
                raise exception using errcode = '22000', message = 'amount en fazla iki ondalık basamağa sahip olabilir.';
            end if;
            if new.amount > 92233720368547758.07 then
                raise exception using errcode = '22003', message = 'amount BIGINT sınırını aşıyor.';
            end if;
            if round(new.amount * 100)::bigint <> new.amount_minor then
                raise exception using
                    errcode = '22000',
                    message = 'amount ve amount_minor tutarsız: amount=' || new.amount::text || ', amount_minor=' || new.amount_minor::text;
            end if;

        else
            -- İkisi de değişmedi (İlgisiz alan güncellemesi: description, receipt_path vb.)
            -- Mevcut tutarlılığı doğrula, tutarları değiştirme
            if round(new.amount * 100)::bigint <> new.amount_minor then
                raise exception using
                    errcode = '22000',
                    message = 'Mevcut satırdaki amount ve amount_minor tutarsız: amount=' || new.amount::text || ', amount_minor=' || new.amount_minor::text;
            end if;
        end if;
    end if;

    return new;
end;
$$;

-- Tetikleyiciyi yeniden bağla
drop trigger if exists transactions_money_compat_before_write on public.transactions;

create trigger transactions_money_compat_before_write
before insert or update of amount, amount_minor, currency, user_id
on public.transactions
for each row execute function public.sync_transactions_money_compat();

-- Yetki kısıtlaması: Trigger fonksiyonu doğrudan RPC çağrısı değildir.
-- PUBLIC, anon ve authenticated rollerinden doğrudan EXECUTE yetkisini kaldırıyoruz.
-- service_role için de ayrıca açık istemci grant'i eklemiyoruz.
revoke all on function public.sync_transactions_money_compat() from public;
revoke all on function public.sync_transactions_money_compat() from anon;
revoke all on function public.sync_transactions_money_compat() from authenticated;

-- ----------------------------------------------------------------------------
-- 3. POST-CHECK KONTROLLERİ
-- ----------------------------------------------------------------------------
do $$
declare
    v_prosecdef boolean;
    v_proconfig text[];
    v_basic_drift integer;
    v_mismatch_drift integer;
    v_trg_total integer;
    v_trg_valid integer;
    v_trg_cols text[];
begin
    -- 3.1 Fonksiyon mevcut ve security invoker mi?
    select p.prosecdef, p.proconfig
    into v_prosecdef, v_proconfig
    from pg_catalog.pg_proc p
    where p.oid = to_regprocedure('public.sync_transactions_money_compat()');

    if v_prosecdef is null then
        raise exception 'Post-check failed: sync_transactions_money_compat fonksiyonu bulunamadı.';
    end if;

    if v_prosecdef <> false then
        raise exception 'Post-check failed: sync_transactions_money_compat SECURITY DEFINER olmamalıdır (SECURITY INVOKER bekleniyor).';
    end if;

    -- search_path tam olarak search_path="" olmalıdır:
    if v_proconfig is null or not ('search_path=""' = any(v_proconfig)) then
        raise exception 'Post-check failed: sync_transactions_money_compat search_path güvenli şekilde sabitlenmemiş (beklenen: search_path="", mevcut: %).', v_proconfig;
    end if;

    -- 3.2 İstemci rolleri doğrudan EXECUTE yetkisine sahip olmamalı:
    if has_function_privilege('public', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE') then
        raise exception 'Post-check failed: public rolünün sync_transactions_money_compat üzerinde doğrudan EXECUTE yetkisi olmamalıdır.';
    end if;

    if has_function_privilege('anon', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE') then
        raise exception 'Post-check failed: anon rolünün sync_transactions_money_compat üzerinde doğrudan EXECUTE yetkisi olmamalıdır.';
    end if;

    if has_function_privilege('authenticated', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE') then
        raise exception 'Post-check failed: authenticated rolünün sync_transactions_money_compat üzerinde doğrudan EXECUTE yetkisi olmamalıdır.';
    end if;

    -- 3.3 Trigger katalog düzeyinde tam olarak beklenen yapıda mı?
    select
        count(*),
        count(*) filter (
            where t.tgenabled = 'O'
              and not t.tgisinternal
              and (t.tgtype & 1) != 0   -- row-level
              and (t.tgtype & 2) != 0   -- BEFORE
              and (t.tgtype & 4) != 0   -- INSERT
              and (t.tgtype & 16) != 0  -- UPDATE
              and (t.tgtype & 8) = 0    -- NOT DELETE
              and (t.tgtype & 32) = 0   -- NOT TRUNCATE
              and t.tgfoid = to_regprocedure('public.sync_transactions_money_compat()')
        ),
        (
            select coalesce(array_agg(a.attname::text order by a.attname), array[]::text[])
            from unnest(t.tgattr) as col_attnum
            join pg_catalog.pg_attribute a
              on a.attrelid = c.oid and a.attnum = col_attnum
        )
    into v_trg_total, v_trg_valid, v_trg_cols
    from pg_catalog.pg_trigger t
    join pg_catalog.pg_class c on c.oid = t.tgrelid
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname = 'transactions'
      and t.tgname = 'transactions_money_compat_before_write'
    group by t.tgattr, c.oid;

    if v_trg_total is null or v_trg_total <> 1 or v_trg_valid <> 1 then
        raise exception 'Post-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz.';
    end if;

    if v_trg_cols <> array['amount', 'amount_minor', 'currency', 'user_id'] then
        raise exception 'Post-check failed: transactions_money_compat_before_write trigger UPDATE OF kolonları beklenen küme değil: %', v_trg_cols;
    end if;

    -- 3.4 Güvenli para veri doğrulaması (Out-of-range ::bigint cast hatası üretmeden):
    select count(*)
    into v_basic_drift
    from public.transactions
    where amount is null
       or amount_minor is null
       or amount <= 0
       or amount_minor <= 0
       or amount <> round(amount, 2)
       or amount > 92233720368547758.07
       or currency is null
       or currency not in ('TRY', 'USD', 'EUR');

    if v_basic_drift > 0 then
        raise exception 'Post-check failed: public.transactions içinde para sözleşmesine uymayan (drift: %) adet satır bulundu.', v_basic_drift;
    end if;

    select count(*)
    into v_mismatch_drift
    from public.transactions
    where case
        when amount is not null
         and amount > 0
         and amount = round(amount, 2)
         and amount <= 92233720368547758.07
        then round(amount * 100)::bigint <> amount_minor
        else false
    end;

    if v_mismatch_drift > 0 then
        raise exception 'Post-check failed: public.transactions içinde para sözleşmesine uymayan (tutarsız: %) adet satır bulundu.', v_mismatch_drift;
    end if;

    -- 3.5 RLS açık kalmış mı?
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'transactions' and c.relrowsecurity = true
    ) then
        raise exception 'Post-check failed: public.transactions tablosunda RLS kapatılmış.';
    end if;
end;
$$;

commit;
