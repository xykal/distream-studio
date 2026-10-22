package id.distream.studio.model

enum class StudioThemeMode(val label: String) {
    DARK("Obsidian Dark"),
    LIGHT("Daylight Clean"),
    ROYAL_GOLD("Royal Gold VIP")
}

enum class SaasPlanTier(
    val title: String,
    val badge: String,
    val priceIdr: Int,
    val periodLabel: String,
    val durationHours: Int,
    val removeWatermark: Boolean,
    val allow1080p60AndHevc: Boolean,
    val unlockAllSoundFx: Boolean,
    val unlockAllScenes: Boolean,
    val allowMultiStreamSimulcast: Boolean,
    val allowInstantReplayClip: Boolean,
    val maxCustomTextLayers: Int,
    val allowSmartTtsDonation: Boolean,
    val allowCloudFailoverShield: Boolean
) {
    FREE(
        title = "Starter Gratis",
        badge = "Gratis",
        priceIdr = 0,
        periodLabel = "Selamanya",
        durationHours = 0,
        removeWatermark = false,
        allow1080p60AndHevc = false,
        unlockAllSoundFx = false,
        unlockAllScenes = false,
        allowMultiStreamSimulcast = false,
        allowInstantReplayClip = false,
        maxCustomTextLayers = 1,
        allowSmartTtsDonation = false,
        allowCloudFailoverShield = false
    ),
    DAILY(
        title = "Pass Harian",
        badge = "Harian 24 Jam",
        priceIdr = 5_000,
        periodLabel = "Rp 5.000 / hari",
        durationHours = 24,
        removeWatermark = true,
        allow1080p60AndHevc = true,
        unlockAllSoundFx = true,
        unlockAllScenes = true,
        allowMultiStreamSimulcast = false,
        allowInstantReplayClip = false,
        maxCustomTextLayers = 3,
        allowSmartTtsDonation = false,
        allowCloudFailoverShield = false
    ),
    WEEKLY(
        title = "Creator Mingguan",
        badge = "Hemat 46%",
        priceIdr = 19_000,
        periodLabel = "Rp 19.000 / 7 hari",
        durationHours = 24 * 7,
        removeWatermark = true,
        allow1080p60AndHevc = true,
        unlockAllSoundFx = true,
        unlockAllScenes = true,
        allowMultiStreamSimulcast = true,
        allowInstantReplayClip = true,
        maxCustomTextLayers = 99,
        allowSmartTtsDonation = false,
        allowCloudFailoverShield = false
    ),
    MONTHLY(
        title = "Studio Pro Bulanan",
        badge = "VIP Populer",
        priceIdr = 59_000,
        periodLabel = "Rp 59.000 / 30 hari",
        durationHours = 24 * 30,
        removeWatermark = true,
        allow1080p60AndHevc = true,
        unlockAllSoundFx = true,
        unlockAllScenes = true,
        allowMultiStreamSimulcast = true,
        allowInstantReplayClip = true,
        maxCustomTextLayers = 99,
        allowSmartTtsDonation = true,
        allowCloudFailoverShield = true
    )
}

enum class StreamOrientation(
    val label: String,
    val ratioTag: String,
    val isPortrait: Boolean,
    val aspectRatio: Float
) {
    PORTRAIT_9_16("TikTok Potret (9:16)", "9:16", true, 9f / 16f),
    LANDSCAPE_16_9("Lanskap Game/YT (16:9)", "16:9", false, 16f / 9f),
    CREATOR_4_5("Feed Kreator (4:5)", "4:5", true, 4f / 5f),
    SQUARE_1_1("Kotak Studio (1:1)", "1:1", false, 1.0f)
}

enum class VideoCodecOption(val label: String, val subtitle: String, val requiresPro: Boolean) {
    H264_AVC("H.264 (AVC)", "Kompatibilitas universal server TikTok & YouTube", false),
    H265_HEVC("H.265 (HEVC Pro)", "Gambar tajam hemat kuota 40% (Mulai Paket Harian)", true)
}

