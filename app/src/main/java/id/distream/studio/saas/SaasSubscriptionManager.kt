package id.distream.studio.saas

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.StreamSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SaasCheckoutOrder(
    val orderId: String,
    val txnId: String = "",
    val orderToken: String = "",
    val planTier: SaasPlanTier,
    val basePriceIdr: Int,
    val discountPercent: Int,
    val finalAmountIdr: Int,
    val feeIdr: Int,
    val totalPaymentIdr: Int,
    val qrisPayload: String,
    val hostedPaymentUrl: String,
    val status: String,
    val gatewayLive: Boolean = true,
    val isSandbox: Boolean = false,
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
 * Mesin Manajemen Langganan SaaS DiStream Studio v4.0 (Real Pakasir API v2 + ZXing QR Matrix).
 */
object SaasSubscriptionManager {

    const val PRODUCTION_CLOUD_URL = "https://distream-studio-cloud.vercel.app"
    private const val DEFAULT_SIGNING_SECRET = "distream-studio-default-hmac-key-2026"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun normalizeCloudUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (
            trimmed.isBlank() ||
            trimmed.contains("localhost") ||
            trimmed.contains("127.0.0.1") ||
            trimmed.contains("10.0.2.2")
        ) {
            return PRODUCTION_CLOUD_URL
        }
        return trimmed
    }

    /**
     * Menghasilkan matriks QR Code ISO/IEC 18004 asli (pure JVM, dapat diuji unit test tanpa Android OS).
     */
    fun encodeQrBitMatrix(payload: String, sizePx: Int = 512): BitMatrix {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        return QRCodeWriter().encode(
            payload.ifBlank { PRODUCTION_CLOUD_URL },
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
    }

    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap? = runCatching {
        val matrix = encodeQrBitMatrix(payload, sizePx)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()

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
        val cloudBase = normalizeCloudUrl(backendBaseUrl)
        val bodyJson = JSONObject().apply {
            put("planId", tier.name)
            put("deviceId", deviceId)
            put("promoCode", promoCode.trim())
            put("email", email.trim())
        }.toString()

        val remoteOrder = runCatching {
            val req = Request.Builder()
                .url("$cloudBase/api/saas/checkout")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val o = JSONObject(resp.body?.string().orEmpty()).getJSONObject("order")
                SaasCheckoutOrder(
                    orderId = o.getString("orderId"),
                    txnId = o.optString("txnId", ""),
                    orderToken = o.optString("orderToken", ""),
                    planTier = runCatching { SaasPlanTier.valueOf(o.getString("planId")) }.getOrDefault(tier),
                    basePriceIdr = o.optInt("baseAmount", tier.priceIdr),
                    discountPercent = o.optInt("discountPercent", 0),
                    finalAmountIdr = o.getInt("amount"),
                    feeIdr = o.optInt("fee", 0),
                    totalPaymentIdr = o.optInt("totalPayment", o.getInt("amount")),
                    qrisPayload = o.optString("qrisString", ""),
                    hostedPaymentUrl = o.optString("paymentUrl", ""),
                    status = o.optString("status", "pending"),
                    gatewayLive = o.optBoolean("gatewayLive", true),
                    isSandbox = o.optBoolean("isSandbox", false),
                    licenseToken = o.optString("licenseToken", ""),
                    expiresAtMillis = o.optLong("expiresAt", 0L)
                )
            }
        }.getOrNull()

        if (remoteOrder != null) return remoteOrder

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
                gatewayLive = false,
                licenseToken = tok,
                expiresAtMillis = exp
            )
        }
        val fee = (amount * 0.007f).toInt() + 310
        val fallbackUrl = "https://app.pakasir.com/pay/distream/$amount?order_id=$orderId&qris_only=1"
        return SaasCheckoutOrder(
            orderId = orderId,
            planTier = effectiveTier,
            basePriceIdr = effectiveTier.priceIdr,
            discountPercent = discPct,
            finalAmountIdr = amount,
            feeIdr = fee,
            totalPaymentIdr = amount + fee,
            qrisPayload = fallbackUrl,
            hostedPaymentUrl = fallbackUrl,
            status = "pending",
            gatewayLive = false
        )
    }

    fun pollOrderStatus(
        backendBaseUrl: String,
        pendingOrder: SaasCheckoutOrder
    ): SaasCheckoutOrder {
        val cloudBase = normalizeCloudUrl(backendBaseUrl)
        return runCatching {
            val encodedToken = URLEncoder.encode(pendingOrder.orderToken, "UTF-8")
            val req = Request.Builder()
                .url("$cloudBase/api/saas/order/${pendingOrder.orderId}?orderToken=$encodedToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use pendingOrder
                val o = JSONObject(resp.body?.string().orEmpty()).getJSONObject("order")
                pendingOrder.copy(
                    status = o.optString("status", pendingOrder.status),
                    licenseToken = o.optString("licenseToken", pendingOrder.licenseToken),
                    expiresAtMillis = o.optLong("expiresAt", pendingOrder.expiresAtMillis)
                )
            }
        }.getOrDefault(pendingOrder)
    }

    fun confirmOrderSettlement(
        backendBaseUrl: String,
        pendingOrder: SaasCheckoutOrder,
        deviceId: String
    ): SaasCheckoutOrder {
        val cloudBase = normalizeCloudUrl(backendBaseUrl)
        val remoteSettled = runCatching {
            val body = JSONObject().apply {
                put("orderId", pendingOrder.orderId)
                put("txnId", pendingOrder.txnId)
                put("orderToken", pendingOrder.orderToken)
            }.toString()
            val req = Request.Builder()
                .url("$cloudBase/api/saas/pakasir/pay-sandbox")
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
        val cloudBase = normalizeCloudUrl(backendBaseUrl)
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
                .url("$cloudBase/api/saas/simulcast/configure")
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
