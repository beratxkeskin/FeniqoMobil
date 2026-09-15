-- Migration: 20260913000100_subscription_lifecycle_and_history.sql
-- Kapsam: Subscriptions tablosuna lifecycle alanları, subscription_price_histories ve subscription_payments tabloları.
-- Yalnız yerel/staging için taslaktır. Canlı/staging Supabase'e doğrudan uygulanmaz.

begin;

-- 1. subscriptions tablosuna yeni alanların eklenmesi
alter table public.subscriptions
    add column if not exists lifecycle_status text not null default 'ACTIVE',
    add column if not exists trial_end_date date,
    add column if not exists cancellation_date date,
    add column if not exists access_end_date date,
    add column if not exists reminder_enabled boolean not null default true;

-- lifecycle_status kısıtlaması
alter table public.subscriptions
    drop constraint if exists subscriptions_lifecycle_status_check;

alter table public.subscriptions
    add constraint subscriptions_lifecycle_status_check
    check (lifecycle_status in ('ACTIVE', 'PAUSED', 'CANCELLED', 'TRIAL', 'EXPIRED'));

-- trial_end_date ve cancellation_date kontrolleri
alter table public.subscriptions
    drop constraint if exists subscriptions_trial_check;

alter table public.subscriptions
    add constraint subscriptions_trial_check
    check (lifecycle_status != 'TRIAL' or trial_end_date is not null);

-- İndeksler
create index if not exists idx_subscriptions_lifecycle_status
    on public.subscriptions (lifecycle_status);

-- 2. subscription_price_histories tablosu
create table if not exists public.subscription_price_histories (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid not null references public.subscriptions(id) on delete cascade,
    old_amount_minor bigint not null,
    new_amount_minor bigint not null,
    currency text not null default 'TRY',
    changed_at timestamptz not null default timezone('utc'::text, now()),
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    deleted_at timestamptz,
    version bigint not null default 1,

    constraint sub_price_histories_old_amount_positive check (old_amount_minor > 0),
    constraint sub_price_histories_new_amount_positive check (new_amount_minor > 0),
    constraint sub_price_histories_currency_check check (currency in ('TRY', 'USD', 'EUR')),
    constraint sub_price_histories_version_positive check (version > 0)
);

create index if not exists idx_sub_price_histories_subscription_id
    on public.subscription_price_histories (subscription_id);

create index if not exists idx_sub_price_histories_sync_cursor
    on public.subscription_price_histories (updated_at, id);

create trigger sub_price_histories_set_server_metadata
    before update on public.subscription_price_histories
    for each row execute function public.sync_set_server_metadata();

alter table public.subscription_price_histories enable row level security;
revoke all on table public.subscription_price_histories from public, anon, authenticated;
grant select on table public.subscription_price_histories to authenticated;

create policy sub_price_histories_select_personal_v1
    on public.subscription_price_histories for select to authenticated
    using (
        exists (
            select 1 from public.subscriptions s
            where s.id = subscription_id
              and s.user_id = (select auth.uid())
        )
    );

-- 3. subscription_payments tablosu
create table if not exists public.subscription_payments (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid not null references public.subscriptions(id) on delete cascade,
    amount_minor bigint not null,
    currency text not null default 'TRY',
    payment_date date not null,
    renewal_due_date date not null,
    source_type text not null default 'MANUAL',
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    deleted_at timestamptz,
    version bigint not null default 1,

    constraint sub_payments_amount_positive check (amount_minor > 0),
    constraint sub_payments_currency_check check (currency in ('TRY', 'USD', 'EUR')),
    constraint sub_payments_source_type_check check (source_type in ('MANUAL', 'AUTOMATIC')),
    constraint sub_payments_version_positive check (version > 0)
);

create unique index if not exists idx_sub_payments_sub_renewal_unique
    on public.subscription_payments (subscription_id, renewal_due_date)
    where deleted_at is null;

create index if not exists idx_sub_payments_subscription_id
    on public.subscription_payments (subscription_id);

create index if not exists idx_sub_payments_sync_cursor
    on public.subscription_payments (updated_at, id);

create trigger sub_payments_set_server_metadata
    before update on public.subscription_payments
    for each row execute function public.sync_set_server_metadata();

alter table public.subscription_payments enable row level security;
revoke all on table public.subscription_payments from public, anon, authenticated;
grant select on table public.subscription_payments to authenticated;

create policy sub_payments_select_personal_v1
    on public.subscription_payments for select to authenticated
    using (
        exists (
            select 1 from public.subscriptions s
            where s.id = subscription_id
              and s.user_id = (select auth.uid())
        )
    );

commit;
