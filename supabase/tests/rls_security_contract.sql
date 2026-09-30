-- ============================================================================
-- SQL SÖZLEŞME TESTİ: rls_security_contract.sql
-- Kapsam: Çekirdek RLS negatif güvenlik matrisi, rol ve workspace izolasyonu,
-- PostgREST doğrudan erişim kısıtlamaları ve sync_write_v2 yetki denetimi.
-- Ortam: Yalnız disposable yerel PostgreSQL. İşlem sonunda ROLLBACK yapılır.
-- ============================================================================

begin;

do $$
declare
    -- Test kullanıcıları (auth.users)
    v_user_a uuid := 'a0000000-0000-4000-8000-000000000001';
    v_user_b uuid := 'a0000000-0000-4000-8000-000000000002';
    v_user_c uuid := 'a0000000-0000-4000-8000-000000000003'; -- Dış kullanıcı (non-member)
    v_owner  uuid := 'a0000000-0000-4000-8000-000000000004'; -- WS1 Sahibi
    v_editor uuid := 'a0000000-0000-4000-8000-000000000005'; -- WS1 Editor
    v_viewer uuid := 'a0000000-0000-4000-8000-000000000006'; -- WS1 Viewer
    v_user_d uuid := 'a0000000-0000-4000-8000-000000000007'; -- WS2 Sahibi/Üyesi

    -- Test çalışma alanları
    v_ws1_id uuid := 'c0000000-0000-4000-8000-000000000001';
    v_ws2_id uuid := 'c0000000-0000-4000-8000-000000000002';

    -- Test kategorileri
    v_sys_cat_id uuid := 'e0000000-0000-4000-8000-000000000001';
    v_cat_a_id   uuid := 'e0000000-0000-4000-8000-000000000002';
    v_cat_b_id   uuid := 'e0000000-0000-4000-8000-000000000003';
    v_cat_ws1_id uuid := 'e0000000-0000-4000-8000-000000000004';

    -- Test işlemleri (transactions)
    v_tx_a_id             uuid := 'f0000000-0000-4000-8000-000000000001';
    v_tx_b_id             uuid := 'f0000000-0000-4000-8000-000000000002';
    v_ws_tx_id            uuid := 'f0000000-0000-4000-8000-000000000003';
    v_viewer_create_tx_id uuid := 'f0000000-0000-4000-8000-000000000004';
    v_viewer_update_tx_id uuid := 'f0000000-0000-4000-8000-000000000005';
    v_viewer_delete_tx_id uuid := 'f0000000-0000-4000-8000-000000000006';
    v_personal_create_tx_id uuid := 'f0000000-0000-4000-8000-000000000007';
    v_service_role_tx_id    uuid := 'f0000000-0000-4000-8000-000000000008';

    -- Durum değişkenleri
    v_cnt int;
    v_rows int;
    v_err_caught boolean;
    v_res jsonb;
    v_res_b jsonb;
    v_sqlstate text;
    v_errmsg text;
    v_check_amount bigint;
    v_check_version int;
    v_check_deleted_at timestamptz;
    v_pass_count int := 0;
    v_fail_count int := 0;
    v_failures text[] := array[]::text[];
    v_vulnerabilities text[] := array[]::text[];
    v_all_results text[] := array[]::text[];
    v_summary text;

    -- Metadata denetim listeleri (19 çekirdek public tablosu)
    v_check_tables text[] := array[
        'profiles',
        'categories',
        'transactions',
        'sync_operations_receipts',
        'budgets',
        'recurring_transactions',
        'subscriptions',
        'goals',
        'goal_contributions',
        'debts',
        'debt_payments',
        'workspaces',
        'workspace_members',
        'workspace_invitations',
        'assets',
        'market_prices',
        'market_price_rate_limits',
        'subscription_price_histories',
        'subscription_payments'
    ];
    v_mutation_privs text[] := array['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE'];
    v_all_privs text[] := array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'];
    v_tbl text;
    v_priv text;
    v_role text;
    v_priv_report text;
    v_rls_enabled boolean;
