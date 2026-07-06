# TICKET-027: Phase 3 Google OAuth Native and OAuth Server Learning

## Tujuan

Membuat jalur pembelajaran identity platform: Android tetap login dengan Google OAuth native/Supabase Auth, lalu UMKMShop memiliki OAuth Server learning/demo sendiri yang bisa dipakai demo client pihak ketiga untuk menjalankan Authorization Code + PKCE sampai mendapatkan token.

Ticket ini **bukan** prasyarat payment, ongkir, atau E2E marketplace. Kerjakan hanya kalau user eksplisit menyebut `TICKET-027`.

## Scope

- Dokumentasikan konfigurasi Google OAuth native Android:
  - package name `com.application.umkmshop`;
  - SHA-1 debug dan release;
  - Android OAuth Client ID;
  - Supabase Google provider/client IDs.
- Buat OAuth Server UMKMShop sebagai route HTTP di backend Kotlin/Ktor tunggal bersama background notification worker.
- Gunakan existing Supabase Auth session sebagai login source untuk consent screen.
- Implement Authorization Code + PKCE.
- Implement Refresh Token flow dengan rotation.
- Implement OpenID Connect discovery dan JWKS.
- Implement demo client public untuk uji flow end-to-end.

## Non-Scope

- Tidak mengganti auth utama Android dari Supabase Auth.
- Tidak membuat partner integration production.
- Tidak membuat `password` grant.
- Tidak membuat `client_credentials` grant.
- Tidak menaruh client secret/private signing key di Android atau repo.
- Tidak menjadikan OAuth Server learning sebagai auth utama Android.

## Endpoint Minimum

| Endpoint | Tujuan |
|---|---|
| `GET /.well-known/openid-configuration` | Metadata issuer, endpoints, scopes, signing alg |
| `GET /oauth/authorize` | Validasi request OAuth dan tampilkan/redirect consent |
| `POST /oauth/token` | Exchange authorization code + PKCE verifier, dan refresh token |
| `GET /oauth/userinfo` | Return user claims sesuai scope |
| `GET /oauth/jwks.json` | Public keys untuk verifikasi JWT |
| `POST /oauth/revoke` | Revoke refresh token/grant |

## Data Model Minimum

- `oauth_clients`
  - `client_id`
  - `client_name`
  - `client_type`: `public` atau `confidential`
  - `client_secret_hash` nullable untuk public client
  - `redirect_uris` exact list
  - `allowed_scopes`
- `oauth_authorization_codes`
  - `code_hash`
  - `client_id`
  - `user_id`
  - `redirect_uri`
  - `scope`
  - `code_challenge`
  - `code_challenge_method = S256`
  - `nonce`
  - `expires_at`
  - `consumed_at`
- `oauth_refresh_tokens`
  - `token_hash`
  - `client_id`
  - `user_id`
  - `scope`
  - `expires_at`
  - `revoked_at`
  - `replaced_by`

## Acceptance Criteria

- Demo public client bisa mulai flow dari `/oauth/authorize`.
- Server menolak `redirect_uri` yang tidak exact match.
- Server menolak request tanpa PKCE `S256`.
- User login via Google OAuth/Email sebelum bisa approve consent.
- Consent screen menampilkan client name, redirect URI, dan scopes.
- Authorization code hanya bisa dipakai sekali.
- Authorization code expired ditolak.
- PKCE verifier salah ditolak.
- `/oauth/token` berhasil menerbitkan access token, refresh token, dan ID token untuk scope `openid`.
- JWT bisa diverifikasi dari `/oauth/jwks.json`.
- `/oauth/userinfo` hanya menerima access token valid.
- Refresh token rotation berjalan: token lama tidak bisa dipakai ulang setelah token baru diterbitkan.
- `/oauth/revoke` membuat refresh token tidak bisa dipakai lagi.
- Secret client confidential, refresh token, dan authorization code disimpan hashed.
- Private signing key dan secret tidak masuk repo, Android, atau log.

## Catatan Implementasi

