# Backend Technical Spec — Marketplace UMKM

**Status:** Draft v1.2 (§8 ditambahkan untuk Fase 3 — orders, notifications, Xendit, ongkir, OAuth learning)
**Pelengkap dari:** PRD-v2.md (mobile), GOALS3.md, PLAN3.md
**Stack:** Supabase Cloud (Postgres + Auth + Storage + Realtime) + Supabase Queues (`pgmq`) + `pg_cron` + backend Kotlin/Ktor tunggal (server 2GB) untuk HTTP OAuth learning, `/health`, dan background notification worker.

---

## 1. Scope Dokumen

Dokumen ini menutup bagian yang sengaja tidak dibahas di PRD: skema SQL lengkap, RLS policies, kontrak antrean/job, dan spesifikasi worker. Audiensnya developer, bukan stakeholder produk.

**Prinsip desain:** sebagian besar CRUD (produk, chat, pesan) **langsung dari mobile app via Supabase client SDK** dengan keamanan ditegakkan oleh RLS — bukan lewat REST API custom. Backend custom Kotlin/Ktor dipakai untuk proses async yang butuh ketahanan (notifikasi, reminder), endpoint health, dan OAuth learning track; backend ini bukan API layer CRUD marketplace biasa.

---

## 2. Skema Database (DDL)

```sql
-- Ekstensi yang dibutuhkan
create extension if not exists pgcrypto;   -- untuk gen_random_uuid()
create extension if not exists pgmq;       -- Supabase Queues
create extension if not exists pg_cron;    -- Scheduler

-- =========================
-- profiles
-- =========================
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,          -- nama tampil sebagai pembeli
  store_name text,             -- nama toko, nullable sampai user pertama kali aktifkan mode penjual
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
  for select using (auth.role() = 'authenticated');

create policy "profiles_update_own" on profiles
  for update using (auth.uid() = id);

-- ===== products =====
-- katalog publik: siapapun (termasuk anon) boleh lihat produk aktif
create policy "products_select_active" on products
  for select using (status = 'active' or seller_id = auth.uid());

create policy "products_insert_own" on products
  for insert with check (seller_id = auth.uid());

create policy "products_update_own" on products
  for update using (seller_id = auth.uid());

create policy "products_delete_own" on products
  for delete using (seller_id = auth.uid());

-- ===== product_images =====
create policy "product_images_select_all" on product_images
  for select using (true);

create policy "product_images_manage_own" on product_images
  for all using (
    exists (select 1 from products p where p.id = product_id and p.seller_id = auth.uid())
  );

-- ===== chat_rooms =====
create policy "chat_rooms_select_participant" on chat_rooms
  for select using (buyer_id = auth.uid() or seller_id = auth.uid());

create policy "chat_rooms_insert_as_buyer" on chat_rooms
  for insert with check (buyer_id = auth.uid());

create policy "chat_rooms_update_participant" on chat_rooms
  for update using (buyer_id = auth.uid() or seller_id = auth.uid());

-- ===== messages =====
create policy "messages_select_participant" on messages
  for select using (
    exists (
      select 1 from chat_rooms r
      where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid())
    )
  );

create policy "messages_insert_participant" on messages
  for insert with check (
    sender_id = auth.uid()
    and exists (
      select 1 from chat_rooms r
      where r.id = room_id and (r.buyer_id = auth.uid() or r.seller_id = auth.uid())
    )
  );

-- ===== push_tokens =====
create policy "push_tokens_manage_own" on push_tokens
  for all using (user_id = auth.uid());
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
        is_replied = (new.sender_id = v_room.seller_id) -- balasan dari seller = replied
    where id = new.room_id;

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
$$ language plpgsql security definer;

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

## 7. Backend Kotlin/Ktor — Spesifikasi

Backend learning app berjalan sebagai **satu process/container**. Di dalam process yang sama:

- Ktor HTTP server mengekspos `/health` dan route OAuth learning.
- Coroutine background menjalankan notification worker `pgmq` → FCM.
- Worker bisa disabled untuk local HTTP/OAuth-only development jika env DB/FCM belum lengkap.
- Production dapat fail-fast dengan `UMKMSHOP_WORKER_ENABLED=true`.

### 7.1 Tanggung Jawab Background Worker

- Poll `pgmq` queue `notifications` secara berkala.
- Untuk tiap job: ambil `push_tokens` milik `to_user_id`, susun teks notifikasi sesuai `type`, kirim via Firebase Admin SDK.
- Tandai job selesai (`pgmq.delete`) atau biarkan visibility timeout habis agar otomatis retry.

### 7.2 Koneksi Database
Gunakan **connection pooler Supabase** (Supavisor, mode *transaction*) untuk koneksi JDBC dari worker — bukan direct connection — supaya tidak menghabiskan slot koneksi Postgres saat worker scaling/restart.

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
- Jalankan sebagai satu Docker service/runtime backend dengan `Restart=always` atau restart policy platform deployment — backend stateless, aman restart kapan saja tanpa kehilangan job (job tetap di Postgres).
- Simpan service account FCM, connection string, OAuth token pepper, dan signing key production sebagai environment/secret store, bukan hardcode.

---

## 8. Fase 3 — Orders, Notifications, Xendit, Ongkir & OAuth Learning

**Status: exploratory** — mengikuti keputusan produk yang sudah dikunci di `GOALS3.md`, tapi satu keputusan arsitektur (lokasi webhook) masih menunggu konfirmasi (lihat §8.4).

### 8.1 DDL — `orders`

```sql
create table orders (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references chat_rooms(id) on delete cascade,
  buyer_id uuid not null references profiles(id),
  seller_id uuid not null references profiles(id),
  product_id uuid not null references products(id),
  item_note text,
  weight_grams int,
  subtotal numeric(12,2) not null check (subtotal >= 0),
  shipping_cost numeric(12,2) not null default 0,
  total_amount numeric(12,2) generated always as (subtotal + shipping_cost) stored,
  status text not null default 'pending'
    check (status in ('pending','paid','expired','cancelled')),
  xendit_invoice_id text,
  xendit_invoice_url text,
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  expired_at timestamptz
);

