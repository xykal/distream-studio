package id.distream.studio.saas

import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.StreamSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SaasCheckoutOrder(
    val orderId: String,
    val planTier: SaasPlanTier,
    val basePriceIdr: Int,
    val discountPercent: Int,
    val finalAmountIdr: Int,
    val feeIdr: Int,
    val totalPaymentIdr: Int,
    val qrisPayload: String,
    val hostedPaymentUrl: String,
    val status: String,
    val licenseToken: String = "",
    val expiresAtMillis: Long = 0L
)

data class DecodedLicenseResult(
    val valid: Boolean,
    val reasonCode: String,
    val tier: SaasPlanTier,
    val deviceId: String = "",
    val expiresAtMillis: Long = 0L
)

/**
 * Mesin Manajemen Langganan SaaS DiStream Studio (Harian, Mingguan, Bulanan & Gratis).
 *
 * Fitur:
 * - Verifikasi kriptografi HMAC-SHA256 (`DST1.<base64urlPayload>.<hmacSignature>`) anti-manipulasi
 * - Pengikatan Device ID (Anti-Sharing Lisensi) & auto-downgrade saat masa aktif habis
 * - Perhitungan Kode Voucher (`XYKAL50`, `HEMAT20`, `TRIALVIP`)
 * - Integrasi Checkout QRIS Pakasir via `distream-backend` + fallback mode offline/sandbox
 */
object SaasSubscriptionManager {

    private const val DEFAULT_SIGNING_SECRET = "distream-studio-default-hmac-key-2026"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun resolveActiveTier(
        settings: StreamSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): SaasPlanTier {
        if (settings.saasTier == SaasPlanTier.FREE) return SaasPlanTier.FREE
        if (settings.saasExpiresAtMillis <= nowMillis) return SaasPlanTier.FREE
        if (settings.saasLicenseToken.isNotBlank()) {
            val verified = verifySignedToken(
                token = settings.saasLicenseToken,
                expectedDeviceId = null,
                nowMillis = nowMillis
            )
            if (!verified.valid) return SaasPlanTier.FREE
            return verified.tier
        }
        return settings.saasTier
    }

    fun calculatePromoPricing(tier: SaasPlanTier, promoCode: String): Triple<SaasPlanTier, Int, Int> {
        return when (promoCode.trim().uppercase()) {
            "XYKAL50" -> Triple(tier, 50, (tier.priceIdr * 50) / 100)
            "HEMAT20" -> Triple(tier, 20, (tier.priceIdr * 80) / 100)
            "TRIALVIP" -> Triple(SaasPlanTier.MONTHLY, 100, 0)
            else -> Triple(tier, 0, tier.priceIdr)
        }
    }

