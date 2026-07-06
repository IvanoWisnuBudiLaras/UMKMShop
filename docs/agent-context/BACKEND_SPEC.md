# Backend Technical Spec — Marketplace UMKM

**Status:** Draft v1.0
**Pelengkap dari:** PRD.md (mobile)
**Stack:** Supabase Cloud (Postgres + Auth + Storage + Realtime) + Supabase Queues (`pgmq`) + `pg_cron` + Kotlin/Ktor worker (server 2GB)

---

## 1. Scope Dokumen

Dokumen ini menutup bagian yang sengaja tidak dibahas di PRD: skema SQL lengkap, RLS policies, kontrak antrean/job, dan spesifikasi worker. Audiensnya developer, bukan stakeholder produk.

**Prinsip desain:** sebagian besar CRUD (produk, chat, pesan) **langsung dari mobile app via Supabase client SDK** dengan keamanan ditegakkan oleh RLS — bukan lewat REST API custom. Backend custom (Kotlin worker) hanya menangani proses async yang butuh ketahanan (notifikasi, reminder), bukan sebagai API layer untuk CRUD biasa.

---

## 2. Skema Database (DDL)

```sql
-- Ekstensi yang dibutuhkan
-- Catatan: gen_random_uuid() dipakai untuk UUID default.
-- Di environment Postgres/Supabase modern fungsi ini bisa tersedia tanpa pgcrypto.
create extension if not exists pgmq;       -- Supabase Queues
create extension if not exists pg_cron;    -- Scheduler

-- =========================
-- profiles
-- =========================
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,
  phone text,
  avatar_url text,
  created_at timestamptz not null default now()
);

-- =========================
-- products
-- =========================
create table products (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references profiles(id) on delete cascade,
  name text not null,
  price numeric(12,2) not null check (price >= 0),
  description text,
  category text,
  status text not null default 'active' check (status in ('active','inactive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index idx_products_seller on products(seller_id);
create index idx_products_browse on products(status, category, created_at desc);

-- =========================
-- product_images
-- =========================
create table product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  image_url text not null,
  sort_order int not null default 0
);

create index idx_product_images_product on product_images(product_id);

-- =========================
-- chat_rooms
-- =========================
create table chat_rooms (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references profiles(id) on delete cascade,
  seller_id uuid not null references profiles(id) on delete cascade,
  product_id uuid not null references products(id) on delete cascade,
  last_message_at timestamptz,
  is_replied boolean not null default false,
  reminder_sent boolean not null default false,
  created_at timestamptz not null default now(),
  unique (buyer_id, seller_id, product_id)
);

create index idx_chat_rooms_buyer on chat_rooms(buyer_id);
create index idx_chat_rooms_seller on chat_rooms(seller_id);
create index idx_chat_rooms_product on chat_rooms(product_id);
create index idx_chat_rooms_reminder on chat_rooms(last_message_at)
  where is_replied = false and reminder_sent = false;

-- =========================
-- messages
-- =========================
create table messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references chat_rooms(id) on delete cascade,
  sender_id uuid not null references profiles(id) on delete cascade,
  message_text text not null,
  created_at timestamptz not null default now()
);

create index idx_messages_room on messages(room_id, created_at);
create index idx_messages_sender on messages(sender_id);

-- Pointer preview percakapan. Dibuat setelah messages karena FK mengarah ke messages(id).
alter table chat_rooms
  add column last_message_id uuid references messages(id) on delete set null;
create index idx_chat_rooms_last_message_id on chat_rooms(last_message_id);

-- =========================
-- push_tokens (untuk FCM)
-- =========================
create table push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  fcm_token text not null,
  device_info text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, fcm_token)
);
```

---

## 3. Row Level Security (RLS)

Semua tabel **wajib** RLS enabled — tanpa ini, mengekspos Data API Supabase sama saja membuka akses penuh.

