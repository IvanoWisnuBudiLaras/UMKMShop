# TICKET-009: Buyer Catalog and Detail

## Tujuan

Mewujudkan flow pembeli PRD: buyer bisa browse, search, filter, membuka detail produk, dan memulai chat dari produk.

## Scope

- Tampilkan list produk aktif.
- Tambahkan pagination sederhana.
- Implement search nama/kategori.
- Implement filter kategori dan rentang harga.
- Buat detail produk dengan foto, deskripsi, harga, kategori, info seller, dan tombol chat.

## Acceptance Criteria

- Buyer hanya melihat produk `active` di katalog umum.
- Produk inactive tidak muncul di katalog buyer.
- Detail produk menampilkan foto dan info seller.
- Tombol chat tersedia dari detail produk.

## Catatan Implementasi

- Query katalog harus mengikuti PRD: produk aktif, search dasar, filter kategori/harga.
- Tombol chat boleh diarahkan ke placeholder jika ticket chat belum dikerjakan, tetapi route-nya harus siap.

## Validasi

- [x] `./gradlew build`
- [ ] Seller membuat produk aktif.
- [ ] Buyer menemukan produk lewat katalog.
- [ ] Buyer membuka detail produk.

## Status 2026-07-02

Implementasi Android katalog/detail buyer selesai:

- Katalog hanya query produk `active`.
- Search nama/kategori, filter kategori, filter harga minimum/maksimum, dan pagination sederhana tersedia.
- Detail produk menampilkan foto, deskripsi, harga, kategori, info seller, dan tombol chat.
- Tombol chat mengarah ke route placeholder dengan `productId` untuk dilanjutkan di TICKET-010.

Validasi nyata:

- `./gradlew build` berhasil.
- Supabase MCP rollback smoke berhasil membuat data sementara 1 produk `active`, 1 produk `inactive`, dan 1 foto; query validasi membaca produk active/filter/detail seller; rollback terverifikasi menyisakan 0 row test.

Belum divalidasi:

- Smoke manual dari app di emulator/device untuk seller membuat produk aktif, buyer menemukan produk lewat katalog, dan buyer membuka detail produk belum dijalankan dari environment agent.
