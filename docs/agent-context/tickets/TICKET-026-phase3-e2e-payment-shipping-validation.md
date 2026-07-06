# TICKET-026: Phase 3 E2E Payment and Shipping Validation

## Tujuan

Memvalidasi seluruh flow Fase 3 dari negosiasi chat sampai invoice, pembayaran Xendit sandbox, inbox, push notification, riwayat transaksi, dan ongkir.

## Scope

- Skenario penuh buyer-seller.
- Validasi RLS `orders` dan `notifications`.
- Validasi idempotency webhook.
- Validasi ongkir otomatis dan fallback manual.
- Validasi worker push tetap berjalan dengan payload Fase 3.
- Cek secret exposure di Android dan repo.

## Acceptance Criteria

- Seller membuat invoice dari chat dengan item note, berat, subtotal, dan shipping cost.
- Buyer membayar invoice Xendit sandbox.
- Webhook mengubah status order otomatis.
- Buyer dan seller melihat riwayat transaksi yang sama konsisten.
- Buyer dan seller mendapat inbox notification.
- Push notification terkirim tanpa menggantikan inbox.
- Webhook duplicate tidak menggandakan status/notification.
- User ketiga tidak bisa membaca order/inbox.

## Catatan Implementasi

- Ini ticket validasi, bukan tempat menambah scope fitur baru.
- Jika ada bug blocking, buat ticket follow-up spesifik.
- Jangan menandai Fase 3 selesai jika payment webhook belum dites dengan payload valid dan invalid.

## Validasi

- [ ] `./gradlew build`
- [ ] Unit/integration test server/worker terkait webhook jika ada.
- [ ] Xendit sandbox paid flow.
- [ ] Invalid signature rejected.
- [ ] Duplicate webhook idempotent.
- [ ] RLS attack scenario untuk orders/notifications.
- [ ] Secret scan source dan APK.

Catatan validasi 2026-07-04:
- `./gradlew --no-configuration-cache :app:assembleDebug :app:testDebugUnitTest :worker:test` berhasil.
- `./gradlew --no-configuration-cache build` gagal cepat sebelum release compile karena `google-services.json is required for release builds so Firebase Messaging is configured.`
- Source audit `rg -n "xendit|XENDIT|webhook|x-callback-token|callback-token|invoice_id|external_id" app worker supabase docs/agent-context/sql docs/agent-context/tickets -S` tidak menemukan implementasi Xendit/webhook di `app`, `worker`, atau `supabase`; hanya model/DDL `xendit_invoice_id`/`xendit_invoice_url` dan dokumen ticket. Karena itu Xendit sandbox paid flow, invalid signature, dan webhook duplicate belum bisa divalidasi tanpa mengerjakan TICKET-024.
- `supabase` CLI dan `deno` tidak tersedia di environment agent, dan tidak ada `DATABASE_URL`/Supabase env aktif; validasi DB live, Supabase advisor, dan typecheck/deploy Edge Function tidak dijalankan.
- Secret scan source untuk marker `service_role`, `sb_secret_`, `private_key`, `BEGIN PRIVATE KEY`, `FIREBASE_SERVICE_ACCOUNT`, `XENDIT`, dan `API_CO_ID_KEY` hanya menemukan placeholder/dokumentasi serta penggunaan env server-side; tidak ada nilai secret nyata yang terdeteksi.
- Secret scan APK debug dengan `strings app/build/outputs/apk/debug/app-debug.apk | rg ...` bersih untuk marker `service_account`, `private_key`, `firebase-adminsdk`, `BEGIN PRIVATE KEY`, `client_email`, `service_role`, `sb_secret_`, `SUPABASE_SERVICE_ROLE_KEY`, `GOOGLE_APPLICATION_CREDENTIALS`, `XENDIT`, `xendit`, dan `API_CO_ID_KEY`.
- Ditambahkan helper rollback-only `docs/agent-context/sql/ticket-026-validation.sql` untuk validasi DB yang bisa dijalankan nanti dengan owner role: RLS `orders`/`notifications`, authenticated client tidak bisa update field payment order, trigger paid membuat inbox+pgmq untuk buyer/seller, dan duplicate paid update tidak menggandakan notification/job. Helper ini belum dijalankan live di environment agent.
