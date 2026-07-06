-- TICKET-018 validation smoke test.
-- Run after ticket-018-phase2-location-filter.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
values
  ('10000000-0018-0000-0000-000000000001', 'ticket018-bandung-seller@example.test', now()),
  ('10000000-0018-0000-0000-000000000002', 'ticket018-jakarta-seller@example.test', now()),
  ('10000000-0018-0000-0000-000000000003', 'ticket018-buyer@example.test', now())
on conflict (id) do nothing;

insert into public.profiles (id, name, city)
values
  ('10000000-0018-0000-0000-000000000001', 'Ticket 018 Bandung Seller', 'Bandung'),
  ('10000000-0018-0000-0000-000000000002', 'Ticket 018 Jakarta Seller', 'Jakarta'),
  ('10000000-0018-0000-0000-000000000003', 'Ticket 018 Buyer', null)
on conflict (id) do update
set
  name = excluded.name,
  city = excluded.city;

insert into public.products (id, seller_id, name, price, description, category, status)
select
  gen_random_uuid(),
  case when series_id % 2 = 0
    then '10000000-0018-0000-0000-000000000001'::uuid
    else '10000000-0018-0000-0000-000000000002'::uuid
  end,
  'Ticket 018 Product ' || series_id,
  18000 + series_id,
  'Location filter smoke',
  'smoke-location',
  case when series_id % 5 = 0 then 'inactive' else 'active' end
from generate_series(1, 2500) as series_id;

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0018-0000-0000-000000000001","role":"authenticated","is_anonymous":false}';

update public.profiles
set city = 'Cimahi'
where id = '10000000-0018-0000-0000-000000000001';

do $$
declare
  v_city text;
begin
  select city
  into v_city
  from public.profiles
  where id = '10000000-0018-0000-0000-000000000001';

  if v_city <> 'Cimahi' then
    raise exception 'seller could not update own city';
  end if;
end $$;

update public.profiles
set city = 'Surabaya'
where id = '10000000-0018-0000-0000-000000000002';

do $$
declare
  v_city text;
begin
  select city
  into v_city
  from public.profiles
  where id = '10000000-0018-0000-0000-000000000002';

  if v_city <> 'Jakarta' then
    raise exception 'seller could update another user city';
  end if;
end $$;

set local request.jwt.claims = '{"sub":"10000000-0018-0000-0000-000000000003","role":"authenticated","is_anonymous":false}';

do $$
declare
  v_cimahi_active_count int;
  v_cimahi_inactive_count int;
  v_jakarta_active_count int;
begin
  select count(*)
  into v_cimahi_active_count
  from public.products p
  join public.profiles seller on seller.id = p.seller_id
  where p.status = 'active'
    and seller.city = 'Cimahi';

  select count(*)
  into v_cimahi_inactive_count
  from public.products p
  join public.profiles seller on seller.id = p.seller_id
  where p.status <> 'active'
    and seller.city = 'Cimahi';

  select count(*)
  into v_jakarta_active_count
  from public.products p
  join public.profiles seller on seller.id = p.seller_id
  where p.status = 'active'
    and seller.city = 'Jakarta';

  if v_cimahi_active_count <> 1000 then
    raise exception 'Cimahi active product count mismatch: %', v_cimahi_active_count;
  end if;

  if v_cimahi_inactive_count <> 0 then
    raise exception 'inactive Cimahi products are visible to buyer: %', v_cimahi_inactive_count;
  end if;

  if v_jakarta_active_count <> 1000 then
    raise exception 'Jakarta active product count mismatch: %', v_jakarta_active_count;
  end if;
end $$;

explain (analyze, buffers)
select p.id
from public.products p
join public.profiles seller on seller.id = p.seller_id
where p.status = 'active'
  and seller.city = 'Cimahi'
order by p.created_at desc
limit 12;

rollback;
