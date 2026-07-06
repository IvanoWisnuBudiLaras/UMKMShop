# TICKET-016: Phase 2 Wishlist/Favorit

## Tujuan

Membuat pembeli bisa menyimpan produk favorit untuk dilihat kembali tanpa harus memulai chat. Ini menurunkan friction eksplorasi katalog sesuai `GOALS2.md`.

## Scope

- Tambah tabel `wishlists`.
- Enable RLS untuk `wishlists`.
- Policy user hanya bisa manage wishlist miliknya sendiri.
- Tombol simpan/hapus favorit dari list katalog.
- Tombol simpan/hapus favorit dari detail produk.
- Layar "Favorit Saya".
- State UI optimistic agar simpan/hapus terasa instan.

## Acceptance Criteria

- User login bisa menambahkan produk aktif ke wishlist.
- User login bisa menghapus produk dari wishlist.
- Produk yang sama tidak bisa masuk wishlist user yang sama lebih dari sekali.
- User tidak bisa membaca atau mengubah wishlist user lain.
- Layar Favorit Saya menampilkan produk yang masih aktif.
- Produk inactive tidak tampil di daftar favorit umum, kecuali diputuskan nanti untuk ditampilkan sebagai item tidak tersedia.

## Catatan Implementasi

SQL awal:

```sql
create table wishlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  product_id uuid not null references products(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (user_id, product_id)
);

create index idx_wishlists_user_created on wishlists(user_id, created_at desc);
create index idx_wishlists_product on wishlists(product_id);

alter table wishlists enable row level security;

create policy "wishlists_select_own" on wishlists
  for select to authenticated
  using (user_id = auth.uid());

create policy "wishlists_insert_own_active_product" on wishlists
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from products p
      where p.id = product_id and p.status = 'active'
    )
  );

create policy "wishlists_delete_own" on wishlists
  for delete to authenticated
  using (user_id = auth.uid());
```

- Jangan memakai `for all using (user_id = auth.uid())` tanpa `with check`; policy insert harus memastikan `user_id` tidak bisa dipalsukan.
- Query Favorit Saya perlu join ke `products` dan `product_images`, mengikuti visibility produk aktif.
- Jika Supabase MCP tidak tersedia, siapkan SQL dan checklist manual.

## Validasi

- [x] Supabase migration/table/policy berhasil.
- [x] User A bisa tambah dan hapus wishlist miliknya.
- [x] User A tidak bisa melihat wishlist User B.
- [x] Duplicate wishlist ditolak unique constraint.
- [ ] `./gradlew build`
- [ ] Smoke manual katalog/detail/favorit jika emulator/device tersedia.

## Hasil Eksekusi

**Tanggal:** 2026-07-03
**Mode:** Supabase MCP aktif
**Project:** UMKMShop (`jhpcpccmzmukiuykkmpq`)
**Migrations:**

- `ticket_016_phase2_wishlist`
- `ticket_016_wishlist_reject_anonymous_sessions`

Yang sudah diterapkan:

- Tabel `public.wishlists` dengan unique constraint `(user_id, product_id)`.
- Index `idx_wishlists_user_created` dan `idx_wishlists_product`.
- RLS aktif untuk `wishlists`.
- Grant Data API eksplisit: `authenticated` hanya `select`, `insert`, `delete`; `anon` dicabut.
- Policy `select`, `insert`, dan `delete` hanya untuk row milik user non-anonymous; insert juga mewajibkan produk masih `active`.
- Android menampilkan tombol favorite di katalog dan detail produk, memakai state aktif/nonaktif dan microinteraction ringan 120ms.
- Layar "Favorit Saya" menampilkan produk favorit yang masih aktif.
- UI memakai optimistic update dan rollback message saat operasi Supabase gagal.
- Follow-up state sync: optimistic add dari katalog/detail sekarang juga memasukkan produk ke state Favorit yang sudah pernah dibuka, dan toggle memakai state produk dari surface yang diklik agar salinan detail/katalog yang stale tidak membuat delete/insert ter-skip.

Validasi:

- Smoke SQL rollback berhasil untuk tambah/hapus wishlist milik sendiri.
- Duplicate wishlist ditolak unique constraint.
- User A tidak bisa insert/read wishlist User B.
- Produk inactive tidak tampil dari query Favorit Saya.
- Supabase security advisor setelah follow-up tidak lagi menampilkan warning untuk `public.wishlists`; warning tersisa berasal dari tabel/ekstensi di luar scope ticket ini.
- `./gradlew build` sudah dicoba tetapi gagal di guard release karena `google-services.json` tidak tersedia.
- `./gradlew assembleDebug testDebugUnitTest` berhasil.
- Follow-up state sync divalidasi ulang dengan `./gradlew assembleDebug testDebugUnitTest`.
- Smoke manual emulator/device belum dijalankan karena environment agent tidak menyediakan device.
