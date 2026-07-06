# GOALS3.md — Marketplace UMKM Fase 3

**Prasyarat masuk fase ini:** Fase 1 (MVP) & Fase 2 sudah berjalan. Fase 3 ini beda karakter dari dua fase sebelumnya — bukan cuma penyempurnaan, tapi **pivot model bisnis** sekaligus penambahan komponen yang sebelumnya sengaja dihindari (payment gateway, tracking transaksi). Tambahan terbaru: ada **learning track OAuth Server UMKMShop** supaya proyek ini juga dipakai untuk memahami OAuth 2.1/OpenID Connect tanpa mengganggu flow marketplace utama.

---

## 1. Konteks & Pivot Bisnis

Model berubah dari **C2C umum** (siapa saja jual ke siapa saja) menjadi **B2B rantai pasok bahan baku/setengah jadi**: penjual di platform ini menjual barang belum jadi atau komponen (bahan makanan mentah, madu belum diolah, komponen motor, komponen HP, komponen IoT), dan pembelinya adalah **bisnis** yang mengolah barang itu jadi produk akhir (contoh konkret: pembeli madu mentah adalah usaha kue yang memakainya sebagai bahan, bukan konsumen akhir peminum madu).

Ini mengubah premis penting yang tadinya jadi fondasi arsitektur sejak PRD awal: **transaksi tidak lagi terjadi di luar sistem.** Payment gateway dan riwayat transaksi sekarang jadi bagian inti, bukan non-goal.

## 2. Tujuan per Komponen

| Komponen | Tujuan |
|---|---|
| Auth utama (Google OAuth2 + Email) | Google OAuth2 dan Email menjadi jalur login utama app; Google dipakai native Android/Credential Manager, bukan secret di client |
| OAuth Server UMKMShop (learning) | Membangun authorization server sendiri untuk belajar OAuth 2.1/OIDC: authorization code + PKCE, consent screen, token endpoint, refresh token, dan JWKS |
| Kategori produk baru | Makanan (bahan makanan), Minuman (bahan belum diolah), Komponen Motor, Komponen HP, Komponen IoT — mencerminkan posisi platform di rantai pasok, bukan katalog konsumen umum |
| Payment (Xendit) | Setelah harga disepakati di chat, **penjual** yang generate invoice dengan harga hasil deal — bukan checkout harga tetap dari listing produk |
| Riwayat Transaksi | Setiap invoice yang dibuat & statusnya (pending/dibayar/kedaluwarsa) tercatat permanen, bisa dilihat ulang oleh pembeli maupun penjual |
| Inbox | Notifikasi pesanan & pembayaran — **terpisah dari Chat** — bisa dibuka lengkap dari Profile, dan muncul (ringkas) di halaman Toko dan Produk |
| Ongkir | Dihitung otomatis lewat api.co.id, bukan input manual penjual kecuali fallback saat API gagal |

## 3. Tujuan Teknis

- **Push notification (`pgmq` + worker) dan Inbox itu dua hal berbeda, bukan satu.** `pgmq`/worker cuma jalur pengiriman FCM yang bersifat sekali-lewat (transient) — begitu terkirim, selesai, tidak ada yang bisa "dibuka lagi" dari situ. Inbox butuh tabel tersendiri yang menyimpan histori notifikasi secara persisten dan bisa di-query, supaya user bisa buka Inbox kapan saja dan lihat semua yang pernah masuk, bukan cuma notifikasi yang baru lewat.
- **Webhook Xendit butuh endpoint HTTP publik** — ini beda karakter dari semua yang kita bangun sejauh ini (client SDK + RLS). Webhook itu server-to-server, tidak ada sesi user, jadi RLS tidak bisa dipakai untuk mengamankannya. Wajib verifikasi signature dari Xendit di endpoint ini sebelum mempercayai payload apapun.
- Perhitungan ongkir butuh data alamat yang lebih presisi dari sekadar `city` text yang ditambahkan di Fase 2 — api.co.id butuh referensi wilayah sendiri, terutama `village_code`, plus kode pos.
- Status pembayaran (`pending`/`paid`/`expired`/`cancelled`) harus **satu sumber kebenaran** di tabel `orders` — status ini yang dipercaya, bukan asumsi dari sisi client.
- **OAuth Server UMKMShop harus dipisahkan dari auth utama Android, tetapi hidup di backend Kotlin/Ktor learning app yang sama dengan background worker.** Android tetap login lewat Supabase Auth/Google OAuth; OAuth Server learning menerbitkan token untuk demo client pihak ketiga. Worker FCM tetap consumer `pgmq` sebagai coroutine/background job, bukan jalur login Android.

## 4. Tujuan Pembelajaran

- **Verifikasi signature webhook** — paham cara kerja HMAC/token verification supaya endpoint publik tidak bisa dipalsukan orang iseng yang tahu URL-nya.
- **Idempotency di sistem pembayaran** — webhook bisa terkirim lebih dari sekali (retry dari sisi Xendit), sistem harus aman diproses ulang tanpa mencatat pembayaran dobel.
- **Integrasi API pihak ketiga (shipping cost)** — paham pola mapping data lokasi internal ke referensi ID eksternal, dan bagaimana menangani kalau API pihak ketiga down/lambat (fallback, timeout, caching hasil).
- **Desain notifikasi persisten vs transient** — beda filosofi antara "kirim sekali lewat" (push) dan "catatan yang bisa dibuka lagi" (Inbox), dan kenapa keduanya perlu hidup berdampingan, bukan salah satu saja.
- **OAuth 2.1 Authorization Code + PKCE** — paham kenapa mobile/public client tidak boleh menyimpan client secret, dan kenapa code harus single-use serta bound ke `code_verifier`.
- **OpenID Connect basics** — paham ID token, UserInfo endpoint, issuer, audience, nonce, JWKS, dan asymmetric signing key.
- **Token lifecycle** — paham refresh token rotation, revocation, hashing token di database, exact redirect URI validation, dan consent audit.

## 5. Non-Goals (Tetap Berlaku)

- Belum evaluasi multi-currency/transaksi lintas negara.
- Belum ada dashboard admin keuangan/rekonsiliasi otomatis skala besar — cukup riwayat transaksi per user dulu.
- Belum ada refund otomatis via API — kalau ada sengketa/refund, tetap ditangani manual dulu di fase ini.
- iOS masih belum dievaluasi ulang (tetap status `GOALS.md`).
- OAuth Server UMKMShop belum dianggap identity provider production untuk partner nyata. Di fase ini statusnya learning/demo sampai ada security review khusus.

## 6. Kriteria Sukses

| Level | Kriteria |
|---|---|
| Payment jalan | Penjual bisa generate invoice dari chat, pembeli bisa bayar via Xendit, status ter-update otomatis dari webhook |
| Riwayat transaksi jalan | Kedua pihak bisa lihat histori order lengkap dengan status yang akurat |
| Inbox jalan | Notifikasi pesanan/pembayaran bisa dibuka ulang kapan saja, tidak hilang setelah push notification lewat |
| Ongkir jalan | Estimasi ongkir muncul otomatis saat invoice dibuat, tanpa penjual perlu hitung manual |
| Keamanan webhook teruji | Endpoint menolak payload yang signature-nya tidak valid — dicoba aktif, bukan diasumsikan aman |
| OAuth learning jalan | Demo client bisa menjalankan Authorization Code + PKCE sampai mendapat token, lalu memanggil `/oauth/userinfo`; invalid redirect URI, reused code, dan bad PKCE verifier ditolak |
