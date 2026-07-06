-- TICKET-023 validation helper.
-- Run with an owner role. The transaction rolls back all seed data.

begin;

do $$
declare
  v_buyer uuid := '10000000-0023-0000-0000-000000000001';
  v_seller uuid := '10000000-0023-0000-0000-000000000002';
  v_other uuid := '10000000-0023-0000-0000-000000000003';
  v_product uuid := '20000000-0023-0000-0000-000000000001';
  v_room uuid := '30000000-0023-0000-0000-000000000001';
  v_order uuid;
  v_notification uuid;
  v_visible_count int;
  v_unread_count bigint;
  v_updated int;
  v_payload_count int;
begin
  insert into auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
  values
    (v_buyer, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-023-buyer@example.test', 'validation-only', now(), now(), now()),
    (v_seller, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-023-seller@example.test', 'validation-only', now(), now(), now()),
    (v_other, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-023-other@example.test', 'validation-only', now(), now(), now())
  on conflict (id) do nothing;

  insert into public.profiles (id, name)
  values
    (v_buyer, 'Buyer TICKET-023'),
    (v_seller, 'Seller TICKET-023'),
    (v_other, 'Other TICKET-023')
  on conflict (id) do nothing;

  insert into public.products (id, seller_id, name, price, category, status)
  values (v_product, v_seller, 'Produk TICKET-023', 10000, 'Makanan (bahan makanan)', 'active')
  on conflict (id) do nothing;

  insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
  values (v_room, v_buyer, v_seller, v_product)
  on conflict (buyer_id, seller_id, product_id) do nothing;

  insert into public.orders (
    chat_room_id,
    buyer_id,
    seller_id,
    product_id,
    item_note,
    weight_grams,
    subtotal
  )
  values (v_room, v_buyer, v_seller, v_product, '50kg bahan validasi', 50000, 250000)
  returning id into v_order;

  select id into v_notification
  from public.notifications
  where user_id = v_buyer
    and related_order_id = v_order
    and type = 'order_created';

  if v_notification is null then
    raise exception 'Order insert did not create buyer inbox notification.';
  end if;

  select count(*) into v_payload_count
  from pgmq.q_notifications
  where message ->> 'type' = 'order_created'
    and message ->> 'order_id' = v_order::text
    and message ->> 'to_user_id' = v_buyer::text;

  if v_payload_count <> 1 then
    raise exception 'Order insert did not enqueue exactly one buyer push job.';
  end if;

  update public.orders
  set status = 'paid', paid_at = now()
  where id = v_order;

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order
    and type = 'payment_paid';

  if v_visible_count <> 2 then
    raise exception 'Paid status did not create buyer and seller notifications.';
  end if;

  set local role authenticated;
  perform set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', v_buyer::text,
      'role', 'authenticated',
      'is_anonymous', false
    )::text,
    true
  );

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order;

  if v_visible_count <> 2 then
    raise exception 'Buyer cannot read own notifications only.';
  end if;

  select public.unread_notifications_count() into v_unread_count;
  if v_unread_count <> 2 then
    raise exception 'Buyer unread count expected 2, got %.', v_unread_count;
  end if;

  select public.mark_notifications_read() into v_updated;
  if v_updated <> 2 then
    raise exception 'Buyer mark read expected 2 updates, got %.', v_updated;
  end if;

  select public.unread_notifications_count() into v_unread_count;
  if v_unread_count <> 0 then
    raise exception 'Buyer unread count after mark read expected 0, got %.', v_unread_count;
  end if;

  begin
    update public.notifications
    set title = 'spoofed'
    where id = v_notification;
    raise exception 'Authenticated client unexpectedly updated notification payload directly.';
  exception
    when insufficient_privilege then
      null;
  end;

  perform set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', v_other::text,
      'role', 'authenticated',
      'is_anonymous', false
    )::text,
    true
  );

  select count(*) into v_visible_count
  from public.notifications
  where related_order_id = v_order;

  if v_visible_count <> 0 then
    raise exception 'Unrelated user can read another user notification.';
  end if;
end $$;

rollback;