    fun signLocalLicenseToken(
        tier: SaasPlanTier,
        deviceId: String,
        durationHoursOverride: Int? = null,
        issuedAtMillis: Long = System.currentTimeMillis(),
        signingSecret: String = DEFAULT_SIGNING_SECRET
    ): Pair<String, Long> {
        val hours = durationHoursOverride ?: tier.durationHours
        val expiresAt = issuedAtMillis + hours * 3_600_000L
        val payloadJson = JSONObject().apply {
            put("sub", "DST-${tier.name}-$issuedAtMillis")
            put("tier", tier.name)
            put("deviceId", deviceId.ifBlank { "android-device" })
            put("issuedAt", issuedAtMillis)
            put("expiresAt", expiresAt)
        }.toString()

        val encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))

        val sig = computeHmacBase64Url("DST1.$encodedPayload", signingSecret)
        return "DST1.$encodedPayload.$sig" to expiresAt
    }

    fun verifySignedToken(
        token: String,
        expectedDeviceId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        signingSecret: String = DEFAULT_SIGNING_SECRET
    ): DecodedLicenseResult {
        val parts = token.trim().split(".")
        if (parts.size != 3 || parts[0] != "DST1") {
            return DecodedLicenseResult(false, "MALFORMED_TOKEN", SaasPlanTier.FREE)
        }

        val encodedPayload = parts[1]
        val providedSig = parts[2]
        val expectedSig = computeHmacBase64Url("DST1.$encodedPayload", signingSecret)

        if (!MessageDigest.isEqual(
                providedSig.toByteArray(StandardCharsets.UTF_8),
                expectedSig.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            return DecodedLicenseResult(false, "INVALID_SIGNATURE", SaasPlanTier.FREE)
        }

        val payloadObj = runCatching {
            val raw = Base64.getUrlDecoder().decode(encodedPayload)
            JSONObject(String(raw, StandardCharsets.UTF_8))
        }.getOrElse {
            return DecodedLicenseResult(false, "CORRUPT_PAYLOAD", SaasPlanTier.FREE)
        }

        val boundDevice = payloadObj.optString("deviceId", "")
        if (!expectedDeviceId.isNullOrBlank() && boundDevice.isNotBlank() && boundDevice != expectedDeviceId) {
            return DecodedLicenseResult(false, "DEVICE_MISMATCH", SaasPlanTier.FREE, boundDevice)
        }

        val expiresAt = payloadObj.optLong("expiresAt", 0L)
        if (expiresAt <= nowMillis) {
            return DecodedLicenseResult(false, "LICENSE_EXPIRED", SaasPlanTier.FREE, boundDevice, expiresAt)
        }

        val tierName = payloadObj.optString("tier", "FREE")
        val resolvedTier = runCatching { SaasPlanTier.valueOf(tierName) }
            .getOrDefault(SaasPlanTier.FREE)

        return DecodedLicenseResult(
            valid = resolvedTier != SaasPlanTier.FREE,
            reasonCode = "ACTIVE",
            tier = resolvedTier,
            deviceId = boundDevice,
            expiresAtMillis = expiresAt
        )
    }

    fun createCheckoutSession(
        backendBaseUrl: String,
        tier: SaasPlanTier,
        deviceId: String,
        promoCode: String,
        email: String
    ): SaasCheckoutOrder {
        val bodyJson = JSONObject().apply {
            put("planId", tier.name)
            put("deviceId", deviceId)
            put("promoCode", promoCode.trim())
            put("email", email.trim())
        }.toString()

        val remoteOrder = runCatching {
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/saas/checkout")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty()).getJSONObject("order")
                SaasCheckoutOrder(
                    orderId = o.getString("orderId"),
                    planTier = runCatching { SaasPlanTier.valueOf(o.getString("planId")) }.getOrDefault(tier),
                    basePriceIdr = o.optInt("baseAmount", tier.priceIdr),
                    discountPercent = o.optInt("discountPercent", 0),
                    finalAmountIdr = o.getInt("amount"),
                    feeIdr = o.optInt("fee", 0),
                    totalPaymentIdr = o.optInt("totalPayment", o.getInt("amount")),
                    qrisPayload = o.optString("qrisString", ""),
                    hostedPaymentUrl = o.optString("paymentUrl", ""),
                    status = o.optString("status", "pending"),
                    licenseToken = o.optString("licenseToken", ""),
                    expiresAtMillis = o.optLong("expiresAt", 0L)
                )
            }
        }.getOrNull()

        if (remoteOrder != null) return remoteOrder

        // Fallback offline/sandbox apabila server lokal belum dinyalakan
        val (effectiveTier, discPct, amount) = calculatePromoPricing(tier, promoCode)
        val orderId = "DST-${effectiveTier.name}-${System.currentTimeMillis() % 1_000_000}"
        if (amount == 0) {
            val (tok, exp) = signLocalLicenseToken(effectiveTier, deviceId, durationHoursOverride = 24)
            return SaasCheckoutOrder(
                orderId = orderId,
                planTier = effectiveTier,
                basePriceIdr = effectiveTier.priceIdr,
                discountPercent = discPct,
                finalAmountIdr = 0,
                feeIdr = 0,
                totalPaymentIdr = 0,
                qrisPayload = "",
                hostedPaymentUrl = "",
                status = "completed",
                licenseToken = tok,
                expiresAtMillis = exp
            )
        }
        val fee = (amount * 0.007f).toInt() + 310
        return SaasCheckoutOrder(
            orderId = orderId,
            planTier = effectiveTier,
            basePriceIdr = effectiveTier.priceIdr,
            discountPercent = discPct,
            finalAmountIdr = amount,
            feeIdr = fee,
            totalPaymentIdr = amount + fee,
            qrisPayload = "00020101021226660014ID.CO.DISTREAM.WWW011893600914${orderId.takeLast(10)}5303360540${amount}5802ID",
            hostedPaymentUrl = "https://app.pakasir.com/pay/distream/$amount?order_id=$orderId&qris_only=1",
            status = "pending"
        )
    }

    fun confirmOrderSettlement(
        backendBaseUrl: String,
        pendingOrder: SaasCheckoutOrder,
        deviceId: String
    ): SaasCheckoutOrder {
        val remoteSettled = runCatching {
            val body = JSONObject().put("orderId", pendingOrder.orderId).toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/saas/sandbox/complete")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty()).getJSONObject("order")
                pendingOrder.copy(
                    status = o.getString("status"),
                    licenseToken = o.getString("licenseToken"),
                    expiresAtMillis = o.getLong("expiresAt")
                )
            }
        }.getOrNull()

        if (remoteSettled != null) return remoteSettled

        val (localToken, exp) = signLocalLicenseToken(pendingOrder.planTier, deviceId)
        return pendingOrder.copy(
            status = "completed",
            licenseToken = localToken,
            expiresAtMillis = exp
        )
    }

    fun requestSimulcastIngestUrl(
        backendBaseUrl: String,
        settings: StreamSettings,
        deviceId: String
    ): String? {
        val targets = JSONArray().apply {
            if (settings.streamKey.isNotBlank()) {
                put(JSONObject().put("platform", "Primary").put("url", settings.buildFullEndpoint()))
            }
            if (settings.simulcastYoutubeKey.isNotBlank()) {
                put(JSONObject().put("platform", "YouTube").put("url", "rtmps://a.rtmps.youtube.com/live2/${settings.simulcastYoutubeKey.trim()}"))
            }
            if (settings.simulcastTwitchKey.isNotBlank()) {
                put(JSONObject().put("platform", "Twitch").put("url", "rtmps://sin01.contribute.live-video.net/app/${settings.simulcastTwitchKey.trim()}"))
            }
        }
        if (targets.length() <= 1) return null

        return runCatching {
            val payload = JSONObject().apply {
                put("licenseToken", settings.saasLicenseToken)
                put("deviceId", deviceId)
                put("targets", targets)
            }.toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/saas/simulcast/configure")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty())
                    .getJSONObject("relay")
                    .getString("relayIngestUrl")
            }
        }.getOrNull()
    }

    private fun computeHmacBase64Url(input: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
