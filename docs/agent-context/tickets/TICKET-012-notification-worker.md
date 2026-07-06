# TICKET-012: Notification Worker

## Tujuan

Membuat worker Kotlin/Ktor stateless yang memproses queue `notifications` dan mengirim push notification via FCM.

## Scope

- Buat modul/project worker Kotlin/Ktor jika belum ada.
- Gunakan Supabase pooler transaction mode untuk JDBC.
- Set JDBC PostgreSQL `prepareThreshold=0` saat memakai Supavisor transaction mode, atau gunakan session mode khusus worker.
- Poll `pgmq.read('notifications', 30, 10)`.
- Parse payload `new_message` dan `reply_reminder`.
- Ambil token dari `push_tokens`.
- Kirim FCM.
- Delete job sukses.
- Archive job tanpa token atau setelah `MAX_ATTEMPTS`.
- Biarkan job gagal retry lewat visibility timeout.

## Acceptance Criteria

- Job `new_message` menghasilkan push notification.
- Job `reply_reminder` menghasilkan push notification.
- Token invalid dihapus.
- Job gagal tidak hilang sebelum sukses atau archived.
- Worker tidak menyimpan state lokal.

## Catatan Implementasi

- Jangan membuat API CRUD di worker.
- Jangan hardcode secret; gunakan environment variable.
- Hindari JDBC server-side prepared statements di Supavisor transaction mode karena bisa gagal setelah connection reuse.
- Log error cukup untuk debug, tetapi jangan bocorkan connection string atau service account.

## Validasi

- [x] Unit test parser payload.
- [x] Unit test retry/archive decision.
- [ ] Manual kirim message dari app dan cek push masuk.

## Status 2026-07-03

Implementasi worker Kotlin/Ktor dibuat sebagai module JVM `:worker` terpisah dari APK Android. Worker membaca queue notifications via `pgmq.read('notifications', 30, 10, '{}'::jsonb)` karena signature pgmq aktif di project memakai argumen `conditional jsonb`, memakai JDBC URL dengan `prepareThreshold=0`, mengambil `push_tokens`, mengirim FCM via Firebase Admin SDK, menghapus token invalid, `pgmq.delete` untuk job sukses, `pgmq.archive` untuk recipient tanpa token atau job yang mencapai `MAX_ATTEMPTS`, dan membiarkan error transient retry lewat visibility timeout. Validasi unit parser payload dan retry/archive decision sudah ditambahkan; validasi push end-to-end masih blocker eksternal karena butuh service account FCM, JDBC Supavisor credential, dan device/token nyata.

Review lanjutan selesai:

- Policy mixed delivery dibuat eksplisit: jika minimal satu token berhasil dikirim, worker menghapus job agar token sukses tidak menerima duplikasi pada retry berikutnya; token invalid tetap dihapus; retry visibility timeout hanya dipakai jika semua token gagal transient.
- Unit test `deliveryCompletionDecision` ditambahkan untuk kasus semua token gagal transient dan kasus minimal satu token sukses.
- `worker/build/**` dicek tidak tracked/staged (`git ls-files worker/build` kosong). Setelah build/test, artefak muncul sebagai ignored output karena aturan `.gitignore`, bukan sebagai file yang akan ikut merge.

Validasi nyata:

- `./gradlew :app:assembleDebug :worker:test` berhasil.
