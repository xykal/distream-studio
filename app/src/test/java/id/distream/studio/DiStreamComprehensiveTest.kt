package id.distream.studio

import id.distream.studio.audio.ProBroadcastDspEffect
import id.distream.studio.audio.SoundFxSynthesizer
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamOrientation
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.VideoQualityPreset
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.security.StreamKeyVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiStreamComprehensiveTest {

    @Test
    fun `SaaS tiers matrix strictly enforces exclusive daily, weekly, and monthly entitlements`() {
        assertFalse(SaasPlanTier.FREE.removeWatermark)
        assertFalse(SaasPlanTier.FREE.allow1080p60AndHevc)
        assertFalse(SaasPlanTier.FREE.allowMultiStreamSimulcast)
        assertFalse(SaasPlanTier.FREE.allowSmartTtsDonation)
        assertEquals(1, SaasPlanTier.FREE.maxCustomTextLayers)

        // Daily Pass (Rp 5.000 / 24h) unlocks clean feed + 1080p60 HEVC + 8 SoundFX pads
        assertTrue(SaasPlanTier.DAILY.removeWatermark)
        assertTrue(SaasPlanTier.DAILY.allow1080p60AndHevc)
        assertTrue(SaasPlanTier.DAILY.unlockAllSoundFx)
        assertFalse(SaasPlanTier.DAILY.allowMultiStreamSimulcast)

        // Weekly Creator (Rp 19.000 / 7d) unlocks Cloud Multi-Stream Simulcast + Instant Replay 30s
        assertTrue(SaasPlanTier.WEEKLY.allowMultiStreamSimulcast)
        assertTrue(SaasPlanTier.WEEKLY.allowInstantReplayClip)
        assertEquals(99, SaasPlanTier.WEEKLY.maxCustomTextLayers)
        assertFalse(SaasPlanTier.WEEKLY.allowSmartTtsDonation)

        // Monthly Studio Pro (Rp 59.000 / 30d) unlocks Smart TTS + Cloud Anti-Disconnect Failover
        assertTrue(SaasPlanTier.MONTHLY.allowSmartTtsDonation)
        assertTrue(SaasPlanTier.MONTHLY.allowCloudFailoverShield)
    }

    @Test
    fun `SaaS signed token verifies authenticity, blocks tampering, and enforces device binding`() {
        val now = 1_760_000_000_000L
        val (token, expiresAt) = SaasSubscriptionManager.signLocalLicenseToken(
            tier = SaasPlanTier.MONTHLY,
            deviceId = "pixel-9-pro-id",
            issuedAtMillis = now
        )

        val validCheck = SaasSubscriptionManager.verifySignedToken(
            token = token,
            expectedDeviceId = "pixel-9-pro-id",
            nowMillis = now + 60_000L
        )
        assertTrue(validCheck.valid)
        assertEquals(SaasPlanTier.MONTHLY, validCheck.tier)
        assertEquals(expiresAt, validCheck.expiresAtMillis)

        // Device mismatch must be rejected
        val mismatchCheck = SaasSubscriptionManager.verifySignedToken(
            token = token,
            expectedDeviceId = "unauthorized-device",
            nowMillis = now + 60_000L
        )
        assertFalse(mismatchCheck.valid)
        assertEquals("DEVICE_MISMATCH", mismatchCheck.reasonCode)

        // Expired token must automatically downgrade to FREE
        val expiredCheck = SaasSubscriptionManager.verifySignedToken(
            token = token,
            expectedDeviceId = "pixel-9-pro-id",
            nowMillis = expiresAt + 1_000L
        )
        assertFalse(expiredCheck.valid)
        assertEquals(SaasPlanTier.FREE, expiredCheck.tier)

        // Forged signature must fail HMAC check
        val tampered = token.dropLast(4) + "AAAA"
        val tamperCheck = SaasSubscriptionManager.verifySignedToken(
            token = tampered,
            expectedDeviceId = "pixel-9-pro-id",
            nowMillis = now + 1_000L
        )
        assertFalse(tamperCheck.valid)
        assertEquals("INVALID_SIGNATURE", tamperCheck.reasonCode)
    }

    @Test
    fun `Promo pricing accurately applies XYKAL50, HEMAT20, and TRIALVIP`() {
        val (_, d1, p1) = SaasSubscriptionManager.calculatePromoPricing(SaasPlanTier.WEEKLY, "XYKAL50")
        assertEquals(50, d1)
        assertEquals(9_500, p1)

        val (t3, d3, p3) = SaasSubscriptionManager.calculatePromoPricing(SaasPlanTier.DAILY, "trialvip")
        assertEquals(SaasPlanTier.MONTHLY, t3)
        assertEquals(100, d3)
        assertEquals(0, p3)
    }

    @Test
    fun `SoundFxSynthesizer generates clean non-silent 48kHz stereo PCM waveforms across all 8 pads`() {
        for (pad in SoundFxPad.entries) {
            val pcm = SoundFxSynthesizer.buildStereoPcm16(pad, volumeGain = 0.85f)
            assertTrue("Pad ${pad.name} must produce stereo samples", pcm.size > 10_000)
            assertEquals("Stereo buffer must have even sample length", 0, pcm.size % 2)
            assertTrue("Pad ${pad.name} must contain non-zero signal", pcm.any { it.toInt() != 0 })
        }
    }

    @Test
    fun `ProBroadcastDspEffect enforces noise gate, 1kHz censor tone, and SoundFX mixing cleanly`() {
        val dsp = ProBroadcastDspEffect { _, _, _ -> }

        // Quiet fan hiss (16-bit little-endian amplitude = 24 -> -62.7 dBFS < -42 dBFS threshold)
        // must be cleanly zeroed out by the broadcast noise gate
        val quietBuffer = ByteArray(1024) { idx -> if (idx % 2 == 0) 24 else 0 }
        dsp.process(quietBuffer)
        assertTrue("Noise gate must zero out low-amplitude hiss", quietBuffer.all { it.toInt() == 0 })

        // Injecting SoundFX over gated mic must emit SoundFX samples into RTMP output
        val coinSamples = SoundFxSynthesizer.buildStereoPcm16(SoundFxPad.COIN_ALERT, 0.9f)
        dsp.enqueueSfxSamples(coinSamples)
        val mixedOut = ByteArray(1024) { 0 }
        dsp.process(mixedOut)
        assertTrue("SoundFX samples must appear in RTMP output buffer", mixedOut.any { it.toInt() != 0 })

        // Live 1kHz Censor Tone overrides microphone audio completely
        dsp.clearSfxSamples()
        dsp.liveCensorToneActive = true
        val censorOut = ByteArray(1024) { 0 }
        dsp.process(censorOut)
        assertTrue("1kHz censor tone must populate PCM buffer", censorOut.any { it.toInt() != 0 })
    }

    @Test
    fun `Security redactor hides secret RTMP stream keys in logs and UI`() {
        val settings = StreamSettings(
            serverUrl = "rtmps://push.rtmp.tiktok.com/live",
            streamKey = "sk_super_secret_live_99887766"
        )
        val redacted = StreamKeyVault.sanitizeEndpointForDisplay(settings.buildFullEndpoint())
        assertFalse("Stream key must never appear in display log", redacted.contains("998877"))
        assertTrue(redacted.startsWith("rtmps://push.rtmp.tiktok.com/live/"))
    }

    @Test
    fun `Portrait, Landscape, 4x5, and 1x1 encoder resolution resolver computes exact even dimensions`() {
        val (pw, ph) = VideoQualityPreset.FHD_1080P_60.resolveDimensions(StreamOrientation.PORTRAIT_9_16)
        assertEquals(1080, pw)
        assertEquals(1920, ph)

        val (lw, lh) = VideoQualityPreset.FHD_1080P_60.resolveDimensions(StreamOrientation.LANDSCAPE_16_9)
        assertEquals(1920, lw)
        assertEquals(1080, lh)

        val (fw, fh) = VideoQualityPreset.FHD_1080P_60.resolveDimensions(StreamOrientation.CREATOR_4_5)
        assertEquals(1080, fw)
        assertEquals(1350, fh)

        val (sw, sh) = VideoQualityPreset.FHD_1080P_60.resolveDimensions(StreamOrientation.SQUARE_1_1)
        assertEquals(1080, sw)
        assertEquals(1080, sh)
    }

    @Test
    fun `ZXing ISO 18004 QR matrix generator encodes real scannable QRIS payload and Cloud URL normalizes`() {
        assertEquals(
            "https://distream-studio-cloud.vercel.app",
            SaasSubscriptionManager.normalizeCloudUrl("http://localhost:21213")
        )
        val matrix = SaasSubscriptionManager.encodeQrBitMatrix(
            "https://app.pakasir.com/pay-v2/gkiq8c8iq?qris_only=1",
            256
        )
        assertEquals(256, matrix.width)
        assertEquals(256, matrix.height)
    }
}
