# GOALS.md — Hasil Akhir Marketplace UMKM

**Pelengkap dari:** PRD.md, BACKEND_SPEC.md, PLAN.md
**Status:** Draft v1.1

---

## 1. Bentuk Akhir Produk

UMKMShop selesai sebagai aplikasi Android native untuk marketplace UMKM lokal dengan model seperti Facebook Marketplace: penjual memajang produk, pembeli menemukan produk, lalu transaksi diselesaikan lewat chat di luar sistem.

Hasil akhir MVP harus terasa seperti produk yang utuh, bukan demo teknis:

- Pengguna bisa daftar, login, dan melengkapi profil dasar.
- Satu akun bisa dipakai sebagai pembeli dan penjual.
- Penjual bisa membuat, mengubah, menonaktifkan, dan menampilkan produk dengan foto.
- Pembeli bisa melihat katalog produk aktif, mencari produk, memakai filter dasar, dan membuka detail produk.
- Pembeli bisa menekan tombol chat dari detail produk dan langsung masuk ke percakapan yang terkait produk tersebut.
- Kedua pihak bisa bertukar pesan realtime.
- Penjual menerima push notification saat ada pesan baru atau reminder chat belum dibalas.

---

## 2. Bentuk Akhir Flow Bisnis

### 2.1 Onboarding

Pengguna membuka app, daftar atau login, lalu masuk ke beranda katalog dalam mode pembeli. Setelah profil dasar tersedia, pengguna bisa berpindah mode ke penjual tanpa membuat akun baru.

### 2.2 Flow Penjual

Penjual membuka dashboard toko, menambahkan produk, mengisi nama, harga, deskripsi, kategori, dan foto. Setelah disimpan, produk langsung muncul di katalog publik jika statusnya aktif. Penjual juga bisa mengedit atau menonaktifkan produk miliknya.

### 2.3 Flow Pembeli

Pembeli membuka katalog, mencari atau memfilter produk, lalu membuka detail produk. Jika tertarik, pembeli menekan tombol chat penjual. Sistem membuat atau membuka `chat_rooms` berdasarkan kombinasi unik `buyer_id`, `seller_id`, dan `product_id`.

### 2.4 Flow Chat

Pesan tersimpan permanen di `messages`. Saat pesan dikirim, `chat_rooms.last_message_at` diperbarui, pesan muncul realtime untuk participant yang sedang online, dan job notifikasi dikirim ke queue `notifications`.

### 2.5 Flow Notifikasi

Worker stateless membaca queue `notifications` dari `pgmq`, mengambil token FCM penerima dari `push_tokens`, lalu mengirim push notification. Job sukses dihapus, job gagal dibiarkan retry lewat visibility timeout, dan job bermasalah diarsipkan setelah melewati batas percobaan.

---

## 3. Bentuk Akhir Arsitektur

- Android native memakai Kotlin dan Jetpack Compose.
- Supabase Cloud menjadi backend utama untuk Auth, Postgres, Storage, Realtime, Queues, dan Cron.
- Mobile app melakukan CRUD produk, gambar, chat room, pesan, dan push token langsung via Supabase SDK.
- Row Level Security menjadi boundary keamanan utama.
- Tidak ada REST API custom untuk CRUD MVP.
- Queue notifikasi memakai Supabase Queues (`pgmq`).
- Scheduler housekeeping memakai `pg_cron`.
- Server 2GB hanya menjalankan Kotlin/Ktor worker stateless untuk push notification.
- Semua source of truth tetap di Postgres; worker tidak menyimpan state lokal.

---

## 4. Kriteria Sukses MVP

| Area | Hasil akhir yang harus terbukti |
|---|---|
| Produk | Seller bisa upload produk, buyer bisa menemukan produk, chat, dan lanjut transaksi manual di luar app. |
| Auth | User bisa signup, login, logout, restore session, dan punya profil dasar. |
| Katalog | Produk aktif tampil ke pembeli; produk inactive tidak tampil di katalog umum. |
| Chat | Dua akun berbeda bisa chat realtime pada room yang terikat ke produk. |
| Notifikasi | Pesan baru dan reminder chat menghasilkan push notification lewat worker. |
| Security | RLS mencegah user membaca atau mengubah data yang bukan haknya. |
| Operasional | Worker restart aman, queue tidak hilang, dan server 2GB tidak mendekati OOM pada load ringan. |

---

## 5. Non-Goals MVP

- Tidak ada payment gateway, escrow, status pembayaran, atau pencatatan transaksi selesai.
- Tidak ada integrasi kurir, ongkir, atau tracking.
- Tidak ada iOS.
- Tidak ada rating, review, wishlist, sistem report, atau moderasi otomatis.
- Tidak ada Redis, RabbitMQ, Kafka, atau microservices.
- Tidak ada backend REST custom untuk menggantikan Supabase CRUD.

---

## 6. Batas yang Sengaja Dijaga

MVP ini menguji apakah pola Supabase-first cukup untuk marketplace kecil. Selama traffic masih di skala MVP, kompleksitas tambahan harus ditolak kecuali ada bukti operasional bahwa batasnya sudah tercapai.

Yang harus dipantau setelah MVP jalan:

- Queue depth `pgmq`.
- Job gagal atau archived.
- Token FCM invalid.
- Latensi pesan realtime.
- Query katalog dan chat yang mulai lambat.
- Policy RLS yang terlalu longgar atau terlalu ketat.

