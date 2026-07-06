-- TICKET-022 validation helper.
-- Run with an owner role. The transaction rolls back all seed data.

begin;

do $$
declare
  v_buyer uuid := '10000000-0022-0000-0000-000000000001';
  v_seller uuid := '10000000-0022-0000-0000-000000000002';
  v_other uuid := '10000000-0022-0000-0000-000000000003';
  v_product uuid := '20000000-0022-0000-0000-000000000001';
  v_room uuid := '30000000-0022-0000-0000-000000000001';
  v_order uuid;
  v_visible_count int;
begin
  insert into auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
  values
    (v_buyer, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-022-buyer@example.test', 'validation-only', now(), now(), now()),
    (v_seller, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-022-seller@example.test', 'validation-only', now(), now(), now()),
    (v_other, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ticket-022-other@example.test', 'validation-only', now(), now(), now())
  on conflict (id) do nothing;

  insert into public.profiles (id, name)
  values
    (v_buyer, 'Buyer TICKET-022'),
    (v_seller, 'Seller TICKET-022'),
    (v_other, 'Other TICKET-022')
  on conflict (id) do nothing;

  insert into public.products (id, seller_id, name, price, category, status)
  values (v_product, v_seller, 'Produk TICKET-022', 10000, 'Makanan (bahan makanan)', 'active')
  on conflict (id) do nothing;

  insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
  values (v_room, v_buyer, v_seller, v_product)
  on conflict (buyer_id, seller_id, product_id) do nothing;

  set local role authenticated;
  perform set_config(
    'request.jwt.claims',
    json_build_object(
      'sub', v_seller::text,
      'role', 'authenticated',
      'is_anonymous', false
    )::text,
    true
  );

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

  if v_order is null then
    raise exception 'Seller insert did not return an order id.';
  end if;

  select count(*) into v_visible_count
  from public.orders
  where id = v_order;

  if v_visible_count <> 1 then
    raise exception 'Seller cannot read their order.';
  end if;

  begin
    insert into public.orders (
      chat_room_id,
      buyer_id,
      seller_id,
      product_id,
      subtotal,
      status,
      xendit_invoice_id
    )
    values (v_room, v_buyer, v_seller, v_product, 250000, 'paid', 'spoofed');
    raise exception 'Client insert accepted protected payment fields.';
  exception
    when insufficient_privilege or check_violation or with_check_option_violation then
      null;
  end;

  begin
    update public.orders
    set status = 'paid', paid_at = now()
    where id = v_order;
    raise exception 'Authenticated client unexpectedly updated order status.';
  exception
    when insufficient_privilege then
      null;
  end;

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
  from public.orders
  where id = v_order;

  if v_visible_count <> 1 then
    raise exception 'Buyer cannot read their order.';
  end if;

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
  from public.orders
  where id = v_order;

  if v_visible_count <> 0 then
    raise exception 'Unrelated user can read another user order.';
  end if;
end $$;

rollback;
