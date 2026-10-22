# DiStream Studio v3.0

[![DiStream Studio CI/CD, QA & Release APK](https://github.com/xykal/distream-studio/actions/workflows/ci-build-qa.yml/badge.svg)](https://github.com/xykal/distream-studio/actions/workflows/ci-build-qa.yml)

**DiStream Studio** (`id.distream.studio`) adalah platform mobile live broadcasting kelas studio setara **OBS Studio + TikTok Live Studio** di Android berbasis **Kotlin Native, Jetpack Compose Material 3, RootEncoder 2.8.1, Real-Time 48kHz PCM Audio DSP, Multi-Layer OpenGL Compositor, dan Sistem Monetisasi Langganan SaaS (Harian, Mingguan, Bulanan)**.

---

## Skema Monetisasi SaaS & Fitur Eksklusif Pro

| Fitur & Kapabilitas | Starter Gratis (`Rp 0`) | Pass Harian (`Rp 5.000 / 24 Jam`) | Creator Mingguan (`Rp 19.000 / 7 Hari`) | Studio Pro VIP (`Rp 59.000 / 30 Hari`) |
| :--- | :---: | :---: | :---: | :---: |
| Resolusi & Codec Maksimal | 720p60 H.264 | **1080p60 + H.265 (HEVC)** | **1080p60 + H.265 (HEVC)** | **1080p60 + H.265 (HEVC)** |
| Hapus Watermark (Clean Feed) | Tidak | **Ya (100% Bersih)** | **Ya (100% Bersih)** | **Ya (100% Bersih)** |
| SoundFX Audio Deck 48kHz PCM | 4 Pad Dasar | **8 Pad Penuh** | **8 Pad Penuh** | **8 Pad Penuh** |
| Hot-Swap Scene & Subathon Timer | 3 Scene | **5 Scene Penuh + Subathon** | **5 Scene Penuh + Subathon** | **5 Scene Penuh + Subathon** |
| **Cloud Multi-Stream Simulcast Relay** | 1 Platform | 1 Platform | **3 Platform Serentak (Hemat Kuota 66%)** | **3 Platform Serentak (Hemat Kuota 66%)** |
| **Instant Replay Clip 30 Detik** | Tidak | Tidak | **Ya** | **Ya** |
| **Teks Kustom Bebas & Tema Royal Gold VIP** | Maks 1 Teks | Maks 3 Teks | **Tanpa Batas + Tema VIP** | **Tanpa Batas + Tema VIP** |
| **Smart TTS Pembaca Suara Donasi/Gift** | Tidak | Tidak | Tidak | **Otomatis Bahasa Indonesia** |
| **Cloud Anti-Disconnect Shield (60s)** | Tidak | Tidak | Tidak | **Server Standby Anti-Putus Room** |
| **Analitik Real-Time & Estimasi Rupiah** | Dasar | Dasar | **Aktif** | **Lengkap** |

---

## Arsitektur Teknis Utama

1. **UI/UX Creator Ergonomis (Dual Theme: *Studio Dark* & *Clean Light*)**
   - Identitas visual modern non-cyber dengan logo vektor geometris `DiStream`, transisi pegas halus, serta in-game floating control bar ber-drawer cepat SoundFX.
2. **Cryptographic SaaS Licensing & Pakasir QRIS Payment Gateway (`saasEngine.js` & `SaasSubscriptionManager.kt`)**
   - Integrasi langsung API **Pakasir QRIS** (`POST /api/transactioncreate/qris` & verifikasi server-ke-server `GET /api/transactiondetail`), dukungan kode voucher (`XYKAL50`, `HEMAT20`, `TRIALVIP`), bukti email otomatis via **Resend API**, serta token lisensi bertanda tangan **`HMAC-SHA256` (`DST1.<payload>.<signature>`)** yang terikat pada `Device ID` dan disimpan terenkripsi di Android Hardware Keystore.
3. **3-Bus Real-Time PCM Audio Mixer (`SoundFxSynthesizer.kt` & `ProBroadcastDspEffect.kt`)**
   - Menggabungkan vokal mikrofon (Noise Gate `-42 dBFS` + Auto Voice Ducking), audio internal game, efek SoundFX Deck, dan Sensor Kata Kasar `1kHz` langsung ke stream RTMP/RTMPS.
4. **Keamanan Berlapis (`StreamKeyVault.kt` & `network_security_config.xml`)**
   - Enkripsi hardware `AES-256-GCM`, proteksi layar aplikasi `FLAG_SECURE`, kunci PIN sebelum menghentikan live, Tombol Panik (*Privacy Slate + Mute*), auto-wipe Stream Key, pengingat mode DND, serta sensor kata terlarang otomatis pada komentar dan donasi Saweria.

---

## Menjalankan Backend & Automated QA Test Suite

```bash
cd distream-backend
cp ../.env.example .env
npm install
npm test
npm start
```

## Build APK Android Lokal atau via GitHub Actions

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Setiap push ke branch `main` otomatis menjalankan pipeline **GitHub Actions CI/CD** untuk mengaudit keamanan `.env`, menguji backend SaaS & WebSocket, menjalankan JUnit 4 Unit Tests & Android Lint, serta menandatangani dan mempublikasikan file **`DiStreamStudio-v3.0.0-release-signed.apk`** ke halaman **GitHub Releases**.
