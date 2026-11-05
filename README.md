# Kaca Launcher

Launcher Android bergaya *liquid glass* (terinspirasi Glass OS) dengan kustomisasi ala Nova Launcher.

## Fitur
- **Kaca blur asli**: dock, gelembung status, laci aplikasi, menu, dan pengaturan memakai panel kaca yang mem-blur wallpaper di belakangnya (Android 12+). Tingkat blur, ketebalan kaca, sudut, dan warna aksen bisa diatur.
- **Wallpaper**: 5 gradien bawaan, foto dari galeri, atau wallpaper sistem.
- **Grid**: kolom & baris Home, kolom laci, ukuran ikon, nama aplikasi on/off + ukuran teks, jumlah ikon dock, beberapa halaman Home.
- **Ikon**: icon pack format Nova/ADW, ganti ikon per aplikasi dari icon pack mana pun, ganti nama aplikasi, bentuk ikon (lingkaran, squircle, kotak bulat, tetes, dll).
- **Gestur**: swipe atas, swipe bawah, swipe bawah 2 jari, ketuk dua kali → laci, cari, notifikasi, quick settings, kunci layar, recents, pengaturan.
- Tahan ikon lalu geser untuk menyusun ulang; tahan tanpa geser untuk menu (edit, dock, sembunyikan, info, uninstall).
- Tahan area kosong Home untuk menu cepat.

## Install
Setiap push ke `main` otomatis di-build oleh GitHub Actions. Ambil APK dari tab **Releases** (bisa langsung dari HP), install, lalu tekan Home → pilih **Kaca Launcher** → *Selalu*.

Untuk ketuk-dua-kali kunci layar: Pengaturan launcher → Gestur → *Aktifkan di Aksesibilitas* → nyalakan "Kaca Launcher – Gestur".

## Build manual
Buka folder ini di Android Studio (JDK 17), lalu Run. Atau `./gradlew assembleDebug`.
