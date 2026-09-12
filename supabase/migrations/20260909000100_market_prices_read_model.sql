-- Genel piyasa fiyatı read model'i. Yazma yalnız güvenilir backend/service-role içindir.
begin;

create table public.market_prices (
    asset_type text not null,
    symbol text not null,
    quote_currency text not null,
    price_unscaled bigint not null,
    price_scale integer not null,
    observed_at timestamptz not null,
    fetched_at timestamptz not null default timezone('utc'::text, now()),
    expires_at timestamptz not null,
    source text not null,
    constraint market_prices_primary_key primary key (asset_type, symbol, quote_currency),
    constraint market_prices_asset_type_check
        check (asset_type in ('CRYPTO', 'STOCKS', 'PRECIOUS_METALS')),
    constraint market_prices_symbol_check
        check (symbol = upper(symbol) and symbol ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    constraint market_prices_currency_check
        check (quote_currency in ('TRY', 'USD', 'EUR')),
    constraint market_prices_value_check
        check (price_unscaled > 0 and price_scale between 0 and 12),
    constraint market_prices_source_check
        check (length(trim(source)) between 1 and 64),
    constraint market_prices_expiry_check
        check (expires_at > fetched_at)
);

create index idx_market_prices_expiry
    on public.market_prices (expires_at, asset_type, symbol, quote_currency);

alter table public.market_prices enable row level security;
revoke all on table public.market_prices from public, anon, authenticated, service_role;
grant select on table public.market_prices to authenticated;
grant select, insert, update on table public.market_prices to service_role;

create policy market_prices_authenticated_select_v1
    on public.market_prices
    for select
    to authenticated
    using (true);

commit;
