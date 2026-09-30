-- ============================================================================
-- SQL SÖZLEŞME TESTİ: workspace_invitation_security_contract.sql
-- Kapsam: public.workspace_invitations tablosu token_hash görünürlük hardening'i,
-- column-level SELECT izinleri, RLS üyelik koruması ve authenticated rolü altında
-- SECURITY DEFINER RPC (sync_write_v2, redeem_workspace_invitation_v1) yürütülmesi.
-- Ortam: Yalnız disposable yerel PostgreSQL. İşlem sonunda ROLLBACK yapılır.
-- ============================================================================

begin;

do $$
declare
    v_ws_id uuid := 'c0000000-0000-4000-8000-000000000001';
    v_other_ws_id uuid := 'c0000000-0000-4000-8000-000000000002';
    v_owner_id uuid := 'b0000000-0000-4000-8000-000000000001';
    v_member_id uuid := 'b0000000-0000-4000-8000-000000000002';
    v_other_id uuid := 'b0000000-0000-4000-8000-000000000003';
    v_redeemer_id uuid := 'b0000000-0000-4000-8000-000000000004';
    v_inv_id uuid := 'd0000000-0000-4000-8000-000000000001';
    v_raw_token text := 'feniqo_test_raw_token_xyz_1234567890abcdef';
    v_token_hash text;
    v_result jsonb;
    v_redeem_result jsonb;
    v_err_caught boolean;
    v_visible_count int;
    v_table_privs text[] := array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'];
    v_roles text[] := array['authenticated', 'anon', 'public'];
    v_role text;
    v_priv text;
    v_safe_cols text[] := array[
        'id',
        'workspace_id',
        'inviter_id',
        'role_code',
        'created_at',
        'expires_at',
        'max_uses',
        'uses_count',
        'updated_at',
        'deleted_at',
        'version'
    ];
    v_col text;
    v_policy_rec record;
    v_rls_enabled boolean;
    v_canonical_qual text;
    v_negative_qual text;
    v_negative_canonical text;
