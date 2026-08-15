-- TASLAK: Eski web kolonlarını kaldırmadan yeni mobil para sözleşmesini genişletir.
begin;

alter table public.transactions
    add column amount_minor bigint,
    add column currency text;

alter table public.transactions
    add constraint transactions_amount_minor_positive
        check (amount_minor is null or amount_minor > 0) not valid,
    add constraint transactions_currency_supported
        check (currency is null or currency in ('TRY', 'USD', 'EUR')) not valid;

commit;
