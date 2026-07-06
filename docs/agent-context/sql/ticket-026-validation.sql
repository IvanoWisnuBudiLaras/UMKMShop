-- TICKET-026 validation helper.
-- Run with an owner role after TICKET-022 and TICKET-023 migrations.
-- This script rolls back all seed data.
--
-- Scope covered here:
-- - orders RLS: buyer/seller can read, unrelated user cannot.
-- - notifications RLS: only recipient can read.
-- - authenticated clients cannot update payment-owned order fields.
-- - order status paid creates buyer+seller inbox notifications and pgmq jobs.
-- - duplicate paid update does not create duplicate notifications/jobs.
--
-- Scope not covered here:
-- - real Xendit sandbox invoice/payment.
-- - real Xendit callback token/signature verification.
-- - real api.co.id shipping response.
-- - real FCM delivery.

begin;

do $$
declare
  v_buyer uuid := '10000000-0026-0000-0000-000000000001';
  v_seller uuid := '10000000-0026-0000-0000-000000000002';
  v_other uuid := '10000000-0026-0000-0000-000000000003';
  v_product uuid := '20000000-0026-0000-0000-000000000001';
  v_room uuid := '30000000-0026-0000-0000-000000000001';
  v_order uuid;
  v_visible_count int;
  v_notification_count int;
  v_queue_count int;
begin
  insert into auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
  values
    (v_buyer, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-026-buyer@example.test', 'validation-only', now(), now(), now()),
    (v_seller, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-026-seller@example.test', 'validation-only', now(), now(), now()),
    (v_other, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-026-other@example.test', 'validation-only', now(), now(), now())
  on conflict (id) do nothing;

  insert into public.profiles (id, name, store_name, city, postal_code, village_code)
  values
    (v_buyer, 'Buyer TICKET-026', null, 'Bandung', '40513', '3277010001'),
    (v_seller, 'Seller TICKET-026', 'Supplier TICKET-026', 'Jakarta', '40111', '3173010001'),
    (v_other, 'Other TICKET-026', null, 'Bogor', '16111', '3271010001')
  on conflict (id) do update
  set name = excluded.name,
      store_name = excluded.store_name,
      city = excluded.city,
      postal_code = excluded.postal_code,
      village_code = excluded.village_code;

  insert into public.products (id, seller_id, name, price, category, status)
  values (v_product, v_seller, 'Bahan Validasi TICKET-026', 10000, 'Makanan (bahan makanan)', 'active')
  on conflict (id) do update
  set seller_id = excluded.seller_id,
      name = excluded.name,
      price = excluded.price,
      category = excluded.category,
      status = excluded.status;

  insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
  values (v_room, v_buyer, v_seller, v_product)
  on conflict (buyer_id, seller_id, product_id) do update
  set last_message_at = excluded.last_message_at
  returning id into v_room;

  insert into public.orders (
    chat_room_id,
    buyer_id,
    seller_id,
    product_id,
    item_note,
    weight_grams,
    subtotal,
    shipping_cost
  )
  values (v_room, v_buyer, v_seller, v_product, '50kg bahan validasi', 50000, 250000, 18000)
  returning id into v_order;

  select count(*) into v_notification_count
  from public.notifications
  where related_order_id = v_order
    and type = 'order_created'
    and user_id = v_buyer;

  if v_notification_count <> 1 then
    raise exception 'order_created inbox notification expected 1, got %.', v_notification_count;
  end if;

  update public.orders
  set status = 'paid',
      paid_at = now()
  where id = v_order;

  select count(*) into v_notification_count
  from public.notifications
  where related_order_id = v_order
    and type = 'payment_paid';

  if v_notification_count <> 2 then
    raise exception 'payment_paid inbox notifications expected 2, got %.', v_notification_count;
  end if;

  select count(*) into v_queue_count
  from pgmq.q_notifications
  where message ->> 'type' = 'payment_paid'
    and message ->> 'order_id' = v_order::text;

  if v_queue_count <> 2 then
    raise exception 'payment_paid pgmq jobs expected 2, got %.', v_queue_count;
  end if;

  -- Simulates a duplicate webhook retry that re-applies the same terminal status.
  update public.orders
  set status = 'paid',
      paid_at = paid_at
  where id = v_order;

  select count(*) into v_notification_count
  from public.notifications
  where related_order_id = v_order
    and type = 'payment_paid';

  if v_notification_count <> 2 then
    raise exception 'duplicate paid update created duplicate inbox notifications: %.', v_notification_count;
  end if;

  select count(*) into v_queue_count
  from pgmq.q_notifications
  where message ->> 'type' = 'payment_paid'
    and message ->> 'order_id' = v_order::text;

  if v_queue_count <> 2 then
    raise exception 'duplicate paid update created duplicate pgmq jobs: %.', v_queue_count;
  end if;

  set local role authenticated;
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_buyer::text, 'role', 'authenticated', 'is_anonymous', false)::text,
    true
  );

  select count(*) into v_visible_count
  from public.orders
  where id = v_order;

  if v_visible_count <> 1 then
    raise exception 'buyer cannot read own order.';
  end if;

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order;

  if v_visible_count <> 2 then
    raise exception 'buyer should see own order_created and payment_paid notifications, got %.', v_visible_count;
  end if;

  begin
    update public.orders
    set status = 'cancelled',
        paid_at = null
    where id = v_order;
    raise exception 'authenticated buyer unexpectedly updated order payment fields.';
  exception
    when insufficient_privilege then
      null;
  end;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_seller::text, 'role', 'authenticated', 'is_anonymous', false)::text,
    true
  );

  select count(*) into v_visible_count
  from public.orders
  where id = v_order;

  if v_visible_count <> 1 then
    raise exception 'seller cannot read own order.';
  end if;

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order;

  if v_visible_count <> 1 then
    raise exception 'seller should see only seller payment notification, got %.', v_visible_count;
  end if;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_other::text, 'role', 'authenticated', 'is_anonymous', false)::text,
    true
  );

  select count(*) into v_visible_count
  from public.orders
  where id = v_order;

  if v_visible_count <> 0 then
    raise exception 'unrelated user can read another user order.';
  end if;

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order;

  if v_visible_count <> 0 then
    raise exception 'unrelated user can read another user notifications.';
  end if;
end $$;

rollback;
