# DiStream Studio v4.0 — Real Live Broadcast & Custom Creator Studio

**Aplikasi Mobile Live Streaming & Creator Broadcast Studio Android + Cloud SaaS Platform (`id.distream.studio` • `v4.0.0`)**

- **Live Production Cloud & Interactive Web Studio:** [https://distream-studio-cloud.vercel.app](https://distream-studio-cloud.vercel.app)
- **GitHub Pages Mirror:** [https://xykal.github.io/distream-studio/](https://xykal.github.io/distream-studio/)
- **Signed Android Release APK (`v4.0.0`):** Tersedia langsung pada tab [GitHub Releases (`v4.0.0`)](https://github.com/xykal/distream-studio/releases/tag/v4.0.0)

---

## Pembaruan Utama v4.0.0 (Real Production Edition)

1. **100% Antarmuka Studio Kustom Non-Material (`ui/DiStreamCustomUi.kt`)**:
   - Seluruh komponen UI dirancang murni dari nol menggunakan primitif kanvas, vektor geometris, fader taktil studio, dan animasi pegas (*spring physics*) tanpa widget stok Android / Material3 bawaan.
   - Mendukung 3 skema warna kreator bersih: **Studio Dark**, **Daylight Clean**, dan **Royal Gold VIP**.
2. **Kanvas Preview Live WYSIWYG Interaktif (`ui/LiveCanvasEditor.kt`)**:
   - **Feed Kamera Hardware Asli (`CameraX PreviewView`)** langsung menyala di panggung utama maupun kotak Facecam PiP (Lensa Depan/Belakang + Mirror).
   - **Ganti Orientasi Instan**: `9:16 TikTok Potret (1080x1920)`, `16:9 Lanskap Game/YouTube (1920x1080)`, `4:5 Feed Kreator (1080x1350)`, dan `1:1 Kotak Studio (1080x1080)` lengkap dengan panduan **Safe Zone TikTok LIVE** serta **Grid 3x3**.
   - **Drag, Resize & Edit Langsung di Layar**: Sentuh dan geser bebas posisi widget `Target Gift`, `Tap-Tap Love`, `Subathon Timer`, `Running Text`, maupun layer teks kustom langsung di atas kanvas preview, lalu koordinat persisnya ikut tersinkron ke encoder video RTMP/RTMPS. *(Catatan rilis: kotak `Facecam PiP` sudah bisa digeser di preview, namun belum ikut ter-composite ke encoder — masuk backlog Sprint 2.)*
3. **Mode Uji Kapsul Melayang Mandiri ("Tes Melayang" — `overlay/FloatingHudManager.kt`)**:
   - Uji dan jalankan kapsul kontrol melayang (`TYPE_APPLICATION_OVERLAY`) kapan saja di atas game atau aplikasi lain **tanpa harus memulai stream RTMP terlebih dahulu**.
   - Fitur kapsul melayang: *free-drag*, mengecil otomatis menjadi gelembung tepi layar (`DS LIVE`), pengatur transparansi (`96% / 78% / 58%`), ukuran Facecam PiP (`S / M / L`), ganti Scene cepat, pad SoundFX, tombol tahan sensor 1kHz, serta Layar Privasi Anti-OTP.
4. **Checkout Pembayaran Cloud Asli via Pakasir API v2 (`https://distream-studio-cloud.vercel.app`)**:
   - Terintegrasi langsung ke endpoint produksi **Pakasir API v2** (`/api/v2/create-transaction/{slug}/{order_id}` dan `/api/v2/transaction-status/{slug}/{txn_id}`), merender matriks **QR Code Scannable ISO/IEC 18004 (ZXing & PNG)** langsung di dalam aplikasi, auto-polling status lunas setiap 4.5 detik, penerbitan token lisensi kriptografis **HMAC-SHA256 (`DS1.*`)**, dan pengiriman email otomatis melalui **Resend API**.
5. **Mesin Audio DSP 48kHz & Monitoring Lokal Langsung**:
   - Pad SoundFX (`Tepuk Tangan`, `Hype Horn`, `Koin Masuk`, `Victory`, `Rimshot`, `Bass Drop`), nada sensor **1kHz Censor Beep**, serta pembaca donasi **Smart Indonesian TTS (`id-ID`)** dapat didengar langsung melalui speaker HP saat pratinjau maupun tercampur mulus ke jalur audio stream (`MixAudioSource`).

---

## Pembaruan v4.0.1 (Sprint 2 — Kompositor & Overlay Provider)

1. **Overlay provider donasi dimuat sebagai WebView sendiri (`graphics/SceneCompositor.kt`)**
   - Halaman overlay Saweria / Sociabuzz / BagiBagi sekarang dibuka lewat `loadUrl()` langsung,
     bukan `<iframe>` di dalam HTML base URL fiktif. Penyebab overlay tampil kosong sebelumnya
     adalah `X-Frame-Options` / CSP dari halaman provider.
   - Audio dari halaman overlay selalu dimatikan; suara alert tetap keluar lewat jalur DSP
     (`ProBroadcastDspEffect`) agar benar-benar terdengar penonton di RTMP.

2. **Facecam ikut terekam ke siaran — BETA (`facecamInEncoder`, default MATI)**
   - Aktifkan toggle **"Facecam Ikut Terekam ke Siaran (Beta)"** di tab Facecam & Event.
   - Kamera dipindah ke dalam pohon View kompositor (CameraX `PreviewView` mode COMPATIBLE),
     sehingga posisi/ukuran kotak facecam di kanvas benar-benar ikut ke encoder — sekaligus
     menghapus dua pipeline kamera yang dulu memicu `Camera in use`.
   - Butuh izin **"Gambar di atas aplikasi lain"** (jendela overlay offscreen diperlukan agar
     TextureView kamera menerima frame). Bila izin belum ada, aplikasi otomatis jatuh kembali ke
     `Camera2Source` dan scene IRL tidak pernah menjadi layar hitam.
   - Wajib diuji di perangkat fisik sebelum dijadikan default.

## Backend Cloud (v4.0.2 — pindah ke Cloudflare Workers)

Akun Vercel (`xykalnotkel's projects`) diblokir Vercel karena fair-use
(`402 DEPLOYMENT_DISABLED`), sehingga domain `distream-studio-cloud.vercel.app` tidak melayani
request apa pun. Backend dipindahkan ke Cloudflare Workers tanpa mengubah logika bisnis:

- **Endpoint produksi baru:** `https://distream-studio-cloud.dikanjut.workers.dev`
  (sudah menjadi default `saasBackendUrl` di aplikasi v4.0.2)
- Sumber kode tetap SATU: `distream-backend/server.js` + `saasEngine.js`.
  `worker-entry.js` hanya menjembatani Fetch API Workers ke handler gaya Node (req, res).
- Deploy ulang:
  ```bash
  cd distream-backend
  npm run build:worker          # esbuild -> dist/worker.js
  npx wrangler deploy           # butuh CLOUDFLARE_API_TOKEN
  ```
- Batasan saat ini: **relay WebSocket chat/gift TikTok belum ada di Worker** (butuh Durable
  Object). Semua endpoint HTTP berfungsi: health, katalog paket, checkout QRIS Pakasir,
  verifikasi lisensi, dan webhook donasi.

## Struktur Proyek

- `app/` — Aplikasi Android Native Kotlin (`compileSdk = 36`, `minSdk = 29`, `RootEncoder 2.8.1`, `CameraX`, `ZXing 3.5.3`, Custom Studio UI).
- `distream-backend/` — Server Cloud Produksi Node.js & Vercel Serverless Function (Pakasir API v2 QRIS, HMAC-SHA256 License Signer, Resend Email Delivery, TikTok LIVE + Saweria Real-Time Event Bus).
- `docs/index.html` — Aplikasi Web Studio Interaktif 3-Kolom (Webcam/Screen Capture Asli, Drag & Resize Canvas, Kapsul Melayang + Browser Picture-in-Picture, Rekaman Lokal WebM, & Live QRIS Pakasir v2).
- `.github/workflows/ci-build-qa.yml` — Pipeline QA Otomatis GitHub Actions (Backend Test Suite, JUnit 4 Comprehensive Suite, Android Lint, R8 Release Build, APK v2/v3 Signing & GitHub Release).
