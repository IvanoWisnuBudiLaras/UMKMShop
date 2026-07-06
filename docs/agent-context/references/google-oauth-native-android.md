# Google OAuth Native Android — TICKET-027

Status: dokumentasi konfigurasi untuk auth utama Android. Implementasi kode Google native belum ada di app saat audit TICKET-027; auth Android yang berjalan saat ini masih Email/Password via Supabase Auth.

## Keputusan

- Google OAuth native adalah jalur login utama Android bersama Email/Password.
- Email/Password tidak dihapus.
- OAuth Server UMKMShop learning bukan pengganti login Android dan bukan dependency Xendit, ongkir, atau payment E2E.
- Android tidak boleh menyimpan Google client secret, Supabase service role key, private signing key OAuth Server, atau confidential client secret.

## Identitas Android

| Item | Nilai |
|---|---|
| Package / applicationId | `com.application.umkmshop` |
| Namespace | `com.application.umkmshop` |
| Debug SHA-1 | `A9:F6:E3:5B:B9:9E:BD:7E:5E:F0:FD:08:07:A5:B8:B4:7B:61:1E:C4` |
| Debug SHA-256 | `41:E0:D0:E2:43:3F:D2:1E:82:F5:BA:54:C5:CB:27:BE:AC:A9:17:83:C9:AC:34:BB:AA:85:F1:31:84:5A:35:AD` |
| Release SHA-1 | Belum tersedia di environment agent; `:app:signingReport` menunjukkan release `Config: null`. |

Sumber validasi: `./gradlew --no-configuration-cache :app:signingReport` pada 2026-07-04.

## Google Cloud Setup

1. Buat OAuth Client ID tipe Android.
2. Isi package name `com.application.umkmshop`.
3. Tambahkan SHA-1 debug di atas.
4. Tambahkan SHA-1 release setelah release keystore tersedia.
5. Simpan Android OAuth Client ID di Google Cloud Console dan catat di Supabase Dashboard.

## Supabase Google Provider

Di Supabase Dashboard, buka Authentication → Providers → Google:

- Aktifkan Google provider.
- Tambahkan Android OAuth Client ID ke daftar Client IDs.
- Jika memakai Web client untuk flow lain, Web client ID tetap dikelola di provider Google sesuai kebutuhan Supabase.
- Jangan masukkan Google client secret ke Android. Secret provider hanya boleh berada di Supabase/server-side configuration.

## Smoke Checklist

- [x] Package/applicationId terverifikasi dari Gradle.
- [x] Debug SHA-1 terverifikasi dari signing report.
- [ ] Android OAuth Client ID belum tersedia di repo/environment agent.
- [ ] Supabase Google provider/client IDs belum bisa diverifikasi dari environment agent.
- [ ] Smoke Google OAuth native dari device/emulator belum dijalankan di environment agent.

## Implementasi Android yang Masih Dibutuhkan

Tambahkan Credential Manager / Sign in with Google native di layar auth, lalu tukar Google ID token ke Supabase Auth session. Jalur Email/Password di `AuthRepository` dan `AuthViewModel` tetap dipertahankan.
