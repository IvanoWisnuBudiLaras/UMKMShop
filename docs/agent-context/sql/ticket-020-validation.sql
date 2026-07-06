-- TICKET-020 validation smoke test.
-- Run after ticket-020-phase2-product-reports.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
values
  ('10000000-0020-0000-0000-000000000001', 'ticket020-seller@example.test', now()),
  ('10000000-0020-0000-0000-000000000002', 'ticket020-reporter@example.test', now())
on conflict (id) do nothing;

insert into public.profiles (id, name)
values
  ('10000000-0020-0000-0000-000000000001', 'Ticket 020 Seller'),
  ('10000000-0020-0000-0000-000000000002', 'Ticket 020 Reporter')
on conflict (id) do update
set name = excluded.name;

insert into public.products (id, seller_id, name, price, description, category, status)
values
  (
    '20000000-0020-0000-0000-000000000001',
    '10000000-0020-0000-0000-000000000001',
    'Ticket 020 Active Product',
    20000,
    'Report smoke active',
    'smoke',
    'active'
  ),
  (
    '20000000-0020-0000-0000-000000000002',
    '10000000-0020-0000-0000-000000000001',
    'Ticket 020 Inactive Product',
    21000,
    'Report smoke inactive',
    'smoke',
    'inactive'
  );

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0020-0000-0000-000000000002","role":"authenticated","is_anonymous":false}';

insert into public.product_reports (product_id, reporter_id, reason)
values (
  '20000000-0020-0000-0000-000000000001',
  '10000000-0020-0000-0000-000000000002',
  'Produk terlihat spam'
);

do $$
declare
  v_status text;
  v_duplicate_blocked boolean := false;
  v_inactive_blocked boolean := false;
  v_update_blocked boolean := false;
begin
  select status
  into v_status
  from public.product_reports
  where product_id = '20000000-0020-0000-0000-000000000001'
    and reporter_id = '10000000-0020-0000-0000-000000000002'
    and reason = 'Produk terlihat spam';

  if v_status <> 'pending' then
    raise exception 'report status default is not pending';
  end if;

  begin
    insert into public.product_reports (product_id, reporter_id, reason)
    values (
      '20000000-0020-0000-0000-000000000001',
      '10000000-0020-0000-0000-000000000002',
      'Produk terlihat spam'
    );
  exception
    when unique_violation then
      v_duplicate_blocked := true;
  end;

  if not v_duplicate_blocked then
    raise exception 'duplicate report was not blocked';
  end if;

  begin
    insert into public.product_reports (product_id, reporter_id, reason)
    values (
      '20000000-0020-0000-0000-000000000002',
      '10000000-0020-0000-0000-000000000002',
      'Produk inactive'
    );
  exception
    when insufficient_privilege or check_violation or with_check_option_violation then
      v_inactive_blocked := true;
  end;

  if not v_inactive_blocked then
    raise exception 'inactive product report was not blocked';
  end if;

  begin
    update public.product_reports
    set status = 'reviewed'
    where product_id = '20000000-0020-0000-0000-000000000001'
      and reporter_id = '10000000-0020-0000-0000-000000000002';
  exception
    when insufficient_privilege then
      v_update_blocked := true;
  end;

  if not v_update_blocked then
    raise exception 'authenticated user could update report status';
  end if;
end $$;

reset role;

update public.product_reports
set status = 'reviewed'
where product_id = '20000000-0020-0000-0000-000000000001'
  and reporter_id = '10000000-0020-0000-0000-000000000002';

do $$
declare
  v_status text;
begin
  select status
  into v_status
  from public.product_reports
  where product_id = '20000000-0020-0000-0000-000000000001'
    and reporter_id = '10000000-0020-0000-0000-000000000002';

  if v_status <> 'reviewed' then
    raise exception 'owner/manual SQL could not update report status';
  end if;
end $$;

rollback;
