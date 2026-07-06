-- TICKET-019: Phase 2 Search Optimization
-- Decision: use pg_trgm only for active product names.
-- Category remains a compact exact filter in the Android catalog UI.

create extension if not exists pg_trgm with schema extensions;

create index if not exists idx_products_active_name_trgm
  on public.products using gin (name extensions.gin_trgm_ops)
  where status = 'active';
