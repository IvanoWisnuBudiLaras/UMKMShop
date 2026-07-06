# PLAN.md — Sesi Eksekusi Marketplace UMKM

**Pelengkap dari:** PRD.md, BACKEND_SPEC.md, GOALS.md
**Prinsip:** PLAN berisi urutan sesi kerja. Detail scope dan acceptance criteria tiap sesi ditulis di file `tickets/`.

---

## Aturan Eksekusi

- PLAN hanya routing dan dependency map, bukan otorisasi untuk otomatis mulai ticket berikutnya.
- Agent hanya boleh mengerjakan ticket yang eksplisit disebut user pada turn/session tersebut.
- Kerjakan sesi berurutan kecuali dependency eksplisit mengizinkan paralel.
- Setiap sesi harus membaca PRD untuk menjaga flow bisnis tetap benar.
- Setiap sesi teknis backend harus membaca BACKEND_SPEC sebelum implementasi.
- Setelah sesi selesai, update checklist ini dan ticket terkait.
- Jangan lompat ke fitur mobile yang bergantung pada schema/RLS sebelum fondasi Supabase selesai.

---

## Sesi 01 — Definisikan MVP

**Ticket:** `tickets/TICKET-001-define-mvp.md`

- [ ] Finalisasi scope MVP berdasarkan PRD.
- [ ] Pastikan GOALS menjelaskan hasil akhir, bukan task teknis.
- [ ] Pastikan PLAN menjadi urutan sesi kerja.
- [ ] Pastikan ticket berikutnya bisa dieksekusi tanpa membaca ulang seluruh chat.

**Selesai bila:** PRD, GOALS, PLAN, dan ticket awal konsisten.

---

## Sesi 02 — Supabase Schema Base

**Ticket:** `tickets/TICKET-002-supabase-schema-base.md`

- [x] Buat project Supabase Cloud.
- [x] Aktifkan extension `pgmq` dan `pg_cron`.
- [x] Verifikasi fungsi `gen_random_uuid()` tersedia untuk UUID default.
- [x] Jalankan DDL tabel dan index dari BACKEND_SPEC.
- [x] Buat queue `notifications`.
- [x] Jika agent tidak punya akses Supabase, siapkan SQL siap pakai dan tunggu user menjalankannya. Tidak diperlukan: Supabase MCP aktif dan SQL sudah dieksekusi.

**Selesai bila:** tabel inti dan queue tersedia untuk diuji manual.

---

## Sesi 03 — RLS dan Security Manual Test

**Ticket:** `tickets/TICKET-003-rls-security-policies.md`

- [x] Enable RLS untuk semua tabel.
- [x] Deploy policy sesuai BACKEND_SPEC.
- [x] Test akses dengan minimal seller, buyer, dan user ketiga.

**Selesai bila:** akses sah berhasil dan akses lintas user yang tidak sah ditolak.

---

## Sesi 04 — Storage Foto Produk

**Ticket:** `tickets/TICKET-004-product-storage.md`

- [x] Buat bucket foto produk.
- [x] Terapkan public read dan authenticated write.
- [x] Tentukan convention path file produk.

**Selesai bila:** foto produk bisa di-upload seller dan dibaca katalog.

---

## Sesi 05 — Trigger dan Cron Backend

**Ticket:** `tickets/TICKET-005-trigger-and-cron.md`

- [x] Deploy trigger notifikasi pesan baru.
- [x] Deploy cron inactive stale products.
- [x] Deploy cron reminder chat belum dibalas.
- [x] Test queue job dari insert message dan reminder.

**Status 2026-07-02:** selesai melalui Supabase MCP dengan migration `ticket_005_trigger_and_cron`; smoke test rollback menghasilkan `ticket_005_smoke_passed`, dan security/performance advisor bersih.

**Selesai bila:** backend bisa menghasilkan job notifikasi tanpa mobile app.

---

## Sesi 06 — Android Skeleton dan Navigasi

