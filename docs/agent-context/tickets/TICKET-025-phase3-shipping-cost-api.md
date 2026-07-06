# TICKET-025: Phase 3 Shipping Cost API

## Tujuan

Menambahkan estimasi ongkir otomatis lewat api.co.id saat seller membuat invoice, dengan fallback manual jika api.co.id lambat/down.

## Scope

- Gunakan api.co.id sebagai provider ongkir/API wilayah.
- Tambah data alamat presisi di `profiles`.
- Simpan `postal_code`.
- Simpan `village_code` sesuai `BACKEND_SPEC-v2.md`.
- UI input alamat yang tidak meminta user mengetik kode internal manual.
- Saat buat invoice, panggil API ongkir berdasarkan asal, tujuan, dan `weight_grams`.
- Simpan `shipping_cost` sebagai snapshot di `orders`.
- Fallback input manual jika API gagal.

## Acceptance Criteria

- Seller dan buyer bisa menyimpan alamat cukup presisi untuk kalkulasi ongkir.
- Form invoice bisa menghitung ongkir otomatis saat data alamat dan berat tersedia.
- Jika API ongkir gagal/timeout, seller tetap bisa lanjut dengan input manual.
- `shipping_cost` order tidak dihitung ulang saat order ditampilkan ulang.
- API key provider ongkir tidak masuk Android app jika provider membutuhkan secret.
- User memilih kelurahan dari hasil pencarian api.co.id, bukan mengisi `village_code` manual.

## Catatan Implementasi

- `city` Fase 2 tetap dipakai untuk filter katalog; `postal_code` dan `village_code` Fase 3 melengkapi, bukan menggantikan.
- Jika api.co.id membutuhkan secret, panggilan API harus lewat Edge Function/server, bukan langsung dari Android.
- Jika api.co.id punya public key aman untuk client, tetap dokumentasikan risiko dan rate limit.
- Timeout harus pendek dan error UI harus jelas.

## Validasi

- [x] Provider ongkir dipilih: api.co.id.
- [ ] Migration alamat berhasil.
- [ ] User tidak bisa update alamat user lain.
- [ ] Ongkir otomatis berhasil untuk data valid.
- [ ] Fallback manual berhasil saat API gagal.
- [ ] `./gradlew build`

Catatan validasi 2026-07-04:
- `:app:assembleDebug` berhasil.
- `:app:testDebugUnitTest` berhasil.
- Full `./gradlew build` belum bisa dijadikan bukti karena release guard gagal sebelum compile release: `google-services.json is required for release builds so Firebase Messaging is configured.`
- Migrasi/validasi Supabase dan deploy/typecheck Edge Function belum dijalankan di environment ini karena `supabase` CLI dan `deno` tidak tersedia.
