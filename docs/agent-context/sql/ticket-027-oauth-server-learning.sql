-- TICKET-027 OAuth Server UMKMShop learning track.
-- Design artifact only in this ticket run; not applied to Supabase from the agent environment.
-- OAuth tables are server-side only. Do not expose them to Android clients.

create table if not exists oauth_clients (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique,
  client_name text not null,
  client_type text not null check (client_type in ('public', 'confidential')),
  client_secret_hash text,
  redirect_uris text[] not null,
  allowed_scopes text[] not null default array['openid', 'email', 'profile'],
  created_at timestamptz not null default now(),
  check (
    (client_type = 'public' and client_secret_hash is null)
    or (client_type = 'confidential' and client_secret_hash is not null)
  )
);

create table if not exists oauth_authorization_codes (
  id uuid primary key default gen_random_uuid(),
  code_hash text not null unique,
  client_id text not null references oauth_clients(client_id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  redirect_uri text not null,
  scope text not null,
  code_challenge text not null,
  code_challenge_method text not null check (code_challenge_method = 'S256'),
  nonce text,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists oauth_refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  token_hash text not null unique,
  client_id text not null references oauth_clients(client_id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  scope text not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  replaced_by uuid references oauth_refresh_tokens(id),
  created_at timestamptz not null default now()
);

create table if not exists oauth_consents (
  id uuid primary key default gen_random_uuid(),
  client_id text not null references oauth_clients(client_id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  scope text not null,
  approved_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique (client_id, user_id, scope)
);

create index if not exists idx_oauth_authorization_codes_client_expires
  on oauth_authorization_codes(client_id, expires_at);

create index if not exists idx_oauth_refresh_tokens_user_client
  on oauth_refresh_tokens(user_id, client_id);

create index if not exists idx_oauth_refresh_tokens_active
  on oauth_refresh_tokens(user_id, client_id)
  where revoked_at is null;

alter table oauth_clients enable row level security;
alter table oauth_authorization_codes enable row level security;
alter table oauth_refresh_tokens enable row level security;
alter table oauth_consents enable row level security;

-- No anon/authenticated policies on purpose.
-- OAuth Server accesses these tables only from trusted server-side code.
