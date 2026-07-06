-- Final grants for Data API
grant usage on schema public to anon, authenticated;
grant all on all tables in schema public to postgres, service_role;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant select on all tables in schema public to anon;

-- Explicit grants for functions called by client
grant execute on function public.refresh_seller_rating_aggregate(uuid) to authenticated;
