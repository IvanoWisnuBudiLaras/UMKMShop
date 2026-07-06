-- TICKET-024: Phase 3 Xendit invoices and webhook
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

create unique index if not exists idx_notifications_order_type_user_once
  on public.notifications(user_id, type, related_order_id)
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

alter table public.orders
  add column if not exists xendit_external_id text,
  add column if not exists xendit_status text,
  add column if not exists payment_updated_at timestamptz;

create unique index if not exists idx_orders_xendit_invoice_id
  on public.orders(xendit_invoice_id)
  where xendit_invoice_id is not null;

create unique index if not exists idx_orders_xendit_external_id
  on public.orders(xendit_external_id)
  where xendit_external_id is not null;

create or replace function public.ticket_024_enqueue_order_notification_once(
  p_user_id uuid,
  p_type text,
  p_title text,
  p_body text,
  p_order public.orders
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_inserted_id uuid;
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
  )
  on conflict (user_id, type, related_order_id)
    where related_order_id is not null
  do nothing
  returning id into v_inserted_id;

  if v_inserted_id is null then
    return false;
  end if;

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

  return true;
end;
$$;

revoke all on function public.ticket_024_enqueue_order_notification_once(uuid, text, text, text, public.orders)
  from public, anon, authenticated;
grant execute on function public.ticket_024_enqueue_order_notification_once(uuid, text, text, text, public.orders)
  to service_role;

create or replace function public.ticket_024_store_xendit_invoice(
  p_order_id uuid,
  p_seller_id uuid,
  p_xendit_invoice_id text,
  p_xendit_invoice_url text,
  p_xendit_external_id text,
  p_xendit_status text
)
returns public.orders
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order public.orders;
  v_product_name text;
  v_body text;
begin
  select *
  into v_order
  from public.orders
  where id = p_order_id
  for update;

  if not found then
    raise exception 'Order tidak ditemukan.';
  end if;

  if v_order.seller_id <> p_seller_id then
    raise exception 'Caller bukan seller order ini.';
  end if;

  if v_order.status <> 'pending' then
    raise exception 'Order tidak lagi pending.';
  end if;

  if v_order.xendit_invoice_id is not null and v_order.xendit_invoice_url is not null then
    return v_order;
  end if;

  update public.orders
  set xendit_invoice_id = p_xendit_invoice_id,
      xendit_invoice_url = p_xendit_invoice_url,
      xendit_external_id = p_xendit_external_id,
      xendit_status = p_xendit_status,
      payment_updated_at = now()
  where id = p_order_id
  returning * into v_order;

  select coalesce(nullif(p.name, ''), 'Produk')
  into v_product_name
  from public.products p
  where p.id = v_order.product_id;

  v_body := 'Invoice untuk ' || coalesce(v_product_name, 'Produk') || ' sudah siap dibayar.';

  perform public.ticket_024_enqueue_order_notification_once(
    v_order.buyer_id,
    'order_created',
    'Invoice baru',
    v_body,
    v_order
  );

  perform public.ticket_024_enqueue_order_notification_once(
    v_order.seller_id,
    'order_created',
    'Invoice dibuat',
    v_body,
    v_order
  );

  return v_order;
end;
$$;

revoke all on function public.ticket_024_store_xendit_invoice(uuid, uuid, text, text, text, text)
  from public, anon, authenticated;
grant execute on function public.ticket_024_store_xendit_invoice(uuid, uuid, text, text, text, text)
  to service_role;

