-- TICKET-017 validation smoke test.
-- Run after ticket-017-rating-self-attested.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
values
  ('10000000-0017-0000-0000-000000000001', 'ticket017-seller@example.test', now()),
  ('10000000-0017-0000-0000-000000000002', 'ticket017-buyer@example.test', now()),
  ('10000000-0017-0000-0000-000000000003', 'ticket017-other@example.test', now())
on conflict (id) do nothing;

insert into public.profiles (id, name)
values
  ('10000000-0017-0000-0000-000000000001', 'Ticket 017 Seller'),
  ('10000000-0017-0000-0000-000000000002', 'Ticket 017 Buyer'),
  ('10000000-0017-0000-0000-000000000003', 'Ticket 017 Other')
on conflict (id) do update
set name = excluded.name;

insert into public.products (id, seller_id, name, price, description, category, status)
values (
  '20000000-0017-0000-0000-000000000001',
  '10000000-0017-0000-0000-000000000001',
  'Ticket 017 Rated Product',
  17000,
  'Rating smoke',
  'smoke',
  'active'
)
on conflict (id) do update
set seller_id = excluded.seller_id,
    name = excluded.name,
    price = excluded.price,
    status = excluded.status;

insert into public.chat_rooms (id, buyer_id, seller_id, product_id)
values (
  '30000000-0017-0000-0000-000000000001',
  '10000000-0017-0000-0000-000000000002',
  '10000000-0017-0000-0000-000000000001',
  '20000000-0017-0000-0000-000000000001'
)
on conflict (id) do nothing;

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0017-0000-0000-000000000002","role":"authenticated","is_anonymous":false}';

insert into public.reviews (chat_room_id, reviewer_id, seller_id, rating, comment)
values (
  '30000000-0017-0000-0000-000000000001',
  '10000000-0017-0000-0000-000000000002',
  '10000000-0017-0000-0000-000000000001',
  5,
  'Transaksi lancar'
);

do $$
declare
  v_duplicate_blocked boolean := false;
  v_avg numeric;
  v_count int;
begin
  begin
    insert into public.reviews (chat_room_id, reviewer_id, seller_id, rating)
    values (
      '30000000-0017-0000-0000-000000000001',
      '10000000-0017-0000-0000-000000000002',
      '10000000-0017-0000-0000-000000000001',
      4
    );
  exception
    when unique_violation then
      v_duplicate_blocked := true;
  end;

  if not v_duplicate_blocked then
    raise exception 'duplicate review per room was not blocked';
  end if;

  select rating_avg, rating_count
  into v_avg, v_count
  from public.profiles
  where id = '10000000-0017-0000-0000-000000000001';

  if v_avg <> 5.00 or v_count <> 1 then
    raise exception 'aggregate after insert is wrong: avg %, count %', v_avg, v_count;
  end if;
end $$;

set local request.jwt.claims = '{"sub":"10000000-0017-0000-0000-000000000003","role":"authenticated","is_anonymous":false}';

do $$
declare
  v_outsider_blocked boolean := false;
begin
  begin
    insert into public.reviews (chat_room_id, reviewer_id, seller_id, rating)
    values (
      '30000000-0017-0000-0000-000000000001',
      '10000000-0017-0000-0000-000000000003',
      '10000000-0017-0000-0000-000000000001',
      4
    );
  exception
    when insufficient_privilege or check_violation or with_check_option_violation or raise_exception then
      v_outsider_blocked := true;
  end;

  if not v_outsider_blocked then
    raise exception 'outside user could review seller without valid chat room';
  end if;
end $$;

set local request.jwt.claims = '{"sub":"10000000-0017-0000-0000-000000000002","role":"authenticated","is_anonymous":false}';

update public.reviews
set rating = 3,
    comment = 'Diubah setelah dipikir ulang'
where chat_room_id = '30000000-0017-0000-0000-000000000001'
  and reviewer_id = '10000000-0017-0000-0000-000000000002';

do $$
declare
  v_avg numeric;
  v_count int;
begin
  select rating_avg, rating_count
  into v_avg, v_count
  from public.profiles
  where id = '10000000-0017-0000-0000-000000000001';

  if v_avg <> 3.00 or v_count <> 1 then
    raise exception 'aggregate after update is wrong: avg %, count %', v_avg, v_count;
  end if;
end $$;

delete from public.reviews
where chat_room_id = '30000000-0017-0000-0000-000000000001'
  and reviewer_id = '10000000-0017-0000-0000-000000000002';

do $$
declare
  v_avg numeric;
  v_count int;
begin
  select rating_avg, rating_count
  into v_avg, v_count
  from public.profiles
  where id = '10000000-0017-0000-0000-000000000001';

  if v_avg <> 0 or v_count <> 0 then
    raise exception 'aggregate after delete is wrong: avg %, count %', v_avg, v_count;
  end if;
end $$;

rollback;
