# Plan-Fase2.md — Marketplace UMKM

**Prasyarat:** `GOALS2.md` §1 (Fase 1 sudah tervalidasi). Urutan di bawah disusun dari yang paling sederhana/independen ke yang paling butuh keputusan desain terbuka diselesaikan dulu.
**Keputusan produk terkunci:** rating memakai model self-attested; moderasi memakai post-moderasi + report.

---

## Tahap A — Wishlist/Favorit

Paling sederhana, tidak menyentuh fitur lain.

**Ticket:** `tickets/TICKET-016-phase2-wishlist.md`

- [x] Tambah tabel + RLS di atas.
- [x] Tombol simpan/hapus dari halaman detail produk & list katalog.
- [x] Layar "Favorit Saya".

**Status 2026-07-03:** TICKET-016 selesai. Supabase migration `ticket_016_phase2_wishlist` dan follow-up `ticket_016_wishlist_reject_anonymous_sessions` diterapkan; RLS smoke rollback memvalidasi add/delete milik sendiri, duplicate ditolak unique constraint, insert/read user lain tertolak, dan produk inactive tidak muncul dari query Favorit Saya. Android debug build + unit test lewat `./gradlew assembleDebug testDebugUnitTest`; `./gradlew build` masih terblokir guard release karena `google-services.json` tidak tersedia di environment agent.

**Definition of Done:** simpan/hapus wishlist instan, tidak perlu reload katalog.

---

## Tahap B — Rating & Review Penjual

**Ticket:** `tickets/TICKET-017-phase2-rating-self-attested.md`

**Keputusan:** rating memakai model **self-attested**.

Aplikasi ini **tidak melacak status transaksi** (kesepakatan terjadi di luar sistem — lihat PRD §5.6). Artinya sistem tidak tahu pasti apakah seseorang benar-benar pernah bertransaksi dengan penjual yang dia rating. Ini beda dari marketplace dengan payment gateway, di mana rating baru bisa diberikan setelah status "pesanan selesai" tercatat sistem.

**Pilihan yang dipertimbangkan sebelum keputusan:**

| Opsi | Cara Kerja | Risiko |
|---|---|---|
| **Rating terbuka** | Siapa saja yang pernah buat `chat_room` dengan penjual boleh rating | Rawan fake review — cukup chat sekali tanpa transaksi nyata |
| **Rating self-attested** | Setelah chat berjalan, muncul prompt "Sudah transaksi dengan penjual ini?" — kedua pihak konfirmasi manual, baru rating terbuka | Lebih jujur tapi bergantung kejujuran pengguna, masih bisa dimanipulasi kalau dua akun kerja sama |
| **Tunda rating ke Fase 3** | Rating baru dibangun kalau nanti ada pelacakan transaksi (mis. setelah evaluasi payment gateway) | Aman dari fake review, tapi fitur ini tertunda tanpa kepastian |

Model self-attested dipilih karena paling seimbang antara usaha implementasi dan kualitas sinyal, tanpa perlu menunggu payment gateway yang memang belum jadi prioritas.

```sql
create table reviews (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references chat_rooms(id) on delete cascade,
  reviewer_id uuid not null references profiles(id),
  seller_id uuid not null references profiles(id),
  rating int not null check (rating between 1 and 5),
  comment text,
  created_at timestamptz not null default now(),
  unique (chat_room_id, reviewer_id) -- satu review per chat room
);

-- Kolom agregat di profiles, diupdate via trigger (bukan AVG() real-time)
alter table profiles add column rating_avg numeric(3,2) default 0;
alter table profiles add column rating_count int default 0;
```

- [x] Putuskan opsi rating: self-attested.
- [x] Tabel `reviews` + RLS (hanya reviewer sendiri yang bisa insert, hanya untuk chat_room yang dia ikut).
- [x] Trigger update `rating_avg`/`rating_count` di `profiles` saat ada review baru.
- [x] Tampilkan rating di halaman profil penjual & badge singkat di list produk.

**Status 2026-07-03:** TICKET-017 selesai. Supabase migration `ticket_017_rating_self_attested` dan follow-up `ticket_017_reviews_reviewer_index` diterapkan; smoke rollback memvalidasi buyer di chat room bisa review, user luar tertolak, duplicate per room ditolak unique constraint, dan agregat `rating_avg`/`rating_count` berubah otomatis pada insert/update/delete. Android debug build + unit test lewat `./gradlew assembleDebug testDebugUnitTest`; `./gradlew build` masih terblokir guard release karena `google-services.json` tidak tersedia di environment agent.

**Definition of Done:** rating baru bisa masuk lewat jalur yang sudah disepakati, agregat ter-update otomatis tanpa query `AVG()` mahal tiap render halaman.

---

## Tahap C — Filter Lokasi

Mulai dari pendekatan termurah dulu, sesuai `GOALS2.md` §3.

**Ticket:** `tickets/TICKET-018-phase2-location-filter.md`

**Iterasi 1 — Text match (mulai di sini):**
```sql
alter table profiles add column city text;
-- filter: where city ilike '%' || :query || '%'
```
- [x] Tambah field kota di profil penjual (diisi saat setup toko).
- [x] Filter katalog by kota (dropdown/autocomplete, bukan free text penuh biar konsisten).

