# Glens - Intelligent Screen Assistant

**Glens** adalah aplikasi asisten pintar untuk platform Android yang dirancang untuk meningkatkan produktivitas melalui interaksi konten layar secara *real-time*. Dengan menggabungkan teknologi OCR (Optical Character Recognition) dan layanan aksesibilitas, Glens memungkinkan pengguna untuk mengekstraksi, menerjemahkan, dan mengelola informasi yang muncul di layar dengan cepat.

---

## Fitur Utama

- **Live Screen OCR**: Mengekstraksi teks dari area mana pun di layar, termasuk teks dalam gambar atau aplikasi yang memproteksi fitur *copy-paste*.
- **Quick Action Overlay**: Antarmuka melayang (*overlay*) yang memberikan akses cepat ke fungsi salin, terjemah, dan cari tanpa meninggalkan aplikasi yang sedang dibuka.
- **Deep Integration**: Terintegrasi sebagai *Voice Interaction Service* (Asisten) dan *Accessibility Service* di sistem Android.
- **Offline Processing**: Menggunakan `ppocr-sdk` (PaddleOCR) untuk pemrosesan teks yang cepat dan aman langsung di perangkat.

## Arsitektur & Teknologi

Proyek ini terbagi menjadi dua modul utama:
1.  **`:app`**: Modul aplikasi utama yang menangani UI, layanan latar belakang (*Accessibility* & *Session Service*), dan logika interaksi pengguna.
2.  **`:ppocr-sdk`**: SDK pengenalan karakter optik berbasis PaddleOCR yang dioptimalkan untuk perangkat seluler.

**Teknologi yang digunakan:**
- **Language**: Kotlin/Java
- **OCR Engine**: PaddleOCR (PP-OCR)
- **Android APIs**: 
    - `AccessibilityService` (Membaca konten UI)
    - `VoiceInteractionService` (Integrasi asisten sistem)
    - `ForegroundService` (Sinkronisasi data)

## Cara Kerja

1.  **Deteksi Konten**: Glens menggunakan `AccessibilityService` untuk memahami struktur tampilan aplikasi lain.
2.  **Ekstraksi Visual**: Untuk teks yang tidak dapat dibaca sebagai metadata UI, aplikasi melakukan tangkapan layar parsial dan memprosesnya melalui `:ppocr-sdk`.
3.  **Interaksi**: Hasil ekstraksi ditampilkan melalui *Session Service* (Overlay) agar pengguna dapat melakukan tindakan lebih lanjut secara instan.

## Instalasi & Persiapan

1.  Clone repositori ini.
2.  Buka di **Android Studio**.
3.  Lakukan **Gradle Sync** untuk mengunduh dependensi.
4.  Jalankan aplikasi di perangkat Android (Min. API 21+ direkomendasikan).

> **Catatan:** Setelah instalasi, Anda harus mengaktifkan **Layanan Aksesibilitas Glens** di pengaturan Android agar aplikasi dapat berfungsi secara maksimal.

## Izin (Permissions)

Aplikasi ini memerlukan beberapa izin khusus:
- `BIND_ACCESSIBILITY_SERVICE`: Untuk membaca teks dan berinteraksi dengan layar.
- `BIND_VOICE_INTERACTION`: Untuk mendaftarkan diri sebagai asisten sistem.
- `FOREGROUND_SERVICE_DATA_SYNC`: Untuk memastikan layanan tetap berjalan saat memproses data.

---

## Lisensi

Proyek ini dilisensikan di bawah [LICENSE](LICENSE).

---
*Dibuat dengan sepenuh hati untuk meningkatkan produktivitas pengguna Android.*
