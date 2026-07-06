# TICKET-011: FCM Token Registration

## Tujuan

Menyimpan token device user supaya worker bisa mengirim push notification untuk pesan baru dan reminder chat.

## Scope

- [x] Integrasi Firebase Messaging di Android.
- [x] Ambil token FCM saat user login.
- [x] Simpan token ke tabel `push_tokens`.
- [x] Update token saat Firebase melakukan token refresh.
- [x] Hapus atau abaikan duplikasi berdasarkan unique constraint.

## Acceptance Criteria

- Token FCM user tersimpan di `push_tokens`.
- Login ulang tidak membuat duplikasi token yang sama.
- User hanya bisa manage token miliknya sendiri.
- Service account FCM tidak pernah masuk Android app.

## Catatan Implementasi

- Android hanya bertugas register token; pengiriman push dilakukan worker.
- Simpan `device_info` sederhana jika tersedia, tetapi jangan jadikan blocker.

## Validasi

- [x] `./gradlew build`
- [ ] Login di device/emulator dan cek row `push_tokens`.
- [ ] Trigger token refresh jika memungkinkan.

## Status 2026-07-02

Implementasi Android selesai. App menambahkan Firebase Messaging dependency, service `UMKMFirebaseMessagingService`, dan repository upsert ke `push_tokens` memakai constraint `(user_id, fcm_token)` supaya login ulang tidak membuat duplikasi. Auth flow melakukan registrasi token setelah session restore, signup yang langsung menghasilkan session, dan login. Token refresh dari Firebase juga melakukan upsert untuk user yang sedang login.

Validasi nyata:

- `./gradlew build` berhasil.
- Pencarian source untuk marker service account/private key tidak menemukan hasil.
- APK debug dan release dicek dengan `strings` dan tidak mengandung marker `service_account`, `private_key`, `firebase-adminsdk`, `BEGIN PRIVATE KEY`, `client_email`, atau `serviceAccountKey`.

Blocker validasi manual:

- `google-services.json` belum ada di repo/environment agent, sehingga token FCM nyata belum bisa diterbitkan dari Firebase project.
- Device/emulator/`adb` tidak tersedia di environment agent, sehingga login dari app dan cek row `push_tokens` belum dieksekusi.

## Status 2026-07-03

Review hardening selesai:

- Debug/agent build tetap boleh berjalan tanpa `google-services.json`, tetapi release build sekarang fail-fast jika Firebase config tidak tersedia.
- `SUPABASE_PUBLISHABLE_KEY` divalidasi saat konfigurasi Gradle dan wajib memakai format publishable key `sb_publishable_`; runtime auth guard juga tidak lagi memakai substring `service_role`.
- Signed-in flow meminta runtime permission `POST_NOTIFICATIONS` pada Android 13+.
- Logout menghapus token FCM device saat ini dari `push_tokens` sebelum `signOut()`.
- `onNewToken` tidak lagi best-effort saja; kegagalan registrasi token dienqueue ke WorkManager dengan retry exponential backoff.
- Review lanjutan memastikan repository auth/token tidak mengevaluasi `SupabaseClientProvider.client` saat konstruksi, sehingga debug/agent build tanpa config bisa menampilkan pesan konfigurasi dari `restoreSession()`.
- Feature ViewModel tidak lagi dibuat di root composable saat user masih di auth screen; ViewModel katalog, seller, dan chat dibuat di destination yang membutuhkannya.

Validasi nyata:

- `./gradlew :app:assembleDebug` berhasil.
- `./gradlew :app:assembleRelease` gagal cepat sesuai desain dengan pesan `google-services.json is required for release builds so Firebase Messaging is configured.`
- `./gradlew :app:assembleDebug :worker:test` berhasil setelah review lanjutan.
