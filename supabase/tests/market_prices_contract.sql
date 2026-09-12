begin;

insert into public.market_prices (
    asset_type, symbol, quote_currency, price_unscaled, price_scale,
    observed_at, fetched_at, expires_at, source
) values (
    'CRYPTO', 'BTC', 'TRY', 286543210, 2,
    timezone('utc'::text, now()), timezone('utc'::text, now()),
    timezone('utc'::text, now()) + interval '5 minutes', 'contract-provider'
);

do $$
declare
    v_count integer;
    v_rejected boolean := false;
begin
    set local role authenticated;
    select count(*) into v_count
      from public.market_prices
     where asset_type = 'CRYPTO' and symbol = 'BTC' and quote_currency = 'TRY';
    if v_count <> 1 then
        raise exception 'Authenticated market price SELECT başarısız.';
    end if;

    begin
        insert into public.market_prices (
            asset_type, symbol, quote_currency, price_unscaled, price_scale,
            observed_at, fetched_at, expires_at, source
        ) values (
            'STOCKS', 'AAPL', 'USD', 20000, 2,
            now(), now(), now() + interval '15 minutes', 'forged-client'
        );
    exception when insufficient_privilege then
        v_rejected := true;
    end;
    if not v_rejected then
        raise exception 'Authenticated istemci market_prices INSERT yapabildi.';
    end if;
    reset role;

    if has_table_privilege('anon', 'public.market_prices', 'SELECT') then
        raise exception 'Anon rolünde market_prices SELECT yetkisi bulunmamalıdır.';
    end if;
    if has_table_privilege('authenticated', 'public.market_prices', 'INSERT')
       or has_table_privilege('authenticated', 'public.market_prices', 'UPDATE')
       or has_table_privilege('authenticated', 'public.market_prices', 'DELETE') then
        raise exception 'Authenticated rolü market_prices mutation yetkisi aldı.';
    end if;
    if not has_table_privilege('service_role', 'public.market_prices', 'SELECT,INSERT,UPDATE') then
        raise exception 'Service role gerekli market_prices yetkilerine sahip değil.';
    end if;

    v_rejected := false;
    begin
        insert into public.market_prices (
            asset_type, symbol, quote_currency, price_unscaled, price_scale,
            observed_at, fetched_at, expires_at, source
        ) values (
            'CRYPTO', 'btc/url', 'TRY', 1, 2,
            now(), now(), now() + interval '5 minutes', 'contract-provider'
        );
    exception when check_violation then
        v_rejected := true;
    end;
    if not v_rejected then raise exception 'Güvensiz sembol reddedilmedi.'; end if;

    v_rejected := false;
    begin
        insert into public.market_prices (
            asset_type, symbol, quote_currency, price_unscaled, price_scale,
            observed_at, fetched_at, expires_at, source
        ) values (
            'REAL_ESTATE', 'HOME', 'TRY', 1, 2,
            now(), now(), now() + interval '5 minutes', 'contract-provider'
        );
    exception when check_violation then
        v_rejected := true;
    end;
    if not v_rejected then raise exception 'Desteklenmeyen asset türü reddedilmedi.'; end if;

    v_rejected := false;
    begin
        insert into public.market_prices (
            asset_type, symbol, quote_currency, price_unscaled, price_scale,
            observed_at, fetched_at, expires_at, source
        ) values (
            'PRECIOUS_METALS', 'XAU', 'EUR', 1, 13,
            now(), now(), now() + interval '5 minutes', 'contract-provider'
        );
    exception when check_violation then
        v_rejected := true;
    end;
    if not v_rejected then raise exception 'Geçersiz fiyat ölçeği reddedilmedi.'; end if;

    set local role service_role;
    update public.market_prices
       set price_unscaled = 286600000,
           fetched_at = timezone('utc'::text, now()),
           expires_at = timezone('utc'::text, now()) + interval '5 minutes'
     where asset_type = 'CRYPTO' and symbol = 'BTC' and quote_currency = 'TRY';
    if not found then raise exception 'Service role market price UPDATE başarısız.'; end if;
    reset role;

    raise notice 'Market price tablo, constraint ve rol sözleşmeleri doğrulandı; ROLLBACK uygulanıyor.';
end
$$;

rollback;
