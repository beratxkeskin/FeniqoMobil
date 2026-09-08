-- =============================================================================
-- TRANSFER_WORKSPACE_OWNERSHIP_V1
-- Güvenli, tek-transaction ve atomik çalışma alanı sahiplik devri RPC'si.
-- =============================================================================

begin;

create or replace function public.transfer_workspace_ownership_v1(
    p_workspace_id uuid,
    p_target_user_id uuid,
    p_expected_workspace_version bigint,
    p_expected_current_owner_member_version bigint,
    p_expected_target_member_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare
    actor_id uuid := auth.uid();
    v_ws_record record;
    v_actor_member_record record;
    v_target_member_record record;
    v_now timestamptz;
begin
    -- 1. Kimlik doğrulama
    if actor_id is null then
        raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.';
    end if;

    -- 2. Girdi kontrolleri
    if p_workspace_id is null or p_target_user_id is null or
       p_expected_workspace_version is null or p_expected_workspace_version <= 0 or
       p_expected_current_owner_member_version is null or p_expected_current_owner_member_version <= 0 or
       p_expected_target_member_version is null or p_expected_target_member_version <= 0 then
        raise exception using message = 'Geçersiz parametreler.';
    end if;

    -- Kendine transfer engeli
    if actor_id = p_target_user_id then
        raise exception using message = 'cannot_change_own_role';
    end if;

    -- 3. Deterministik sıra ile satır kilitleme (Deadlock önleme)
    -- Önce workspace satırını kilitle
    select * into v_ws_record
      from public.workspaces
     where id = p_workspace_id
       and deleted_at is null
       for update;

    if not found then
        raise exception using message = 'workspace_not_found';
    end if;

    -- Sonra iki üye satırını user_id'ye göre artan sırada kilitle
    perform 1 from public.workspace_members
     where workspace_id = p_workspace_id
       and user_id in (actor_id, p_target_user_id)
     order by user_id asc
     for update;

    -- 4. Actor üyelik ve yetki kontrolleri
    select * into v_actor_member_record
      from public.workspace_members
     where workspace_id = p_workspace_id
       and user_id = actor_id
       and deleted_at is null;

    if not found or v_actor_member_record.role_code <> 'OWNER' or v_ws_record.owner_id <> actor_id then
        raise exception using message = 'ownership_transfer_actor_not_owner';
    end if;

    -- 5. Hedef üye kontrolleri
    select * into v_target_member_record
      from public.workspace_members
     where workspace_id = p_workspace_id
       and user_id = p_target_user_id
       and deleted_at is null;

    if not found then
        raise exception using message = 'ownership_transfer_target_not_member';
    end if;

    if v_target_member_record.role_code = 'OWNER' then
        raise exception using message = 'ownership_transfer_target_already_owner';
    end if;

    -- 6. Optimistic concurrency (Beklenen versiyon) doğrulamaları
    if v_ws_record.version <> p_expected_workspace_version
       or v_actor_member_record.version <> p_expected_current_owner_member_version
       or v_target_member_record.version <> p_expected_target_member_version then
        raise exception using message = 'ownership_transfer_version_conflict';
    end if;

    -- 7. Atomik güncellemeler (Sunucu saati ve versiyon artırımı)
    v_now := pg_catalog.timezone('utc'::text, pg_catalog.now());

    update public.workspaces
       set owner_id = p_target_user_id,
           updated_at = v_now,
           version = version + 1
     where id = p_workspace_id
    returning * into v_ws_record;

    update public.workspace_members
       set role_code = 'EDITOR',
           updated_at = v_now,
           version = version + 1
     where workspace_id = p_workspace_id
       and user_id = actor_id
    returning * into v_actor_member_record;

    update public.workspace_members
       set role_code = 'OWNER',
           updated_at = v_now,
           version = version + 1
     where workspace_id = p_workspace_id
       and user_id = p_target_user_id
    returning * into v_target_member_record;

    -- 8. Güvenli jsonb dönüş
    return pg_catalog.jsonb_build_object(
        'workspace', pg_catalog.to_jsonb(v_ws_record),
        'actor_member', pg_catalog.to_jsonb(v_actor_member_record),
        'target_member', pg_catalog.to_jsonb(v_target_member_record)
    );
end;
$$;

revoke execute on function public.transfer_workspace_ownership_v1(uuid, uuid, bigint, bigint, bigint) from public;
revoke execute on function public.transfer_workspace_ownership_v1(uuid, uuid, bigint, bigint, bigint) from anon;
grant execute on function public.transfer_workspace_ownership_v1(uuid, uuid, bigint, bigint, bigint) to authenticated;

commit;
