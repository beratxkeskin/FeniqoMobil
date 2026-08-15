-- Yeni/staging Supabase projeleri için Feniqo V1 taban şeması.
-- Mevcut production şemasında doğrudan çalıştırılmaz; önce migration history baseline edilir.
begin;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    full_name text,
    currency text not null default 'TRY' check (currency in ('TRY', 'USD', 'EUR')),
    theme text not null default 'system' check (theme in ('light', 'dark', 'system')),
    lang text not null default 'tr',
    active_workspace_id uuid,
    created_at timestamptz not null default timezone('utc'::text, now())
);

create table public.categories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade,
    workspace_id uuid,
    name text not null,
    type text not null check (type in ('income', 'expense')),
    color text not null,
    icon text,
    is_default boolean not null default false,
    created_at timestamptz not null default timezone('utc'::text, now()),
    constraint categories_personal_owner_required
        check (is_default or user_id is not null)
);

create table public.transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    workspace_id uuid,
    amount numeric not null check (amount > 0),
    type text not null check (type in ('income', 'expense')),
    category_id uuid not null references public.categories(id) on delete restrict,
    description text,
    payment_method text not null,
    transaction_date date not null default current_date,
    receipt_url text,
    installment_number integer,
    total_installments integer,
    installment_group_id text,
    created_at timestamptz not null default timezone('utc'::text, now())
);

create index categories_owner_created_idx on public.categories (user_id, created_at, id);
create index transactions_owner_date_idx on public.transactions (user_id, transaction_date desc, id);
create index transactions_category_idx on public.transactions (category_id);

create or replace function public.handle_new_user_v1()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, email, full_name, currency, theme, lang)
    values (
        new.id,
        coalesce(new.email, ''),
        nullif(new.raw_user_meta_data ->> 'full_name', ''),
        'TRY',
        'system',
        'tr'
    )
    on conflict (id) do nothing;
    return new;
end
$$;

create trigger on_auth_user_created_feniqo_v1
after insert on auth.users
for each row execute function public.handle_new_user_v1();

commit;
