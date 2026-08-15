-- TASLAK: Yalnız yerel/staging doğrulaması içindir. Production'a doğrudan uygulamayın.
begin;

alter table public.profiles
    add column updated_at timestamptz,
    add column version bigint not null default 1;

update public.profiles
set updated_at = created_at
where updated_at is null;

alter table public.profiles
    alter column updated_at set default timezone('utc'::text, now()),
    alter column updated_at set not null,
    add constraint profiles_version_positive check (version > 0);

alter table public.categories
    add column slug text,
    add column updated_at timestamptz,
    add column deleted_at timestamptz,
    add column version bigint not null default 1;

update public.categories
set updated_at = created_at
where updated_at is null;

alter table public.categories
    alter column updated_at set default timezone('utc'::text, now()),
    alter column updated_at set not null,
    add constraint categories_version_positive check (version > 0);

alter table public.transactions
    add column receipt_path text,
    add column updated_at timestamptz,
    add column deleted_at timestamptz,
    add column version bigint not null default 1;

update public.transactions
set updated_at = created_at
where updated_at is null;

alter table public.transactions
    alter column updated_at set default timezone('utc'::text, now()),
    alter column updated_at set not null,
    add constraint transactions_version_positive check (version > 0);

-- Artımlı pull aynı timestamp'teki kayıtları id ile kararlı biçimde sıralar.
create index profiles_sync_cursor_idx on public.profiles (updated_at, id);
create index categories_sync_cursor_idx on public.categories (updated_at, id);
create index transactions_sync_cursor_idx on public.transactions (updated_at, id);
create index categories_active_owner_idx on public.categories (user_id, updated_at, id)
    where deleted_at is null;
create index transactions_active_owner_idx on public.transactions (user_id, updated_at, id)
    where deleted_at is null;

commit;
