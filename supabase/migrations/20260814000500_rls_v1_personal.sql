-- V1 kişisel profil, kategori ve işlem politikaları. Hard-delete istemciye kapalıdır.
begin;

alter table public.profiles enable row level security;
alter table public.categories enable row level security;
alter table public.transactions enable row level security;

revoke all on table public.profiles, public.categories, public.transactions from anon;
revoke all on table public.profiles, public.categories, public.transactions from authenticated;

grant select, update on table public.profiles to authenticated;
grant select, insert, update on table public.categories to authenticated;
grant select, insert, update on table public.transactions to authenticated;

create policy profiles_select_own_v1
on public.profiles for select to authenticated
using (id = (select auth.uid()));

create policy profiles_update_own_v1
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

create policy categories_select_personal_v1
on public.categories for select to authenticated
using (
    (is_default and user_id is null)
    or (user_id = (select auth.uid()) and workspace_id is null)
);

create policy categories_insert_personal_v1
on public.categories for insert to authenticated
with check (
    user_id = (select auth.uid())
    and workspace_id is null
    and not is_default
);

create policy categories_update_personal_v1
on public.categories for update to authenticated
using (
    user_id = (select auth.uid())
    and workspace_id is null
    and not is_default
)
with check (
    user_id = (select auth.uid())
    and workspace_id is null
    and not is_default
);

create policy transactions_select_personal_v1
on public.transactions for select to authenticated
using (user_id = (select auth.uid()) and workspace_id is null);

create policy transactions_insert_personal_v1
on public.transactions for insert to authenticated
with check (user_id = (select auth.uid()) and workspace_id is null);

create policy transactions_update_personal_v1
on public.transactions for update to authenticated
using (user_id = (select auth.uid()) and workspace_id is null)
with check (user_id = (select auth.uid()) and workspace_id is null);

create or replace function public.protect_profile_identity_v1()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id is distinct from old.id
       or new.created_at is distinct from old.created_at then
        raise exception 'Profil kimliği ve oluşturma zamanı değiştirilemez.';
    end if;
    return new;
end
$$;

create or replace function public.protect_category_identity_v1()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id is distinct from old.id
       or new.user_id is distinct from old.user_id
       or new.workspace_id is distinct from old.workspace_id
       or new.is_default is distinct from old.is_default
       or new.created_at is distinct from old.created_at then
        raise exception 'Kategori sahiplik alanları değiştirilemez.';
    end if;
    return new;
end
$$;

create or replace function public.protect_transaction_identity_v1()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id is distinct from old.id
       or new.user_id is distinct from old.user_id
       or new.workspace_id is distinct from old.workspace_id
       or new.created_at is distinct from old.created_at then
        raise exception 'İşlem sahiplik alanları değiştirilemez.';
    end if;
    return new;
end
$$;

create trigger profiles_protect_identity_v1
before update on public.profiles
for each row execute function public.protect_profile_identity_v1();

create trigger categories_protect_identity_v1
before update on public.categories
for each row execute function public.protect_category_identity_v1();

create trigger transactions_protect_identity_v1
before update on public.transactions
for each row execute function public.protect_transaction_identity_v1();

commit;
