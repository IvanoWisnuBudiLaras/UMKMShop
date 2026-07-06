# TICKET-019: Phase 2 Search Optimization

## Tujuan

Mengukur dan meningkatkan relevansi/kecepatan search katalog saat jumlah produk mulai besar, tanpa optimasi buta sebelum ada data.

## Scope

- Buat data dummy untuk mengukur search katalog pada ribuan produk.
- Ukur query search Fase 1 yang masih memakai pola sederhana.
- Tentukan apakah `ILIKE` masih cukup.
- Jika lambat/noisy, aktifkan `pg_trgm` dan tambah GIN index yang tepat.
- Update query Android agar tetap compatible dengan strategi search terpilih.

## Acceptance Criteria

- Ada angka baseline search sebelum optimasi.
- Keputusan optimasi dicatat: lanjut `ILIKE`, `pg_trgm`, atau backlog full-text search.
- Jika `pg_trgm` dipakai, index dibuat dan query plan membaik.
- Search tetap memfilter produk aktif.
- Search tidak membocorkan produk inactive.

## Catatan Implementasi

SQL opsi optimasi:

```sql
create extension if not exists pg_trgm;
create index idx_products_name_trgm on products using gin (name gin_trgm_ops);
create index idx_products_description_trgm on products using gin (description gin_trgm_ops);
```

- Jangan langsung membuat banyak index tanpa `EXPLAIN`/pengukuran.
- Untuk MVP ribuan produk, trigram pada `name` mungkin cukup; `description` bisa mahal dan perlu bukti.
- Jika query search join profil/lokasi, ukur kombinasi filter status, city, dan keyword.

## Validasi

- [x] Baseline query search dicatat.
- [x] `EXPLAIN`/advisor dicek jika Supabase MCP tersedia.
- [x] Keputusan optimasi dicatat di ticket atau `DOCS.md`.
- [x] Validasi Gradle dijalankan untuk perubahan Android.

## Catatan Implementasi 2026-07-03

Baseline diukur lewat Supabase MCP dengan 25.000 produk dummy rollback:

- Dataset: 21.875 produk aktif, 3.125 inactive.
- Query lama `status = 'active' and (name ilike '%beras%' or category ilike '%beras%')` melakukan seq scan, membuang 23.125 row, dan selesai sekitar 52.132 ms.
- Query lama no-match `'%zzztidakada%'` juga seq scan 25.000 row dan selesai sekitar 54.013 ms.
- Query dengan city join tetap memakai index seller dari TICKET-018 dan selesai sekitar 17.577 ms pada dataset dummy, tetapi search keyword masih tidak terbantu oleh query `name OR category`.

Keputusan: `ILIKE` polos tidak cukup untuk katalog puluhan ribu row. Dipakai `pg_trgm` dengan satu partial GIN index saja:

```sql
create extension if not exists pg_trgm with schema extensions;

create index if not exists idx_products_active_name_trgm
  on public.products using gin (name extensions.gin_trgm_ops)
  where status = 'active';
```

Tidak dibuat index trigram untuk `description` atau `category` karena belum ada bukti butuh:

- `description` lebih mahal untuk write/storage dan belum dipakai search UI.
- `category` tetap field filter terpisah di Android, bukan dicampur ke keyword search.

Query Android diubah agar keyword search hanya `name ilike`, tetap selalu memfilter `status = 'active'`. Label UI menjadi "Cari nama produk" supaya jelas dan tetap compact sesuai `DESIGN.md`.

Validasi setelah migration `ticket_019_search_name_trgm`:

- Query final `status = 'active' and name ilike '%beras%'` memakai `Bitmap Index Scan on idx_products_active_name_trgm` dan selesai sekitar 4.480 ms.
- Query final no-match `status = 'active' and name ilike '%zzztidakada%'` memakai index yang sama dan selesai sekitar 0.047 ms.
- Dummy inactive matches ada 625 row, tetapi query app mengembalikan `inactive_leak_rows=0` karena predicate `status = 'active'` tetap wajib.
- Supabase performance advisor hanya memberi info `unused_index` dari ticket sebelumnya; security advisor hanya memberi warning anonymous/leaked-password yang sudah berada di luar scope TICKET-019.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` berhasil. `./gradlew build` tetap gagal di guard release karena `google-services.json is required for release builds so Firebase Messaging is configured.`
