# TICKET-018: Phase 2 Location Filter

## Tujuan

Membuat pembeli bisa memfilter katalog berdasarkan kota penjual dengan pendekatan paling murah terlebih dahulu, sesuai `GOALS2.md`: text match/city filter sebelum radius geospasial.

## Scope

- Tambah field kota di profil penjual.
- UI edit profil/toko untuk mengisi kota.
- Filter katalog berdasarkan kota.
- Dropdown/autocomplete kota, bukan free text penuh.
- Index sederhana untuk query kota.

## Acceptance Criteria

- Seller bisa mengisi/mengubah kota di profil.
- Buyer bisa memilih kota untuk memfilter katalog.
- Katalog hanya menampilkan produk aktif dari seller di kota yang dipilih.
- Query filter tetap cepat untuk data dummy ribuan produk.
- Tidak ada PostGIS/radius di iterasi awal.

## Catatan Implementasi

SQL awal:

```sql
alter table profiles add column city text;
create index idx_profiles_city on profiles(city);
```

- Jika ingin case-insensitive konsisten, pertimbangkan menyimpan normalized city atau memakai expression index `lower(city)`.
- Query katalog kemungkinan perlu join `products.seller_id -> profiles.id`.
- Jangan mulai dari latitude/longitude atau PostGIS sebelum ada data bahwa city filter kurang cukup.

## Validasi

- [x] Seller bisa update city miliknya.
- [x] Buyer filter kota dan melihat produk aktif yang sesuai.
- [x] Produk inactive tetap tidak tampil.
- [x] User tidak bisa mengubah city profile user lain.
- [ ] `./gradlew build`

## Hasil Implementasi 2026-07-03

**Mode:** Supabase MCP aktif

**Migration:** `ticket_018_phase2_location_filter`

Yang diterapkan:

- Kolom `profiles.city`.
- Index `idx_profiles_city` untuk filter kota exact dari dropdown.
- Index `idx_profiles_city_lower` untuk jalur case-insensitive jika nanti dibutuhkan.
- Dashboard seller punya kontrol kota toko dengan autocomplete sederhana dan ikon `location_on`.
- Katalog buyer punya dropdown kota read-only, bukan free text penuh.
- Query katalog tetap hanya mengambil produk `active`, lalu membatasi `seller_id` berdasarkan profil seller di kota terpilih.
- Detail produk menampilkan kota seller jika tersedia.
- Tidak ada `latitude`, `longitude`, PostGIS, atau radius.

Validasi:

- Smoke SQL rollback berhasil dengan 2.500 produk dummy: seller bisa update city sendiri, update city user lain tidak mengubah row, buyer filter kota hanya melihat produk aktif dari kota tersebut, dan produk inactive tidak terlihat di context buyer.
- `EXPLAIN (analyze, buffers)` pada query dummy 2.500 produk selesai sekitar 1.3 ms; planner memakai index produk `idx_products_seller` dan `idx_products_browse`. `profiles` masih seq scan di data dummy kecil, tetapi index kota permanen sudah tersedia untuk data nyata.
- Supabase security advisor tidak menampilkan warning baru spesifik TICKET-018; warning tersisa berasal dari policy anonymous/auth setting existing di tabel lain dan `profiles`.
- Supabase performance advisor hanya menandai index baru `idx_profiles_city`/`idx_profiles_city_lower` sebagai unused karena belum ada traffic nyata setelah migrasi, plus unused index lama dari ticket lain.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil.
- `./gradlew build` masih terblokir guard release karena `app/google-services.json` tidak tersedia di environment agent.
