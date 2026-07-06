# TICKET-003: RLS Security Policies

## Tujuan

Menjadikan Supabase Data API aman untuk dipakai langsung oleh Android app tanpa REST API custom.

## Scope

- Enable RLS untuk semua tabel.
- Deploy policy `profiles`, `products`, `product_images`, `chat_rooms`, `messages`, dan `push_tokens` dari `BACKEND_SPEC.md`.
- Buat skenario uji dengan tiga user: seller, buyer, dan user ketiga sebagai penyerang.

## Acceptance Criteria

- User login bisa membaca profil publik yang diperlukan katalog/chat.
- Seller bisa insert/update/delete produk miliknya sendiri.
- Seller tidak bisa mengubah produk seller lain.
- Gambar produk inactive tidak bisa dibaca publik, kecuali oleh seller pemilik produk.
- Buyer hanya bisa membuat chat room untuk produk aktif yang benar-benar dimiliki `seller_id`.
- Buyer tidak bisa membuat chat room dengan dirinya sendiri sebagai seller.
- Buyer hanya bisa melihat chat room yang ia ikuti.
- Mobile client tidak bisa update langsung field routing/state `chat_rooms`.
- User ketiga tidak bisa membaca `chat_rooms` atau `messages` milik buyer-seller lain.
- User hanya bisa manage `push_tokens` miliknya sendiri.

## Catatan Implementasi

- RLS adalah security boundary utama karena PRD dan BACKEND_SPEC memilih mobile app langsung ke Supabase.
- Jangan mengandalkan validasi UI sebagai keamanan.
- Gunakan policy target eksplisit seperti `to authenticated`, bukan predicate `auth.role() = 'authenticated'`.
- Jangan expose direct update `chat_rooms` dari client; gunakan trigger atau controlled SQL function untuk mutasi state.
- Kalau policy diubah dari BACKEND_SPEC, catat alasannya di `DOCS.md` atau ADR.

## Validasi

- [x] Test query sebagai seller.
- [x] Test query sebagai buyer.
- [x] Test query sebagai user ketiga.
- [x] Pastikan query tidak sah gagal dari client context, bukan hanya dari SQL editor admin.

## Hasil Eksekusi

**Tanggal:** 2026-07-02
**Mode:** Supabase MCP aktif
**Project:** UMKMShop (`jhpcpccmzmukiuykkmpq`)
**Migrations:**

- `ticket_003_rls_security_policies`
- `ticket_003_product_images_policy_split`

Yang sudah diterapkan:

- RLS aktif untuk `profiles`, `products`, `product_images`, `chat_rooms`, `messages`, dan `push_tokens`.
- Policy `profiles`, `products`, `chat_rooms`, `messages`, dan `push_tokens` diterapkan sesuai kontrak BACKEND_SPEC.
- Policy `product_images_manage_own` dari bentuk `for all` dipecah menjadi `product_images_insert_own`, `product_images_update_own`, dan `product_images_delete_own` agar authenticated `SELECT` tidak mengevaluasi dua permissive policy sekaligus. Perilaku akses tetap sama: gambar produk aktif bisa dibaca publik, gambar produk inactive hanya bisa dibaca seller pemilik, dan gambar hanya bisa dimanage pemilik produk.
- Grant Data API dibuat least-privilege:
  - `anon`: hanya `SELECT` pada `products` dan `product_images`.
  - `authenticated`: privilege sesuai kebutuhan CRUD MVP per tabel.
  - Privilege berbahaya yang sebelumnya muncul dari broad grant seperti `TRUNCATE` dicabut dari `anon` dan `authenticated`.

Validasi smoke test:

- Seller authenticated bisa membaca profil publik yang diperlukan katalog/chat.
- Seller bisa insert dan update produk miliknya sendiri.
- User ketiga tidak bisa update produk milik seller.
- Anon hanya bisa membaca gambar produk aktif; gambar produk inactive tidak terlihat publik.
- Anon tidak bisa insert produk.
- Buyer bisa membuat `chat_rooms` hanya untuk produk aktif milik seller yang benar.
- Buyer tidak bisa membuat room dengan dirinya sendiri sebagai seller.
- Buyer tidak bisa membuat room untuk produk inactive.
- Buyer bisa insert message sebagai participant.
- Mobile client tidak bisa update langsung field state `chat_rooms`; query gagal dengan `permission denied for table chat_rooms`.
- User ketiga tidak bisa membaca `chat_rooms` atau `messages` milik buyer-seller.
- Buyer bisa insert/update `push_tokens` miliknya sendiri.
- User ketiga tidak bisa update `push_tokens` milik buyer.
- Seller participant bisa membaca room terkait.

Hasil validasi:

- Smoke test final menghasilkan `ticket_003_rls_smoke_passed_after_policy_split` pada `2026-07-02 05:47:25.705979+00`.
- Semua enam tabel target memiliki `rowsecurity = true`.
- Supabase security advisor: tidak ada lint.
- Supabase performance advisor: tersisa info `unused_index` untuk `idx_chat_rooms_reminder`. Ini normal untuk schema baru karena job reminder/cron belum menghasilkan traffic nyata.
