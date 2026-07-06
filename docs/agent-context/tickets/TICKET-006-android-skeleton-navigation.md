# TICKET-006: Android Skeleton and Navigation

## Tujuan

Menyiapkan kerangka Android Native agar semua flow PRD punya tempat implementasi yang jelas sebelum integrasi Supabase penuh.

## Scope

- Rapikan struktur package di `app/src/main/java/com/application/umkmshop/`.
- Setup Jetpack Compose navigation.
- Buat shell layar auth, buyer catalog, seller dashboard, product form, product detail, chat list, dan chat room.
- Tambahkan mode Pembeli/Penjual berbasis state lokal.

## Acceptance Criteria

- App terbuka tanpa crash.
- User bisa navigasi antar shell layar utama.
- Mode Pembeli/Penjual bisa ditoggle.
- Belum ada mock data yang mengunci bentuk data bertentangan dengan PRD/BACKEND_SPEC.

## Catatan Implementasi

- Gunakan nama layar sesuai flow PRD agar ticket berikutnya mudah melanjutkan.
- Jangan mulai integrasi Supabase di ticket ini kecuali dependency dasar memang sudah diperlukan untuk build.

## Validasi

- [x] `./gradlew build` — berhasil pada 2026-07-02.
- [ ] Smoke manual di emulator/device — blocker: `adb` tidak tersedia di environment agent (`/bin/bash: line 1: adb: command not found`).

## Hasil Implementasi

- Struktur Android dipisah dari template awal: `MainActivity` hanya memasang theme dan root app.
- Route shell didefinisikan terpusat untuk auth, katalog pembeli, dashboard seller, form produk, detail produk, daftar chat, dan ruang chat.
- Navigation Compose dipakai untuk berpindah antar shell layar utama.
- Toggle mode Pembeli/Penjual berbasis state lokal mengarahkan user ke katalog pembeli atau dashboard seller.
- Tidak ada integrasi Supabase dan tidak ada mock entity produk/chat yang mengunci kontrak data ticket berikutnya.
