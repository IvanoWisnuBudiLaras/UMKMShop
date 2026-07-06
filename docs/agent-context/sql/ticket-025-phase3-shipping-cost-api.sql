-- TICKET-025: Phase 3 Shipping Cost API
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

alter table public.profiles
  add column if not exists postal_code text,
  add column if not exists village_code text;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'profiles_postal_code_format'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_postal_code_format
      check (postal_code is null or postal_code ~ '^[0-9]{5}$');
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'profiles_village_code_format'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_village_code_format
      check (village_code is null or village_code ~ '^[0-9]{10}$');
  end if;
end $$;

create index if not exists idx_profiles_village_code
  on public.profiles(village_code)
  where village_code is not null and village_code <> '';

commit;
