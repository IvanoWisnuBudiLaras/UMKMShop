# TICKET-014: End-to-End MVP Validation

## Tujuan

Membuktikan seluruh flow bisnis PRD berjalan sebagai satu sistem dari signup sampai chat dan notifikasi.

## Scope

- Test signup seller dan buyer.
- Test seller upload produk.
- Test buyer browse, search, filter, dan detail produk.
- Test buyer chat seller.
- Test seller menerima push notification.
- Test seller membalas chat.
- Test reminder chat dengan timestamp buatan.
- Test RLS dari sisi user ketiga.
- Test load ringan worker.

## Acceptance Criteria

- Flow utama PRD selesai tanpa bug blocking.
- Dua akun bisa chat realtime.
- Push notification terkirim untuk pesan baru.
- Reminder terkirim satu kali untuk chat belum dibalas.
- User ketiga tidak bisa membaca data chat.
- Worker tidak menggandakan notifikasi untuk satu job sukses.

## Catatan Implementasi

- Validasi ini adalah gerbang sebelum release candidate.
- Jika ada bug besar, buat ticket perbaikan baru daripada menyembunyikannya di sesi release.

## Validasi

- [ ] `./gradlew build`
- [x] `./gradlew test`
- [ ] Manual E2E dua akun.
- [x] Manual RLS attack scenario.
- [x] Manual worker queue processing.

## Status 2026-07-03

Validasi belum bisa dinyatakan selesai sebagai E2E MVP penuh karena ada blocker eksternal untuk device/push nyata:

- `./gradlew build` gagal cepat dengan pesan `google-services.json is required for release builds so Firebase Messaging is configured.` Ini sesuai release hardening TICKET-011, tetapi berarti release/full build belum tervalidasi di environment agent.
- `./gradlew test` berhasil.
- `./gradlew :app:assembleDebug :worker:test :worker:installDist` berhasil.
- `adb` tidak tersedia di environment agent, sehingga manual E2E dua akun, realtime chat dua device/emulator, dan push notification nyata ke device belum bisa dieksekusi.
- `app/google-services.json` tidak ada, sehingga Firebase Messaging client config untuk push nyata belum tersedia.
- Supabase Auth REST signup nyata belum berhasil: email `example.com` ditolak sebagai invalid, lalu request berikutnya terkena `email rate limit exceeded`. Karena itu validasi signup manual akun seller/buyer belum bisa diklaim lulus.

Validasi Supabase MCP rollback berhasil tanpa menyisakan data:

- Synthetic `auth.users` insert memicu pembuatan 3 `profiles`.
- Seller membuat product dan `product_images` record.
- Buyer browse/search/filter/detail berhasil menemukan produk aktif beserta seller dan image path.
- Buyer membuat `chat_rooms` dan mengirim message; trigger membuat job `new_message` ke queue `notifications`.
- Seller membaca chat dan membalas; `chat_rooms.is_replied=true` dan `last_message_id` terupdate.
- User ketiga tidak bisa membaca `chat_rooms`/`messages` dan insert message ditolak dengan `42501`.
- Reminder timestamp buatan menghasilkan tepat 1 job `reply_reminder`; eksekusi ulang tidak menggandakan reminder.
- Queue light load: 12 job marker dibaca dan di-delete; read ulang marker menghasilkan 0 job.
- Rollback cleanup terverifikasi: `profiles=0`, `products=0`, `product_images=0`, `chat_rooms=0`, `messages=0`, `push_tokens=0`, `notification_queue_length=0`.

Catatan storage: direct SQL insert ke `storage.objects` sebagai role `authenticated` sempat ditolak RLS, tetapi evaluasi kondisi policy path untuk `products/{seller_id}/{product_id}/{filename}` bernilai true. Karena upload nyata seharusnya lewat Storage API/SDK, bukan direct insert manual, hasil ini dicatat sebagai area yang tetap perlu smoke test device/Storage API sebelum release candidate.
