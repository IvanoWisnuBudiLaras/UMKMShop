# TICKET-007: Supabase Auth

**Status:** Implementasi selesai, menunggu smoke manual di device/emulator.

## Tujuan

Menghubungkan Android app ke Supabase Auth agar onboarding PRD bisa berjalan dari app nyata.

## Scope

- [x] Tambahkan Supabase Kotlin SDK untuk Auth dan dependency pendukung.
- [x] Konfigurasi Supabase URL dan anon key dengan cara yang aman untuk client app.
- [x] Implement signup, login, logout, dan session restore.
- [x] Pastikan profile dasar tersedia setelah signup.

## Acceptance Criteria

- User bisa daftar dari app.
- User bisa login dari app.
- Session tetap tersedia setelah app restart.
- User bisa logout.
- Setelah signup, user punya row `profiles` atau flow eksplisit untuk membuat profil.

## Catatan Implementasi

- Jangan pernah memasukkan service role key ke Android app.
- Pembuatan `profiles` memakai trigger `auth.users` lewat migration `ticket_007_profile_on_signup`; keputusan sudah dicatat di BACKEND_SPEC dan DOCS.
- Android membaca `SUPABASE_URL` dan `SUPABASE_PUBLISHABLE_KEY` dari `local.properties` atau environment ke `BuildConfig`. Publishable/anon key aman untuk client, service role key tetap dilarang.
- Supabase Kotlin SDK dipin ke `3.1.4` dan Ktor ke `3.1.3` karena Ktor `3.2.0` gagal dexing di minSdk 24.
- Profile fetch harus difilter by signed-in user id walaupun RLS memperbolehkan authenticated user melihat profil lain untuk kebutuhan marketplace.
- Session material Supabase tidak boleh ikut Android backup; app backup dimatikan dan backup/data-extraction rules mengecualikan app data.
- `AuthViewModel` dibuat lewat AndroidX `viewModel()` supaya mengikuti ViewModelStore dan lifecycle Compose.
- `org.jetbrains.kotlin.android` eksplisit tidak diterapkan karena gagal build dengan konflik extension `kotlin`; `./gradlew :app:tasks --all` tetap menampilkan `compileDebugKotlin`, `compileReleaseKotlin`, dan `compileDebugUnitTestKotlin`.

## Validasi

- [x] `./gradlew build`
- [x] Supabase trigger `on_auth_user_created_create_profile` ada di `auth.users`.
- [x] Function `public.handle_new_auth_user_profile()` tidak memberi execute privilege ke `PUBLIC`, `anon`, atau `authenticated`.
- [x] Supabase security advisor bersih.
- [x] Supabase performance advisor bersih.
- [x] Profile fetch memakai filter `id = currentUser.id` sebelum `decodeSingle`.
- [x] Android backup/session transfer risk dimitigasi.
- [x] `AuthViewModel` memakai AndroidX ViewModelStore.
- [x] Task Kotlin compile tersedia: `compileDebugKotlin`, `compileReleaseKotlin`, `compileDebugUnitTestKotlin`.
- [ ] Signup manual dari app. Belum dieksekusi: `adb` tidak tersedia di environment agent.
- [ ] Login manual dari app. Belum dieksekusi: `adb` tidak tersedia di environment agent.
- [ ] Restart app dan cek session restore. Belum dieksekusi: `adb` tidak tersedia di environment agent.
- [ ] Logout manual dari app. Belum dieksekusi: `adb` tidak tersedia di environment agent.
