# Design.md — UMKMShop

**Pelengkap dari:** PRD.md, Goal.md
**Font:** Inter · **Ikon:** Google Material Symbols · **Target device:** Android low–mid range (UMKM di Indonesia belum tentu pakai flagship)

---

## 1. Prinsip Desain

Mengikuti filosofi yang sudah dikunci di brief logo: **kesederhanaan yang disengaja.** Setiap keputusan visual di dokumen ini — termasuk motion — harus bisa dijawab dengan "ini menyembunyikan kerumitan sistem, bukan menambah kerumitan visual." Kalau sebuah animasi atau elemen tidak membantu pengguna memahami apa yang terjadi, itu dicoret, bukan dipertahankan karena "terlihat bagus."

Satu hal yang perlu ditegaskan sejak awal karena beda dari kebanyakan referensi desain app 2026 yang menyarankan animasi berat/immersive: target pengguna UMKMShop kemungkinan besar pakai HP kelas menengah-bawah, bukan flagship. Jadi prinsip performa **mendahului** prinsip estetika di setiap keputusan motion — bukan trade-off yang dipertimbangkan belakangan.

---

## 2. Tipografi — Inter

Inter dipilih bukan cuma karena populer, tapi karena alasan efisiensi konkret: **satu file variable font mencakup semua weight** (300–700) tanpa perlu bundling banyak file font terpisah — relevan langsung untuk ukuran APK dan waktu render di device rendah.

| Peran | Weight | Ukuran (sp) | Contoh Pemakaian |
|---|---|---|---|
| Headline | SemiBold (600) | 24 | Judul layar ("Toko Saya", "Katalog") |
| Title | Medium (500) | 18 | Nama produk, nama penjual di header chat |
| Body | Regular (400) | 15 | Deskripsi produk, isi pesan chat |
| Label | Medium (500) | 13 | Teks tombol, tag kategori |
| Caption | Regular (400) | 12 | Timestamp chat, harga di list produk (angka sengaja diberi tracking sedikit lebih lebar untuk keterbacaan nominal Rupiah) |

**Aturan:** tidak lebih dari 3 weight aktif dalam satu layar (biasanya SemiBold untuk judul, Medium untuk aksen, Regular untuk isi). Jangan pakai Bold (700) di body text — cukup untuk kondisi darurat seperti angka harga di halaman checkout kalau nanti Fase 3 (payment) berjalan.

---

## 3. Warna

| Token | Hex | Peran |
|---|---|---|
| `primary` (Terracotta) | `#E86A33` | Aksi utama, CTA, atap ikon logo, badge notifikasi |
| `primary-dark` (Teal) | `#1B4B43` | Elemen struktural, ikon aktif, teks brand "UMKM" |
| `background` | `#FAF8F5` | Latar utama — off-white hangat, bukan putih steril, selaras nuansa lokal |
| `surface` | `#FFFFFF` | Kartu produk, bubble chat penjual |
| `surface-accent` | `#1B4B43` (10% opacity di atas background) | Bubble chat milik sendiri |
| `text-primary` | `#1A1F1D` | Teks utama |
| `text-secondary` | `#6B6862` | Timestamp, placeholder, teks pendukung |
| `border` | `#E5E1DA` | Divider, outline input |
| `success` | `#3C8C5C` | Konfirmasi ("Produk berhasil disimpan") |
| `error` | `#D64545` | Validasi gagal, tombol laporkan produk |

Kontras `text-primary` di atas `background` dan `primary` di atas `surface` sudah dicek memenuhi rasio minimum WCAG AA untuk teks normal — jangan turunkan opacity token warna ini demi alasan estetika tanpa cek ulang kontrasnya.

### 3.1 Dark Mode

| Token | Light | Dark |
|---|---|---|
| `background` | `#FAF8F5` | `#14201C` |
| `surface` | `#FFFFFF` | `#1D2B26` |
| `primary` | `#E86A33` | `#EF8556` |
| `text-primary` | `#1A1F1D` | `#F5F2EC` |
| `text-secondary` | `#6B6862` | `#A8A49C` |
| `border` | `#E5E1DA` | `#2E3B36` |

Dua keputusan yang sengaja diambil, bukan sekadar invert warna:
- **Bukan hitam pekat (`#000000`)** untuk background dark — dipakai teal gelap senada brand (`#14201C`), karena hitam murni terasa "kosong"/tidak berkarakter, walau ironisnya itu justru warna paling gelap yang tersedia.
- **`primary` dinaikkan kecerahannya** di dark mode (`#E86A33` → `#EF8556`) — warna jenuh terasa lebih "berat" di atas latar gelap dibanding di atas latar terang, jadi perlu dikompensasi, bukan dipakai hex yang sama persis di kedua mode.

---

## 4. Ikon — Google Material Symbols

