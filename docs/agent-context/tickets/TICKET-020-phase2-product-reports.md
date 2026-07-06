# TICKET-020: Phase 2 Product Reports

## Tujuan

Membuat jalur post-moderasi produk: produk tetap tayang dulu, tetapi pembeli bisa melaporkan produk spam/tidak pantas agar bisa ditinjau manual.

## Scope

- Tambah tabel `product_reports`.
- Enable RLS untuk report.
- User authenticated bisa melaporkan produk aktif.
- Satu user tidak bisa spam report produk yang sama dengan alasan yang sama.
- Tombol "Laporkan Produk" di detail produk.
- Form alasan laporan.
- Proses tindak lanjut awal manual dari Supabase SQL Editor, belum perlu dashboard admin.

## Acceptance Criteria

- User login bisa membuat report untuk produk aktif.
- User tidak bisa membuat report untuk produk inactive.
- User tidak bisa mengubah status report sendiri.
- Status report default `pending`.
- Admin/manual SQL bisa menandai `reviewed` atau `dismissed`.
- Produk yang dilaporkan tidak otomatis hilang dari katalog pada iterasi awal.

## Catatan Implementasi

SQL awal:

```sql
create table product_reports (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  reporter_id uuid not null references profiles(id) on delete cascade,
  reason text not null,
  status text not null default 'pending' check (status in ('pending','reviewed','dismissed')),
  created_at timestamptz not null default now(),
  unique (product_id, reporter_id, reason)
);

create index idx_product_reports_status_created on product_reports(status, created_at desc);
create index idx_product_reports_product on product_reports(product_id);

alter table product_reports enable row level security;

create policy "product_reports_insert_authenticated_active_product" on product_reports
  for insert to authenticated
  with check (
    reporter_id = auth.uid()
    and exists (
      select 1 from products p
      where p.id = product_id and p.status = 'active'
    )
  );

create policy "product_reports_select_own" on product_reports
  for select to authenticated
  using (reporter_id = auth.uid());
```

- Jangan membuat admin dashboard dulu kecuali diminta; proses awal cukup manual.
- Jika nanti ada role admin di `app_metadata`, policy admin harus memakai claim yang tidak user-editable.
- Jangan memakai `raw_user_meta_data` untuk role admin.

## Validasi

- [x] User authenticated bisa membuat report produk aktif.
- [x] User tidak bisa report produk inactive.
- [x] Duplicate report yang sama ditolak.
- [x] User tidak bisa update `status`.
- [ ] `./gradlew build`
- [x] `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- [x] `./gradlew :worker:test`
- [ ] Smoke manual report dari detail produk jika emulator/device tersedia.

## Status 2026-07-03

TICKET-020 selesai untuk jalur post-moderasi + report. Supabase migration `ticket_020_phase2_product_reports` dan follow-up `ticket_020_product_reports_reporter_index` diterapkan; smoke rollback memvalidasi user authenticated bisa report produk aktif, produk inactive tertolak, duplicate report dengan alasan sama tertolak, user tidak bisa update status, dan owner/manual SQL bisa menandai report `reviewed`. Android detail produk menampilkan aksi "Laporkan Produk" dengan dialog alasan; token error hanya dipakai pada aksi report/destructive. Android debug build + unit test lewat `./gradlew :app:assembleDebug :app:testDebugUnitTest` dan worker test lewat `./gradlew :worker:test` berhasil. `./gradlew build` masih terblokir guard release karena `google-services.json` tidak tersedia di environment agent.
