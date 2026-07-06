-- TICKET-016 validation smoke test.
-- Run after ticket-016-phase2-wishlist.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
values
  ('10000000-0016-0000-0000-000000000001', 'ticket016-seller@example.test', now()),
  ('10000000-0016-0000-0000-000000000002', 'ticket016-buyer@example.test', now()),
  ('10000000-0016-0000-0000-000000000003', 'ticket016-other@example.test', now());

insert into public.profiles (id, name)
values
  ('10000000-0016-0000-0000-000000000001', 'Ticket 016 Seller'),
  ('10000000-0016-0000-0000-000000000002', 'Ticket 016 Buyer'),
  ('10000000-0016-0000-0000-000000000003', 'Ticket 016 Other')
on conflict (id) do update
set name = excluded.name;

insert into public.products (id, seller_id, name, price, description, category, status)
values
  (
    '20000000-0016-0000-0000-000000000001',
    '10000000-0016-0000-0000-000000000001',
    'Ticket 016 Active Product',
    16000,
    'Wishlist smoke active',
    'smoke',
    'active'
  ),
  (
    '20000000-0016-0000-0000-000000000002',
    '10000000-0016-0000-0000-000000000001',
    'Ticket 016 Inactive Product',
    17000,
    'Wishlist smoke inactive',
    'smoke',
    'inactive'
  );

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0016-0000-0000-000000000002","role":"authenticated","is_anonymous":false}';

insert into public.wishlists (user_id, product_id)
values (
  '10000000-0016-0000-0000-000000000002',
  '20000000-0016-0000-0000-000000000001'
);

do $$
declare
  v_duplicate_blocked boolean := false;
  v_other_insert_blocked boolean := false;
  v_inactive_insert_blocked boolean := false;
begin
  begin
    insert into public.wishlists (user_id, product_id)
    values (
      '10000000-0016-0000-0000-000000000002',
      '20000000-0016-0000-0000-000000000001'
    );
  exception
    when unique_violation then
      v_duplicate_blocked := true;
  end;

  if not v_duplicate_blocked then
    raise exception 'duplicate wishlist was not blocked';
  end if;

  begin
    insert into public.wishlists (user_id, product_id)
    values (
      '10000000-0016-0000-0000-000000000002',
      '20000000-0016-0000-0000-000000000002'
    );
  exception
    when insufficient_privilege or check_violation or with_check_option_violation then
      v_inactive_insert_blocked := true;
  end;

  if not v_inactive_insert_blocked then
    raise exception 'inactive product wishlist insert was not blocked';
  end if;

  begin
    insert into public.wishlists (user_id, product_id)
    values (
      '10000000-0016-0000-0000-000000000003',
      '20000000-0016-0000-0000-000000000001'
    );
  exception
    when insufficient_privilege or check_violation or with_check_option_violation then
      v_other_insert_blocked := true;
  end;

  if not v_other_insert_blocked then
    raise exception 'buyer could insert wishlist for another user';
  end if;
end $$;

set local request.jwt.claims = '{"sub":"10000000-0016-0000-0000-000000000003","role":"authenticated","is_anonymous":false}';

do $$
declare
  v_visible_count int;
begin
  select count(*)
  into v_visible_count
  from public.wishlists
  where user_id = '10000000-0016-0000-0000-000000000002';

  if v_visible_count <> 0 then
    raise exception 'other user can read buyer wishlist rows';
  end if;
end $$;

reset role;

update public.products
set status = 'inactive'
where id = '20000000-0016-0000-0000-000000000001';

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0016-0000-0000-000000000002","role":"authenticated","is_anonymous":false}';

do $$
declare
  v_visible_count int;
begin
  select count(*)
  into v_visible_count
  from public.wishlists w
  join public.products p on p.id = w.product_id
  where w.user_id = '10000000-0016-0000-0000-000000000002'
    and p.status = 'active';

  if v_visible_count <> 0 then
    raise exception 'inactive favorited product is still returned by active favorites query';
  end if;
end $$;

delete from public.wishlists
where user_id = '10000000-0016-0000-0000-000000000002'
  and product_id = '20000000-0016-0000-0000-000000000001';

do $$
begin
  if exists (
    select 1
    from public.wishlists
    where user_id = '10000000-0016-0000-0000-000000000002'
      and product_id = '20000000-0016-0000-0000-000000000001'
  ) then
    raise exception 'buyer could not delete own wishlist row';
  end if;
end $$;

rollback;
