# TICKET-017: Phase 2 Rating Self-Attested

## Tujuan

Membangun rating dan review penjual dengan model self-attested: rating baru tersedia setelah percakapan terjadi dan reviewer menyatakan transaksi sudah dilakukan di luar sistem.

## Scope

- Tambah tabel `reviews`.
- Tambah kolom agregat `rating_avg` dan `rating_count` di `profiles`.
- Tambah trigger untuk update agregat rating saat review dibuat/diubah/dihapus.
- RLS agar reviewer hanya bisa membuat review dari `chat_rooms` yang ia ikuti.
- UI prompt self-attested: "Sudah transaksi dengan penjual ini?"
- UI submit rating 1-5 dan komentar opsional.
- Tampilkan rating di profil penjual, detail produk, dan badge singkat di katalog.

## Acceptance Criteria

- Buyer hanya bisa review seller dari chat room yang ia ikuti.
- Reviewer tidak bisa review dirinya sendiri.
- Satu reviewer hanya bisa membuat satu review per chat room.
- `seller_id` review harus sama dengan `chat_rooms.seller_id`.
- Rating hanya menerima 1 sampai 5.
- Agregat `profiles.rating_avg` dan `profiles.rating_count` otomatis konsisten.
- Halaman produk dan katalog membaca agregat, bukan menghitung `AVG()` realtime setiap render.

## Catatan Implementasi

SQL awal perlu diperketat saat implementasi:

```sql
create table reviews (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references chat_rooms(id) on delete cascade,
  reviewer_id uuid not null references profiles(id) on delete cascade,
  seller_id uuid not null references profiles(id) on delete cascade,
  rating int not null check (rating between 1 and 5),
  comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (chat_room_id, reviewer_id)
);

alter table profiles add column rating_avg numeric(3,2) not null default 0;
alter table profiles add column rating_count int not null default 0;
```

- Gunakan RLS `to authenticated`, bukan `auth.role()`.
- Gunakan trigger/function dengan `security definer` hanya jika perlu, pin `search_path`, dan revoke direct execute dari client roles.
- Jangan menambahkan status transaksi/payment; PRD tetap menyatakan transaksi di luar sistem.
- Model ini mengurangi fake review, tapi tidak menghilangkan kolusi dua akun. Itu diterima sebagai tradeoff Fase 2.

## Validasi

- [x] User yang ikut chat room bisa submit review setelah konfirmasi self-attested.
- [x] User yang tidak ikut chat room tidak bisa submit review.
- [x] Duplicate review pada room yang sama ditolak.
- [x] Rating agregat berubah sesuai insert/update/delete review.
- [ ] `./gradlew build`
- [ ] Smoke manual UI rating jika emulator/device tersedia.

## Hasil Eksekusi

**Tanggal:** 2026-07-03
**Mode:** Supabase MCP aktif
**Project:** UMKMShop (`jhpcpccmzmukiuykkmpq`)
**Migrations:**

- `ticket_017_rating_self_attested`
- `ticket_017_reviews_reviewer_index`

Yang sudah diterapkan:

- Kolom agregat `profiles.rating_avg` dan `profiles.rating_count`.
- Tabel `public.reviews` dengan unique constraint `(chat_room_id, reviewer_id)`, check rating 1-5, dan marker `self_attested`.
- Trigger validasi agar review hanya untuk buyer pada `chat_rooms` valid, `seller_id` wajib sama dengan seller room, dan reviewer tidak bisa review dirinya sendiri.
- Trigger agregat agar `rating_avg`/`rating_count` otomatis konsisten saat review dibuat, diubah, atau dihapus.
- RLS + grant eksplisit Data API untuk `authenticated`, dengan anonymous session ditolak.
- UI chat buyer menampilkan prompt "Sudah transaksi dengan penjual ini?", checkbox self-attested, rating 1-5, dan komentar opsional.
- Katalog, detail produk, dan kartu profil seller membaca badge rating dari agregat `profiles`, bukan `AVG()` realtime.

Validasi:

- Smoke SQL rollback berhasil untuk buyer valid, user luar, duplicate review, dan agregat insert/update/delete.
- Supabase security advisor tidak menampilkan warning untuk `public.reviews`; warning yang tersisa berasal dari tabel/setting di luar scope ticket ini.
- Supabase performance advisor tidak lagi menampilkan unindexed FK untuk `public.reviews`; warning tersisa adalah unused index pada database yang belum punya traffic.
- `./gradlew assembleDebug testDebugUnitTest` berhasil.
- `./gradlew build` sudah dicoba tetapi gagal di guard release karena `google-services.json` tidak tersedia.
- Smoke manual emulator/device belum dijalankan karena environment agent tidak menyediakan device.
