begin;

insert into auth.users(id, email)
values (extensions.gen_random_uuid(), 'market-rate-contract@example.invalid');

do $$
declare
    v_user_id uuid;
    v_allowed boolean;
    v_count integer;
begin
    select id into v_user_id from auth.users where email = 'market-rate-contract@example.invalid';
    perform set_config('request.jwt.claim.sub', v_user_id::text, true);
    set local role authenticated;
    for v_count in 1..10 loop
        select public.claim_market_price_request() into v_allowed;
        if not v_allowed then raise exception 'Kota % numaralı geçerli isteği reddetti.', v_count; end if;
    end loop;
    select public.claim_market_price_request() into v_allowed;
    if v_allowed then raise exception 'Dakikalık 11. piyasa fiyatı isteği reddedilmedi.'; end if;
    reset role;

    if has_table_privilege('authenticated', 'public.market_price_rate_limits', 'SELECT')
       or has_table_privilege('authenticated', 'public.market_price_rate_limits', 'INSERT')
       or has_table_privilege('authenticated', 'public.market_price_rate_limits', 'UPDATE') then
        raise exception 'Authenticated rolü kota tablosuna doğrudan erişebiliyor.';
    end if;
    if has_function_privilege('anon', 'public.claim_market_price_request()', 'EXECUTE') then
        raise exception 'Anon rolü kota fonksiyonunu çağırabiliyor.';
    end if;
    raise notice 'Market price kullanıcı kotası doğrulandı; ROLLBACK uygulanıyor.';
end
$$;

rollback;
