-- TICKET-018: Phase 2 Location Filter
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

alter table public.profiles
  add column if not exists city text;

create index if not exists idx_profiles_city
  on public.profiles(city)
  where city is not null and city <> '';

create index if not exists idx_profiles_city_lower
  on public.profiles(lower(city))
  where city is not null and city <> '';

commit;
