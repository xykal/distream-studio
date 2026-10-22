/**
 * DiStream Studio SaaS Subscription, Cryptographic Licensing & Payment Engine
 *
 * Fitur:
 * - Katalog Langganan Harian (Rp 5.000), Mingguan (Rp 19.000), Bulanan VIP (Rp 59.000) & Starter Gratis
 * - Integrasi Payment Gateway Pakasir QRIS (POST /api/transactioncreate/qris & GET /api/transactiondetail)
 * - Pengiriman Email Bukti Aktivasi & Kode Lisensi via Resend API
 * - Signed License Token HMAC-SHA256 (`DST1.<payload>.<signature>`) terikat Device ID + Anti-Tamper
 * - Cloud Multi-Stream Simulcast Relay & Anti-Disconnect Failover Shield Gatekeeping
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

function loadDotEnv() {
  const candidates = [
    path.resolve(__dirname, ".env"),
    path.resolve(__dirname, "..", ".env")
  ];
  for (const filePath of candidates) {
    if (!fs.existsSync(filePath)) continue;
    const content = fs.readFileSync(filePath, "utf8");
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
    this.signingSecret =
      options.signingSecret ||
      process.env.DISTREAM_LICENSE_SECRET ||
      "distream-studio-default-hmac-key-2026";
    this.pakasirProject = options.pakasirProject ?? process.env.PAKASIR_PROJECT ?? "";
    this.pakasirApiKey = options.pakasirApiKey ?? process.env.PAKASIR_API_KEY ?? "";
    this.resendApiKey = options.resendApiKey ?? process.env.RESEND_EMAIL_API_KEY ?? "";
    this.sandboxMode = options.sandboxMode ?? (process.env.PAKASIR_SANDBOX !== "false");
    this.enableExternalGatewayCalls = options.enableExternalGatewayCalls ?? false;

    this.orders = new Map();
    this.usedDeviceTrials = new Set();
    this.simulcastSessions = new Map();
    this.failoverShields = new Map();
  }

  getPlansCatalog() {
    return Object.values(SAAS_PLANS);
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
    const orderId = `DST-${plan.id}-${Date.now()}-${crypto.randomBytes(3).toString("hex").toUpperCase()}`;
    const durationHours = promo?.forcedHours || plan.durationHours;

    // Jika promo 100% (Trial Gratis), langsung terbitkan lisensi aktif tanpa menunggu bayar
    if (finalAmount === 0) {
      if (promo?.oncePerDevice) this.usedDeviceTrials.add(cleanDevice);
      const issued = this.issueLicense(plan.id, cleanDevice, durationHours, orderId);
      const freeOrder = {
        orderId,
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
        licenseToken: issued.licenseToken,
        expiresAt: issued.expiresAt,
        createdAt: Date.now(),
        completedAt: Date.now()
      };
      this.orders.set(orderId, freeOrder);
      return freeOrder;
    }

    const projectSlug = this.pakasirProject || "distream";
    const hostedPaymentUrl = `https://app.pakasir.com/pay/${encodeURIComponent(projectSlug)}/${finalAmount}?order_id=${encodeURIComponent(orderId)}&qris_only=1`;
    const estimatedFee = Math.round(finalAmount * 0.007) + 310;

    let qrisString = `00020101021226660014ID.CO.DISTREAM.WWW011893600914${orderId.slice(-10)}520458155303360540${finalAmount}5802ID5915DISTREAM STUDIO6007JAKARTA6304A1B2`;
    let fee = estimatedFee;
    let totalPayment = finalAmount + estimatedFee;

    if (this.enableExternalGatewayCalls && this.pakasirProject && this.pakasirApiKey) {
      try {
        const resp = await fetch("https://app.pakasir.com/api/transactioncreate/qris", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            project: this.pakasirProject,
            order_id: orderId,
            amount: finalAmount,
            api_key: this.pakasirApiKey
          })
        });
        if (resp.ok) {
          const data = await resp.json();
          const p = data?.payment;
          if (p) {
            qrisString = p.payment_number || qrisString;
            fee = Number(p.fee ?? fee);
            totalPayment = Number(p.total_payment ?? totalPayment);
          }
        }
      } catch {
        // Fallback aman ke hosted payment URL Pakasir
      }
    }

    const order = {
      orderId,
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
      paymentUrl: hostedPaymentUrl,
      licenseToken: null,
      expiresAt: null,
      createdAt: Date.now(),
      completedAt: null
    };
    this.orders.set(orderId, order);
    return order;
  }

  async settleOrder(orderId, { verifyWithPakasir = false } = {}) {
    const order = this.orders.get(orderId);
    if (!order) {
      const err = new Error("Order tidak ditemukan");
      err.statusCode = 404;
      throw err;
    }

    // Idempotent settlement guard: jangan pernah menerbitkan dua kali
    if (order.status === "completed" && order.licenseToken) {
      return order;
    }

    if (verifyWithPakasir && !this.sandboxMode) {
      if (!this.pakasirProject || !this.pakasirApiKey) {
        const err = new Error("Konfigurasi verifikasi gateway belum aktif");
        err.statusCode = 503;
        throw err;
      }
      const detailUrl =
        `https://app.pakasir.com/api/transactiondetail?project=${encodeURIComponent(this.pakasirProject)}` +
        `&order_id=${encodeURIComponent(order.orderId)}&amount=${order.amount}` +
        `&api_key=${encodeURIComponent(this.pakasirApiKey)}`;
      const checkResp = await fetch(detailUrl);
      const checkJson = await checkResp.json();
      const tx = checkJson?.transaction;
      if (!tx || tx.status !== "completed" || Number(tx.amount) !== order.amount) {
        const err = new Error("Pembayaran belum terkonfirmasi lunas oleh Pakasir");
        err.statusCode = 402;
        throw err;
      }
    }

    const issued = this.issueLicense(order.planId, order.deviceId, order.durationHours, order.orderId);
    order.status = "completed";
    order.licenseToken = issued.licenseToken;
    order.expiresAt = issued.expiresAt;
    order.completedAt = Date.now();

    if (this.enableExternalGatewayCalls && this.resendApiKey && order.email) {
      fetch("https://api.resend.com/emails", {
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
      }).catch(() => {});
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
    const relayKey = `simul_${crypto.randomBytes(8).toString("hex")}`;
    const session = {
      relayIngestUrl: `rtmps://relay.distream.studio/live/${relayKey}`,
      targetCount: targets.length,
      platforms: targets.map((t) => t.platform || "Custom RTMP"),
      bandwidthSavedPercent: Math.round(((targets.length - 1) / targets.length) * 100),
      active: true
    };
    this.simulcastSessions.set(relayKey, session);
    return session;
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
