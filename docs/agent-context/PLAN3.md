# PLAN3.md — Marketplace UMKM Fase 3

**Prasyarat:** `GOALS3.md`. Urutan disusun dari yang paling independen ke yang paling banyak keputusan terbuka.
**Aturan eksekusi:** PLAN3 hanya routing dan dependency map. Agent hanya boleh mengerjakan ticket Fase 3 yang eksplisit disebut user.

---

## Tahap A — Kategori Produk Baru

Tidak butuh migrasi skema (`products.category` sudah `text` bebas sejak awal) — murni update dokumentasi & UI dropdown kategori di app.

**Ticket:** `tickets/TICKET-021-phase3-b2b-categories.md`

- [x] Update pilihan kategori di form tambah produk (Android): Makanan (bahan makanan), Minuman (bahan belum diolah), Komponen Motor, Komponen HP, Komponen IoT.
- [x] Update copy/branding kalau perlu — pertimbangkan apakah nama "UMKMShop" masih pas untuk positioning B2B bahan baku, atau perlu ditinjau ulang nanti (di luar cakupan teknis Plan ini, cukup dicatat sebagai catatan produk).

**Definition of Done:** kategori baru muncul di form & filter katalog.

---

## Tahap B — Tabel `orders` (Invoice & Riwayat Transaksi)

**Ticket:** `tickets/TICKET-022-phase3-orders-transaction-history.md`

```sql
create table orders (
  id uuid primary key default gen_random_uuid(),
  chat_room_id uuid not null references chat_rooms(id) on delete cascade,
  buyer_id uuid not null references profiles(id),
  seller_id uuid not null references profiles(id),
  product_id uuid not null references products(id),
  item_note text,                      -- deskripsi manual hasil deal, mis. "50kg beras premium"
  weight_grams int,                    -- untuk perhitungan ongkir
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

-- Hanya penjual yang boleh membuat invoice (sesuai flow: penjual generate harga)
create policy "orders_insert_as_seller" on orders
  for insert with check (
    seller_id = auth.uid()
    and exists (
      select 1 from chat_rooms cr
      where cr.id = chat_room_id and cr.seller_id = auth.uid()
    )
  );

create policy "orders_select_participant" on orders
  for select using (buyer_id = auth.uid() or seller_id = auth.uid());

-- Update status TIDAK lewat client RLS biasa — hanya via service_role dari webhook (lihat Tahap D)
-- Sengaja tidak ada policy UPDATE untuk role authenticated.
```

- [x] Jalankan DDL di atas.
- [x] Layar "Buat Invoice" di sisi penjual (dari dalam chat room) — input harga hasil deal + berat barang.
- [x] Layar "Riwayat Transaksi" — list `orders` milik user (sebagai buyer atau seller), status berwarna beda per state.

Validasi 2026-07-04: migration Supabase MCP berhasil, RLS rollback script TICKET-022 lolos, `:app:assembleDebug` + `:app:testDebugUnitTest` berhasil. Full `./gradlew build` belum dipakai sebagai bukti karena release guard membutuhkan `google-services.json`.

**Definition of Done:** penjual bisa buat order manual (status `pending`) dari SQL editor dulu untuk tes, pembeli & penjual bisa lihat row itu di riwayat masing-masing, user lain tidak bisa.

---

## Tahap C — Tabel `notifications` (Inbox)

Terpisah dari `pgmq` — ini penyimpanan permanen, bukan antrean pengiriman.

**Ticket:** `tickets/TICKET-023-phase3-persistent-inbox.md`

```sql
create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  type text not null, -- 'order_created' | 'payment_paid' | 'payment_expired' | 'reply_reminder' | dst
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
-- INSERT sengaja tidak dibuka ke client — hanya lewat trigger/service_role.
```

- [ ] Jalankan DDL di atas.
- [ ] Trigger di `orders` (saat status berubah) → insert row `notifications` untuk pihak yang relevan **dan** `pgmq.send()` untuk push — dua-duanya, bukan salah satu.
- [ ] Layar Inbox penuh (dibuka dari Profile) — list `notifications`, tandai dibaca saat dibuka.
- [ ] Widget ringkas Inbox (badge jumlah belum dibaca) di halaman Toko dan Produk — cukup query `count(*) where is_read = false`, bukan tarik semua data.

