-- TICKET-023: Phase 3 Persistent Inbox
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

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

create index if not exists idx_notifications_user
  on public.notifications(user_id, created_at desc);

create index if not exists idx_notifications_unread
  on public.notifications(user_id)
  where is_read = false;

create index if not exists idx_notifications_related_order
  on public.notifications(related_order_id)
  where related_order_id is not null;

alter table public.notifications enable row level security;

revoke all on table public.notifications from anon;
revoke all on table public.notifications from authenticated;

grant select on table public.notifications to authenticated;
grant select, insert, update, delete on table public.notifications to service_role;

drop policy if exists "notifications_select_own" on public.notifications;

create policy "notifications_select_own" on public.notifications
  for select to authenticated
  using (
    coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and user_id = (select auth.uid())
  );

create or replace function public.unread_notifications_count()
returns bigint
language sql
security definer
set search_path = ''
as $$
  select case
    when (select auth.uid()) is null then 0::bigint
    when coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) then 0::bigint
    else (
      select count(*)
      from public.notifications
      where user_id = (select auth.uid())
        and is_read = false
    )
  end;
$$;

create or replace function public.mark_notifications_read()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_updated integer;
begin
  if (select auth.uid()) is null then
    raise exception 'Authentication required.';
  end if;

  if coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) then
    raise exception 'Anonymous sessions cannot mark notifications read.';
  end if;

  update public.notifications
  set is_read = true
  where user_id = (select auth.uid())
    and is_read = false;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$$;

revoke all on function public.unread_notifications_count() from public, anon;
revoke all on function public.mark_notifications_read() from public, anon;
grant execute on function public.unread_notifications_count() to authenticated;
grant execute on function public.mark_notifications_read() to authenticated;

create or replace function public.enqueue_order_notification(
  p_user_id uuid,
  p_type text,
  p_title text,
  p_body text,
  p_order public.orders
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.notifications (
    user_id,
    type,
    title,
    body,
    related_order_id
  )
  values (
    p_user_id,
    p_type,
    p_title,
    p_body,
    p_order.id
  );

  perform pgmq.send(
    'notifications',
    jsonb_build_object(
      'type', p_type,
      'order_id', p_order.id,
      'room_id', p_order.chat_room_id,
      'to_user_id', p_user_id,
      'title', p_title,
      'body', p_body
    )
  );
end;
$$;

revoke all on function public.enqueue_order_notification(uuid, text, text, text, public.orders) from public, anon, authenticated;

create or replace function public.notify_order_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_product_name text;
  v_event_type text;
  v_buyer_title text;
  v_seller_title text;
  v_body text;
begin
  select p.name
  into v_product_name
  from public.products p
  where p.id = new.product_id;

  v_product_name := coalesce(nullif(v_product_name, ''), 'Produk');

  if tg_op = 'INSERT' then
    v_event_type := 'order_created';
    v_buyer_title := 'Invoice baru';
    v_body := 'Invoice untuk ' || v_product_name || ' sudah dibuat.';

    perform public.enqueue_order_notification(
      new.buyer_id,
      v_event_type,
      v_buyer_title,
      v_body,
      new
    );

    return new;
  end if;

  if tg_op = 'UPDATE' and old.status is not distinct from new.status then
    return new;
  end if;

  case new.status
    when 'paid' then
      v_event_type := 'payment_paid';
      v_buyer_title := 'Pembayaran berhasil';
      v_seller_title := 'Pembayaran diterima';
      v_body := 'Pembayaran untuk ' || v_product_name || ' sudah tercatat.';
    when 'expired' then
      v_event_type := 'payment_expired';
      v_buyer_title := 'Invoice kedaluwarsa';
      v_seller_title := 'Invoice kedaluwarsa';
      v_body := 'Invoice untuk ' || v_product_name || ' sudah kedaluwarsa.';
    when 'cancelled' then
      v_event_type := 'payment_cancelled';
      v_buyer_title := 'Invoice dibatalkan';
      v_seller_title := 'Invoice dibatalkan';
      v_body := 'Invoice untuk ' || v_product_name || ' sudah dibatalkan.';
    else
      return new;
  end case;

  perform public.enqueue_order_notification(
    new.buyer_id,
    v_event_type,
    v_buyer_title,
    v_body,
    new
  );

  perform public.enqueue_order_notification(
    new.seller_id,
    v_event_type,
    v_seller_title,
    v_body,
    new
  );

  return new;
end;
$$;

revoke all on function public.notify_order_event() from public, anon, authenticated;

drop trigger if exists trg_after_order_insert_notification on public.orders;
create trigger trg_after_order_insert_notification
after insert on public.orders
for each row execute function public.notify_order_event();

drop trigger if exists trg_after_order_status_update_notification on public.orders;
create trigger trg_after_order_status_update_notification
after update of status on public.orders
for each row
when (old.status is distinct from new.status)
execute function public.notify_order_event();

commit;
