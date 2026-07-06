# Technical Documentation

## Stack

- Android project dengan Gradle Kotlin DSL.
- Source utama berada di `app/src/main/java/com/application/umkmshop/`.
- Resource Android berada di `app/src/main/res/`.

## Catatan Arsitektur

- Supabase project aktif untuk MVP: UMKMShop (`jhpcpccmzmukiuykkmpq`), region `ap-south-1`, Postgres `17.6.1.141`.
- Schema base TICKET-002 sudah diterapkan melalui Supabase MCP dengan migrasi `ticket_002_supabase_schema_base`.
- Follow-up TICKET-002 menerapkan migrasi `ticket_002_advisor_followups` untuk menambah index FK `idx_chat_rooms_product` dan `idx_messages_sender`, serta mencabut execute publik dari function `public.rls_auto_enable()`.
- Queue notifikasi memakai Supabase Queues (`pgmq`) bernama `notifications`; tidak ada tabel queue custom.
- Extension aktif yang relevan: `pgmq` `1.5.1`, `pg_cron` `1.6.4`, dan `pgcrypto` `1.3`.
- Karena perubahan Supabase 2026-04-28, sesi RLS/security perlu memeriksa apakah tabel public terekspos ke Data API dan apakah grant untuk `anon`/`authenticated` sesuai kebutuhan.
- Setelah TICKET-002, RLS sudah aktif pada tabel public tetapi policy belum ada. Ini membuat akses client tertahan sampai TICKET-003 menerapkan policy sesuai BACKEND_SPEC.
- TICKET-003 menerapkan RLS policy dan grant Data API least-privilege melalui migrasi `ticket_003_rls_security_policies`. Broad grant awal untuk `anon`/`authenticated` dicabut, termasuk privilege berbahaya seperti `TRUNCATE` yang tidak dilindungi RLS.
- Follow-up TICKET-003 menerapkan migrasi `ticket_003_product_images_policy_split`: policy `product_images_manage_own` dari BACKEND_SPEC yang berbentuk `for all` dipecah menjadi policy `insert`, `update`, dan `delete` eksplisit. Ini menjaga perilaku akses tetap sama, tetapi menghindari multiple permissive `SELECT` policies untuk role `authenticated`.
- Validasi TICKET-003 menggunakan client-context simulation dengan role `anon`/`authenticated` dan JWT `sub` untuk seller, buyer, dan user ketiga. Security advisor bersih; performance advisor hanya menyisakan info `unused_index` untuk `idx_chat_rooms_reminder` karena belum ada traffic reminder.
- TICKET-004 membuat Supabase Storage bucket `product-images` lewat migrasi `ticket_004_product_storage`, lalu memperbaiki policy path lewat migrasi `ticket_004_fix_storage_object_path_policy`. Bucket bersifat public untuk public URL katalog, tetapi operasi tulis tetap dibatasi RLS `storage.objects`.
- Konvensi path foto produk: `products/{seller_id}/{product_id}/{filename}` di dalam bucket `product-images`. `seller_id` wajib sama dengan `auth.uid()`, dan `product_id` wajib menunjuk ke baris `products` milik seller tersebut. Simpan public URL/path hasil upload ke `product_images.image_url`; file mentah tidak disimpan di database.
- Bucket `product-images` membatasi upload ke `image/jpeg`, `image/png`, dan `image/webp` dengan limit `5 MiB`. Tidak ada policy SELECT tambahan untuk listing object; katalog membaca gambar melalui public URL bucket, bukan list bucket.
- TICKET-005 menerapkan migration `ticket_005_trigger_and_cron` untuk function `public.enqueue_new_message_notification()`, trigger `trg_after_message_insert`, cron `deactivate-stale-products`, dan cron `chat-reply-reminder`.
- Function `public.enqueue_new_message_notification()` memakai `SECURITY DEFINER`, `search_path=pg_catalog, public, pgmq`, dan direct execute sudah dicabut dari `PUBLIC`, `anon`, serta `authenticated`.
- Trigger `trg_after_message_insert` berjalan `AFTER INSERT` pada `public.messages`; pesan buyer/seller mengupdate state room dan enqueue payload flat `new_message` ke queue `notifications`.
- Cron `deactivate-stale-products` berjalan harian `0 0 * * *`; cron `chat-reply-reminder` berjalan tiap 10 menit `*/10 * * * *` dan menghindari reminder berulang lewat `reminder_sent`.
- TICKET-005 smoke test rollback menghasilkan `ticket_005_smoke_passed`; security advisor dan performance advisor bersih.
- TICKET-007 memakai Supabase Kotlin SDK dari Android app dengan `SUPABASE_URL` dan `SUPABASE_PUBLISHABLE_KEY` yang dibaca dari `local.properties`/environment ke `BuildConfig`. Service role key tidak boleh dan tidak dipakai di client app.
- Dependency auth Android dipin ke Supabase Kotlin SDK `3.1.4` dan Ktor `3.1.3`; percobaan `3.2.0-ksp-b1`/Ktor `3.2.0` gagal dexing untuk minSdk 24 dengan error `Space characters in SimpleName 'use streaming syntax' are not allowed prior to DEX version 040`.
- TICKET-007 menerapkan migration `ticket_007_profile_on_signup`: trigger `on_auth_user_created_create_profile` pada `auth.users` menjalankan function `public.handle_new_auth_user_profile()` untuk membuat/backfill row `profiles` setelah signup. Execute function dicabut dari `PUBLIC`, `anon`, dan `authenticated`; security/performance advisor bersih.
- Validasi TICKET-007: `./gradlew build` berhasil. Smoke manual signup/login/session restore/logout dari app belum dieksekusi di environment agent karena `adb` tidak tersedia.
- Follow-up review TICKET-007: app backup dimatikan (`allowBackup=false`) dan backup/data extraction rules mengecualikan app data agar session material Supabase tidak ikut cloud backup/device transfer; profile fetch difilter berdasarkan signed-in user id; `AuthViewModel` dibuat via AndroidX `viewModel()` agar mengikuti ViewModelStore.
- Catatan Gradle TICKET-007: menambahkan `org.jetbrains.kotlin.android` eksplisit sudah dicoba tetapi gagal build dengan `Cannot add extension with name 'kotlin', as there is an extension already registered with that name`. Build valid tanpa plugin eksplisit tersebut dan task `compileDebugKotlin`, `compileReleaseKotlin`, serta `compileDebugUnitTestKotlin` tersedia.
- TICKET-011 menambahkan Firebase Messaging untuk Android dengan Firebase BOM `34.7.0` dan Google Services plugin `4.4.4`. Plugin `com.google.gms.google-services` hanya diterapkan jika `app/google-services.json` tersedia agar agent environment tetap bisa build tanpa file client Firebase.
- TICKET-011 menambahkan `PushTokenRepository` yang melakukan upsert ke `push_tokens` dengan `onConflict = "user_id,fcm_token"` dan `updated_at` client timestamp UTC. Registrasi token dipanggil setelah session restore, signup yang langsung menghasilkan session, login, dan `FirebaseMessagingService.onNewToken`.
- TICKET-011 tidak menambahkan Firebase Admin SDK atau service account ke Android app. `.gitignore` menolak pola `firebase-adminsdk*.json`, `*-firebase-adminsdk-*.json`, dan `serviceAccountKey.json`; source dan APK debug/release sudah dicek tidak mengandung marker service account/private key.
- Validasi TICKET-011: `./gradlew build` berhasil. Smoke manual token nyata belum dieksekusi karena `google-services.json` dan device/emulator tidak tersedia di environment agent.
- TICKET-013 menambahkan artefak deployment worker di `deploy/worker/`: unit `umkmshop-worker.service`, template `worker.env.example`, dan runbook `README.md`. Service berjalan sebagai user `umkmshop`, memakai `Restart=always`, `JAVA_OPTS=-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError`, `MemoryMax=768M`, journald logging, dan env `UMKMSHOP_DB_POOL_MAX_SIZE=8`. Secret nyata tidak disimpan di repository; server harus mengisi `/etc/umkmshop-worker/worker.env` dengan Supavisor JDBC credential dan Firebase service account.
- Validasi TICKET-013 lokal: `./gradlew --no-configuration-cache :app:assembleDebug :worker:test :worker:installDist` berhasil; `systemd-analyze verify` berhasil memakai root sementara; baseline Supabase `pgmq.metrics('notifications')` menunjukkan `queue_length=0` dan `total_messages=16`. Deploy/restart/journalctl live belum dieksekusi karena agent tidak punya akses SSH/systemd ke server 2GB.
- Update 2026-07-05: arsitektur pembelajaran dirapikan menjadi satu modul/runtime `:backend` yang menggabungkan worker notification dan OAuth learning. Docker runtime hanya punya service `backend`; worker lama dan oauth-server lama tidak lagi menjadi modul/runtime terpisah. Validasi lokal: `:backend:test`, `:backend:installDist`, `:app:assembleDebug`, `docker compose config`, `docker compose --profile runtime config`, dan smoke `/health`/OAuth discovery/JWKS berhasil. Docker image build belum tervalidasi karena Docker daemon tidak tersedia di environment agent.
- TICKET-018 menerapkan migration `ticket_018_phase2_location_filter`: kolom `profiles.city`, index `idx_profiles_city`, dan expression index `idx_profiles_city_lower`. Iterasi ini sengaja hanya text/city filter; tidak ada PostGIS, radius, latitude, atau longitude.
- Validasi TICKET-018: smoke SQL rollback dengan 2.500 produk dummy memverifikasi seller bisa update city sendiri, user lain tidak bisa mengubah city profile tersebut, buyer filter kota hanya melihat produk aktif dari kota itu, dan produk inactive tidak tampil. `EXPLAIN (analyze, buffers)` selesai sekitar 1.3 ms di data dummy; Android `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil; `./gradlew build` masih terblokir guard release karena `app/google-services.json` tidak tersedia.
- TICKET-019 menerapkan migration `ticket_019_search_name_trgm`: extension `pg_trgm` dan partial GIN index `idx_products_active_name_trgm` pada `products.name` untuk row `status='active'`. Tidak dibuat trigram index untuk `description`/`category`; kategori tetap filter eksplisit di UI katalog.
- Baseline TICKET-019 dengan 25.000 produk dummy rollback: query lama `status='active' and (name ilike '%beras%' or category ilike '%beras%')` seq scan sekitar 52.132 ms; no-match seq scan sekitar 54.013 ms. Setelah index dan query Android diubah ke `status='active' and name ilike`, final plan memakai `Bitmap Index Scan on idx_products_active_name_trgm`: match sekitar 4.480 ms, no-match sekitar 0.047 ms, dan inactive leak count 0. `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil; `./gradlew build` masih terblokir guard release karena `app/google-services.json` tidak tersedia.

## Konvensi Implementasi

- Simpan keputusan produk di `PRD.md`.
- Simpan rencana eksekusi di `PLAN.md`.
- Simpan task kecil di `tickets/`.
- Simpan alasan keputusan teknis besar di `decisions/`.

## Perintah Validasi

```bash
./gradlew build
./gradlew test
```

Catatan: jalankan perintah sesuai kebutuhan task dan kondisi environment lokal.