create index idx_orders_buyer on orders(buyer_id, created_at desc);
create index idx_orders_seller on orders(seller_id, created_at desc);

alter table orders enable row level security;

create policy "orders_insert_as_seller" on orders
  for insert with check (
    seller_id = auth.uid()
    and exists (select 1 from chat_rooms cr where cr.id = chat_room_id and cr.seller_id = auth.uid())
  );

create policy "orders_select_participant" on orders
  for select using (buyer_id = auth.uid() or seller_id = auth.uid());

-- Sengaja tidak ada policy UPDATE untuk role authenticated.
-- Status hanya berubah lewat webhook (service_role), lihat §8.4.
```

### 8.2 DDL — `notifications` (Inbox)

```sql
create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  type text not null,
  title text not null,
  body text,
  related_order_id uuid references orders(id),
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create index idx_notifications_user on notifications(user_id, created_at desc);
create index idx_notifications_unread on notifications(user_id) where is_read = false;

alter table notifications enable row level security;

create policy "notifications_select_own" on notifications
  for select using (user_id = auth.uid());

create policy "notifications_update_own_read_status" on notifications
  for update using (user_id = auth.uid());
-- INSERT tidak dibuka ke client — hanya lewat trigger/service_role.
```

**Prinsip penting:** `notifications` (tabel ini) dan `pgmq` (§4) adalah dua hal berbeda yang hidup berdampingan, bukan pengganti satu sama lain — `pgmq` jalur pengiriman push transient, `notifications` penyimpanan permanen untuk Inbox. Setiap perubahan status `orders` harus menulis ke **keduanya**: insert `notifications` (persisten, buat Inbox) **dan** `pgmq.send()` (buat push FCM).

### 8.3 Data Alamat Tambahan (untuk Ongkir — api.co.id)

`profiles.city` (dari Fase 2) tidak cukup presisi — `api.co.id` butuh `village_code` (10 digit, level **kelurahan**, bukan kota):

```sql
alter table profiles
  add column if not exists postal_code text,
  add column if not exists village_code text; -- 10 digit, dari endpoint /regional/indonesia/villages milik api.co.id
