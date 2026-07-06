# Hapus folder .git (hati-hati, ini akan menghapus history commit lokal Anda)
rm -rf .git

# Inisialisasi ulang
git init

# Tambahkan semua file (sekarang .gitignore akan bekerja dengan benar sejak awal)
git add .

# Buat commit pertama yang bersih
git commit -m "initial commit: UMKM Shop with lightweight docker"
