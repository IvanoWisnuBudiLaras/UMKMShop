-- ============================================================================
-- 1. EXTENSIONS
-- ============================================================================
create extension if not exists pgcrypto;
create extension if not exists pgmq;
create extension if not exists pg_cron;
create extension if not exists pg_trgm;

-- ============================================================================
-- 2. TABLES
-- ============================================================================

-- profiles
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,
  store_name text,
  phone text,
  avatar_url text,
  city text,
  postal_code text,
  village_code text,
  rating_avg numeric(3,2) not null default 0,
  rating_count int not null default 0,
  created_at timestamptz not null default now(),
  constraint profiles_postal_code_format check (postal_code is null or postal_code ~ '^[0-9]{5}$'),
  constraint profiles_village_code_format check (village_code is null or village_code ~ '^[0-9]{10}$')
);

-- products
create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  price numeric(12,2) not null check (price >= 0),
  description text,
  category text,
  status text not null default 'active' check (status in ('active','inactive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- product_images
create table if not exists public.product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  image_url text not null,
  sort_order int not null default 0
);

-- chat_rooms
create table if not exists public.chat_rooms (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references public.profiles(id) on delete cascade,
  seller_id uuid not null references public.profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  last_message_at timestamptz,
  last_message_id uuid,
  is_replied boolean not null default false,
  reminder_sent boolean not null default false,
  created_at timestamptz not null default now(),
  unique (buyer_id, seller_id, product_id),
  constraint chat_rooms_id_buyer_seller_product_unique unique (id, buyer_id, seller_id, product_id)
);

-- messages
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.chat_rooms(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  message_text text not null,
  created_at timestamptz not null default now()
);

-- push_tokens
create table if not exists public.push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  fcm_token text not null,
  device_info text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, fcm_token)
);

-- orders
create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null,
  buyer_id uuid not null,
  seller_id uuid not null,
  product_id uuid not null,
  item_note text,
  weight_grams int,
  subtotal numeric(12,2) not null check (subtotal >= 0),
  shipping_cost numeric(12,2) not null default 0 check (shipping_cost >= 0),
  total_amount numeric(12,2) generated always as (subtotal + shipping_cost) stored,
  status text not null default 'pending' check (status in ('pending','paid','expired','cancelled')),
  xendit_invoice_id text,
  xendit_invoice_url text,
  xendit_external_id text,
  xendit_status text,
  payment_updated_at timestamptz,
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  expired_at timestamptz,
  constraint orders_chat_room_participants_fkey
    foreign key (chat_room_id, buyer_id, seller_id, product_id)
    references public.chat_rooms(id, buyer_id, seller_id, product_id)
    on delete cascade,
  constraint orders_buyer_id_fkey foreign key (buyer_id) references public.profiles(id),
  constraint orders_seller_id_fkey foreign key (seller_id) references public.profiles(id),
  constraint orders_product_id_fkey foreign key (product_id) references public.products(id),
  constraint orders_weight_grams_positive check (weight_grams is null or weight_grams > 0),
  constraint orders_paid_at_matches_status check ((status = 'paid' and paid_at is not null) or (status <> 'paid' and paid_at is null))
);

-- notifications (Inbox)
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null,
  title text not null,
  body text,
  related_order_id uuid references public.orders(id) on delete cascade,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

-- wishlists
create table if not exists public.wishlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint wishlists_user_product_unique unique (user_id, product_id)
);

-- reviews
create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references public.chat_rooms(id) on delete cascade,
  reviewer_id uuid not null references public.profiles(id) on delete cascade,
  seller_id uuid not null references public.profiles(id) on delete cascade,
  rating int not null check (rating between 1 and 5),
  comment text,
  self_attested boolean not null default true check (self_attested),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint reviews_room_reviewer_unique unique (chat_room_id, reviewer_id),
  constraint reviews_reviewer_not_seller check (reviewer_id <> seller_id)
);

-- product_reports
create table if not exists public.product_reports (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null,
  status text not null default 'pending' check (status in ('pending', 'reviewed', 'dismissed')),
  created_at timestamptz not null default now(),
  constraint product_reports_reason_not_blank check (btrim(reason) <> ''),
  constraint product_reports_product_reporter_reason_unique unique (product_id, reporter_id, reason)
);

-- ============================================================================
-- 3. INDEXES
-- ============================================================================
create index if not exists idx_profiles_city on public.profiles(city) where city is not null and city <> '';
create index if not exists idx_profiles_city_lower on public.profiles(lower(city)) where city is not null and city <> '';
create index if not exists idx_profiles_village_code on public.profiles(village_code) where village_code is not null and village_code <> '';

create index if not exists idx_products_seller on public.products(seller_id);
create index if not exists idx_products_browse on public.products(status, category, created_at desc);
create index if not exists idx_products_active_name_trgm on public.products using gin (name gin_trgm_ops) where status = 'active';

create index if not exists idx_product_images_product on public.product_images(product_id);

