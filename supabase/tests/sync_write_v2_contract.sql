-- FeniqoMobil Sync Write V2 RPC Staging Sözleşme ve İdempotency Kabul Testi
-- Bu test dosyası:
-- 1. 20260826000100_sync_write_v2_receipts.sql
-- 2. 20260826000200_sync_write_v2_rpc.sql
-- 3. 20260829000100_sync_write_v2_budgets.sql
-- 4. 20260830000100_sync_write_v2_recurring_transactions.sql
-- migration'ları sonrasında sync_write_v2 sözleşmesini ve idempotency garantilerini doğrulamak için tasarlanmıştır.
-- Bütün test verileri dinamik ve rastgeledir; test sonunda koşulsuz ROLLBACK ile temizlenir.

begin;

do $$
declare
    v_test_user_id uuid;
    v_cat_id uuid := extensions.gen_random_uuid();
    v_tx_id uuid := extensions.gen_random_uuid();
    v_missing_id uuid := extensions.gen_random_uuid();
    v_default_expense_cat_id uuid;
    v_default_income_cat_id uuid;

    -- 32 karakter küçük harf hex operation_id üretimi
    v_op_create_cat text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_cat text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_cat2 text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_cat text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_notfound_cat text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_create_tx text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_tx text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_invalid text := 'INVALID_NOT_HEX_32_CHARS_LONG!';

    -- Budget test değişkenleri
    v_budget_id uuid := extensions.gen_random_uuid();
    v_income_budget_id uuid := extensions.gen_random_uuid();
    v_op_create_budget text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_income_budget text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_budget text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_budget text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_budget text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_notfound_budget text := replace(extensions.gen_random_uuid()::text, '-', '');

    -- Recurring Transaction test değişkenleri
    v_rec_id uuid := extensions.gen_random_uuid();
    v_mismatch_rec_id uuid := extensions.gen_random_uuid();
    v_owner_manipulate_rec_id uuid := extensions.gen_random_uuid();
    v_workspace_manipulate_rec_id uuid := extensions.gen_random_uuid();
    v_op_create_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_mismatch_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_owner_manipulate_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_workspace_manipulate_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_regression_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_rec text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_notfound_rec text := replace(extensions.gen_random_uuid()::text, '-', '');

    v_res jsonb;
    v_res_replay jsonb;
    v_res_replay_old_create jsonb;
    v_applied_version bigint;
    v_cat_db_version bigint;
    v_budget_db_version bigint;
    v_rec_db_version bigint;
    v_rec_prev_version bigint;
    v_rec_prev_last_generated date;
    v_rec_post_version bigint;
    v_rec_post_last_generated date;
    v_receipt_count integer;
    v_cat_count integer;
    v_tx_count integer;
    v_budget_count integer;
    v_rec_count integer;
    v_error_caught boolean;
