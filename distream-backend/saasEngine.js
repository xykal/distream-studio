/**
 * DiStream Studio v4.0 — Real Pakasir API v2 + Stateless Cloud Licensing & Resend Engine
 *
 * Endpoint Gateway Resmi Pakasir API v2 (https://pakasir.com/p/create-transaction):
 * - POST https://app.pakasir.com/api/v2/create-transaction/{slug}/{order_id}  (Header: X-Api-Key)
 * - GET  https://app.pakasir.com/api/v2/transaction-status/{slug}/{txn_id}    (Header: X-Api-Key)
 * - POST https://app.pakasir.com/api/v2/payment-simulation/{slug}/{txn_id}    (Header: X-Api-Key, khusus proyek Sandbox Pakasir)
 * - Hosted Checkout: https://app.pakasir.com/pay-v2/{txn_id}?qris_only=1
 *
 * Mendukung mode Stateful (VPS / Docker WebSocket Server) sekaligus Stateless Serverless (Vercel Cloud)
 * melalui amplop order bertanda tangan HMAC-SHA256 (`DSO1.<payload>.<hmac>`) dan token lisensi
 * terikat perangkat (`DST1.<payload>.<hmac>`).
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const QRCode = require("qrcode");

function loadDotEnv() {
  // Di lingkungan tanpa filesystem (Cloudflare Workers) fungsi ini harus no-op:
  // konfigurasi datang dari binding environment, bukan dari file .env.
  if (typeof __dirname === "undefined" || typeof fs.existsSync !== "function") return;
  let candidates;
  try {
    candidates = [
      path.resolve(__dirname, ".env"),
      path.resolve(__dirname, "..", ".env")
    ];
  } catch {
    return;
  }
  for (const filePath of candidates) {
    let exists = false;
    try {
      exists = fs.existsSync(filePath);
    } catch {
      return;
    }
    if (!exists) continue;
    let content;
    try {
      content = fs.readFileSync(filePath, "utf8");
    } catch {
      continue;
    }
    for (const rawLine of content.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) continue;
      const eqIdx = line.indexOf("=");
      if (eqIdx <= 0) continue;
      const key = line.slice(0, eqIdx).trim();
      const val = line.slice(eqIdx + 1).trim().replace(/^['"]|['"]$/g, "");
      if (key && process.env[key] === undefined) {
        process.env[key] = val;
      }
    }
  }
}

loadDotEnv();

const SAAS_PLANS = {
  FREE: {
    id: "FREE",
    name: "Starter Gratis",
    badge: "Gratis Selamanya",
    priceIdr: 0,
    durationHours: 0,
    periodLabel: "Selamanya",
    summary: "Streaming dasar 720p 60fps untuk kreator pemula",
    entitlements: {
      removeWatermark: false,
      allow1080p60: false,
      allowHevcCodec: false,
      unlockedAllSoundFx: false,
      unlockedAllScenes: false,
      subathonTimer: false,
      multiStreamSimulcast: false,
      maxSimulcastTargets: 1,
      instantReplayBuffer: false,
      maxCustomTextLayers: 1,
      premiumOverlayThemes: false,
      smartTtsReadout: false,
      cloudFailoverShield: false,
      proAnalyticsExport: false
    }
  },
  DAILY: {
    id: "DAILY",
    name: "Pass Harian",
    badge: "Pas Buat Mabar / Turnamen",
    priceIdr: 5000,
    durationHours: 24,
    periodLabel: "24 Jam",
    summary: "Buka kualitas penuh 1080p60 HEVC, tanpa watermark, & 8 pad SoundFX penuh selama 24 jam",
    entitlements: {
      removeWatermark: true,
      allow1080p60: true,
      allowHevcCodec: true,
      unlockedAllSoundFx: true,
      unlockedAllScenes: true,
      subathonTimer: true,
      multiStreamSimulcast: false,
      maxSimulcastTargets: 1,
      instantReplayBuffer: false,
      maxCustomTextLayers: 3,
      premiumOverlayThemes: true,
      smartTtsReadout: false,
      cloudFailoverShield: false,
      proAnalyticsExport: false
    }
  },
  WEEKLY: {
    id: "WEEKLY",
    name: "Creator Mingguan",
    badge: "Hemat 46%",
    priceIdr: 19000,
    durationHours: 24 * 7,
    periodLabel: "7 Hari",
    summary: "Multi-Stream Simulcast (Live ke TikTok + YouTube sekaligus hemat baterai), Replay 30s, & Teks Tanpa Batas",
    entitlements: {
      removeWatermark: true,
      allow1080p60: true,
      allowHevcCodec: true,
      unlockedAllSoundFx: true,
      unlockedAllScenes: true,
      subathonTimer: true,
      multiStreamSimulcast: true,
      maxSimulcastTargets: 3,
      instantReplayBuffer: true,
      maxCustomTextLayers: 99,
      premiumOverlayThemes: true,
      smartTtsReadout: false,
      cloudFailoverShield: false,
      proAnalyticsExport: true
    }
  },
  MONTHLY: {
    id: "MONTHLY",
    name: "Studio Pro Bulanan",
    badge: "Paling Populer - VIP",
    priceIdr: 59000,
    durationHours: 24 * 30,
    periodLabel: "30 Hari",
    summary: "Paket terlengkap: Suara Pintar TTS Pembaca Donasi/Gift, Cloud Anti-Disconnect 60s, Simulcast 3 Platform, & Analitik IDR",
    entitlements: {
      removeWatermark: true,
      allow1080p60: true,
      allowHevcCodec: true,
      unlockedAllSoundFx: true,
      unlockedAllScenes: true,
      subathonTimer: true,
      multiStreamSimulcast: true,
      maxSimulcastTargets: 3,
      instantReplayBuffer: true,
      maxCustomTextLayers: 99,
      premiumOverlayThemes: true,
      smartTtsReadout: true,
      cloudFailoverShield: true,
      proAnalyticsExport: true
    }
  }
};

const PROMO_CODES = {
  XYKAL50: { discountPercent: 50, description: "Diskon Komunitas Kreator 50%" },
  HEMAT20: { discountPercent: 20, description: "Potongan Langganan 20%" },
  TRIALVIP: { discountPercent: 100, forcedPlan: "MONTHLY", forcedHours: 24, oncePerDevice: true, description: "Coba Gratis VIP Studio Pro 24 Jam" }
};

class SaasBillingEngine {
  constructor(options = {}) {
    const resolvedSecret =
      options.signingSecret ||
      process.env.DISTREAM_LICENSE_SECRET ||
      "";
    const allowInsecure =
      options.allowInsecureDefaultSecret === true ||
      process.env.DISTREAM_ALLOW_INSECURE_LICENSE_SECRET === "true";

    // JANGAN pernah menandatangani lisensi dengan secret bawaan yang ikut terkirim di APK.
    // Secret default lama ("distream-studio-default-hmac-key-2026") ada di dalam binary klien,
    // jadi siapa pun bisa mencetak token MONTHLY valid selamanya bila server memakainya.
    if (!resolvedSecret && !allowInsecure) {
      throw new Error(
        "DISTREAM_LICENSE_SECRET wajib di-set (minimal 32 karakter acak). " +
          "Setel DISTREAM_ALLOW_INSECURE_LICENSE_SECRET=true hanya untuk unit test lokal."
      );
    }
    if (resolvedSecret.length > 0 && resolvedSecret.length < 32 && !allowInsecure) {
      throw new Error("DISTREAM_LICENSE_SECRET terlalu pendek (minimal 32 karakter).");
    }
    this.signingSecret = resolvedSecret || "insecure-test-only-secret";
    this.pakasirProject = options.pakasirProject ?? process.env.PAKASIR_PROJECT ?? "";
    this.pakasirApiKey = options.pakasirApiKey ?? process.env.PAKASIR_API_KEY ?? "";
    this.resendApiKey = options.resendApiKey ?? process.env.RESEND_EMAIL_API_KEY ?? "";
    this.sandboxMode = options.sandboxMode ?? (process.env.PAKASIR_SANDBOX !== "false");
    this.enableExternalGatewayCalls =
      options.enableExternalGatewayCalls ?? Boolean(this.pakasirProject && this.pakasirApiKey);

    this.orders = new Map();
    this.usedDeviceTrials = new Set();
    this.simulcastSessions = new Map();
    this.failoverShields = new Map();
  }

  getPlansCatalog() {
    return Object.values(SAAS_PLANS);
  }

  signOrderEnvelope(orderObj) {
    const slim = {
      orderId: orderObj.orderId,
      txnId: orderObj.txnId || "",
      planId: orderObj.planId,
      planName: orderObj.planName,
      deviceId: orderObj.deviceId,
      email: orderObj.email || "",
      baseAmount: orderObj.baseAmount,
      discountPercent: orderObj.discountPercent,
      amount: orderObj.amount,
      fee: orderObj.fee,
      totalPayment: orderObj.totalPayment,
      durationHours: orderObj.durationHours,
      qrisString: orderObj.qrisString || "",
      paymentUrl: orderObj.paymentUrl || "",
      isSandbox: Boolean(orderObj.isSandbox),
      gatewayLive: Boolean(orderObj.gatewayLive),
      createdAt: orderObj.createdAt
    };
    const encoded = Buffer.from(JSON.stringify(slim), "utf8").toString("base64url");
    const sig = crypto
      .createHmac("sha256", this.signingSecret)
      .update(`DSO1.${encoded}`)
      .digest("base64url");
    return `DSO1.${encoded}.${sig}`;
  }

  verifyOrderEnvelope(orderToken) {
    if (!orderToken || typeof orderToken !== "string") return null;
    const parts = orderToken.trim().split(".");
    if (parts.length !== 3 || parts[0] !== "DSO1") return null;
    const [, encoded, providedSig] = parts;
    const expectedSig = crypto
      .createHmac("sha256", this.signingSecret)
      .update(`DSO1.${encoded}`)
      .digest("base64url");
    const bufA = Buffer.from(providedSig, "utf8");
    const bufB = Buffer.from(expectedSig, "utf8");
    if (bufA.length !== bufB.length || !crypto.timingSafeEqual(bufA, bufB)) return null;
    try {
      return JSON.parse(Buffer.from(encoded, "base64url").toString("utf8"));
    } catch {
      return null;
    }
  }

  resolveOrder(orderId, orderToken = null) {
    if (orderId && this.orders.has(orderId)) {
      return this.orders.get(orderId);
    }
    const decoded = this.verifyOrderEnvelope(orderToken);
    if (decoded && (!orderId || decoded.orderId === orderId)) {
      const restored = {
        ...decoded,
        paymentMethod: "QRIS",
        status: "pending",
        licenseToken: null,
        expiresAt: null,
        completedAt: null,
        orderToken
      };
      this.orders.set(restored.orderId, restored);
      return restored;
    }
    return null;
  }

  signLicenseToken(payloadObj) {
    const encodedPayload = Buffer.from(JSON.stringify(payloadObj), "utf8").toString("base64url");
    const sig = crypto
      .createHmac("sha256", this.signingSecret)
      .update(`DST1.${encodedPayload}`)
      .digest("base64url");
    return `DST1.${encodedPayload}.${sig}`;
  }

  verifyLicenseToken(token, expectedDeviceId = null, nowEpochMs = Date.now()) {
    if (!token || typeof token !== "string") {
      return { valid: false, code: "MISSING_TOKEN", plan: SAAS_PLANS.FREE };
    }
    const parts = token.trim().split(".");
    if (parts.length !== 3 || parts[0] !== "DST1") {
      return { valid: false, code: "MALFORMED_TOKEN", plan: SAAS_PLANS.FREE };
    }
    const [, encodedPayload, providedSig] = parts;
    const expectedSig = crypto
      .createHmac("sha256", this.signingSecret)
      .update(`DST1.${encodedPayload}`)
      .digest("base64url");

    const bufA = Buffer.from(providedSig, "utf8");
    const bufB = Buffer.from(expectedSig, "utf8");
    if (bufA.length !== bufB.length || !crypto.timingSafeEqual(bufA, bufB)) {
      return { valid: false, code: "INVALID_SIGNATURE", plan: SAAS_PLANS.FREE };
    }

    let decoded;
    try {
      decoded = JSON.parse(Buffer.from(encodedPayload, "base64url").toString("utf8"));
    } catch {
      return { valid: false, code: "CORRUPT_PAYLOAD", plan: SAAS_PLANS.FREE };
    }

    if (expectedDeviceId && decoded.deviceId && decoded.deviceId !== expectedDeviceId) {
      return { valid: false, code: "DEVICE_MISMATCH", plan: SAAS_PLANS.FREE };
    }

    if (typeof decoded.expiresAt !== "number" || nowEpochMs >= decoded.expiresAt) {
      return { valid: false, code: "LICENSE_EXPIRED", plan: SAAS_PLANS.FREE, payload: decoded };
    }

    const plan = SAAS_PLANS[decoded.tier] || SAAS_PLANS.FREE;
    return {
      valid: true,
      code: "ACTIVE",
      tier: plan.id,
      plan,
      expiresAt: decoded.expiresAt,
      remainingSeconds: Math.max(0, Math.floor((decoded.expiresAt - nowEpochMs) / 1000)),
      payload: decoded
    };
  }

  issueLicense(planId, deviceId, durationHoursOverride = null, orderId = "DIRECT") {
    const plan = SAAS_PLANS[planId];
    if (!plan || plan.id === "FREE") {
      throw new Error("Invalid paid plan tier");
    }
    const now = Date.now();
    const hours = durationHoursOverride || plan.durationHours;
    const expiresAt = now + hours * 3600 * 1000;
    const payload = {
      sub: orderId,
      tier: plan.id,
      deviceId: (deviceId || "android-default").trim(),
      issuedAt: now,
      expiresAt,
      entitlements: plan.entitlements
    };
    const licenseToken = this.signLicenseToken(payload);
    return {
      licenseToken,
      tier: plan.id,
      planName: plan.name,
      issuedAt: now,
      expiresAt,
      entitlements: plan.entitlements
    };
  }

  async createCheckoutOrder({ planId, deviceId, promoCode = "", email = "" }) {
    const cleanDevice = (deviceId || "android-default").trim();
    const normalizedPromo = (promoCode || "").trim().toUpperCase();
    const promo = PROMO_CODES[normalizedPromo] || null;

    const targetPlanId = promo?.forcedPlan || planId;
    const plan = SAAS_PLANS[targetPlanId];
    if (!plan || plan.id === "FREE") {
      const err = new Error("Pilih paket langganan berbayar (DAILY, WEEKLY, atau MONTHLY)");
      err.statusCode = 400;
      throw err;
    }

    if (promo?.oncePerDevice && this.usedDeviceTrials.has(cleanDevice)) {
      const err = new Error("Trial gratis sudah pernah digunakan pada perangkat ini");
      err.statusCode = 409;
      throw err;
    }

    const discountPct = promo ? promo.discountPercent : 0;
    const finalAmount = Math.max(0, Math.round(plan.priceIdr * (1 - discountPct / 100)));
    const orderId = `DST${plan.id.slice(0, 3)}${Date.now()}${crypto.randomBytes(2).toString("hex").toUpperCase()}`;
    const durationHours = promo?.forcedHours || plan.durationHours;

    if (finalAmount === 0) {
      if (promo?.oncePerDevice) this.usedDeviceTrials.add(cleanDevice);
      const issued = this.issueLicense(plan.id, cleanDevice, durationHours, orderId);
      const freeOrder = {
        orderId,
        txnId: "",
        planId: plan.id,
        planName: plan.name,
        deviceId: cleanDevice,
        email,
        baseAmount: plan.priceIdr,
        discountPercent: discountPct,
        amount: 0,
        fee: 0,
        totalPayment: 0,
        paymentMethod: "PROMO_FREE_PASS",
        status: "completed",
        durationHours,
        qrisString: "",
        paymentUrl: "",
        isSandbox: false,
        gatewayLive: true,
        licenseToken: issued.licenseToken,
        expiresAt: issued.expiresAt,
        createdAt: Date.now(),
        completedAt: Date.now()
      };
      freeOrder.orderToken = this.signOrderEnvelope(freeOrder);
      this.orders.set(orderId, freeOrder);
      return freeOrder;
    }

    const projectSlug = this.pakasirProject || "distream";
    const estimatedFee = Math.round(finalAmount * 0.007) + 310;
    let txnId = "";
    let qrisString = `00020101021226660014ID.CO.DISTREAM.WWW011893600914${orderId.slice(-10)}520458155303360540${finalAmount}5802ID5915DISTREAM STUDIO6007JAKARTA6304A1B2`;
    let fee = estimatedFee;
    let totalPayment = finalAmount + estimatedFee;
    let hostedPaymentUrl = `https://app.pakasir.com/pay/${encodeURIComponent(projectSlug)}/${finalAmount}?order_id=${encodeURIComponent(orderId)}&qris_only=1`;
    let isSandbox = Boolean(this.sandboxMode);
    let gatewayLive = false;

    // Panggilan REAL ke Pakasir API v2 (https://app.pakasir.com/api/v2/create-transaction/{slug}/{order_id})
    if (this.enableExternalGatewayCalls && this.pakasirProject && this.pakasirApiKey) {
      try {
        const createUrl = `https://app.pakasir.com/api/v2/create-transaction/${encodeURIComponent(this.pakasirProject)}/${encodeURIComponent(orderId)}`;
        const resp = await fetch(createUrl, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Api-Key": this.pakasirApiKey,
            "User-Agent": "DiStreamStudio/4.0"
          },
          body: JSON.stringify({
            method: "qris",
            amount: finalAmount
          })
        });
        if (resp.ok) {
          const data = await resp.json();
          if (data && data.txn_id) {
            txnId = String(data.txn_id);
            fee = Number(data.fee ?? fee);
            totalPayment = Number(data.total_payment ?? totalPayment);
            hostedPaymentUrl = `https://app.pakasir.com/pay-v2/${encodeURIComponent(txnId)}?qris_only=1`;
            qrisString =
              data.qr_string && !String(data.qr_string).startsWith("qris-placeholder")
                ? String(data.qr_string)
                : hostedPaymentUrl;
            isSandbox = Boolean(data.is_sandbox);
            gatewayLive = true;
          }
        }
      } catch {
        // Fallback aman bila jaringan eksternal sedang gangguan
      }
    }

    let qrSvgDataUrl = "";
    try {
      qrSvgDataUrl = await QRCode.toDataURL(qrisString || hostedPaymentUrl, {
        errorCorrectionLevel: "M",
        margin: 1,
        width: 300
      });
    } catch {
      qrSvgDataUrl = "";
    }

    const order = {
      orderId,
      txnId,
      planId: plan.id,
      planName: plan.name,
      deviceId: cleanDevice,
      email,
      baseAmount: plan.priceIdr,
      discountPercent: discountPct,
      amount: finalAmount,
      fee,
      totalPayment,
      paymentMethod: "QRIS",
      status: "pending",
      durationHours,
      qrisString,
      qrSvgDataUrl,
      paymentUrl: hostedPaymentUrl,
      isSandbox,
      gatewayLive,
      licenseToken: null,
      expiresAt: null,
      createdAt: Date.now(),
      completedAt: null
    };
    order.orderToken = this.signOrderEnvelope(order);
    this.orders.set(orderId, order);
    return order;
  }

  async syncLiveStatusWithPakasir(orderId, orderToken = null) {
    const order = this.resolveOrder(orderId, orderToken);
    if (!order) {
      const err = new Error("Order tidak ditemukan");
      err.statusCode = 404;
      throw err;
    }
    if (order.status === "completed" && order.licenseToken) {
      return order;
    }
    if (this.enableExternalGatewayCalls && this.pakasirProject && this.pakasirApiKey && order.txnId) {
      try {
        const statusUrl = `https://app.pakasir.com/api/v2/transaction-status/${encodeURIComponent(this.pakasirProject)}/${encodeURIComponent(order.txnId)}`;
        const resp = await fetch(statusUrl, {
          headers: {
            "X-Api-Key": this.pakasirApiKey,
            "User-Agent": "DiStreamStudio/4.0"
          }
        });
        if (resp.ok) {
          const data = await resp.json();
          if (data?.status === "completed" && Number(data.amount) === order.amount) {
            return await this.settleOrder(order.orderId, {
              verifyWithPakasir: false,
              orderToken
            });
          }
          if (data?.status === "canceled") {
            order.status = "canceled";
          }
        }
      } catch {
        // Biarkan tetap pending hingga poll berikutnya
      }
    }
    return order;
  }

  async settleOrder(orderId, { verifyWithPakasir = false, orderToken = null, triggerPakasirSandboxApi = false } = {}) {
    const order = this.resolveOrder(orderId, orderToken);
    if (!order) {
      const err = new Error("Order tidak ditemukan");
      err.statusCode = 404;
      throw err;
    }

    if (order.status === "completed" && order.licenseToken) {
      return order;
    }

    // Jika proyek Pakasir milik merchant berstatus Sandbox di app.pakasir.com, picu API resmi Pakasir v2 payment-simulation
    if (
      triggerPakasirSandboxApi &&
      this.enableExternalGatewayCalls &&
      this.pakasirProject &&
      this.pakasirApiKey &&
      order.txnId &&
      order.isSandbox
    ) {
      const simUrl = `https://app.pakasir.com/api/v2/payment-simulation/${encodeURIComponent(this.pakasirProject)}/${encodeURIComponent(order.txnId)}`;
      await fetch(simUrl, {
        method: "POST",
        headers: {
          "X-Api-Key": this.pakasirApiKey,
          "User-Agent": "DiStreamStudio/4.0"
        }
      });
    }

    if (verifyWithPakasir && this.pakasirProject && this.pakasirApiKey && order.txnId) {
      const statusUrl = `https://app.pakasir.com/api/v2/transaction-status/${encodeURIComponent(this.pakasirProject)}/${encodeURIComponent(order.txnId)}`;
      const reqHeaders = {
        "X-Api-Key": this.pakasirApiKey,
        "User-Agent": "DiStreamStudio/4.0"
      };
      let checkResp = await fetch(statusUrl, { headers: reqHeaders });
      if (checkResp.status === 429) {
        await new Promise((r) => setTimeout(r, 4150));
        checkResp = await fetch(statusUrl, { headers: reqHeaders });
      }
      const checkJson = await checkResp.json();
      if (!checkJson || checkJson.status !== "completed" || Number(checkJson.amount) !== order.amount) {
        const err = new Error("Pembayaran belum terkonfirmasi lunas di sistem Pakasir");
        err.statusCode = 402;
        throw err;
      }
    } else if (verifyWithPakasir && !this.sandboxMode) {
      const err = new Error("Konfigurasi verifikasi gateway belum aktif");
      err.statusCode = 503;
      throw err;
    }

    const issued = this.issueLicense(order.planId, order.deviceId, order.durationHours, order.orderId);
    order.status = "completed";
    order.licenseToken = issued.licenseToken;
    order.expiresAt = issued.expiresAt;
    order.completedAt = Date.now();

    if (this.enableExternalGatewayCalls && this.resendApiKey && order.email) {
      try {
        await fetch("https://api.resend.com/emails", {
          method: "POST",
          headers: {
            Authorization: `Bearer ${this.resendApiKey}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            from: process.env.DISTREAM_FROM_EMAIL || "DiStream Studio <onboarding@resend.dev>",
            to: [order.email],
            subject: `Lisensi Aktif DiStream Studio — ${order.planName}`,
            text: `Terima kasih! Paket ${order.planName} (${order.orderId}) sudah aktif.\nToken Lisensi Anda:\n${order.licenseToken}`
          })
        });
      } catch {
        // Pengiriman email gagal tidak membatalkan lisensi yang sudah dibayar
      }
    }

    return order;
  }

  configureSimulcastRelay(licenseToken, deviceId, targets) {
    const check = this.verifyLicenseToken(licenseToken, deviceId);
    if (!check.valid || !check.plan.entitlements.multiStreamSimulcast) {
      const err = new Error("Fitur Multi-Stream Simulcast memerlukan paket Mingguan atau Bulanan VIP");
      err.statusCode = 403;
      throw err;
    }
    if (!Array.isArray(targets) || targets.length === 0 || targets.length > check.plan.entitlements.maxSimulcastTargets) {
      const err = new Error("Jumlah target simulcast harus 1 sampai 3 platform RTMP/RTMPS");
      err.statusCode = 400;
      throw err;
    }
    // Relay ingest RTMP ini belum punya server produksi. Versi sebelumnya mengembalikan URL
    // `rtmps://relay.distream.studio/...` yang tidak pernah ada, sehingga aplikasi yang memakainya
    // justru gagal connect ke semua platform. Multistream dilakukan di sisi penyedia relay
    // (Restream/SociaBuzz) dengan satu endpoint ingest.
    const err = new Error(
      "Cloud relay ingest belum tersedia. Gunakan satu endpoint ingest (mis. Restream) untuk multistream."
    );
    err.statusCode = 501;
    throw err;
  }

  armCloudFailoverShield(licenseToken, deviceId, fallbackHeadline) {
    const check = this.verifyLicenseToken(licenseToken, deviceId);
    if (!check.valid || !check.plan.entitlements.cloudFailoverShield) {
      const err = new Error("Cloud Anti-Disconnect Shield eksklusif untuk pelanggan Studio Pro Bulanan");
      err.statusCode = 403;
      throw err;
    }
    const state = {
      deviceId,
      standbyKeepAliveSeconds: 60,
      fallbackHeadline: fallbackHeadline || "Sinyal sedang disambungkan kembali, jangan keluar room!",
      armedAt: Date.now(),
      status: "ARMED"
    };
    this.failoverShields.set(deviceId, state);
    return state;
  }
}

module.exports = {
  SAAS_PLANS,
  PROMO_CODES,
  SaasBillingEngine
};
