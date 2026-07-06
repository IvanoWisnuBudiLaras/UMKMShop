-- TICKET-017: Phase 2 Rating Self-Attested
-- Run in Supabase SQL Editor or via Supabase MCP with an owner role.

begin;

alter table public.profiles
  add column if not exists rating_avg numeric(3,2) not null default 0,
  add column if not exists rating_count int not null default 0;

create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references public.chat_rooms(id) on delete cascade,
  reviewer_id uuid not null references public.profiles(id) on delete cascade,
  seller_id uuid not null references public.profiles(id) on delete cascade,
  rating int not null check (rating between 1 and 5),
  comment text,
  self_attested boolean not null default true check (self_attested),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint reviews_room_reviewer_unique unique (chat_room_id, reviewer_id),
  constraint reviews_reviewer_not_seller check (reviewer_id <> seller_id)
);

create index if not exists idx_reviews_seller_created
  on public.reviews(seller_id, created_at desc);

create index if not exists idx_reviews_room
  on public.reviews(chat_room_id);

create index if not exists idx_reviews_reviewer
  on public.reviews(reviewer_id);

alter table public.reviews enable row level security;

revoke all on table public.reviews from anon;
revoke all on table public.reviews from authenticated;

grant select, insert, update, delete on table public.reviews to authenticated;
grant select, insert, update, delete on table public.reviews to service_role;

create or replace function public.validate_review_chat_room()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_buyer_id uuid;
  v_seller_id uuid;
begin
  select cr.buyer_id, cr.seller_id
  into v_buyer_id, v_seller_id
  from public.chat_rooms cr
  where cr.id = new.chat_room_id;

  if v_buyer_id is null then
    raise exception 'Review membutuhkan chat room yang valid.';
  end if;

  if new.reviewer_id <> v_buyer_id then
    raise exception 'Hanya pembeli di chat room ini yang bisa memberi rating.';
  end if;

  if new.seller_id <> v_seller_id then
    raise exception 'Seller review harus sesuai dengan seller di chat room.';
  end if;

  if new.comment is not null then
    new.comment = nullif(trim(new.comment), '');
  end if;
  new.updated_at = now();

  return new;
end;
$$;

revoke all on function public.validate_review_chat_room() from public;
revoke all on function public.validate_review_chat_room() from anon;
revoke all on function public.validate_review_chat_room() from authenticated;

drop trigger if exists trg_validate_review_chat_room on public.reviews;
create trigger trg_validate_review_chat_room
  before insert or update of chat_room_id, reviewer_id, seller_id, comment
  on public.reviews
  for each row execute function public.validate_review_chat_room();

create or replace function public.refresh_seller_rating_aggregate(p_seller_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  update public.profiles p
  set
    rating_avg = coalesce((
      select round(avg(r.rating)::numeric, 2)
      from public.reviews r
      where r.seller_id = p_seller_id
    ), 0),
    rating_count = (
      select count(*)::int
      from public.reviews r
      where r.seller_id = p_seller_id
    )
  where p.id = p_seller_id;
end;
$$;

revoke all on function public.refresh_seller_rating_aggregate(uuid) from public;
revoke all on function public.refresh_seller_rating_aggregate(uuid) from anon;
revoke all on function public.refresh_seller_rating_aggregate(uuid) from authenticated;

create or replace function public.handle_review_rating_aggregate()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'DELETE' then
    perform public.refresh_seller_rating_aggregate(old.seller_id);
    return old;
  end if;

  perform public.refresh_seller_rating_aggregate(new.seller_id);
  if tg_op = 'UPDATE' and old.seller_id <> new.seller_id then
    perform public.refresh_seller_rating_aggregate(old.seller_id);
  end if;

  return new;
end;
$$;

revoke all on function public.handle_review_rating_aggregate() from public;
revoke all on function public.handle_review_rating_aggregate() from anon;
revoke all on function public.handle_review_rating_aggregate() from authenticated;

drop trigger if exists trg_reviews_rating_aggregate on public.reviews;
create trigger trg_reviews_rating_aggregate
  after insert or update of seller_id, rating or delete
  on public.reviews
  for each row execute function public.handle_review_rating_aggregate();

drop policy if exists "reviews_select_participant" on public.reviews;
drop policy if exists "reviews_insert_self_attested_buyer" on public.reviews;
drop policy if exists "reviews_update_own" on public.reviews;
drop policy if exists "reviews_delete_own" on public.reviews;

create policy "reviews_select_participant" on public.reviews
  for select to authenticated
  using (
    coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and (
      reviewer_id = (select auth.uid())
      or seller_id = (select auth.uid())
    )
  );

create policy "reviews_insert_self_attested_buyer" on public.reviews
  for insert to authenticated
  with check (
    reviewer_id = (select auth.uid())
    and reviewer_id <> seller_id
    and self_attested = true
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and exists (
      select 1
      from public.chat_rooms cr
      where cr.id = chat_room_id
        and cr.buyer_id = (select auth.uid())
        and cr.seller_id = seller_id
    )
  );

create policy "reviews_update_own" on public.reviews
  for update to authenticated
  using (
    reviewer_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
  )
  with check (
    reviewer_id = (select auth.uid())
    and reviewer_id <> seller_id
    and self_attested = true
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
    and exists (
      select 1
      from public.chat_rooms cr
      where cr.id = chat_room_id
        and cr.buyer_id = (select auth.uid())
        and cr.seller_id = seller_id
    )
  );

create policy "reviews_delete_own" on public.reviews
  for delete to authenticated
  using (
    reviewer_id = (select auth.uid())
    and coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false) = false
  );

commit;
