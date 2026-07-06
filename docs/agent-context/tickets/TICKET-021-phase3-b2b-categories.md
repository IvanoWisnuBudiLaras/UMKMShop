# TICKET-021: Phase 3 B2B Categories

## Tujuan

Mengubah pilihan kategori produk agar sesuai pivot Fase 3: dari marketplace C2C umum menjadi B2B rantai pasok bahan baku/setengah jadi.

## Scope

- Update pilihan kategori di form tambah/edit produk Android.
- Update filter kategori katalog.
- Pastikan kategori baru konsisten di UI seller dan buyer.
- Catat positioning B2B di copy UI seperlunya tanpa mengubah brand besar.

Kategori Fase 3:

- Makanan (bahan makanan)
- Minuman (bahan belum diolah)
- Komponen Motor
- Komponen HP
- Komponen IoT

## Acceptance Criteria

- Seller hanya melihat opsi kategori Fase 3 pada form produk.
- Buyer bisa filter katalog berdasarkan kategori Fase 3.
- Produk lama dengan kategori legacy tetap tidak membuat app crash; tampil dengan fallback label bila masih ada data lama.
- Tidak ada migrasi schema untuk kategori karena `products.category` tetap `text`.

## Catatan Implementasi

- Baca `GOALS3.md`, `PLAN3.md`, `PRD-v2.md`, `BACKEND_SPEC-v2.md`, dan `DESIGN.md`.
- Ikuti desain ringan di `DESIGN.md`; dropdown kategori harus sederhana dan mudah dipakai di device low-mid.
- Jangan menambah tabel category kecuali ada kebutuhan admin dinamis yang eksplisit.

## Validasi

- [ ] `./gradlew build`
- [x] Form tambah/edit produk menampilkan kategori Fase 3.
- [x] Filter katalog memakai kategori Fase 3.
- [x] Produk kategori lama tidak crash dan punya fallback display.

## Hasil Implementasi

- Android menambahkan source of truth kategori Fase 3 di `ProductCategories.kt`.
- Form tambah/edit produk seller memakai dropdown kategori Fase 3 dan menolak kategori non-Fase 3 saat simpan.
- Filter katalog buyer memakai dropdown kategori Fase 3 dengan opsi "Semua kategori".
- Label kategori produk memakai fallback `Kategori lama` untuk data legacy dan `Tanpa kategori` untuk data kosong.
- Copy katalog buyer diubah ringan ke positioning B2B: "Katalog Bahan & Komponen".

## Hasil Validasi

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` berhasil.
- `./gradlew build` belum berhasil karena guard release project: `google-services.json is required for release builds so Firebase Messaging is configured.`
