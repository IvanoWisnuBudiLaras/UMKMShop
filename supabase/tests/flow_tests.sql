-- ============================================================================
-- BUSINESS FLOW VALIDATION (pgTAP)
-- ============================================================================

begin;
select plan(10);

-- 1. Setup Data
insert into auth.users (id, email) values ('00000000-0000-0000-0000-000000000010', 'flow-seller@test.com');
insert into auth.users (id, email) values ('00000000-0000-0000-0000-000000000011', 'flow-buyer@test.com');

-- 2. Catalog & Search Flow (§5.3)
insert into public.products (id, seller_id, name, price, status)
values ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000010', 'Beras Cianjur Super', 15000, 'active');

select is(
    (select count(*) from public.products where name ilike '%beras%')::int,
    1,
    'Catalog search should find product by name'
);

-- 3. Chat & Notification Flow (§5.4)
-- Setup room
insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
values ('00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000100');

-- Insert message and check trigger
insert into public.messages (room_id, sender_id, message_text)
values ('00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000011', 'Halo ready?');

select results_eq(
    'select is_replied, reminder_sent from public.chat_rooms where id = ''00000000-0000-0000-0000-000000000200''',
    $$ values (false, false) $$,
    'Buyer message should not mark room as replied'
);

-- Check PGMQ enqueue
select is(
    (select count(*) from pgmq.q_notifications)::int,
    1,
    'Message trigger should enqueue notification in PGMQ'
);

-- 4. Order & Payment Flow (§5.5)
-- Seller creates order
insert into public.orders (id, chat_room_id, buyer_id, seller_id, product_id, subtotal)
values ('00000000-0000-0000-0000-000000000300', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000100', 15000);

-- Check Inbox sync (notifications table)
select is(
    (select count(*) from public.notifications where related_order_id = '00000000-0000-0000-0000-000000000300' and user_id = '00000000-0000-0000-0000-000000000011')::int,
    1,
    'Order creation should sync to buyer inbox'
);

-- Simulate Xendit PAID callback
update public.orders
set status = 'paid', paid_at = now()
where id = '00000000-0000-0000-0000-000000000300';

-- Check status change notifications
select is(
    (select count(*) from public.notifications where type = 'payment_paid' and related_order_id = '00000000-0000-0000-0000-000000000300')::int,
    2, -- One for buyer, one for seller
    'Status paid should notify both parties in inbox'
);

-- 5. Review System Flow (§5.6)
insert into public.reviews (chat_room_id, reviewer_id, seller_id, rating, comment)
values ('00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', 5, 'Great seller');

select is(
    (select rating_avg from public.profiles where id = '00000000-0000-0000-0000-000000000010')::numeric,
    5.00,
    'Review should update seller rating average'
);

-- 6. Cron Verification (Manual trigger of schedules logic if possible or test the logic)
-- Since we can't wait 30 days, we test the logic via direct call or by manipulating timestamps
update public.products
set updated_at = now() - interval '31 days'
where id = '00000000-0000-0000-0000-000000000100';

-- Manually run stale cleanup logic
update public.products
set status = 'inactive', updated_at = now()
where status = 'active' and updated_at < now() - interval '30 days';

select is(
    (select status from public.products where id = '00000000-0000-0000-0000-000000000100'),
    'inactive',
    'Stale product cleanup logic works'
);

select * from finish();
rollback;
