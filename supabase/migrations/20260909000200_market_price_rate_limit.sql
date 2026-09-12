-- Edge Function piyasa fiyatı istekleri için kullanıcı bazlı atomik kota.
begin;

create table public.market_price_rate_limits (
    user_id uuid primary key references auth.users(id) on delete cascade,
    window_started_at timestamptz not null,
    request_count integer not null,
    constraint market_price_rate_limits_count_check check (request_count between 1 and 10)
);

alter table public.market_price_rate_limits enable row level security;
revoke all on table public.market_price_rate_limits from public, anon, authenticated, service_role;

create function public.claim_market_price_request()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    actor_id uuid := auth.uid();
    v_now timestamptz := pg_catalog.now();
    current_row public.market_price_rate_limits%rowtype;
begin
    if actor_id is null then
        raise insufficient_privilege using message = 'Oturum açmış kullanıcı gerekli.';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(actor_id::text), 1297372756);
    select * into current_row
      from public.market_price_rate_limits
     where user_id = actor_id
     for update;

    if not found or current_row.window_started_at <= v_now - interval '1 minute' then
        insert into public.market_price_rate_limits(user_id, window_started_at, request_count)
        values(actor_id, v_now, 1)
        on conflict(user_id) do update
            set window_started_at = excluded.window_started_at,
                request_count = 1;
        return true;
    end if;
    if current_row.request_count >= 10 then return false; end if;
    update public.market_price_rate_limits
       set request_count = request_count + 1
     where user_id = actor_id;
    return true;
end
$$;

revoke execute on function public.claim_market_price_request() from public, anon, service_role;
grant execute on function public.claim_market_price_request() to authenticated;

commit;
