-- TICKET-022: Phase 3 Orders and Transaction History
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'chat_rooms_id_buyer_seller_product_unique'
      and conrelid = 'public.chat_rooms'::regclass
  ) then
    alter table public.chat_rooms
      add constraint chat_rooms_id_buyer_seller_product_unique
      unique (id, buyer_id, seller_id, product_id);
  end if;
end $$;

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null,
  buyer_id uuid not null,
  seller_id uuid not null,
  product_id uuid not null,
  item_note text,
  weight_grams int,
  subtotal numeric(12,2) not null,
  shipping_cost numeric(12,2) not null default 0,
  total_amount numeric(12,2) generated always as (subtotal + shipping_cost) stored,
  status text not null default 'pending',
  xendit_invoice_id text,
  xendit_invoice_url text,
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  expired_at timestamptz,
  constraint orders_chat_room_participants_fkey
    foreign key (chat_room_id, buyer_id, seller_id, product_id)
    references public.chat_rooms(id, buyer_id, seller_id, product_id)
    on delete cascade,
  constraint orders_buyer_id_fkey
    foreign key (buyer_id) references public.profiles(id),
  constraint orders_seller_id_fkey
    foreign key (seller_id) references public.profiles(id),
  constraint orders_product_id_fkey
    foreign key (product_id) references public.products(id),
  constraint orders_subtotal_non_negative
    check (subtotal >= 0),
  constraint orders_shipping_cost_non_negative
    check (shipping_cost >= 0),
  constraint orders_weight_grams_positive
    check (weight_grams is null or weight_grams > 0),
  constraint orders_status_check
    check (status in ('pending', 'paid', 'expired', 'cancelled')),
  constraint orders_paid_at_matches_status
    check ((status = 'paid' and paid_at is not null) or (status <> 'paid' and paid_at is null))
);

create index if not exists idx_orders_chat_room
  on public.orders(chat_room_id);

create index if not exists idx_orders_chat_room_participants
  on public.orders(chat_room_id, buyer_id, seller_id, product_id);

create index if not exists idx_orders_buyer
  on public.orders(buyer_id, created_at desc);

create index if not exists idx_orders_seller
  on public.orders(seller_id, created_at desc);

create index if not exists idx_orders_product
  on public.orders(product_id);

create unique index if not exists idx_orders_xendit_invoice_id
  on public.orders(xendit_invoice_id)
  where xendit_invoice_id is not null;

alter table public.orders enable row level security;

revoke all on table public.orders from anon;
revoke all on table public.orders from authenticated;

grant select, insert on table public.orders to authenticated;
grant select, insert, update, delete on table public.orders to service_role;

drop policy if exists "orders_insert_as_seller" on public.orders;
drop policy if exists "orders_select_participant" on public.orders;

create policy "orders_insert_as_seller" on public.orders
  for insert to authenticated
  with check (
    seller_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and status = 'pending'
    and paid_at is null
    and expired_at is null
    and xendit_invoice_id is null
    and xendit_invoice_url is null
    and exists (
      select 1
      from public.chat_rooms cr
      where cr.id = chat_room_id
        and cr.buyer_id = orders.buyer_id
        and cr.seller_id = orders.seller_id
        and cr.product_id = orders.product_id
        and cr.seller_id = (select auth.uid())
    )
  );

create policy "orders_select_participant" on public.orders
  for select to authenticated
  using (
    coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and ((select auth.uid()) = buyer_id or (select auth.uid()) = seller_id)
  );

commit;