```sql
alter table profiles enable row level security;
alter table products enable row level security;
alter table product_images enable row level security;
alter table chat_rooms enable row level security;
alter table messages enable row level security;
alter table push_tokens enable row level security;

-- ===== profiles =====
-- siapapun yang login boleh lihat profil (nama, avatar) — dibutuhkan utk tampilkan info penjual
create policy "profiles_select_authenticated" on profiles
  for select to authenticated using (true);

create policy "profiles_update_own" on profiles
  for update to authenticated
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- Profile dasar dibuat otomatis saat Supabase Auth membuat auth.users.
-- Mobile app tidak diberi policy INSERT ke profiles supaya client tidak bisa
-- membuat profil untuk user lain. Implementasi memakai trigger
-- auth.users -> public.handle_new_auth_user_profile() yang mengisi name dari
-- raw_user_meta_data.name, fallback local-part email, lalu backfill existing
-- auth users yang belum punya profile.

-- ===== products =====
-- katalog publik: siapapun (termasuk anon) boleh lihat produk aktif
create policy "products_select_active" on products
  for select to anon, authenticated
  using (status = 'active' or seller_id = auth.uid());

create policy "products_insert_own" on products
  for insert to authenticated
  with check (seller_id = auth.uid());

create policy "products_update_own" on products
  for update to authenticated
  using (seller_id = auth.uid())
  with check (seller_id = auth.uid());

create policy "products_delete_own" on products
  for delete to authenticated
  using (seller_id = auth.uid());

-- ===== product_images =====
create policy "product_images_select_visible" on product_images
  for select to anon, authenticated
  using (
    exists (
      select 1 from products p
      where p.id = product_id
        and (p.status = 'active' or p.seller_id = auth.uid())
    )
  );

create policy "product_images_manage_own" on product_images
  for all to authenticated
  using (
    exists (select 1 from products p where p.id = product_id and p.seller_id = auth.uid())
  )
  with check (
    exists (select 1 from products p where p.id = product_id and p.seller_id = auth.uid())
  );

-- ===== chat_rooms =====
create policy "chat_rooms_select_participant" on chat_rooms
  for select to authenticated
  using (buyer_id = auth.uid() or seller_id = auth.uid());

create policy "chat_rooms_insert_as_buyer" on chat_rooms
  for insert to authenticated
  with check (
    buyer_id = (select auth.uid())
    and seller_id <> (select auth.uid())
    and last_message_at is null
    and is_replied = false
    and reminder_sent = false
    and exists (
      select 1 from products p
      where p.id = product_id
        and p.seller_id = chat_rooms.seller_id
        and p.status = 'active'
    )
  );

-- Tidak ada policy update langsung untuk chat_rooms dari mobile client.
-- Field routing/state seperti buyer_id, seller_id, product_id, is_replied,
-- reminder_sent, dan last_message_at hanya boleh berubah lewat trigger atau
-- controlled SQL function agar client tidak bisa memalsukan state notifikasi.

-- ===== messages =====
create policy "messages_select_participant" on messages
  for select to authenticated
  using (
    exists (
      select 1 from chat_rooms r
      where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid())
    )
  );

create policy "messages_insert_participant" on messages
  for insert to authenticated
  with check (
    sender_id = auth.uid()
    and exists (
      select 1 from chat_rooms r
      where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid())
    )
  );

-- ===== push_tokens =====
create policy "push_tokens_manage_own" on push_tokens
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
```

---

## 4. Supabase Queues (`pgmq`) — Kontrak Job

```sql
-- Buat queue sekali di awal
select pgmq.create('notifications');
```

**Format payload job (JSON):**

```json
{
  "type": "new_message",
  "message_id": "uuid",
  "room_id": "uuid",
  "to_user_id": "uuid",
  "preview_text": "isi pesan dipotong 100 karakter"
}
```

```json
{
  "type": "reply_reminder",
  "room_id": "uuid",
  "to_user_id": "uuid"
}
```