```

Alur mendapatkan `village_code` saat user isi alamat: panggil `/regional/indonesia/villages` (search berdasarkan nama kelurahan) → user pilih dari hasil pencarian → simpan `village_code`-nya. Jangan minta user mengetik kode manual — mereka tidak akan tahu kodenya.

### 8.4 Xendit Webhook — Keputusan Arsitektur Belum Dikunci

Webhook itu server-to-server call tanpa sesi user — RLS tidak berlaku untuknya, jadi butuh endpoint HTTP publik tersendiri di luar pola "client SDK + RLS" yang dipakai di seluruh dokumen ini.

| Opsi | Konsekuensi |
|---|---|
| **Supabase Edge Function** (rekomendasi) | Serverless, tidak menambah beban server 2GB, logic ditulis JS/TS |
| **Kotlin worker jadi juga HTTP listener** | Konsisten bahasa, tapi worker berubah peran dari consumer pasif jadi server yang harus reachable dari luar (perlu domain+HTTPS) |

**Checklist implementasi (berlaku untuk opsi manapun yang dipilih):**
- [ ] Verifikasi signature/token dari header request Xendit — tolak kalau tidak cocok, jangan proses payload apapun sebelum verifikasi lolos.
- [ ] Idempotency: cek `orders.status` masih `pending` sebelum update — webhook retry dari Xendit tidak boleh menghasilkan notifikasi dobel.
- [ ] Update `orders.status` → insert `notifications` untuk buyer & seller → `pgmq.send()` untuk push — tiga langkah ini terjadi dalam satu transaksi, bukan terpisah (supaya tidak ada state yang "setengah update" kalau salah satu langkah gagal).

### 8.5 Ongkir — Alur Panggilan API

1. Penjual isi form invoice: harga, `weight_grams`.
2. Sistem panggil api.co.id untuk ongkir: asal (`village_code` penjual), tujuan (`village_code` pembeli), berat.
3. Hasil (`shipping_cost`) disimpan sebagai snapshot di `orders` — **bukan** dihitung ulang tiap kali ditampilkan (harga API bisa berubah, snapshot di waktu invoice dibuat itu yang mengikat sebagai kontrak).
4. Kalau API down/timeout: jangan blocking pembuatan invoice — sediakan fallback input manual.

### 8.6 OAuth Server UMKMShop — Learning Track

Status: **learning/demo**, bukan identity provider production untuk partner nyata. Auth utama Android tetap Supabase Auth + Google OAuth/Email. OAuth Server UMKMShop menerbitkan token untuk demo client pihak ketiga agar konsep OAuth 2.1/OIDC bisa dipelajari lewat implementasi sendiri.

**Prinsip desain:**
- OAuth Server berjalan sebagai route HTTP di backend Kotlin/Ktor tunggal. Background worker tetap consumer `pgmq`; OAuth route bukan jalur login Android.
- User login source tetap Supabase Auth. Consent screen hanya boleh approve authorization untuk user yang punya session valid.
- Flow awal hanya **Authorization Code + PKCE** dan **Refresh Token**. Jangan implement `password` grant atau `client_credentials` dulu.
- Public client wajib `token_endpoint_auth_method = none` dan PKCE `S256`.
- Confidential client boleh punya secret, tetapi secret disimpan hashed.
- Redirect URI harus exact match; tidak boleh wildcard.
- Authorization code single-use, short-lived, dan disimpan hashed.
- Refresh token disimpan hashed, rotatable, dan bisa direvoke.
- JWT access/ID token ditandatangani asymmetric key; public key diekspos lewat JWKS.
- Scope awal: `openid`, `email`, `profile`.

**Endpoint minimum:**

| Endpoint | Fungsi |
|---|---|
| `GET /.well-known/openid-configuration` | Discovery metadata issuer, authorization endpoint, token endpoint, JWKS endpoint, scopes |
| `GET /oauth/authorize` | Validasi client/redirect/scope/PKCE, lalu tampilkan atau redirect ke consent |
| `POST /oauth/token` | Tukar authorization code + PKCE verifier menjadi token; refresh token flow |
| `GET /oauth/userinfo` | Return claim user sesuai scope dari access token valid |
| `GET /oauth/jwks.json` | Public key untuk verifikasi JWT |
| `POST /oauth/revoke` | Revoke refresh token/access grant |

**DDL awal (konseptual, finalisasi di ticket):**

```sql
create table oauth_clients (
  id uuid primary key default gen_random_uuid(),
  client_id text not null unique,
  client_name text not null,
  client_type text not null check (client_type in ('public', 'confidential')),
  client_secret_hash text,
  redirect_uris text[] not null,
  allowed_scopes text[] not null default array['openid', 'email', 'profile'],
  created_at timestamptz not null default now()
);

