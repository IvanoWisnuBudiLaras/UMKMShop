# TICKET-023: Phase 3 Persistent Inbox

## Tujuan

Membuat inbox persisten untuk notifikasi bisnis seperti invoice dibuat, pembayaran berhasil, pembayaran expired, dan reminder. Inbox berbeda dari `pgmq`: inbox bisa dibuka ulang kapan saja, sedangkan `pgmq` hanya antrean push transient.

## Scope

- Tambah tabel `notifications`.
- Enable RLS untuk `notifications`.
- User bisa membaca notifikasi miliknya.
- User hanya bisa update `is_read` miliknya sendiri.
- Insert notification tidak dibuka ke client; hanya trigger/service role.
- Trigger/order event membuat row inbox dan mengirim push via `pgmq`.
- UI Inbox penuh dari Profile.
- Badge unread ringkas di Profile/Toko/Produk.

## Acceptance Criteria

- Perubahan order membuat notification persisten untuk pihak relevan.
- Notification tetap bisa dibuka ulang setelah push lewat.
- Badge unread akurat tanpa menarik seluruh row notification.
- User tidak bisa membaca notification user lain.
- User tidak bisa mengubah `user_id`, `type`, `title`, `body`, atau `related_order_id`.

## Catatan Implementasi

- Tabel `notifications` harus memiliki index `(user_id, created_at desc)` dan partial index unread.
- Policy update sebaiknya dibatasi kolom di layer SQL privilege/function atau endpoint RPC sempit agar client hanya bisa mark read.
- Event order harus menulis `notifications` dan `pgmq.send()` dalam satu transaksi untuk menghindari state setengah jadi.
- Jangan mengganti worker push dengan inbox; keduanya hidup berdampingan.

## Validasi

- [ ] Supabase migration/table/policy berhasil.
- [ ] Insert notification internal berhasil.
- [ ] User bisa mark read miliknya.
- [ ] User tidak bisa membaca atau update notification user lain.
- [ ] Badge unread menghitung row belum dibaca.
- [ ] `./gradlew build`

### Hasil Validasi 2026-07-04

- SQL implementasi disiapkan di `docs/agent-context/sql/ticket-023-phase3-persistent-inbox.sql`, termasuk tabel `notifications`, RLS select-own, tanpa grant direct update/insert untuk `authenticated`, RPC sempit `unread_notifications_count()` dan `mark_notifications_read()`, trigger `orders` yang insert inbox + `pgmq.send()` dalam transaksi, serta worker payload order event.
- SQL rollback-only validation disiapkan di `docs/agent-context/sql/ticket-023-validation.sql`, tetapi belum dijalankan karena Supabase MCP tidak tersedia di tool sesi ini dan Supabase CLI tidak terpasang (`supabase: command not found`).
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil.
- `./gradlew :worker:test` berhasil.
- `./gradlew build` belum bisa dicentang karena berhenti pada guard release: `google-services.json is required for release builds so Firebase Messaging is configured.`