enum class VideoQualityPreset(
    val shortCode: String,
    val label: String,
    val shortSide: Int,
    val longSide: Int,
    val fps: Int,
    val maxBitrateBps: Int,
    val requiresPro: Boolean
) {
    SD_540P_30("540p • 30fps", "SD 540p 30 FPS (Hemat Sinyal)", 540, 960, 30, 1_800_000, false),
    HD_720P_30("720p • 30fps", "HD 720p 30 FPS (Stabil Harian)", 720, 1280, 30, 2_800_000, false),
    HD_720P_60("720p • 60fps", "HD 720p 60 FPS (Standar Game)", 720, 1280, 60, 4_200_000, false),
    FHD_1080P_60("1080p • 60fps", "Full HD 1080p 60 FPS Pro (Mulai Paket Harian)", 1080, 1920, 60, 6_200_000, true);

    fun resolveDimensions(orientation: StreamOrientation): Pair<Int, Int> = when (orientation) {
        StreamOrientation.PORTRAIT_9_16 -> shortSide to longSide
        StreamOrientation.LANDSCAPE_16_9 -> longSide to shortSide
        StreamOrientation.CREATOR_4_5 -> shortSide to (((shortSide * 5) / 4) and -2)
        StreamOrientation.SQUARE_1_1 -> shortSide to shortSide
    }
}

enum class StudioScene(val label: String, val description: String, val requiresPro: Boolean) {
    GAMEPLAY("Layar Game", "Tangkap layar penuh + layer overlay aktif", false),
    STARTING_SOON("Segera Mulai", "Layar pembuka siaran + hitung mundur", false),
    INTERMISSION_BRB("Istirahat (BRB)", "Tampilan tunggu sementara (Pro)", true),
    IRL_CAMERA("Kamera Penuh", "Kamera depan/belakang jernih untuk ngobrol (Pro)", true),
    PRIVACY_SHIELD("Layar Privasi", "Tutup instan saat ketik OTP / buka chat", false)
}

enum class OverlayFramePreset(
    val label: String,
    val description: String,
    val accentHex: String,
    val requiresPro: Boolean
) {
    CLEAN_CREATOR("Clean Creator", "Tipografi modern bersih dengan sudut halus", "#2563EB", false),
    SOFT_GLASS("Frosted Glass", "Transparan lembut tidak menutup visual game", "#0EA5E9", true),
    TOURNAMENT_PRO("Esports Broadcast", "Bar turnamen kontras tinggi", "#E11D48", true),
    ROYAL_GOLD_VIP("Royal Gold VIP", "Tema eksklusif aksen emas untuk kreator Pro", "#D97706", true),
    MINIMAL_LIGHT("Minimal Light", "Kartu putih terang untuk konten edukasi/IRL", "#18181B", false)
}

enum class LoveTapPreset(val label: String, val description: String) {
    NONE("Nonaktif", "Sembunyikan widget ajakan Tap-Tap Layar"),
    TAP_LOVE_PILL("Tap-Tap Love & Share", "Animasi ikon hati halus + ajakan tap layar"),
    LIKE_TARGET_CARD("Target Like Live", "Counter progres like real-time menuju target"),
    TOP_SUPPORTER_BADGE("Top Supporter", "Menampilkan pemberi gift tertinggi sesi ini")
}

enum class ColorGradePreset(val label: String, val description: String) {
    NATURAL("Natural", "Warna standar bawaan layar"),
    VIVID_GAME("Vivid Game", "Warna arena & skill lebih hidup (+25% saturasi)"),
    CRISP_CONTRAST("Crisp Contrast", "Detail bayangan lebih tegas"),
    MONOCHROME("Monokrom", "Hitam-putih lembut untuk mode jeda")
}

enum class AudioCaptureMode(val label: String, val subtitle: String) {
    MIX_GAME_AND_MIC("Game + Mikrofon", "Suara game internal & vokal streamer digabung"),
    INTERNAL_GAME_ONLY("Suara Game Saja", "Tanpa input mikrofon luar"),
    MIC_ONLY("Mikrofon Saja", "Khusus ngobrol / Kamera IRL")
}

enum class SoundFxPad(
    val label: String,
    val category: String,
    val durationMs: Int,
    val baseFreqHz: Double,
    val requiresPro: Boolean
) {
    APPLAUSE("Tepuk Tangan", "Apresiasi", 1400, 440.0, false),
    AIRHORN("Hype Horn", "Selebrasi", 950, 587.33, false),
    COIN_ALERT("Koin Masuk", "Donasi", 600, 987.77, false),
    CENSOR_BEEP("Sensor 1kHz", "Keamanan Suara", 900, 1000.0, false),
    VICTORY("Victory Chime", "Menang", 1100, 659.25, true),
    RIMSHOT("Ba-Dum-Tss", "Komedi", 750, 329.63, true),
    BOOM_DROP("Bass Drop", "Reaksi", 800, 92.50, true),
    SUSPENSE("Riser Tegang", "Momen Clutch", 1300, 261.63, true)
}