create or replace function public.ticket_024_apply_xendit_webhook(
  p_xendit_invoice_id text,
  p_xendit_external_id text,
  p_amount numeric,
  p_currency text,
  p_xendit_status text,
  p_paid_at timestamptz,
  p_expired_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order public.orders;
  v_new_status text;
  v_product_name text;
  v_buyer_title text;
  v_seller_title text;
  v_body text;
begin
  if coalesce(p_xendit_invoice_id, '') = '' and coalesce(p_xendit_external_id, '') = '' then
    raise exception 'Invoice identifier wajib diisi.';
  end if;

  select *
  into v_order
  from public.orders
  where (
      p_xendit_invoice_id is not null
      and xendit_invoice_id = p_xendit_invoice_id
    )
    or (
      p_xendit_external_id is not null
      and xendit_external_id = p_xendit_external_id
    )
  for update;

  if not found then
    raise exception 'Order invoice tidak ditemukan.';
  end if;

  if p_currency is not null and upper(p_currency) <> 'IDR' then
    raise exception 'Currency invoice tidak valid.';
  end if;

  if p_amount is null or p_amount <> v_order.total_amount then
    raise exception 'Amount invoice tidak sesuai.';
  end if;

  v_new_status := case upper(coalesce(p_xendit_status, ''))
    when 'PAID' then 'paid'
    when 'SETTLED' then 'paid'
    when 'EXPIRED' then 'expired'
    when 'CANCELLED' then 'cancelled'
    else null
  end;

  if v_new_status is null then
    update public.orders
    set xendit_status = p_xendit_status,
        payment_updated_at = now()
    where id = v_order.id
    returning * into v_order;

    return jsonb_build_object(
      'changed', false,
      'order_id', v_order.id,
      'status', v_order.status,
      'xendit_status', v_order.xendit_status
    );
  end if;

  if v_order.status <> 'pending' then
    return jsonb_build_object(
      'changed', false,
      'order_id', v_order.id,
      'status', v_order.status,
      'xendit_status', v_order.xendit_status
    );
  end if;

  update public.orders
  set status = v_new_status,
      paid_at = case when v_new_status = 'paid' then coalesce(p_paid_at, now()) else null end,
      expired_at = case when v_new_status = 'expired' then coalesce(p_expired_at, now()) else expired_at end,
      xendit_status = p_xendit_status,
      payment_updated_at = now()
  where id = v_order.id
  returning * into v_order;

  select coalesce(nullif(p.name, ''), 'Produk')
  into v_product_name
  from public.products p
  where p.id = v_order.product_id;

  case v_new_status
    when 'paid' then
      v_buyer_title := 'Pembayaran berhasil';
      v_seller_title := 'Pembayaran diterima';
      v_body := 'Pembayaran untuk ' || coalesce(v_product_name, 'Produk') || ' sudah tercatat.';
    when 'expired' then
      v_buyer_title := 'Invoice kedaluwarsa';
      v_seller_title := 'Invoice kedaluwarsa';
      v_body := 'Invoice untuk ' || coalesce(v_product_name, 'Produk') || ' sudah kedaluwarsa.';
    when 'cancelled' then
      v_buyer_title := 'Invoice dibatalkan';
      v_seller_title := 'Invoice dibatalkan';
      v_body := 'Invoice untuk ' || coalesce(v_product_name, 'Produk') || ' sudah dibatalkan.';
    else
      raise exception 'Status order tidak valid.';
  end case;

  perform public.ticket_024_enqueue_order_notification_once(
    v_order.buyer_id,
    'payment_' || v_new_status,
    v_buyer_title,
    v_body,
    v_order
  );

  perform public.ticket_024_enqueue_order_notification_once(
    v_order.seller_id,
    'payment_' || v_new_status,
    v_seller_title,
    v_body,
    v_order
  );

  return jsonb_build_object(
    'changed', true,
    'order_id', v_order.id,
    'status', v_order.status,
    'xendit_status', v_order.xendit_status
  );
end;
$$;

revoke all on function public.ticket_024_apply_xendit_webhook(text, text, numeric, text, text, timestamptz, timestamptz)
  from public, anon, authenticated;
grant execute on function public.ticket_024_apply_xendit_webhook(text, text, numeric, text, text, timestamptz, timestamptz)
  to service_role;

commit;