create table oauth_authorization_codes (
  id uuid primary key default gen_random_uuid(),
  code_hash text not null unique,
  client_id text not null references oauth_clients(client_id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  redirect_uri text not null,
  scope text not null,
  code_challenge text not null,
  code_challenge_method text not null check (code_challenge_method = 'S256'),
  nonce text,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create table oauth_refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  token_hash text not null unique,
  client_id text not null references oauth_clients(client_id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  scope text not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  replaced_by uuid references oauth_refresh_tokens(id),
  created_at timestamptz not null default now()
);
```

Tabel di atas tidak boleh diekspos ke Android client. Operasi OAuth Server harus lewat service-side code dengan credential backend, bukan Supabase mobile SDK langsung.

---

## 9. Yang Sengaja Tidak Dibuat

- **REST API custom untuk CRUD produk/chat** — client mobile langsung pakai Supabase client SDK, keamanan ditegakkan RLS di §3. Membuat REST layer tambahan di sini cuma duplikasi tanpa manfaat di skala MVP. Pengecualian: OAuth Server learning track punya endpoint sendiri karena memang sedang mempelajari protokol OAuth/OIDC.
- **Redis/RabbitMQ** — `pgmq` cukup untuk traffic puluhan–ratusan notifikasi/menit (lihat PRD §9). Baru dipertimbangkan kalau traffic naik signifikan.
- **Refund otomatis via API** (Fase 3) — sengketa/refund ditangani manual dulu, sesuai `GOALS3.md` §5.

---

## 10. Pertanyaan Terbuka

- Format teks notifikasi per `type` (copy pesan push) — perlu disepakati tim produk.
- Apakah `MAX_ATTEMPTS` job = 5 sudah pas, atau perlu disesuaikan setelah lihat data nyata tingkat kegagalan FCM.
- Kebijakan retensi `messages` — disimpan permanen selamanya, atau ada archiving setelah N bulan?
- **(Fase 3) Lokasi endpoint webhook Xendit** — Edge Function atau Kotlin worker? Lihat §8.4, ini blocking sebelum implementasi payment dimulai.
- **(Fase 3) Detail endpoint ongkir api.co.id yang dipakai** — provider sudah dikunci ke api.co.id, tetapi endpoint/rate limit/timeout final perlu diverifikasi saat implementasi.
- **(Fase 3 learning) Issuer/domain OAuth Server UMKMShop** — perlu domain HTTPS stabil sebelum token dianggap realistis untuk demo client.
- **(Fase 3 learning) Penyimpanan private signing key OAuth** — pilih secret manager/env server yang tidak masuk repo; jangan hardcode private key di source.
