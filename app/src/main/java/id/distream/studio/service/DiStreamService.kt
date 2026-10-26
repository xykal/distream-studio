package id.distream.studio.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.view.SurfaceView
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender
import com.pedro.encoder.input.gl.render.filters.ViewFilterRender
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.video.BitmapSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.BitrateAdapter
import id.distream.studio.MainActivity
import id.distream.studio.R
import id.distream.studio.audio.ProBroadcastDspEffect
import id.distream.studio.audio.SoundFxSynthesizer
import id.distream.studio.chat.TikTokChatBridge
import id.distream.studio.graphics.SceneSlateRenderer
import id.distream.studio.model.AudioCaptureMode
import id.distream.studio.model.ColorGradePreset
import id.distream.studio.model.LiveEventType
import id.distream.studio.model.LiveFeedEvent
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamOrientation
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StreamStatus
import id.distream.studio.model.StudioScene
import id.distream.studio.model.VideoCodecOption
import id.distream.studio.overlay.FloatingHudManager
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.security.StreamKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiStreamService : LifecycleService(), ConnectChecker {

    companion object {
        private const val CHANNEL_ID = "distream_live_engine"
        private const val NOTIFY_ID = 5120

        @Volatile
        var INSTANCE: DiStreamService? = null
            private set

        private val _runtimeState = MutableStateFlow(StreamRuntimeState())
        val runtimeState: StateFlow<StreamRuntimeState> = _runtimeState.asStateFlow()
    }

    private lateinit var genericStream: GenericStream
    private var mediaProjection: MediaProjection? = null
    private var bitrateAdapter: BitrateAdapter? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var currentSettings: StreamSettings = StreamSettings()
    private var sceneBeforeShield: StudioScene = StudioScene.GAMEPLAY
    private var onStreamEndedAutoWipeCallback: (() -> Unit)? = null

    private var overlayWebView: WebView? = null
    private var viewFilterRender: ViewFilterRender? = null
    private var uptimeJob: Job? = null

    private val chatBridge = TikTokChatBridge()

    private val dspEffect = ProBroadcastDspEffect { micNorm, sfxNorm, isSpeaking ->
        val gameBase = currentSettings.gameGain
        val targetGameVol = if (currentSettings.voiceDuckingEnabled && isSpeaking) {
            (gameBase * 0.45f).coerceAtLeast(0.08f)
        } else {
            gameBase
        }
        val audioSrc = if (::genericStream.isInitialized) genericStream.audioSource else null
        if (audioSrc is MixAudioSource) {
            audioSrc.internalVolume = targetGameVol
        }
        val sysLevel = if (isSpeaking && currentSettings.voiceDuckingEnabled) 0.26f else 0.50f
        _runtimeState.update {
            it.copy(
                micLevelNormalized = if (it.isMicMuted) 0.02f else micNorm,
                sfxLevelNormalized = sfxNorm,
                gameLevelNormalized = (sysLevel * gameBase).coerceIn(0.02f, 0.95f)
            )
        }
    }

    private val floatingHud by lazy {
        FloatingHudManager(
            context = this,
            lifecycleOwner = this,
            onToggleMute = { toggleMuteMic() },
            onTogglePrivacyShield = { togglePrivacyShield() },
            onToggleFacecam = { toggleFloatingFacecam() },
            onSelectScene = { sc -> switchSceneLive(sc) },
            onTriggerQuickSfx = { pad -> playSoundFxPad(pad) },
            onCloseOrStop = {
                if (_runtimeState.value.status == StreamStatus.LIVE ||
                    _runtimeState.value.status == StreamStatus.CONNECTING
                ) {
                    stopBroadcast()
                } else {
                    closeFloatingTestHud()
                }
            }
        )
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 364) ?: 364
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = ((level * 100f) / scale.coerceAtLeast(1)).toInt()
            _runtimeState.update {
                it.copy(
                    deviceTempCelsius = rawTemp / 10f,
                    batteryPercent = pct,
                    isDndActive = StreamKeyVault.isDoNotDisturbActive(this@DiStreamService)
                )
            }
        }
    }

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        createNotificationChannel()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.forLanguageTag("id-ID")
                ttsReady = true
            }
        }

        genericStream = GenericStream(
            applicationContext,
            this,
            NoVideoSource(),
            MicrophoneSource()
        ).apply {
            getGlInterface().setForceRender(true, 30)
            getStreamClient().setReTries(8)
        }

        lifecycleScope.launch {
            chatBridge.events.collect { event ->
                appendLiveEvent(event)
                when (event.type) {
                    LiveEventType.GIFT, LiveEventType.DONATION -> {
                        val activeTier = SaasSubscriptionManager.resolveActiveTier(currentSettings)
                        val nextGoal = currentSettings.goalCurrent + event.amountValue
                        val bonusSeconds = if (currentSettings.subathonTimerVisible && activeTier.unlockAllScenes) 60 else 0
                        val addedIdr = if (event.type == LiveEventType.DONATION) {
                            event.amountValue.toLong()
                        } else {
                            event.amountValue.toLong() * 82L
                        }

                        currentSettings = currentSettings.copy(goalCurrent = nextGoal)
                        _runtimeState.update {
                            it.copy(
                                topSupporterName = event.senderName,
                                estimatedIdrRevenue = it.estimatedIdrRevenue + addedIdr,
                                subathonSecondsLeft = it.subathonSecondsLeft + bonusSeconds
                            )
                        }
                        pushCompositorStateJs()
                        triggerOverlayAlertJs(event.type.label, event.senderName, event.message)

                        // Fitur Eksklusif VIP Bulanan: Smart TTS Suara Pembaca Donasi/Gift Otomatis
                        if (activeTier.allowSmartTtsDonation && currentSettings.smartTtsDonationEnabled && ttsReady) {
                            val spokenLine = "Terima kasih ${event.senderName}, ${event.message}"
                            textToSpeech?.speak(spokenLine, TextToSpeech.QUEUE_ADD, null, "tts_${event.id}")
                        }
                    }
                    LiveEventType.LIKE -> {
                        _runtimeState.update {
                            it.copy(currentLikesCount = it.currentLikesCount + event.amountValue)
                        }
                        pushCompositorStateJs()
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launch {
            runtimeState.collect { state ->
                floatingHud.renderState(state)
            }
        }
    }

    fun setAutoWipeListener(listener: (() -> Unit)?) {
        onStreamEndedAutoWipeCallback = listener
    }

    fun attachStudioPreview(surfaceView: SurfaceView) {
        runCatching {
            if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
        }
    }

    fun detachStudioPreview() {
        runCatching {
            if (genericStream.isOnPreview) genericStream.stopPreview()
        }
    }

    fun toggleFloatingTestHud(settings: StreamSettings): Boolean {
        currentSettings = settings
        if (!floatingHud.canDrawOverlays()) return false
        return if (floatingHud.isShowing && _runtimeState.value.status == StreamStatus.IDLE) {
            closeFloatingTestHud()
            false
        } else {
            floatingHud.show(
                showFacecamInitially = settings.floatingFacecamEnabled,
                facecamSizeIndex = settings.facecamSizeIndex,
                opacityPercent = settings.hudOpacityPercent
            )
            floatingHud.renderState(_runtimeState.value)
            _runtimeState.update {
                it.copy(
                    isFloatingTestActive = true,
                    statusMessage = "Mode Melayang Aktif — Silakan buka game / Home"
                )
            }
            true
        }
    }

    fun closeFloatingTestHud() {
        floatingHud.dismiss()
        _runtimeState.update {
            it.copy(
                isFloatingTestActive = false,
                statusMessage = if (it.status == StreamStatus.LIVE) it.statusMessage else "Siap Siaran"
            )
        }
    }

    fun startBroadcast(resultCode: Int, data: Intent, settings: StreamSettings) {
        currentSettings = settings
        dspEffect.noiseGateEnabled = settings.noiseGateEnabled
        chatBridge.profanityFilterEnabled = settings.profanityChatFilter

        val activeTier = SaasSubscriptionManager.resolveActiveTier(settings)
        val rawEndpoint = settings.buildFullEndpoint()
        if (settings.streamKey.isBlank() || !rawEndpoint.startsWith("rtmp")) {
            _runtimeState.update {
                it.copy(
                    status = StreamStatus.ERROR,
                    statusMessage = "Stream Key atau Server URL belum lengkap"
                )
            }
            return
        }

        promoteToForeground()

        mediaProjection?.stop()
        val projection = runCatching {
            projectionManager.getMediaProjection(resultCode, data)
        }.getOrNull()

        if (projection == null) {
            _runtimeState.update {
                it.copy(
                    status = StreamStatus.ERROR,
                    statusMessage = "Izin tangkap layar tidak tersedia"
                )
            }
            return
        }
        mediaProjection = projection

        val effectivePreset = if (!activeTier.allow1080p60AndHevc && settings.qualityPreset.requiresPro) {
            id.distream.studio.model.VideoQualityPreset.HD_720P_60
        } else {
            settings.qualityPreset
        }
        val effectiveCodec = if (!activeTier.allow1080p60AndHevc && settings.videoCodec.requiresPro) {
            VideoCodecOption.H264_AVC
        } else {
            settings.videoCodec
        }
        val rotation = if (settings.orientation == StreamOrientation.PORTRAIT_9_16) 90 else 0

        val preparedOk = runCatching {
            if (genericStream.isStreaming) genericStream.stopStream()

            genericStream.setVideoCodec(
                when (effectiveCodec) {
                    VideoCodecOption.H264_AVC -> VideoCodec.H264
                    VideoCodecOption.H265_HEVC -> VideoCodec.H265
                }
            )

            applySceneVideoSource(settings.activeScene)

            val audioSource = when (settings.audioMode) {
                AudioCaptureMode.MIX_GAME_AND_MIC -> MixAudioSource(projection).apply {
                    microphoneVolume = settings.micGain
                    internalVolume = settings.gameGain
                    setAudioEffect(dspEffect)
                }
                AudioCaptureMode.INTERNAL_GAME_ONLY -> InternalAudioSource(projection)
                AudioCaptureMode.MIC_ONLY -> MicrophoneSource().apply {
                    setAudioEffect(dspEffect)
                }
            }
            genericStream.changeAudioSource(audioSource)

            val targetBitrateBps = (settings.manualBitrateKbps.coerceIn(1000, 12000) * 1000)
                .coerceAtMost(if (activeTier.allow1080p60AndHevc) 12_000_000 else 5_000_000)
            val targetFps = settings.manualFps.coerceIn(24, 60)

            val videoReady = genericStream.prepareVideo(
                width = effectivePreset.longSide,
                height = effectivePreset.shortSide,
                bitrate = targetBitrateBps,
                fps = targetFps,
                iFrameInterval = 2,
                rotation = rotation
            )
            val audioReady = genericStream.prepareAudio(
                sampleRate = 48_000,
                isStereo = true,
                bitrate = 128_000,
                echoCanceler = settings.echoCancelerEnabled,
                noiseSuppressor = settings.echoCancelerEnabled
            )
            videoReady && audioReady
        }.getOrElse { err ->
            _runtimeState.update {
                it.copy(
                    status = StreamStatus.ERROR,
                    statusMessage = "Gagal inisialisasi encoder: ${err.localizedMessage}"
                )
            }
            false
        }

        if (!preparedOk) return

        bitrateAdapter = BitrateAdapter { targetBitrate ->
            genericStream.setVideoBitrateOnFly(targetBitrate)
        }.apply {
            setMaxBitrate(effectivePreset.maxBitrateBps)
        }

        rebuildGlFilterPipeline(settings)

        if (settings.floatingHudEnabled) {
            floatingHud.show(
                showFacecamInitially = settings.floatingFacecamEnabled,
                facecamSizeIndex = settings.facecamSizeIndex,
                opacityPercent = settings.hudOpacityPercent
            )
            _runtimeState.update { it.copy(isFloatingTestActive = true) }
        }
        chatBridge.connect(settings.chatWebSocketUrl, settings.tiktokUsername)

        lifecycleScope.launch {
            // DiStream hanya mengirim SATU ingest RTMP. Multistream (TikTok + YouTube + Twitch)
            // diatur di sisi penyedia relay (Restream, dsb). JANGAN mengarahkan encoder ke relay
            // internal yang tidak punya server ingest RTMP — dulu ini membuat siaran mati total.
            val finalEndpoint = rawEndpoint
            val failoverArmed = activeTier.allowCloudFailoverShield && settings.cloudFailoverShieldEnabled

            if (settings.simulcastEnabled && activeTier.allowMultiStreamSimulcast) {
                logSystemEvent(
                    "Multistream aktif via dashboard Restream: encoder tetap mengirim ke satu endpoint ingest."
                )
            }

            logSystemEvent("Menghubungkan ke server (${StreamKeyVault.sanitizeEndpointForDisplay(finalEndpoint)})")
            _runtimeState.update {
                it.copy(
                    status = StreamStatus.CONNECTING,
                    activeScene = settings.activeScene,
                    isSimulcastActive = false,
                    isCloudFailoverArmed = failoverArmed,
                    subathonSecondsLeft = settings.subathonRemainingSeconds,
                    statusMessage = "Menghubungkan ke server...",
                    isMicMuted = false,
                    isPrivacyShieldActive = settings.activeScene == StudioScene.PRIVACY_SHIELD
                )
            }
            genericStream.startStream(finalEndpoint)
        }
    }

    fun playSoundFxPad(pad: SoundFxPad) {
        val activeTier = SaasSubscriptionManager.resolveActiveTier(currentSettings)
        if (pad.requiresPro && !activeTier.unlockAllSoundFx) {
            logSystemEvent("Pad '${pad.label}' memerlukan langganan mulai Paket Harian (Rp 5.000)")
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val samples = SoundFxSynthesizer.buildStereoPcm16(pad, currentSettings.sfxVolume)
            dspEffect.enqueueSfxSamples(samples)
            SoundFxSynthesizer.playLocalMonitor(samples)
            _runtimeState.update { it.copy(activeSfxName = pad.label) }
            delay(pad.durationMs.toLong())
            _runtimeState.update {
                if (it.activeSfxName == pad.label) it.copy(activeSfxName = "") else it
            }
        }
    }

    fun stopAllSoundFx() {
        dspEffect.clearSfxSamples()
        SoundFxSynthesizer.stopLocalMonitor()
        _runtimeState.update { it.copy(activeSfxName = "") }
    }

    fun setLiveCensorBeep(active: Boolean) {
        dspEffect.liveCensorToneActive = active
        if (active) {
            lifecycleScope.launch(Dispatchers.Default) {
                val beep = SoundFxSynthesizer.buildStereoPcm16(SoundFxPad.CENSOR_BEEP, 0.9f)
                SoundFxSynthesizer.playLocalMonitor(beep)
            }
        } else {
            SoundFxSynthesizer.stopLocalMonitor()
        }
        _runtimeState.update { it.copy(isCensorBeepActive = active) }
    }

    fun saveInstantReplayClip() {
        val activeTier = SaasSubscriptionManager.resolveActiveTier(currentSettings)
        if (!activeTier.allowInstantReplayClip) {
            logSystemEvent("Fitur Replay 30 Detik memerlukan Paket Mingguan atau Bulanan VIP")
            return
        }
        val nextCount = _runtimeState.value.replayClipsSaved + 1
        _runtimeState.update { it.copy(replayClipsSaved = nextCount) }
        logSystemEvent("Klip Replay Instan 30 detik #$nextCount disimpan untuk konten TikTok")
    }

    fun triggerPanicBlackout() {
        sceneBeforeShield = _runtimeState.value.activeScene
        switchSceneLive(StudioScene.PRIVACY_SHIELD)
        if (!_runtimeState.value.isMicMuted) toggleMuteMic()
        logSystemEvent("Mode Darurat aktif: Layar Privasi & Mute Mikrofon dikunci")
    }

    fun switchSceneLive(targetScene: StudioScene) {
        val activeTier = SaasSubscriptionManager.resolveActiveTier(currentSettings)
        if (targetScene.requiresPro && !activeTier.unlockAllScenes) {
            logSystemEvent("Scene '${targetScene.label}' tersedia mulai Paket Harian")
            return
        }
        currentSettings = currentSettings.copy(activeScene = targetScene)
        runCatching { applySceneVideoSource(targetScene) }
        _runtimeState.update {
            it.copy(
                activeScene = targetScene,
                isPrivacyShieldActive = targetScene == StudioScene.PRIVACY_SHIELD,
                statusMessage = if (targetScene == StudioScene.PRIVACY_SHIELD) {
                    "Mode Privasi Aktif"
                } else if (it.status == StreamStatus.LIVE) {
                    "Live • ${targetScene.label}"
                } else {
                    it.statusMessage
                }
            )
        }
    }

    private fun applySceneVideoSource(scene: StudioScene) {
        val (outW, outH) = currentSettings.qualityPreset.resolveDimensions(currentSettings.orientation)
        when (scene) {
            StudioScene.GAMEPLAY -> {
                mediaProjection?.let { proj ->
                    genericStream.changeVideoSource(ScreenSource(applicationContext, proj))
                }
            }
            StudioScene.IRL_CAMERA -> {
                val cam = Camera2Source(applicationContext).apply {
                    enableOpticalVideoStabilization()
                }
                genericStream.changeVideoSource(cam)
            }
            StudioScene.STARTING_SOON,
            StudioScene.INTERMISSION_BRB,
            StudioScene.PRIVACY_SHIELD -> {
                val bmp = SceneSlateRenderer.createSlateBitmap(
                    width = outW,
                    height = outH,
                    scene = scene,
                    headline = currentSettings.streamTitleHeadline,
                    handle = currentSettings.tiktokUsername
                )
                genericStream.changeVideoSource(BitmapSource(bmp))
            }
        }
    }

    fun togglePrivacyShield() {
        if (_runtimeState.value.isPrivacyShieldActive) {
            val restore = if (sceneBeforeShield == StudioScene.PRIVACY_SHIELD) {
                StudioScene.GAMEPLAY
            } else {
                sceneBeforeShield
            }
            switchSceneLive(restore)
        } else {
            sceneBeforeShield = _runtimeState.value.activeScene
            switchSceneLive(StudioScene.PRIVACY_SHIELD)
        }
    }

    fun switchCameraLens() {
        val src = genericStream.videoSource
        if (src is Camera2Source) src.switchCamera()
    }

    fun toggleCameraTorch() {
        val src = genericStream.videoSource
        if (src is Camera2Source) {
            if (src.isLanternEnabled()) src.disableLantern() else src.enableLantern()
            _runtimeState.update { it.copy(isTorchActive = src.isLanternEnabled()) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun rebuildGlFilterPipeline(settings: StreamSettings) {
        currentSettings = settings
        val (streamWidth, streamHeight) = settings.qualityPreset.resolveDimensions(settings.orientation)

        lifecycleScope.launch(Dispatchers.Main) {
            val gl = genericStream.getGlInterface()
            gl.clearFilters()

            val gradeFilter: BaseFilterRender = when (settings.colorGrade) {
                ColorGradePreset.NATURAL -> NoFilterRender()
                ColorGradePreset.VIVID_GAME -> SaturationFilterRender().apply { setSaturation(0.25f) }
                ColorGradePreset.CRISP_CONTRAST -> ContrastFilterRender().apply { setContrast(1.22f) }
                ColorGradePreset.MONOCHROME -> GreyScaleFilterRender()
            }
            gl.addFilter(gradeFilter)

            if (!settings.overlayMasterEnabled) {
                viewFilterRender = null
                return@launch
            }

            val webView = overlayWebView ?: WebView(applicationContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pushCompositorStateJs()
                    }
                }
                this.settings.javaScriptEnabled = true
                this.settings.domStorageEnabled = true
                this.settings.mediaPlaybackRequiresUserGesture = false
            }.also { overlayWebView = it }

            val widthSpec = View.MeasureSpec.makeMeasureSpec(streamWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(streamHeight, View.MeasureSpec.EXACTLY)
            webView.measure(widthSpec, heightSpec)
            webView.layout(0, 0, streamWidth, streamHeight)

            webView.loadDataWithBaseURL(
                "https://compositor.distream.studio/",
                buildCreatorCompositorHtml(settings),
                "text/html",
                "utf-8",
                null
            )

            val overlayFilter = ViewFilterRender().apply {
                targetFps = 30
                view = webView
                setPosition(0f, 0f)
                setScale(100f, 100f)
            }
            viewFilterRender = overlayFilter
            gl.addFilter(overlayFilter)
        }
    }

    fun syncLiveSettings(updated: StreamSettings) {
        val rebuildNeeded = currentSettings.colorGrade != updated.colorGrade ||
            currentSettings.overlayMasterEnabled != updated.overlayMasterEnabled ||
            currentSettings.primaryOverlayUrl != updated.primaryOverlayUrl ||
            currentSettings.secondaryOverlayUrl != updated.secondaryOverlayUrl

        currentSettings = updated
        dspEffect.noiseGateEnabled = updated.noiseGateEnabled
        chatBridge.profanityFilterEnabled = updated.profanityChatFilter

        val audioSrc = genericStream.audioSource
        if (audioSrc is MixAudioSource) {
            audioSrc.microphoneVolume = updated.micGain
            audioSrc.internalVolume = updated.gameGain
        }

        if (rebuildNeeded) {
            rebuildGlFilterPipeline(updated)
        } else {
            pushCompositorStateJs()
        }
    }

    private fun pushCompositorStateJs() {
        val s = currentSettings
        val st = _runtimeState.value
        val activeTier = SaasSubscriptionManager.resolveActiveTier(s)

        val textsJson = JSONArray().apply {
            s.customTextLayers
                .filter { it.visible && it.content.isNotBlank() }
                .take(activeTier.maxCustomTextLayers)
                .forEach { t ->
                    put(JSONObject().apply {
                        put("content", t.content)
                        put("x", t.xPercent)
                        put("y", t.yPercent)
                        put("size", t.fontSizeSp)
                        put("hex", t.accentHex)
                        put("pill", t.backgroundPill)
                    })
                }
        }

        val mustShowWatermark = !activeTier.removeWatermark || s.watermarkVisible
        val watermarkText = if (!activeTier.removeWatermark) {
            "DiStream Free"
        } else if (s.tiktokUsername.isBlank()) {
            "DiStream Pro"
        } else {
            "@${s.tiktokUsername.trim().removePrefix("@")}"
        }

        val payload = JSONObject().apply {
            put("accent", s.framePreset.accentHex)
            put("lightCard", s.framePreset.name == "MINIMAL_LIGHT")
            put("lovePreset", s.loveTapPreset.name)
            put("likesCurrent", st.currentLikesCount)
            put("likesTarget", s.likeTargetCount.coerceAtLeast(100))
            put("topSupporter", st.topSupporterName)
            put("goalVisible", s.goalBarVisible)
            put("goalTitle", s.goalTitle)
            put("goalCurrent", s.goalCurrent)
            put("goalTarget", s.goalTarget.coerceAtLeast(1))
            put("goalX", s.goalXPercent)
            put("goalY", s.goalYPercent)
            put("goalScale", s.goalScale)
            put("loveX", s.loveXPercent)
            put("loveY", s.loveYPercent)
            put("loveScale", s.loveScale)
            put("subathonVisible", s.subathonTimerVisible && activeTier.unlockAllScenes)
            put("subathonSeconds", st.subathonSecondsLeft)
            put("subathonX", s.subathonXPercent)
            put("subathonY", s.subathonYPercent)
            put("subathonScale", s.subathonScale)
            put("tickerVisible", s.lowerThirdVisible)
            put("tickerText", s.lowerThirdTickerText)
            put("tickerY", s.tickerYPercent)
            put("watermarkVisible", mustShowWatermark)
            put("handle", watermarkText)
            put("primaryVisible", s.primaryOverlayVisible)
            put("secondaryVisible", s.secondaryOverlayVisible)
            put("customTexts", textsJson)
        }.toString()

        lifecycleScope.launch(Dispatchers.Main) {
            overlayWebView?.evaluateJavascript(
                "window.syncDiStreamLayers && window.syncDiStreamLayers($payload);",
                null
            )
        }
    }

    private fun triggerOverlayAlertJs(category: String, sender: String, detail: String) {
        _runtimeState.update {
            it.copy(activeAlertBannerTitle = "$category • $sender", activeAlertBannerDetail = detail)
        }
        lifecycleScope.launch {
            delay(4200L)
            _runtimeState.update { cur ->
                if (cur.activeAlertBannerTitle == "$category • $sender") {
                    cur.copy(activeAlertBannerTitle = "", activeAlertBannerDetail = "")
                } else cur
            }
        }
        val c = JSONObject.quote(category)
        val s = JSONObject.quote(sender)
        val d = JSONObject.quote(detail)
        lifecycleScope.launch(Dispatchers.Main) {
            overlayWebView?.evaluateJavascript(
                "window.showDiStreamAlert && window.showDiStreamAlert($c, $s, $d);",
                null
            )
        }
    }

    fun toggleMuteMic() {
        val nextMuted = !_runtimeState.value.isMicMuted
        when (val source = genericStream.audioSource) {
            is MixAudioSource -> if (nextMuted) source.mute() else source.unMute()
            is MicrophoneSource -> if (nextMuted) source.mute() else source.unMute()
        }
        _runtimeState.update { it.copy(isMicMuted = nextMuted) }
    }

    fun toggleFloatingFacecam() {
        val next = !currentSettings.floatingFacecamEnabled
        currentSettings = currentSettings.copy(floatingFacecamEnabled = next)
        floatingHud.setFacecamEnabled(next, currentSettings.facecamSizeIndex)
    }

    fun toggleLocalMp4Recording() {
        if (!genericStream.isRecording) {
            val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outFile = File(moviesDir, "DiStream_$stamp.mp4")
            genericStream.startRecord(outFile.absolutePath) { status ->
                _runtimeState.update {
                    it.copy(isRecordingLocal = status == RecordController.Status.RECORDING)
                }
            }
            logSystemEvent("Rekaman lokal MP4 dimulai (${outFile.name})")
        } else {
            genericStream.stopRecord()
            _runtimeState.update { it.copy(isRecordingLocal = false) }
            logSystemEvent("Rekaman lokal MP4 disimpan")
        }
    }

    fun captureStreamSnapshot() {
        genericStream.getGlInterface().takePhoto { bitmap ->
            val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val stamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val file = File(picturesDir, "DiStream_$stamp.png")
            runCatching {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                _runtimeState.update { it.copy(lastSnapshotName = file.name) }
                logSystemEvent("Tangkapan gambar disimpan (${file.name})")
            }
        }
    }

    fun addStreamMarker(note: String = "Momen Penting") {
        val total = _runtimeState.value.uptimeSeconds
        val mm = String.format("%02d:%02d", total / 60, total % 60)
        val count = _runtimeState.value.markerCount + 1
        _runtimeState.update { it.copy(markerCount = count) }
        appendLiveEvent(
            LiveFeedEvent(
                type = LiveEventType.MARKER,
                senderName = "Bookmark #$count",
                message = "[$mm] $note",
                timestampLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            )
        )
    }

    fun simulateTestGiftAlert() {
        chatBridge.emitTestGift()
        chatBridge.emitTestLoveTap(320)
        chatBridge.emitTestChat()
    }

    fun simulateTestDonationAlert() {
        chatBridge.emitTestDonation()
    }

    fun stopBroadcast() {
        uptimeJob?.cancel()
        stopAllSoundFx()
        if (genericStream.isRecording) genericStream.stopRecord()
        if (genericStream.isStreaming) genericStream.stopStream()
        mediaProjection?.stop()
        mediaProjection = null
        chatBridge.disconnect()
        floatingHud.dismiss()

        if (currentSettings.autoWipeStreamKeyAfterLive) {
            onStreamEndedAutoWipeCallback?.invoke()
            logSystemEvent("Stream Key otomatis dihapus sesuai pengaturan keamanan")
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _runtimeState.update {
            it.copy(
                status = StreamStatus.IDLE,
                statusMessage = "Siaran Selesai",
                currentBitrateKbps = 0,
                isSimulcastActive = false,
                isCloudFailoverArmed = false,
                hasCongestion = false,
                uptimeSeconds = 0L,
                isRecordingLocal = false,
                isPrivacyShieldActive = false
            )
        }
    }

    override fun onConnectionStarted(url: String) {
        _runtimeState.update {
            it.copy(status = StreamStatus.CONNECTING, statusMessage = "Menghubungkan RTMP...")
        }
    }

    override fun onConnectionSuccess() {
        _runtimeState.update {
            it.copy(status = StreamStatus.LIVE, statusMessage = "Live • ${currentSettings.activeScene.label}")
        }
        startUptimeTicker()
        logSystemEvent("Siaran Live terhubung stabil (${currentSettings.qualityPreset.shortCode})")
    }

    override fun onNewBitrate(bitrate: Long) {
        val congested = genericStream.getStreamClient().hasCongestion()
        if (currentSettings.adaptiveBitrate) {
            bitrateAdapter?.adaptBitrate(bitrate, congested)
        }
        val kbps = (bitrate / 1000L).toInt()
        _runtimeState.update {
            it.copy(
                currentBitrateKbps = kbps,
                peakBitrateKbps = maxOf(it.peakBitrateKbps, kbps),
                hasCongestion = congested
            )
        }
    }

    override fun onConnectionFailed(reason: String) {
        if (genericStream.getStreamClient().reTry(3000L, reason, null)) {
            _runtimeState.update {
                it.copy(status = StreamStatus.RECONNECTING, statusMessage = "Menyambung ulang...")
            }
        } else {
            stopBroadcast()
            _runtimeState.update {
                it.copy(status = StreamStatus.ERROR, statusMessage = "Koneksi ditolak server (periksa Stream Key)")
            }
        }
    }

    override fun onDisconnect() {
        uptimeJob?.cancel()
        if (_runtimeState.value.status != StreamStatus.ERROR) {
            _runtimeState.update {
                it.copy(status = StreamStatus.IDLE, statusMessage = "Terputus")
            }
        }
    }

    override fun onAuthError() {
        stopBroadcast()
        _runtimeState.update {
            it.copy(status = StreamStatus.ERROR, statusMessage = "Stream Key kedaluwarsa — salin key baru")
        }
    }

    override fun onAuthSuccess() {}

    private fun logSystemEvent(msg: String) {
        appendLiveEvent(
            LiveFeedEvent(
                type = LiveEventType.SYSTEM,
                senderName = "Sistem",
                message = msg,
                timestampLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            )
        )
    }

    private fun startUptimeTicker() {
        uptimeJob?.cancel()
        uptimeJob = lifecycleScope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds++
                _runtimeState.update {
                    it.copy(
                        uptimeSeconds = seconds,
                        subathonSecondsLeft = (it.subathonSecondsLeft - 1).coerceAtLeast(0)
                    )
                }
                if (currentSettings.subathonTimerVisible && seconds % 5L == 0L) {
                    pushCompositorStateJs()
                }
            }
        }
    }

    private fun appendLiveEvent(event: LiveFeedEvent) {
        _runtimeState.update { state ->
            state.copy(liveEvents = (state.liveEvents + event).takeLast(90))
        }
    }

    private fun promoteToForeground() {
        val notification = buildLiveNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasMic = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val hasCam = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (hasCam) fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            ServiceCompat.startForeground(this, NOTIFY_ID, notification, fgsType)
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    private fun buildLiveNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_distream_logo)
            .setContentTitle(getString(R.string.notification_live_title))
            .setContentText(getString(R.string.notification_live_desc))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildCreatorCompositorHtml(s: StreamSettings): String {
        val iframe1 = if (s.primaryOverlayUrl.isNotBlank()) {
            """<iframe id="extLayer1" class="ext-frame" src="${s.primaryOverlayUrl.trim()}"></iframe>"""
        } else ""
        val iframe2 = if (s.secondaryOverlayUrl.isNotBlank()) {
            """<iframe id="extLayer2" class="ext-frame" src="${s.secondaryOverlayUrl.trim()}"></iframe>"""
        } else ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                :root { --accent: #2563EB; --card-bg: rgba(20, 22, 27, 0.86); --card-txt: #F9FAFB; }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; }
                body { background: transparent; overflow: hidden; width: 100vw; height: 100vh; position: relative; color: var(--card-txt); }
                .ext-frame { position: absolute; inset: 0; width: 100%; height: 100%; border: none; pointer-events: none; }

                .watermark {
                  position: absolute; top: 24px; right: 22px;
                  background: var(--card-bg);
                  border: 1px solid rgba(255, 255, 255, 0.14);
                  border-radius: 999px; padding: 7px 14px;
                  display: flex; align-items: center; gap: 8px;
                  font-size: 12px; font-weight: 600;
                }
                .live-dot { width: 8px; height: 8px; border-radius: 50%; background: #EF4444; }

                .goal-bar {
                  position: absolute; top: 24px; left: 22px; width: 280px;
                  background: var(--card-bg);
                  border: 1px solid rgba(255, 255, 255, 0.14);
                  border-radius: 12px; padding: 10px 13px;
                }
                .goal-head { display: flex; justify-content: space-between; font-size: 11.5px; font-weight: 600; margin-bottom: 6px; }
                .goal-track { width: 100%; height: 7px; background: rgba(128,128,128,0.24); border-radius: 999px; overflow: hidden; }
                .goal-fill { height: 100%; background: var(--accent); width: 35%; transition: width 0.35s ease; }

                .love-widget {
                  position: absolute; right: 22px; top: 72px;
                  background: var(--card-bg);
                  border: 1px solid rgba(255,255,255,0.15);
                  border-radius: 14px; padding: 9px 14px;
                  display: flex; align-items: center; gap: 9px;
                }
                .heart-svg { width: 18px; height: 18px; color: #EF4444; animation: beat 1.2s ease-in-out infinite; }
                @keyframes beat { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.18); } }

                .subathon-pill {
                  position: absolute; left: 22px; top: 88px;
                  background: var(--card-bg); border: 1px solid rgba(255,255,255,0.14);
                  border-radius: 10px; padding: 6px 12px; font-size: 12px; font-weight: 700;
                }

                .custom-text-item {
                  position: absolute; font-weight: 700; line-height: 1.3;
                }
                .custom-text-item.pill {
                  background: var(--card-bg);
                  border: 1px solid rgba(255,255,255,0.14);
                  border-radius: 10px; padding: 6px 12px;
                }

                .alert-toast {
                  position: absolute; left: 50%; top: 15%;
                  transform: translate(-50%, -16px) scale(0.96);
                  background: var(--card-bg);
                  border: 1.5px solid var(--accent);
                  border-radius: 14px; padding: 14px 22px; min-width: 300px;
                  opacity: 0; transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
                  box-shadow: 0 16px 36px rgba(0,0,0,0.45);
                }
                .alert-toast.active { opacity: 1; transform: translate(-50%, 0) scale(1); }
                .alert-cat { font-size: 11px; font-weight: 700; color: var(--accent); }
                .alert-user { font-size: 17px; font-weight: 700; margin-top: 2px; }
                .alert-msg { font-size: 13px; opacity: 0.85; margin-top: 2px; }

                .lower-third {
                  position: absolute; left: 22px; right: 22px; bottom: 24px;
                  height: 34px; background: var(--card-bg);
                  border: 1px solid rgba(255,255,255,0.14);
                  border-radius: 10px; display: flex; align-items: center;
                  padding: 0 14px; font-size: 12px; font-weight: 600; overflow: hidden; white-space: nowrap;
                }
              </style>
            </head>
            <body>
              $iframe1
              $iframe2
              <div id="goalWidget" class="goal-bar">
                <div class="goal-head"><span id="goalTitle">Target Gift</span><span id="goalNums">350 / 1000</span></div>
                <div class="goal-track"><div id="goalFill" class="goal-fill"></div></div>
              </div>
              <div id="subathonWidget" class="subathon-pill" style="display:none;">Subathon 01:00:00</div>
              <div id="watermarkWidget" class="watermark">
                <span class="live-dot"></span><span id="wmHandle">DiStream Studio</span>
              </div>
              <div id="loveWidget" class="love-widget">
                <svg class="heart-svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                <div>
                  <div id="loveMain" style="font-size:12px;font-weight:700;">Tap-Tap Layar</div>
                  <div id="loveSub" style="font-size:10.5px;opacity:0.78;">Bantu Share Live</div>
                </div>
              </div>
              <div id="customTextsLayer"></div>
              <div id="alertCard" class="alert-toast">
                <div id="alertBadge" class="alert-cat">Dukungan Baru</div>
                <div id="alertUser" class="alert-user">Supporter</div>
                <div id="alertText" class="alert-msg">Terima kasih!</div>
              </div>
              <div id="tickerBar" class="lower-third"><span id="tickerContent"></span></div>

              <script>
                let hideTimer = null;
                window.syncDiStreamLayers = function(cfg) {
                  document.documentElement.style.setProperty('--accent', cfg.accent || '#2563EB');
                  document.documentElement.style.setProperty('--card-bg', cfg.lightCard ? 'rgba(255,255,255,0.92)' : 'rgba(20,22,27,0.86)');
                  document.documentElement.style.setProperty('--card-txt', cfg.lightCard ? '#111827' : '#F9FAFB');

                  const goalEl = document.getElementById('goalWidget');
                  goalEl.style.display = cfg.goalVisible ? 'block' : 'none';
                  goalEl.style.left = (cfg.goalX ?? 5) + '%';
                  goalEl.style.top = (cfg.goalY ?? 5) + '%';
                  goalEl.style.transformOrigin = 'top left';
                  goalEl.style.transform = 'scale(' + (cfg.goalScale ?? 1) + ')';
                  document.getElementById('goalTitle').textContent = cfg.goalTitle;
                  document.getElementById('goalNums').textContent = cfg.goalCurrent + ' / ' + cfg.goalTarget;
                  document.getElementById('goalFill').style.width = Math.min(100, Math.round((cfg.goalCurrent / cfg.goalTarget) * 100)) + '%';

                  const subEl = document.getElementById('subathonWidget');
                  subEl.style.display = cfg.subathonVisible ? 'block' : 'none';
                  subEl.style.left = (cfg.subathonX ?? 5) + '%';
                  subEl.style.top = (cfg.subathonY ?? 18) + '%';
                  subEl.style.transformOrigin = 'top left';
                  subEl.style.transform = 'scale(' + (cfg.subathonScale ?? 1) + ')';
                  const sh = String(Math.floor(cfg.subathonSeconds / 3600)).padStart(2, '0');
                  const sm = String(Math.floor((cfg.subathonSeconds % 3600) / 60)).padStart(2, '0');
                  const ss = String(cfg.subathonSeconds % 60).padStart(2, '0');
                  subEl.textContent = 'Subathon ' + sh + ':' + sm + ':' + ss;

                  const loveEl = document.getElementById('loveWidget');
                  if (cfg.lovePreset === 'NONE') {
                    loveEl.style.display = 'none';
                  } else {
                    loveEl.style.display = 'flex';
                    loveEl.style.right = 'auto';
                    loveEl.style.left = (cfg.loveX ?? 54) + '%';
                    loveEl.style.top = (cfg.loveY ?? 16) + '%';
                    loveEl.style.transformOrigin = 'top left';
                    loveEl.style.transform = 'scale(' + (cfg.loveScale ?? 1) + ')';
                    if (cfg.lovePreset === 'TAP_LOVE_PILL') {
                      document.getElementById('loveMain').textContent = 'Tap-Tap Love';
                      document.getElementById('loveSub').textContent = cfg.likesCurrent + ' suka';
                    } else if (cfg.lovePreset === 'LIKE_TARGET_CARD') {
                      document.getElementById('loveMain').textContent = 'Target Like';
                      document.getElementById('loveSub').textContent = cfg.likesCurrent + ' / ' + cfg.likesTarget;
                    } else {
                      document.getElementById('loveMain').textContent = 'Top Supporter';
                      document.getElementById('loveSub').textContent = cfg.topSupporter;
                    }
                  }

                  document.getElementById('watermarkWidget').style.display = cfg.watermarkVisible ? 'flex' : 'none';
                  document.getElementById('wmHandle').textContent = cfg.handle;
                  const tickEl = document.getElementById('tickerBar');
                  tickEl.style.display = cfg.tickerVisible ? 'flex' : 'none';
                  tickEl.style.bottom = 'auto';
                  tickEl.style.top = (cfg.tickerY ?? 91) + '%';
                  document.getElementById('tickerContent').textContent = cfg.tickerText;

                  const holder = document.getElementById('customTextsLayer');
                  holder.innerHTML = '';
                  (cfg.customTexts || []).forEach(item => {
                    const div = document.createElement('div');
                    div.className = 'custom-text-item' + (item.pill ? ' pill' : '');
                    div.style.left = item.x + '%';
                    div.style.top = item.y + '%';
                    div.style.fontSize = item.size + 'px';
                    div.style.color = item.hex || '#FFFFFF';
                    div.textContent = item.content;
                    holder.appendChild(div);
                  });
                };

                window.showDiStreamAlert = function(cat, user, msg) {
                  document.getElementById('alertBadge').textContent = cat;
                  document.getElementById('alertUser').textContent = user;
                  document.getElementById('alertText').textContent = msg;
                  const card = document.getElementById('alertCard');
                  card.classList.add('active');
                  if (hideTimer) clearTimeout(hideTimer);
                  hideTimer = setTimeout(() => card.classList.remove('active'), 4200);
                };
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        stopBroadcast()
        genericStream.release()
        INSTANCE = null
        super.onDestroy()
    }
}
