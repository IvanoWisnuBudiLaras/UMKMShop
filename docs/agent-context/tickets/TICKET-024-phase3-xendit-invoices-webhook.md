# TICKET-024: Phase 3 Xendit Invoices and Webhook

## Tujuan

Mengintegrasikan Xendit agar seller bisa membuat invoice setelah deal di chat, buyer bisa membayar, dan status order berubah otomatis dari webhook yang aman dan idempotent.

## Scope

- Gunakan Xendit sebagai payment gateway.
- Kunci keputusan lokasi webhook: Supabase Edge Function atau Kotlin worker HTTP listener.
- Rekomendasi default: Supabase Edge Function.
- Implement pembuatan invoice Xendit dari form order seller.
- Simpan `xendit_invoice_id` dan `xendit_invoice_url` ke `orders`.
- Buat webhook publik yang memverifikasi signature/token Xendit.
- Webhook update `orders.status`, `paid_at`/`expired_at`, insert `notifications`, dan enqueue `pgmq`.
- Pastikan webhook idempotent.
- Test di sandbox Xendit.

## Acceptance Criteria

- Payload webhook tanpa signature/token valid ditolak.
- Webhook retry untuk invoice yang sama tidak membuat notifikasi dobel.
- Order hanya berubah dari `pending` ke status terminal yang valid.
- Buyer dan seller mendapat inbox notification saat invoice dibuat/dibayar/expired.
- Push notification tetap dikirim via worker dari `pgmq`.
- Secret Xendit tidak pernah masuk Android app.
- Auth user tetap memakai Google OAuth2/Email via Supabase; Xendit endpoint tidak boleh bergantung pada session user.

## Catatan Implementasi

- Jika memakai Edge Function, simpan Xendit secret di environment Supabase, bukan repo.
- Jika memakai worker HTTP listener, butuh domain publik + HTTPS + hardening operasional; jangan dipilih tanpa alasan kuat.
- Semua update payment harus server-side/service role, bukan client SDK.
- Webhook harus memproses dalam transaksi: validasi order pending, update order, insert inbox notification, enqueue push.
- Jangan menambahkan refund otomatis; refund/sengketa tetap manual di Fase 3.

## Validasi

- [x] Keputusan lokasi webhook dicatat.
- [ ] Invoice sandbox Xendit bisa dibuat.
- [ ] Buyer membuka invoice URL.
- [ ] Webhook valid mengubah order status.
- [x] Webhook invalid ditolak.
- [x] Webhook duplicate tidak membuat notifikasi dobel.
- [ ] Supabase advisor/security check bersih untuk perubahan terkait.

## Catatan Implementasi 2026-07-04

- Keputusan final: webhook Xendit memakai Supabase Edge Function `xendit-webhook` dengan `verify_jwt=false`; pembuatan invoice memakai Edge Function `create-xendit-invoice` dengan `verify_jwt=true`.
- SQL/RPC TICKET-024 diterapkan ke Supabase project UMKMShop (`jhpcpccmzmukiuykkmpq`): tabel `notifications` dibuat bila belum ada, `orders` ditambah `xendit_external_id`, `xendit_status`, dan `payment_updated_at`, serta RPC `ticket_024_store_xendit_invoice()` dan `ticket_024_apply_xendit_webhook()` dibuat untuk transaksi server-side.
- Edge Function `create-xendit-invoice` dan `xendit-webhook` berhasil deploy via Supabase MCP. `create-xendit-invoice` menolak request tanpa auth header dengan HTTP 401. `xendit-webhook` menolak request public tanpa callback token valid dengan HTTP 401.
- Validasi rollback-only RPC berhasil: wrong seller diblokir, invoice URL dummy tersimpan, webhook pertama mengubah order menjadi `paid`, webhook duplicate tidak mengubah status ulang, jumlah `notifications` tetap 2, dan jumlah queue `pgmq.q_notifications` tetap 2.
- Android `:app:assembleDebug` dan `:app:testDebugUnitTest` berhasil setelah wiring create invoice dan tombol buka invoice URL via browser.
- Secret scan source hanya menemukan placeholder `.env.example`; scan APK debug tidak menemukan marker Xendit secret, Supabase service-role/secret key, private key, atau Firebase admin key.
- Blocker eksternal: belum bisa mencentang invoice sandbox, buyer membuka invoice URL nyata, dan webhook valid dari Xendit sandbox karena `XENDIT_SECRET_KEY`, konfigurasi callback URL dashboard Xendit, dan uji pembayaran sandbox nyata harus diisi/dijalankan manual di environment Xendit.
- Supabase security advisor berjalan, tetapi belum "bersih": masih ada warning existing terkait anonymous access policies pada tabel lama/extension dan leaked password protection disabled. Performance advisor juga menandai beberapa unused index, termasuk index baru `notifications`, karena belum ada traffic nyata.