**Status 2026-07-03:** TICKET-018 selesai untuk Iterasi 1 text/city filter. Supabase migration `ticket_018_phase2_location_filter` diterapkan; smoke rollback memvalidasi seller bisa update city sendiri, user lain tidak bisa mengubah city profile tersebut, buyer filter kota hanya melihat produk aktif dari kota itu, dan produk inactive tidak tampil. Android debug build + unit test lewat `./gradlew :app:assembleDebug :app:testDebugUnitTest`; `./gradlew build` masih terblokir guard release karena `google-services.json` tidak tersedia di environment agent.

**Iterasi 2 — Radius geospasial (baru kalau iterasi 1 terasa kurang, jangan mulai dari sini):**
- [ ] Evaluasi PostGIS (Supabase sudah support ekstensi ini) vs hitung manual Haversine.
- [ ] Tambah `latitude`/`longitude` ke `profiles`, index `GIST` kalau pakai PostGIS.

**Definition of Done (Iterasi 1):** pembeli bisa persempit katalog ke kota tertentu, query tetap cepat dengan index biasa.

---

## Tahap D — Optimasi Search

**Ticket:** `tickets/TICKET-019-phase2-search-optimization.md`

- [x] Ukur dulu: query search saat ini (`ILIKE` biasa dari Fase 1) mulai lambat di jumlah produk berapa? (test dengan data dummy sebelum optimasi buta).
- [x] Kalau lambat: tambah ekstensi `pg_trgm`, index GIN secukupnya. TICKET-019 hanya menambah partial GIN index untuk `name` produk aktif; `description` tidak di-index karena belum ada bukti butuh.
- [x] Kalau belum lambat: skip dulu, catat sebagai backlog — jangan optimasi sebelum ada bukti butuh. Hasil TICKET-019 membuktikan optimasi dibutuhkan.
- [x] TICKET-019 selesai pada 2026-07-03. Baseline 25.000 produk dummy menunjukkan query lama `ILIKE` name/category seq scan sekitar 52-54 ms. Diputuskan memakai `pg_trgm` dengan satu partial GIN index `idx_products_active_name_trgm` untuk `products.name` aktif saja; `description`/`category` tidak di-index trigram karena belum ada bukti butuh. Query Android keyword search diarahkan ke nama produk, kategori tetap filter terpisah. Final EXPLAIN: match sekitar 4.480 ms, no-match sekitar 0.047 ms, inactive leak count 0.

```sql
create extension if not exists pg_trgm;
create index idx_products_name_trgm on products using gin (name gin_trgm_ops);
```

**Definition of Done:** keputusan optimasi didasarkan data pengukuran, bukan asumsi.

---

## Tahap E — Moderasi Konten

**Ticket:** `tickets/TICKET-020-phase2-product-reports.md`

**Keputusan:** moderasi memakai **post-moderasi + report**.

Untuk skala UMKM awal, **post-moderasi + report** lebih realistis — pre-moderasi butuh tim/proses review manual yang belum ada.

```sql
create table product_reports (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  reporter_id uuid not null references profiles(id),
  reason text not null,
  status text not null default 'pending' check (status in ('pending','reviewed','dismissed')),
  created_at timestamptz not null default now()
);
```

- [x] Tombol "Laporkan Produk" di halaman detail.
- [x] Tabel `product_reports` + RLS (siapapun boleh insert, hanya admin/role tertentu boleh update status).
- [x] Proses tindak lanjut laporan — di awal cukup manual (kamu cek langsung dari SQL editor Supabase), belum perlu dashboard admin.

**Status 2026-07-03:** TICKET-020 selesai untuk post-moderasi + report. Supabase migration `ticket_020_phase2_product_reports` dan follow-up `ticket_020_product_reports_reporter_index` diterapkan; smoke rollback memvalidasi report produk aktif berhasil, produk inactive tertolak, duplicate report sama tertolak, user tidak bisa update status, dan owner/manual SQL bisa menandai `reviewed`. Android detail produk punya dialog "Laporkan Produk" dengan alasan laporan; token error dibatasi untuk aksi report/destructive. Android debug build + unit test lewat `./gradlew :app:assembleDebug :app:testDebugUnitTest` dan worker test lewat `./gradlew :worker:test` berhasil; `./gradlew build` masih terblokir guard release karena `google-services.json` tidak tersedia di environment agent.

**Definition of Done:** laporan tersimpan dan bisa ditinjau, walau belum ada UI admin khusus.

---

## Ringkasan Urutan

```
Tahap A (Wishlist) ── independen, kerjakan kapan saja
Tahap B (Rating)   ── self-attested, bisa dikerjakan setelah wishlist
Tahap C (Lokasi)   ── mulai iterasi 1, iterasi 2 opsional nanti
Tahap D (Search)   ── ukur dulu sebelum optimasi
Tahap E (Moderasi) ── post-moderasi + report
```

## Rekomendasi Urutan Berikutnya

Urutan rekomendasi Fase 2 dimulai dari **Tahap A — Wishlist/Favorit** melalui `tickets/TICKET-016-phase2-wishlist.md`, lalu rating self-attested, filter lokasi, optimasi search berbasis pengukuran, dan post-moderasi produk.

Eksekusi tetap menunggu user menyebut ticket spesifik pada turn/session tersebut, dan prasyarat MVP tervalidasi di `GOALS2.md` tetap harus dipenuhi sebelum fitur Fase 2 dijalankan di produk nyata.
