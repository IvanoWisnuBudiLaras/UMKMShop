# OAuth 2.0 & OIDC Learning Notes (Senior Engineering Perspective)

Proyek ini bukan sekadar implementasi "Login", melainkan pembangunan **Authorization Server** mandiri menggunakan Ktor dan Supabase. Berikut adalah alasan di balik keputusan arsitektur yang diambil.

## 1. Asymmetric Signing (RS256 vs HS256)

**Keputusan:** Menggunakan **RS256** (RSA Signature with SHA-256).

**Alasan:**
- **HS256 (Symmetric)** menggunakan *shared secret* yang sama untuk menandatangani (sign) dan memverifikasi token. Jika ada 10 aplikasi pihak ketiga yang ingin memverifikasi token kita, kita harus memberikan secret tersebut kepada mereka semua. Jika satu bocor, seluruh sistem hancur.
- **RS256 (Asymmetric)** menggunakan pasangan kunci: **Private Key** (disimpan ketat di server kita untuk membuat token) dan **Public Key** (disebarkan ke dunia untuk memverifikasi).
- Dengan RS256, aplikasi pihak ketiga bisa memverifikasi bahwa token itu benar dari UMKMShop tanpa pernah punya kemampuan untuk membuat token palsu. Ini adalah dasar dari ekosistem identitas yang scalable (seperti Google dan AWS).

## 2. Authorization Code Grant & Single-Use Codes

**Keputusan:** Authorization Code wajib berumur pendek (max 60 detik) dan **Single-Use** (ditandai `used` di database).

**Alasan:**
- Kode otorisasi dikirim melalui browser (front-channel) yang tidak aman. Ada risiko kode dicuri dari riwayat browser atau cache proxy.
- Dengan menandainya sebagai `used`, jika pencuri mencoba menukar kode yang sama untuk kedua kalinya, server akan mendeteksi redundansi tersebut. RFC 6749 menyarankan jika kode yang sudah dipakai dikirim lagi, server harus membatalkan semua token yang pernah diterbitkan dari kode tersebut (deteksi replay attack).

## 3. Mandatory PKCE (RFC 7636)

**Keputusan:** Mewajibkan PKCE (`code_challenge` & `code_verifier`) bahkan untuk server-side client.

**Alasan:**
- PKCE awalnya diciptakan untuk mobile app yang tidak bisa menyimpan *client secret* dengan aman. Namun, sekarang dianggap sebagai *best practice* untuk semua jenis client untuk mencegah **Authorization Code Injection**.
- Tanpa PKCE, jika pencuri berhasil mendapatkan `code`, mereka bisa mencoba menukarnya dari server mereka sendiri. Dengan PKCE, mereka wajib memiliki `code_verifier` (rahasia sekali pakai yang dibuat di browser saat mulai login) untuk mendapatkan token.

## 4. Refresh Token Rotation & Theft Detection

**Keputusan:** Setiap kali Refresh Token digunakan, ia harus diganti dengan yang baru (**Rotation**). Jika Refresh Token lama (yang sudah dirotasi/revoked) digunakan kembali, seluruh rantai token untuk user tersebut harus dibatalkan.

**Alasan:**
- Refresh Token berumur panjang (30 hari). Jika dicuri, pencuri bisa memiliki akses selamanya.
- Dengan rotasi, kita bisa mendeteksi jika ada dua pihak (user asli dan pencuri) yang memegang token yang sama. Saat salah satu menggunakan token lama yang sudah expired/rotated, itu adalah sinyal merah bahwa telah terjadi kebocoran, dan sistem secara otomatis mengunci akses demi keamanan user.

## 5. Unified Backend (Worker + OAuth)

**Keputusan:** Menggabungkan Notification Worker dan OAuth Server dalam satu aplikasi Kotlin/Ktor.

**Alasan:**
- **Efisiensi Resource**: Menjalankan dua aplikasi terpisah di server 2GB akan memakan overhead JVM dua kali lipat. Dengan satu aplikasi, kita berbagi koneksi pool (HikariCP) dan memori heap secara efisien.
- **Stateless Architecture**: Kedua modul bersifat stateless. OAuth menyimpan state di tabel `oauth_*`, dan Worker membaca antrean dari `pgmq`. Keduanya bisa di-restart kapan saja tanpa kehilangan data.
- **Isolasi**: Meskipun dalam satu proses, keduanya berjalan di Coroutine Scope yang berbeda. Kegagalan di loop Worker (misal: API Firebase down) tidak akan mengganggu proses Login user.

**Trade-off Blast Radius:**
Jika terjadi *Memory Leak* di bagian Worker, seluruh server (termasuk OAuth) akan terkena dampaknya. Namun, untuk skala MVP UMKMShop, penghematan biaya dan kemudahan manajemen (satu systemd service) lebih bernilai daripada isolasi total microservice.
