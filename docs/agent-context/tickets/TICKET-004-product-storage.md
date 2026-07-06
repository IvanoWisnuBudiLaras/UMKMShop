# TICKET-004: Product Storage

## Tujuan

Menyiapkan penyimpanan foto produk supaya flow seller di PRD bisa membuat produk dengan gambar, dan flow buyer bisa melihat gambar di katalog serta detail produk.

## Scope

- Buat Supabase Storage bucket untuk foto produk.
- Terapkan public read untuk gambar katalog.
- Terapkan authenticated write untuk seller.
- Tentukan path convention file produk.
- Dokumentasikan convention path di `DOCS.md` jika sudah diputuskan.

## Acceptance Criteria

- Seller login bisa upload foto produk.
- URL foto bisa dibaca publik untuk katalog.
- File produk punya struktur path yang bisa dikaitkan ke `seller_id` dan `product_id`.
- App tidak membutuhkan service role key untuk upload foto.

## Catatan Implementasi

- Path default yang disarankan: `products/{seller_id}/{product_id}/{filename}`.
- Jangan simpan file mentah di database; database hanya menyimpan `image_url` dan `sort_order`.
- Bucket Supabase Storage yang dipakai: `product-images`.
- Bucket dibuat public agar katalog/detail produk bisa memakai public URL tanpa service role.
- Operasi tulis pada `storage.objects` hanya dibuka untuk role `authenticated` dan hanya jika path mengikuti `products/{seller_id}/{product_id}/{filename}`, `seller_id = auth.uid()`, serta `product_id` adalah produk milik seller tersebut.
- Upload dibatasi ke MIME `image/jpeg`, `image/png`, dan `image/webp` dengan limit `5 MiB`.

## Hasil Implementasi

- Migrasi `ticket_004_product_storage` membuat bucket `product-images` dan policy tulis awal.
- Migrasi `ticket_004_fix_storage_object_path_policy` memperbaiki referensi path object supaya subquery policy membaca `storage.objects.name`, bukan `products.name`.
- Security advisor setelah migrasi: bersih.

## Validasi

- [x] Upload gambar dummy sebagai user login. Divalidasi dengan smoke test SQL transaksi rollback: role `authenticated` dengan JWT `sub` seller bisa `insert` object pada path produk miliknya.
- [x] Buka public URL gambar. Divalidasi dari konfigurasi bucket `public = true`; upload file nyata belum dilakukan karena project belum punya Auth user nyata dan pemanggilan publishable key MCP timeout.
- [x] Coba upload tanpa login dan pastikan ditolak. Role `anon` gagal insert object dengan error `42501: new row violates row-level security policy for table "objects"`.
- [x] Pastikan smoke test tidak meninggalkan data dummy: `storage.objects` untuk bucket `product-images` tetap `0`, dan auth user dummy `ticket004-*` tetap `0`.