**Ticket:** `tickets/TICKET-006-android-skeleton-navigation.md`

- [x] Rapikan struktur package Android.
- [x] Setup Compose navigation.
- [x] Buat shell layar utama sesuai PRD.
- [x] Tambah toggle mode pembeli/penjual lokal.

**Status 2026-07-02:** implementasi skeleton selesai dan `./gradlew build` berhasil. Smoke manual emulator/device belum dieksekusi karena `adb` tidak tersedia di environment agent.

**Selesai bila:** app build dan user bisa berpindah antar shell layar utama.

---

## Sesi 07 — Supabase Client dan Auth

**Ticket:** `tickets/TICKET-007-supabase-auth.md`

- [x] Tambah Supabase Kotlin SDK.
- [x] Konfigurasi Supabase URL dan anon key.
- [x] Implement signup, login, logout, dan session restore.
- [x] Pastikan profil dasar tersedia.

**Status 2026-07-02:** implementasi Android Auth selesai dengan Supabase Kotlin SDK `3.1.4`, publishable key di client config, dan trigger profile `ticket_007_profile_on_signup`. `./gradlew build` berhasil; smoke manual signup/login/session restore/logout dari app belum dieksekusi karena `adb` tidak tersedia di environment agent.

**Selesai bila:** user bisa auth dari app dan masuk ke katalog.

---

## Sesi 08 — Seller Product Management

**Ticket:** `tickets/TICKET-008-seller-product-management.md`

- [x] Buat form tambah/edit produk.
- [x] Upload foto ke Storage.
- [x] Simpan produk dan gambar ke Supabase.
- [x] Nonaktifkan produk milik sendiri.

**Status 2026-07-02:** implementasi Android seller product management selesai. App memakai Supabase Kotlin SDK `3.1.4` dengan module Storage, bucket `product-images`, path `products/{seller_id}/{product_id}/{filename}`, insert/update `products`, insert/update `product_images`, dan update `status='inactive'` untuk nonaktif produk. `./gradlew build` berhasil. Supabase SQL rollback menghasilkan `ticket_008_sql_rls_smoke_passed` untuk Seller A create/edit/nonaktif dan Seller B gagal update product/image Seller A. Storage API upload nyata belum bisa divalidasi ulang karena Auth signup API terkena `email rate limit exceeded`; validasi storage mengacu pada TICKET-004 dan tetap perlu smoke manual device.

**Selesai bila:** seller bisa membuat produk aktif yang muncul di database dan storage.

---

## Sesi 09 — Buyer Catalog dan Detail

**Ticket:** `tickets/TICKET-009-buyer-catalog-detail.md`

- [x] Tampilkan katalog produk aktif.
- [x] Implement search dan filter dasar.
- [x] Buat halaman detail produk.
- [x] Tampilkan info seller dan tombol chat.

**Status 2026-07-02:** implementasi Android buyer catalog/detail selesai. `./gradlew build` berhasil. Supabase MCP rollback smoke berhasil memvalidasi data sementara active/inactive, filter harga/kategori, detail seller, dan foto tanpa menyisakan row test. Smoke manual emulator/device belum dieksekusi dari environment agent.

**Selesai bila:** buyer bisa menemukan produk seller lain dari flow katalog PRD.

---

## Sesi 10 — Chat Realtime

**Ticket:** `tickets/TICKET-010-chat-realtime.md`

- [x] Create/get chat room dari detail produk.
- [x] Kirim dan tampilkan pesan.
- [x] Subscribe Realtime per room.
- [x] Tampilkan list percakapan.