begin
    -- Token hash hesapla (sha256 hex: 64 karakter)
    v_token_hash := pg_catalog.encode(extensions.digest(v_raw_token, 'sha256'), 'hex');

    -- Fixture 0: auth.users kullanıcıları
    insert into auth.users (id, email) values
        (v_owner_id, 'owner@feniqo.test'),
        (v_member_id, 'member@feniqo.test'),
        (v_other_id, 'other@feniqo.test'),
        (v_redeemer_id, 'redeemer@feniqo.test')
    on conflict (id) do nothing;

    -- Fixture 1: Ana workspace ve OWNER üyeliği
    insert into public.workspaces (id, name, normalized_name, owner_id, type_code, currency_code, created_at, updated_at, version)
    values (v_ws_id, 'Test Workspace', 'test workspace', v_owner_id, 'shared', 'TRY', timezone('utc', now()), timezone('utc', now()), 1);

    insert into public.workspace_members (workspace_id, user_id, role_code, joined_at, updated_at, version)
    values (v_ws_id, v_owner_id, 'OWNER', timezone('utc', now()), timezone('utc', now()), 1);

    -- Fixture 2: Workspace üyesi ekle (EDITOR)
    insert into public.workspace_members (workspace_id, user_id, role_code, joined_at, updated_at, version)
    values (v_ws_id, v_member_id, 'EDITOR', timezone('utc', now()), timezone('utc', now()), 1);

    -- Fixture 3: İkinci kullanıcıya ait bağımsız workspace
    insert into public.workspaces (id, name, normalized_name, owner_id, type_code, currency_code, created_at, updated_at, version)
    values (v_other_ws_id, 'Other Workspace', 'other workspace', v_other_id, 'shared', 'TRY', timezone('utc', now()), timezone('utc', now()), 1);

    insert into public.workspace_members (workspace_id, user_id, role_code, joined_at, updated_at, version)
    values (v_other_ws_id, v_other_id, 'OWNER', timezone('utc', now()), timezone('utc', now()), 1);

    -- ========================================================================
    -- SENARYO A: SET LOCAL ROLE authenticated ALTINDA sync_write_v2 CREATE
    --            (Davet oluşturulur ve token_hash kesinlikle döndürülmez)
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);

    set local role authenticated;
    begin
        v_result := public.sync_write_v2(
            md5('inv_op_create_001'),
            'WORKSPACE_INVITATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_inv_id,
                'workspace_id', v_ws_id,
                'role_code', 'EDITOR',
                'token_hash', v_token_hash,
                'expires_at', timezone('utc', now() + interval '7 days'),
                'max_uses', 5
            )
        );
        reset role;
    exception when others then
        reset role;
        raise;
    end;

    if v_result ->> 'status' <> 'APPLIED' then
        raise exception 'Senaryo A FAIL: authenticated altında sync_write_v2 CREATE başarısız: %', v_result;
    end if;

    if (v_result -> 'record') ? 'token_hash' then
        raise exception 'Senaryo A FAIL: sync_write_v2 CREATE dönüşü token_hash içeriyor: %', v_result -> 'record';
    end if;

    -- ========================================================================
    -- SENARYO B: SET LOCAL ROLE authenticated ALTINDA sync_write_v2 CONFLICT
    --            (Conflict current_record dönüşü token_hash kesinlikle içermez)
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);

    set local role authenticated;
    begin
        v_result := public.sync_write_v2(
            md5('inv_op_conflict_001'),
            'WORKSPACE_INVITATION',
            'CREATE',
            null,
            jsonb_build_object(
                'id', v_inv_id,
                'workspace_id', v_ws_id,
                'role_code', 'VIEWER',
                'token_hash', v_token_hash,
                'expires_at', timezone('utc', now() + interval '7 days'),
                'max_uses', 1
            )
        );
        reset role;
    exception when others then
        reset role;
        raise;
    end;

    if v_result ->> 'status' <> 'CONFLICT' then
        raise exception 'Senaryo B FAIL: Aynı davet ID için CONFLICT dönmedi: %', v_result;
    end if;

    if (v_result -> 'current_record') ? 'token_hash' then
        raise exception 'Senaryo B FAIL: sync_write_v2 CONFLICT current_record dönüşü token_hash içeriyor: %', v_result -> 'current_record';
    end if;

    -- ========================================================================
    -- SENARYO 1: authenticated workspace üyesi açık güvenli kolon listesini okuyabilir
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_member_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_member_id::text, true);

    set local role authenticated;
    begin
        select count(*) into v_visible_count
        from (
            select id, workspace_id, inviter_id, role_code, created_at, expires_at,
                   max_uses, uses_count, updated_at, deleted_at, version
            from public.workspace_invitations
            where id = v_inv_id
        ) t;
        reset role;
    exception when others then
        reset role;
        raise;
    end;

    if v_visible_count <> 1 then
        raise exception 'Senaryo 1 FAIL: Workspace üyesi güvenli kolonları okuyamadı (count=%).', v_visible_count;
    end if;

    -- ========================================================================
    -- SENARYO 2: authenticated workspace üyesinin token_hash SELECT denemesi
    --            insufficient_privilege ile reddedilir
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_member_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_member_id::text, true);
    v_err_caught := false;

    set local role authenticated;
    begin
        execute 'select id, token_hash from public.workspace_invitations where id = ' || quote_literal(v_inv_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;

    if not v_err_caught then
        raise exception 'Senaryo 2 FAIL: token_hash sorgusu insufficient_privilege ile engellenmedi!';
    end if;

    -- ========================================================================
    -- SENARYO 3: authenticated workspace üyesinin SELECT * denemesi
    --            insufficient_privilege ile reddedilir (wildcard token_hash içerir)
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_member_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_member_id::text, true);
    v_err_caught := false;

    set local role authenticated;
    begin
        execute 'select * from public.workspace_invitations where id = ' || quote_literal(v_inv_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;

    if not v_err_caught then
        raise exception 'Senaryo 3 FAIL: SELECT * sorgusu insufficient_privilege ile engellenmedi!';
    end if;

    -- ========================================================================
    -- SENARYO 4: Başka workspace kullanıcısı güvenli invitation metadata satırını göremez (RLS)
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_other_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_other_id::text, true);

    set local role authenticated;
    begin
        select count(*) into v_visible_count
        from (
            select id, workspace_id, role_code, expires_at
            from public.workspace_invitations
            where id = v_inv_id
        ) t;
        reset role;
    exception when others then
        reset role;
        raise;
    end;

    if v_visible_count <> 0 then
        raise exception 'Senaryo 4 FAIL: Başka workspace kullanıcısı daveti gördü (count=%).', v_visible_count;
    end if;

    -- ========================================================================
    -- SENARYO 5: anon güvenli invitation metadata’sını okuyamaz
    -- ========================================================================
    v_err_caught := false;

    set local role anon;
    begin
        execute 'select id, workspace_id from public.workspace_invitations';
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;

    if not v_err_caught then
        raise exception 'Senaryo 5 FAIL: anon rolü insufficient_privilege almadı!';
    end if;

    -- ========================================================================
    -- SENARYO 6: authenticated doğrudan INSERT/UPDATE/DELETE yapamaz
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);

    -- INSERT denemesi
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'insert into public.workspace_invitations (workspace_id, inviter_id, role_code, token_hash, expires_at) ' ||
                'values (' || quote_literal(v_ws_id) || ', ' || quote_literal(v_owner_id) || ', ''EDITOR'', ''0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'', now())';
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;
    if not v_err_caught then
        raise exception 'Senaryo 6 FAIL: authenticated doğrudan INSERT yapabildi!';
    end if;

    -- UPDATE denemesi
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'update public.workspace_invitations set max_uses = 10 where id = ' || quote_literal(v_inv_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;
    if not v_err_caught then
        raise exception 'Senaryo 6 FAIL: authenticated doğrudan UPDATE yapabildi!';
    end if;

    -- DELETE denemesi
    v_err_caught := false;
    set local role authenticated;
    begin
        execute 'delete from public.workspace_invitations where id = ' || quote_literal(v_inv_id);
        reset role;
    exception when insufficient_privilege then
        v_err_caught := true;
        reset role;
    when others then
        reset role;
        raise;
    end;
    if not v_err_caught then
        raise exception 'Senaryo 6 FAIL: authenticated doğrudan DELETE yapabildi!';
    end if;

    -- ========================================================================
    -- SENARYO 7: SET LOCAL ROLE authenticated ALTINDA redeem_workspace_invitation_v1
    --            (SECURITY DEFINER RPC authenticated rolünde başarıyla çalışır)
    -- ========================================================================
    perform set_config('request.jwt.claims', json_build_object('sub', v_redeemer_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_redeemer_id::text, true);

    set local role authenticated;
    begin
        v_redeem_result := public.redeem_workspace_invitation_v1(v_raw_token);
        reset role;
    exception when others then
        reset role;
        raise;
    end;

    if v_redeem_result is null or not (v_redeem_result ? 'workspace') or not (v_redeem_result ? 'member') then
        raise exception 'Senaryo 7 FAIL: authenticated altında redeem_workspace_invitation_v1 dönüşü geçersiz: %', v_redeem_result;
    end if;

    -- Redeemer üye oldu mu doğrula
    perform set_config('request.jwt.claims', json_build_object('sub', v_redeemer_id)::text, true);
    perform set_config('request.jwt.claim.sub', v_redeemer_id::text, true);
    if not public.is_workspace_member(v_ws_id) then
        raise exception 'Senaryo 7 FAIL: Daveti kabul eden kullanıcı çalışma alanı üyesi olamadı.';
    end if;

    -- ========================================================================
    -- SENARYO 8: Tablo düzeyi metadata yetkileri doğrulaması
    --            authenticated, anon ve public için 7 yetki (SELECT, INSERT, UPDATE,
    --            DELETE, TRUNCATE, REFERENCES, TRIGGER) kesinlikle FALSE olmalı.
    --            (NOT: TRUNCATE davranışsal çalıştırılmaz; metadata ile denetlenir)
    -- ========================================================================
    foreach v_role in array v_roles loop
        foreach v_priv in array v_table_privs loop
            if has_table_privilege(v_role, 'public.workspace_invitations', v_priv) then
                raise exception 'Senaryo 8 FAIL: % rolü public.workspace_invitations üzerinde beklenmeyen % yetkisine sahip.', v_role, v_priv;
            end if;
        end loop;
    end loop;

    -- ========================================================================
    -- SENARYO 9: has_column_privilege(authenticated, ..., 'token_hash', 'SELECT') FALSE olmalı
    -- ========================================================================
    if has_column_privilege('authenticated', 'public.workspace_invitations', 'token_hash', 'SELECT') then
        raise exception 'Senaryo 9 FAIL: authenticated rolü token_hash kolonunda SELECT yetkisine sahip.';
    end if;

    -- ========================================================================
    -- SENARYO 10: 11 güvenli allowlist kolonunun her birinde has_column_privilege TRUE olmalı
    -- ========================================================================
    foreach v_col in array v_safe_cols loop
        if not has_column_privilege('authenticated', 'public.workspace_invitations', v_col, 'SELECT') then
            raise exception 'Senaryo 10 FAIL: Güvenli kolonda (%) authenticated SELECT yetkisi eksik.', v_col;
        end if;
    end loop;

    -- ========================================================================
    -- SENARYO 11: RLS ve workspace_invitations_member_select tam policy sözleşmesi
    -- ========================================================================
    select relrowsecurity into v_rls_enabled
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'workspace_invitations';

    if v_rls_enabled is not true then
        raise exception 'Senaryo 11 FAIL: workspace_invitations RLS aktif değil.';
    end if;

    select
        schemaname,
        tablename,
        policyname,
        permissive,
        roles,
        cmd,
        qual,
        with_check
    into v_policy_rec
    from pg_catalog.pg_policies
    where schemaname = 'public'
      and tablename = 'workspace_invitations'
      and policyname = 'workspace_invitations_member_select';

    if v_policy_rec.policyname is null then
        raise exception 'Senaryo 11 FAIL: workspace_invitations_member_select politikası bulunamadı.';
    end if;

    if v_policy_rec.permissive <> 'PERMISSIVE' then
        raise exception 'Senaryo 11 FAIL: policy permissive değeri PERMISSIVE değil: %', v_policy_rec.permissive;
    end if;

    if v_policy_rec.cmd <> 'SELECT' then
        raise exception 'Senaryo 11 FAIL: policy cmd değeri SELECT değil: %', v_policy_rec.cmd;
    end if;

    if v_policy_rec.roles <> '{authenticated}'::name[] and v_policy_rec.roles <> array['authenticated']::name[] then
        raise exception 'Senaryo 11 FAIL: policy roller yalnızca {authenticated} olmalı: %', v_policy_rec.roles;
    end if;

    if v_policy_rec.with_check is not null then
        raise exception 'Senaryo 11 FAIL: policy with_check null olmalı: %', v_policy_rec.with_check;
    end if;

    -- Gerçek policy qual canonical doğrulama (tam eşitlik)
    v_canonical_qual := regexp_replace(v_policy_rec.qual, '\s+', '', 'g');
    v_canonical_qual := replace(v_canonical_qual, 'public.', '');
    if v_canonical_qual ~ '^\(.*\)$' then
        v_canonical_qual := regexp_replace(v_canonical_qual, '^\((.*)\)$', '\1');
    end if;

    if v_canonical_qual <> 'is_workspace_member(workspace_id)' then
        raise exception 'Senaryo 11 FAIL: Policy qual tam canonical eşleşmedi: beklenen "is_workspace_member(workspace_id)", alınan "%" (ham: "%")',
            v_canonical_qual, v_policy_rec.qual;
    end if;

    -- Negatif test: Genişletilmiş yetki aşımı içeren ifadenin (ör. OR auth.uid() IS NOT NULL)
    -- canonical doğrulama mantığı tarafından kesinlikle reddedildiğinin kanıtı
    v_negative_qual := 'is_workspace_member(workspace_id) OR auth.uid() IS NOT NULL';
    v_negative_canonical := regexp_replace(v_negative_qual, '\s+', '', 'g');
    v_negative_canonical := replace(v_negative_canonical, 'public.', '');
    if v_negative_canonical ~ '^\(.*\)$' then
        v_negative_canonical := regexp_replace(v_negative_canonical, '^\((.*)\)$', '\1');
    end if;

    if v_negative_canonical = 'is_workspace_member(workspace_id)' then
        raise exception 'Senaryo 11 Negatif Test FAIL: Genişletilmiş güvensiz predicate ("%") canonical kontrolden geçti!', v_negative_qual;
    end if;

    raise notice 'Tüm workspace invitation security sözleşme senaryoları BAŞARIYLA DOĞRULANDI.';
end
$$;

rollback;
