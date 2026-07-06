-- OAuth tables for learning track
create table if not exists public.oauth_clients (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique,
  client_name text not null,
  client_type text not null check (client_type in ('public', 'confidential')),
  client_secret_hash text,
  redirect_uris text[] not null,
  allowed_scopes text[] not null default array['openid', 'email', 'profile'],
  created_at timestamptz not null default now(),
  check ((client_type = 'public' and client_secret_hash is null) or (client_type = 'confidential' and client_secret_hash is not null))
);

create table if not exists public.oauth_authorization_codes (
  id uuid primary key default gen_random_uuid(),
  code_hash text not null unique,
  client_id text not null references public.oauth_clients(client_id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  redirect_uri text not null,
  scope text not null,
  code_challenge text not null,
  code_challenge_method text not null check (code_challenge_method = 'S256'),
  nonce text,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.oauth_refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  token_hash text not null unique,
  client_id text not null references public.oauth_clients(client_id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  scope text not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  replaced_by uuid references public.oauth_refresh_tokens(id),
  created_at timestamptz not null default now()
);

alter table public.oauth_clients enable row level security;
alter table public.oauth_authorization_codes enable row level security;
alter table public.oauth_refresh_tokens enable row level security;
