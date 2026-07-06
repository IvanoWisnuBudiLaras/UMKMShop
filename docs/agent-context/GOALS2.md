# GOALS2.md — Marketplace UMKM Fase 2

**Prasyarat masuk fase ini:** Fase 1 (MVP) sudah memenuhi kriteria "MVP tervalidasi" di `GOALS.md` §5 — ada pengguna nyata yang berhasil transaksi via app, bukan cuma hasil testing internal. Kalau belum, fokus dulu ke situ; menambah fitur di atas fondasi yang belum tervalidasi cuma menambah biaya perawatan tanpa data yang membenarkan.

---

## 1. Fitur Cakupan Fase 2

Sesuai `PRD.md` §11: wishlist/favorit produk, rating & review penjual, filter lokasi/radius, optimasi search, moderasi konten produk.

## 2. Tujuan per Fitur

| Fitur | Tujuan |
|---|---|
| Wishlist/Favorit | Pembeli bisa tandai produk untuk dilihat lagi nanti, tanpa harus chat dulu — turunkan friction eksplorasi |
| Rating & Review | Beri sinyal kepercayaan ke pembeli baru sebelum chat penjual yang belum dikenal |
| Filter Lokasi | Pembeli bisa persempit katalog ke area terjangkau — relevan karena banyak transaksi UMKM ujungnya COD |
| Optimasi Search | Hasil pencarian tetap relevan & cepat walau jumlah produk sudah ribuan, bukan lagi puluhan |
| Moderasi Konten | Cegah produk spam/tidak pantas merusak kepercayaan pengguna terhadap katalog secara keseluruhan |

## 3. Tujuan Teknis

- Semua fitur baru tetap taat prinsip Fase 1: **minim moving parts baru.** Sebelum menambah dependency/ekstensi baru, cek dulu apakah Postgres native (index, extension bawaan Supabase) sudah cukup — pola yang sama seperti keputusan `pgmq` dibanding queue custom di Fase 1.
- Rating & review tidak menambah beban baca yang signifikan ke halaman detail produk — pertimbangkan agregat (rata-rata rating) dihitung via materialized view atau kolom denormalisasi yang diupdate trigger, bukan `AVG()` real-time tiap kali halaman dibuka.
- Filter lokasi dimulai dari pendekatan paling murah (text match kota/kecamatan) sebelum lompat ke geospasial radius (PostGIS) — konsisten dengan prinsip "jangan over-engineer sebelum data membuktikan butuh."

## 4. Tujuan Pembelajaran

- **Full-text search di Postgres** (`pg_trgm`, `tsvector`/`tsquery`) — paham kapan index GIN lebih tepat dibanding `ILIKE` biasa, dan trade-off resource-nya.
- **Materialized view vs trigger-based denormalization** untuk agregat rating — dua pendekatan beda untuk masalah yang sama, bagus dipahami trade-off keduanya (freshness data vs write overhead).
- **Geospasial dasar** (Haversine manual vs ekstensi PostGIS) kalau filter radius jadi dibangun — kesempatan paham cara kerja query jarak di database relasional, bukan cuma import library.
- **Desain sistem moderasi/trust** — soal yang lebih "sistem" daripada "kode": bagaimana menyeimbangkan otomatisasi vs review manual tanpa bikin proses jadi bottleneck.

## 5. Non-Goals (Tetap Berlaku di Fase 2)

- Payment gateway masih di luar cakupan — Fase 2 ini murni penyempurnaan katalog & kepercayaan, bukan transaksi.
- iOS/cross-platform belum dievaluasi ulang.
- Tidak menambah server/infrastruktur baru di luar server 2GB yang sudah ada, kecuali data pembuktikan itu benar-benar perlu.

## 6. Kriteria Sukses Fase 2

| Level | Kriteria |
|---|---|
| Wishlist jalan | Pembeli bisa simpan & lihat kembali daftar favorit tanpa lag berarti |
| Rating jalan | Ada mekanisme submit rating yang tahan dari spam/fake review paling dasar (lihat catatan desain di `Plan-Fase2.md`) |
| Filter lokasi jalan | Query filter tidak memperlambat browsing katalog secara terasa (tetap di bawah ambang UX yang sama seperti Fase 1) |
| Search tetap relevan | Hasil pencarian untuk kata kunci umum tidak didominasi noise saat jumlah produk sudah signifikan lebih banyak dari MVP |
| Moderasi berjalan | Ada jalur report produk yang bisa ditindaklanjuti, walau masih manual di tahap awal |
