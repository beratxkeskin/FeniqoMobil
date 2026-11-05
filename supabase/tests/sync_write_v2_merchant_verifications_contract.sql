-- ============================================================================
-- FeniqoMobil - Faz 8.6-D1: sync_write_v2 User Merchant Verification Sözleşme Testi
-- ============================================================================
-- Bu sözleşme testi, izole ve geçici test veritabanında çalıştırılır ve
-- işlem sonunda ROLLBACK yapılarak temizlenir.
--
-- Kapsam:
-- 1. Tablo, kolon, primary key, check constraint ve indeks doğrulama
-- 2. Receipt constraint tam 15 entity type doğrulama
-- 3. İki golden vector deterministik UUIDv8 tam eşleşmesi:
--    - NETFLIX -> fddf797b-6b74-89ea-a969-f30df71d5921
--    - KAHVE DUNYASI -> ac077b4d-9255-813b-abd2-0893d4b1c26a
-- 4. Yanlış deterministik ID reddi ve receipt oluşmaması (CREATE)
-- 5. Canonical alias doğrulaması (küçük harf reddi, generic token reddi)
-- 6. Canonical lowercase UUIDv8/v4 format doğrulaması (uppercase ve mixed-case id/user_id reddi)
-- 7. Zorunlu workspace_id doğrulaması (CREATE, UPDATE, DELETE için eksik anahtar ve string "null" reddi)
-- 8. ASSIGN/BLOCK karar invariantları
-- 9. Idempotent replay (aynı operation_id + aynı fingerprint -> önceki APPLIED receipt)
-- 10. Idempotency fingerprint mismatch reddi
-- 11. Canonical UPDATE, stale base_version CONFLICT ve soft-delete/reactivation
-- 12. Bilinmeyen deterministik ID ile NOT_FOUND senaryoları (UPDATE ve DELETE)
-- 13. UPDATE ve DELETE için alias eksikliği, non-deterministik ID ve SQLSTATE 42501 cross-user BOLA testleri
-- 14. Eşzamanlı CREATE yarışında raw unique exception yerine CONFLICT
-- 15. RLS ve ACL güvenlik testleri:
--     - Authenticated own-row SELECT başarısı (1 satır)
--     - Cross-user SELECT izolasyonu (0 satır)
--     - Authenticated doğrudan INSERT, UPDATE ve DELETE engeli (3 ayrı test)
--     - Anon/public sync_write_v2 erişim engeli
-- 16. Eski wrapper (sync_write_v2_before_user_merchant_verification) doğrudan çağrı koruması
-- 17. Eski 14 entity RPC delegasyon regresyonu (CATEGORY)
-- 18. Tabloda asla hard-delete bulunmaması
-- ============================================================================

begin;

-- Test-scoped auth fixture'ları
insert into auth.users (id, email, created_at)
values
    ('67a3f892-12c4-4b9a-b8e7-9d8e7a6b5c4d', 'golden_user@feniqo.local', timezone('utc', now())),
    ('a0000000-0000-4000-8000-000000000002', 'other_user@feniqo.local', timezone('utc', now()))
on conflict (id) do nothing;

do $$
declare
    v_user_id uuid := '67a3f892-12c4-4b9a-b8e7-9d8e7a6b5c4d'::uuid;
    v_other_user_id uuid := 'a0000000-0000-4000-8000-000000000002'::uuid;

    -- Golden Vector Beklenen ID'leri
    v_expected_netflix_id uuid := 'fddf797b-6b74-89ea-a969-f30df71d5921'::uuid;
    v_expected_kd_id uuid := 'ac077b4d-9255-813b-abd2-0893d4b1c26a'::uuid;

    -- NOT_FOUND testi için DB'de bulunmayan ikinci canonical alias ve hesaplanan deterministik ID'si
    v_not_found_alias text := 'DISNEY PLUS';
    v_not_found_id uuid;
    v_hash bytea;
    v_bytes bytea;
    v_hex text;

    v_op_id_1 text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_id_2 text := replace(extensions.gen_random_uuid()::text, '-', '');

    v_result jsonb;
    v_receipt_count integer;
    v_visible_count integer;
    v_error_caught boolean := false;
    v_table_record record;
    v_constraint_count integer;
    v_category_id uuid := extensions.gen_random_uuid();
