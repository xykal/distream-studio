package id.distream.studio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.distream.studio.model.AudioCaptureMode
import id.distream.studio.model.ColorGradePreset
import id.distream.studio.model.CustomTextLayer
import id.distream.studio.model.LoveTapPreset
import id.distream.studio.model.OverlayFramePreset
import id.distream.studio.model.RtmpDestinationPreset
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.StreamOrientation
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StudioThemeMode
import id.distream.studio.model.VideoCodecOption
import id.distream.studio.model.VideoQualityPreset
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.security.StreamKeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.distreamDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "distream_studio_v4_prefs"
)

class StreamPreferencesRepository(private val appContext: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")

        val SAAS_TIER = stringPreferencesKey("saas_tier")
        val ENC_SAAS_LICENSE = stringPreferencesKey("enc_saas_license")
        val SAAS_EXPIRES_MS = longPreferencesKey("saas_expires_ms")
        val SAAS_BACKEND_URL = stringPreferencesKey("saas_backend_url")
        val SUBSCRIBER_EMAIL = stringPreferencesKey("subscriber_email")
        val SIMULCAST_ENABLED = booleanPreferencesKey("simulcast_enabled")
        val ENC_YOUTUBE_KEY = stringPreferencesKey("enc_youtube_key")
        val ENC_TWITCH_KEY = stringPreferencesKey("enc_twitch_key")
        val SMART_TTS_DONATION = booleanPreferencesKey("smart_tts_donation")
        val CLOUD_FAILOVER = booleanPreferencesKey("cloud_failover")

        val DEST_PRESET = stringPreferencesKey("dest_preset")
        val SERVER_URL = stringPreferencesKey("server_url")
        val ENCRYPTED_STREAM_KEY = stringPreferencesKey("enc_stream_key")
        val ORIENTATION = stringPreferencesKey("orientation")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val MANUAL_BITRATE_KBPS = intPreferencesKey("manual_bitrate_kbps")
        val MANUAL_FPS = intPreferencesKey("manual_fps")
        val ADAPTIVE_BITRATE = booleanPreferencesKey("adaptive_bitrate")
        val SAFE_ZONE = booleanPreferencesKey("safe_zone")
        val GRID_GUIDE = booleanPreferencesKey("grid_guide")
        val CAM_FRONT_LENS = booleanPreferencesKey("cam_front_lens")

        val FRAME_PRESET = stringPreferencesKey("frame_preset")
        val LOVE_PRESET = stringPreferencesKey("love_preset")
        val LIKE_TARGET = intPreferencesKey("like_target")
        val COLOR_GRADE = stringPreferencesKey("color_grade")
        val HEADLINE = stringPreferencesKey("headline")
        val SUBATHON_VIS = booleanPreferencesKey("subathon_vis")
        val SUBATHON_SEC = intPreferencesKey("subathon_sec")

        val GOAL_X = floatPreferencesKey("goal_x")
        val GOAL_Y = floatPreferencesKey("goal_y")
        val GOAL_SCALE = floatPreferencesKey("goal_scale")
        val LOVE_X = floatPreferencesKey("love_x")
        val LOVE_Y = floatPreferencesKey("love_y")
        val LOVE_SCALE = floatPreferencesKey("love_scale")
        val SUBATHON_X = floatPreferencesKey("subathon_x")
        val SUBATHON_Y = floatPreferencesKey("subathon_y")
        val SUBATHON_SCALE = floatPreferencesKey("subathon_scale")
        val FACECAM_X = floatPreferencesKey("facecam_x")
        val FACECAM_Y = floatPreferencesKey("facecam_y")
        val FACECAM_SCALE = floatPreferencesKey("facecam_scale")
        val TICKER_Y = floatPreferencesKey("ticker_y")

        val OVERLAY_MASTER = booleanPreferencesKey("overlay_master")
        val OVERLAY_PRIMARY_URL = stringPreferencesKey("overlay_primary_url")
        val OVERLAY_PRIMARY_VIS = booleanPreferencesKey("overlay_primary_vis")
        val OVERLAY_SECONDARY_URL = stringPreferencesKey("overlay_secondary_url")
        val OVERLAY_SECONDARY_VIS = booleanPreferencesKey("overlay_secondary_vis")
        val GOAL_VIS = booleanPreferencesKey("goal_vis")
        val GOAL_TITLE = stringPreferencesKey("goal_title")
        val GOAL_CURRENT = intPreferencesKey("goal_current")
        val GOAL_TARGET = intPreferencesKey("goal_target")
        val TICKER_VIS = booleanPreferencesKey("ticker_vis")
        val TICKER_TEXT = stringPreferencesKey("ticker_text")
        val WATERMARK_VIS = booleanPreferencesKey("watermark_vis")
        val CUSTOM_TEXTS_JSON = stringPreferencesKey("custom_texts_json")

        val FLOATING_HUD = booleanPreferencesKey("floating_hud")
        val FLOATING_FACECAM = booleanPreferencesKey("floating_facecam")
        val FACECAM_IN_ENCODER = booleanPreferencesKey("facecam_in_encoder")
        val FACECAM_SIZE = intPreferencesKey("facecam_size")
        val HUD_OPACITY = intPreferencesKey("hud_opacity")

        val AUDIO_MODE = stringPreferencesKey("audio_mode")
        val MIC_GAIN = floatPreferencesKey("mic_gain")
        val GAME_GAIN = floatPreferencesKey("game_gain")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val NOISE_GATE = booleanPreferencesKey("noise_gate")
        val VOICE_DUCKING = booleanPreferencesKey("voice_ducking")
        val ECHO_CANCEL = booleanPreferencesKey("echo_cancel")

        val SECURE_WINDOW = booleanPreferencesKey("secure_window")
        val AUTO_WIPE_KEY = booleanPreferencesKey("auto_wipe_key")
        val CONFIRM_STOP_PIN = booleanPreferencesKey("confirm_stop_pin")
        val ENCRYPTED_STOP_PIN = stringPreferencesKey("enc_stop_pin")
        val PROFANITY_FILTER = booleanPreferencesKey("profanity_filter")
        val DND_WARNING = booleanPreferencesKey("dnd_warning")

        val TIKTOK_USERNAME = stringPreferencesKey("tiktok_username")
        val CHAT_WS_URL = stringPreferencesKey("chat_ws_url")
    }

    val settingsFlow: Flow<StreamSettings> = appContext.distreamDataStore.data.map { prefs ->
        val decryptedKey = StreamKeyVault.decryptSecret(prefs[Keys.ENCRYPTED_STREAM_KEY].orEmpty())
        val decryptedPin = StreamKeyVault.decryptSecret(prefs[Keys.ENCRYPTED_STOP_PIN].orEmpty())
            .ifBlank { "1234" }
        val decryptedLicense = StreamKeyVault.decryptSecret(prefs[Keys.ENC_SAAS_LICENSE].orEmpty())
        val decryptedYt = StreamKeyVault.decryptSecret(prefs[Keys.ENC_YOUTUBE_KEY].orEmpty())
        val decryptedTwitch = StreamKeyVault.decryptSecret(prefs[Keys.ENC_TWITCH_KEY].orEmpty())

        StreamSettings(
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], StudioThemeMode.DARK),
            saasTier = enumOrDefault(prefs[Keys.SAAS_TIER], SaasPlanTier.FREE),
            saasLicenseToken = decryptedLicense,
            saasExpiresAtMillis = prefs[Keys.SAAS_EXPIRES_MS] ?: 0L,
            saasBackendUrl = SaasSubscriptionManager.normalizeCloudUrl(
                prefs[Keys.SAAS_BACKEND_URL].orEmpty()
            ),
            subscriberEmail = prefs[Keys.SUBSCRIBER_EMAIL] ?: "",
            simulcastEnabled = prefs[Keys.SIMULCAST_ENABLED] ?: false,
            simulcastYoutubeKey = decryptedYt,
            simulcastTwitchKey = decryptedTwitch,
            smartTtsDonationEnabled = prefs[Keys.SMART_TTS_DONATION] ?: true,
            cloudFailoverShieldEnabled = prefs[Keys.CLOUD_FAILOVER] ?: true,
            destinationPreset = enumOrDefault(prefs[Keys.DEST_PRESET], RtmpDestinationPreset.TIKTOK_RTMPS),
            serverUrl = prefs[Keys.SERVER_URL] ?: "rtmps://push.rtmp.tiktok.com/live",
            streamKey = decryptedKey,
            orientation = enumOrDefault(prefs[Keys.ORIENTATION], StreamOrientation.PORTRAIT_9_16),
            videoCodec = enumOrDefault(prefs[Keys.VIDEO_CODEC], VideoCodecOption.H264_AVC),
            qualityPreset = enumOrDefault(prefs[Keys.QUALITY_PRESET], VideoQualityPreset.HD_720P_60),
            manualBitrateKbps = prefs[Keys.MANUAL_BITRATE_KBPS] ?: 4200,
            manualFps = prefs[Keys.MANUAL_FPS] ?: 60,
            adaptiveBitrate = prefs[Keys.ADAPTIVE_BITRATE] ?: true,
            showSafeZoneGuide = prefs[Keys.SAFE_ZONE] ?: true,
            showGridGuide = prefs[Keys.GRID_GUIDE] ?: false,
            cameraFrontLens = prefs[Keys.CAM_FRONT_LENS] ?: true,
            framePreset = enumOrDefault(prefs[Keys.FRAME_PRESET], OverlayFramePreset.CLEAN_CREATOR),
            loveTapPreset = enumOrDefault(prefs[Keys.LOVE_PRESET], LoveTapPreset.TAP_LOVE_PILL),
            likeTargetCount = prefs[Keys.LIKE_TARGET] ?: 10_000,
            colorGrade = enumOrDefault(prefs[Keys.COLOR_GRADE], ColorGradePreset.VIVID_GAME),
            streamTitleHeadline = prefs[Keys.HEADLINE] ?: "Mabar Santai Push Rank Malam Ini",
            subathonTimerVisible = prefs[Keys.SUBATHON_VIS] ?: true,
            subathonRemainingSeconds = prefs[Keys.SUBATHON_SEC] ?: 3600,
            goalXPercent = prefs[Keys.GOAL_X] ?: 5f,
            goalYPercent = prefs[Keys.GOAL_Y] ?: 5f,
            goalScale = prefs[Keys.GOAL_SCALE] ?: 1.0f,
            loveXPercent = prefs[Keys.LOVE_X] ?: 54f,
            loveYPercent = prefs[Keys.LOVE_Y] ?: 16f,
            loveScale = prefs[Keys.LOVE_SCALE] ?: 1.0f,
            subathonXPercent = prefs[Keys.SUBATHON_X] ?: 5f,
            subathonYPercent = prefs[Keys.SUBATHON_Y] ?: 18f,
            subathonScale = prefs[Keys.SUBATHON_SCALE] ?: 1.0f,
            facecamXPercent = prefs[Keys.FACECAM_X] ?: 64f,
            facecamYPercent = prefs[Keys.FACECAM_Y] ?: 58f,
            facecamScale = prefs[Keys.FACECAM_SCALE] ?: 1.0f,
            tickerYPercent = prefs[Keys.TICKER_Y] ?: 91f,
            overlayMasterEnabled = prefs[Keys.OVERLAY_MASTER] ?: true,
            primaryOverlayUrl = prefs[Keys.OVERLAY_PRIMARY_URL] ?: "",
            primaryOverlayVisible = prefs[Keys.OVERLAY_PRIMARY_VIS] ?: true,
            secondaryOverlayUrl = prefs[Keys.OVERLAY_SECONDARY_URL] ?: "",
            secondaryOverlayVisible = prefs[Keys.OVERLAY_SECONDARY_VIS] ?: false,
            goalBarVisible = prefs[Keys.GOAL_VIS] ?: true,
            goalTitle = prefs[Keys.GOAL_TITLE] ?: "Target Gift Malam Ini",
            goalCurrent = prefs[Keys.GOAL_CURRENT] ?: 350,
            goalTarget = prefs[Keys.GOAL_TARGET] ?: 1000,
            lowerThirdVisible = prefs[Keys.TICKER_VIS] ?: true,
            lowerThirdTickerText = prefs[Keys.TICKER_TEXT]
                ?: "Follow & Tap-Tap Layar  •  Open Mabar ketik di kolom chat  •  Dukungan via link profil",
            watermarkVisible = prefs[Keys.WATERMARK_VIS] ?: true,
            customTextLayers = decodeCustomTexts(prefs[Keys.CUSTOM_TEXTS_JSON]),
            floatingHudEnabled = prefs[Keys.FLOATING_HUD] ?: true,
            floatingFacecamEnabled = prefs[Keys.FLOATING_FACECAM] ?: true,
            facecamInEncoder = prefs[Keys.FACECAM_IN_ENCODER] ?: false,
            facecamSizeIndex = prefs[Keys.FACECAM_SIZE] ?: 1,
            hudOpacityPercent = prefs[Keys.HUD_OPACITY] ?: 92,
            audioMode = enumOrDefault(prefs[Keys.AUDIO_MODE], AudioCaptureMode.MIX_GAME_AND_MIC),
            micGain = prefs[Keys.MIC_GAIN] ?: 2.0f,
            gameGain = prefs[Keys.GAME_GAIN] ?: 1.0f,
            sfxVolume = prefs[Keys.SFX_VOLUME] ?: 0.85f,
            noiseGateEnabled = prefs[Keys.NOISE_GATE] ?: true,
            voiceDuckingEnabled = prefs[Keys.VOICE_DUCKING] ?: true,
            echoCancelerEnabled = prefs[Keys.ECHO_CANCEL] ?: true,
            protectAppWindowFromCapture = prefs[Keys.SECURE_WINDOW] ?: true,
            autoWipeStreamKeyAfterLive = prefs[Keys.AUTO_WIPE_KEY] ?: false,
            confirmPinBeforeStop = prefs[Keys.CONFIRM_STOP_PIN] ?: true,
            stopStreamPin = decryptedPin,
            profanityChatFilter = prefs[Keys.PROFANITY_FILTER] ?: true,
            dndGuardWarning = prefs[Keys.DND_WARNING] ?: true,
            tiktokUsername = prefs[Keys.TIKTOK_USERNAME] ?: "",
            chatWebSocketUrl = prefs[Keys.CHAT_WS_URL] ?: "wss://distream-studio-cloud.vercel.app/"
        )
    }

    suspend fun saveSettings(s: StreamSettings) {
        appContext.distreamDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = s.themeMode.name
            prefs[Keys.SAAS_TIER] = s.saasTier.name
            prefs[Keys.ENC_SAAS_LICENSE] = StreamKeyVault.encryptSecret(s.saasLicenseToken)
            prefs[Keys.SAAS_EXPIRES_MS] = s.saasExpiresAtMillis
            prefs[Keys.SAAS_BACKEND_URL] = s.saasBackendUrl
            prefs[Keys.SUBSCRIBER_EMAIL] = s.subscriberEmail
            prefs[Keys.SIMULCAST_ENABLED] = s.simulcastEnabled
            prefs[Keys.ENC_YOUTUBE_KEY] = StreamKeyVault.encryptSecret(s.simulcastYoutubeKey)
            prefs[Keys.ENC_TWITCH_KEY] = StreamKeyVault.encryptSecret(s.simulcastTwitchKey)
            prefs[Keys.SMART_TTS_DONATION] = s.smartTtsDonationEnabled
            prefs[Keys.CLOUD_FAILOVER] = s.cloudFailoverShieldEnabled
            prefs[Keys.DEST_PRESET] = s.destinationPreset.name
            prefs[Keys.SERVER_URL] = s.serverUrl
            prefs[Keys.ENCRYPTED_STREAM_KEY] = StreamKeyVault.encryptSecret(s.streamKey)
            prefs[Keys.ORIENTATION] = s.orientation.name
            prefs[Keys.VIDEO_CODEC] = s.videoCodec.name
            prefs[Keys.QUALITY_PRESET] = s.qualityPreset.name
            prefs[Keys.MANUAL_BITRATE_KBPS] = s.manualBitrateKbps
            prefs[Keys.MANUAL_FPS] = s.manualFps
            prefs[Keys.ADAPTIVE_BITRATE] = s.adaptiveBitrate
            prefs[Keys.SAFE_ZONE] = s.showSafeZoneGuide
            prefs[Keys.GRID_GUIDE] = s.showGridGuide
            prefs[Keys.CAM_FRONT_LENS] = s.cameraFrontLens
            prefs[Keys.FRAME_PRESET] = s.framePreset.name
            prefs[Keys.LOVE_PRESET] = s.loveTapPreset.name
            prefs[Keys.LIKE_TARGET] = s.likeTargetCount
            prefs[Keys.COLOR_GRADE] = s.colorGrade.name
            prefs[Keys.HEADLINE] = s.streamTitleHeadline
            prefs[Keys.SUBATHON_VIS] = s.subathonTimerVisible
            prefs[Keys.SUBATHON_SEC] = s.subathonRemainingSeconds
            prefs[Keys.GOAL_X] = s.goalXPercent
            prefs[Keys.GOAL_Y] = s.goalYPercent
            prefs[Keys.GOAL_SCALE] = s.goalScale
            prefs[Keys.LOVE_X] = s.loveXPercent
            prefs[Keys.LOVE_Y] = s.loveYPercent
            prefs[Keys.LOVE_SCALE] = s.loveScale
            prefs[Keys.SUBATHON_X] = s.subathonXPercent
            prefs[Keys.SUBATHON_Y] = s.subathonYPercent
            prefs[Keys.SUBATHON_SCALE] = s.subathonScale
            prefs[Keys.FACECAM_X] = s.facecamXPercent
            prefs[Keys.FACECAM_Y] = s.facecamYPercent
            prefs[Keys.FACECAM_SCALE] = s.facecamScale
            prefs[Keys.TICKER_Y] = s.tickerYPercent
            prefs[Keys.OVERLAY_MASTER] = s.overlayMasterEnabled
            prefs[Keys.OVERLAY_PRIMARY_URL] = s.primaryOverlayUrl
            prefs[Keys.OVERLAY_PRIMARY_VIS] = s.primaryOverlayVisible
            prefs[Keys.OVERLAY_SECONDARY_URL] = s.secondaryOverlayUrl
            prefs[Keys.OVERLAY_SECONDARY_VIS] = s.secondaryOverlayVisible
            prefs[Keys.GOAL_VIS] = s.goalBarVisible
            prefs[Keys.GOAL_TITLE] = s.goalTitle
            prefs[Keys.GOAL_CURRENT] = s.goalCurrent
            prefs[Keys.GOAL_TARGET] = s.goalTarget
            prefs[Keys.TICKER_VIS] = s.lowerThirdVisible
            prefs[Keys.TICKER_TEXT] = s.lowerThirdTickerText
            prefs[Keys.WATERMARK_VIS] = s.watermarkVisible
            prefs[Keys.CUSTOM_TEXTS_JSON] = encodeCustomTexts(s.customTextLayers)
            prefs[Keys.FLOATING_HUD] = s.floatingHudEnabled
            prefs[Keys.FLOATING_FACECAM] = s.floatingFacecamEnabled
            prefs[Keys.FACECAM_IN_ENCODER] = s.facecamInEncoder
            prefs[Keys.FACECAM_SIZE] = s.facecamSizeIndex
            prefs[Keys.HUD_OPACITY] = s.hudOpacityPercent
            prefs[Keys.AUDIO_MODE] = s.audioMode.name
            prefs[Keys.MIC_GAIN] = s.micGain
            prefs[Keys.GAME_GAIN] = s.gameGain
            prefs[Keys.SFX_VOLUME] = s.sfxVolume
            prefs[Keys.NOISE_GATE] = s.noiseGateEnabled
            prefs[Keys.VOICE_DUCKING] = s.voiceDuckingEnabled
            prefs[Keys.ECHO_CANCEL] = s.echoCancelerEnabled
            prefs[Keys.SECURE_WINDOW] = s.protectAppWindowFromCapture
            prefs[Keys.AUTO_WIPE_KEY] = s.autoWipeStreamKeyAfterLive
            prefs[Keys.CONFIRM_STOP_PIN] = s.confirmPinBeforeStop
            prefs[Keys.ENCRYPTED_STOP_PIN] = StreamKeyVault.encryptSecret(s.stopStreamPin)
            prefs[Keys.PROFANITY_FILTER] = s.profanityChatFilter
            prefs[Keys.DND_WARNING] = s.dndGuardWarning
            prefs[Keys.TIKTOK_USERNAME] = s.tiktokUsername
            prefs[Keys.CHAT_WS_URL] = s.chatWebSocketUrl
        }
    }

    private fun encodeCustomTexts(list: List<CustomTextLayer>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("content", item.content)
                put("x", item.xPercent)
                put("y", item.yPercent)
                put("size", item.fontSizeSp)
                put("hex", item.accentHex)
                put("pill", item.backgroundPill)
                put("vis", item.visible)
            })
        }
        return arr.toString()
    }

    private fun decodeCustomTexts(json: String?): List<CustomTextLayer> {
        if (json.isNullOrBlank()) {
            return listOf(
                CustomTextLayer("txt_1", "ID Game: 82910422 (Open Slot)", 6, 28, 13, "#FFFFFF", true, true)
            )
        }
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CustomTextLayer(
                            id = o.optString("id", "txt_$i"),
                            content = o.optString("content", ""),
                            xPercent = o.optInt("x", 6),
                            yPercent = o.optInt("y", 28),
                            fontSizeSp = o.optInt("size", 13),
                            accentHex = o.optString("hex", "#FFFFFF"),
                            backgroundPill = o.optBoolean("pill", true),
                            visible = o.optBoolean("vis", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
