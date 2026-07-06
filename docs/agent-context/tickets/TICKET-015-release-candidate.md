# TICKET-015: Release Candidate

## Tujuan

Menyiapkan build MVP Android untuk distribusi internal atau closed beta ke tester nyata.

## Scope

- Konfigurasi release build.
- Review Android permissions.
- Review exposure secret di app bundle.
- Build APK/AAB release.
- Install release build di device.
- Siapkan checklist distribusi internal.

## Acceptance Criteria

- APK/AAB release berhasil dibuat.
- Tidak ada service role key atau FCM service account di app bundle.
- App release bisa login, browse produk, chat, dan menerima notifikasi.
- Checklist distribusi internal tersedia.

## Catatan Implementasi

- Supabase anon key boleh ada di client; service role key tidak boleh.
- Jangan mengubah scope MVP di sesi ini kecuali ada blocker release.
- Checklist distribusi internal: `docs/agent-context/release-candidate-checklist.md`.

## Status 2026-07-03

- Release signing guard ditambahkan ke Gradle: release build wajib punya `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, dan `RELEASE_KEY_PASSWORD` dari `local.properties`/environment.
- `.gitignore` diperketat untuk mencegah keystore dan file key umum masuk repository.
- `./gradlew --no-configuration-cache :app:assembleDebug :worker:test` berhasil.
- `./gradlew --no-configuration-cache :app:assembleRelease` dan `:app:bundleRelease` gagal cepat karena `app/google-services.json` belum tersedia. Ini blocker release yang disengaja agar FCM tidak rusak diam-diam.
- Secret scan source dan APK debug tidak menemukan marker `service_account`, `private_key`, `firebase-adminsdk`, `BEGIN PRIVATE KEY`, `client_email`, `service_role`, `sb_secret_`, `SUPABASE_SERVICE_ROLE_KEY`, atau `GOOGLE_APPLICATION_CREDENTIALS`.
- Android permission dump dari APK debug menunjukkan `INTERNET`, `POST_NOTIFICATIONS`, permission Firebase Messaging, dan permission runtime WorkManager/Firebase; tidak ada lokasi, kontak, kamera, atau storage permission.
- Supabase MCP aktif untuk project UMKMShop. Security advisor bersih; performance advisor hanya INFO `unused_index` untuk `idx_chat_rooms_last_message_id`.
- Install release dan smoke test auth/katalog/chat/push belum bisa dieksekusi dari agent karena `adb` tidak tersedia dan Firebase config/device/token nyata belum tersedia.

## Validasi

- [ ] Build release APK/AAB.
- [ ] Install di device fisik.
- [ ] Smoke test auth, katalog, chat, dan push notification.
