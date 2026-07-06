# UMKMShop Backend Deployment

The learning backend is a single Kotlin/Ktor process:

- HTTP server for `/health` and OAuth learning endpoints.
- Background notification worker polling Supabase `pgmq` and sending FCM.
- Demo OAuth public client at `/demo`.

It replaces the previous separate `worker` and `oauth-server` runtime services.

## Build

```bash
./gradlew :backend:installDist
```

The runnable distribution is generated at:

```text
backend/build/install/backend
```

## Local Docker

```bash
cp .env.example .env
docker compose --profile runtime up --build backend
```

For local OAuth-only learning, keep `UMKMSHOP_WORKER_ENABLED=false`. For production-like notification processing, set `UMKMSHOP_WORKER_ENABLED=true` and provide `UMKMSHOP_BACKEND_DATABASE_URL` plus Firebase credentials.

## Required Environment

- `UMKMSHOP_BACKEND_PORT`: HTTP port for health and OAuth endpoints.
- `UMKMSHOP_WORKER_ENABLED`: `true` for production notification processing, `false` for HTTP/OAuth-only local development.
- `UMKMSHOP_BACKEND_DATABASE_URL`: Supavisor transaction-mode JDBC URL. Legacy `UMKMSHOP_DATABASE_URL` is still accepted.
- `FIREBASE_SERVICE_ACCOUNT_JSON` or `GOOGLE_APPLICATION_CREDENTIALS`: Firebase Admin credentials for FCM.
- `UMKMSHOP_OAUTH_ISSUER`: public issuer URL for OAuth discovery/JWT.
- `UMKMSHOP_OAUTH_TOKEN_PEPPER`: server-side secret used to hash authorization codes and tokens.

Do not put Firebase service account files, database passwords, service-role credentials, private signing keys, or refresh tokens in Android or the repository.