- **Style:** *Rounded* — dipilih di atas *Sharp*/*Outlined* karena sudut membulat lebih selaras dengan nuansa hangat brand dibanding kesan korporat/tajam dari Sharp.
- **Weight default:** 400 untuk ikon interaktif (tombol, tab), 300 untuk ikon dekoratif/nonaktif.
- **Fill sebagai state, bukan cuma warna:** untuk tab bar dan toggle Pembeli/Penjual, gunakan parameter `fill` Material Symbols (0 → 1) sebagai penanda status aktif — ikon "terisi" saat dipilih, outline saat tidak. Ini teknik yang sama dipakai microinteraction like-button populer (ikon hati yang terisi warna saat ditekan) — pengenalan statusnya instan tanpa butuh label teks tambahan.
- **Ikon inti yang dipakai:** `storefront` (mode penjual), `search` (katalog), `chat_bubble` (chat), `favorite` (wishlist, Fase 2), `notifications` (badge reminder), `location_on` (filter lokasi, Fase 2).

---

## 5. Motion — Prinsip & Anggaran Performa

### 5.1 Aturan Dasar
- Setiap animasi harus punya **fungsi** (menjelaskan perubahan state), bukan dekorasi. Kalau tidak bisa dijawab "animasi ini membantu pengguna paham apa yang terjadi", jangan dipakai.
- **Durasi standar, jangan campur-campur:** micro-interaction (tap tombol, fill ikon) 100–150ms; transisi kecil (bubble chat muncul, kartu produk tap) 200–250ms; transisi antar layar 300ms. Konsistensi durasi di seluruh app lebih penting daripada animasi yang "menarik" sendiri-sendiri.
- **Easing:** pakai kurva ease-out untuk elemen masuk (terasa responsif), ease-in-out untuk transisi antar state. Hindari linear — terasa kaku/robotic.
- **Hormati pengaturan Reduce Motion** di level sistem Android — animasi non-esensial (bounce, parallax) otomatis nonaktif, animasi esensial (loading indicator) tetap ada tapi versi sederhana.

### 5.2 Pilihan Teknologi — Prioritaskan yang Paling Ringan Dulu

| Kebutuhan | Pilihan | Kenapa |
|---|---|---|
| Animasi properti UI biasa (fade, scale, slide, warna) | **Jetpack Compose native** (`animateFloatAsState`, `AnimatedVisibility`, `updateTransition`) | Dikompilasi native, tidak decode file tambahan, paling ringan di CPU/GPU — pilihan default untuk hampir semua interaksi di app ini |
| Animasi ilustratif kompleks (state kosong, onboarding) | **Lottie** (vector JSON, bukan video/GIF) | Dipakai sangat terbatas — cuma di momen yang benar-benar butuh cerita visual, bukan interaksi rutin. Tetap jauh lebih ringan dari video karena vector-based, tapi lebih berat dari native Compose, jadi dijatah |
| Video/GIF raster untuk animasi UI | **Tidak dipakai sama sekali** | Paling boros memori & baterai, tidak ada alasan kuat untuk kasus app ini |

**Anggaran performa konkret:** target minimum 60fps stabil di device kelas menengah-bawah (bukan flagship) — ukur pakai Android Profiler sebelum satu animasi dianggap "selesai", bukan cuma terlihat mulus di emulator/device development.

### 5.3 Motion per Momen (dipetakan ke flow di PRD.md)

| Momen | Motion | Referensi PRD |
|---|---|---|
| Kirim pesan chat | Bubble muncul dari bawah, fade + slide 200ms, ease-out | §5.4 |
| Notifikasi chat baru masuk (badge) | Pulse sekali (scale 1→1.15→1, 300ms) — bukan berulang, cukup sekali untuk menarik perhatian tanpa mengganggu | §5.4 |
| Produk berhasil disimpan (penjual) | Ikon check morph dari ikon simpan, 250ms — pola serupa animasi "like" yang mengubah ikon jadi terisi sebagai konfirmasi instan, bukan sekadar toast teks | §5.2 |
| Toggle mode Pembeli ⇄ Penjual | Cross-fade warna latar/header 250ms, ease-in-out — perubahan konteks besar butuh transisi yang terasa "berpindah ruang", bukan potongan tiba-tiba | §4 |
| Reminder pengingat chat (dari cron) | Muncul sebagai notifikasi sistem standar Android — **tidak perlu** animasi custom in-app, ini kejadian pasif, jangan dipaksa jadi momen visual | §5.5 |
| Pull-to-refresh katalog | Pakai pola bawaan platform Android — jangan reinvent, pengguna sudah familiar | §5.3 |

---

## 6. Referensi Desain (Motion Ringan)

- **Material Design Motion System** (`m3.material.io/styles/motion`) — rujukan resmi Google untuk durasi/easing yang konsisten dengan komponen Material Symbols yang sudah dipakai di §4.
- **Jetpack Compose Animation docs** (`developer.android.com/develop/ui/compose/animation`) — sumber utama implementasi `animateFloatAsState`/`Modifier` untuk semua motion di §5.3, karena native dan paling hemat resource untuk device target.
- **LottieFiles** (`lottiefiles.com`) — kalau butuh animasi ilustratif terbatas (§5.2), cari aset vector JSON ringan di sini, bukan bikin/download animasi berbasis video.
- **Pola referensi konkret yang relevan (bukan buat ditiru mentah, tapi buat dipahami prinsipnya):** animasi "like" pada X/Twitter — ikon hati yang terisi warna dan sedikit membesar saat ditekan adalah contoh baik micro-interaction yang murah secara komputasi tapi punya dampak persepsi besar; pola serupa dipakai untuk konfirmasi simpan produk di §5.3.

---

## 7. Yang Sengaja Dihindari

- Parallax scroll, animasi 3D/depth, atau transisi "immersive" ala tren app besar 2026 — tidak sesuai anggaran performa device target dan tidak menambah kejelasan, cuma menambah beban render.
- Animasi berulang/looping tanpa trigger jelas (contoh: ikon yang terus bergoyang) — mengganggu, bukan membantu.
- Haptic feedback berlebihan di setiap tap — dipakai sangat selektif (misal cuma saat kirim pesan berhasil), bukan default di semua interaksi.