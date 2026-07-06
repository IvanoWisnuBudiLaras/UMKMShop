# TICKET-022: Phase 3 Orders and Transaction History

## Tujuan

Menambahkan fondasi transaksi in-app: penjual bisa membuat invoice/order dari chat setelah harga disepakati, dan pembeli/penjual bisa melihat riwayat transaksi.

## Scope

- Tambah tabel `orders`.
- Enable RLS untuk `orders`.
- Penjual membuat order dari chat room yang ia ikuti sebagai seller.
- Buyer dan seller bisa melihat order yang terkait dengan mereka.
- Tidak ada update status order dari client authenticated.
- UI "Buat Invoice" di sisi penjual dari chat room.
- UI "Riwayat Transaksi" untuk buyer dan seller.

## Acceptance Criteria

- Seller bisa membuat order `pending` dari `chat_room` valid miliknya.
- `buyer_id`, `seller_id`, dan `product_id` order harus konsisten dengan `chat_rooms`.
- User lain tidak bisa membaca order yang bukan miliknya.
- Client authenticated tidak bisa mengubah `status`, `paid_at`, `expired_at`, `xendit_invoice_id`, atau `xendit_invoice_url`.
- `total_amount` tersimpan sebagai generated column dari `subtotal + shipping_cost`.
- Riwayat transaksi menampilkan status `pending`, `paid`, `expired`, dan `cancelled` dengan visual berbeda.

## Catatan Implementasi

Gunakan pola RLS yang sudah dikeraskan di Fase 1/2:

- Gunakan `to authenticated`, bukan `auth.role()`.
- Policy insert wajib punya `with check` yang memvalidasi relasi chat room.
- Tidak membuka policy update untuk client pada field status/payment.
- Tambahkan index FK yang dibutuhkan advisor, termasuk `chat_room_id`, `buyer_id`, `seller_id`, dan `product_id`.

SQL awal dari `PLAN3.md`/`BACKEND_SPEC-v2.md` perlu disesuaikan sebelum eksekusi agar aman dengan schema Fase 1/2 yang sudah berjalan.

## Validasi

- [x] Supabase migration/table/policy berhasil.
- [x] Seller bisa membuat order dari chat room miliknya.
- [x] Buyer dan seller bisa membaca order.
- [x] User ketiga tidak bisa membaca order.
- [x] Client tidak bisa update status order.
- [ ] `./gradlew build`
- [ ] Smoke manual riwayat transaksi jika emulator/device tersedia.

### Hasil Validasi 2026-07-04

- Supabase MCP project `jhpcpccmzmukiuykkmpq`: migration `ticket_022_phase3_orders_transaction_history` dan follow-up `ticket_022_orders_composite_fk_index` berhasil.
- Script rollback-only `docs/agent-context/sql/ticket-022-validation.sql` berhasil: seller insert `pending`, buyer/seller read, user ketiga tidak bisa read, client authenticated tidak bisa update status/payment fields.
- Supabase performance advisor sempat menemukan composite FK tanpa covering index; sudah diperbaiki dengan `idx_orders_chat_room_participants`. Setelah follow-up, tidak ada lagi lint `unindexed_foreign_keys` untuk `orders`.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil.
- `./gradlew build` belum dicentang karena berhenti pada guard release: `google-services.json is required for release builds so Firebase Messaging is configured.`
- Smoke manual riwayat transaksi via emulator/device belum dijalankan.