**Status 2026-07-02:** implementasi Android chat realtime selesai. App membuat/membuka `chat_rooms` dari detail produk dengan `product_id`, kirim/tampil `messages`, subscribe Supabase Realtime per `room_id`, dan menampilkan list percakapan. Supabase migration `ticket_010_messages_realtime_publication` menambahkan `public.messages` ke `supabase_realtime`; review follow-up migration `ticket_010_chat_room_last_message_id` menambahkan `chat_rooms.last_message_id` agar preview chat tidak mengambil riwayat penuh, lalu `ticket_010_index_last_message_fk` mempertahankan FK index untuk operasi FK/set-null. `./gradlew clean build` berhasil. Supabase MCP rollback smoke memvalidasi unique room, buyer/seller message visibility, seller reply trigger state, `last_message_id`, user ketiga tertolak, dan cleanup test 0 row. Smoke manual dua device/emulator belum dieksekusi karena `adb` tidak tersedia di environment agent.

**Selesai bila:** dua akun di dua device/emulator bisa chat realtime.

---

## Sesi 11 — FCM Token Registration

**Ticket:** `tickets/TICKET-011-fcm-token-registration.md`

- [x] Integrasi Firebase Messaging di Android.
- [x] Simpan token ke `push_tokens`.
- [x] Update token saat refresh.

**Status 2026-07-02:** implementasi Android FCM token registration selesai. App menambahkan Firebase Messaging, `UMKMFirebaseMessagingService`, upsert `push_tokens` via Supabase client dengan `onConflict = "user_id,fcm_token"`, registrasi token setelah session restore/signup/login, dan update saat `onNewToken`. `./gradlew build` berhasil. Source dan APK debug/release dicek tidak mengandung marker service account/private key. Smoke manual login device dan cek row `push_tokens` belum dieksekusi karena `google-services.json` dan device/emulator tidak tersedia di environment agent.

**Status 2026-07-03:** review hardening selesai. Debug build tetap berjalan tanpa Firebase config, release build fail-fast jika `google-services.json` hilang, publishable key Supabase divalidasi format `sb_publishable_`, signed-in flow meminta `POST_NOTIFICATIONS` pada Android 13+, logout menghapus token device sebelum `signOut()`, dan refresh token FCM memakai WorkManager retry. Review lanjutan memastikan repository auth/token lazy terhadap Supabase client dan feature ViewModel dibuat per destination, bukan di root auth screen. `./gradlew :app:assembleDebug` berhasil; `./gradlew :app:assembleRelease` gagal cepat sesuai desain karena `google-services.json` belum tersedia di environment agent; `./gradlew :app:assembleDebug :worker:test` berhasil setelah review lanjutan.

**Selesai bila:** token device user tersedia untuk worker.

---

## Sesi 12 — Notification Worker

**Ticket:** `tickets/TICKET-012-notification-worker.md`

- [x] Buat worker Kotlin/Ktor.
- [x] Poll `pgmq` queue `notifications`.
- [x] Kirim FCM untuk pesan baru dan reminder.
- [x] Handle retry, invalid token, dan archive.

**Status 2026-07-03:** implementasi worker selesai sebagai module JVM `:worker` dengan Firebase Admin SDK, Hikari JDBC, Supavisor transaction-mode guard `prepareThreshold=0`, `pgmq.read/delete/archive`, invalid token cleanup, archive tanpa token atau setelah `MAX_ATTEMPTS`, dan health endpoint non-CRUD `/health`. Signature pgmq aktif di project memakai `pgmq.read(queue_name, vt, qty, conditional jsonb)`, sehingga worker membaca semua job dengan conditional `'{}'::jsonb`. Unit test parser payload, retry/archive decision, dan mixed delivery decision ditambahkan. Review lanjutan menghapus risiko duplikasi retry: job di-delete jika minimal satu token sukses, dan hanya retry jika semua token gagal transient. `worker/build/**` dicek ignored dan tidak tracked/staged. Push end-to-end belum divalidasi karena butuh service account FCM, Supavisor JDBC credential, dan device/token nyata.

**Selesai bila:** job queue menghasilkan push notification end-to-end.

---

## Sesi 13 — Deployment Worker

**Ticket:** `tickets/TICKET-013-worker-deployment.md`

