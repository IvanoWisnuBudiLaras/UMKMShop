-- ============================================================================
-- RLS POLICIES TEST (pgTAP)
-- ============================================================================

begin;
select plan(8);

-- 1. Setup Test Users
-- Create users in auth.users and profiles
insert into auth.users (id, email)
values
  ('00000000-0000-0000-0000-000000000001', 'seller@test.com'),
  ('00000000-0000-0000-0000-000000000002', 'buyer@test.com');

-- Profiles are created via trigger. Let's verify they exist.
select results_eq(
  'select count(*) from public.profiles where id in (''00000000-0000-0000-0000-000000000001'', ''00000000-0000-0000-0000-000000000002'')',
  array[2::bigint],
  'Profiles should be automatically created via trigger'
);

-- 2. Test profiles policies
-- Seller updates own profile
select set_config('role', 'authenticated', true);
select set_config('request.jwt.claims', '{"sub": "00000000-0000-0000-0000-000000000001"}', true);

select lives_ok(
  $$ update public.profiles set store_name = 'Test Store' where id = '00000000-0000-0000-0000-000000000001' $$,
  'Authenticated user can update own profile'
);

-- Buyer tries to update seller's profile
select set_config('request.jwt.claims', '{"sub": "00000000-0000-0000-0000-000000000002"}', true);
select results_eq(
  'update public.profiles set store_name = ''Hacked'' where id = ''00000000-0000-0000-0000-000000000001'' returning 1',
  'select 1 where 1=0',
  'User cannot update other profiles (RLS should filter out row)'
);

-- 3. Test products policies
-- Seller creates product
select set_config('request.jwt.claims', '{"sub": "00000000-0000-0000-0000-000000000001"}', true);
select lives_ok(
  $$ insert into public.products (id, seller_id, name, price) values ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'Test Product', 1000) $$,
  'Seller can create own product'
);

-- Buyer tries to update seller's product
select set_config('request.jwt.claims', '{"sub": "00000000-0000-0000-0000-000000000002"}', true);
select results_eq(
  'update public.products set price = 0 where id = ''00000000-0000-0000-0000-000000000101'' returning 1',
  'select 1 where 1=0',
  'Buyer cannot update seller product'
);

-- 4. Test reviews policies
-- Setup: Need a chat room for review
insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
values ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000101');

-- Buyer can review seller in that room
select set_config('request.jwt.claims', '{"sub": "00000000-0000-0000-0000-000000000002"}', true);
select lives_ok(
  $$ insert into public.reviews (chat_room_id, reviewer_id, seller_id, rating, comment) values ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 5, 'Good') $$,
  'Buyer can review seller if chat room exists'
);

-- Rating aggregate should be updated
select results_eq(
  'select rating_avg, rating_count from public.profiles where id = ''00000000-0000-0000-0000-000000000001''',
  $$ values (5.00::numeric, 1) $$,
  'Profile rating aggregate should update automatically'
);

-- 5. Test anonymous access
select set_config('role', 'anon', true);
select set_config('request.jwt.claims', '{}', true);

select results_eq(
  'select count(*) from public.products where status = ''active''',
  array[1::bigint],
  'Anon can see active products'
);

select * from finish();
rollback;