create index if not exists idx_chat_rooms_buyer on public.chat_rooms(buyer_id);
create index if not exists idx_chat_rooms_seller on public.chat_rooms(seller_id);
create index if not exists idx_chat_rooms_reminder on public.chat_rooms(last_message_at) where is_replied = false and reminder_sent = false;

create index if not exists idx_messages_room on public.messages(room_id, created_at);

create index if not exists idx_orders_buyer on public.orders(buyer_id, created_at desc);
create index if not exists idx_orders_seller on public.orders(seller_id, created_at desc);
create index if not exists idx_orders_xendit_invoice_id on public.orders(xendit_invoice_id) where xendit_invoice_id is not null;
create index if not exists idx_orders_xendit_external_id on public.orders(xendit_external_id) where xendit_external_id is not null;

create index if not exists idx_notifications_user on public.notifications(user_id, created_at desc);
create index if not exists idx_notifications_unread on public.notifications(user_id) where is_read = false;
create index if not exists idx_notifications_order_type_user_once on public.notifications(user_id, type, related_order_id) where related_order_id is not null;

create index if not exists idx_wishlists_user_created on public.wishlists(user_id, created_at desc);

create index if not exists idx_reviews_seller_created on public.reviews(seller_id, created_at desc);

-- ============================================================================
-- 4. RLS ENABLEMENT
-- ============================================================================
alter table public.profiles enable row level security;
alter table public.products enable row level security;
alter table public.product_images enable row level security;
alter table public.chat_rooms enable row level security;
alter table public.messages enable row level security;
alter table public.push_tokens enable row level security;
alter table public.orders enable row level security;
alter table public.notifications enable row level security;
alter table public.wishlists enable row level security;
alter table public.reviews enable row level security;
alter table public.product_reports enable row level security;

-- ============================================================================
-- 5. POLICIES
-- ============================================================================

-- profiles
create policy "profiles_select_authenticated" on public.profiles for select to authenticated using (true);
create policy "profiles_update_own" on public.profiles for update to authenticated using (auth.uid() = id);

-- products
create policy "products_select_active" on public.products for select using (status = 'active' or seller_id = auth.uid());
create policy "products_insert_own" on public.products for insert to authenticated with check (seller_id = auth.uid());
create policy "products_update_own" on public.products for update to authenticated using (seller_id = auth.uid());
create policy "products_delete_own" on public.products for delete to authenticated using (seller_id = auth.uid());

-- product_images
create policy "product_images_select_all" on public.product_images for select using (true);
create policy "product_images_manage_own" on public.product_images for all to authenticated using (
  exists (select 1 from public.products p where p.id = product_id and p.seller_id = auth.uid())
);

-- chat_rooms
create policy "chat_rooms_select_participant" on public.chat_rooms for select to authenticated using (buyer_id = auth.uid() or seller_id = auth.uid());
create policy "chat_rooms_insert_as_buyer" on public.chat_rooms for insert to authenticated with check (buyer_id = auth.uid());

-- messages
create policy "messages_select_participant" on public.messages for select to authenticated using (
  exists (select 1 from public.chat_rooms r where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid()))
);
create policy "messages_insert_participant" on public.messages for insert to authenticated with check (
  sender_id = auth.uid() and exists (select 1 from public.chat_rooms r where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid()))
);

-- push_tokens
create policy "push_tokens_manage_own" on public.push_tokens for all to authenticated using (user_id = auth.uid());

-- orders
create policy "orders_insert_as_seller" on public.orders for insert to authenticated with check (
  seller_id = auth.uid() and exists (select 1 from public.chat_rooms cr where cr.id = chat_room_id and cr.seller_id = auth.uid())
);
create policy "orders_select_participant" on public.orders for select to authenticated using (buyer_id = auth.uid() or seller_id = auth.uid());

-- notifications
create policy "notifications_select_own" on public.notifications for select to authenticated using (user_id = auth.uid());
create policy "notifications_update_own" on public.notifications for update to authenticated using (user_id = auth.uid());

-- wishlists
create policy "wishlists_select_own" on public.wishlists for select to authenticated using (user_id = auth.uid());
create policy "wishlists_insert_own" on public.wishlists for insert to authenticated with check (user_id = auth.uid());
create policy "wishlists_delete_own" on public.wishlists for delete to authenticated using (user_id = auth.uid());

-- reviews
create policy "reviews_select_participant" on public.reviews for select to authenticated using (reviewer_id = auth.uid() or seller_id = auth.uid());
create policy "reviews_insert_buyer" on public.reviews for insert to authenticated with check (reviewer_id = auth.uid());

-- product_reports
create policy "product_reports_insert_own" on public.product_reports for insert to authenticated with check (reporter_id = auth.uid());
create policy "product_reports_select_own" on public.product_reports for select to authenticated using (reporter_id = auth.uid());

-- ============================================================================
-- 6. QUEUE & CRON INIT
-- ============================================================================
select pgmq.create('notifications') where not exists (select 1 from pgmq.meta where queue_name = 'notifications');
