-- TICKET-016: Phase 2 Wishlist/Favorit
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

create table if not exists public.wishlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint wishlists_user_product_unique unique (user_id, product_id)
);

create index if not exists idx_wishlists_user_created
  on public.wishlists(user_id, created_at desc);

create index if not exists idx_wishlists_product
  on public.wishlists(product_id);

alter table public.wishlists enable row level security;

revoke all on table public.wishlists from anon;
revoke all on table public.wishlists from authenticated;

grant select, insert, delete on table public.wishlists to authenticated;
grant select, insert, update, delete on table public.wishlists to service_role;

drop policy if exists "wishlists_select_own" on public.wishlists;
drop policy if exists "wishlists_insert_own_active_product" on public.wishlists;
drop policy if exists "wishlists_delete_own" on public.wishlists;

create policy "wishlists_select_own" on public.wishlists
  for select to authenticated
  using (
    user_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
  );

create policy "wishlists_insert_own_active_product" on public.wishlists
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and exists (
      select 1
      from public.products p
      where p.id = product_id
        and p.status = 'active'
    )
  );

create policy "wishlists_delete_own" on public.wishlists
  for delete to authenticated
  using (
    user_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
  );

commit;
