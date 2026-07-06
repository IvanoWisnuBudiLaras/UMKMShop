# TICKET-010: Chat Realtime

## Tujuan

Mewujudkan flow chat PRD: buyer dan seller bisa bercakap-cakap realtime dalam chat room yang terikat ke produk.

## Scope

- Implement create/get `chat_rooms` dari tombol chat di detail produk.
- Implement layar chat untuk kirim dan tampilkan `messages`.
- Subscribe Supabase Realtime untuk pesan baru per room.
- Implement list percakapan user berdasarkan `chat_rooms`.

## Acceptance Criteria

- Kombinasi buyer, seller, dan product hanya menghasilkan satu chat room.
- Buyer bisa mengirim pesan ke seller.
- Seller bisa membalas di room yang sama.
- Pesan baru muncul realtime di device/emulator lain.
- User ketiga tidak bisa membaca room/messages.

## Catatan Implementasi

- Chat selalu berangkat dari produk, bukan chat generik.
- Gunakan unique constraint `(buyer_id, seller_id, product_id)` sebagai guard terhadap duplicate room.
- Jangan simpan status pembayaran atau transaksi di chat room.

## Validasi

- [x] `./gradlew build`
- [ ] Manual chat dua akun di dua device/emulator.
- [x] Manual cek RLS sebagai user ketiga.

## Status 2026-07-02

Implementasi Android chat realtime selesai:

- Tombol chat dari detail produk membuat/membuka `chat_rooms` yang selalu terikat `product_id`.
- Guard duplicate room memakai kombinasi `(buyer_id, seller_id, product_id)`; app query room existing dulu dan fallback query ulang jika insert kalah race dengan unique constraint.
- Layar room menampilkan riwayat `messages`, mengirim pesan sebagai session aktif, dan subscribe Supabase Realtime `postgresChangeFlow` khusus `room_id`.
- List percakapan menampilkan room user sebagai buyer/seller, nama produk, lawan bicara, dan preview pesan terakhir.
- Supabase migration `ticket_010_messages_realtime_publication` menambahkan `public.messages` ke publication `supabase_realtime`.
- Review follow-up menambahkan Kotlin Android plugin eksplisit, cleanup Realtime channel dengan `NonCancellable`, dan `chat_rooms.last_message_id` supaya list chat mengambil satu pesan terakhir per room tanpa download full history.
- Review follow-up migration `ticket_010_index_last_message_fk` mempertahankan index `last_message_id` sebagai foreign-key index untuk operasi FK/set-null.

Validasi nyata:

- `./gradlew clean build` berhasil.
- Supabase MCP rollback smoke menghasilkan:
  - `duplicate_inserted=0`
  - buyer melihat `1` room dan `2` pesan
  - seller melihat `1` room dan `2` pesan
  - `seller_reply_marked=true`
  - user ketiga melihat `0` room dan `0` pesan
  - `messages_in_realtime_publication=true`
- Review smoke memvalidasi `last_message_id=40000000-0000-0000-0000-000000000051`, seller melihat `1` room/`2` pesan, dan user ketiga melihat `0` room/`0` pesan dalam rollback transaction.
- Cleanup test terverifikasi: `remaining_auth_users=0`, `remaining_products=0`, `remaining_rooms=0`, `remaining_messages=0`.
- Security advisor Supabase bersih (`lints=[]`).
- Performance advisor dapat menandai index `idx_chat_rooms_last_message_id` sebagai unused sampai ada workload nyata; index tetap dipertahankan karena `last_message_id` adalah FK.

Belum divalidasi:

- Smoke manual dua akun di dua device/emulator belum dieksekusi karena `adb` tidak tersedia di environment agent.
- Performance advisor Supabase gagal dieksekusi karena error internal SQL advisor: `syntax error at or near "'storage.buckets'"`.