**Definition of Done:** perubahan status order otomatis muncul di Inbox tanpa refresh manual, badge unread akurat di ketiga tempat (Profile, Toko, Produk).

---

## Tahap D — Integrasi Xendit ⚠️ Butuh Keputusan Arsitektur Dulu

**Ticket:** `tickets/TICKET-024-phase3-xendit-invoices-webhook.md`

**Keputusan yang belum dikunci** (sempat disinggung waktu bahas "apakah skala besar butuh bikin API sendiri"): endpoint webhook Xendit ditaruh di mana?

| Opsi | Konsekuensi |
|---|---|
| **Supabase Edge Function** | Serverless, tidak nambah beban server 2GB, tapi logic verifikasi ditulis JS/TS |
| **Kotlin worker jadi juga HTTP listener** | Konsisten Kotlin, tapi worker berubah peran dari consumer pasif jadi server yang harus reachable dari luar (butuh domain+HTTPS) |

Rekomendasi: **Edge Function** — webhook cuma perlu (1) verifikasi signature, (2) update `orders.status`, (3) insert `notifications`. Ini kerjaan ringan yang tidak butuh keunggulan Kotlin (batching, coroutine) yang jadi alasan worker kita ada. Menaruhnya di worker cuma menambah operational overhead tanpa manfaat nyata.

- [ ] **Putuskan opsi di atas dulu** sebelum lanjut item di bawah.
- [ ] Buat invoice via Xendit API saat penjual submit form Buat Invoice (Tahap B) — simpan `xendit_invoice_id`/`xendit_invoice_url` ke `orders`.
- [ ] Buat endpoint webhook (sesuai opsi terpilih): verifikasi signature/token dari header request Xendit, tolak kalau tidak cocok.
- [ ] Webhook update `orders.status` + insert `notifications` + trigger `pgmq.send()` — pastikan idempotent (cek dulu status belum berubah sebelum proses, supaya retry webhook tidak dobel notifikasi).
- [ ] Test di sandbox Xendit (yang sudah kita bahas sebelumnya) sebelum publish key production.

**Definition of Done:** bayar invoice sandbox → status `orders` berubah otomatis dalam hitungan detik, notifikasi masuk ke buyer & seller, retry webhook manual (kirim payload sama 2x) tidak menghasilkan notifikasi dobel.

---

## Tahap E — Integrasi Ongkir (API Pihak Ketiga)

**Ticket:** `tickets/TICKET-025-phase3-shipping-cost-api.md`

**Provider terkunci:** api.co.id.

**Ketergantungan data:** field `city` yang ditambahkan di Fase 2 kemungkinan besar tidak cukup presisi — api.co.id butuh `village_code` level kelurahan sesuai referensi internal mereka, bukan teks bebas.

- [ ] Tambah kolom alamat presisi ke `profiles` (`postal_code`, `village_code`) — field tambahan di samping `city`, bukan menggantikannya (city tetap dipakai untuk filter Fase 2).
- [ ] Tambah UI pencarian kelurahan dari api.co.id; user memilih hasil, bukan mengetik `village_code` manual.
- [ ] Saat penjual isi form Buat Invoice: panggil API ongkir dengan asal (kota penjual) dan tujuan (kota pembeli) + `weight_grams` dari order → dapat estimasi biaya kirim.
- [ ] Simpan `shipping_cost` hasil pilihan ke `orders` (bukan dihitung ulang tiap kali ditampilkan — harga bisa berubah, snapshot di waktu invoice dibuat itu yang mengikat).
- [ ] Tangani kasus API down/timeout: jangan blocking pembuatan invoice — beri opsi input manual sebagai fallback kalau API gagal merespons dalam waktu wajar.

**Definition of Done:** estimasi ongkir muncul otomatis di form invoice, dan tetap bisa lanjut (via fallback) kalau api.co.id sedang bermasalah.

---

## Tahap F — Testing End-to-End