enum class RtmpDestinationPreset(val platformName: String, val defaultServerUrl: String) {
    TIKTOK_RTMPS("TikTok LIVE (RTMPS Aman)", "rtmps://push.rtmp.tiktok.com/live"),
    TIKTOK_RTMP("TikTok LIVE (RTMP Standar)", "rtmp://push.rtmp.tiktok.com/live"),
    YOUTUBE_LIVE("YouTube Live", "rtmps://a.rtmps.youtube.com/live2"),
    TWITCH("Twitch", "rtmps://sin01.contribute.live-video.net/app"),
    CUSTOM("Server Kustom", "rtmps://")
}

data class CustomTextLayer(
    val id: String,
    val content: String,
    val xPercent: Int = 6,
    val yPercent: Int = 28,
    val fontSizeSp: Int = 14,
    val accentHex: String = "#FFFFFF",
    val backgroundPill: Boolean = true,
    val visible: Boolean = true
)

enum class StreamStatus {
    IDLE, CONNECTING, LIVE, RECONNECTING, ERROR
}

data class StreamSettings(
    val themeMode: StudioThemeMode = StudioThemeMode.DARK,

    // SaaS Subscription & Real Cloud Gateway (Pakasir API v2 + Vercel Production Cloud)
    val saasTier: SaasPlanTier = SaasPlanTier.FREE,
    val saasLicenseToken: String = "",
    val saasExpiresAtMillis: Long = 0L,
    val saasBackendUrl: String = "https://distream-studio-cloud.vercel.app",
    val subscriberEmail: String = "",
    val simulcastEnabled: Boolean = false,
    val simulcastYoutubeKey: String = "",
    val simulcastTwitchKey: String = "",
    val smartTtsDonationEnabled: Boolean = true,
    val cloudFailoverShieldEnabled: Boolean = true,

    // Broadcast Destination & Granular Manual/Preset Encoder Controls
    val destinationPreset: RtmpDestinationPreset = RtmpDestinationPreset.TIKTOK_RTMPS,
    val serverUrl: String = "rtmps://push.rtmp.tiktok.com/live",
    val streamKey: String = "",
    val orientation: StreamOrientation = StreamOrientation.PORTRAIT_9_16,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264_AVC,
    val qualityPreset: VideoQualityPreset = VideoQualityPreset.HD_720P_60,
    val manualBitrateKbps: Int = 4200,
    val manualFps: Int = 60,
    val adaptiveBitrate: Boolean = true,
    val showSafeZoneGuide: Boolean = true,
    val showGridGuide: Boolean = false,
    val cameraFrontLens: Boolean = true,

    // Scene & Visual Preset
    val activeScene: StudioScene = StudioScene.GAMEPLAY,
    val framePreset: OverlayFramePreset = OverlayFramePreset.CLEAN_CREATOR,
    val loveTapPreset: LoveTapPreset = LoveTapPreset.TAP_LOVE_PILL,
    val likeTargetCount: Int = 10_000,
    val colorGrade: ColorGradePreset = ColorGradePreset.VIVID_GAME,
    val streamTitleHeadline: String = "Mabar Santai Push Rank Malam Ini",
    val subathonTimerVisible: Boolean = true,
    val subathonRemainingSeconds: Int = 3600,

    // Interactive WYSIWYG Canvas Coordinates (0..100% Normalized Stage Space + Scale)
    val goalXPercent: Float = 5f,
    val goalYPercent: Float = 5f,
    val goalScale: Float = 1.0f,
    val loveXPercent: Float = 54f,
    val loveYPercent: Float = 16f,
    val loveScale: Float = 1.0f,
    val subathonXPercent: Float = 5f,
    val subathonYPercent: Float = 18f,
    val subathonScale: Float = 1.0f,
    val facecamXPercent: Float = 64f,
    val facecamYPercent: Float = 58f,
    val facecamScale: Float = 1.0f,
    val tickerYPercent: Float = 91f,

    // Multi-Layer Overlay & Teks Kustom
    val overlayMasterEnabled: Boolean = true,
    val primaryOverlayUrl: String = "",
    val primaryOverlayVisible: Boolean = true,
    val secondaryOverlayUrl: String = "",
    val secondaryOverlayVisible: Boolean = false,
    val goalBarVisible: Boolean = true,
    val goalTitle: String = "Target Gift Malam Ini",
    val goalCurrent: Int = 350,
    val goalTarget: Int = 1000,
    val lowerThirdVisible: Boolean = true,
    val lowerThirdTickerText: String = "Follow & Tap-Tap Layar  •  Open Mabar ketik di kolom chat  •  Dukungan via link profil",
    val watermarkVisible: Boolean = true,
    val customTextLayers: List<CustomTextLayer> = listOf(
        CustomTextLayer(
            id = "txt_1",
            content = "ID Game: 82910422 (Open Slot)",
            xPercent = 6,
            yPercent = 28,
            fontSizeSp = 13,
            accentHex = "#FFFFFF",
            backgroundPill = true,
            visible = true
        )
    ),

    // Floating Control & Facecam
    val floatingHudEnabled: Boolean = true,
    val floatingFacecamEnabled: Boolean = true,
    val facecamSizeIndex: Int = 1,
    val hudOpacityPercent: Int = 92,

    // Audio & SoundFX
    val audioMode: AudioCaptureMode = AudioCaptureMode.MIX_GAME_AND_MIC,
    val micGain: Float = 2.0f,
    val gameGain: Float = 1.0f,
    val sfxVolume: Float = 0.85f,
    val noiseGateEnabled: Boolean = true,
    val voiceDuckingEnabled: Boolean = true,
    val echoCancelerEnabled: Boolean = true,

    // Security & Account Protection
    val protectAppWindowFromCapture: Boolean = true,
    val autoWipeStreamKeyAfterLive: Boolean = false,
    val confirmPinBeforeStop: Boolean = true,
    val stopStreamPin: String = "1234",
    val profanityChatFilter: Boolean = true,
    val dndGuardWarning: Boolean = true,

    // Live Event Relay
    val tiktokUsername: String = "",
    val chatWebSocketUrl: String = "wss://distream-studio-cloud.vercel.app/"
) {
    fun buildFullEndpoint(): String {
        val cleanBase = serverUrl.trim().trimEnd('/')
        val cleanKey = streamKey.trim().trimStart('/')
        return "$cleanBase/$cleanKey"
    }
}

