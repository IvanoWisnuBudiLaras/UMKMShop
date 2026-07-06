# ADR-001: Google OAuth Native and OAuth Server Learning Track

## Status

Accepted for learning scope. Updated 2026-07-05: backend runtime is a single Kotlin/Ktor learning app, not separate worker and OAuth microservices.

## Context

UMKMShop sudah memakai Supabase Auth untuk auth utama Android. User ingin tetap memakai Google OAuth, tetapi juga ingin membangun OAuth Server sendiri sebagai pembelajaran, bukan sekadar membuat OAuth client di Google Cloud.

OAuth Server sendiri berbeda dari login biasa. Ia menjadi authorization server yang menerbitkan authorization code, access token, refresh token, ID token, dan JWKS untuk client pihak ketiga. Salah desain di area ini bisa membuka risiko token replay, account takeover, atau akses data lintas client.

## Decision

- Google OAuth native Android tetap menjadi jalur login utama bersama Email/Password.
- OAuth Server UMKMShop dibuat sebagai learning/demo track Fase 3, bukan dependency untuk Xendit, ongkir, atau auth utama Android.
- OAuth Server harus dipisahkan dari auth utama Android, tetapi boleh berjalan di process/container backend Kotlin/Ktor yang sama dengan background worker FCM untuk tujuan pembelajaran monolith.
- Worker FCM tetap consumer `pgmq`; OAuth Server learning tetap bukan jalur login utama Android.
- OAuth Server awal hanya mendukung Authorization Code + PKCE dan Refresh Token.
- Public client tidak boleh memakai client secret.
- Confidential client secret, authorization code, dan refresh token harus disimpan hashed.
- JWT harus memakai asymmetric signing key dan public key diekspos lewat JWKS.
- Scope awal dibatasi ke `openid`, `email`, dan `profile`.

## Consequences

- Fase 3 produk tetap bisa berjalan tanpa menunggu OAuth Server learning selesai.
- Runtime backend pembelajaran sekarang satu modul/container agar konsep HTTP API, OAuth learning, background worker, queue, Firebase, dan Supabase terlihat dalam satu aplikasi.
- Sesi baru harus mengerjakan OAuth Server hanya jika user menyebut `TICKET-027`.
- Sebelum dipakai partner nyata, OAuth Server wajib melewati security review, negative tests, secret scan, dan keputusan domain/issuer HTTPS.