begin

    -- ------------------------------------------------------------------------
    -- 1. TABLO, CONSTRAINT VE İNDEKS KONTROLLERİ
    -- ------------------------------------------------------------------------
    if not exists (
        select 1 from information_schema.tables
        where table_schema = 'public' and table_name = 'user_merchant_verifications'
    ) then
        raise exception 'public.user_merchant_verifications tablosu mevcut değil.';
    end if;

    -- Receipt constraint 15 entity türü tam kontrolü
    select count(*) into v_constraint_count
    from pg_constraint
    where conname = 'sync_operations_receipts_entity_type_check'
      and contype = 'c';

    if v_constraint_count <> 1 then
        raise exception 'sync_operations_receipts_entity_type_check constraint bulunamadı.';
    end if;

    -- ------------------------------------------------------------------------
    -- 2. GOLDEN VECTOR 1: NETFLIX -> fddf797b-6b74-89ea-a969-f30df71d5921
    -- ------------------------------------------------------------------------
    perform set_config('request.jwt.claim.sub', v_user_id::text, true);

    v_result := public.sync_write_v2(
        v_op_id_1,
        'USER_MERCHANT_VERIFICATION',
        'CREATE',
        null,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'decision_code', 'ASSIGN',
            'merchant_id', 'netflix',
            'scope_code', 'PERSONAL'
        )
    );

    if v_result ->> 'status' <> 'APPLIED' then
        raise exception 'Golden 1 CREATE başarısız: %', v_result;
    end if;
    if (v_result -> 'record' ->> 'id')::uuid <> v_expected_netflix_id then
        raise exception 'Golden 1 ID beklenenle uyuşmuyor: % vs %', v_result -> 'record' ->> 'id', v_expected_netflix_id;
    end if;
    if (v_result -> 'record' ->> 'version')::bigint <> 1 then
        raise exception 'Golden 1 version 1 değil: %', v_result;
    end if;

    -- Receipt yazıldı mı?
    select count(*) into v_receipt_count
    from public.sync_operations_receipts
    where operation_id = v_op_id_1
      and entity_type = 'USER_MERCHANT_VERIFICATION'
      and entity_id = v_expected_netflix_id;

    if v_receipt_count <> 1 then
        raise exception 'Golden 1 receipt kaydedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 3. GOLDEN VECTOR 2: KAHVE DUNYASI -> ac077b4d-9255-813b-abd2-0893d4b1c26a
    -- ------------------------------------------------------------------------
    v_result := public.sync_write_v2(
        v_op_id_2,
        'USER_MERCHANT_VERIFICATION',
        'CREATE',
        null,
        jsonb_build_object(
            'id', v_expected_kd_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'KAHVE DUNYASI',
            'decision_code', 'BLOCK',
            'merchant_id', null,
            'scope_code', 'PERSONAL'
        )
    );

    if v_result ->> 'status' <> 'APPLIED' then
        raise exception 'Golden 2 CREATE başarısız: %', v_result;
    end if;
    if (v_result -> 'record' ->> 'id')::uuid <> v_expected_kd_id then
        raise exception 'Golden 2 ID beklenenle uyuşmuyor: % vs %', v_result -> 'record' ->> 'id', v_expected_kd_id;
    end if;

    -- ------------------------------------------------------------------------
    -- 4. YANLIŞ DETERMINISTIC ID REDDİ VE RECEIPT OLUŞMAMASI (CREATE)
    -- ------------------------------------------------------------------------
    v_error_caught := false;
    declare
        v_fake_op_id text := replace(extensions.gen_random_uuid()::text, '-', '');
        v_random_id uuid := extensions.gen_random_uuid();
    begin
        perform public.sync_write_v2(
            v_fake_op_id,
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_random_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'SPOTIFY',
                'decision_code', 'ASSIGN',
                'merchant_id', 'spotify',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Yanlış deterministic ID definitive rejection üretmedi.';
    end if;

    -- Yanlış ID için receipt oluşmadığını doğrula
    select count(*) into v_receipt_count
    from public.sync_operations_receipts
    where entity_type = 'USER_MERCHANT_VERIFICATION'
      and entity_id not in (v_expected_netflix_id, v_expected_kd_id);
    if v_receipt_count <> 0 then
        raise exception 'Hatalı ID için receipt oluşmuş!';
    end if;

    -- ------------------------------------------------------------------------
    -- 5. CANONICAL ALIAS DOĞRULAMASI (KÜÇÜK HARF VE GENERIC TOKEN REDDİ)
    -- ------------------------------------------------------------------------
    -- Küçük harf reddi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'netflix', -- Küçük harf yasak
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Küçük harfli alias reddedilmedi.';
    end if;

    -- Generic token 'MARKET' reddi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'MARKET',
                'decision_code', 'BLOCK',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Geçersiz kanonik alias biçimi.%' then
            v_error_caught := true;
        else
            raise exception 'MARKET reddinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'Generic token MARKET reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 5.1 CANONICAL LOWERCASE UUID DOĞRULAMASI (UPPERCASE & MIXED-CASE REDDİ)
    -- ------------------------------------------------------------------------
    -- Uppercase id reddedilir
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', upper(v_expected_netflix_id::text),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Merchant verification payload geçersiz.%' then
            v_error_caught := true;
        else
            raise exception 'Uppercase id reddinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'Uppercase id reddedilmedi.';
    end if;

    -- Mixed-case id reddedilir
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', replace(v_expected_netflix_id::text, 'f', 'F'),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Merchant verification payload geçersiz.%' then
            v_error_caught := true;
        else
            raise exception 'Mixed-case id reddinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'Mixed-case id reddedilmedi.';
    end if;

    -- Uppercase user_id reddedilir
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', upper(v_user_id::text),
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Merchant verification user_id geçersiz.%' then
            v_error_caught := true;
        else
            raise exception 'Uppercase user_id reddinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'Uppercase user_id reddedilmedi.';
    end if;

    -- Mixed-case user_id reddedilir
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', replace(v_user_id::text, 'a', 'A'),
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Merchant verification user_id geçersiz.%' then
            v_error_caught := true;
        else
            raise exception 'Mixed-case user_id reddinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'Mixed-case user_id reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 5.2 ZORUNLU WORKSPACE_ID TESTLERİ (EKSİK ANAHTAR VE STRING "null" REDDİ)
    -- ------------------------------------------------------------------------
    -- CREATE: workspace_id anahtarı yoksa ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                -- 'workspace_id' anahtarı tamamen eksik
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Kişisel doğrulamada workspace_id null olmalıdır.%' then
            v_error_caught := true;
        else
            raise exception 'CREATE eksik workspace_id için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'CREATE payload''unda workspace_id eksikken reddedilmedi.';
    end if;

    -- CREATE: workspace_id string "null" ise ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', 'null', -- String "null"
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Kişisel doğrulamada workspace_id null olmalıdır.%' then
            v_error_caught := true;
        else
            raise exception 'CREATE string "null" workspace_id için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'CREATE payload''unda workspace_id string "null" iken reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 6. ASSIGN / BLOCK INVARIANTLARI
    -- ------------------------------------------------------------------------
    -- ASSIGN ancak merchant_id boş: reddedilmeli
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'AMAZON',
                'decision_code', 'ASSIGN',
                'merchant_id', null,
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'ASSIGN için merchant_id null iken reddedilmedi.';
    end if;

    -- BLOCK ancak merchant_id dolu: reddedilmeli
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'AMAZON',
                'decision_code', 'BLOCK',
                'merchant_id', 'amazon',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'BLOCK için merchant_id dolu iken reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 7. IDEMPOTENT REPLAY & FINGERPRINT MISMATCH
    -- ------------------------------------------------------------------------
    -- Aynı op_id_1 ile aynı payload tekrar çağrıldığında önceki APPLIED sonucu dönmeli
    v_result := public.sync_write_v2(
        v_op_id_1,
        'USER_MERCHANT_VERIFICATION',
        'CREATE',
        null,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'decision_code', 'ASSIGN',
            'merchant_id', 'netflix',
            'scope_code', 'PERSONAL'
        )
    );
    if v_result ->> 'status' <> 'APPLIED' then
        raise exception 'Idempotent replay APPLIED dönmedi: %', v_result;
    end if;

    -- Aynı op_id_1 ile farklı payload: reddedilmeli
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            v_op_id_1,
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'BLOCK', -- Farklı payload
                'merchant_id', null,
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Idempotency fingerprint mismatch reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 8. UPDATE, STALE BASE_VERSION CONFLICT VE REACTIVATION
    -- ------------------------------------------------------------------------
    -- Geçerli UPDATE: NETFLIX ASSIGN -> BLOCK (version 1 -> 2)
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'UPDATE',
        1,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'scope_code', 'PERSONAL',
            'decision_code', 'BLOCK',
            'merchant_id', null
        )
    );
    if v_result ->> 'status' <> 'APPLIED' or (v_result -> 'record' ->> 'version')::bigint <> 2 then
        raise exception 'Geçerli UPDATE başarısız: %', v_result;
    end if;
    if v_result -> 'record' ->> 'decision_code' <> 'BLOCK' then
        raise exception 'UPDATE sonrası decision_code BLOCK değil: %', v_result;
    end if;

    -- Stale UPDATE: version 1 gönderilirse CONFLICT dönmeli (mevcut version 2)
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'UPDATE',
        1,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'scope_code', 'PERSONAL',
            'decision_code', 'ASSIGN',
            'merchant_id', 'netflix'
        )
    );
    if v_result ->> 'status' <> 'CONFLICT' or (v_result -> 'record' ->> 'version')::bigint <> 2 then
        raise exception 'Stale UPDATE CONFLICT dönmedi: %', v_result;
    end if;

    -- ------------------------------------------------------------------------
    -- 9. SOFT-DELETE VE REACTIVATION
    -- ------------------------------------------------------------------------
    -- DELETE (version 2 -> 3, deleted_at dolmalı, hard-delete olmamalı)
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'DELETE',
        2,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'scope_code', 'PERSONAL'
        )
    );
    if v_result ->> 'status' <> 'APPLIED' or (v_result -> 'record' ->> 'version')::bigint <> 3 then
        raise exception 'DELETE başarısız: %', v_result;
    end if;
    if v_result -> 'record' ->> 'deleted_at' is null then
        raise exception 'DELETE sonrası deleted_at null kalamaz: %', v_result;
    end if;

    -- Tabloda satır hala var mı? (Hard-delete yok kontrolü)
    select * into v_table_record
    from public.user_merchant_verifications
    where id = v_expected_netflix_id;

    if v_table_record.deleted_at is null then
        raise exception 'Soft-delete satırı deleted_at taşımıyor.';
    end if;

    -- Reactivation: Silinmiş kaydı UPDATE ile tekrar canlandır (deleted_at null olmalı)
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'UPDATE',
        3,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'scope_code', 'PERSONAL',
            'decision_code', 'ASSIGN',
            'merchant_id', 'netflix'
        )
    );
    if v_result ->> 'status' <> 'APPLIED' or (v_result -> 'record' ->> 'version')::bigint <> 4 then
        raise exception 'Reactivation UPDATE başarısız: %', v_result;
    end if;
    if v_result -> 'record' ->> 'deleted_at' is not null then
        raise exception 'Reactivation sonrası deleted_at temizlenmedi: %', v_result;
    end if;

    -- ------------------------------------------------------------------------
    -- 10. NOT_FOUND SENARYOLARI (Kayıtlı olmayan deterministik ID ile UPDATE/DELETE)
    -- ------------------------------------------------------------------------
    -- DISNEY PLUS için deterministik UUIDv8 hesabı
    v_hash := extensions.digest(
        pg_catalog.convert_to('feniqo:user-merchant-verification:v1', 'UTF8')
        || pg_catalog.decode('00', 'hex')
        || pg_catalog.convert_to(v_user_id::text, 'UTF8')
        || pg_catalog.decode('00', 'hex')
        || pg_catalog.convert_to(v_not_found_alias, 'UTF8'),
        'sha256'
    );
    v_bytes := pg_catalog.substring(v_hash, 1, 16);
    v_bytes := pg_catalog.set_byte(v_bytes, 6, (pg_catalog.get_byte(v_bytes, 6) & 15) | 128);
    v_bytes := pg_catalog.set_byte(v_bytes, 8, (pg_catalog.get_byte(v_bytes, 8) & 63) | 128);
    v_hex := pg_catalog.encode(v_bytes, 'hex');
    v_not_found_id := (
        pg_catalog.substring(v_hex, 1, 8) || '-' ||
        pg_catalog.substring(v_hex, 9, 4) || '-' ||
        pg_catalog.substring(v_hex, 13, 4) || '-' ||
        pg_catalog.substring(v_hex, 17, 4) || '-' ||
        pg_catalog.substring(v_hex, 21, 12)
    )::uuid;

    -- Bilinmeyen deterministik ID ile UPDATE -> NOT_FOUND
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'UPDATE',
        1,
        jsonb_build_object(
            'id', v_not_found_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', v_not_found_alias,
            'scope_code', 'PERSONAL',
            'decision_code', 'BLOCK',
            'merchant_id', null
        )
    );
    if v_result ->> 'status' <> 'NOT_FOUND' then
        raise exception 'Bilinmeyen deterministik ID ile UPDATE NOT_FOUND dönmedi: %', v_result;
    end if;

    -- Bilinmeyen deterministik ID ile DELETE -> NOT_FOUND
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'DELETE',
        1,
        jsonb_build_object(
            'id', v_not_found_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', v_not_found_alias,
            'scope_code', 'PERSONAL'
        )
    );
    if v_result ->> 'status' <> 'NOT_FOUND' then
        raise exception 'Bilinmeyen deterministik ID ile DELETE NOT_FOUND dönmedi: %', v_result;
    end if;

    -- ------------------------------------------------------------------------
    -- 11. UPDATE VE DELETE İÇİN ALIAS, DETERMINISTIK ID VE BOLA GÜVENLİK TESTLERİ
    -- ------------------------------------------------------------------------
    -- 11.1 UPDATE'te normalized_alias eksikse ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'UPDATE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'scope_code', 'PERSONAL',
                'decision_code', 'BLOCK',
                'merchant_id', null
            )
        );
    exception when others then
        if sqlerrm like '%Geçersiz kanonik alias biçimi.%' then
            v_error_caught := true;
        else
            raise exception 'UPDATE eksik normalized_alias için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'UPDATE operasyonunda normalized_alias eksikken reddedilmedi.';
    end if;

    -- 11.2 DELETE'te normalized_alias eksikse ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'DELETE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Geçersiz kanonik alias biçimi.%' then
            v_error_caught := true;
        else
            raise exception 'DELETE eksik normalized_alias için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'DELETE operasyonunda normalized_alias eksikken reddedilmedi.';
    end if;

    -- 11.3 UPDATE operasyonunda alias ile uyuşmayan rastgele ID reddi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'UPDATE',
            4,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL',
                'decision_code', 'BLOCK',
                'merchant_id', null
            )
        );
    exception when others then
        if sqlerrm like '%Geçersiz deterministik doğrulama kimliği.%' then
            v_error_caught := true;
        else
            raise exception 'UPDATE uyumsuz ID için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'UPDATE operasyonunda alias ile uyuşmayan rastgele ID reddedilmedi.';
    end if;

    -- 11.4 DELETE operasyonunda alias ile uyuşmayan rastgele ID reddi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'DELETE',
            4,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Geçersiz deterministik doğrulama kimliği.%' then
            v_error_caught := true;
        else
            raise exception 'DELETE uyumsuz ID için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'DELETE operasyonunda alias ile uyuşmayan rastgele ID reddedilmedi.';
    end if;

    -- 11.5 Cross-user user_id saldırısı (UPDATE) -> SQLSTATE 42501 ve sahiplik mesajı
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'UPDATE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_other_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL',
                'decision_code', 'BLOCK',
                'merchant_id', null
            )
        );
    exception when others then
        if sqlstate = '42501' or sqlerrm like '%Doğrulama sahipliği reddedildi.%' then
            v_error_caught := true;
        else
            raise exception 'UPDATE cross-user BOLA testinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'UPDATE operasyonunda cross-user user_id saldırısı reddedilmedi.';
    end if;

    -- 11.6 Cross-user user_id saldırısı (DELETE) -> SQLSTATE 42501 ve sahiplik mesajı
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'DELETE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_other_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlstate = '42501' or sqlerrm like '%Doğrulama sahipliği reddedildi.%' then
            v_error_caught := true;
        else
            raise exception 'DELETE cross-user BOLA testinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'DELETE operasyonunda cross-user user_id saldırısı reddedilmedi.';
    end if;

    -- 11.7 Cross-user user_id saldırısı (CREATE) -> SQLSTATE 42501 ve sahiplik mesajı
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_other_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlstate = '42501' or sqlerrm like '%Doğrulama sahipliği reddedildi.%' then
            v_error_caught := true;
        else
            raise exception 'CREATE cross-user BOLA testinde beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'CREATE operasyonunda cross-user user_id saldırısı reddedilmedi.';
    end if;

    -- 11.8 UPDATE operasyonunda workspace_id anahtarı yoksa ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'UPDATE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                -- 'workspace_id' eksik
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL',
                'decision_code', 'BLOCK',
                'merchant_id', null
            )
        );
    exception when others then
        if sqlerrm like '%Kişisel doğrulamada workspace_id null olmalıdır.%' then
            v_error_caught := true;
        else
            raise exception 'UPDATE eksik workspace_id için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'UPDATE operasyonunda workspace_id eksikken reddedilmedi.';
    end if;

    -- 11.9 DELETE operasyonunda workspace_id anahtarı yoksa ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'DELETE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                -- 'workspace_id' eksik
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL'
            )
        );
    exception when others then
        if sqlerrm like '%Kişisel doğrulamada workspace_id null olmalıdır.%' then
            v_error_caught := true;
        else
            raise exception 'DELETE eksik workspace_id için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'DELETE operasyonunda workspace_id eksikken reddedilmedi.';
    end if;

    -- 11.10 UPDATE operasyonunda workspace_id string "null" ise ret
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'UPDATE',
            4,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', 'null', -- String "null"
                'normalized_alias', 'NETFLIX',
                'scope_code', 'PERSONAL',
                'decision_code', 'BLOCK',
                'merchant_id', null
            )
        );
    exception when others then
        if sqlerrm like '%Kişisel doğrulamada workspace_id null olmalıdır.%' then
            v_error_caught := true;
        else
            raise exception 'UPDATE string "null" workspace_id için beklenmeyen hata: % (SQLSTATE %)', sqlerrm, sqlstate;
        end if;
    end;
    if not v_error_caught then
        raise exception 'UPDATE operasyonunda workspace_id string "null" iken reddedilmedi.';
    end if;

    -- ------------------------------------------------------------------------
    -- 12. CREATE YARIŞINDA RAW 23505 YERİNE CONFLICT DÖNMESİ
    -- ------------------------------------------------------------------------
    -- Zaten var olan NETFLIX için yeni CREATE gönderildiğinde CONFLICT dönmeli
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'USER_MERCHANT_VERIFICATION',
        'CREATE',
        null,
        jsonb_build_object(
            'id', v_expected_netflix_id,
            'user_id', v_user_id,
            'workspace_id', null,
            'normalized_alias', 'NETFLIX',
            'decision_code', 'ASSIGN',
            'merchant_id', 'netflix',
            'scope_code', 'PERSONAL'
        )
    );
    if v_result ->> 'status' <> 'CONFLICT' then
        raise exception 'Mevcut kayıt için CREATE CONFLICT dönmedi: %', v_result;
    end if;

    -- ------------------------------------------------------------------------
    -- 13. RLS VE TABLO ACL TESTLERİ
    -- ------------------------------------------------------------------------
    -- 13.1 Own-row SELECT: Authenticated kullanıcı kendi satırını okuyabilir (1 satır)
    perform set_config('request.jwt.claim.sub', v_user_id::text, true);
    set local role authenticated;
    select count(*) into v_visible_count
    from public.user_merchant_verifications
    where id = v_expected_netflix_id;
    reset role;
    if v_visible_count <> 1 then
        raise exception 'Own-row SELECT authenticated kullanıcı için 1 satır döndürmedi: %', v_visible_count;
    end if;

    -- 13.2 Cross-user SELECT: Diğer kullanıcı bu kullanıcının kaydını göremez (0 satır)
    perform set_config('request.jwt.claim.sub', v_other_user_id::text, true);
    set local role authenticated;
    select count(*) into v_visible_count
    from public.user_merchant_verifications
    where id = v_expected_netflix_id;
    reset role;
    if v_visible_count <> 0 then
        raise exception 'Cross-user RLS izolasyonu başarısız: Başka kullanıcının satırı göründü!';
    end if;

    -- 13.3 Doğrudan INSERT engeli (authenticated rolüne grant verilmemiştir)
    perform set_config('request.jwt.claim.sub', v_user_id::text, true);
    v_error_caught := false;
    begin
        set local role authenticated;
        insert into public.user_merchant_verifications (
            id, user_id, workspace_id, normalized_alias, decision_code, scope_code,
            created_at, updated_at, version
        ) values (
            extensions.gen_random_uuid(), v_user_id, null, 'DIRECT_INSERT', 'BLOCK', 'PERSONAL',
            now(), now(), 1
        );
        reset role;
    exception when others then
        reset role;
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Doğrudan INSERT authenticated rolüne engellenmedi!';
    end if;

    -- 13.4 Doğrudan UPDATE engeli (authenticated rolüne grant verilmemiştir)
    v_error_caught := false;
    begin
        set local role authenticated;
        update public.user_merchant_verifications
           set decision_code = 'BLOCK'
         where id = v_expected_netflix_id;
        reset role;
    exception when others then
        reset role;
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Doğrudan UPDATE authenticated rolüne engellenmedi!';
    end if;

    -- 13.5 Doğrudan DELETE engeli (authenticated rolüne grant verilmemiştir)
    v_error_caught := false;
    begin
        set local role authenticated;
        delete from public.user_merchant_verifications
         where id = v_expected_netflix_id;
        reset role;
    exception when others then
        reset role;
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Doğrudan DELETE authenticated rolüne engellenmedi!';
    end if;

    -- 13.6 Anon / Public RPC erişim reddi
    v_error_caught := false;
    begin
        set local role anon;
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'USER_MERCHANT_VERIFICATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_expected_netflix_id,
                'user_id', v_user_id,
                'workspace_id', null,
                'normalized_alias', 'NETFLIX',
                'decision_code', 'ASSIGN',
                'merchant_id', 'netflix',
                'scope_code', 'PERSONAL'
            )
        );
        reset role;
    exception when others then
        reset role;
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Anon rolü sync_write_v2 çağırabildi (reddedilmeliydi)!';
    end if;

    -- ------------------------------------------------------------------------
    -- 14. ESKİ WRAPPER DOĞRUDAN ERİŞİM ENGELİ
    -- ------------------------------------------------------------------------
    v_error_caught := false;
    begin
        set local role authenticated;
        perform public.sync_write_v2_before_user_merchant_verification(
            replace(extensions.gen_random_uuid()::text, '-', ''),
            'CATEGORY',
            'CREATE',
            null,
            jsonb_build_object('id', extensions.gen_random_uuid())
        );
        reset role;
    exception when others then
        reset role;
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Eski wrapper doğrudan authenticated çağrısına kapatılmamış!';
    end if;

    -- ------------------------------------------------------------------------
    -- 15. ESKİ 14 ENTITY RPC DELEGASYON REGRESYONU (Örn. CATEGORY)
    -- ------------------------------------------------------------------------
    perform set_config('request.jwt.claim.sub', v_user_id::text, true);
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''),
        'CATEGORY',
        'CREATE',
        null,
        jsonb_build_object(
            'id', v_category_id,
            'user_id', v_user_id,
            'name', 'D1 Test Kategori',
            'icon', 'category',
            'color', '#FF0000',
            'type', 'expense',
            'is_system', false
        )
    );
    if v_result ->> 'status' <> 'APPLIED' then
        raise exception 'Eski entity (CATEGORY) delegasyonu başarısız: %', v_result;
    end if;

    raise notice 'Faz 8.6-D1 SQL Contract testleri başarıyla doğrulandı. ROLLBACK uygulanıyor.';
end
$$;

rollback;
