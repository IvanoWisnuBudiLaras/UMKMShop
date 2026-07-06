-- TICKET-025 validation smoke test.
-- Run after ticket-025-phase3-shipping-cost-api.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
values
  ('10000000-0025-0000-0000-000000000001', 'ticket025-owner@example.test', now()),
  ('10000000-0025-0000-0000-000000000002', 'ticket025-other@example.test', now())
on conflict (id) do nothing;

insert into public.profiles (id, name, city)
values
  ('10000000-0025-0000-0000-000000000001', 'Ticket 025 Owner', 'Bandung'),
  ('10000000-0025-0000-0000-000000000002', 'Ticket 025 Other', 'Jakarta')
on conflict (id) do update
set
  name = excluded.name,
  city = excluded.city,
  postal_code = null,
  village_code = null;

do $$
begin
  if not exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'profiles'
      and column_name = 'postal_code'
  ) then
    raise exception 'profiles.postal_code does not exist';
  end if;

  if not exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'profiles'
      and column_name = 'village_code'
  ) then
    raise exception 'profiles.village_code does not exist';
  end if;
end $$;

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0025-0000-0000-000000000001","role":"authenticated","is_anonymous":false}';

update public.profiles
set postal_code = '40513',
    village_code = '3277010001'
where id = '10000000-0025-0000-0000-000000000001';

do $$
declare
  v_postal_code text;
  v_village_code text;
begin
  select postal_code, village_code
  into v_postal_code, v_village_code
  from public.profiles
  where id = '10000000-0025-0000-0000-000000000001';

  if v_postal_code <> '40513' or v_village_code <> '3277010001' then
    raise exception 'owner could not update own shipping address';
  end if;
end $$;

update public.profiles
set postal_code = '40111',
    village_code = '3173010001'
where id = '10000000-0025-0000-0000-000000000002';

do $$
declare
  v_postal_code text;
  v_village_code text;
begin
  select postal_code, village_code
  into v_postal_code, v_village_code
  from public.profiles
  where id = '10000000-0025-0000-0000-000000000002';

  if v_postal_code is not null or v_village_code is not null then
    raise exception 'owner could update another user shipping address';
  end if;
end $$;

set local role postgres;

do $$
begin
  begin
    update public.profiles
    set postal_code = 'abcde'
    where id = '10000000-0025-0000-0000-000000000001';
    raise exception 'invalid postal_code was accepted';
  exception
    when check_violation then null;
  end;

  begin
    update public.profiles
    set village_code = '123'
    where id = '10000000-0025-0000-0000-000000000001';
    raise exception 'invalid village_code was accepted';
  exception
    when check_violation then null;
  end;
end $$;

rollback;