`type` membedakan cara worker menyusun teks notifikasi. Skema ini sengaja rata (flat) dan minim — payload cukup jadi *pointer*, worker query detail terbaru (misal isi pesan, nama pengirim) dari database saat proses, bukan menyalin semua data ke payload (menghindari data stale kalau job baru diproses belakangan).

---

## 5. Trigger: Enqueue Notifikasi

```sql
create or replace function enqueue_new_message_notification()
returns trigger as $$
declare
  v_room chat_rooms;
  v_recipient uuid;
begin
  select * into v_room from chat_rooms where id = new.room_id;

  v_recipient := case
    when new.sender_id = v_room.buyer_id then v_room.seller_id
    else v_room.buyer_id
  end;

  update chat_rooms
    set last_message_at = new.created_at,
        last_message_id = new.id,
        is_replied = (new.sender_id = v_room.seller_id), -- balasan dari seller = replied
        reminder_sent = case
          when new.sender_id = v_room.buyer_id then false -- pertanyaan baru buyer butuh reminder baru jika tidak dibalas
          else reminder_sent
        end
    where id = new.room_id
      and (
        last_message_at is null
        or new.created_at >= last_message_at
      );

  perform pgmq.send(
    'notifications',
    jsonb_build_object(
      'type', 'new_message',
      'message_id', new.id,
      'room_id', new.room_id,
      'to_user_id', v_recipient,
      'preview_text', left(new.message_text, 100)
    )
  );

  return new;
end;
$$ language plpgsql
security definer
set search_path = public, pgmq, pg_catalog;

-- Function ini dipakai sebagai trigger path yang boleh melewati RLS untuk update
-- state room dan enqueue job. Jangan expose direct execute ke client roles.
revoke all on function enqueue_new_message_notification() from anon, authenticated;

create trigger trg_after_message_insert
after insert on messages
for each row execute procedure enqueue_new_message_notification();
```

---

## 6. `pg_cron` — Job Terjadwal

```sql
-- Nonaktifkan produk yang tidak diupdate >30 hari, jalan tiap hari jam 00:00
select cron.schedule(
  'deactivate-stale-products',
  '0 0 * * *',
  $$
    update products
    set status = 'inactive', updated_at = now()
    where status = 'active' and updated_at < now() - interval '30 days'
  $$
);

-- Sweep reminder chat belum dibalas >2 jam, jalan tiap 10 menit
select cron.schedule(
  'chat-reply-reminder',
  '*/10 * * * *',
  $$
    with due as (
      update chat_rooms
      set reminder_sent = true
      where is_replied = false
        and reminder_sent = false
        and last_message_at < now() - interval '2 hours'
        and exists (
          select 1
          from messages latest_buyer_message
          where latest_buyer_message.room_id = chat_rooms.id
            and latest_buyer_message.sender_id = chat_rooms.buyer_id
            and latest_buyer_message.created_at = chat_rooms.last_message_at
            and not exists (
              select 1
              from messages newer_message
              where newer_message.room_id = chat_rooms.id
                and newer_message.created_at > latest_buyer_message.created_at
            )
        )
      returning id, seller_id
    )
    select pgmq.send(
      'notifications',
      jsonb_build_object('type', 'reply_reminder', 'room_id', id, 'to_user_id', seller_id)
    )
    from due
  $$
);
```

---

## 7. Kotlin Worker — Spesifikasi

### 7.1 Tanggung Jawab
- Poll `pgmq` queue `notifications` secara berkala.
- Untuk tiap job: ambil `push_tokens` milik `to_user_id`, susun teks notifikasi sesuai `type`, kirim via Firebase Admin SDK.
- Tandai job selesai (`pgmq.delete`) atau biarkan visibility timeout habis agar otomatis retry.

### 7.2 Koneksi Database
Gunakan **connection pooler Supabase** (Supavisor, mode *transaction*) untuk koneksi JDBC dari worker — bukan direct connection — supaya tidak menghabiskan slot koneksi Postgres saat worker scaling/restart.

Karena Supavisor transaction mode dapat bentrok dengan JDBC server-side prepared statements setelah connection reuse, worker wajib memakai salah satu opsi ini:

