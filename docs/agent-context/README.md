# Agent Context Hub

Folder ini adalah wadah konteks proyek agar detail produk, rencana, tiket, dan dokumentasi tidak perlu selalu ditempel di chat. Saat meminta bantuan coding, cukup arahkan agent ke file yang relevan di folder ini.

## Cara Pakai

- Baca `GOALS.md` untuk memahami tujuan utama proyek.
- Baca `PRD.md` jika perubahan menyentuh kebutuhan produk atau perilaku fitur.
- Baca `PLAN.md` untuk melihat prioritas dan urutan implementasi.
- Baca file di `tickets/` untuk task spesifik yang siap dikerjakan.
- Baca `DOCS.md` untuk catatan teknis, arsitektur, dan keputusan penting.
- Baca `decisions/` jika perlu memahami alasan keputusan sebelumnya.

## Aturan Konteks Ringkas

- Jangan masukkan semua isi folder ke prompt kecuali memang dibutuhkan.
- Untuk coding harian, mulai dari `GOALS.md`, `PLAN.md`, dan tiket terkait.
- Update file setelah keputusan berubah supaya chat tetap ringan.
- Simpan detail panjang di file referensi, bukan di chat.

## Struktur

```text
docs/agent-context/
├── README.md
├── GOALS.md
├── PRD.md
├── PLAN.md
├── DOCS.md
├── tickets/
│   └── TICKET-000-template.md
├── decisions/
│   └── ADR-000-template.md
└── references/
    └── README.md
```