- Untuk pembelajaran, mulai dari satu demo public client dulu.
- Gunakan HTTPS untuk issuer realistis; local dev boleh memakai localhost/tunnel selama jelas bukan production.
- Access token pendek, misalnya 15 menit.
- Authorization code pendek, misalnya 5-10 menit.
- Refresh token boleh lebih panjang, tetapi wajib rotatable.
- Simpan audit minimal: client, user, scope, approve/deny, issued/revoked timestamp.
- Pakai asymmetric signing key untuk JWT agar JWKS bisa diverifikasi client tanpa private key.
- Jangan memasukkan claim otorisasi dari `user_metadata`; jika perlu claim authz, gunakan data server-side/app metadata yang tidak user-editable.

## Validasi

- [x] `./gradlew --no-configuration-cache :app:signingReport` menghasilkan SHA-1 debug yang dicatat.
- [ ] Google OAuth native Android smoke berhasil dari device/emulator.
- [x] Demo OAuth client public berhasil Authorization Code + PKCE.
- [x] Invalid redirect URI ditolak.
- [x] Bad PKCE verifier ditolak.
- [x] Reused authorization code ditolak.
- [x] Expired authorization code ditolak.
- [x] Revoked refresh token ditolak.
- [x] JWT diverifikasi memakai JWKS.
- [x] Secret scan repo/source tidak menemukan private signing key, client secret, refresh token, atau service role key bernilai nyata.

## Status Implementasi 2026-07-04

- Audit Android auth: kode Android saat ini masih Email/Password via Supabase Auth (`AuthRepository`/`AuthViewModel`). Implementasi Google OAuth native/Credential Manager belum ada di source; Email/Password tidak dihapus.
- Dokumentasi Google OAuth native ditambahkan di `docs/agent-context/references/google-oauth-native-android.md`. Debug SHA-1: `A9:F6:E3:5B:B9:9E:BD:7E:5E:F0:FD:08:07:A5:B8:B4:7B:61:1E:C4`. Release SHA-1 belum tersedia karena release signing `Config: null`.
- Data model OAuth Server didesain di `docs/agent-context/sql/ticket-027-oauth-server-learning.sql`: `oauth_clients`, `oauth_authorization_codes`, `oauth_refresh_tokens`, dan `oauth_consents`, dengan authorization code / refresh token / confidential client secret berbasis hash.
- Modul `:oauth-server` dan `:worker` digabung menjadi `:backend`; backend tunggal menjalankan HTTP OAuth learning, `/health`, dan notification worker sebagai coroutine/background job.
- Endpoint minimum tersedia: `/.well-known/openid-configuration`, `/oauth/authorize`, `/oauth/token`, `/oauth/userinfo`, `/oauth/jwks.json`, dan `/oauth/revoke`.
- Demo public client tersedia di `/demo` dan memakai Authorization Code + PKCE S256 tanpa client secret.
- JWT access token dan ID token memakai RS256 dengan asymmetric key yang digenerate runtime untuk demo lokal; public key diekspos di `/oauth/jwks.json`.
- Grant yang tersedia hanya `authorization_code` dan `refresh_token`; `password` dan `client_credentials` tidak diimplementasikan.

## Validasi 2026-07-04

- `./gradlew --no-configuration-cache :app:signingReport` berhasil; debug SHA-1/SHA-256 dicatat, release signing belum tersedia.
- `./gradlew --no-configuration-cache :oauth-server:test` berhasil.
- `./gradlew --no-configuration-cache :oauth-server:installDist` berhasil.
- Smoke HTTP lokal dengan `UMKMSHOP_OAUTH_TOKEN_PEPPER=local-test-pepper oauth-server/build/install/oauth-server/bin/oauth-server` berhasil:
  - discovery endpoint mengembalikan metadata issuer/endpoints/grants;
  - JWKS endpoint mengembalikan RSA public key;
  - `/demo` mengembalikan demo public client;
  - invalid redirect URI mengembalikan `400 invalid_request`;
  - flow approve + token exchange + `/oauth/userinfo` berhasil untuk demo user lokal.