- Tambahkan parameter JDBC PostgreSQL `prepareThreshold=0` agar driver memakai unnamed statements.
- Atau gunakan Supavisor session mode khusus worker jika prepared statements memang dibutuhkan.

Default MVP: transaction mode + `prepareThreshold=0`.

### 7.3 Loop Utama (pseudocode)

```kotlin
suspend fun workerLoop(pgPool: DataSource, fcm: FirebaseMessaging) {
    while (isActive) {
        val jobs = pgPool.readQueue(
            queueName = "notifications",
            visibilityTimeoutSec = 30,
            qty = 10
        ) // SELECT * FROM pgmq.read('notifications', 30, 10)

        if (jobs.isEmpty()) {
            delay(2000)
            continue
        }

        coroutineScope {
            jobs.forEach { job ->
                launch {
                    try {
                        val payload = job.message
                        val tokens = pgPool.getPushTokens(payload.toUserId)

                        if (tokens.isEmpty()) {
                            pgPool.archive("notifications", job.msgId) // tidak ada device, arsipkan
                            return@launch
                        }

                        tokens.forEach { token ->
                            try {
                                fcm.send(buildFcmMessage(payload, token.fcmToken))
                            } catch (e: FirebaseMessagingException) {
                                if (e.isTokenInvalid()) pgPool.deleteToken(token.id)
                            }
                        }

                        pgPool.delete("notifications", job.msgId) // SELECT pgmq.delete(...)
                    } catch (e: Exception) {
                        // biarkan visibility timeout habis -> job otomatis muncul lagi utk retry
                        log.warn("Job ${job.msgId} gagal, akan retry via visibility timeout", e)
                    }
                }
            }
        }
    }
}
```

### 7.4 Retry & Dead Letter
- `pgmq` melacak `read_ct` (berapa kali job dibaca) secara otomatis.
- Kalau `read_ct` sebuah job melewati batas (mis. 5), worker eksplisit memanggil `pgmq.archive()` alih-alih membiarkannya retry terus — mencegah job "beracun" (payload rusak, user terhapus) berputar selamanya.

```kotlin
if (job.readCt >= MAX_ATTEMPTS) {
    pgPool.archive("notifications", job.msgId)
    log.error("Job ${job.msgId} dipindah ke archive setelah ${job.readCt} percobaan")
    return@launch
}
```

### 7.5 Konfigurasi Resource (Server 2GB)

| Parameter | Nilai |
|---|---|
| JVM heap | `-Xms256m -Xmx512m` |
| DB connection pool | max 8 |
| Poll interval saat queue kosong | 2 detik |
| Batch size per poll | 10 |
| Concurrency per batch | 8 coroutine paralel |
| Visibility timeout | 30 detik (naikkan kalau FCM call lambat) |

### 7.6 Deployment
- Jalankan sebagai `systemd` service dengan `Restart=always` — worker stateless, aman restart kapan saja tanpa kehilangan job (job tetap di Postgres).
- Simpan service account FCM & connection string sebagai environment variable, bukan hardcode.

---

## 8. Yang Sengaja Tidak Dibuat

- **REST API custom untuk CRUD produk/chat** — client mobile langsung pakai Supabase client SDK, keamanan ditegakkan RLS di §3. Membuat REST layer tambahan di sini cuma duplikasi tanpa manfaat di skala MVP.
- **Redis/RabbitMQ** — `pgmq` cukup untuk traffic puluhan–ratusan notifikasi/menit (lihat PRD §9). Baru dipertimbangkan kalau traffic naik signifikan.

---

## 9. Pertanyaan Terbuka

- Format teks notifikasi per `type` (copy pesan push) — perlu disepakati tim produk.
- Apakah `MAX_ATTEMPTS` job = 5 sudah pas, atau perlu disesuaikan setelah lihat data nyata tingkat kegagalan FCM.
- Kebijakan retensi `messages` — disimpan permanen selamanya, atau ada archiving setelah N bulan?
