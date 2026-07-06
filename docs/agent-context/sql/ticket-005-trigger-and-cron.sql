-- TICKET-005: Trigger and Cron
-- Run in Supabase SQL Editor with an owner role.

begin;

create extension if not exists pgmq;
create extension if not exists pg_cron;

select pgmq.create('notifications')
where not exists (
  select 1
  from pgmq.meta
  where queue_name = 'notifications'
);

drop policy if exists "chat_rooms_insert_as_buyer" on public.chat_rooms;

create policy "chat_rooms_insert_as_buyer" on public.chat_rooms
  for insert to authenticated
  with check (
    buyer_id = (select auth.uid())
    and seller_id <> (select auth.uid())
    and last_message_at is null
    and is_replied = false
    and reminder_sent = false
    and exists (
      select 1
      from public.products p
      where p.id = product_id
        and p.seller_id = chat_rooms.seller_id
        and p.status = 'active'
    )
  );

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
  select *
  into v_room
  from public.chat_rooms
  where id = new.room_id;

  if not found then
    raise exception 'chat room % not found for message %', new.room_id, new.id
      using errcode = 'foreign_key_violation';
  end if;

  if new.sender_id not in (v_room.buyer_id, v_room.seller_id) then
    raise exception 'sender % is not a participant in room %', new.sender_id, new.room_id
      using errcode = 'check_violation';
  end if;

  v_recipient := case
    when new.sender_id = v_room.buyer_id then v_room.seller_id
    else v_room.buyer_id
  end;

  update public.chat_rooms
  set
    last_message_at = new.created_at,
    is_replied = (new.sender_id = v_room.seller_id),
    reminder_sent = case
      when new.sender_id = v_room.buyer_id then false
      else reminder_sent
    end
  where id = new.room_id
    and (
      last_message_at is null
      or new.created_at >= last_message_at
    );

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

revoke all on function public.enqueue_new_message_notification() from public;
revoke all on function public.enqueue_new_message_notification() from anon;
revoke all on function public.enqueue_new_message_notification() from authenticated;

drop trigger if exists trg_after_message_insert on public.messages;

create trigger trg_after_message_insert
after insert on public.messages
for each row
execute function public.enqueue_new_message_notification();

select cron.unschedule('deactivate-stale-products')
where exists (
  select 1 from cron.job where jobname = 'deactivate-stale-products'
);

select cron.schedule(
  'deactivate-stale-products',
  '0 0 * * *',
  $cron$
    update public.products
    set status = 'inactive', updated_at = now()
    where status = 'active'
      and updated_at < now() - interval '30 days';
  $cron$
);

select cron.unschedule('chat-reply-reminder')
where exists (
  select 1 from cron.job where jobname = 'chat-reply-reminder'
);

select cron.schedule(
  'chat-reply-reminder',
  '*/10 * * * *',
  $cron$
    with due as (
      update public.chat_rooms
      set reminder_sent = true
      where is_replied = false
        and reminder_sent = false
        and last_message_at < now() - interval '2 hours'
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
    select pgmq.send(
      'notifications',
      jsonb_build_object(
        'type', 'reply_reminder',
        'room_id', id,
        'to_user_id', seller_id
      )
    )
    from due;
  $cron$
);

commit;
