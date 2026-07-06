# TICKET-002: Supabase Schema Base

## Tujuan

Menyiapkan fondasi database Supabase untuk flow bisnis PRD: user punya profil, seller punya produk dan gambar, buyer bisa membuka chat room berbasis produk, pesan tersimpan, dan worker punya sumber token push.

## Scope

- Buat project Supabase Cloud untuk UMKMShop.
- Aktifkan extension `pgmq` dan `pg_cron`.
- Verifikasi fungsi `gen_random_uuid()` tersedia untuk UUID default.
- Jalankan DDL `profiles`, `products`, `product_images`, `chat_rooms`, `messages`, dan `push_tokens` dari `BACKEND_SPEC.md`.
- Buat index untuk browse produk, gambar per produk, chat buyer/seller, message history, dan reminder.
- Buat queue `notifications` dengan `select pgmq.create('notifications')`.

## Mode Eksekusi

### Optional — Supabase MCP dan Agent Skills

Supabase MCP dan Agent Skills boleh dipakai untuk membantu agent bekerja lebih akurat, tetapi keduanya punya fungsi berbeda:

- Agent Skills (`npx skills add supabase/agent-skills`) hanya menambahkan instruksi/resources untuk coding agent.
- MCP client memberi tool tambahan agar agent bisa berinteraksi dengan Supabase jika server MCP dan credential sudah dikonfigurasi.
- Keduanya tidak otomatis memberi akses database; project ref, auth, CLI login, atau connection string tetap perlu tersedia.

Jika user meminta setup ini, lakukan sebelum Mode A/Mode B:

- Install Supabase Agent Skills jika toolchain mendukung.
- Konfigurasi Supabase MCP di MCP client yang sedang dipakai.
- Verifikasi apakah agent benar-benar mendapat tool Supabase, bukan hanya instruksi tambahan.

### Mode A — Agent Tidak Punya Akses Supabase

Ini mode default jika belum ada project ref, database password, CLI login, atau koneksi Postgres.

Agent tetap bisa mengerjakan bagian repo:

- Siapkan SQL schema final dari `BACKEND_SPEC.md`.
- Siapkan SQL verifikasi untuk `gen_random_uuid()`, tabel, constraint, index, dan queue.
- Tulis urutan eksekusi yang harus dijalankan user di Supabase SQL Editor.
- Tandai ticket sebagai **blocked on external execution** sampai user menjalankan SQL dan memberi hasil/error.

Ticket belum boleh dianggap selesai penuh sebelum hasil eksekusi Supabase diverifikasi.

### Mode B — Agent Punya Akses Supabase

Mode ini hanya dipakai jika user memberi akses eksplisit, misalnya:

- Supabase CLI sudah login dan linked ke project.
- Connection string Postgres tersedia di environment lokal.
- User meminta agent menjalankan SQL langsung.

Agent boleh menjalankan migrasi/SQL, lalu mencatat output validasi di ticket atau `DOCS.md`.

## Acceptance Criteria

- Semua tabel inti tersedia.
- Insert row pada tabel yang memakai `default gen_random_uuid()` berhasil tanpa mengisi `id` manual.
- Constraint FK aktif sesuai relasi PRD.
- `products.price` tidak bisa negatif.
- `products.status` hanya menerima `active` atau `inactive`.
- Kombinasi `(buyer_id, seller_id, product_id)` unik di `chat_rooms`.
- Queue `notifications` tersedia.

## Catatan Implementasi

- Jangan membuat tabel transaksi/payment karena PRD menyatakan transaksi selesai di luar sistem.
- Jangan membuat tabel queue custom karena BACKEND_SPEC menetapkan `pgmq`.
- Jika project Supabase sudah ada, dokumentasikan project ref dan jangan membuat project baru.
- Tanpa akses Supabase, hasil kerja agent adalah SQL siap pakai dan checklist, bukan eksekusi database.

## Validasi

- [x] `select gen_random_uuid();` berhasil.
- [x] Extension `pgmq` dan `pg_cron` aktif.
- [x] Insert manual minimal 2 profile dummy.
- [x] Insert produk untuk seller.
- [x] Insert chat room antara buyer dan seller pada satu produk.
- [x] Insert message pada room tersebut.
- [x] Cek queue `notifications` sudah bisa diakses.

## Hasil Eksekusi

**Tanggal:** 2026-07-02
**Mode:** Mode B — Supabase MCP aktif
**Project:** UMKMShop (`jhpcpccmzmukiuykkmpq`)
**Migrations:**

- `ticket_002_supabase_schema_base`
- `ticket_002_advisor_followups`

Yang sudah diterapkan:

- Extension `pgmq` aktif versi `1.5.1`.
- Extension `pg_cron` aktif versi `1.6.4`.
- `gen_random_uuid()` tersedia dan menghasilkan UUID.
- Tabel `profiles`, `products`, `product_images`, `chat_rooms`, `messages`, dan `push_tokens` tersedia.
- Index browse produk, gambar per produk, chat buyer/seller, reminder, dan message history tersedia.
- Follow-up index FK `idx_chat_rooms_product` dan `idx_messages_sender` ditambahkan setelah Supabase performance advisor menandai FK tanpa covering index.
- Queue `notifications` tersedia via `pgmq.meta`.

Validasi smoke test:

- Insert test untuk 2 `auth.users` + `profiles` berhasil.
- Insert `products`, `product_images`, `chat_rooms`, `messages`, dan `push_tokens` berhasil tanpa mengisi UUID manual pada tabel yang memakai `default gen_random_uuid()`.
- Constraint `products.price >= 0` menolak harga negatif.
- Constraint `products.status in ('active','inactive')` menolak status selain nilai yang diizinkan.
- Unique constraint `(buyer_id, seller_id, product_id)` pada `chat_rooms` menolak room duplikat.
- Queue `notifications` berhasil menerima test job dan `pgmq.delete()` menghapus job tersebut.

Error yang ditemukan dan diselesaikan:

- Validasi awal sempat gagal saat test insert langsung ke `auth.users` karena kolom `confirmed_at` adalah generated column di schema Supabase saat ini. Test diperbaiki dengan hanya mengisi `email_confirmed_at`, lalu validasi ulang berhasil.
- Supabase security advisor menemukan function `public.rls_auto_enable()` sebagai `SECURITY DEFINER` yang masih executable oleh `anon`/`authenticated`. Hak execute publik sudah dicabut di migrasi follow-up; validasi ulang menunjukkan `anon`, `authenticated`, dan `public` tidak bisa execute function tersebut.

Catatan lanjutan:

- Changelog Supabase 2026-04-28 menyebut tabel baru tidak selalu otomatis terekspos ke Data API. Sesi RLS/security berikutnya perlu memeriksa exposure/grant Data API selain policy RLS.
- Supabase security advisor masih menampilkan `RLS Enabled No Policy` untuk tabel public karena RLS sudah aktif tetapi policy belum dibuat. Ini sesuai urutan kerja dan harus diselesaikan di TICKET-003.
- Supabase performance advisor masih menampilkan `Unused Index` untuk index yang baru dibuat/belum dipakai traffic nyata. Ini normal untuk schema baru, bukan error migrasi.
