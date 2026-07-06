-- TICKET-019 validation smoke test.
-- Run after ticket-019-search-name-trgm.sql with an owner role.
-- This script rolls back all dummy rows.

begin;

insert into auth.users (id, email, email_confirmed_at)
select
  ('10000000-0019-0000-0000-' || lpad(series_id::text, 12, '0'))::uuid,
  'ticket019-seller-' || series_id || '@example.test',
  now()
from generate_series(1, 20) as series_id
on conflict (id) do nothing;

insert into public.profiles (id, name, city)
select
  ('10000000-0019-0000-0000-' || lpad(series_id::text, 12, '0'))::uuid,
  'Ticket 019 Seller ' || series_id,
  case when series_id % 4 = 0 then 'Bandung'
       when series_id % 4 = 1 then 'Jakarta'
       when series_id % 4 = 2 then 'Surabaya'
       else 'Yogyakarta'
  end
from generate_series(1, 20) as series_id
on conflict (id) do update
set
  name = excluded.name,
  city = excluded.city;

insert into public.products (id, seller_id, name, price, description, category, status, created_at, updated_at)
select
  gen_random_uuid(),
  ('10000000-0019-0000-0000-' || lpad(((series_id % 20) + 1)::text, 12, '0'))::uuid,
  case
    when series_id % 10 = 0 then 'Keripik Pisang Premium ' || series_id
    when series_id % 10 = 1 then 'Kopi Robusta UMKM ' || series_id
    when series_id % 10 = 2 then 'Beras Organik Lokal ' || series_id
    when series_id % 10 = 3 then 'Sambal Bawang Botol ' || series_id
    when series_id % 10 = 4 then 'Tas Anyaman Handmade ' || series_id
    when series_id % 10 = 5 then 'Kue Kering Lebaran ' || series_id
    when series_id % 10 = 6 then 'Madu Hutan Murni ' || series_id
    when series_id % 10 = 7 then 'Batik Tulis Desa ' || series_id
    when series_id % 10 = 8 then 'Sabun Herbal Wangi ' || series_id
    else 'Frozen Food Rumahan ' || series_id
  end,
  10000 + (series_id % 500) * 1000,
  'Ticket 019 dummy product for catalog search validation ' || series_id,
  case
    when series_id % 5 = 0 then 'Makanan'
    when series_id % 5 = 1 then 'Minuman'
    when series_id % 5 = 2 then 'Sembako'
    when series_id % 5 = 3 then 'Fashion'
    else 'Kerajinan'
  end,
  case when series_id % 8 = 0 then 'inactive' else 'active' end,
  now() - (series_id || ' minutes')::interval,
  now() - (series_id || ' minutes')::interval
from generate_series(1, 25000) as series_id;

-- Bulk dummy inserts can sit in the GIN pending list inside this validation
-- transaction. Flush it so EXPLAIN reflects the steady-state index path.
select gin_clean_pending_list('public.idx_products_active_name_trgm'::regclass);

analyze public.products;
analyze public.profiles;

do $$
declare
  v_active_matches int;
  v_inactive_matches int;
begin
  select count(*)
  into v_active_matches
  from public.products
  where status = 'active'
    and name ilike '%beras%';

  select count(*)
  into v_inactive_matches
  from public.products
  where status <> 'active'
    and name ilike '%beras%';

  if v_active_matches <> 1875 then
    raise exception 'active beras search count mismatch: %', v_active_matches;
  end if;

  if v_inactive_matches <> 625 then
    raise exception 'dummy dataset mismatch for inactive beras count: %', v_inactive_matches;
  end if;

  if exists (
    select 1
    from public.products
    where status <> 'active'
      and name ilike '%beras%'
    limit 1
  ) then
    -- Inactive rows exist in the dataset, but the app query below must exclude them.
    null;
  end if;
end $$;

explain (analyze, buffers)
select p.id, p.name, p.price, p.category, p.seller_id, p.created_at
from public.products p
where p.status = 'active'
  and p.name ilike '%beras%'
order by p.created_at desc
limit 13;

explain (analyze, buffers)
select p.id, p.name, p.price, p.category, p.seller_id, p.created_at
from public.products p
where p.status = 'active'
  and p.name ilike '%zzztidakada%'
order by p.created_at desc
limit 13;

rollback;