- `./gradlew --no-configuration-cache :oauth-server:test :worker:test` berhasil.
- `./gradlew --no-configuration-cache :app:assembleDebug :app:testDebugUnitTest` belum berhasil karena compile error di luar scope TICKET-027: `app/src/main/java/com/application/umkmshop/data/order/OrderRepository.kt:140:13 No value passed for parameter 'xenditInvoiceId'.`
- Source secret scan TICKET-027 tidak menemukan private signing key atau secret nyata; temuan hanya placeholder/dokumentasi seperti `client_secret_hash`, `refresh_token`, dan `service_role` di SQL/dokumen.

## Update Arsitektur 2026-07-05

- Keputusan learning architecture diubah dari runtime microservice terpisah menjadi backend Kotlin/Ktor tunggal.
- Modul `:worker` dan `:oauth-server` dihapus dari `settings.gradle.kts`; source keduanya dipindah ke modul `:backend`.
- Namespace worker menjadi `com.application.umkmshop.backend.worker`.
- Namespace OAuth learning menjadi `com.application.umkmshop.backend.oauth`.
- Backend tunggal menjalankan:
  - `/health`;
  - endpoint OAuth learning: `/.well-known/openid-configuration`, `/oauth/authorize`, `/oauth/token`, `/oauth/userinfo`, `/oauth/jwks.json`, `/oauth/revoke`;
  - demo public client `/demo`;
  - notification worker `pgmq` → FCM sebagai coroutine/background job jika env DB/Firebase tersedia atau `UMKMSHOP_WORKER_ENABLED=true`.
- Docker runtime sekarang hanya punya satu service `backend`. `android-builder` dan `android-emulator` tetap ada untuk tooling Android.
- `UMKMSHOP_WORKER_ENABLED=false` boleh dipakai untuk local HTTP/OAuth-only development. Production-like notification processing harus set `UMKMSHOP_WORKER_ENABLED=true` agar missing DB/FCM config fail-fast.

## Validasi 2026-07-05

- `./gradlew --no-configuration-cache :backend:test` berhasil.
- `./gradlew --no-configuration-cache :backend:installDist` berhasil.
- `./gradlew --no-configuration-cache :app:assembleDebug` berhasil.
- `docker compose config` berhasil.
- `docker compose --profile runtime config` berhasil dan hanya menampilkan satu runtime service `backend`.
- Smoke backend gabungan dari binary `backend/build/install/backend/bin/backend` dengan `UMKMSHOP_WORKER_ENABLED=false` berhasil:
  - `/health` mengembalikan `ok`;
  - `/.well-known/openid-configuration` mengembalikan metadata OAuth;
  - `/oauth/jwks.json` mengembalikan RSA JWKS;
  - invalid redirect URI ditolak dengan `400 invalid_request`.
- `docker build --target backend -t umkmshop/backend:local .` belum bisa divalidasi karena Docker daemon tidak tersedia: `failed to connect to the docker API at unix:///var/run/docker.sock`.
- Secret scan marker `service_role|sb_secret_|BEGIN PRIVATE KEY|firebase-adminsdk|XENDIT_SECRET|refresh_token` tidak menemukan secret nyata; temuan berupa placeholder/env name/dokumentasi/SQL dan route OAuth yang memang memakai parameter `refresh_token`.

## Blocker / Batas Belum Selesai

- Android OAuth Client ID, Supabase Google provider/client IDs, dan smoke Google OAuth native device/emulator belum tersedia di environment agent, jadi Google native belum bisa dicentang sebagai smoke nyata.
- Issuer production HTTPS belum diputuskan; demo lokal memakai `http://127.0.0.1:8090`.
- Penyimpanan private signing key production belum diputuskan; demo lokal menggenerate RSA key saat startup agar private key tidak masuk repo.
- OAuth persistence production belum dihubungkan ke Supabase/Postgres; service TICKET-027 saat ini memakai in-memory store untuk learning/demo flow lokal dan negative tests.
