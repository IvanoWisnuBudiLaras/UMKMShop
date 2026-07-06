-- TICKET-005 validation smoke test.
-- Run after ticket-005-trigger-and-cron.sql.
-- This script rolls back all dummy rows, including queue messages.

begin;

do $$
declare
  v_seller uuid := gen_random_uuid();
  v_buyer uuid := gen_random_uuid();
  v_third uuid := gen_random_uuid();
  v_product uuid;
  v_stale_product uuid;
  v_room uuid;
  v_due_room uuid;
  v_race_room uuid;
  v_buyer_message uuid;
  v_seller_message uuid;
  v_seller_newer_at timestamptz := now() - interval '1 minute';
  v_job_count int;
begin
  insert into auth.users (id, email, email_confirmed_at)
  values
    (v_seller, 'ticket005-seller@example.test', now()),
    (v_buyer, 'ticket005-buyer@example.test', now()),
    (v_third, 'ticket005-third@example.test', now());

  insert into public.profiles (id, name)
  values
    (v_seller, 'Ticket 005 Seller'),
    (v_buyer, 'Ticket 005 Buyer'),
    (v_third, 'Ticket 005 Third');

  insert into public.products (seller_id, name, price, description, category)
  values (v_seller, 'Ticket 005 Product', 10000, 'Trigger smoke product', 'smoke')
  returning id into v_product;

  insert into public.chat_rooms (
    buyer_id,
    seller_id,
    product_id,
    last_message_at,
    is_replied,
    reminder_sent
  )
  values (v_buyer, v_seller, v_product, now() - interval '3 hours', true, true)
  returning id into v_room;

  insert into public.messages (room_id, sender_id, message_text)
  values (v_room, v_buyer, repeat('buyer asks about the smoke product ', 5))
  returning id into v_buyer_message;

  if not exists (
    select 1
    from public.chat_rooms
    where id = v_room
      and last_message_at is not null
      and is_replied = false
      and reminder_sent = false
  ) then
    raise exception 'buyer message did not update room state correctly';
  end if;

  if not exists (
    select 1
    from pgmq.q_notifications
    where message @> jsonb_build_object(
      'type', 'new_message',
      'message_id', v_buyer_message,
      'room_id', v_room,
      'to_user_id', v_seller
    )
      and jsonb_typeof(message) = 'object'
      and message ? 'preview_text'
  ) then
    raise exception 'buyer message did not enqueue notification for seller';
  end if;

  insert into public.messages (room_id, sender_id, message_text)
  values (v_room, v_seller, 'seller replies')
  returning id into v_seller_message;

  if not exists (
    select 1
    from public.chat_rooms
    where id = v_room
      and is_replied = true
      and reminder_sent = false
  ) then
    raise exception 'seller message did not mark room as replied';
  end if;

  if not exists (
    select 1
    from pgmq.q_notifications
    where message @> jsonb_build_object(
      'type', 'new_message',
      'message_id', v_seller_message,
      'room_id', v_room,
      'to_user_id', v_buyer
    )
  ) then
    raise exception 'seller message did not enqueue notification for buyer';
  end if;

  insert into public.products (seller_id, name, price, description, category)
  values (v_seller, 'Ticket 005 Race Product', 15000, 'Race smoke product', 'smoke')
  returning id into v_product;

  insert into public.chat_rooms (buyer_id, seller_id, product_id)
  values (v_buyer, v_seller, v_product)
  returning id into v_race_room;

  insert into public.messages (room_id, sender_id, message_text, created_at)
  values (v_race_room, v_seller, 'newer seller message', v_seller_newer_at);

  insert into public.messages (room_id, sender_id, message_text, created_at)
  values (v_race_room, v_buyer, 'older buyer message committed later', v_seller_newer_at - interval '5 minutes');

  if not exists (
    select 1
    from public.chat_rooms
    where id = v_race_room
      and last_message_at = v_seller_newer_at
      and is_replied = true
  ) then
    raise exception 'older message trigger overwrote newer room state';
  end if;

  insert into public.products (
    seller_id,
    name,
    price,
    description,
    category,
    status,
    updated_at
  )
  values (
    v_seller,
    'Ticket 005 Stale Product',
    20000,
    'Stale smoke product',
    'smoke',
    'active',
    now() - interval '31 days'
  )
  returning id into v_stale_product;

  update public.products
  set status = 'inactive', updated_at = now()
  where status = 'active'
    and updated_at < now() - interval '30 days'
    and id = v_stale_product;

  if not exists (
    select 1
    from public.products
    where id = v_stale_product
      and status = 'inactive'
  ) then
    raise exception 'stale product cron body did not deactivate product';
  end if;

  insert into public.products (seller_id, name, price, description, category)
  values (v_seller, 'Ticket 005 Reminder Product', 30000, 'Reminder product', 'smoke')
  returning id into v_product;

  insert into public.chat_rooms (buyer_id, seller_id, product_id)
  values (v_third, v_seller, v_product)
  returning id into v_due_room;

  insert into public.messages (room_id, sender_id, message_text, created_at)
  values (v_due_room, v_third, 'buyer reminder due', now() - interval '3 hours');

  with due as (
    update public.chat_rooms
    set reminder_sent = true
    where is_replied = false
      and reminder_sent = false
      and last_message_at < now() - interval '2 hours'
      and id = v_due_room
      and exists (
        select 1
        from public.messages latest_buyer_message
        where latest_buyer_message.room_id = chat_rooms.id
          and latest_buyer_message.sender_id = chat_rooms.buyer_id
          and latest_buyer_message.created_at = chat_rooms.last_message_at
          and not exists (
            select 1
            from public.messages newer_message
            where newer_message.room_id = chat_rooms.id
              and newer_message.created_at > latest_buyer_message.created_at
          )
      )
    returning id, seller_id
  )
  select count(*)
  into v_job_count
  from (
    select pgmq.send(
      'notifications',
      jsonb_build_object(
        'type', 'reply_reminder',
        'room_id', id,
        'to_user_id', seller_id
      )
    )
    from due
  ) sent;

  if v_job_count < 1 then
    raise exception 'reminder cron body did not enqueue any reminder jobs';
  end if;

  if not exists (
    select 1
    from public.chat_rooms
    where id = v_due_room
      and reminder_sent = true
  ) then
    raise exception 'reminder cron body did not mark due room as reminder_sent';
  end if;

  if not exists (
    select 1
    from pgmq.q_notifications
    where message @> jsonb_build_object(
      'type', 'reply_reminder',
      'room_id', v_due_room,
      'to_user_id', v_seller
    )
  ) then
    raise exception 'reminder cron body did not enqueue expected payload';
  end if;

  if exists (
    select 1
    from public.chat_rooms client_spoof_room
    where client_spoof_room.id = v_due_room
      and not exists (
        select 1
        from public.messages buyer_message
        where buyer_message.room_id = client_spoof_room.id
          and buyer_message.sender_id = client_spoof_room.buyer_id
      )
  ) then
    raise exception 'reminder validation did not prove a buyer-authored message exists';
  end if;

  if exists (
    select 1
    from information_schema.role_routine_grants
    where routine_schema = 'public'
      and routine_name = 'enqueue_new_message_notification'
      and grantee in ('PUBLIC', 'anon', 'authenticated')
  ) then
    raise exception 'enqueue_new_message_notification is directly executable by client roles';
  end if;

  if not exists (
    select 1
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'enqueue_new_message_notification'
      and p.prosecdef = true
      and p.proconfig @> array['search_path=pg_catalog, public, pgmq']
  ) then
    raise exception 'function is missing SECURITY DEFINER or pinned search_path';
  end if;

  if not exists (
    select 1
    from cron.job
    where jobname = 'deactivate-stale-products'
      and schedule = '0 0 * * *'
      and active = true
  ) then
    raise exception 'deactivate-stale-products cron job is not scheduled as expected';
  end if;

  if not exists (
    select 1
    from cron.job
    where jobname = 'chat-reply-reminder'
      and schedule = '*/10 * * * *'
      and active = true
  ) then
    raise exception 'chat-reply-reminder cron job is not scheduled as expected';
  end if;
end $$;

rollback;
