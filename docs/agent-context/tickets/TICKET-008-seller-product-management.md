# TICKET-008: Seller Product Management

## Tujuan

Mewujudkan flow penjual PRD: seller bisa menambahkan, mengubah, dan menonaktifkan produk dengan foto.

## Scope

- Buat form tambah produk.
- Buat form edit produk.
- Upload foto ke Supabase Storage.
- Insert/update `products`.
- Insert/update `product_images`.
- Nonaktifkan produk milik sendiri.

## Acceptance Criteria

- Seller bisa membuat produk dengan nama, harga, deskripsi, kategori, dan minimal satu foto.
- Produk baru berstatus `active` secara default.
- Seller bisa mengedit produk miliknya.
- Seller bisa mengubah produk menjadi `inactive`.
- RLS menolak seller mengubah produk user lain.

## Catatan Implementasi

- Ikuti schema `products` dan `product_images` dari BACKEND_SPEC.
- Jangan menambahkan status transaksi seperti sold/paid karena PRD menyatakan transaksi tidak dilacak di MVP.
- Implementasi Android memakai `storage-kt` dan bucket `product-images`.
- Path upload mengikuti keputusan TICKET-004: `products/{seller_id}/{product_id}/{filename}`.
- Form tambah produk mewajibkan minimal satu foto. Form edit boleh menyimpan perubahan tanpa foto baru; jika foto baru dipilih, primary image di `product_images` diganti.
- Nonaktif produk dilakukan dengan update `products.status = 'inactive'`, bukan delete.

## Hasil Implementasi

- Dashboard seller menampilkan produk milik session aktif, jumlah foto tersimpan, status, tombol edit, dan tombol nonaktif.
- Form tambah/edit produk menyimpan nama, harga, deskripsi, kategori, dan foto ke Supabase.
- Query gambar dashboard memakai batch filter `product_id in (...)` agar tidak N+1 per produk.
- Supabase MCP aktif untuk referensi dokumentasi; tidak ada perubahan schema/database pada ticket ini.

## Validasi

- [x] `./gradlew build` — berhasil pada 2026-07-02.
- [x] Seller A membuat produk — tervalidasi via SQL RLS smoke rollback sebagai role `authenticated`.
- [x] Seller A mengedit produk — tervalidasi via SQL RLS smoke rollback sebagai role `authenticated`.
- [x] Seller B gagal mengedit produk Seller A — tervalidasi via SQL RLS smoke rollback; update `products` dan `product_images` menghasilkan 0 row.

Hasil smoke:

- `./gradlew build` berhasil.
- Supabase SQL rollback menghasilkan `ticket_008_sql_rls_smoke_passed`.
- Cleanup setelah percobaan Auth/API: `remaining_auth_users=0`, `remaining_products=0`, `remaining_storage_objects=0`.

Catatan blocker:

- Storage API upload foto nyata dari client token belum bisa divalidasi ulang pada sesi ini karena Auth signup API mengembalikan `email rate limit exceeded`, sedangkan user Auth yang dibuat langsung via SQL tidak bisa login lewat GoTrue (`Invalid login credentials`). Implementasi tetap memakai Storage SDK resmi dan path/policy TICKET-004 yang sudah tervalidasi sebelumnya.
- Smoke manual lewat device/emulator belum dieksekusi dari environment agent.
