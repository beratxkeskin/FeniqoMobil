-- FeniqoMobil Sync Write V2 RPC Staging Sözleşme ve İdempotency Kabul Testi
-- Bu test dosyası:
-- 1. 20260826000100_sync_write_v2_receipts.sql
-- 2. 20260826000200_sync_write_v2_rpc.sql
-- 3. 20260829000100_sync_write_v2_budgets.sql
-- 4. 20260830000100_sync_write_v2_recurring_transactions.sql
-- 5. 20260831000100_sync_write_v2_subscriptions.sql
-- 6. 20260901000100_sync_write_v2_goals_and_debts.sql
-- 7. 20260901000200_reconcile_goals_debts_sync_contract.sql
-- 8. 20260906000100_sync_write_v2_workspaces.sql
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

    -- Subscription test değişkenleri
    v_sub_id uuid := extensions.gen_random_uuid();
    v_sub_no_cat_id uuid := extensions.gen_random_uuid();
    v_income_sub_id uuid := extensions.gen_random_uuid();
    v_owner_manipulate_sub_id uuid := extensions.gen_random_uuid();
    v_workspace_manipulate_sub_id uuid := extensions.gen_random_uuid();
    v_op_create_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_create_sub_no_cat text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_income_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_owner_manipulate_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_workspace_manipulate_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_regression_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_sub text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_notfound_sub text := replace(extensions.gen_random_uuid()::text, '-', '');

    -- Goal & Contribution test değişkenleri
    v_goal_id uuid := extensions.gen_random_uuid();
    v_invalid_color_goal_id uuid := extensions.gen_random_uuid();
    v_contrib_add_id uuid := extensions.gen_random_uuid();
    v_contrib_rem_id uuid := extensions.gen_random_uuid();
    v_contrib_fail_id uuid := extensions.gen_random_uuid();
    v_owner_manipulate_goal_id uuid := extensions.gen_random_uuid();
    v_workspace_manipulate_goal_id uuid := extensions.gen_random_uuid();
    v_op_create_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_invalid_color_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_owner_manipulate_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_workspace_manipulate_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_direct_current_amount text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_currency_change_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_goal text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_contrib_add text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_contrib_rem text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_contrib_underflow text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_contrib_conflict text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_goal text := replace(extensions.gen_random_uuid()::text, '-', '');

    -- Debt & Payment test değişkenleri
    v_debt_id uuid := extensions.gen_random_uuid();
    v_pay_part_id uuid := extensions.gen_random_uuid();
    v_pay_full_id uuid := extensions.gen_random_uuid();
    v_pay_over_id uuid := extensions.gen_random_uuid();
    v_owner_manipulate_debt_id uuid := extensions.gen_random_uuid();
    v_workspace_manipulate_debt_id uuid := extensions.gen_random_uuid();
    v_op_create_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_owner_manipulate_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_workspace_manipulate_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_status_manipulate_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_conflict_debt text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_pay_partial text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_pay_over text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_pay_settle text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_pay_conflict text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_debt text := replace(extensions.gen_random_uuid()::text, '-', '');

    -- Workspace test değişkenleri
    v_ws_id uuid := extensions.gen_random_uuid();
    v_other_user_id uuid;
    v_op_create_ws text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_extra_key text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_forbidden_owner text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_forbidden_member text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_missing_type text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_missing_curr text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_update_missing_type text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_update_missing_curr text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_nonowner_update text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_nonowner_delete text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_ws_conflict text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_update_ws text := replace(extensions.gen_random_uuid()::text, '-', '');
    v_op_delete_ws text := replace(extensions.gen_random_uuid()::text, '-', '');

    v_ws_db_version bigint;
    v_ws_db_name text;
    v_ws_db_normalized text;
    v_ws_db_deleted_at timestamptz;
    v_ws_member_count integer;
    v_ws_member_role text;
    v_ws_visible boolean;
    v_ws_member_visible boolean;

    v_goal_db_current_amount bigint;
    v_goal_db_version bigint;
    v_contrib_count integer;
    v_debt_db_status text;
    v_debt_db_version bigint;
    v_payment_count integer;

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
    v_sub_db_version bigint;
    v_sub_prev_version bigint;
    v_sub_prev_next_renewal date;
    v_sub_post_version bigint;
    v_sub_post_next_renewal date;
    v_receipt_count integer;
    v_cat_count integer;
    v_tx_count integer;
    v_budget_count integer;
    v_rec_count integer;
    v_sub_count integer;
    v_error_caught boolean;
    -- 0. STAGING KULLANICISI SEÇİMİ VE SESSION AYARLAMASI
    select id into v_test_user_id from auth.users order by created_at asc limit 1;
    if v_test_user_id is null then
        raise exception 'Staging test kullanıcısı bulunamadı (auth.users tablosu boş). Test iptal edildi.';
    end if;

    -- Workspace non-owner sözleşme testi için auth.users içinden ikinci gerçek kullanıcıyı seç
    select id into v_other_user_id from auth.users where id <> v_test_user_id order by created_at asc limit 1;
    if v_other_user_id is null then
        raise exception 'Workspace non-owner sözleşme testi için ikinci test kullanıcısı gerekli.';
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
    -- SENARYO 29: SUBSCRIPTION CREATE ve APPLIED Receipt Kaydı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_sub_id,
            'user_id', v_test_user_id,
            'name', 'Netflix',
            'amount_minor', 19900,
            'currency', 'TRY',
            'category_id', v_default_expense_cat_id,
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-09-01',
            'is_active', true
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 29 Başarısız: Abonelik CREATE APPLIED dönmedi: %', v_res;
    end if;

    v_applied_version := (v_res -> 'record' ->> 'version')::bigint;
    if v_applied_version <> 1 then
        raise exception 'Senaryo 29 Başarısız: Abonelik ilk oluşturma sürümü 1 olmalıdır. Alınan: %', v_applied_version;
    end if;

    select version into v_sub_db_version from public.subscriptions where id = v_sub_id and user_id = v_test_user_id;
    if v_sub_db_version <> 1 then
        raise exception 'Senaryo 29 Başarısız: Abonelik tablodaki sürümü 1 olmalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_sub and result_status = 'APPLIED';
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 29 Başarısız: Abonelik APPLIED receipt sync_operations_receipts tablosuna kaydedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 30: SUBSCRIPTION CREATE Replay Snapshot Eşitliği
    -- =========================================================================
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_sub_id,
            'user_id', v_test_user_id,
            'name', 'Netflix',
            'amount_minor', 19900,
            'currency', 'TRY',
            'category_id', v_default_expense_cat_id,
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-09-01',
            'is_active', true
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 30 Başarısız: Abonelik CREATE replay ilk dönen response ile birebir eşleşmedi.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_sub;
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 30 Başarısız: Abonelik Replay sonrası birden fazla receipt oluştu.';
    end if;


    -- =========================================================================
    -- SENARYO 31: Nullable Category ile SUBSCRIPTION CREATE
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_sub_no_cat,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_sub_no_cat_id,
            'user_id', v_test_user_id,
            'name', 'iCloud',
            'amount_minor', 4999,
            'currency', 'TRY',
            'category_id', null,
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-09-01',
            'is_active', true
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'category_id') is not null then
        raise exception 'Senaryo 31 Başarısız: Kredisiz/Kategorisiz abonelik CREATE APPLIED dönmedi veya category_id null değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_create_sub_no_cat and result_status = 'APPLIED';
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 31 Başarısız: Nullable category abonelik receipt kaydedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 32: Gelir ('income') Kategorisiyle SUBSCRIPTION CREATE Reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_income_sub,
            p_entity_type := 'SUBSCRIPTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_income_sub_id,
                'user_id', v_test_user_id,
                'name', 'Maaş',
                'amount_minor', 5000000,
                'currency', 'TRY',
                'category_id', v_default_income_cat_id,
                'frequency', 'MONTHLY',
                'interval', 1,
                'start_date', '2026-08-01',
                'next_renewal_date', '2026-09-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 32 Başarısız: Gelir kategorisi ile abonelik CREATE exception fırlatmalıdır.';
    end if;

    select count(*) into v_sub_count from public.subscriptions where id = v_income_sub_id;
    if v_sub_count <> 0 then
        raise exception 'Senaryo 32 Başarısız: Reddedilen gelir aboneliği subscriptions tablosuna yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_income_sub;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 32 Başarısız: Reddedilen işlem için receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 33: Başka user_id veya workspace_id manipülasyonu reddi
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_owner_manipulate_sub,
            p_entity_type := 'SUBSCRIPTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_owner_manipulate_sub_id,
                'user_id', v_missing_id,
                'name', 'Yetkisiz Abonelik',
                'amount_minor', 10000,
                'currency', 'TRY',
                'category_id', v_default_expense_cat_id,
                'frequency', 'MONTHLY',
                'start_date', '2026-08-01',
                'next_renewal_date', '2026-09-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 33 Başarısız: Farklı user_id ile abonelik CREATE yetkilendirme hatası fırlatmalıdır.';
    end if;

    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_workspace_manipulate_sub,
            p_entity_type := 'SUBSCRIPTION',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_workspace_manipulate_sub_id,
                'user_id', v_test_user_id,
                'workspace_id', v_cat_id,
                'name', 'Yetkisiz Workspace Aboneliği',
                'amount_minor', 10000,
                'currency', 'TRY',
                'category_id', v_default_expense_cat_id,
                'frequency', 'MONTHLY',
                'start_date', '2026-08-01',
                'next_renewal_date', '2026-09-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 33 Başarısız: workspace_id içeren abonelik CREATE yetkilendirme hatası fırlatmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 34: SUBSCRIPTION UPDATE ve version artışı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_sub_id,
            'user_id', v_test_user_id,
            'name', 'Netflix 4K Premium',
            'amount_minor', 29900,
            'currency', 'TRY',
            'category_id', v_default_expense_cat_id,
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-10-01',
            'is_active', true
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 34 Başarısız: Abonelik UPDATE APPLIED dönmedi: %', v_res;
    end if;

    v_applied_version := (v_res -> 'record' ->> 'version')::bigint;
    if v_applied_version <> 2 then
        raise exception 'Senaryo 34 Başarısız: Abonelik güncelleme sonrası sürüm 2 olmalıdır. Alınan: %', v_applied_version;
    end if;

    select version into v_sub_db_version from public.subscriptions where id = v_sub_id;
    if v_sub_db_version <> 2 then
        raise exception 'Senaryo 34 Başarısız: Abonelik tablodaki sürümü 2 olmalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_update_sub and result_status = 'APPLIED';
    if v_receipt_count <> 1 then
        raise exception 'Senaryo 34 Başarısız: Abonelik UPDATE receipt sync_operations_receipts tablosuna kaydedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 35: SUBSCRIPTION next_renewal_date Regresyon Reddi
    -- =========================================================================
    select version, next_renewal_date
      into v_sub_prev_version, v_sub_prev_next_renewal
      from public.subscriptions
     where id = v_sub_id;

    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_regression_sub,
            p_entity_type := 'SUBSCRIPTION',
            p_operation := 'UPDATE',
            p_base_version := 2,
            p_payload := jsonb_build_object(
                'id', v_sub_id,
                'user_id', v_test_user_id,
                'name', 'Netflix 4K Premium',
                'amount_minor', 29900,
                'currency', 'TRY',
                'category_id', v_default_expense_cat_id,
                'frequency', 'MONTHLY',
                'interval', 1,
                'start_date', '2026-08-01',
                'next_renewal_date', '2026-09-01' -- Mevcut '2026-10-01' öncesine geriye çekme
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 35 Başarısız: next_renewal_date geriye çekildiğinde exception fırlatılmalıdır.';
    end if;

    select version, next_renewal_date
      into v_sub_post_version, v_sub_post_next_renewal
      from public.subscriptions
     where id = v_sub_id;

    if v_sub_post_version <> v_sub_prev_version or v_sub_post_next_renewal <> v_sub_prev_next_renewal then
        raise exception 'Senaryo 35 Başarısız: Regresyon denemesi sonrasında satırın version veya next_renewal_date değeri değişmemelidir.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_regression_sub;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 35 Başarısız: Regresyon denemesinde receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 36: SUBSCRIPTION Eski base_version ile CONFLICT
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel sürüm 2 olduğu için CONFLICT üretmeli
        p_payload := jsonb_build_object(
            'id', v_sub_id,
            'user_id', v_test_user_id,
            'name', 'Netflix 4K Premium',
            'amount_minor', 35000,
            'currency', 'TRY',
            'category_id', v_default_expense_cat_id,
            'frequency', 'MONTHLY',
            'interval', 1,
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-10-01'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or pg_catalog.jsonb_typeof(v_res -> 'record') <> 'object' then
        raise exception 'Senaryo 36 Başarısız: Eski base_version ile abonelik CONFLICT dönmedi veya record nesne değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_conflict_sub;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 36 Başarısız: Abonelik CONFLICT durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 37: SUBSCRIPTION DELETE Soft-Delete ve Replay
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_sub_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 37 Başarısız: Abonelik DELETE APPLIED soft-delete dönmedi.';
    end if;

    -- DELETE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_delete_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_sub_id
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 37 Başarısız: Abonelik DELETE replay ilk snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 38: SUBSCRIPTION Olmayan Kayıtta NOT_FOUND
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_notfound_sub,
        p_entity_type := 'SUBSCRIPTION',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_missing_id,
            'user_id', v_test_user_id,
            'name', 'Olmayan Abonelik',
            'amount_minor', 10000,
            'currency', 'TRY',
            'category_id', v_default_expense_cat_id,
            'frequency', 'MONTHLY',
            'start_date', '2026-08-01',
            'next_renewal_date', '2026-09-01'
        )
    );

    if (v_res ->> 'status') <> 'NOT_FOUND' or (v_res -> 'record') is distinct from 'null'::jsonb then
        raise exception 'Senaryo 38 Başarısız: Olmayan abonelik NOT_FOUND dönmedi veya record JSON null değil.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_notfound_sub;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 38 Başarısız: Abonelik NOT_FOUND durumunda receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 39: GOAL CREATE, Replay ve Idempotency
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_goal,
        p_entity_type := 'GOAL',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_goal_id,
            'user_id', v_test_user_id,
            'name', 'Araba Birikimi',
            'target_amount_minor', 50000000,
            'current_amount_minor', 0,
            'currency', 'TRY',
            'target_date', '2027-06-01',
            'color_hex', '#0A7A55',
            'icon_key', 'car'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'version')::bigint <> 1 or (v_res -> 'record' ->> 'current_amount_minor')::bigint <> 0 then
        raise exception 'Senaryo 39 Başarısız: Hedef CREATE APPLIED veya version 1 dönmedi.';
    end if;

    -- GOAL CREATE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_goal,
        p_entity_type := 'GOAL',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_goal_id,
            'user_id', v_test_user_id,
            'name', 'Araba Birikimi',
            'target_amount_minor', 50000000,
            'current_amount_minor', 0,
            'currency', 'TRY',
            'target_date', '2027-06-01',
            'color_hex', '#0A7A55',
            'icon_key', 'car'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 39 Başarısız: Hedef CREATE replay ilk snapshot ile eşleşmedi.';
    end if;

    -- GOAL Owner / Workspace Manipülasyon Reddi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_owner_manipulate_goal,
            p_entity_type := 'GOAL',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_owner_manipulate_goal_id,
                'user_id', extensions.gen_random_uuid(), -- Yetkisiz owner
                'name', 'Sahte Hedef',
                'target_amount_minor', 1000000,
                'currency', 'TRY',
                'target_date', '2027-01-01'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 39 Başarısız: Başka bir kullanıcının ID''si ile hedef ekleme engellenmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 40: GOAL CREATE Geçersiz color_hex Fail-Closed Reddi (No Row, No Receipt)
    -- =========================================================================
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_invalid_color_goal,
            p_entity_type := 'GOAL',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', v_invalid_color_goal_id,
                'user_id', v_test_user_id,
                'name', 'Geçersiz Renkli Hedef',
                'target_amount_minor', 1000000,
                'currency', 'TRY',
                'target_date', '2027-01-01',
                'color_hex', 'INVALID_HEX_COLOR' -- Geçersiz renk formatı
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 40 Başarısız: Geçersiz color_hex ile hedef ekleme engellenmedi.';
    end if;

    -- Tabloda satır ve receipt tablosunda kayıt oluşmadığını somut assert et
    select count(*) into v_receipt_count from public.goals where id = v_invalid_color_goal_id;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 40 Başarısız: Geçersiz renk denemesinde goals tablosuna satır yazılmamalıdır.';
    end if;

    select count(*) into v_receipt_count from public.sync_operations_receipts where user_id = v_test_user_id and operation_id = v_op_invalid_color_goal;
    if v_receipt_count <> 0 then
        raise exception 'Senaryo 40 Başarısız: Geçersiz renk denemesinde receipt kaydedilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 41: GOAL UPDATE, Currency/CurrentAmount Manipülasyon Reddi ve Conflict
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_goal,
        p_entity_type := 'GOAL',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_goal_id,
            'user_id', v_test_user_id,
            'name', 'Yeni Araba Birikimi',
            'target_amount_minor', 60000000,
            'currency', 'TRY',
            'target_date', '2027-12-01',
            'color_hex', '#123456',
            'icon_key', 'car_sport'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'version')::bigint <> 2 or (v_res -> 'record' ->> 'name') <> 'Yeni Araba Birikimi' then
        raise exception 'Senaryo 41 Başarısız: Hedef UPDATE APPLIED veya version 2 dönmedi.';
    end if;

    -- Para birimi değiştirme denemesi (fail-closed)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_currency_change_goal,
            p_entity_type := 'GOAL',
            p_operation := 'UPDATE',
            p_base_version := 2,
            p_payload := jsonb_build_object(
                'id', v_goal_id,
                'user_id', v_test_user_id,
                'name', 'Yeni Araba Birikimi',
                'target_amount_minor', 60000000,
                'currency', 'USD', -- Değiştirilemez!
                'target_date', '2027-12-01',
                'color_hex', '#123456'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 41 Başarısız: Hedef para birimi güncellemesi engellenmedi.';
    end if;

    -- Mevcut birikim doğrudan UPDATE denemesi (fail-closed)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_direct_current_amount,
            p_entity_type := 'GOAL',
            p_operation := 'UPDATE',
            p_base_version := 2,
            p_payload := jsonb_build_object(
                'id', v_goal_id,
                'user_id', v_test_user_id,
                'name', 'Yeni Araba Birikimi',
                'target_amount_minor', 60000000,
                'current_amount_minor', 999999, -- Doğrudan değiştirilemez!
                'currency', 'TRY',
                'target_date', '2027-12-01',
                'color_hex', '#123456'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 41 Başarısız: Hedef current_amount_minor doğrudan UPDATE engellenmedi.';
    end if;

    -- Eski base_version ile CONFLICT
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_goal,
        p_entity_type := 'GOAL',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel 2 olduğu için CONFLICT
        p_payload := jsonb_build_object(
            'id', v_goal_id,
            'user_id', v_test_user_id,
            'name', 'Stale Update',
            'target_amount_minor', 60000000,
            'currency', 'TRY',
            'target_date', '2027-12-01',
            'color_hex', '#123456'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or pg_catalog.jsonb_typeof(v_res -> 'record') <> 'object' then
        raise exception 'Senaryo 41 Başarısız: Eski base_version ile hedef UPDATE CONFLICT dönmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 42: GOAL_CONTRIBUTION CREATE ADD ve Aggregate Güncellemesi
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_contrib_add,
        p_entity_type := 'GOAL_CONTRIBUTION',
        p_operation := 'CREATE',
        p_base_version := 2, -- Parent Goal'un beklenen sürümü
        p_payload := jsonb_build_object(
            'id', v_contrib_add_id,
            'goal_id', v_goal_id,
            'amount_minor', 15000000,
            'currency', 'TRY',
            'direction', 'ADD',
            'occurred_on', '2026-09-01',
            'note', 'İlk katkı'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' -> 'goal' ->> 'current_amount_minor')::bigint <> 15000000
       or (v_res -> 'record' -> 'goal' ->> 'version')::bigint <> 3
       or (v_res -> 'record' -> 'contribution' ->> 'id')::uuid <> v_contrib_add_id then
        raise exception 'Senaryo 42 Başarısız: GOAL_CONTRIBUTION ADD APPLIED veya aggregate güncellenmedi.';
    end if;

    select current_amount_minor, version into v_goal_db_current_amount, v_goal_db_version
      from public.goals where id = v_goal_id;
    if v_goal_db_current_amount <> 15000000 or v_goal_db_version <> 3 then
        raise exception 'Senaryo 42 Başarısız: DB goals tablosu atomik olarak güncellenmedi.';
    end if;

    -- GOAL_CONTRIBUTION Replay (Duplicate Event Oluşmama Kanıtı)
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_contrib_add,
        p_entity_type := 'GOAL_CONTRIBUTION',
        p_operation := 'CREATE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_contrib_add_id,
            'goal_id', v_goal_id,
            'amount_minor', 15000000,
            'currency', 'TRY',
            'direction', 'ADD',
            'occurred_on', '2026-09-01',
            'note', 'İlk katkı'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 42 Başarısız: GOAL_CONTRIBUTION replay ilk snapshot ile eşleşmedi.';
    end if;

    select count(*) into v_contrib_count from public.goal_contributions where goal_id = v_goal_id and deleted_at is null;
    if v_contrib_count <> 1 then
        raise exception 'Senaryo 42 Başarısız: Replay sonrası duplicate katkı kaydı oluştu.';
    end if;


    -- =========================================================================
    -- SENARYO 43: GOAL_CONTRIBUTION CREATE REMOVE ve Underflow Reddi
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_contrib_rem,
        p_entity_type := 'GOAL_CONTRIBUTION',
        p_operation := 'CREATE',
        p_base_version := 3, -- Parent Goal güncel sürümü
        p_payload := jsonb_build_object(
            'id', v_contrib_rem_id,
            'goal_id', v_goal_id,
            'amount_minor', 5000000,
            'currency', 'TRY',
            'direction', 'REMOVE',
            'occurred_on', '2026-09-02',
            'note', 'Kısmi çekim'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' -> 'goal' ->> 'current_amount_minor')::bigint <> 10000000
       or (v_res -> 'record' -> 'goal' ->> 'version')::bigint <> 4 then
        raise exception 'Senaryo 43 Başarısız: GOAL_CONTRIBUTION REMOVE APPLIED veya bakiye 10.000.000 olmadı.';
    end if;

    -- Underflow Denemesi (Bakiye 10.000.000 iken 20.000.000 çıkarma - fail-closed)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_contrib_underflow,
            p_entity_type := 'GOAL_CONTRIBUTION',
            p_operation := 'CREATE',
            p_base_version := 4,
            p_payload := jsonb_build_object(
                'id', v_contrib_fail_id,
                'goal_id', v_goal_id,
                'amount_minor', 20000000, -- Bakiyeyi aşan tutar!
                'currency', 'TRY',
                'direction', 'REMOVE',
                'occurred_on', '2026-09-03'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 43 Başarısız: Bakiyeyi aşan REMOVE işlemi engellenmedi.';
    end if;

    -- Hedef bakiyesi değişmemiş olmalı
    select current_amount_minor into v_goal_db_current_amount from public.goals where id = v_goal_id;
    if v_goal_db_current_amount <> 10000000 then
        raise exception 'Senaryo 43 Başarısız: Underflow denemesinde hedef bakiyesi değişmemelidir.';
    end if;

    -- Eski parent version ile CONFLICT
    v_res := public.sync_write_v2(
        p_operation_id := v_op_contrib_conflict,
        p_entity_type := 'GOAL_CONTRIBUTION',
        p_operation := 'CREATE',
        p_base_version := 3, -- Güncel parent version 4
        p_payload := jsonb_build_object(
            'id', extensions.gen_random_uuid(),
            'goal_id', v_goal_id,
            'amount_minor', 1000000,
            'currency', 'TRY',
            'direction', 'ADD',
            'occurred_on', '2026-09-03'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' then
        raise exception 'Senaryo 43 Başarısız: Stale parent base_version ile katkı CONFLICT dönmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 44: GOAL DELETE Soft-Delete ve Katkı Tombstone Yayılımı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_goal,
        p_entity_type := 'GOAL',
        p_operation := 'DELETE',
        p_base_version := 4,
        p_payload := jsonb_build_object(
            'id', v_goal_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 44 Başarısız: Hedef DELETE APPLIED soft-delete dönmedi.';
    end if;

    select count(*) into v_contrib_count from public.goal_contributions where goal_id = v_goal_id and deleted_at is null;
    if v_contrib_count <> 0 then
        raise exception 'Senaryo 44 Başarısız: Hedef silindiğinde canlı katkılar tombstone yapılmadı.';
    end if;


    -- =========================================================================
    -- SENARYO 45: DEBT CREATE, Replay ve Idempotency
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_debt,
        p_entity_type := 'DEBT',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_debt_id,
            'user_id', v_test_user_id,
            'title', 'Elden Borç',
            'amount_minor', 5000000,
            'currency', 'TRY',
            'type', 'DEBT',
            'due_date', '2026-12-31',
            'status', 'OPEN',
            'description', 'Ahmet''ten alınan borç'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' ->> 'version')::bigint <> 1
       or (v_res -> 'record' ->> 'status') <> 'OPEN' then
        raise exception 'Senaryo 45 Başarısız: Borç CREATE APPLIED veya status OPEN dönmedi.';
    end if;

    -- DEBT CREATE Replay
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_debt,
        p_entity_type := 'DEBT',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_debt_id,
            'user_id', v_test_user_id,
            'title', 'Elden Borç',
            'amount_minor', 5000000,
            'currency', 'TRY',
            'type', 'DEBT',
            'due_date', '2026-12-31',
            'status', 'OPEN',
            'description', 'Ahmet''ten alınan borç'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 45 Başarısız: Borç CREATE replay ilk snapshot ile eşleşmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 46: DEBT UPDATE, Type Değişimi (RECEIVABLE), Status Manipülasyon Reddi ve Stale Conflict
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_debt,
        p_entity_type := 'DEBT',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_debt_id,
            'user_id', v_test_user_id,
            'title', 'Elden Alacak - Güncel',
            'amount_minor', 5000000,
            'currency', 'TRY',
            'type', 'RECEIVABLE', -- DEBT -> RECEIVABLE geçişi (geçerli domain kuralı)
            'due_date', '2027-01-15',
            'status', 'OPEN',
            'description', 'Açıklama güncellendi'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' ->> 'version')::bigint <> 2
       or (v_res -> 'record' ->> 'type') <> 'RECEIVABLE'
       or (v_res -> 'record' ->> 'status') <> 'OPEN' then
        raise exception 'Senaryo 46 Başarısız: Borç UPDATE APPLIED, version 2, type RECEIVABLE veya status OPEN dönmedi.';
    end if;

    select status, version into v_debt_db_status, v_debt_db_version from public.debts where id = v_debt_id and type = 'RECEIVABLE';
    if v_debt_db_status <> 'OPEN' or v_debt_db_version <> 2 then
        raise exception 'Senaryo 46 Başarısız: DB debts tablosunda type RECEIVABLE, status OPEN ve version 2 olarak güncellenmedi.';
    end if;

    -- Status doğrudan değiştirme denemesi (fail-closed)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_status_manipulate_debt,
            p_entity_type := 'DEBT',
            p_operation := 'UPDATE',
            p_base_version := 2,
            p_payload := jsonb_build_object(
                'id', v_debt_id,
                'user_id', v_test_user_id,
                'title', 'Elden Alacak - Güncel',
                'amount_minor', 5000000,
                'currency', 'TRY',
                'type', 'RECEIVABLE',
                'due_date', '2027-01-15',
                'status', 'SETTLED' -- Ödeme olmadan doğrudan değiştirilemez!
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 46 Başarısız: Borç status doğrudan değiştirme engellenmedi.';
    end if;

    -- Stale version UPDATE ile CONFLICT
    v_res := public.sync_write_v2(
        p_operation_id := v_op_conflict_debt,
        p_entity_type := 'DEBT',
        p_operation := 'UPDATE',
        p_base_version := 1, -- Güncel 2
        p_payload := jsonb_build_object(
            'id', v_debt_id,
            'user_id', v_test_user_id,
            'title', 'Stale Debt',
            'amount_minor', 5000000,
            'currency', 'TRY',
            'type', 'DEBT',
            'due_date', '2027-01-15'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' then
        raise exception 'Senaryo 46 Başarısız: Stale debt UPDATE CONFLICT dönmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 47: DEBT_PAYMENT Kısmi Ödeme (OPEN) ve Replay
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_pay_partial,
        p_entity_type := 'DEBT_PAYMENT',
        p_operation := 'CREATE',
        p_base_version := 2, -- Parent Debt sürümü
        p_payload := jsonb_build_object(
            'id', v_pay_part_id,
            'debt_id', v_debt_id,
            'amount_minor', 2000000,
            'currency', 'TRY',
            'paid_on', '2026-09-01'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' -> 'debt' ->> 'status') <> 'OPEN'
       or (v_res -> 'record' -> 'debt' ->> 'version')::bigint <> 3
       or (v_res -> 'record' -> 'payment' ->> 'id')::uuid <> v_pay_part_id then
        raise exception 'Senaryo 47 Başarısız: DEBT_PAYMENT kısmi ödeme APPLIED veya status OPEN kalmadı.';
    end if;

    select status, version into v_debt_db_status, v_debt_db_version from public.debts where id = v_debt_id;
    if v_debt_db_status <> 'OPEN' or v_debt_db_version <> 3 then
        raise exception 'Senaryo 47 Başarısız: DB debts tablosu status/version atomik güncellenmedi.';
    end if;

    -- DEBT_PAYMENT Replay (Duplicate Event Oluşmama Kanıtı)
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_pay_partial,
        p_entity_type := 'DEBT_PAYMENT',
        p_operation := 'CREATE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_pay_part_id,
            'debt_id', v_debt_id,
            'amount_minor', 2000000,
            'currency', 'TRY',
            'paid_on', '2026-09-01'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 47 Başarısız: DEBT_PAYMENT replay ilk snapshot ile eşleşmedi.';
    end if;

    select count(*) into v_payment_count from public.debt_payments where debt_id = v_debt_id and deleted_at is null;
    if v_payment_count <> 1 then
        raise exception 'Senaryo 47 Başarısız: Replay sonrası duplicate ödeme kaydı oluştu.';
    end if;


    -- =========================================================================
    -- SENARYO 48: DEBT_PAYMENT Overpayment Reddi ve Tam Ödeme (SETTLED)
    -- =========================================================================
    -- Toplam 5.000.000 anapara, 2.000.000 ödendi. 4.000.000 ödeme denemesi overpayment olmalı (fail-closed)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_pay_over,
            p_entity_type := 'DEBT_PAYMENT',
            p_operation := 'CREATE',
            p_base_version := 3,
            p_payload := jsonb_build_object(
                'id', v_pay_over_id,
                'debt_id', v_debt_id,
                'amount_minor', 4000000, -- Kalan 3.000.000'i aşıyor!
                'currency', 'TRY',
                'paid_on', '2026-09-02'
            )
        );
    exception when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 48 Başarısız: Anaparayı aşan ödeme engellenmedi.';
    end if;

    -- Stale parent version ile CONFLICT
    v_res := public.sync_write_v2(
        p_operation_id := v_op_pay_conflict,
        p_entity_type := 'DEBT_PAYMENT',
        p_operation := 'CREATE',
        p_base_version := 2, -- Güncel 3
        p_payload := jsonb_build_object(
            'id', extensions.gen_random_uuid(),
            'debt_id', v_debt_id,
            'amount_minor', 1000000,
            'currency', 'TRY',
            'paid_on', '2026-09-02'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' then
        raise exception 'Senaryo 48 Başarısız: Stale debt base_version ile ödeme CONFLICT dönmedi.';
    end if;

    -- Kalan 3.000.000'in tam ödenmesi -> SETTLED olmalı
    v_res := public.sync_write_v2(
        p_operation_id := v_op_pay_settle,
        p_entity_type := 'DEBT_PAYMENT',
        p_operation := 'CREATE',
        p_base_version := 3,
        p_payload := jsonb_build_object(
            'id', v_pay_full_id,
            'debt_id', v_debt_id,
            'amount_minor', 3000000,
            'currency', 'TRY',
            'paid_on', '2026-09-02'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' -> 'debt' ->> 'status') <> 'SETTLED'
       or (v_res -> 'record' -> 'debt' ->> 'version')::bigint <> 4 then
        raise exception 'Senaryo 48 Başarısız: Borç tam ödeme sonrası SETTLED veya version 4 olmadı.';
    end if;

    select status, version into v_debt_db_status, v_debt_db_version from public.debts where id = v_debt_id;
    if v_debt_db_status <> 'SETTLED' or v_debt_db_version <> 4 then
        raise exception 'Senaryo 48 Başarısız: DB debts tablosu SETTLED olarak güncellenmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 49: DEBT DELETE Soft-Delete ve Ödeme Tombstone Yayılımı
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_debt,
        p_entity_type := 'DEBT',
        p_operation := 'DELETE',
        p_base_version := 4,
        p_payload := jsonb_build_object(
            'id', v_debt_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 49 Başarısız: Borç DELETE APPLIED soft-delete dönmedi.';
    end if;

    select count(*) into v_payment_count from public.debt_payments where debt_id = v_debt_id and deleted_at is null;
    if v_payment_count <> 0 then
        raise exception 'Senaryo 49 Başarısız: Borç silindiğinde canlı ödemeler tombstone yapılmadı.';
    end if;


    -- =========================================================================
    -- SENARYO 50: WORKSPACE CREATE ve Atomic OWNER Bootstrap
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_create_ws,
        p_entity_type := 'WORKSPACE',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_ws_id,
            'name', '  Şirket Ana Çalışma Alanı  ',
            'type_code', 'shared',
            'currency_code', 'TRY',
            'description', 'Staging test workspace'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED' then
        raise exception 'Senaryo 50 Başarısız: WORKSPACE CREATE APPLIED dönmedi.';
    end if;

    if (v_res -> 'record' ->> 'normalized_name') <> 'şirket ana çalışma alanı' then
        raise exception 'Senaryo 50 Başarısız: normalized_name sunucuda doğru türetilmedi (beklenen: şirket ana çalışma alanı, gelen: %).', (v_res -> 'record' ->> 'normalized_name');
    end if;

    if (v_res -> 'record' ->> 'owner_id')::uuid <> v_test_user_id then
        raise exception 'Senaryo 50 Başarısız: owner_id actor_id ile eşleşmiyor.';
    end if;

    -- DB kontrolleri: workspace kaydı ve atomic owner member kaydı
    select version, name, normalized_name into v_ws_db_version, v_ws_db_name, v_ws_db_normalized
      from public.workspaces where id = v_ws_id;

    if v_ws_db_version <> 1 or v_ws_db_normalized <> 'şirket ana çalışma alanı' then
        raise exception 'Senaryo 50 Başarısız: DB workspaces tablosundaki kayıt beklenen değerlere sahip değil.';
    end if;

    select count(*), max(role_code) into v_ws_member_count, v_ws_member_role
      from public.workspace_members
     where workspace_id = v_ws_id and user_id = v_test_user_id and deleted_at is null;

    if v_ws_member_count <> 1 or v_ws_member_role <> 'OWNER' then
        raise exception 'Senaryo 50 Başarısız: Workspace CREATE sonrası atomic OWNER bootstrap üyelik kaydı bulunamadı.';
    end if;


    -- =========================================================================
    -- SENARYO 51: WORKSPACE CREATE Exact Idempotent Replay
    -- =========================================================================
    v_res_replay := public.sync_write_v2(
        p_operation_id := v_op_create_ws,
        p_entity_type := 'WORKSPACE',
        p_operation := 'CREATE',
        p_base_version := null,
        p_payload := jsonb_build_object(
            'id', v_ws_id,
            'name', '  Şirket Ana Çalışma Alanı  ',
            'type_code', 'shared',
            'currency_code', 'TRY',
            'description', 'Staging test workspace'
        )
    );

    if (v_res_replay ->> 'status') <> 'APPLIED' or (v_res_replay -> 'record') <> (v_res -> 'record') then
        raise exception 'Senaryo 51 Başarısız: Workspace CREATE idempotent replay ilk kayıt snapshotı ile tam eşleşmedi.';
    end if;

    select version into v_ws_db_version from public.workspaces where id = v_ws_id;
    if v_ws_db_version <> 1 then
        raise exception 'Senaryo 51 Başarısız: Workspace Replay versiyonu artırmamalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 52: WORKSPACE Payload Allowlist, Yasak Alan ve Zorunlu Alan İhlalleri
    -- =========================================================================
    -- A. Fazladan / bilinmeyen alan
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_extra_key,
            p_entity_type := 'WORKSPACE',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'name', 'Illegal Extra Key Workspace',
                'type_code', 'personal',
                'currency_code', 'TRY',
                'unknown_property', 'hacked'
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-A Başarısız: Bilinmeyen alan içeren payload reddedilmedi.';
    end if;

    -- B. Yasak owner_id alanı (istemciden enjekte edilmeye çalışılan)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_forbidden_owner,
            p_entity_type := 'WORKSPACE',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'name', 'Illegal Owner Workspace',
                'type_code', 'personal',
                'currency_code', 'TRY',
                'owner_id', extensions.gen_random_uuid()
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-B Başarısız: owner_id alanı içeren payload allowlist tarafından reddedilmedi.';
    end if;

    -- C. Yasak members / token alanı
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_forbidden_member,
            p_entity_type := 'WORKSPACE',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'name', 'Illegal Members Workspace',
                'type_code', 'personal',
                'currency_code', 'TRY',
                'members', jsonb_build_array(jsonb_build_object('user_id', extensions.gen_random_uuid(), 'role_code', 'OWNER'))
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-C Başarısız: members alanı içeren payload allowlist tarafından reddedilmedi.';
    end if;

    -- D. CREATE eksik/null type_code (silent default engeli)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_missing_type,
            p_entity_type := 'WORKSPACE',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'name', 'Missing Type Workspace',
                'currency_code', 'TRY'
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-D Başarısız: type_code eksik olan CREATE payload reddedilmedi.';
    end if;

    -- E. CREATE eksik/null currency_code (silent default engeli)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_missing_curr,
            p_entity_type := 'WORKSPACE',
            p_operation := 'CREATE',
            p_base_version := null,
            p_payload := jsonb_build_object(
                'id', extensions.gen_random_uuid(),
                'name', 'Missing Currency Workspace',
                'type_code', 'personal'
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-E Başarısız: currency_code eksik olan CREATE payload reddedilmedi.';
    end if;

    -- F. UPDATE eksik/null type_code (silent default engeli)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_update_missing_type,
            p_entity_type := 'WORKSPACE',
            p_operation := 'UPDATE',
            p_base_version := 1,
            p_payload := jsonb_build_object(
                'id', v_ws_id,
                'name', 'Updated Name',
                'currency_code', 'TRY'
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-F Başarısız: type_code eksik olan UPDATE payload reddedilmedi.';
    end if;

    -- G. UPDATE eksik/null currency_code (silent default engeli)
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_update_missing_curr,
            p_entity_type := 'WORKSPACE',
            p_operation := 'UPDATE',
            p_base_version := 1,
            p_payload := jsonb_build_object(
                'id', v_ws_id,
                'name', 'Updated Name',
                'type_code', 'personal'
            )
        );
    rescue when others then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 52-G Başarısız: currency_code eksik olan UPDATE payload reddedilmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 53: Non-OWNER UPDATE ve DELETE Yetki Reddi
    -- =========================================================================
    -- Başka bir kullanıcıyı VIEWER olarak ekle
    insert into public.workspace_members (
        workspace_id,
        user_id,
        role_code,
        joined_at,
        updated_at,
        deleted_at,
        version
    ) values (
        v_ws_id,
        v_other_user_id,
        'VIEWER',
        timezone('utc'::text, now()),
        timezone('utc'::text, now()),
        null,
        1
    );

    -- Actor'ı diğer kullanıcıya çevir
    perform set_config('request.jwt.claim.sub', v_other_user_id::text, true);

    -- A. Non-owner UPDATE denemesi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_nonowner_update,
            p_entity_type := 'WORKSPACE',
            p_operation := 'UPDATE',
            p_base_version := 1,
            p_payload := jsonb_build_object(
                'id', v_ws_id,
                'name', 'Viewer Tarafından Güncellenemez',
                'type_code', 'shared',
                'currency_code', 'TRY'
            )
        );
    rescue when insufficient_privilege then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 53-A Başarısız: VIEWER rolündeki kullanıcının UPDATE işlemi insufficient_privilege ile engellenmedi.';
    end if;

    -- B. Non-owner DELETE denemesi
    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            p_operation_id := v_op_ws_nonowner_delete,
            p_entity_type := 'WORKSPACE',
            p_operation := 'DELETE',
            p_base_version := 1,
            p_payload := jsonb_build_object(
                'id', v_ws_id
            )
        );
    rescue when insufficient_privilege then
        v_error_caught := true;
    end;

    if not v_error_caught then
        raise exception 'Senaryo 53-B Başarısız: VIEWER rolündeki kullanıcının DELETE işlemi insufficient_privilege ile engellenmedi.';
    end if;

    -- Actor'ı tekrar asıl test sahibine çevir
    perform set_config('request.jwt.claim.sub', v_test_user_id::text, true);


    -- =========================================================================
    -- SENARYO 54: WORKSPACE Optimistic Version Conflict (Stale base_version)
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_ws_conflict,
        p_entity_type := 'WORKSPACE',
        p_operation := 'UPDATE',
        p_base_version := 99,
        p_payload := jsonb_build_object(
            'id', v_ws_id,
            'name', 'Conflict Olacak İsim',
            'type_code', 'shared',
            'currency_code', 'TRY'
        )
    );

    if (v_res ->> 'status') <> 'CONFLICT' or (v_res -> 'record' ->> 'version')::bigint <> 1 then
        raise exception 'Senaryo 54 Başarısız: Yanlış base_version ile UPDATE CONFLICT ve güncel versiyon 1 dönmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 55: WORKSPACE UPDATE Başarılı Değişim
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_update_ws,
        p_entity_type := 'WORKSPACE',
        p_operation := 'UPDATE',
        p_base_version := 1,
        p_payload := jsonb_build_object(
            'id', v_ws_id,
            'name', 'Yeni Şirket Adı',
            'type_code', 'shared',
            'currency_code', 'USD',
            'description', 'Güncellenmiş açıklama'
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' ->> 'version')::bigint <> 2
       or (v_res -> 'record' ->> 'normalized_name') <> 'yeni şirket adı'
       or (v_res -> 'record' ->> 'currency_code') <> 'USD' then
        raise exception 'Senaryo 55 Başarısız: WORKSPACE UPDATE APPLIED veya version 2 / USD olmadı.';
    end if;

    select version, currency_code, normalized_name into v_ws_db_version, v_debt_currency, v_ws_db_normalized
      from public.workspaces where id = v_ws_id;

    if v_ws_db_version <> 2 or v_debt_currency <> 'USD' or v_ws_db_normalized <> 'yeni şirket adı' then
        raise exception 'Senaryo 55 Başarısız: DB workspaces tablosunda versiyon 2 veya USD güncellenmedi.';
    end if;


    -- =========================================================================
    -- SENARYO 56: WORKSPACE DELETE Soft-Delete (Members Tombstone Edilmez)
    -- =========================================================================
    v_res := public.sync_write_v2(
        p_operation_id := v_op_delete_ws,
        p_entity_type := 'WORKSPACE',
        p_operation := 'DELETE',
        p_base_version := 2,
        p_payload := jsonb_build_object(
            'id', v_ws_id
        )
    );

    if (v_res ->> 'status') <> 'APPLIED'
       or (v_res -> 'record' ->> 'version')::bigint <> 3
       or (v_res -> 'record' ->> 'deleted_at') is null then
        raise exception 'Senaryo 56 Başarısız: WORKSPACE DELETE APPLIED soft-delete ve version 3 dönmedi.';
    end if;

    select deleted_at into v_ws_db_deleted_at from public.workspaces where id = v_ws_id;
    if v_ws_db_deleted_at is null then
        raise exception 'Senaryo 56 Başarısız: DB workspaces tablosunda deleted_at atanmadı.';
    end if;

    -- Membership kayıtlarının canlı kalma invariant'ı (soft-delete edilmez)
    select count(*) into v_ws_member_count from public.workspace_members where workspace_id = v_ws_id and deleted_at is null;
    if v_ws_member_count < 2 then
        raise exception 'Senaryo 56 Başarısız: Workspace silindiğinde membership satırları silinmemeli/tombstone edilmemelidir.';
    end if;


    -- =========================================================================
    -- SENARYO 57: Tombstone Satırının RLS SELECT Görünürlüğü (authenticated rolünde)
    -- =========================================================================
    -- Gerçek authenticated rolü ve owner JWT kimliği altında RLS değerlendirmesini çalıştır
    perform set_config('request.jwt.claim.sub', v_test_user_id::text, true);
    set local role authenticated;

    select exists (
        select 1
          from public.workspaces
         where id = v_ws_id
    ) into v_ws_visible;

    select exists (
        select 1
          from public.workspace_members
         where workspace_id = v_ws_id and user_id = v_test_user_id
    ) into v_ws_member_visible;

    -- Privileged role geri dön
    reset role;
    perform set_config('request.jwt.claim.sub', v_test_user_id::text, true);

    if not v_ws_visible then
        raise exception 'Senaryo 57 Başarısız: Soft-delete edilmiş workspace tombstone kaydı authenticated üye için RLS SELECT sorgusunda görünür kalmalıdır.';
    end if;

    if not v_ws_member_visible then
        raise exception 'Senaryo 57 Başarısız: Workspace üyelik kaydı authenticated üye için RLS SELECT sorgusunda görünür kalmalıdır.';
    end if;


    -- =========================================================================
    -- SENARYO 58: BİLGİLENDİRME VE GÜVENLİ TEMİZLİK
    -- =========================================================================
    raise notice 'Tüm 57 sözleşme ve idempotency senaryosu (PROFILE, CATEGORY, TRANSACTION, BUDGET, RECURRING_TRANSACTION, SUBSCRIPTION, GOAL, GOAL_CONTRIBUTION, DEBT, DEBT_PAYMENT, WORKSPACE) başarıyla doğrulandı. İşlemler ROLLBACK ile geri alınıyor.';
end
$$;

-- 58. Koşulsuz ROLLBACK: Veritabanında hiçbir geçici kayıt veya yan etki bırakılmaz
rollback;

