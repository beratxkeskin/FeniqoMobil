begin;

do $$
declare
    v_owner_id uuid;
    v_other_user_id uuid;
    v_asset_id uuid := extensions.gen_random_uuid();
    v_result jsonb;
    v_error_caught boolean := false;
    v_visible_count integer;
begin
    select id into v_owner_id from auth.users where email = 'asset-local-1@example.invalid';
    select id into v_other_user_id from auth.users where email = 'asset-local-2@example.invalid';
    if v_owner_id is null or v_other_user_id is null then
        raise exception 'Asset contract kullanıcı fixture kayıtları bulunamadı.';
    end if;

    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'CREATE', null,
        jsonb_build_object(
            'id', v_asset_id, 'user_id', v_owner_id, 'name', 'Altın',
            'type', 'PRECIOUS_METALS', 'current_value_minor', 100000,
            'currency', 'TRY', 'quantity_unscaled', 10, 'quantity_scale', 1,
            'purchase_unit_price_minor', 90000, 'tracking_symbol', 'XAU',
            'auto_track', true, 'created_at', timezone('utc', now())
        )
    );
    if v_result ->> 'status' <> 'APPLIED' or (v_result -> 'record' ->> 'version')::bigint <> 1 then
        raise exception 'Asset CREATE başarısız: %', v_result;
    end if;

    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'UPDATE', 1,
        jsonb_build_object(
            'id', v_asset_id, 'user_id', v_owner_id, 'name', 'Altın Güncel',
            'type', 'PRECIOUS_METALS', 'current_value_minor', 110000,
            'currency', 'TRY', 'quantity_unscaled', 10, 'quantity_scale', 1,
            'purchase_unit_price_minor', 90000, 'tracking_symbol', 'XAU',
            'auto_track', true
        )
    );
    if v_result ->> 'status' <> 'APPLIED' or (v_result -> 'record' ->> 'version')::bigint <> 2 then
        raise exception 'Asset UPDATE başarısız: %', v_result;
    end if;

    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'UPDATE', 1,
        jsonb_build_object(
            'id', v_asset_id, 'user_id', v_owner_id, 'name', 'Eski',
            'type', 'PRECIOUS_METALS', 'current_value_minor', 1,
            'currency', 'TRY', 'auto_track', false
        )
    );
    if v_result ->> 'status' <> 'CONFLICT' then
        raise exception 'Stale Asset sürümü CONFLICT dönmedi: %', v_result;
    end if;

    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'CREATE', null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(), 'user_id', v_other_user_id,
                'name', 'Yetkisiz', 'type', 'OTHER', 'current_value_minor', 1,
                'currency', 'TRY', 'auto_track', false
            )
        );
    exception when insufficient_privilege then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Asset owner mutation izolasyonu başarısız.';
    end if;

    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'CREATE', null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(), 'user_id', v_owner_id,
                'name', 'Takip', 'type', 'STOCKS', 'current_value_minor', 1,
                'currency', 'TRY', 'auto_track', true
            )
        );
    exception when check_violation then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Asset auto_track sembol doğrulaması başarısız.';
    end if;

    v_error_caught := false;
    begin
        perform public.sync_write_v2(
            replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'CREATE', null,
            jsonb_build_object(
                'id', extensions.gen_random_uuid(), 'user_id', v_owner_id,
                'name', 'Geçersiz miktar', 'type', 'OTHER', 'current_value_minor', 1,
                'currency', 'TRY', 'quantity_unscaled', 1, 'quantity_scale', 13,
                'auto_track', false
            )
        );
    exception when check_violation then
        v_error_caught := true;
    end;
    if not v_error_caught then
        raise exception 'Asset quantity_scale doğrulaması başarısız.';
    end if;

    perform set_config('request.jwt.claim.sub', v_other_user_id::text, true);
    set local role authenticated;
    select count(*) into v_visible_count from public.assets where id = v_asset_id;
    reset role;
    if v_visible_count <> 0 then
        raise exception 'Asset owner RLS izolasyonu başarısız.';
    end if;

    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);
    v_result := public.sync_write_v2(
        replace(extensions.gen_random_uuid()::text, '-', ''), 'ASSET', 'DELETE', 2,
        jsonb_build_object('id', v_asset_id)
    );
    if v_result ->> 'status' <> 'APPLIED' or v_result -> 'record' ->> 'deleted_at' is null then
        raise exception 'Asset DELETE başarısız: %', v_result;
    end if;

    raise notice 'Asset sync_write_v2 contract senaryoları doğrulandı; ROLLBACK uygulanıyor.';
end
$$;

rollback;
