# TICKET-013: Worker Deployment

## Tujuan

Menjalankan worker di server 2GB secara stabil, restart-safe, dan sesuai batas resource BACKEND_SPEC.

## Scope

- Setup JDK di server.
- Siapkan environment variable untuk DB dan FCM.
- Buat systemd service dengan `Restart=always`.
- Set JVM `-Xms256m -Xmx512m`.
- Set DB pool max 8.
- Pastikan log bisa dibaca dari `journalctl`.

## Acceptance Criteria

- Worker auto-start setelah server reboot.
- Worker restart tidak menghilangkan job queue.
- Memory tidak mendekati OOM pada load ringan.
- Queue depth turun saat worker berjalan.

## Catatan Implementasi

- Server 2GB hanya menjalankan worker, bukan database atau queue broker.
- Semua source of truth tetap di Supabase Postgres.

## Validasi

- [x] Build artefak worker lokal.
- [x] Unit test worker.
- [x] Validasi sintaks unit systemd.
- [x] Cek baseline queue depth via Supabase MCP.
- [ ] Restart service manual. Blocker: agent tidak punya akses SSH/systemd ke server 2GB.
- [ ] Restart server jika akses tersedia. Blocker: agent tidak punya akses SSH/systemd ke server 2GB.
- [ ] Cek `journalctl`. Blocker: agent tidak punya akses SSH/systemd ke server 2GB.
- [ ] Cek queue depth sebelum dan sesudah worker berjalan. Baseline sudah dicek; after-run butuh worker berjalan di server.

## Hasil Implementasi

- Menambahkan runbook deployment di `deploy/worker/README.md` untuk setup JDK 17, user service, direktori release, install artefak Gradle, enable service, restart check, reboot check, memory check, dan query queue depth.
- Menambahkan unit `deploy/worker/umkmshop-worker.service` dengan `Restart=always`, `JAVA_OPTS=-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError`, `MemoryMax=768M`, journald logging, dan hardening dasar systemd.
- Menambahkan template env `deploy/worker/worker.env.example` dengan `UMKMSHOP_DB_POOL_MAX_SIZE=8`, Supavisor JDBC URL placeholder, queue/retry config, dan Firebase service account placeholder tanpa secret nyata.
- Tidak ada secret nyata yang ditambahkan ke repository.

## Status 2026-07-03

- `./gradlew --no-configuration-cache :app:assembleDebug :worker:test :worker:installDist` berhasil.
- `systemd-analyze verify` terhadap unit service berhasil memakai root sementara yang mensimulasikan path server `/opt/umkmshop-worker/current/bin/worker` dan `/etc/umkmshop-worker/worker.env`.
- Supabase MCP baseline `pgmq.metrics('notifications')`: `queue_length = 0`, `total_messages = 16`.
- `./gradlew --no-configuration-cache build` gagal cepat sesuai desain TICKET-011 karena release build membutuhkan `app/google-services.json`.
- Deploy nyata ke server, restart service, reboot check, `journalctl`, memory live, dan queue depth after-run belum dieksekusi karena environment agent tidak punya akses server.
