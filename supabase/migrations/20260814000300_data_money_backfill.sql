-- TASLAK: Çalıştırmadan önce yedek alınmalı ve staging kontrol toplamları onaylanmalıdır.
begin;

do $$
begin
    if exists (
        select 1
        from public.transactions
        where amount <= 0
           or amount <> round(amount, 2)
           or amount > 92233720368547758.07
    ) then
        raise exception 'Para backfill durduruldu: pozitif olmayan, ikiden fazla ondalıklı veya BIGINT sınırını aşan amount bulundu.';
    end if;

    if exists (
        select 1
        from public.transactions as t
        join public.profiles as p on p.id = t.user_id
        where p.currency not in ('TRY', 'USD', 'EUR')
    ) then
        raise exception 'Para backfill durduruldu: desteklenmeyen profil para birimi bulundu.';
    end if;
end
$$;

update public.transactions as t
set amount_minor = round(t.amount * 100)::bigint,
    currency = p.currency
from public.profiles as p
where p.id = t.user_id
  and (t.amount_minor is null or t.currency is null);

do $$
declare
    check_row record;
begin
    if exists (
        select 1
        from public.transactions
        where amount_minor is null or currency is null
    ) then
        raise exception 'Para backfill eksik kaldı; NULL hedef alan bulundu.';
    end if;

    for check_row in
        select
            currency,
            round(sum(amount) * 100)::bigint as source_minor_total,
            sum(amount_minor)::bigint as target_minor_total
        from public.transactions
        group by currency
    loop
        if check_row.source_minor_total <> check_row.target_minor_total then
            raise exception 'Para kontrol toplamı uyuşmadı: %', check_row.currency;
        end if;
    end loop;
end
$$;

create or replace function public.sync_transactions_money_compat()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
    profile_currency text;
begin
    select p.currency
    into profile_currency
    from public.profiles as p
    where p.id = new.user_id;

    if new.amount_minor is null and new.amount is not null then
        new.amount_minor := round(new.amount * 100)::bigint;
    end if;

    if new.currency is null then
        new.currency := profile_currency;
    end if;

    if new.amount is null and new.amount_minor is not null then
        new.amount := new.amount_minor::numeric / 100;
    end if;

    if new.amount_minor is null or new.amount_minor <= 0 then
        raise exception 'amount_minor sıfırdan büyük olmalıdır.';
    end if;

    if new.currency not in ('TRY', 'USD', 'EUR') then
        raise exception 'Desteklenmeyen currency değeri: %', new.currency;
    end if;

    return new;
end
$$;

create trigger transactions_money_compat_before_write
before insert or update of amount, amount_minor, currency, user_id
on public.transactions
for each row execute function public.sync_transactions_money_compat();

alter table public.transactions
    validate constraint transactions_amount_minor_positive;
alter table public.transactions
    validate constraint transactions_currency_supported;

commit;