**Ticket:** `tickets/TICKET-026-phase3-e2e-payment-shipping-validation.md`

- [ ] Skenario penuh: chat nego harga → penjual buat invoice (dengan ongkir otomatis) → pembeli bayar via Xendit sandbox → status berubah → notifikasi masuk Inbox kedua pihak → riwayat transaksi terupdate.
- [ ] Coba retry webhook manual — pastikan idempotent.
- [ ] Coba akses `orders`/`notifications` milik user lain lewat query manual — pastikan RLS menolak.

---

## Tahap G — Google OAuth Native & OAuth Server Learning Track

**Ticket:** `tickets/TICKET-027-phase3-oauth-server-learning.md`

Tahap ini adalah jalur pembelajaran identity platform, bukan dependency untuk payment/ongkir. Auth utama Android tetap memakai Supabase Auth + Google OAuth/Email. OAuth Server UMKMShop dibuat sebagai route learning di backend Kotlin/Ktor tunggal agar konsep Authorization Code + PKCE, token endpoint, refresh token, consent, dan JWKS dipahami lewat implementasi nyata tanpa membuat microservice terpisah.

**Batas arsitektur:**

| Area | Keputusan |
|---|---|
| Login utama Android | Tetap Supabase Auth; Google OAuth native Android/Credential Manager |
| OAuth Server UMKMShop | Route HTTP di backend Kotlin/Ktor tunggal untuk demo client pihak ketiga |
| Worker FCM | Coroutine/background job di backend yang tetap consumer `pgmq` |
| Client publik | Wajib PKCE, tidak boleh punya client secret |
| Token/JWT | Ditandatangani asymmetric key dan diekspos via JWKS |

- [ ] Pastikan Google OAuth native Android terdokumentasi: package `com.application.umkmshop`, SHA-1 debug/release, Android OAuth Client ID, dan Supabase Google provider.
- [ ] Buat desain OAuth Server UMKMShop: client registry, exact redirect URI, consent screen, auth code, token endpoint, refresh token, revocation, JWKS.
- [ ] Implement endpoint minimal di modul `:backend`: `/.well-known/openid-configuration`, `/oauth/authorize`, `/oauth/token`, `/oauth/userinfo`, `/oauth/jwks.json`, `/oauth/revoke`.
- [ ] Buat demo client public yang menjalankan Authorization Code + PKCE dari awal sampai memanggil `/oauth/userinfo`.
- [ ] Tambahkan negative tests: invalid redirect URI, reused authorization code, bad PKCE verifier, expired code, revoked refresh token.

**Definition of Done:** demo client berhasil login via UMKMShop OAuth Server menggunakan Authorization Code + PKCE, token bisa diverifikasi lewat JWKS, dan semua negative tests utama ditolak.

---

## Ringkasan Urutan

```
Tahap A (Kategori) ── independen, kerjakan kapan saja
Tahap B (orders)   ── fondasi, blocking Tahap C & D
Tahap C (Inbox)    ── butuh Tahap B selesai (relies on orders)
Tahap D (Xendit)   ── STOP dulu di keputusan endpoint sebelum coding
Tahap E (Ongkir)   ── butuh data alamat presisi ditambah dulu
Tahap F (Testing)  ── setelah semua tahap di atas jalan
Tahap G (OAuth)    ── learning track, tidak blocking payment/ongkir
```

## Rekomendasi Urutan Berikutnya

Urutan rekomendasi Fase 3 produk: `TICKET-021` kategori B2B, `TICKET-022` orders/riwayat transaksi, `TICKET-023` inbox persisten, `TICKET-024` Xendit, `TICKET-025` ongkir, lalu `TICKET-026` E2E.

`TICKET-027` OAuth Server adalah learning track terpisah. Kerjakan hanya kalau user secara eksplisit menyebut `TICKET-027`, dan jangan jadikan ticket ini prasyarat payment/ongkir.

Sebelum implementasi payment, **putuskan lokasi webhook Xendit** di `TICKET-024`. Eksekusi tetap menunggu user menyebut ticket spesifik pada turn/session tersebut.
