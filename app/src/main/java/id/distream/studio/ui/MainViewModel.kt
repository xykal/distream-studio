package id.distream.studio.ui

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun toggleThemeMode() {
        updateSettings {
            val next = if (it.themeMode == StudioThemeMode.DARK) {
                StudioThemeMode.LIGHT
            } else {
                StudioThemeMode.DARK
            }
            it.copy(themeMode = next)
        }
    }

    fun startSaasCheckout(plan: SaasPlanTier, promoCode: String) {
        viewModelScope.launch {
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
            if (order.status == "completed" && order.licenseToken.isNotBlank()) {
                applyActivatedLicense(order.planTier, order.licenseToken, order.expiresAtMillis)
                _pendingOrder.value = null
                _saasNotice.value = "Trial VIP ${order.planTier.title} berhasil diaktifkan!"
            } else {
                _pendingOrder.value = order
            }
        }
    }

    fun confirmPendingOrderPayment() {
        val order = _pendingOrder.value ?: return
        viewModelScope.launch {
            val settled = withContext(Dispatchers.IO) {
                SaasSubscriptionManager.confirmOrderSettlement(
                    backendBaseUrl = settings.value.saasBackendUrl,
                    pendingOrder = order,
                    deviceId = currentDeviceId()
                )
            }
            if (settled.status == "completed" && settled.licenseToken.isNotBlank()) {
                applyActivatedLicense(settled.planTier, settled.licenseToken, settled.expiresAtMillis)
                _pendingOrder.value = null
                _saasNotice.value = "Langganan ${settled.planTier.title} aktif!"
            }
        }
    }

    fun dismissPendingOrder() {
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
                watermarkVisible = if (tier.removeWatermark) false else true
            )
        }
    }

    fun addCustomTextLayer(text: String) {
        if (text.isBlank()) return
        val activeTier = SaasSubscriptionManager.resolveActiveTier(settings.value)
        if (settings.value.customTextLayers.size >= activeTier.maxCustomTextLayers) {
            _saasNotice.value = "Batas teks paket ${activeTier.title} tercapai (${activeTier.maxCustomTextLayers}). Upgrade ke Pro untuk teks tanpa batas."
            return
        }
        val newItem = CustomTextLayer(
            id = "txt_${System.currentTimeMillis()}",
            content = text.trim(),
            xPercent = 8,
            yPercent = (14 + settings.value.customTextLayers.size * 9).coerceAtMost(72),
            fontSizeSp = 14,
            backgroundPill = true,
            visible = true
        )
        updateSettings { it.copy(customTextLayers = it.customTextLayers + newItem) }
    }

    fun updateCustomTextPosition(id: String, xPercent: Int, yPercent: Int) {
        updateSettings { s ->
            s.copy(
                customTextLayers = s.customTextLayers.map { item ->
                    if (item.id == id) {
                        item.copy(
                            xPercent = xPercent.coerceIn(2, 85),
                            yPercent = yPercent.coerceIn(4, 86)
                        )
                    } else item
                }
            )
        }
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

    fun selectScene(scene: StudioScene) {
        updateSettings { it.copy(activeScene = scene) }
        DiStreamService.INSTANCE?.switchSceneLive(scene)
    }

    fun playSoundFx(pad: SoundFxPad) {
        DiStreamService.INSTANCE?.playSoundFxPad(pad)
    }

    fun stopAllSoundFx() {
        DiStreamService.INSTANCE?.stopAllSoundFx()
    }

    fun setLiveCensorBeep(active: Boolean) {
        DiStreamService.INSTANCE?.setLiveCensorBeep(active)
    }

    fun saveInstantReplayClip() {
        DiStreamService.INSTANCE?.saveInstantReplayClip()
    }

    fun triggerPanicBlackout() {
        DiStreamService.INSTANCE?.triggerPanicBlackout()
    }

    fun togglePrivacyShield() {
        DiStreamService.INSTANCE?.togglePrivacyShield()
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
        DiStreamService.INSTANCE?.switchCameraLens()
    }

    fun toggleTorch() {
        DiStreamService.INSTANCE?.toggleCameraTorch()
    }

    fun addGoalProgress(delta: Int) {
        updateSettings { it.copy(goalCurrent = (it.goalCurrent + delta).coerceAtLeast(0)) }
    }

    fun resetGoalProgress() {
        updateSettings { it.copy(goalCurrent = 0) }
    }

    fun reloadCompositor() {
        DiStreamService.INSTANCE?.rebuildGlFilterPipeline(settings.value)
    }

    fun triggerTestGift() {
        DiStreamService.INSTANCE?.simulateTestGiftAlert()
    }

    fun triggerTestDonation() {
        DiStreamService.INSTANCE?.simulateTestDonationAlert()
    }

    fun stopLiveStream() {
        DiStreamService.INSTANCE?.stopBroadcast()
    }
}
