# DiStream Studio — Mobile Broadcast Platform (Android Native + Event Backend)

**DiStream Studio** (`id.distream.studio`) adalah platform live streaming mobile all-in-one setara **OBS Studio + TikTok Live Studio** di Android berbasis **Kotlin Native, Jetpack Compose Material 3, RootEncoder 2.8.1, OpenGL Compositor, Real-Time 48kHz PCM Audio DSP, dan Node.js Event Backend**.

---

## Keunggulan Utama Arsitektur DiStream v2.0

### 1. Identitas Brand & UI/UX Ergonomis Non-Cyber (`Theme.kt` & `StudioHomeScreen.kt`)
- **Identitas Brand `DiStream`**: Logo vektor geometris kustom (`ic_distream_logo.xml` & `DiStreamLogoMark` Canvas) yang bersih dan modern.
- **Dual Theme (`Studio Dark` & `Clean Light`)**: Menggunakan warna netral hangat ergonomis (`#121316` / `#191B20` / `#2563EB`) seperti software kreatif modern (Linear / Figma / Apple Pro Apps), bebas dari tampilan gelap hacker/cyber berlebihan, dan 100% bebas emoji.
- **Animasi Halus & Responsif**: Transisi scene, indikator tab, animasi widget Love, dan *alert toast* memakai kurva pegas halus.

### 2. SoundFX Audio Deck & 3-Bus DSP Mixer (`SoundFxSynthesizer.kt` + `ProBroadcastDspEffect.kt`)
- **8 Tombol Efek Suara Instan**: *Tepuk Tangan, Hype Horn, Victory Chime, Ba-Dum-Tss, Bass Drop, Koin Masuk, Riser Tegang,* dan *Sensor 1kHz*.
- **Injeksi Langsung ke Stream RTMP**: Sampel audio PCM 48kHz Stereo digabungkan langsung di level byte array (`CustomAudioEffect`) sehingga suara efek **pasti terdengar jernih oleh penonton TikTok** sekaligus terdengar di telinga streamer (`AudioTrack` monitor).
- **Noise Gate (`-42 dBFS`) + Voice Priority Auto-Ducking**: Menghilangkan desis kipas pendingin HP (*Funcooler*) saat diam, dan otomatis mengecilkan suara game ke `45%` saat streamer berbicara.
- **Live 1kHz Censor Beep**: Tombol sensor cepat untuk menutupi kata kasar di voice chat tim.

### 3. Preset Tap-Tap Love, Multi-Layer Overlay, & Teks Kustom Bebas (`DiStreamService.kt`)
- **Preset Interaksi Penonton TikTok**:
  - *Tap-Tap Love & Share* (animasi ikon hati halus + counter suka *real-time*).
  - *Target Like Live* (progres menuju target like).
  - *Top Supporter Badge* (otomatis menampilkan pemberi gift tertinggi sesi berjalan).
- **Teks Kustom Tak Terbatas**: Tambah, sembunyikan, hapus, dan geser posisi X/Y teks bebas di kanvas (misal ID Mabar, Pengumuman Turnamen, Jadwal Live).
- **Subathon Countdown Timer**: Hitung mundur otomatis yang menambah durasi setiap kali ada Gift atau Donasi Saweria masuk.
- **5-Scene Hot Switcher (`<20ms`)**: *Layar Game*, *Segera Mulai*, *Istirahat (BRB)*, *Kamera Penuh (OIS)*, dan *Layar Privasi*.

### 4. Keamanan Berlapis & Perlindungan Akun (`StreamKeyVault.kt` + `network_security_config.xml`)
- **Enkripsi Hardware `AES-256-GCM` (`AndroidKeyStore`)**: Stream Key RTMP/RTMPS dan PIN pengaman tidak pernah disimpan sebagai teks biasa di memori HP (`allowBackup="false"`).
- **Proteksi Jendela Rekam Layar (`FLAG_SECURE`)**: Mencegah halaman input Stream Key & PIN terekam oleh `MediaProjection` saat sedang live.
- **Kunci PIN Hentikan Siaran & Auto-Wipe Key**: Mencegah siaran mati akibat salah sentuh, serta opsi hapus otomatis Stream Key begitu live selesai.
- **Tombol Darurat Panik (Panic Blackout)**: 1 sentuhan langsung mengunci tampilan ke *Layar Privasi* sekaligus membisukan mikrofon.
- **Pengingat Mode Jangan Ganggu (DND Guard) & Filter Kata Terlarang**: Mencegah pop-up WhatsApp/OTP bocor ke layar, dan menyensor kata-kata berisiko pelanggaran TikTok LIVE pada komentar maupun pesan Saweria.
- **Kebijakan TLS Ketat (`network_security_config.xml`)**: Memaksa HTTPS/RTMPS terenkripsi untuk seluruh trafik internet publik.

### 5. Backend Event & Webhook Server (`distream-backend/server.js`)
- Membaca event publik TikTok LIVE (`chat`, `gift`, `like`, `follow`, *Top Supporter*) secara *read-only* tanpa login akun (0% risiko banned).
- Menyediakan endpoint `POST /webhook/donation/:roomToken` dengan validasi header `X-Stream-Token` untuk menerima notifikasi donasi dari **Saweria / SociaBuzz / BagiBagi** lengkap dengan sensor kata kasar otomatis.

---

## Cara Build & Jalankan

1. Ekstrak **`DiStreamStudioAndroid.zip`** lalu buka folder `DiStreamStudioAndroid` di **Android Studio (`File -> Open`)**.
2. Tunggu **Gradle Sync** selesai mengunduh dependensi Jetpack Compose, CameraX, OkHttp, dan `RootEncoder:2.8.1`.
3. Hubungkan perangkat Android fisik (**Android 10 / API 29+** ke atas) dan klik **Run**.
4. *(Opsional)* Untuk menjalankan server pembaca chat & webhook Saweria di PC/VPS:
   ```bash
   cd distream-backend
   npm install
   npm start
   ```
