-- TICKET-020: Phase 2 Product Reports
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

create table if not exists public.product_reports (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  constraint product_reports_reason_not_blank check (btrim(reason) <> ''),
  constraint product_reports_status_check check (status in ('pending', 'reviewed', 'dismissed')),
  constraint product_reports_product_reporter_reason_unique unique (product_id, reporter_id, reason)
);

create index if not exists idx_product_reports_status_created
  on public.product_reports(status, created_at desc);

create index if not exists idx_product_reports_product
  on public.product_reports(product_id);

create index if not exists idx_product_reports_reporter
  on public.product_reports(reporter_id);

alter table public.product_reports enable row level security;

revoke all on table public.product_reports from anon;
revoke all on table public.product_reports from authenticated;

grant select, insert on table public.product_reports to authenticated;
grant select, insert, update, delete on table public.product_reports to service_role;

drop policy if exists "product_reports_insert_authenticated_active_product" on public.product_reports;
drop policy if exists "product_reports_select_own" on public.product_reports;

create policy "product_reports_insert_authenticated_active_product" on public.product_reports
  for insert to authenticated
  with check (
    reporter_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and status = 'pending'
    and exists (
      select 1
      from public.products p
      where p.id = product_id
        and p.status = 'active'
    )
  );

create policy "product_reports_select_own" on public.product_reports
  for select to authenticated
  using (
    reporter_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
  );

commit;
