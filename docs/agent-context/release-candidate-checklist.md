# Release Candidate Checklist

## Required Local Files

- `app/google-services.json` from the Firebase Android app for `com.application.umkmshop`.
- Release signing keystore stored outside git.
- `local.properties` or environment variables:
  - `SUPABASE_URL`
  - `SUPABASE_PUBLISHABLE_KEY`
  - `RELEASE_STORE_FILE`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`

Do not put Firebase service account JSON, Supabase service-role keys, database passwords, or release keystore files in the repository.

## Build Commands

```bash
./gradlew --no-configuration-cache :app:assembleRelease :app:bundleRelease
```

Expected outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## Pre-Distribution Review

- Confirm release APK/AAB is signed with the release key.
- Confirm `strings` on release APK/AAB does not show:
  - `service_account`
  - `private_key`
  - `firebase-adminsdk`
  - `BEGIN PRIVATE KEY`
  - `client_email`
  - `service_role`
  - `sb_secret_`
  - `SUPABASE_SERVICE_ROLE_KEY`
  - `GOOGLE_APPLICATION_CREDENTIALS`
- Confirm Android permissions are limited to network, notification, Firebase messaging, and WorkManager/runtime support permissions.

## Device Smoke Test

Use a physical Android device with Google Play services.

1. Install the release artifact.
2. Create or login with a buyer account.
3. Login with a seller account on another device or session.
4. Seller creates an active product with photo.
5. Buyer browses catalog, searches/filters, opens detail, and starts chat.
6. Buyer sends a message; seller sees the room and receives realtime message.
7. Seller replies; buyer sees reply.
8. Background the seller device, send a buyer message, and verify FCM push notification arrives.
9. Confirm Supabase `push_tokens` has the current device token and `notifications` queue is not accumulating stuck jobs.

## Current Blockers in Agent Environment

- `app/google-services.json` is missing, so release build intentionally fails before producing fresh APK/AAB.
- `adb` is not installed in the agent environment, so release install and device smoke tests cannot be executed here.
- Push notification smoke needs a real Firebase config, a physical/emulated device with Play services, a registered FCM token, and the notification worker configured with Firebase service account credentials.
