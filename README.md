# Notes Keep Local 2.0

Aplikasi catatan Android yang menyimpan seluruh data secara lokal. Dibangun dengan Kotlin, Jetpack Compose, Room, dan Material 3.

## Fitur

- Catatan teks dan checklist
- Pin, arsip, pencarian, warna, dan beberapa lampiran gambar
- Tema terang, gelap, atau mengikuti sistem
- Impor Google Keep dari JSON atau ZIP Google Takeout
- Deteksi duplikat saat mengimpor
- Ekspor seluruh catatan dan gambar ke backup ZIP
- Migrasi database yang mempertahankan data versi sebelumnya
- Penyimpanan lokal tanpa Android cloud backup

## Menjalankan proyek

### Prasyarat

- Android Studio versi terbaru yang kompatibel dengan Android Gradle Plugin 9.1.1
- Android SDK 36
- JDK 17 (JBR bawaan Android Studio dapat digunakan)

### Langkah

1. Buka Android Studio.
2. Pilih **Open**, lalu pilih direktori proyek ini.
3. Tunggu Gradle sync selesai.
4. Jalankan konfigurasi `app` pada emulator atau perangkat Android minimal API 24.

Dari terminal Windows, unit test dapat dijalankan dengan:

```powershell
.\gradlew.bat testDebugUnitTest
```

Aplikasi tidak membutuhkan Gemini API key, akun Google, atau izin internet.

## Backup dan pemulihan

Buka **Setelan → Backup Lokal → Ekspor Backup ZIP** untuk menyimpan seluruh catatan beserta gambar. Berkas ZIP tersebut dapat dipulihkan melalui tombol **Pilih ZIP / JSON**.

Untuk mengimpor Google Keep, unduh data Keep dari [Google Takeout](https://takeout.google.com/) dan pilih berkas ZIP hasil ekspor tanpa perlu mengekstraknya.

## Privasi

Database dan lampiran disimpan di penyimpanan internal aplikasi. Android cloud backup dinonaktifkan. Data hanya keluar dari aplikasi ketika pengguna memilih lokasi ekspor backup sendiri.