enum class LiveEventType(val label: String) {
    CHAT("Chat"),
    GIFT("Gift"),
    DONATION("Saweria"),
    LIKE("Tap Love"),
    FOLLOW("Follow"),
    MARKER("Bookmark"),
    SYSTEM("Sistem")
}

data class LiveFeedEvent(
    val id: Long = System.nanoTime(),
    val type: LiveEventType,
    val senderName: String,
    val message: String,
    val amountValue: Int = 0,
    val timestampLabel: String
)

data class StreamRuntimeState(
    val status: StreamStatus = StreamStatus.IDLE,
    val statusMessage: String = "Siap Siaran",
    val activeScene: StudioScene = StudioScene.GAMEPLAY,
    val isFloatingTestActive: Boolean = false,
    val activeAlertBannerTitle: String = "",
    val activeAlertBannerDetail: String = "",
    val currentBitrateKbps: Int = 0,
    val peakBitrateKbps: Int = 0,
    val estimatedIdrRevenue: Long = 0L,
    val replayClipsSaved: Int = 0,
    val isSimulcastActive: Boolean = false,
    val isCloudFailoverArmed: Boolean = false,
    val hasCongestion: Boolean = false,
    val uptimeSeconds: Long = 0L,
    val subathonSecondsLeft: Int = 3600,
    val currentLikesCount: Int = 2450,
    val topSupporterName: String = "Belum ada",
    val deviceTempCelsius: Float = 36.4f,
    val batteryPercent: Int = 100,
    val isDndActive: Boolean = false,
    val isMicMuted: Boolean = false,
    val isCensorBeepActive: Boolean = false,
    val isPrivacyShieldActive: Boolean = false,
    val isRecordingLocal: Boolean = false,
    val isTorchActive: Boolean = false,
    val activeSfxName: String = "",
    val micLevelNormalized: Float = 0.08f,
    val gameLevelNormalized: Float = 0.04f,
    val sfxLevelNormalized: Float = 0.0f,
    val lastSnapshotName: String = "",
    val markerCount: Int = 0,
    val liveEvents: List<LiveFeedEvent> = emptyList()
)
