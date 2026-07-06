# TICKET-005: Trigger and Cron

## Tujuan

Membuat backend otomatis menghasilkan notifikasi dan housekeeping sesuai PRD tanpa mobile app perlu mengelola queue secara langsung.

## Scope

- Deploy function `enqueue_new_message_notification`.
- Deploy trigger `trg_after_message_insert`.
- Deploy cron `deactivate-stale-products`.
- Deploy cron `chat-reply-reminder`.
- Test payload `new_message` dan `reply_reminder`.

## Acceptance Criteria

- Insert `messages` memperbarui `chat_rooms.last_message_at`.
- Pesan dari buyer membuat notifikasi ke seller.
- Pesan baru dari buyer mereset `reminder_sent = false` agar reminder berikutnya tetap mungkin terkirim jika tidak dibalas.
- Pesan dari seller membuat notifikasi ke buyer dan menandai `is_replied = true`.
- Produk aktif yang stale lebih dari 30 hari menjadi inactive.
- Chat belum dibalas lebih dari 2 jam menghasilkan reminder satu kali.

## Catatan Implementasi

- Payload queue harus tetap flat dan minimal seperti BACKEND_SPEC.
- Trigger tidak boleh bergantung pada Android app.
- `SECURITY DEFINER` function wajib pin `search_path` atau fully qualify semua object.
- Direct execute privilege function trigger untuk `anon` dan `authenticated` harus direvoke jika tidak dibutuhkan.
- Update state `chat_rooms` dari trigger hanya boleh berlaku jika message yang diproses adalah message terbaru berdasarkan `created_at`.
- Cron reminder harus menghindari reminder berulang lewat `reminder_sent`.
- Cron reminder wajib membuktikan latest message room berasal dari buyer, bukan hanya percaya state `chat_rooms`.
- SQL siap eksekusi ada di `docs/agent-context/sql/ticket-005-trigger-and-cron.sql`.
- SQL validasi rollback ada di `docs/agent-context/sql/ticket-005-validation.sql`.

## Status Eksekusi

**Tanggal:** 2026-07-02
**Mode:** Mode B — Supabase MCP aktif
**Project:** UMKMShop (`jhpcpccmzmukiuykkmpq`)
**Migration:** `ticket_005_trigger_and_cron`, patch follow-up `ticket_005_concurrency_and_cron_guards`, `ticket_005_chat_room_insert_policy_initplan`, `ticket_005_chat_room_insert_policy_qualify_seller`

Yang sudah disiapkan:

- Function `public.enqueue_new_message_notification()` sebagai `SECURITY DEFINER`.
- `search_path` function dipin ke `pg_catalog, public, pgmq`.
- Direct execute dicabut dari `public`, `anon`, dan `authenticated`.
- Trigger `trg_after_message_insert` dibuat ulang secara idempotent.
- Cron `deactivate-stale-products` dijadwalkan harian jam `00:00`.
- Cron `chat-reply-reminder` dijadwalkan tiap 10 menit.
- Policy `chat_rooms_insert_as_buyer` diketatkan agar client tidak bisa memasok state reminder saat membuat room.
- SQL validasi rollback mencakup payload `new_message`, payload `reply_reminder`, stale product deactivation, room state update, dan privilege function.

Hasil validasi:

- Migration `ticket_005_trigger_and_cron` berhasil diterapkan.
- Smoke test transaksi rollback menghasilkan `ticket_005_smoke_passed`.
- Insert pesan buyer memperbarui `chat_rooms.last_message_at`, set `is_replied = false`, reset `reminder_sent = false`, dan enqueue payload `new_message` ke seller.
- Insert pesan seller set `is_replied = true` dan enqueue payload `new_message` ke buyer.
- Trigger tidak mengizinkan pesan lama yang commit belakangan menimpa state room dari pesan yang lebih baru.
- Body cron stale product menonaktifkan produk aktif dengan `updated_at < now() - interval '30 days'`.
- Body cron reminder menandai room due sebagai `reminder_sent = true` dan enqueue payload `reply_reminder` satu kali hanya jika latest message berasal dari buyer.
- Function permanen terverifikasi `SECURITY DEFINER`, `proconfig = search_path=pg_catalog, public, pgmq`, dan tidak punya direct execute grantee untuk `PUBLIC`, `anon`, atau `authenticated`.
- Trigger permanen terverifikasi sebagai `AFTER INSERT` pada `public.messages` dengan action `EXECUTE FUNCTION enqueue_new_message_notification()`.
- Cron permanen terverifikasi aktif untuk `deactivate-stale-products` (`0 0 * * *`) dan `chat-reply-reminder` (`*/10 * * * *`).
- Policy `chat_rooms_insert_as_buyer` terverifikasi memakai state default room dan product seller yang sesuai.
- RLS smoke rollback memverifikasi insert room valid diterima, state spoof ditolak, dan seller/product mismatch ditolak.
- `cron.job_run_details` mencatat execution result `chat-reply-reminder` dengan status `succeeded`; `deactivate-stale-products` belum punya run detail karena jadwalnya harian jam `00:00`.
- Smoke test tidak meninggalkan data dummy: user/profiles/products `Ticket 005` = `0`.
- Supabase security advisor: bersih.
- Supabase performance advisor: bersih.

## Validasi

- [x] Insert message dummy dan cek `pgmq.q_notifications`.
- [x] Ubah satu `products.updated_at` dummy ke lebih dari 30 hari lalu jalankan body cron manual terfilter ke dummy row.
- [x] Ubah satu room dummy agar due lebih dari 2 jam lalu jalankan body cron manual terfilter ke dummy row.
- [x] Cek minimal satu execution result di `cron.job_run_details` setelah scheduler berjalan.