- [ ] Setup server 2GB.
- [x] Buat systemd service.
- [x] Set JVM dan pool DB sesuai batas resource.
- [ ] Verifikasi restart worker aman.

**Status 2026-07-03:** artefak deployment siap sebagai fallback tanpa akses server: `deploy/worker/umkmshop-worker.service`, `deploy/worker/worker.env.example`, dan `deploy/worker/README.md`. Unit service memakai `Restart=always`, `JAVA_OPTS=-Xms256m -Xmx512m`, `MemoryMax=768M`, journald logging, dan env `UMKMSHOP_DB_POOL_MAX_SIZE=8`. `./gradlew --no-configuration-cache :app:assembleDebug :worker:test :worker:installDist` berhasil; `systemd-analyze verify` berhasil dengan root sementara; baseline `pgmq.metrics('notifications')` menunjukkan `queue_length=0`, `total_messages=16`. Setup/restart/reboot/journalctl live masih blocker eksternal karena agent tidak punya akses SSH/systemd ke server 2GB.

**Selesai bila:** worker jalan stabil sebagai service dan tidak menyimpan state lokal.

---

## Sesi 14 — End-to-End MVP Validation

**Ticket:** `tickets/TICKET-014-e2e-mvp-validation.md`

- [ ] Test flow signup sampai chat.
- [ ] Test push notification.
- [x] Test reminder dengan timestamp buatan.
- [x] Test RLS dari sisi user penyerang.
- [x] Test load ringan worker.

**Status 2026-07-03:** validasi parsial selesai. `./gradlew test` berhasil dan `./gradlew :app:assembleDebug :worker:test :worker:installDist` berhasil. Supabase MCP rollback smoke memvalidasi profile trigger, product/image record, buyer katalog/detail, chat room/message trigger queue, seller reply state, reminder tepat satu kali, RLS user ketiga, queue light load 12 job tanpa duplikasi setelah delete, dan cleanup 0 row/queue. Full E2E belum selesai karena `./gradlew build` gagal cepat tanpa `google-services.json` untuk release Firebase config, `adb` tidak tersedia, push notification nyata butuh Firebase config/device/token, dan Auth REST signup nyata terblokir validasi email/rate limit.

**Selesai bila:** kriteria GOALS level MVP jalan terpenuhi.

---

## Sesi 15 — Release Candidate

**Ticket:** `tickets/TICKET-015-release-candidate.md`

- [ ] Build release APK/AAB.
- [x] Review permission dan secret exposure.
- [ ] Install release build di device.
- [ ] Siapkan distribusi internal/closed beta.

**Status 2026-07-03:** release readiness parsial selesai. Release signing guard dan checklist distribusi internal ditambahkan, `.gitignore` mencegah keystore/key umum masuk repo, `./gradlew --no-configuration-cache :app:assembleDebug :worker:test` berhasil, secret scan source/APK debug bersih dari marker service account/service role, permission dump tidak menunjukkan permission sensitif di luar network/notification/Firebase/WorkManager, dan Supabase security advisor bersih. `:app:assembleRelease` serta `:app:bundleRelease` masih gagal cepat karena `app/google-services.json` belum tersedia; install device dan smoke auth/katalog/chat/push belum bisa dijalankan karena `adb`, Firebase config, device/token nyata, dan worker credential runtime tidak tersedia di environment agent.

**Selesai bila:** build MVP siap dibagikan ke tester nyata.

---

## Dependency Ringkas

```text
Sesi 01
  -> Sesi 02
  -> Sesi 03
  -> Sesi 04
  -> Sesi 05
  -> Sesi 06
  -> Sesi 07
  -> Sesi 08
  -> Sesi 09
  -> Sesi 10
  -> Sesi 11
  -> Sesi 12
  -> Sesi 13
  -> Sesi 14
  -> Sesi 15
```

Catatan: Sesi 06 bisa mulai setelah Sesi 02 jika ingin paralel, tetapi urutan default tetap menyelesaikan fondasi Supabase agar tidak ada rework besar di mobile.