begin
    -- ========================================================================
    -- FİXTURE KURULUMU (postgres test yöneticisi bağlamında)
    -- ========================================================================

    -- 1. auth.users
    insert into auth.users (id, email) values
        (v_user_a, 'user_a@feniqo.test'),
        (v_user_b, 'user_b@feniqo.test'),
        (v_user_c, 'user_c@feniqo.test'),
        (v_owner,  'owner@feniqo.test'),
        (v_editor, 'editor@feniqo.test'),
        (v_viewer, 'viewer@feniqo.test'),
        (v_user_d, 'user_d@feniqo.test')
    on conflict (id) do nothing;

    -- 2. profiles
    insert into public.profiles (id, full_name, email, created_at, updated_at, version) values
        (v_user_a, 'User A', 'user_a@feniqo.test', now(), now(), 1),
        (v_user_b, 'User B', 'user_b@feniqo.test', now(), now(), 1),
        (v_user_c, 'User C', 'user_c@feniqo.test', now(), now(), 1),
        (v_owner,  'Owner',  'owner@feniqo.test',  now(), now(), 1),
        (v_editor, 'Editor', 'editor@feniqo.test', now(), now(), 1),
        (v_viewer, 'Viewer', 'viewer@feniqo.test', now(), now(), 1),
        (v_user_d, 'User D', 'user_d@feniqo.test', now(), now(), 1)
    on conflict (id) do nothing;

    -- 3. workspaces
    insert into public.workspaces (id, name, normalized_name, owner_id, type_code, currency_code, created_at, updated_at, version) values
        (v_ws1_id, 'Shared WS 1', 'shared ws 1', v_owner, 'shared', 'TRY', now(), now(), 1),
        (v_ws2_id, 'Other WS 2',  'other ws 2',  v_user_d, 'shared', 'TRY', now(), now(), 1)
    on conflict (id) do nothing;

    -- 4. workspace_members
    insert into public.workspace_members (workspace_id, user_id, role_code, joined_at, updated_at, version) values
        (v_ws1_id, v_owner,  'OWNER',  now(), now(), 1),
        (v_ws1_id, v_editor, 'EDITOR', now(), now(), 1),
        (v_ws1_id, v_viewer, 'VIEWER', now(), now(), 1),
        (v_ws2_id, v_user_d, 'OWNER',  now(), now(), 1)
    on conflict (workspace_id, user_id) do nothing;

    -- 5. categories
    insert into public.categories (id, user_id, workspace_id, name, slug, type, color, icon, is_default, created_at, version) values
        (v_sys_cat_id, null,     null,     'System Expense', 'system-expense', 'expense', '#000000', 'box', true,  now(), 1),
        (v_cat_a_id,   v_user_a, null,     'User A Cat',     'user-a-cat',     'expense', '#111111', 'tag', false, now(), 1),
        (v_cat_b_id,   v_user_b, null,     'User B Cat',     'user-b-cat',     'expense', '#222222', 'tag', false, now(), 1),
        (v_cat_ws1_id, v_owner,  v_ws1_id, 'WS1 Shared Cat', 'ws1-shared-cat', 'expense', '#333333', 'bag', false, now(), 1)
    on conflict (id) do nothing;

    -- 6. transactions
    insert into public.transactions (id, user_id, workspace_id, paid_by_user_id, participant_user_ids, amount_minor, currency, type, category_id, description, payment_method, transaction_date, created_at, version, deleted_at) values
        (v_tx_a_id,             v_user_a, null,     v_user_a, jsonb_build_array(v_user_a::text), 1000, 'TRY', 'expense', v_cat_a_id,   'A personal tx',          'cash', current_date, now(), 1, null),
        (v_tx_b_id,             v_user_b, null,     v_user_b, jsonb_build_array(v_user_b::text), 2000, 'TRY', 'expense', v_cat_b_id,   'B personal tx',          'cash', current_date, now(), 1, null),
        (v_ws_tx_id,            v_owner,  v_ws1_id, v_owner,  jsonb_build_array(v_owner::text),  3000, 'TRY', 'expense', v_cat_ws1_id, 'WS1 shared tx',          'cash', current_date, now(), 1, null),
        (v_viewer_update_tx_id, v_viewer, v_ws1_id, v_viewer, jsonb_build_array(v_viewer::text), 4000, 'TRY', 'expense', v_cat_ws1_id, 'Viewer update fixture', 'cash', current_date, now(), 1, null),
        (v_viewer_delete_tx_id, v_viewer, v_ws1_id, v_viewer, jsonb_build_array(v_viewer::text), 5000, 'TRY', 'expense', v_cat_ws1_id, 'Viewer delete fixture', 'cash', current_date, now(), 1, null)
    on conflict (id) do nothing;


    -- ========================================================================
    -- A. PROFILES SENARYOLARI
    -- ========================================================================

    -- Senaryo 1: Kullanıcı A kendi profilini SELECT edebilir
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.profiles where id = v_user_a;
        reset role;
        if v_cnt = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 1: Kullanıcı A kendi profilini okuyabildi.');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 1 FAIL: Kullanıcı A kendi profilini okuyamadı.');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 1: Kullanıcı A kendi profilini okuyamadı.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 1 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 1: ' || sqlerrm);
    end;

    -- Senaryo 2: Kullanıcı A, kullanıcı B profilini SELECT edemez; sonuç 0 satır
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.profiles where id = v_user_b;
        reset role;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 2: Kullanıcı A kullanıcı B profilini okuyamadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 2 FAIL: Kullanıcı A kullanıcı B profilini okuyabildi (satır sayısı: ' || v_cnt || ').');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 2: Kullanıcı A kullanıcı B profilini okuyabildi.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 2 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 2: ' || sqlerrm);
    end;

    -- Senaryo 3: Kullanıcı A, kullanıcı B profilini UPDATE edemez (0 satır güncellenir)
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        update public.profiles set full_name = 'hacked' where id = v_user_b;
        get diagnostics v_rows = row_count;
        reset role;
        if v_rows = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 3: Kullanıcı A kullanıcı B profilini güncelleyemedi (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 3 FAIL: Kullanıcı A kullanıcı B profilini güncelleyebildi (etkilenen: ' || v_rows || ').');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 3: Kullanıcı A kullanıcı B profilini güncelleyebildi.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 3 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 3: ' || sqlerrm);
    end;

    -- Senaryo 4: Kullanıcı A kendi profilinin identity/id alanını değiştiremez (Trigger)
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        update public.profiles set id = gen_random_uuid() where id = v_user_a;
        reset role;
    exception when others then
        v_err_caught := true;
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 4: Profil identity/id alanının değiştirilmesi tetikleyici tarafından engellendi.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 4 FAIL: Profil identity alanı değiştirilebildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 4: Profil identity alanı değiştirilebildi!');
    end if;


    -- ========================================================================
    -- B. CATEGORIES SENARYOLARI
    -- ========================================================================

    -- Senaryo 5: Kullanıcı A sistem kategorilerini okuyabilir
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.categories where is_default = true and user_id is null;
        reset role;
        if v_cnt > 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 5: Kullanıcı A sistem kategorilerini okuyabildi (adet: ' || v_cnt || ').');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 5 FAIL: Kullanıcı A sistem kategorilerini okuyamadı.');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 5: Kullanıcı A sistem kategorilerini okuyamadı.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 5 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 5: ' || sqlerrm);
    end;

    -- Senaryo 6: Kullanıcı A, kullanıcı B kişisel kategorisini okuyamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.categories where id = v_cat_b_id;
        reset role;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 6: Kullanıcı A kullanıcı B kişisel kategorisini okuyamadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 6 FAIL: Kullanıcı A kullanıcı B kategorisini okuyabildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 6: Kullanıcı A kullanıcı B kategorisini okuyabildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 6 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 6: ' || sqlerrm);
    end;

    -- Senaryo 7: Kullanıcı A, kullanıcı B kategorisini UPDATE edemez
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        update public.categories set name = 'hacked' where id = v_cat_b_id;
        get diagnostics v_rows = row_count;
        reset role;
        if v_rows = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 7: Kullanıcı A kullanıcı B kategorisini güncelleyemedi (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 7 FAIL: Kullanıcı A kullanıcı B kategorisini güncelleyebildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 7: Kullanıcı A kullanıcı B kategorisini güncelleyebildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 7 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 7: ' || sqlerrm);
    end;

    -- Senaryo 8: Kullanıcı A `is_default=true` sistem kategorisi oluşturamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        insert into public.categories (id, user_id, name, slug, type, color, is_default, version)
        values (gen_random_uuid(), v_user_a, 'fake_sys_cat', 'fake-sys-cat', 'expense', '#fff', true, 1);
        reset role;
    exception when others then
        v_err_caught := true;
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 8: Kullanıcı A is_default=true kategorisi oluşturamadı (RLS check engeli).');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 8 FAIL: Kullanıcı A is_default=true kategorisi oluşturabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 8: Kullanıcı A is_default=true kategorisi oluşturabildi!');
    end if;

    -- Senaryo 9: Kullanıcı A mevcut sistem kategorisini değiştiremez
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        update public.categories set name = 'modified_system' where id = v_sys_cat_id;
        get diagnostics v_rows = row_count;
        reset role;
        if v_rows = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 9: Kullanıcı A sistem kategorisini güncelleyemedi (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 9 FAIL: Kullanıcı A sistem kategorisini değiştirebildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 9: Kullanıcı A sistem kategorisini değiştirebildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 9 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 9: ' || sqlerrm);
    end;

    -- Senaryo 10: Authenticated doğrudan hard DELETE yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'delete from public.categories where id = ' || quote_literal(v_cat_a_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 10: Authenticated rolünün categories tablosunda hard DELETE yetkisi yok.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 10 FAIL: Authenticated rolü categories tablosunda hard DELETE yapabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 10: categories hard DELETE yapılabildi!');
    end if;


    -- ========================================================================
    -- C. PERSONAL TRANSACTIONS SENARYOLARI
    -- ========================================================================

    -- Senaryo 11: Kullanıcı A kendi kişisel işlemini okuyabilir
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.transactions where id = v_tx_a_id;
        reset role;
        if v_cnt = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 11: Kullanıcı A kendi kişisel işlemini okuyabildi.');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 11 FAIL: Kullanıcı A kendi kişisel işlemini okuyamadı.');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 11: Kullanıcı A kendi kişisel işlemini okuyamadı.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 11 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 11: ' || sqlerrm);
    end;

    -- Senaryo 12: Kullanıcı A, kullanıcı B kişisel işlemini okuyamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.transactions where id = v_tx_b_id;
        reset role;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 12: Kullanıcı A kullanıcı B kişisel işlemini okuyamadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 12 FAIL: Kullanıcı A kullanıcı B kişisel işlemini okuyabildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 12: Kullanıcı A kullanıcı B işlemini okuyabildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 12 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 12: ' || sqlerrm);
    end;

    -- Senaryo 13: Kullanıcı A, kullanıcı B işlemini UPDATE edemez
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        update public.transactions set description = 'hacked' where id = v_tx_b_id;
        get diagnostics v_rows = row_count;
        reset role;
        if v_rows = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 13: Kullanıcı A kullanıcı B işlemini güncelleyemedi (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 13 FAIL: Kullanıcı A kullanıcı B işlemini güncelleyebildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 13: Kullanıcı A kullanıcı B işlemini güncelleyebildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 13 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 13: ' || sqlerrm);
    end;

    -- Senaryo 14: Authenticated doğrudan transaction DELETE yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'delete from public.transactions where id = ' || quote_literal(v_tx_a_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 14: Authenticated rolünün transactions tablosunda doğrudan DELETE yetkisi yok.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 14 FAIL: Authenticated transactions tablosunda doğrudan DELETE yapabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 14: transactions doğrudan DELETE yapılabildi!');
    end if;

    -- Senaryo 15: Anon transactions SELECT/INSERT/UPDATE/DELETE yapamaz
    v_err_caught := false;
    set local role anon;
    begin
        execute 'select count(*) from public.transactions';
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 15: anon rolünün transactions üzerinde yetkisi yok (insufficient_privilege).');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 15 FAIL: anon rolü transactions tablosuna erişebildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 15: anon rolü transactions tablosuna erişebildi!');
    end if;


    -- ========================================================================
    -- D. WORKSPACE TRANSACTION İZOLASYONU
    -- ========================================================================

    -- Senaryo 16: Workspace EDITOR ortak transaction'ı okuyabilir
    perform set_config('request.jwt.claims', json_build_object('sub', v_editor)::text, true);
    perform set_config('request.jwt.claim.sub', v_editor::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.transactions where id = v_ws_tx_id;
        reset role;
        if v_cnt = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 16: Workspace EDITOR ortak transactionı okuyabildi.');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 16 FAIL: Workspace EDITOR ortak transactionı okuyamadı.');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 16: Workspace EDITOR ortak transactionı okuyamadı.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 16 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 16: ' || sqlerrm);
    end;

    -- Senaryo 17: Workspace dışındaki kullanıcı ortak transaction'ı okuyamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_c)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_c::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.transactions where id = v_ws_tx_id;
        reset role;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 17: Dış kullanıcı ortak transactionı okuyamadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 17 FAIL: Dış kullanıcı ortak transactionı okuyabildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 17: Dış kullanıcı ortak transactionı okuyabildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 17 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 17: ' || sqlerrm);
    end;

    -- Senaryo 18: Başka workspace üyesi bu workspace transaction'ını okuyamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_d)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_d::text, true);
    set local role authenticated;
    begin
        select count(*) into v_cnt from public.transactions where id = v_ws_tx_id;
        reset role;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 18: Diğer workspace üyesi WS1 transactionını okuyamadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 18 FAIL: Diğer workspace üyesi WS1 transactionını okuyabildi!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 18: Diğer workspace üyesi WS1 transactionını okuyabildi!');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 18 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 18: ' || sqlerrm);
    end;

    -- Senaryo 19: VIEWER rolündeki kullanıcı sync_write_v2 üzerinden workspace TRANSACTION CREATE yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_viewer)::text, true);
    perform set_config('request.jwt.claim.sub', v_viewer::text, true);
    v_res := null;
    v_err_caught := false;
    v_sqlstate := null;
    v_errmsg := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('viewer_tx_create_test'),
            'TRANSACTION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_viewer_create_tx_id,
                'user_id', v_viewer,
                'workspace_id', v_ws1_id,
                'category_id', v_cat_ws1_id,
                'amount_minor', 5500,
                'currency', 'TRY',
                'type', 'expense',
                'payment_method', 'cash',
                'transaction_date', current_date
            )
        );
        reset role;
    exception when insufficient_privilege then
        reset role;
        v_err_caught := true;
    when others then
        v_sqlstate := SQLSTATE;
        v_errmsg := SQLERRM;
        reset role;
    end;

    if v_err_caught then
        select count(*) into v_cnt from public.transactions where id = v_viewer_create_tx_id;
        if v_cnt = 0 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 19: VIEWER TRANSACTION CREATE insufficient_privilege ile engellendi; hedef satır oluşmadı (0 satır).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 19 FAIL: insufficient_privilege alındı fakat hedef satır eklendi (satır: ' || v_cnt || ')!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 19: İstisna alındı fakat satır oluşturuldu.');
        end if;
    elsif v_sqlstate is not null then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 19 FAIL (Beklenmeyen istisna): SQLSTATE=' || v_sqlstate || ', ' || v_errmsg);
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 19: Beklenmeyen istisna (SQLSTATE=' || v_sqlstate || '): ' || v_errmsg);
    elsif v_res ->> 'status' = 'APPLIED' then
        select count(*) into v_cnt from public.transactions where id = v_viewer_create_tx_id;
        v_fail_count := v_fail_count + 1;
        v_vulnerabilities := array_append(v_vulnerabilities, 'GÜVENLİK AÇIĞI (BLOKE): VIEWER rolü ortak alanda TRANSACTION CREATE yapabildi! Status=APPLIED, Satır=' || v_cnt || ' (Senaryo 19)');
        v_failures := array_append(v_failures, 'Senaryo 19 FAIL: VIEWER rolü TRANSACTION CREATE yapabildi (Status=APPLIED, Satır=' || v_cnt || ')!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 19: VIEWER TRANSACTION CREATE engellenemedi (Status=APPLIED - Güvenlik Açığı, Satır=' || v_cnt || ')');
    elsif v_res ->> 'status' = 'CONFLICT' then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 19 FAIL (TEST INVALID): VIEWER TRANSACTION CREATE yetkilendirme yerine CONFLICT döndürdü!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 19: Yetkilendirme reddi yerine CONFLICT döndü (Test Geçersiz).');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 19 FAIL: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 19: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
    end if;

    -- Senaryo 20: VIEWER workspace TRANSACTION UPDATE yapamaz (bağımsız fixture ve base_version=1 ile)
    perform set_config('request.jwt.claims', json_build_object('sub', v_viewer)::text, true);
    perform set_config('request.jwt.claim.sub', v_viewer::text, true);
    v_res := null;
    v_err_caught := false;
    v_sqlstate := null;
    v_errmsg := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('viewer_tx_update_test'),
            'TRANSACTION',
            'UPDATE',
            1,
            jsonb_build_object(
                'id', v_viewer_update_tx_id,
                'user_id', v_viewer,
                'workspace_id', v_ws1_id,
                'category_id', v_cat_ws1_id,
                'amount_minor', 9900,
                'currency', 'TRY',
                'type', 'expense',
                'payment_method', 'cash',
                'transaction_date', current_date
            )
        );
        reset role;
    exception when insufficient_privilege then
        reset role;
        v_err_caught := true;
    when others then
        v_sqlstate := SQLSTATE;
        v_errmsg := SQLERRM;
        reset role;
    end;

    -- Durum sonrası veri assertion'ı
    select amount_minor, version, deleted_at
    into v_check_amount, v_check_version, v_check_deleted_at
    from public.transactions
    where id = v_viewer_update_tx_id;

    if v_err_caught then
        if v_check_amount = 4000 and v_check_version = 1 and v_check_deleted_at is null then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 20: VIEWER TRANSACTION UPDATE insufficient_privilege ile engellendi; veri değişmedi (amount=4000, ver=1).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 20 FAIL: insufficient_privilege alındı fakat veri bozuldu! amount=' || coalesce(v_check_amount::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null'));
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 20: İstisna alındı fakat fixture verisi bozuldu.');
        end if;
    elsif v_sqlstate is not null then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 20 FAIL (Beklenmeyen istisna): SQLSTATE=' || v_sqlstate || ', ' || v_errmsg);
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 20: Beklenmeyen istisna (SQLSTATE=' || v_sqlstate || '): ' || v_errmsg);
    elsif v_res ->> 'status' = 'APPLIED' then
        v_fail_count := v_fail_count + 1;
        v_vulnerabilities := array_append(v_vulnerabilities, 'GÜVENLİK AÇIĞI (BLOKE): VIEWER rolü ortak alanda TRANSACTION UPDATE yapabildi! Status=APPLIED, Yeni Amount=' || coalesce(v_check_amount::text, 'null') || ', Yeni Version=' || coalesce(v_check_version::text, 'null') || ' (Senaryo 20)');
        v_failures := array_append(v_failures, 'Senaryo 20 FAIL: VIEWER rolü TRANSACTION UPDATE yapabildi (Status=APPLIED, amount=' || coalesce(v_check_amount::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null') || ')!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 20: VIEWER TRANSACTION UPDATE engellenemedi (Status=APPLIED - Güvenlik Açığı, amount=' || coalesce(v_check_amount::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null') || ')');
    elsif v_res ->> 'status' = 'CONFLICT' then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 20 FAIL (TEST INVALID): VIEWER TRANSACTION UPDATE yetkilendirme yerine CONFLICT döndürdü!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 20: Yetkilendirme reddi yerine CONFLICT döndü (Test Geçersiz).');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 20 FAIL: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 20: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
    end if;

    -- Senaryo 21: VIEWER workspace TRANSACTION DELETE yapamaz (bağımsız fixture ve base_version=1 ile)
    perform set_config('request.jwt.claims', json_build_object('sub', v_viewer)::text, true);
    perform set_config('request.jwt.claim.sub', v_viewer::text, true);
    v_res := null;
    v_err_caught := false;
    v_sqlstate := null;
    v_errmsg := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('viewer_tx_delete_test'),
            'TRANSACTION',
            'DELETE',
            1,
            jsonb_build_object(
                'id', v_viewer_delete_tx_id,
                'user_id', v_viewer,
                'workspace_id', v_ws1_id
            )
        );
        reset role;
    exception when insufficient_privilege then
        reset role;
        v_err_caught := true;
    when others then
        v_sqlstate := SQLSTATE;
        v_errmsg := SQLERRM;
        reset role;
    end;

    -- Durum sonrası veri assertion'ı
    select amount_minor, version, deleted_at
    into v_check_amount, v_check_version, v_check_deleted_at
    from public.transactions
    where id = v_viewer_delete_tx_id;

    if v_err_caught then
        if v_check_deleted_at is null and v_check_version = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 21: VIEWER TRANSACTION DELETE insufficient_privilege ile engellendi; deleted_at NULL kaldı (ver=1).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 21 FAIL: insufficient_privilege alındı fakat kayıt silindi veya version arttı! deleted_at=' || coalesce(v_check_deleted_at::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null'));
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 21: İstisna alındı fakat fixture verisi silindi.');
        end if;
    elsif v_sqlstate is not null then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 21 FAIL (Beklenmeyen istisna): SQLSTATE=' || v_sqlstate || ', ' || v_errmsg);
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 21: Beklenmeyen istisna (SQLSTATE=' || v_sqlstate || '): ' || v_errmsg);
    elsif v_res ->> 'status' = 'APPLIED' then
        v_fail_count := v_fail_count + 1;
        v_vulnerabilities := array_append(v_vulnerabilities, 'GÜVENLİK AÇIĞI (BLOKE): VIEWER rolü ortak alanda TRANSACTION DELETE yapabildi! Status=APPLIED, deleted_at=' || coalesce(v_check_deleted_at::text, 'null') || ', Yeni Version=' || coalesce(v_check_version::text, 'null') || ' (Senaryo 21)');
        v_failures := array_append(v_failures, 'Senaryo 21 FAIL: VIEWER rolü TRANSACTION DELETE yapabildi (Status=APPLIED, deleted_at=' || coalesce(v_check_deleted_at::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null') || ')!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 21: VIEWER TRANSACTION DELETE engellenemedi (Status=APPLIED - Güvenlik Açığı, deleted_at=' || coalesce(v_check_deleted_at::text, 'null') || ', ver=' || coalesce(v_check_version::text, 'null') || ')');
    elsif v_res ->> 'status' = 'CONFLICT' then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 21 FAIL (TEST INVALID): VIEWER TRANSACTION DELETE yetkilendirme yerine CONFLICT döndürdü!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 21: Yetkilendirme reddi yerine CONFLICT döndü (Test Geçersiz).');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 21 FAIL: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 21: Beklenmeyen status=' || coalesce(v_res ->> 'status', 'null'));
    end if;

    -- Senaryo 22: EDITOR ve OWNER izin verilen finansal mutation davranışı (CREATE için APPLIED)
    perform set_config('request.jwt.claims', json_build_object('sub', v_editor)::text, true);
    perform set_config('request.jwt.claim.sub', v_editor::text, true);
    v_res := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('editor_tx_create_test'),
            'TRANSACTION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', gen_random_uuid(),
                'user_id', v_editor,
                'workspace_id', v_ws1_id,
                'category_id', v_cat_ws1_id,
                'amount_minor', 7500,
                'currency', 'TRY',
                'type', 'expense',
                'payment_method', 'cash',
                'transaction_date', current_date
            )
        );
        reset role;
    exception when others then
        reset role;
    end;

    -- OWNER için finansal mutation doğrulaması
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner)::text, true);
    perform set_config('request.jwt.claim.sub', v_owner::text, true);
    v_res_b := null;
    set local role authenticated;
    begin
        v_res_b := public.sync_write_v2(
            md5('owner_tx_create_test'),
            'TRANSACTION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', gen_random_uuid(),
                'user_id', v_owner,
                'workspace_id', v_ws1_id,
                'category_id', v_cat_ws1_id,
                'amount_minor', 8500,
                'currency', 'TRY',
                'type', 'expense',
                'payment_method', 'cash',
                'transaction_date', current_date
            )
        );
        reset role;
    exception when others then
        reset role;
    end;

    if v_res ->> 'status' = 'APPLIED' and v_res_b ->> 'status' = 'APPLIED' then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 22: EDITOR ve OWNER finansal mutation (CREATE) sözleşmeye uygun biçimde APPLIED döndü.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 22 FAIL: EDITOR veya OWNER TRANSACTION CREATE APPLIED dönmedi (EDITOR: ' || coalesce(v_res ->> 'status', 'null') || ', OWNER: ' || coalesce(v_res_b ->> 'status', 'null') || ')');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 22: EDITOR veya OWNER TRANSACTION CREATE APPLIED dönmedi.');
    end if;


    -- ========================================================================
    -- E. WORKSPACE ÜYELİK VE ROL GÜVENLİĞİ
    -- ========================================================================

    -- Senaryo 23: Davetsiz kullanıcı doğrudan WORKSPACE_MEMBER CREATE yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_c)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_c::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'insert into public.workspace_members (workspace_id, user_id, role_code) values (' || quote_literal(v_ws1_id) || ', ' || quote_literal(v_user_c) || ', ''EDITOR'')';
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 23: Davetsiz kullanıcının workspace_members üzerinde doğrudan INSERT izni yok.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 23 FAIL: Davetsiz kullanıcı doğrudan WORKSPACE_MEMBER ekleyebildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 23: Davetsiz kullanıcı doğrudan WORKSPACE_MEMBER ekleyebildi!');
    end if;

    -- Senaryo 24: Davetsiz kullanıcı sync_write_v2 ile WORKSPACE_MEMBER CREATE yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_c)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_c::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('uninvited_member_create'),
            'WORKSPACE_MEMBER',
            'CREATE',
            null,
            jsonb_build_object('workspace_id', v_ws1_id, 'user_id', v_user_c)
        );
        reset role;
    exception when others then
        v_err_caught := true;
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 24: sync_write_v2 üzerinden genel WORKSPACE_MEMBER CREATE reddedildi.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 24 FAIL: sync_write_v2 ile davetsiz WORKSPACE_MEMBER eklenebildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 24: sync_write_v2 ile davetsiz WORKSPACE_MEMBER eklenebildi!');
    end if;

    -- Senaryo 25: EDITOR başka üyenin rolünü değiştiremez
    perform set_config('request.jwt.claims', json_build_object('sub', v_editor)::text, true);
    perform set_config('request.jwt.claim.sub', v_editor::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('editor_change_role_test'),
            'WORKSPACE_MEMBER',
            'UPDATE',
            1,
            jsonb_build_object('workspace_id', v_ws1_id, 'user_id', v_viewer, 'role_code', 'EDITOR')
        );
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 25: EDITOR üye rolü güncelleme denemesi insufficient_privilege ile engellendi.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 25 FAIL: EDITOR başka üyenin rolünü değiştirebildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 25: EDITOR başka üyenin rolünü değiştirebildi!');
    end if;

    -- Senaryo 26: EDITOR başka üyeyi çıkaramaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_editor)::text, true);
    perform set_config('request.jwt.claim.sub', v_editor::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('editor_remove_member_test'),
            'WORKSPACE_MEMBER',
            'DELETE',
            1,
            jsonb_build_object('workspace_id', v_ws1_id, 'user_id', v_viewer)
        );
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 26: EDITOR başka üyeyi çıkarma denemesi insufficient_privilege ile engellendi.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 26 FAIL: EDITOR başka üyeyi çıkarabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 26: EDITOR başka üyeyi çıkarabildi!');
    end if;

    -- Senaryo 27: VIEWER üyelik/rol mutation yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_viewer)::text, true);
    perform set_config('request.jwt.claim.sub', v_viewer::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('viewer_change_role_test'),
            'WORKSPACE_MEMBER',
            'UPDATE',
            1,
            jsonb_build_object('workspace_id', v_ws1_id, 'user_id', v_editor, 'role_code', 'VIEWER')
        );
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 27: VIEWER üye rolü mutation denemesi insufficient_privilege ile engellendi.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 27 FAIL: VIEWER üyelik mutation yapabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 27: VIEWER üyelik mutation yapabildi!');
    end if;

    -- Senaryo 28: OWNER'ın izin verilen üyelik yönetimi mevcut sözleşmeye göre çalışır
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner)::text, true);
    perform set_config('request.jwt.claim.sub', v_owner::text, true);
    v_res := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('owner_change_role_test'),
            'WORKSPACE_MEMBER',
            'UPDATE',
            1,
            jsonb_build_object('workspace_id', v_ws1_id, 'user_id', v_editor, 'role_code', 'VIEWER')
        );
        reset role;
        if v_res ->> 'status' = 'APPLIED' then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 28: OWNER üye rolü güncellemesi başarıyla APPLIED döndü.');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 28 FAIL: OWNER üyelik yönetimi APPLIED dönmedi: ' || coalesce(v_res::text, 'null'));
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 28: OWNER üyelik yönetimi APPLIED dönmedi.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 28 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 28: ' || sqlerrm);
    end;


    -- ========================================================================
    -- F. OPERATION RECEIPTS
    -- ========================================================================

    -- Senaryo 29: authenticated doğrudan sync_operations_receipts SELECT yapamaz
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'select count(*) from public.sync_operations_receipts';
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
    end;
    if v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 29: Authenticated sync_operations_receipts üzerinde doğrudan SELECT yapamadı.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 29 FAIL: Authenticated sync_operations_receipts tablosunu okuyabildi!');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 29: Authenticated sync_operations_receipts tablosunu okuyabildi!');
    end if;

    -- Senaryo 30: Kullanıcı A'nın operation ID replay'i kullanıcı B scope'una veri sızdırmaz
    -- Adım 1: Kullanıcı A işlem yapar
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('receipt_leak_op_1'),
            'CATEGORY',
            'CREATE',
            null,
            jsonb_build_object('id', gen_random_uuid(), 'user_id', v_user_a, 'name', 'A Cat Op', 'type', 'expense', 'color', '#111111')
        );
        reset role;
    exception when others then
        reset role;
    end;

    -- Adım 2: Kullanıcı B aynı operation ID ile çağrı yapar
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_b)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_b::text, true);
    set local role authenticated;
    begin
        v_res_b := public.sync_write_v2(
            md5('receipt_leak_op_1'),
            'CATEGORY',
            'CREATE',
            null,
            jsonb_build_object('id', gen_random_uuid(), 'user_id', v_user_b, 'name', 'B Cat Op', 'type', 'expense', 'color', '#222222')
        );
        reset role;
        if (v_res_b -> 'record' ->> 'user_id') = v_user_a::text then
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 30 FAIL: Kullanıcı A verisi Kullanıcı B operation ID çağrısına sızdı!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 30: Kullanıcı A verisi Kullanıcı B operation ID çağrısına sızdı!');
        else
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 30: Replay çağrısı başka kullanıcının makbuzunu sızdırmadı.');
        end if;
    exception when others then
        reset role;
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 30 ERROR: ' || sqlerrm);
        v_all_results := array_append(v_all_results, '[ERROR] Senaryo 30: ' || sqlerrm);
    end;

    -- Senaryo 31: Aynı operation ID'nin farklı kullanıcılar tarafından izole biçimde kullanılabildiği doğrulanır
    if v_res_b ->> 'status' = 'APPLIED' and (v_res_b -> 'record' ->> 'user_id') = v_user_b::text then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 31: Aynı operation ID farklı kullanıcılar tarafından izole biçimde başarıyla uygulandı.');
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 31 FAIL: Kullanıcı B için izole operation APPLIED olmadı.');
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 31: Kullanıcı B için izole operation APPLIED olmadı.');
    end if;


    -- ========================================================================
    -- G. GRANT METADATA VE AŞIRI GENİŞ YETKİ DENETİMİ
    -- ========================================================================

    -- Senaryo 32: anon ve PUBLIC için tablolarda beklenmeyen mutation grant'i olmadığını doğrula
    v_err_caught := false;
    foreach v_tbl in array v_check_tables loop
        foreach v_role in array array['anon', 'public'] loop
            foreach v_priv in array v_mutation_privs loop
                if has_table_privilege(v_role, 'public.' || v_tbl, v_priv) then
                    v_err_caught := true;
                    v_failures := array_append(v_failures, 'Senaryo 32 FAIL: ' || v_role || ' rolü public.' || v_tbl || ' üzerinde beklenmeyen ' || v_priv || ' yetkisine sahip.');
                end if;
            end loop;
        end loop;
    end loop;
    if not v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 32: anon ve PUBLIC rollerinde beklenmeyen mutation yetkisi yok.');
    else
        v_fail_count := v_fail_count + 1;
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 32: anon veya PUBLIC üzerinde beklenmeyen mutation yetkisi bulundu!');
    end if;

    -- Senaryo 33 & 34: authenticated için her tablo üzerindeki yetkileri raporla ve TRUNCATE/TRIGGER tespit et
    v_err_caught := false;
    foreach v_tbl in array v_check_tables loop
        v_priv_report := '';
        foreach v_priv in array v_all_privs loop
            if has_table_privilege('authenticated', 'public.' || v_tbl, v_priv) then
                v_priv_report := v_priv_report || v_priv || ' ';
                -- Gereksiz geniş grant tespiti: PostgREST istemcisi için TRUNCATE veya TRIGGER yetkisi olmamalı
                if v_priv in ('TRUNCATE', 'TRIGGER') then
                    v_err_caught := true;
                    v_vulnerabilities := array_append(v_vulnerabilities, 'Gereksiz Geniş Yetki: authenticated rolü public.' || v_tbl || ' üzerinde ' || v_priv || ' yetkisine sahip.');
                    v_failures := array_append(v_failures, 'Senaryo 34 FAIL: authenticated public.' || v_tbl || ' tablosunda gereksiz ' || v_priv || ' yetkisine sahip!');
                end if;
            end if;
        end loop;
        v_all_results := array_append(v_all_results, '  * authenticated -> public.' || v_tbl || ': [' || trim(v_priv_report) || '] (tablo yetki envanteri)');
    end loop;

    -- workspaces ve workspace_members için exact grant sözleşmesi doğrulaması (yalnız SELECT)
    foreach v_tbl in array array['workspaces', 'workspace_members'] loop
        if not has_table_privilege('authenticated', 'public.' || v_tbl, 'SELECT') then
            v_err_caught := true;
            v_failures := array_append(v_failures, 'Senaryo 33 FAIL: authenticated public.' || v_tbl || ' üzerinde SELECT yetkisine sahip değil!');
        end if;
        foreach v_priv in array array['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'] loop
            if has_table_privilege('authenticated', 'public.' || v_tbl, v_priv) then
                v_err_caught := true;
                v_vulnerabilities := array_append(v_vulnerabilities, 'Gereksiz Geniş Yetki: authenticated rolü public.' || v_tbl || ' üzerinde ' || v_priv || ' yetkisine sahip.');
                v_failures := array_append(v_failures, 'Senaryo 33 FAIL: authenticated public.' || v_tbl || ' tablosunda beklenmeyen ' || v_priv || ' yetkisine sahip!');
            end if;
        end loop;
    end loop;

    if not v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 33 & 34: Tablo yetki envanteri doğrulandı (19 tablo), gereksiz TRUNCATE/TRIGGER yok ve workspace yetki sözleşmesi sağlandı.');
    else
        v_fail_count := v_fail_count + 1;
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 33 & 34: authenticated üzerinde gereksiz TRUNCATE/TRIGGER veya beklenmeyen workspace yetkisi bulundu!');
    end if;

    -- Senaryo 35: RLS'nin ve trigger güvenlik kontrollerinin açık olduğunu doğrula
    v_err_caught := false;
    foreach v_tbl in array v_check_tables loop
        select relrowsecurity into v_rls_enabled
        from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = v_tbl;

        if v_rls_enabled is not true then
            v_err_caught := true;
            v_failures := array_append(v_failures, 'Senaryo 35 FAIL: public.' || v_tbl || ' üzerinde RLS aktif değil!');
        end if;
    end loop;

    -- Workspace mutation trigger doğrulaması
    if not exists (
        select 1 from information_schema.triggers
        where event_object_schema = 'public' and event_object_table = 'transactions'
          and trigger_name = 'transactions_enforce_workspace_mutation_role_v1'
          and action_timing = 'BEFORE' and action_orientation = 'ROW'
    ) then
        v_err_caught := true;
        v_failures := array_append(v_failures, 'Senaryo 35 FAIL: transactions_enforce_workspace_mutation_role_v1 triggerı eksik!');
    end if;

    if has_function_privilege('authenticated', 'public.enforce_workspace_transaction_mutation_role_v1()', 'EXECUTE') then
        v_err_caught := true;
        v_failures := array_append(v_failures, 'Senaryo 35 FAIL: authenticated rolü trigger fonksiyonunda doğrudan EXECUTE yetkisine sahip!');
    end if;

    if not v_err_caught then
        v_pass_count := v_pass_count + 1;
        v_all_results := array_append(v_all_results, '[PASS] Senaryo 35: Tüm çekirdek tablolarda RLS aktifliği ve trigger güvenlik yapılandırması doğrulandı.');
    else
        v_fail_count := v_fail_count + 1;
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 35: Bazı tablolarda RLS devre dışı veya trigger doğrulaması başarısız!');
    end if;


    -- Senaryo 36: Personal transaction CREATE (sync_write_v2) pozitif regresyonu
    perform set_config('request.jwt.claims', json_build_object('sub', v_user_a)::text, true);
    perform set_config('request.jwt.claim.sub', v_user_a::text, true);
    v_res := null;
    v_sqlstate := null;
    v_errmsg := null;
    set local role authenticated;
    begin
        v_res := public.sync_write_v2(
            md5('personal_tx_create_test'),
            'TRANSACTION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_personal_create_tx_id,
                'user_id', v_user_a,
                'workspace_id', null,
                'category_id', v_cat_a_id,
                'amount_minor', 3500,
                'currency', 'TRY',
                'type', 'expense',
                'payment_method', 'cash',
                'transaction_date', current_date
            )
        );
        reset role;
    exception when others then
        v_sqlstate := SQLSTATE;
        v_errmsg := SQLERRM;
        reset role;
    end;

    if v_sqlstate is not null then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 36 FAIL (Beklenmeyen istisna): SQLSTATE=' || v_sqlstate || ', ' || v_errmsg);
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 36: Kişisel işlem oluşturulurken beklenmeyen istisna (SQLSTATE=' || v_sqlstate || '): ' || v_errmsg);
    elsif v_res ->> 'status' = 'APPLIED' then
        select count(*) into v_cnt
        from public.transactions
        where id = v_personal_create_tx_id
          and user_id = v_user_a
          and workspace_id is null
          and amount_minor = 3500;

        if v_cnt = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 36: Kişisel transaction CREATE pozitif regresyonu doğrulandı (Status=APPLIED, satır user_id=' || v_user_a::text || ' ve workspace_id IS NULL olarak oluştu).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 36 FAIL: sync_write_v2 APPLIED döndü fakat hedef satır veritabanında doğrulanamadı!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 36: Hedef kişisel satır veritabanında bulunamadı.');
        end if;
    else
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 36 FAIL: Kişisel transaction CREATE APPLIED dönmedi: ' || coalesce(v_res::text, 'null'));
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 36: Kişisel işlem APPLIED dönmedi: status=' || coalesce(v_res ->> 'status', 'null'));
    end if;

    -- Senaryo 37: service_role workspace transaction mutasyonu pozitif regresyonu (Trigger bypass)
    execute 'grant usage on schema auth to service_role';
    execute 'grant usage on schema public to service_role';
    execute 'grant select on public.profiles, public.workspaces, public.categories to service_role';
    execute 'grant select, insert, update on public.transactions to service_role';

    perform set_config('request.jwt.claims', json_build_object('role', 'service_role')::text, true);
    perform set_config('request.jwt.claim.role', 'service_role', true);
    perform set_config('request.jwt.claim.sub', '', true);
    v_sqlstate := null;
    v_errmsg := null;
    set local role service_role;
    begin
        insert into public.transactions (
            id, user_id, workspace_id, paid_by_user_id, participant_user_ids,
            amount_minor, currency, type, category_id, description,
            payment_method, transaction_date, created_at, version, deleted_at
        ) values (
            v_service_role_tx_id, v_owner, v_ws1_id, v_owner, jsonb_build_array(v_owner::text),
            12500, 'TRY', 'expense', v_cat_ws1_id, 'Service role test tx',
            'bank_transfer', current_date, now(), 1, null
        );

        update public.transactions
        set amount_minor = 14500
        where id = v_service_role_tx_id;

        reset role;
    exception when others then
        v_sqlstate := SQLSTATE;
        v_errmsg := SQLERRM;
        reset role;
    end;

    if v_sqlstate is not null then
        v_fail_count := v_fail_count + 1;
        v_failures := array_append(v_failures, 'Senaryo 37 FAIL (Beklenmeyen istisna): SQLSTATE=' || v_sqlstate || ', ' || v_errmsg);
        v_all_results := array_append(v_all_results, '[FAIL] Senaryo 37: service_role mutasyonunda beklenmeyen istisna (SQLSTATE=' || v_sqlstate || '): ' || v_errmsg);
    else
        select count(*) into v_cnt
        from public.transactions
        where id = v_service_role_tx_id
          and workspace_id = v_ws1_id
          and amount_minor = 14500;

        if v_cnt = 1 then
            v_pass_count := v_pass_count + 1;
            v_all_results := array_append(v_all_results, '[PASS] Senaryo 37: service_role rolü ile workspace transaction INSERT ve UPDATE mutasyonu trigger tarafından engellenmeden başarıyla tamamlandı (amount=14500 doğrulandı).');
        else
            v_fail_count := v_fail_count + 1;
            v_failures := array_append(v_failures, 'Senaryo 37 FAIL: service_role satırı veritabanında doğrulanamadı!');
            v_all_results := array_append(v_all_results, '[FAIL] Senaryo 37: service_role işlemi sonrasında satır doğrulanamadı.');
        end if;
    end if;


    -- ========================================================================
    -- SONUÇ VE GÜVENLİK BİLDİRİMİ
    -- ========================================================================
    v_summary := E'\n' ||
        '======================================================================\n' ||
        '>>> RLS GÜVENLİK SÖZLEŞMESİ DENETİM RAPORU:\n' ||
        '    Toplam Senaryo: 37 | Başarılı: ' || v_pass_count || ' | Başarısız: ' || v_fail_count || E'\n' ||
        '----------------------------------------------------------------------\n' ||
        'DETAYLI SONUÇLAR:\n' ||
        array_to_string(v_all_results, E'\n') || E'\n' ||
        '----------------------------------------------------------------------\n' ||
        'TESPİT EDİLEN GÜVENLİK AÇIKLARI / BLOKAJ:\n' ||
        case when array_length(v_vulnerabilities, 1) > 0 then array_to_string(v_vulnerabilities, E'\n') else 'Yok' end || E'\n' ||
        '======================================================================\n';

    if v_fail_count > 0 then
        raise exception '%', v_summary;
    else
        raise notice '%', v_summary;
    end if;

end $$;

rollback;
