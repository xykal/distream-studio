package id.distream.studio.ui

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.distream.studio.audio.SoundFxSynthesizer
import id.distream.studio.data.StreamPreferencesRepository
import id.distream.studio.model.CustomTextLayer
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StudioScene
import id.distream.studio.model.StudioThemeMode
import id.distream.studio.saas.SaasCheckoutOrder
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.service.DiStreamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appCtx = application.applicationContext
    private val repo = StreamPreferencesRepository(appCtx)

    val settings: StateFlow<StreamSettings> = repo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StreamSettings()
    )

    val runtimeState: StateFlow<StreamRuntimeState> = DiStreamService.runtimeState

    private val _pendingOrder = MutableStateFlow<SaasCheckoutOrder?>(null)
    val pendingOrder: StateFlow<SaasCheckoutOrder?> = _pendingOrder.asStateFlow()

    private val _saasNotice = MutableStateFlow("")
    val saasNotice: StateFlow<String> = _saasNotice.asStateFlow()

    private val _localPreviewAlert = MutableStateFlow("")
    val localPreviewAlert: StateFlow<String> = _localPreviewAlert.asStateFlow()

    private val _isCreatingOrder = MutableStateFlow(false)
    val isCreatingOrder: StateFlow<Boolean> = _isCreatingOrder.asStateFlow()

    private var orderPollJob: Job? = null
    private var localTts: TextToSpeech? = null
    private var localTtsReady: Boolean = false

    init {
        localTts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.forLanguageTag("id-ID")
                localTtsReady = true
            }
        }
    }

    override fun onCleared() {
        orderPollJob?.cancel()
        localTts?.stop()
        localTts?.shutdown()
        super.onCleared()
    }

    @SuppressLint("HardwareIds")
    private fun currentDeviceId(): String =
        Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"

    fun bindServiceCallbacks() {
        DiStreamService.INSTANCE?.setAutoWipeListener {
            updateSettings { it.copy(streamKey = "") }
        }
    }

    fun updateSettings(transform: (StreamSettings) -> StreamSettings) {
        val updated = transform(settings.value)
        viewModelScope.launch {
            repo.saveSettings(updated)
        }
        DiStreamService.INSTANCE?.syncLiveSettings(updated)
    }

    fun cycleThemeMode() {
        updateSettings { cur ->
            val activeTier = SaasSubscriptionManager.resolveActiveTier(cur)
            val next = when (cur.themeMode) {
                StudioThemeMode.DARK -> StudioThemeMode.LIGHT
                StudioThemeMode.LIGHT -> if (activeTier != SaasPlanTier.FREE) {
                    StudioThemeMode.ROYAL_GOLD
                } else {
                    StudioThemeMode.DARK
                }
                StudioThemeMode.ROYAL_GOLD -> StudioThemeMode.DARK
            }
            cur.copy(themeMode = next)
        }
    }

    fun startSaasCheckout(plan: SaasPlanTier, promoCode: String) {
        if (_isCreatingOrder.value) return
        viewModelScope.launch {
            _isCreatingOrder.value = true
            _saasNotice.value = "Menghubungkan ke server Pakasir API v2..."
            val current = settings.value
            val order = withContext(Dispatchers.IO) {
                SaasSubscriptionManager.createCheckoutSession(
                    backendBaseUrl = current.saasBackendUrl,
                    tier = plan,
                    deviceId = currentDeviceId(),
                    promoCode = promoCode,
                    email = current.subscriberEmail
                )
            }
            _isCreatingOrder.value = false
            if (order.status == "completed" && order.licenseToken.isNotBlank()) {
                applyActivatedLicense(order.planTier, order.licenseToken, order.expiresAtMillis)
                _pendingOrder.value = null
                _saasNotice.value = "Lisensi ${order.planTier.title} langsung aktif!"
            } else {
                _pendingOrder.value = order
                _saasNotice.value = if (order.gatewayLive) {
                    "Invoice QRIS Pakasir #${order.txnId.ifBlank { order.orderId }} siap dipindai"
                } else {
                    "Invoice lokal dibuat — periksa koneksi internet jika ingin QRIS live"
                }
                startLiveOrderPolling(order)
            }
        }
    }

    private fun startLiveOrderPolling(initialOrder: SaasCheckoutOrder) {
        orderPollJob?.cancel()
        orderPollJob = viewModelScope.launch {
            var current = initialOrder
            while (isActive && _pendingOrder.value?.orderId == current.orderId) {
                delay(4500L)
                val polled = withContext(Dispatchers.IO) {
                    SaasSubscriptionManager.pollOrderStatus(settings.value.saasBackendUrl, current)
                }
                current = polled
                if (polled.status == "completed" && polled.licenseToken.isNotBlank()) {
                    applyActivatedLicense(polled.planTier, polled.licenseToken, polled.expiresAtMillis)
                    _pendingOrder.value = null
                    _saasNotice.value = "Pembayaran QRIS terverifikasi otomatis! ${polled.planTier.title} Aktif."
                    break
                }
            }
        }
    }

    fun confirmPendingOrderPayment() {
        val order = _pendingOrder.value ?: return
        viewModelScope.launch {
            _saasNotice.value = "Memverifikasi status transaksi ke server Pakasir..."
            val settled = withContext(Dispatchers.IO) {
                SaasSubscriptionManager.confirmOrderSettlement(
                    backendBaseUrl = settings.value.saasBackendUrl,
                    pendingOrder = order,
                    deviceId = currentDeviceId()
                )
            }
            if (settled.status == "completed" && settled.licenseToken.isNotBlank()) {
                orderPollJob?.cancel()
                applyActivatedLicense(settled.planTier, settled.licenseToken, settled.expiresAtMillis)
                _pendingOrder.value = null
                _saasNotice.value = "Langganan ${settled.planTier.title} terverifikasi & aktif!"
            } else {
                // Lisensi tidak pernah diterbitkan offline lagi: pembayaran harus diverifikasi server.
                _saasNotice.value =
                    "Pembayaran belum terverifikasi. Sambungkan internet lalu cek ulang status QRIS."
            }
        }
    }

    fun dismissPendingOrder() {
        orderPollJob?.cancel()
        _pendingOrder.value = null
    }

    fun redeemManualLicenseToken(rawToken: String) {
        val check = SaasSubscriptionManager.verifySignedToken(rawToken.trim())
        if (check.valid) {
            applyActivatedLicense(check.tier, rawToken.trim(), check.expiresAtMillis)
            _saasNotice.value = "Kode lisensi ${check.tier.title} valid & aktif!"
        } else {
            _saasNotice.value = "Token tidak valid (${check.reasonCode})"
        }
    }

    private fun applyActivatedLicense(tier: SaasPlanTier, token: String, expiresAtMs: Long) {
        updateSettings {
            it.copy(
                saasTier = tier,
                saasLicenseToken = token,
                saasExpiresAtMillis = expiresAtMs,
                watermarkVisible = !tier.removeWatermark
            )
        }
    }

    fun addCustomTextLayer(text: String) {
        if (text.isBlank()) return
        val activeTier = SaasSubscriptionManager.resolveActiveTier(settings.value)
        if (settings.value.customTextLayers.size >= activeTier.maxCustomTextLayers) {
            _saasNotice.value = "Batas teks paket ${activeTier.title} tercapai (${activeTier.maxCustomTextLayers}). Upgrade Pro untuk teks tanpa batas."
            return
        }
        val newItem = CustomTextLayer(
            id = "txt_${System.currentTimeMillis()}",
            content = text.trim(),
            xPercent = 8,
            yPercent = (24 + settings.value.customTextLayers.size * 10).coerceAtMost(78),
            fontSizeSp = 14,
            accentHex = "#FFFFFF",
            backgroundPill = true,
            visible = true
        )
        updateSettings { it.copy(customTextLayers = it.customTextLayers + newItem) }
    }

    fun updateCustomTextLayer(
        id: String,
        content: String? = null,
        xPercent: Int? = null,
        yPercent: Int? = null,
        fontSizeSp: Int? = null,
        accentHex: String? = null,
        backgroundPill: Boolean? = null
    ) {
        updateSettings { s ->
            s.copy(
                customTextLayers = s.customTextLayers.map { item ->
                    if (item.id == id) {
                        item.copy(
                            content = content ?: item.content,
                            xPercent = (xPercent ?: item.xPercent).coerceIn(2, 88),
                            yPercent = (yPercent ?: item.yPercent).coerceIn(3, 90),
                            fontSizeSp = (fontSizeSp ?: item.fontSizeSp).coerceIn(10, 30),
                            accentHex = accentHex ?: item.accentHex,
                            backgroundPill = backgroundPill ?: item.backgroundPill
                        )
                    } else item
                }
            )
        }
    }

    fun updateCustomTextPosition(id: String, xPercent: Int, yPercent: Int) {
        updateCustomTextLayer(id = id, xPercent = xPercent, yPercent = yPercent)
    }

    fun toggleCustomTextVisibility(id: String) {
        updateSettings { s ->
            s.copy(
                customTextLayers = s.customTextLayers.map { item ->
                    if (item.id == id) item.copy(visible = !item.visible) else item
                }
            )
        }
    }

    fun removeCustomTextLayer(id: String) {
        updateSettings { s ->
            s.copy(customTextLayers = s.customTextLayers.filterNot { it.id == id })
        }
    }

    fun resetAllOverlayPositions() {
        updateSettings { s ->
            s.copy(
                goalXPercent = 5f,
                goalYPercent = 5f,
                goalScale = 1.0f,
                loveXPercent = 54f,
                loveYPercent = 16f,
                loveScale = 1.0f,
                subathonXPercent = 5f,
                subathonYPercent = 18f,
                subathonScale = 1.0f,
                facecamXPercent = 64f,
                facecamYPercent = 58f,
                facecamScale = 1.0f,
                tickerYPercent = 91f,
                backgroundXPercent = 0f,
                backgroundYPercent = 0f,
                backgroundScale = 1.0f
            )
        }
    }

    fun selectScene(scene: StudioScene) {
        updateSettings { it.copy(activeScene = scene) }
        DiStreamService.INSTANCE?.switchSceneLive(scene)
    }

    fun toggleFloatingTestWindow(): Boolean {
        val svc = DiStreamService.INSTANCE ?: return false
        return svc.toggleFloatingTestHud(settings.value)
    }

    fun playSoundFx(pad: SoundFxPad) {
        val cur = settings.value
        val activeTier = SaasSubscriptionManager.resolveActiveTier(cur)
        if (pad.requiresPro && !activeTier.unlockAllSoundFx) {
            _saasNotice.value = "Pad '${pad.label}' terbuka mulai Paket Harian (Rp 5.000)"
            return
        }
        val svc = DiStreamService.INSTANCE
        if (svc != null) {
            svc.playSoundFxPad(pad)
        } else {
            viewModelScope.launch(Dispatchers.Default) {
                val pcm = SoundFxSynthesizer.buildStereoPcm16(pad, cur.sfxVolume)
                SoundFxSynthesizer.playLocalMonitor(pcm)
            }
        }
    }

    fun stopAllSoundFx() {
        SoundFxSynthesizer.stopLocalMonitor()
        DiStreamService.INSTANCE?.stopAllSoundFx()
    }

    fun setLiveCensorBeep(active: Boolean) {
        val svc = DiStreamService.INSTANCE
        if (svc != null) {
            svc.setLiveCensorBeep(active)
        } else if (active) {
            viewModelScope.launch(Dispatchers.Default) {
                val pcm = SoundFxSynthesizer.buildStereoPcm16(SoundFxPad.CENSOR_BEEP, 0.9f)
                SoundFxSynthesizer.playLocalMonitor(pcm)
            }
        } else {
            SoundFxSynthesizer.stopLocalMonitor()
        }
    }

    fun saveInstantReplayClip() {
        DiStreamService.INSTANCE?.saveInstantReplayClip()
    }

    fun toggleReplayBuffer() {
        DiStreamService.INSTANCE?.toggleReplayBuffer()
    }

    fun saveReplayBufferClip() {
        DiStreamService.INSTANCE?.saveReplayBufferClip()
    }

    fun triggerPanicBlackout() {
        updateSettings { it.copy(activeScene = StudioScene.PRIVACY_SHIELD) }
        DiStreamService.INSTANCE?.triggerPanicBlackout()
    }

    fun togglePrivacyShield() {
        val svc = DiStreamService.INSTANCE
        if (svc != null) {
            svc.togglePrivacyShield()
        } else {
            updateSettings {
                val next = if (it.activeScene == StudioScene.PRIVACY_SHIELD) {
                    StudioScene.GAMEPLAY
                } else {
                    StudioScene.PRIVACY_SHIELD
                }
                it.copy(activeScene = next)
            }
        }
    }

    fun stopLiveStream() {
        DiStreamService.INSTANCE?.stopBroadcast()
    }

    fun toggleMicMute() {
        DiStreamService.INSTANCE?.toggleMuteMic()
    }

    fun toggleLocalRecord() {
        DiStreamService.INSTANCE?.toggleLocalMp4Recording()
    }

    fun captureSnapshot() {
        DiStreamService.INSTANCE?.captureStreamSnapshot()
    }

    fun addStreamMarker() {
        DiStreamService.INSTANCE?.addStreamMarker("Momen Penting")
    }

    fun switchCameraLens() {
        updateSettings { it.copy(cameraFrontLens = !it.cameraFrontLens) }
        DiStreamService.INSTANCE?.switchCameraLens()
    }

    fun toggleTorch() {
        DiStreamService.INSTANCE?.toggleCameraTorch()
    }

    fun addGoalProgress(delta: Int) {
        updateSettings { it.copy(goalCurrent = (it.goalCurrent + delta).coerceAtLeast(0)) }
    }

    fun reloadCompositor() {
        DiStreamService.INSTANCE?.rebuildGlFilterPipeline(settings.value)
    }

    fun triggerTestGift() {
        playSoundFx(SoundFxPad.COIN_ALERT)
        addGoalProgress(50)
        val svc = DiStreamService.INSTANCE
        if (svc != null) {
            svc.simulateTestGiftAlert()
        } else {
            showLocalStageBanner("GIFT PAUS • SultanBudi99", "Mengirim Gift Paus Samudra (+50 Poin Target)")
        }
    }

    fun triggerTestDonation() {
        playSoundFx(SoundFxPad.VICTORY.takeIf {
            SaasSubscriptionManager.resolveActiveTier(settings.value).unlockAllSoundFx
        } ?: SoundFxPad.COIN_ALERT)
        addGoalProgress(100)
        val cur = settings.value
        val activeTier = SaasSubscriptionManager.resolveActiveTier(cur)
        if (activeTier.allowSmartTtsDonation && cur.smartTtsDonationEnabled && localTtsReady) {
            localTts?.speak(
                "Terima kasih Kak Raka atas saweria lima puluh ribu rupiah. Semangat live malam ini!",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "preview_tts"
            )
        }
        val svc = DiStreamService.INSTANCE
        if (svc != null) {
            svc.simulateTestDonationAlert()
        } else {
            showLocalStageBanner("SAWERIA RP 50.000 • Kak Raka", "Semangat push rank malam ini!")
        }
    }

    private fun showLocalStageBanner(title: String, detail: String) {
        viewModelScope.launch {
            _localPreviewAlert.value = "$title|$detail"
            delay(4200L)
            _localPreviewAlert.value = ""
        }
    }
}