begin
    -- 0. STAGING KULLANICISI SEÇİMİ VE SESSION AYARLAMASI
    select id into v_test_user_id from auth.users limit 1;
    if v_test_user_id is null then
        raise exception 'Staging test kullanıcısı bulunamadı (auth.users tablosu boş). Test iptal edildi.';
    end if;

    -- Transaction-local JWT actor tanımlaması
    perform set_config('request.jwt.claim.sub', v_test_user_id::text, true);

    -- Sistem varsayılan kategorilerini bul
    select id into v_default_expense_cat_id
      from public.categories
     where is_default = true and type = 'expense' and deleted_at is null
     limit 1;

    select id into v_default_income_cat_id
      from public.categories
     where is_default = true and type = 'income' and deleted_at is null
     limit 1;

    if v_default_expense_cat_id is null or v_default_income_cat_id is null then
        raise exception 'Sistem varsayılan kategorileri bulunamadı. Önce seed migration uygulanmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 1: Özel kategori CREATE
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_cat_id,
            'user_id', v_test_user_id,
            'name', 'Staging Test Kategori',
            'type', 'expense',
            'color', '#EF4444'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 1 Başarısız: Kategori CREATE APPLIED dönmedi.';
    end if;

    v_applied_version := (v_res -> 'record' ->> 'version')::bigint;
    if v_applied_version < 1 then
        raise exception 'Senaryo 1 Başarısız: applied_version en az 1 olmalıdır.';
    end if;

    select count(*) into v_cat_count from public.categories where id = v_cat_id and user_id = v_test_user_id;
    if v_cat_count <> 1 then
        raise exception 'Senaryo 1 Başarısız: Kategoriler tablosunda tam 1 satır bulunmalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_cat;
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 1 Başarısız: Receipt tablosunda tam 1 satır bulunmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 2: Aynı operation_id + aynı istek tekrarı (Exact Replay)
    -- =========================================================================
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_cat_id,
            'user_id', v_test_user_id,
            'name', 'Staging Test Kategori',
            'type', 'expense',
            'color', '#EF4444'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 2 Başarısız: Replay ilk kayıt snapshotı ile tam eşleşmedi.';
    end if;

    select version into v_cat_db_version from public.categories where id = v_cat_id;
    if v_cat_db_version <> v_applied_version then
        raise exception 'Senaryo 2 Başarısız: Replay veritabanı sürümünü artırmamalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 3 & 5: Kategori UPDATE ve Eski CREATE Snapshotının Korunması
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'UPDATE',
        p_base_version := v_applied_version,
        p_payload := jsonb_build_object(
            'id', v_cat_id,
            'user_id', v_test_user_id,
            'name', 'Staging Test Kategori Güncellendi',
            'type', 'expense',
            'color', '#3B82F6'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 5 Başarısız: Kategori UPDATE APPLIED dönmedi.';
    end if;

    select version into v_cat_db_version from public.categories where id = v_cat_id;
    if v_cat_db_version <> (v_applied_version + 1) then
        raise exception 'Senaryo 5 Başarısız: Kategori UPDATE sonrası sürüm tam 1 artmalıdır.';
    end if;

    -- Senaryo 3 Doğrulaması: İlk CREATE operation_id tekrar çağrıldığında güncel DB satırı değil ilk snapshot dönmeli
    v_res_replay_old_create := public.sync_write_v2(
        p_operation_id := v_op_create_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_cat_id,
            'user_id', v_test_user_id,
            'name', 'Staging Test Kategori',
            'type', 'expense',
            'color', '#EF4444'
        )
    );

    if (v_res_replay_old_create -> 'record' ->> 'name') <> 'Staging Test Kategori'
       or (v_res_replay_old_create -> 'record' ->> 'version')::bigint <> v_applied_version then
        raise exception 'Senaryo 3 Başarısız: Eski CREATE operation_id ilk snapshot yerine güncel satırı döndü.';
    end if;


    -- =========================================================================
    -- SENARYO 4: Aynı operation_id + farklı payload (Idempotency İhlali)
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_create_cat,
            p_entity_type := 'CATEGORY',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_cat_id,
                'user_id', v_test_user_id,
                'name', 'Tamamen Farklı Payload İhlali',
                'type', 'expense',
                'color', '#10B981'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 4 Başarısız: Farklı payload ile aynı operation_id reddedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 6: Eski baseVersion ile farklı operation_id (CONFLICT)
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel sürüm 2 olduğu için conflict üretmeli
        p_payload := jsonb_build_object(
            'id', v_cat_id,
            'user_id', v_test_user_id,
            'name', 'Çakışan İstek',
            'type', 'expense',
            'color', '#F59E0B'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or pg_catalog.jsonb_typeof(v_res -> 'record') <> 'object' then
        raise exception 'Senaryo 6 Başarısız: Eski base_version CONFLICT dönmedi veya record nesne değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_conflict_cat;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 6 Başarısız: CONFLICT durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 7: Olmayan entity UPDATE (NOT_FOUND)
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_notfound_cat,
        p_entity_type := 'CATEGORY',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_missing_id,
            'user_id', v_test_user_id,
            'name', 'Olmayan Kategori',
            'type', 'expense',
            'color', '#6B7280'
        )
    );

    if (v_res ->> 'status') <> 'NOT_FOUND' or (v_res -> 'record') is distinct from 'null'::jsonb then
        raise exception 'Senaryo 7 Başarısız: Olmayan entity NOT_FOUND dönmedi veya record JSON null değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_notfound_cat;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 7 Başarısız: NOT_FOUND durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 8: Transaction CREATE (Taksit ve Kategori Koruması)
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_tx,
        p_entity_type := 'TRANSACTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_tx_id,
            'user_id', v_test_user_id,
            'amount_minor', 33333,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'description', 'Staging Taksit 1/3',
            'payment_method', 'credit_card',
            'transaction_date', '2026-08-26',
            'installment_number', 1,
            'total_installments', 3,
            'installment_group_id', 'grp-staging-100'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 8 Başarısız: İşlem CREATE APPLIED dönmedi.';
    end if;

    if (v_res -> 'record' ->> 'amount_minor')::bigint <> 33333
       or (v_res -> 'record' ->> 'installment_number')::integer <> 1
       or (v_res -> 'record' ->> 'total_installments')::integer <> 3
       or (v_res -> 'record' ->> 'installment_group_id') <> 'grp-staging-100' then
        raise exception 'Senaryo 8 Başarısız: Taksit veya tutar alanları korunmadı.';
    end if;


    -- =========================================================================
    -- SENARYO 9: Türü uyuşmayan kategori/işlem reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := replace(extensions.gen_random_uuid()::text, '-', ''),
            p_entity_type := 'TRANSACTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_test_user_id,
                'amount_minor', 50000,
                'currency', 'TRY',
                'type', 'income', -- Gelir işlemi fakat gider kategorisi atanıyor
                'category_id', v_default_expense_cat_id,
                'payment_method', 'bank_transfer',
                'transaction_date', '2026-08-26'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 9 Başarısız: Tür uyuşmazlığı olan işlem reddedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 10: Transaction Minimal DELETE ve Replay
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_tx,
        p_entity_type := 'TRANSACTION',
        p_operation := 'DELETE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_tx_id -- Minimal payload: yalnız id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 10 Başarısız: Minimal DELETE APPLIED soft-delete dönmedi.';
    end if;

    -- DELETE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_delete_tx,
        p_entity_type := 'TRANSACTION',
        p_operation := 'DELETE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_tx_id
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 10 Başarısız: DELETE replay ilk snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 11: Geçersiz operation_id formatı
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_invalid,
            p_entity_type := 'CATEGORY',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'user_id', v_test_user_id,
                'name', 'Geçersiz Op Id',
                'type', 'expense',
                'color', '#EF4444'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 11 Başarısız: Geçersiz operation_id reddedilmedi.';
    end if;

    -- =========================================================================
    -- SENARYO 12: BUDGET CREATE ve receipt kaydı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_budget_id,
            'user_id', v_test_user_id,
            'category_id', v_default_expense_cat_id,
            'month', '2026-08',
            'limit_minor', 150000,
            'currency', 'TRY'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 12 Başarısız: Bütçe CREATE APPLIED dönmedi.';
    end if;

    if (v_res -> 'record' ->> 'limit_minor')::bigint <> 150000
       or (v_res -> 'record' ->> 'month') <> '2026-08'
       or (v_res -> 'record' ->> 'category_id')::uuid <> v_default_expense_cat_id then
        raise exception 'Senaryo 12 Başarısız: Bütçe alanları doğru kaydedilmedi.';
    end if;

    v_applied_version := (v_res -> 'record' ->> 'version')::bigint;
    if v_applied_version < 1 then
        raise exception 'Senaryo 12 Başarısız: applied_version en az 1 olmalıdır.';
    end if;

    select count(*) into v_budget_count from public.budgets where id = v_budget_id and user_id = v_test_user_id;
    if v_budget_count <> 1 then
        raise exception 'Senaryo 12 Başarısız: Bütçeler tablosunda tam 1 satır bulunmalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_budget;
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 12 Başarısız: Bütçe CREATE receipt tablosunda tam 1 satır bulunmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 13: BUDGET aynı operation_id ile exact replay
    -- =========================================================================
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_budget_id,
            'user_id', v_test_user_id,
            'category_id', v_default_expense_cat_id,
            'month', '2026-08',
            'limit_minor', 150000,
            'currency', 'TRY'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 13 Başarısız: Bütçe replay ilk kayıt snapshotı ile tam eşleşmedi.';
    end if;

    select version into v_budget_db_version from public.budgets where id = v_budget_id;
    if v_budget_db_version <> v_applied_version then
        raise exception 'Senaryo 13 Başarısız: Bütçe replay veritabanı sürümünü artırmamalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 14: Gelir kategorisi ile BUDGET CREATE denemesi reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_income_budget,
            p_entity_type := 'BUDGET',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_income_budget_id,
                'user_id', v_test_user_id,
                'category_id', v_default_income_cat_id, -- Gelir kategorisi
                'month', '2026-08',
                'limit_minor', 100000,
                'currency', 'TRY'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 14 Başarısız: Gelir kategorisi ile BUDGET CREATE reddedilmedi.';
    end if;

    select count(*) into v_budget_count from public.budgets where id = v_income_budget_id;
    if v_budget_count <> 0 then
        raise exception 'Senaryo 14 Başarısız: Reddedilen gelir bütçesi için veritabanına satır yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_income_budget;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 14 Başarısız: Reddedilen gelir bütçesi için receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 15: BUDGET UPDATE ve version artışı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'UPDATE',
        p_base_version := v_applied_version,
        p_payload := jsonb_build_object(
            'id', v_budget_id,
            'user_id', v_test_user_id,
            'category_id', v_default_expense_cat_id,
            'month', '2026-08',
            'limit_minor', 200000,
            'currency', 'TRY'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 15 Başarısız: Bütçe UPDATE APPLIED dönmedi.';
    end if;

    select version into v_budget_db_version from public.budgets where id = v_budget_id;
    if v_budget_db_version <> (v_applied_version + 1) then
        raise exception 'Senaryo 15 Başarısız: Bütçe UPDATE sonrası sürüm tam 1 artmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 16: BUDGET eski base_version ile CONFLICT ve receipt oluşmaması
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel sürüm 2 olduğu için CONFLICT üretmeli
        p_payload := jsonb_build_object(
            'id', v_budget_id,
            'user_id', v_test_user_id,
            'category_id', v_default_expense_cat_id,
            'month', '2026-08',
            'limit_minor', 250000,
            'currency', 'TRY'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or pg_catalog.jsonb_typeof(v_res -> 'record') <> 'object' then
        raise exception 'Senaryo 16 Başarısız: Eski base_version ile bütçe CONFLICT dönmedi veya record nesne değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_conflict_budget;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 16 Başarısız: Bütçe CONFLICT durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 17: BUDGET DELETE tombstone ve replay
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_budget_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 17 Başarısız: Bütçe DELETE APPLIED soft-delete dönmedi.';
    end if;

    -- Bütçe DELETE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_delete_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_budget_id
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 17 Başarısız: Bütçe DELETE replay ilk snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 18: BUDGET olmayan kayıtta NOT_FOUND
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_notfound_budget,
        p_entity_type := 'BUDGET',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_missing_id,
            'user_id', v_test_user_id,
            'category_id', v_default_expense_cat_id,
            'month', '2026-08',
            'limit_minor', 100000,
            'currency', 'TRY'
        )
    );

    if (v_res ->> 'status') <> 'NOT_FOUND' or (v_res -> 'record') is distinct from 'null'::jsonb then
        raise exception 'Senaryo 18 Başarısız: Olmayan bütçe entity NOT_FOUND dönmedi veya record JSON null değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_notfound_budget;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 18 Başarısız: Bütçe NOT_FOUND durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 19: RECURRING_TRANSACTION CREATE, version 1 ve receipt kaydı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_rec_id,
            'user_id', v_test_user_id,
            'amount_minor', 50000,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'description', 'Aylık İnternet Faturası',
            'payment_method', 'CREDIT_CARD',
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'end_date', null,
            'last_generated_date', null,
            'is_active', true
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 19 Başarısız: Tekrarlayan işlem CREATE APPLIED dönmedi.';
    end if;

    v_applied_version := (v_res -> 'record' ->> 'version')::bigint;
    if v_applied_version <> 1 then
        raise exception 'Senaryo 19 Başarısız: Tekrarlayan işlem CREATE sonrası version 1 olmalıdır.';
    end if;

    select count(*) into v_rec_count from public.recurring_transactions where id = v_rec_id and user_id = v_test_user_id and last_generated_date is null;
    if v_rec_count <> 1 then
        raise exception 'Senaryo 19 Başarısız: Tekrarlayan işlem tablosunda satır doğrulanamadı.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_rec;
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 19 Başarısız: Tekrarlayan işlem CREATE sonrası receipt kaydedilmelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 20: RECURRING_TRANSACTION CREATE Replay
    -- =========================================================================
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_rec_id,
            'user_id', v_test_user_id,
            'amount_minor', 50000,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'description', 'Aylık İnternet Faturası',
            'payment_method', 'CREDIT_CARD',
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'end_date', null,
            'last_generated_date', null,
            'is_active', true
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 20 Başarısız: Tekrarlayan işlem CREATE replay snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 21: RECURRING_TRANSACTION Kategori Türü Uyuşmazlığı Reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_mismatch_rec,
            p_entity_type := 'RECURRING_TRANSACTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_mismatch_rec_id,
                'user_id', v_test_user_id,
                'amount_minor', 30000,
                'currency', 'TRY',
                'type', 'expense',
                'category_id', v_default_income_cat_id, -- Gelir kategorisi ile gider tekrarı
                'payment_method', 'CREDIT_CARD',
                'frequency', 'MONTHLY',
                'interval', 1,
                'start_date', '2026-08-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 21 Başarısız: Kategori türü uyuşmazlığında exception fırlatılmalıdır.';
    end if;

    select count(*) into v_rec_count from public.recurring_transactions where id = v_mismatch_rec_id;
    if v_rec_count <> 0 then
        raise exception 'Senaryo 21 Başarısız: Reddedilen kural için recurring_transactions tablosuna satır yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_mismatch_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 21 Başarısız: Reddedilen kural için receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 22: RECURRING_TRANSACTION Kullanıcı (user_id) Manipülasyonu Reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_owner_manipulate_rec,
            p_entity_type := 'RECURRING_TRANSACTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_owner_manipulate_rec_id,
                'user_id', extensions.gen_random_uuid(), -- Farklı kullanıcı kimliği
                'amount_minor', 20000,
                'currency', 'TRY',
                'type', 'expense',
                'category_id', v_default_expense_cat_id,
                'payment_method', 'CASH',
                'frequency', 'MONTHLY',
                'start_date', '2026-08-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 22 Başarısız: Başka kullanıcı adına kural oluşturma exception fırlatmalıdır.';
    end if;

    select count(*) into v_rec_count from public.recurring_transactions where id = v_owner_manipulate_rec_id;
    if v_rec_count <> 0 then
        raise exception 'Senaryo 22 Başarısız: Yetkisiz kullanıcı adına kural için satır yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where operation_id = v_op_owner_manipulate_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 22 Başarısız: Kullanıcı manipülasyonu girişiminde receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 23: RECURRING_TRANSACTION Çalışma Alanı (workspace_id) Manipülasyonu Reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_workspace_manipulate_rec,
            p_entity_type := 'RECURRING_TRANSACTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_workspace_manipulate_rec_id,
                'user_id', v_test_user_id,
                'workspace_id', extensions.gen_random_uuid(), -- V1 kişisel kapsamda geçersiz workspace_id
                'amount_minor', 25000,
                'currency', 'TRY',
                'type', 'expense',
                'category_id', v_default_expense_cat_id,
                'payment_method', 'CASH',
                'frequency', 'MONTHLY',
                'start_date', '2026-08-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 23 Başarısız: workspace_id dolu kural oluşturma exception fırlatmalıdır.';
    end if;

    select count(*) into v_rec_count from public.recurring_transactions where id = v_workspace_manipulate_rec_id;
    if v_rec_count <> 0 then
        raise exception 'Senaryo 23 Başarısız: Geçersiz workspace_id kuralı için satır yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_workspace_manipulate_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 23 Başarısız: Çalışma alanı manipülasyonu girişiminde receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 24: RECURRING_TRANSACTION UPDATE ve Sürüm Artışı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'UPDATE',
        p_base_version := v_applied_version,
        p_payload := jsonb_build_object(
            'id', v_rec_id,
            'user_id', v_test_user_id,
            'amount_minor', 60000,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'description', 'Aylık Fiber İnternet',
            'payment_method', 'CREDIT_CARD',
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'end_date', null,
            'last_generated_date', '2026-08-01',
            'is_active', true
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 24 Başarısız: Tekrarlayan işlem UPDATE APPLIED dönmedi.';
    end if;

    select version, last_generated_date
      into v_rec_prev_version, v_rec_prev_last_generated
      from public.recurring_transactions
     where id = v_rec_id;

    if v_rec_prev_version <> (v_applied_version + 1) or v_rec_prev_last_generated <> '2026-08-01'::date then
        raise exception 'Senaryo 24 Başarısız: Tekrarlayan işlem UPDATE sonrası sürüm veya last_generated_date doğrulanamadı.';
    end if;


    -- =========================================================================
    -- SENARYO 25: RECURRING_TRANSACTION last_generated_date Regresyonu Reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_regression_rec,
            p_entity_type := 'RECURRING_TRANSACTION',
            p_operation := 'UPDATE',
            p_base_version := v_rec_prev_version,
            p_payload := jsonb_build_object(
                'id', v_rec_id,
                'user_id', v_test_user_id,
                'amount_minor', 60000,
                'currency', 'TRY',
                'type', 'expense',
                'category_id', v_default_expense_cat_id,
                'payment_method', 'CREDIT_CARD',
                'frequency', 'MONTHLY',
                'interval', 1,
                'start_date', '2026-08-01',
                'last_generated_date', '2026-07-15' -- Mevcut '2026-08-01' öncesine geriye çekme
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 25 Başarısız: last_generated_date geriye çekildiğinde exception fırlatılmalıdır.';
    end if;

    select version, last_generated_date
      into v_rec_post_version, v_rec_post_last_generated
      from public.recurring_transactions
     where id = v_rec_id;

    if v_rec_post_version <> v_rec_prev_version or v_rec_post_last_generated <> v_rec_prev_last_generated then
        raise exception 'Senaryo 25 Başarısız: Regresyon denemesi sonrasında satırın version veya last_generated_date değeri değişmemelidir.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_regression_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 25 Başarısız: Regresyon denemesinde receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 26: RECURRING_TRANSACTION Eski base_version ile CONFLICT
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel sürüm 2 olduğu için CONFLICT üretmeli
        p_payload := jsonb_build_object(
            'id', v_rec_id,
            'user_id', v_test_user_id,
            'amount_minor', 70000,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'payment_method', 'CREDIT_CARD',
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'last_generated_date', '2026-08-01'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or pg_catalog.jsonb_typeof(v_res -> 'record') <> 'object' then
        raise exception 'Senaryo 26 Başarısız: Eski base_version ile kural CONFLICT dönmedi veya record nesne değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_conflict_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 26 Başarısız: Tekrarlayan işlem CONFLICT durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 27: RECURRING_TRANSACTION DELETE Soft-Delete ve Replay
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_rec_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 27 Başarısız: Tekrarlayan işlem DELETE APPLIED soft-delete dönmedi.';
    end if;

    -- DELETE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_delete_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_rec_id
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 27 Başarısız: Tekrarlayan işlem DELETE replay ilk snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 28: RECURRING_TRANSACTION Olmayan Kayıtta NOT_FOUND
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_notfound_rec,
        p_entity_type := 'RECURRING_TRANSACTION',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_missing_id,
            'user_id', v_test_user_id,
            'amount_minor', 10000,
            'currency', 'TRY',
            'type', 'expense',
            'category_id', v_default_expense_cat_id,
            'payment_method', 'CASH',
            'frequency', 'MONTHLY',
            'start_date', '2026-08-01'
        )
    );

    if (v_res ->> 'status') <> 'NOT_FOUND' or (v_res -> 'record') is distinct from 'null'::jsonb then
        raise exception 'Senaryo 28 Başarısız: Olmayan tekrarlayan işlem NOT_FOUND dönmedi veya record JSON null değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_notfound_rec;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 28 Başarısız: Tekrarlayan işlem NOT_FOUND durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 29: BİLGİLENDİRME VE GÜVENLİ TEMİZLİK
    -- =========================================================================
    raise notice 'Tüm 28 sözleşme ve idempotency senaryosu (PROFILE, CATEGORY, TRANSACTION, BUDGET, RECURRING_TRANSACTION) başarıyla doğrulandı. İşlemler ROLLBACK ile geri alınıyor.';
end
$$;

-- 29. Koşulsuz ROLLBACK: Veritabanında hiçbir geçici kayıt veya yan etki bırakılmaz
rollback;


