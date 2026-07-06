-- ============================================================================
-- 1. AUTH TRIGGER (PROFILE CREATION)
-- ============================================================================
create or replace function public.handle_new_auth_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'name', split_part(new.email, '@', 1)),
    new.raw_user_meta_data->>'avatar_url'
  );
  return new;
end;
$$;

revoke all on function public.handle_new_auth_user_profile() from public, anon, authenticated;

drop trigger if exists on_auth_user_created_create_profile on auth.users;
create trigger on_auth_user_created_create_profile
after insert on auth.users
for each row execute function public.handle_new_auth_user_profile();

-- ============================================================================
-- 2. CHAT TRIGGER (NOTIFICATIONS & LAST MESSAGE)
-- ============================================================================
create or replace function public.enqueue_new_message_notification()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, pgmq
as $$
declare
  v_room public.chat_rooms%rowtype;
  v_recipient uuid;
begin
  select * into v_room from public.chat_rooms where id = new.room_id;

  if not found then
    raise exception 'chat room % not found', new.room_id using errcode = 'foreign_key_violation';
  end if;

  v_recipient := case when new.sender_id = v_room.buyer_id then v_room.seller_id else v_room.buyer_id end;

  update public.chat_rooms
  set last_message_at = new.created_at,
      last_message_id = new.id,
      is_replied = (new.sender_id = v_room.seller_id),
      reminder_sent = case when new.sender_id = v_room.buyer_id then false else reminder_sent end
  where id = new.room_id;

  perform pgmq.send(
    'notifications',
    jsonb_build_object(
      'type', 'new_message',
      'message_id', new.id,
      'room_id', new.room_id,
      'to_user_id', v_recipient,
      'preview_text', left(new.message_text, 100)
    )
  );

  return new;
end;
$$;

revoke all on function public.enqueue_new_message_notification() from public, anon, authenticated;

drop trigger if exists trg_after_message_insert on public.messages;
create trigger trg_after_message_insert
after insert on public.messages
for each row execute function public.enqueue_new_message_notification();

-- ============================================================================
-- 3. REVIEW TRIGGER (RATING AGGREGATE)
-- ============================================================================
create or replace function public.refresh_seller_rating_aggregate(p_seller_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  update public.profiles p
  set
    rating_avg = coalesce((select round(avg(r.rating)::numeric, 2) from public.reviews r where r.seller_id = p_seller_id), 0),
    rating_count = (select count(*)::int from public.reviews r where r.seller_id = p_seller_id)
  where p.id = p_seller_id;
end;
$$;

create or replace function public.handle_review_rating_aggregate()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'DELETE' then
    perform public.refresh_seller_rating_aggregate(old.seller_id);
    return old;
  end if;
  perform public.refresh_seller_rating_aggregate(new.seller_id);
  return new;
end;
$$;

drop trigger if exists trg_reviews_rating_aggregate on public.reviews;
create trigger trg_reviews_rating_aggregate
after insert or update of rating or delete on public.reviews
for each row execute function public.handle_review_rating_aggregate();

-- ============================================================================
-- 4. ORDER NOTIFICATIONS
-- ============================================================================
create or replace function public.notify_order_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_product_name text;
  v_title text;
  v_body text;
begin
  select p.name into v_product_name from public.products p where p.id = new.product_id;
  v_product_name := coalesce(nullif(v_product_name, ''), 'Produk');

  if tg_op = 'INSERT' then
    v_title := 'Invoice baru';
    v_body := 'Invoice untuk ' || v_product_name || ' sudah dibuat.';

    insert into public.notifications (user_id, type, title, body, related_order_id)
    values (new.buyer_id, 'order_created', v_title, v_body, new.id);

    perform pgmq.send('notifications', jsonb_build_object('type', 'order_created', 'order_id', new.id, 'to_user_id', new.buyer_id, 'title', v_title, 'body', v_body));
    return new;
  end if;

  if old.status is not distinct from new.status then return new; end if;

  v_title := case new.status
    when 'paid' then 'Pembayaran berhasil'
    when 'expired' then 'Invoice kedaluwarsa'
    when 'cancelled' then 'Invoice dibatalkan'
    else null
  end;

  if v_title is not null then
    v_body := 'Status pesanan ' || v_product_name || ' berubah menjadi ' || new.status;

    -- Notify Buyer
    insert into public.notifications (user_id, type, title, body, related_order_id)
    values (new.buyer_id, 'payment_' || new.status, v_title, v_body, new.id);
    perform pgmq.send('notifications', jsonb_build_object('type', 'payment_' || new.status, 'order_id', new.id, 'to_user_id', new.buyer_id, 'title', v_title, 'body', v_body));

    -- Notify Seller
    insert into public.notifications (user_id, type, title, body, related_order_id)
    values (new.seller_id, 'payment_' || new.status, v_title, v_body, new.id);
    perform pgmq.send('notifications', jsonb_build_object('type', 'payment_' || new.status, 'order_id', new.id, 'to_user_id', new.seller_id, 'title', v_title, 'body', v_body));
  end if;

  return new;
end;
$$;

drop trigger if exists trg_after_order_event on public.orders;
create trigger trg_after_order_event
after insert or update of status on public.orders
for each row execute function public.notify_order_event();

-- ============================================================================
-- 5. CRON SCHEDULES
-- ============================================================================
select cron.schedule('deactivate-stale-products', '0 0 * * *', $$ update public.products set status = 'inactive', updated_at = now() where status = 'active' and updated_at < now() - interval '30 days' $$);

select cron.schedule('chat-reply-reminder', '*/10 * * * *', $$
  with due as (
    update public.chat_rooms set reminder_sent = true
    where is_replied = false and reminder_sent = false and last_message_at < now() - interval '2 hours'
    returning id, seller_id
  )
  select pgmq.send('notifications', jsonb_build_object('type', 'reply_reminder', 'room_id', id, 'to_user_id', seller_id))
  from due
$$);
