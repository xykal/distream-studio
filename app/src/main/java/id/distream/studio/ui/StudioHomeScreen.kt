package id.distream.studio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.distream.studio.model.AudioCaptureMode
import id.distream.studio.model.ColorGradePreset
import id.distream.studio.model.LoveTapPreset
import id.distream.studio.model.OverlayFramePreset
import id.distream.studio.model.RtmpDestinationPreset
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StreamStatus
import id.distream.studio.model.StudioScene
import id.distream.studio.model.VideoCodecOption
import id.distream.studio.model.VideoQualityPreset
import id.distream.studio.saas.SaasCheckoutOrder
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.security.StreamKeyVault
import id.distream.studio.ui.theme.LocalDiStreamPalette
import kotlin.math.roundToInt

private enum class StudioDeckTab(val title: String) {
    PRESETS("Preset & Video"),
    OVERLAY("Overlay & Frame"),
    AUDIO("SoundFX & Mic"),
    FLOATING("Melayang & Event"),
    VAULT_SAAS("Keamanan & Pro")
}

private fun formatRupiah(amount: Int): String {
    if (amount <= 0) return "Gratis"
    val raw = amount.toString()
    val sb = StringBuilder("Rp ")
    raw.forEachIndexed { idx, c ->
        sb.append(c)
        val rem = raw.length - 1 - idx
        if (rem > 0 && rem % 3 == 0) sb.append('.')
    }
    return sb.toString()
}

@Composable
fun StudioHomeScreen(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    pendingOrder: SaasCheckoutOrder?,
    saasNotice: String,
    localAlertBanner: String,
    isCreatingOrder: Boolean,
    hasOverlayPermission: Boolean,
    hasCameraPermission: Boolean,
    onToggleTheme: () -> Unit,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onStartCheckout: (SaasPlanTier, String) -> Unit,
    onConfirmOrderPaid: () -> Unit,
    onDismissOrder: () -> Unit,
    onRedeemLicenseToken: (String) -> Unit,
    onSaveReplayClip: () -> Unit,
    onAddCustomText: (String) -> Unit,
    onUpdateCustomText: (id: String, content: String?, x: Int?, y: Int?, size: Int?, hex: String?, pill: Boolean?) -> Unit,
    onDeleteCustomText: (String) -> Unit,
    onResetOverlayLayout: () -> Unit,
    onSelectScene: (StudioScene) -> Unit,
    onPlaySoundFx: (SoundFxPad) -> Unit,
    onStopAllSoundFx: () -> Unit,
    onToggleCensorBeep: (Boolean) -> Unit,
    onTriggerPanic: () -> Unit,
    onToggleFloatingTest: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onStartLiveClick: () -> Unit,
    onStopLiveClick: () -> Unit,
    onToggleMuteClick: () -> Unit,
    onToggleShieldClick: () -> Unit,
    onToggleRecordClick: () -> Unit,
    onCaptureSnapshotClick: () -> Unit,
    onAddMarkerClick: () -> Unit,
    onSwitchCameraClick: () -> Unit,
    onToggleTorchClick: () -> Unit,
    onAddGoalProgress: (Int) -> Unit,
    onReloadOverlayClick: () -> Unit,
    onTestGiftClick: () -> Unit,
    onTestDonationClick: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    val context = LocalContext.current
    val activeTier = SaasSubscriptionManager.resolveActiveTier(settings)
    var currentTab by remember { mutableStateOf(StudioDeckTab.PRESETS) }
    var showSaasModal by remember { mutableStateOf(false) }
    var showStopPinModal by remember { mutableStateOf(false) }
    var enteredStopPin by remember { mutableStateOf("") }
    var canvasExpanded by remember { mutableStateOf(true) }

    val isLiveOrConnecting = runtimeState.status == StreamStatus.LIVE ||
        runtimeState.status == StreamStatus.CONNECTING ||
        runtimeState.status == StreamStatus.RECONNECTING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. HEADER STUDIO CUSTOM (NON-MATERIAL, TACTILE CREATOR BAR)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surfaceLow)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                DsLogoMark(size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DsText("DiStream Studio", size = 15.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (activeTier == SaasPlanTier.FREE) p.surfaceHigh
                                    else Color(0xFFD97706).copy(alpha = 0.22f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            DsText(
                                text = activeTier.badge,
                                size = 9.5.sp,
                                weight = FontWeight.Bold,
                                color = if (activeTier == SaasPlanTier.FREE) p.textSecondary else Color(0xFFFBBF24)
                            )
                        }
                    }
                    DsText(
                        text = runtimeState.statusMessage,
                        size = 11.sp,
                        color = if (isLiveOrConnecting) p.okEmerald else p.textMuted,
                        maxLines = 1
                    )
                }

                DsButton(
                    label = if (runtimeState.isFloatingTestActive) "Tutup Melayang" else "Tes Melayang",
                    compact = true,
                    active = runtimeState.isFloatingTestActive,
                    onClick = {
                        if (!hasOverlayPermission) onRequestOverlayPermission()
                        else onToggleFloatingTest()
                    }
                )
                Spacer(Modifier.width(6.dp))
                DsButton(
                    label = settings.themeMode.label.substringBefore(" "),
                    compact = true,
                    onClick = onToggleTheme
                )
                Spacer(Modifier.width(6.dp))
                DsButton(
                    label = if (activeTier == SaasPlanTier.FREE) "Pro" else "VIP",
                    compact = true,
                    variant = DsBtnVariant.VIP_GOLD,
                    onClick = { showSaasModal = true }
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(p.borderSubtle))

            // 2. ISI UTAMA SCROLLABLE: LIVE WYSIWYG STAGE + DECK KONTROL KUSTOM
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                if (saasNotice.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.accentPrimary.copy(alpha = 0.14f))
                            .border(1.dp, p.accentPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        DsText(saasNotice, size = 11.5.sp, color = p.accentSecondary)
                    }
                }

                // Toggle Ringkas / Penuh Panggung Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsText(
                        "PANGGUNG LIVE INTERAKTIF (GESER & EDIT LANGSUNG)",
                        size = 10.5.sp,
                        weight = FontWeight.Bold,
                        color = p.textMuted
                    )
                    DsButton(
                        label = if (canvasExpanded) "Sembunyikan Preview" else "Buka Preview Kanvas",
                        compact = true,
                        onClick = { canvasExpanded = !canvasExpanded }
                    )
                }

                if (canvasExpanded) {
                    LiveStudioCanvasStage(
                        settings = settings,
                        runtimeState = runtimeState,
                        activeTier = activeTier,
                        localAlertBanner = localAlertBanner,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = onRequestCameraPermission,
                        onUpdateSettings = onUpdateSettings,
                        onSelectScene = onSelectScene,
                        onAddCustomText = onAddCustomText,
                        onUpdateCustomText = onUpdateCustomText,
                        onDeleteCustomText = onDeleteCustomText,
                        onResetLayout = onResetOverlayLayout,
                        onSwitchLens = onSwitchCameraClick
                    )
                }

                // NAVIGASI TAB STUDIO KUSTOM
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudioDeckTab.entries.forEach { tab ->
                        DsButton(
                            label = tab.title,
                            compact = true,
                            active = currentTab == tab,
                            onClick = { currentTab = tab }
                        )
                    }
                }

                // ISI TAB AKTIF
                when (currentTab) {
                    StudioDeckTab.PRESETS -> PresetsAndEncoderDeck(
                        settings = settings,
                        activeTier = activeTier,
                        onUpdateSettings = onUpdateSettings,
                        onOpenUpgrade = { showSaasModal = true }
                    )
                    StudioDeckTab.OVERLAY -> OverlayAndCompositorDeck(
                        settings = settings,
                        runtimeState = runtimeState,
                        activeTier = activeTier,
                        onUpdateSettings = onUpdateSettings,
                        onAddGoalProgress = onAddGoalProgress,
                        onReloadOverlayClick = onReloadOverlayClick,
                        onOpenUpgrade = { showSaasModal = true }
                    )
                    StudioDeckTab.AUDIO -> AudioAndSoundFxDeck(
                        settings = settings,
                        runtimeState = runtimeState,
                        activeTier = activeTier,
                        onUpdateSettings = onUpdateSettings,
                        onPlaySoundFx = onPlaySoundFx,
                        onStopAllSoundFx = onStopAllSoundFx,
                        onToggleCensorBeep = onToggleCensorBeep,
                        onOpenUpgrade = { showSaasModal = true }
                    )
                    StudioDeckTab.FLOATING -> FloatingAndLiveEventDeck(
                        settings = settings,
                        runtimeState = runtimeState,
                        hasOverlayPermission = hasOverlayPermission,
                        onUpdateSettings = onUpdateSettings,
                        onToggleFloatingTest = onToggleFloatingTest,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onTestGiftClick = onTestGiftClick,
                        onTestDonationClick = onTestDonationClick,
                        onSaveReplayClip = onSaveReplayClip,
                        onToggleRecordClick = onToggleRecordClick,
                        onCaptureSnapshotClick = onCaptureSnapshotClick,
                        onAddMarkerClick = onAddMarkerClick,
                        onToggleTorchClick = onToggleTorchClick
                    )
                    StudioDeckTab.VAULT_SAAS -> SecurityAndProCloudDeck(
                        settings = settings,
                        runtimeState = runtimeState,
                        activeTier = activeTier,
                        onUpdateSettings = onUpdateSettings,
                        onOpenUpgrade = { showSaasModal = true },
                        onRedeemToken = onRedeemLicenseToken,
                        onTriggerPanic = onTriggerPanic
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // 3. BOTTOM MASTER BROADCAST ACTION BAR (MULAI LIVE / STOP / MUTE / PRIVASI / MELAYANG)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(p.borderSubtle))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surfaceLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                DsButton(
                    label = if (runtimeState.isMicMuted) "Mic Off" else "Mic On",
                    compact = true,
                    active = !runtimeState.isMicMuted,
                    onClick = onToggleMuteClick
                )
                DsButton(
                    label = "Privasi",
                    compact = true,
                    active = runtimeState.isPrivacyShieldActive,
                    onClick = onToggleShieldClick
                )
                if (!isLiveOrConnecting) {
                    DsButton(
                        label = "MULAI SIARAN LIVE",
                        variant = DsBtnVariant.LIVE_DANGER,
                        modifier = Modifier.weight(1f),
                        onClick = onStartLiveClick
                    )
                } else {
                    val clock = String.format(
                        "%02d:%02d",
                        runtimeState.uptimeSeconds / 60,
                        runtimeState.uptimeSeconds % 60
                    )
                    DsButton(
                        label = "AKHIRI LIVE ($clock)",
                        variant = DsBtnVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (settings.confirmPinBeforeStop) {
                                enteredStopPin = ""
                                showStopPinModal = true
                            } else {
                                onStopLiveClick()
                            }
                        }
                    )
                }
            }
        }

        // MODAL PIN ANTI-SALAH PENCET STOP LIVE
        if (showStopPinModal) {
            DsModalScrim(onDismiss = { showStopPinModal = false }) {
                DsText("Konfirmasi PIN Pengaman Siaran", size = 15.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                DsText(
                    "Masukkan PIN pengaman (${settings.stopStreamPin.length} digit) untuk menghentikan siaran live.",
                    size = 12.sp,
                    color = p.textMuted
                )
                Spacer(Modifier.height(12.dp))
                DsInputField(
                    label = "PIN Pengaman",
                    value = enteredStopPin,
                    placeholder = "Masukkan PIN (default: 1234)",
                    onValueChange = { enteredStopPin = it },
                    isSecret = true,
                    keyboardType = KeyboardType.NumberPassword
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DsButton("Batal", modifier = Modifier.weight(1f), onClick = { showStopPinModal = false })
                    DsButton(
                        label = "Hentikan Siaran",
                        variant = DsBtnVariant.LIVE_DANGER,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (enteredStopPin == settings.stopStreamPin) {
                                showStopPinModal = false
                                onStopLiveClick()
                            }
                        }
                    )
                }
            }
        }

        // MODAL PAKASIR API v2 REAL QRIS CHECKOUT & LANGGANAN PRO
        if (showSaasModal || pendingOrder != null) {
            SaasCheckoutSheetModal(
                activeTier = activeTier,
                settings = settings,
                pendingOrder = pendingOrder,
                isCreatingOrder = isCreatingOrder,
                onUpdateEmail = { em -> onUpdateSettings { it.copy(subscriberEmail = em) } },
                onStartCheckout = onStartCheckout,
                onOpenBrowserCheckout = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onConfirmPaid = onConfirmOrderPaid,
                onClose = {
                    showSaasModal = false
                    onDismissOrder()
                }
            )
        }
    }
}

@Composable
private fun PresetsAndEncoderDeck(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onOpenUpgrade: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    DsPanel {
        DsText("Preset Kualitas Cepat & Kontrol Encoder Penuh", size = 14.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        DsText(
            "Pilih preset cepat 1-ketuk atau atur manual resolusi, FPS, bitrate, codec, dan warna video seperti OBS Studio.",
            size = 11.5.sp,
            color = p.textMuted
        )
        Spacer(Modifier.height(10.dp))

        // Preset Kualitas Video
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoQualityPreset.entries.forEach { preset ->
                val locked = preset.requiresPro && !activeTier.allow1080p60AndHevc
                DsButton(
                    label = preset.label + if (locked) " [Buka Pro]" else "",
                    active = settings.qualityPreset == preset,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (locked) onOpenUpgrade()
                        else onUpdateSettings {
                            it.copy(
                                qualityPreset = preset,
                                manualBitrateKbps = preset.maxBitrateBps / 1000,
                                manualFps = preset.fps
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Fader Bitrate Manual & Pilihan FPS Presisi
        DsFader(
            label = "Bitrate Video Manual",
            value = settings.manualBitrateKbps.toFloat(),
            valueRange = 1200f..10000f,
            formattedValue = String.format("%.1f Mbps (%d kbps)", settings.manualBitrateKbps / 1000f, settings.manualBitrateKbps),
            onValueChange = { kbps -> onUpdateSettings { it.copy(manualBitrateKbps = kbps.roundToInt()) } }
        )

        Spacer(Modifier.height(8.dp))
        DsText("Frame Rate (FPS)", size = 12.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(24, 30, 45, 60).forEach { fps ->
                DsButton(
                    label = "$fps FPS",
                    compact = true,
                    active = settings.manualFps == fps,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateSettings { it.copy(manualFps = fps) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsText("Codec Hardware GPU", size = 12.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            VideoCodecOption.entries.forEach { codec ->
                val locked = codec.requiresPro && !activeTier.allow1080p60AndHevc
                DsButton(
                    label = codec.label,
                    compact = true,
                    active = settings.videoCodec == codec,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (locked) onOpenUpgrade() else onUpdateSettings { it.copy(videoCodec = codec) }
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsText("Filter Warna Kamera & Layar (Color Grading)", size = 12.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ColorGradePreset.entries.forEach { grade ->
                DsButton(
                    label = grade.label,
                    compact = true,
                    active = settings.colorGrade == grade,
                    onClick = { onUpdateSettings { it.copy(colorGrade = grade) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsToggle(
            title = "Adaptive Bitrate Otomatis (Anti-Freeze)",
            subtitle = "Turunkan bitrate mulus saat sinyal drop tanpa memutus koneksi live",
            checked = settings.adaptiveBitrate,
            onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveBitrate = v) } }
        )

        Spacer(Modifier.height(10.dp))
        DsInputField(
            label = "Judul Siaran / Layar Pembuka & BRB",
            value = settings.streamTitleHeadline,
            placeholder = "Mabar Push Rank Mythic Malam Ini",
            onValueChange = { v -> onUpdateSettings { it.copy(streamTitleHeadline = v) } }
        )
    }
}

@Composable
private fun OverlayAndCompositorDeck(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    activeTier: SaasPlanTier,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onAddGoalProgress: (Int) -> Unit,
    onReloadOverlayClick: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    DsPanel {
        DsText("Preset Bingkai & Multi-Layer Compositor", size = 14.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OverlayFramePreset.entries.forEach { preset ->
                val locked = preset.requiresPro && !activeTier.unlockAllScenes
                DsButton(
                    label = preset.label + if (locked) " [Pro]" else "",
                    compact = true,
                    active = settings.framePreset == preset,
                    onClick = {
                        if (locked) onOpenUpgrade() else onUpdateSettings { it.copy(framePreset = preset) }
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsText("Mode Widget Interaksi Tap-Tap Love / Sultan", size = 12.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LoveTapPreset.entries.forEach { lp ->
                DsButton(
                    label = lp.label,
                    compact = true,
                    active = settings.loveTapPreset == lp,
                    onClick = { onUpdateSettings { it.copy(loveTapPreset = lp) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DsToggle(
                title = "Master Layer Overlay Aktif",
                subtitle = "Tampilkan seluruh widget di panggung preview & siaran RTMP",
                checked = settings.overlayMasterEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(overlayMasterEnabled = v) } }
            )
            DsToggle(
                title = "Progress Bar Target Gift / Donasi",
                subtitle = "Progres saat ini: ${settings.goalCurrent} / ${settings.goalTarget}",
                checked = settings.goalBarVisible,
                onCheckedChange = { v -> onUpdateSettings { it.copy(goalBarVisible = v) } }
            )
            DsToggle(
                title = "Subathon Countdown Timer",
                subtitle = "Bertambah +60 detik otomatis saat gift/donasi masuk",
                checked = settings.subathonTimerVisible,
                badge = "Pro",
                onCheckedChange = { v -> onUpdateSettings { it.copy(subathonTimerVisible = v) } }
            )
            DsToggle(
                title = "Running Text Bar Bawah",
                subtitle = settings.lowerThirdTickerText,
                checked = settings.lowerThirdVisible,
                onCheckedChange = { v -> onUpdateSettings { it.copy(lowerThirdVisible = v) } }
            )
            DsToggle(
                title = "Sembunyikan Watermark DiStream",
                subtitle = if (activeTier.removeWatermark) "Aktif — Feed bersih 100% tanpa watermark" else "Upgrade mulai Rp 5.000 untuk siaran bersih tanpa watermark",
                checked = activeTier.removeWatermark && !settings.watermarkVisible,
                badge = "Clean Feed",
                onCheckedChange = { clean ->
                    if (!activeTier.removeWatermark) onOpenUpgrade()
                    else onUpdateSettings { it.copy(watermarkVisible = !clean) }
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DsButton("+100 Target Gift", compact = true, onClick = { onAddGoalProgress(100) })
            DsButton("Reset Target", compact = true, onClick = { onUpdateSettings { it.copy(goalCurrent = 0) } })
            DsButton("Muat Ulang Browser Layer", compact = true, onClick = onReloadOverlayClick)
        }

        Spacer(Modifier.height(10.dp))
        DsInputField(
            label = "URL Browser Source / Saweria / TikFinity Overlay (Transparan)",
            value = settings.primaryOverlayUrl,
            placeholder = "https://saweria.co/overlays/alert?streamKey=...",
            onValueChange = { url -> onUpdateSettings { it.copy(primaryOverlayUrl = url) } }
        )
    }
}

@Composable
private fun AudioAndSoundFxDeck(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    activeTier: SaasPlanTier,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onPlaySoundFx: (SoundFxPad) -> Unit,
    onStopAllSoundFx: () -> Unit,
    onToggleCensorBeep: (Boolean) -> Unit,
    onOpenUpgrade: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    DsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsText("SoundFX Deck 48kHz Real Audio + DSP Mixer", size = 14.sp, weight = FontWeight.Bold)
            DsButton("Stop Efek", compact = true, onClick = onStopAllSoundFx)
        }
        Spacer(Modifier.height(4.dp))
        DsText(
            "Ketuk pad di bawah untuk mendengar efek suara 48kHz langsung di speaker HP sekaligus di-mix ke penonton live.",
            size = 11.5.sp,
            color = p.textMuted
        )

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DsVuBar("MIC", runtimeState.micLevelNormalized, p.okEmerald)
            DsVuBar("GAME", runtimeState.gameLevelNormalized, p.accentPrimary)
            DsVuBar("SFX", runtimeState.sfxLevelNormalized, p.warnAmber)
        }

        Spacer(Modifier.height(12.dp))
        // Grid 8 Pad SoundFX
        SoundFxPad.entries.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                pair.forEach { pad ->
                    val locked = pad.requiresPro && !activeTier.unlockAllSoundFx
                    val isPlaying = runtimeState.activeSfxName == pad.label
                    DsButton(
                        label = pad.label + if (locked) " [Pro]" else "",
                        active = isPlaying,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (locked) onOpenUpgrade() else onPlaySoundFx(pad)
                        }
                    )
                }
            }
        }

        // Tombol Sensor Suara 1kHz
        DsButton(
            label = if (runtimeState.isCensorBeepActive) "MATIKAN SENSOR SUARA 1KHZ" else "AKTIFKAN SENSOR SUARA LIVE 1KHZ",
            variant = if (runtimeState.isCensorBeepActive) DsBtnVariant.LIVE_DANGER else DsBtnVariant.SURFACE,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onToggleCensorBeep(!runtimeState.isCensorBeepActive) }
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AudioCaptureMode.entries.forEach { mode ->
                DsButton(
                    label = mode.label,
                    compact = true,
                    active = settings.audioMode == mode,
                    onClick = { onUpdateSettings { it.copy(audioMode = mode) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsFader(
            label = "Boost Volume Mikrofon Vokal",
            value = settings.micGain,
            valueRange = 0.5f..3.5f,
            formattedValue = String.format("%.1fx", settings.micGain),
            onValueChange = { v -> onUpdateSettings { it.copy(micGain = v) } }
        )
        Spacer(Modifier.height(8.dp))
        DsFader(
            label = "Volume Suara Internal Game",
            value = settings.gameGain,
            valueRange = 0.1f..2.0f,
            formattedValue = String.format("%.1fx", settings.gameGain),
            onValueChange = { v -> onUpdateSettings { it.copy(gameGain = v) } }
        )
        Spacer(Modifier.height(8.dp))
        DsFader(
            label = "Volume SoundFX Deck",
            value = settings.sfxVolume,
            valueRange = 0.2f..1.4f,
            formattedValue = "${(settings.sfxVolume * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } }
        )

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DsToggle(
                title = "Auto Voice Ducking (Kecilkan Game Saat Bicara)",
                subtitle = "Otomatis menurunkan suara game 55% begitu streamer berbicara",
                checked = settings.voiceDuckingEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(voiceDuckingEnabled = v) } }
            )
            DsToggle(
                title = "Broadcast Noise Gate (-42 dBFS)",
                subtitle = "Redam desis kipas HP & suara latar ruangan saat diam",
                checked = settings.noiseGateEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(noiseGateEnabled = v) } }
            )
        }
    }
}

@Composable
private fun FloatingAndLiveEventDeck(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    hasOverlayPermission: Boolean,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onToggleFloatingTest: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onTestGiftClick: () -> Unit,
    onTestDonationClick: () -> Unit,
    onSaveReplayClip: () -> Unit,
    onToggleRecordClick: () -> Unit,
    onCaptureSnapshotClick: () -> Unit,
    onAddMarkerClick: () -> Unit,
    onToggleTorchClick: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    DsPanel {
        DsText("Tes Mode Melayang (Floating HUD) & Alat Siaran", size = 14.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        DsText(
            "Nyalakan kapsul melayang sekarang juga untuk mengetes geser jendela, mengecilkan jadi gelembung tepi, ganti scene, Facecam PiP, dan SoundFX di atas Home Screen / Game tanpa harus mulai RTMP dulu.",
            size = 11.5.sp,
            color = p.textMuted
        )
        Spacer(Modifier.height(10.dp))

        if (!hasOverlayPermission) {
            DsButton(
                label = "1. Berikan Izin Tampil di Atas Aplikasi Lain",
                variant = DsBtnVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestOverlayPermission
            )
            Spacer(Modifier.height(8.dp))
        }

        DsButton(
            label = if (runtimeState.isFloatingTestActive) "MATIKAN JENDELA MELAYANG" else "AKTIFKAN & TES MELAYANG SEKARANG",
            variant = if (runtimeState.isFloatingTestActive) DsBtnVariant.LIVE_DANGER else DsBtnVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!hasOverlayPermission) onRequestOverlayPermission() else onToggleFloatingTest()
            }
        )

        Spacer(Modifier.height(10.dp))
        DsFader(
            label = "Transparansi Kapsul Melayang",
            value = settings.hudOpacityPercent.toFloat(),
            valueRange = 50f..100f,
            formattedValue = "${settings.hudOpacityPercent}%",
            onValueChange = { v -> onUpdateSettings { it.copy(hudOpacityPercent = v.roundToInt()) } }
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0 to "Facecam S", 1 to "Facecam M", 2 to "Facecam L").forEach { (idx, label) ->
                DsButton(
                    label = label,
                    compact = true,
                    active = settings.facecamSizeIndex == idx,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateSettings { it.copy(facecamSizeIndex = idx) } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsToggle(
            title = "Tampilkan Facecam PiP Kamera Langsung",
            subtitle = "Muncul di panggung kanvas & kapsul melayang saat bermain game",
            checked = settings.floatingFacecamEnabled,
            onCheckedChange = { v -> onUpdateSettings { it.copy(floatingFacecamEnabled = v) } }
        )

        Spacer(Modifier.height(12.dp))
        DsText("Tes Alert Donasi, Suara Pintar TTS & Rekaman", size = 12.sp, weight = FontWeight.Bold, color = p.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DsButton("Tes Gift Paus", compact = true, modifier = Modifier.weight(1f), onClick = onTestGiftClick)
            DsButton("Tes Saweria + TTS", compact = true, modifier = Modifier.weight(1f), onClick = onTestDonationClick)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DsButton("Simpan Replay 30s", compact = true, modifier = Modifier.weight(1f), onClick = onSaveReplayClip)
            DsButton(
                label = if (runtimeState.isRecordingLocal) "Stop Rekam MP4" else "Rekam MP4 Lokal",
                compact = true,
                active = runtimeState.isRecordingLocal,
                modifier = Modifier.weight(1f),
                onClick = onToggleRecordClick
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DsButton("Snapshot PNG", compact = true, modifier = Modifier.weight(1f), onClick = onCaptureSnapshotClick)
            DsButton("Bookmark Momen", compact = true, modifier = Modifier.weight(1f), onClick = onAddMarkerClick)
            DsButton("Flash Kamera", compact = true, active = runtimeState.isTorchActive, onClick = onToggleTorchClick)
        }
    }
}

@Composable
private fun SecurityAndProCloudDeck(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    activeTier: SaasPlanTier,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onOpenUpgrade: () -> Unit,
    onRedeemToken: (String) -> Unit,
    onTriggerPanic: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    var manualTokenInput by remember { mutableStateOf("") }

    DsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsText("Brankas RTMP Keystore & Cloud Pro", size = 14.sp, weight = FontWeight.Bold)
            DsButton("Upgrade Paket", compact = true, variant = DsBtnVariant.VIP_GOLD, onClick = onOpenUpgrade)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RtmpDestinationPreset.entries.forEach { dest ->
                DsButton(
                    label = dest.platformName,
                    compact = true,
                    active = settings.destinationPreset == dest,
                    onClick = {
                        onUpdateSettings {
                            it.copy(destinationPreset = dest, serverUrl = dest.defaultServerUrl)
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DsInputField(
            label = "Server RTMP / RTMPS Tujuan",
            value = settings.serverUrl,
            placeholder = "rtmps://push.rtmp.tiktok.com/live",
            onValueChange = { v -> onUpdateSettings { it.copy(serverUrl = v) } }
        )
        Spacer(Modifier.height(8.dp))
        DsInputField(
            label = "Stream Key Rahasia (Terenkripsi Hardware AES-256-GCM)",
            value = settings.streamKey,
            placeholder = "Tempel Stream Key TikTok / YouTube Anda",
            onValueChange = { v -> onUpdateSettings { it.copy(streamKey = v) } },
            isSecret = true
        )
        if (settings.streamKey.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            DsText(
                text = "Endpoint terproteksi: ${StreamKeyVault.sanitizeEndpointForDisplay(settings.buildFullEndpoint())}",
                size = 10.5.sp,
                mono = true,
                color = p.okEmerald
            )
        }

        Spacer(Modifier.height(10.dp))
        DsInputField(
            label = "Username TikTok Live (@handle untuk Chat & Gift Real-Time)",
            value = settings.tiktokUsername,
            placeholder = "@username_kreator",
            onValueChange = { v -> onUpdateSettings { it.copy(tiktokUsername = v) } }
        )

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DsToggle(
                title = "Multi-Stream Cloud Simulcast (TikTok + YouTube + Twitch)",
                subtitle = "1x upload hemat baterai & kuota ke 3 platform sekaligus",
                checked = settings.simulcastEnabled && activeTier.allowMultiStreamSimulcast,
                badge = "Mingguan+",
                onCheckedChange = { v ->
                    if (!activeTier.allowMultiStreamSimulcast) onOpenUpgrade()
                    else onUpdateSettings { it.copy(simulcastEnabled = v) }
                }
            )
            if (settings.simulcastEnabled && activeTier.allowMultiStreamSimulcast) {
                DsInputField(
                    label = "YouTube Stream Key Tambahan",
                    value = settings.simulcastYoutubeKey,
                    placeholder = "xxxx-xxxx-xxxx-xxxx",
                    onValueChange = { v -> onUpdateSettings { it.copy(simulcastYoutubeKey = v) } },
                    isSecret = true
                )
                DsInputField(
                    label = "Twitch Stream Key Tambahan",
                    value = settings.simulcastTwitchKey,
                    placeholder = "live_xxxxxxxx",
                    onValueChange = { v -> onUpdateSettings { it.copy(simulcastTwitchKey = v) } },
                    isSecret = true
                )
            }
            DsToggle(
                title = "Smart Indonesian TTS Pembaca Donasi Otomatis",
                subtitle = "Bacakan nama & pesan saweria/gift otomatis dengan suara Bahasa Indonesia",
                checked = settings.smartTtsDonationEnabled && activeTier.allowSmartTtsDonation,
                badge = "VIP Bulanan",
                onCheckedChange = { v ->
                    if (!activeTier.allowSmartTtsDonation) onOpenUpgrade()
                    else onUpdateSettings { it.copy(smartTtsDonationEnabled = v) }
                }
            )
            DsToggle(
                title = "Cloud Anti-Disconnect Shield 60 Detik",
                subtitle = "Jaga sesi room TikTok LIVE tidak putus saat sinyal 4G/Wi-Fi perpindahan",
                checked = settings.cloudFailoverShieldEnabled && activeTier.allowCloudFailoverShield,
                badge = "VIP Bulanan",
                onCheckedChange = { v ->
                    if (!activeTier.allowCloudFailoverShield) onOpenUpgrade()
                    else onUpdateSettings { it.copy(cloudFailoverShieldEnabled = v) }
                }
            )
            DsToggle(
                title = "Layar Aplikasi Anti-Intip (FLAG_SECURE)",
                subtitle = "Cegah Stream Key terekam saat membuka aplikasi di tengah live",
                checked = settings.protectAppWindowFromCapture,
                onCheckedChange = { v -> onUpdateSettings { it.copy(protectAppWindowFromCapture = v) } }
            )
            DsToggle(
                title = "Kunci PIN Sebelum Stop Live",
                subtitle = "Cegah siaran mati tak sengaja saat layar tersentuh",
                checked = settings.confirmPinBeforeStop,
                onCheckedChange = { v -> onUpdateSettings { it.copy(confirmPinBeforeStop = v) } }
            )
        }

        Spacer(Modifier.height(10.dp))
        DsButton(
            label = "DARURAT PANIC BLACKOUT (TUTUP LAYAR + MUTE MIC INSTAN)",
            variant = DsBtnVariant.LIVE_DANGER,
            modifier = Modifier.fillMaxWidth(),
            onClick = onTriggerPanic
        )

        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DsInputField(
                label = "Aktivasi Kode Token Lisensi (DST1...)",
                value = manualTokenInput,
                placeholder = "Tempel token DST1 dari email bukti bayar",
                onValueChange = { manualTokenInput = it },
                modifier = Modifier.weight(1f)
            )
            DsButton(
                label = "Aktifkan",
                variant = DsBtnVariant.PRIMARY,
                onClick = {
                    if (manualTokenInput.isNotBlank()) {
                        onRedeemToken(manualTokenInput)
                        manualTokenInput = ""
                    }
                }
            )
        }
    }
}

@Composable
private fun SaasCheckoutSheetModal(
    activeTier: SaasPlanTier,
    settings: StreamSettings,
    pendingOrder: SaasCheckoutOrder?,
    isCreatingOrder: Boolean,
    onUpdateEmail: (String) -> Unit,
    onStartCheckout: (SaasPlanTier, String) -> Unit,
    onOpenBrowserCheckout: (String) -> Unit,
    onConfirmPaid: () -> Unit,
    onClose: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    var selectedPlan by remember { mutableStateOf(SaasPlanTier.MONTHLY) }
    var promoInput by remember { mutableStateOf("") }

    DsModalScrim(onDismiss = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DsText("DiStream Studio Pro — Pakasir QRIS v2", size = 15.sp, weight = FontWeight.Bold)
                DsButton("Tutup", compact = true, onClick = onClose)
            }

            if (pendingOrder == null) {
                listOf(SaasPlanTier.DAILY, SaasPlanTier.WEEKLY, SaasPlanTier.MONTHLY).forEach { tier ->
                    val isSel = selectedPlan == tier
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isSel) p.accentPrimary.copy(alpha = 0.14f) else p.surfaceMid)
                            .border(
                                width = if (isSel) 1.5.dp else 1.dp,
                                color = if (isSel) p.accentPrimary else p.borderSubtle,
                                shape = RoundedCornerShape(13.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                DsText(tier.title, size = 13.5.sp, weight = FontWeight.Bold)
                                DsText(tier.badge, size = 10.5.sp, color = p.accentSecondary)
                            }
                            DsButton(
                                label = formatRupiah(tier.priceIdr),
                                compact = true,
                                variant = if (isSel) DsBtnVariant.PRIMARY else DsBtnVariant.SURFACE,
                                onClick = { selectedPlan = tier }
                            )
                        }
                    }
                }

                DsInputField(
                    label = "Email Penerima Token Lisensi (Resend Email Otomatis)",
                    value = settings.subscriberEmail,
                    placeholder = "nama@email.com (opsional)",
                    onValueChange = onUpdateEmail,
                    keyboardType = KeyboardType.Email
                )
                DsInputField(
                    label = "Kode Promo / Voucher",
                    value = promoInput,
                    placeholder = "Kode promo jika ada (XYKAL50 / HEMAT20)",
                    onValueChange = { promoInput = it }
                )

                val (effTier, discPct, finalRp) = SaasSubscriptionManager.calculatePromoPricing(
                    selectedPlan,
                    promoInput
                )
                DsButton(
                    label = if (isCreatingOrder) {
                        "MEMBUAT INVOICE QRIS DI PAKASIR..."
                    } else {
                        "BAYAR ${formatRupiah(finalRp)} VIA QRIS (${effTier.title})" +
                            if (discPct > 0) " [-$discPct%]" else ""
                    },
                    variant = DsBtnVariant.VIP_GOLD,
                    enabled = !isCreatingOrder,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onStartCheckout(selectedPlan, promoInput) }
                )
            } else {
                // TAMPILAN INVOICE REAL QRIS DARI PAKASIR API v2 + ZXING QR BITMAP ASLI!
                val qrBitmap = remember(pendingOrder.qrisPayload) {
                    SaasSubscriptionManager.generateQrBitmap(pendingOrder.qrisPayload, 520)
                }

                DsPanel(elevated = true) {
                    DsText(
                        text = if (pendingOrder.gatewayLive) {
                            "Terhubung Live ke app.pakasir.com (Txn #${pendingOrder.txnId})"
                        } else {
                            "Invoice #${pendingOrder.orderId}"
                        },
                        size = 11.5.sp,
                        weight = FontWeight.Bold,
                        color = p.okEmerald
                    )
                    Spacer(Modifier.height(4.dp))
                    DsText(
                        text = "Total Bayar QRIS: ${formatRupiah(pendingOrder.totalPaymentIdr)} (Termasuk biaya layanan ${formatRupiah(pendingOrder.feeIdr)})",
                        size = 12.sp,
                        color = p.textSecondary
                    )
                    Spacer(Modifier.height(10.dp))

                    if (qrBitmap != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(206.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Kode QRIS Pakasir",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    DsText(
                        text = "Status dipantau otomatis setiap 4.5 detik. Begitu pembayaran diterima oleh Pakasir, lisensi ${pendingOrder.planTier.title} aktif otomatis.",
                        size = 11.sp,
                        color = p.textMuted
                    )
                }

                if (pendingOrder.hostedPaymentUrl.isNotBlank()) {
                    DsButton(
                        label = "Buka Halaman Pembayaran Resmi Pakasir",
                        variant = DsBtnVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenBrowserCheckout(pendingOrder.hostedPaymentUrl) }
                    )
                }

                DsButton(
                    label = if (pendingOrder.isSandbox) {
                        "Konfirmasi Bayar via API Resmi Pakasir (Mode Proyek Sandbox)"
                    } else {
                        "Saya Sudah Scan & Bayar — Cek Status Sekarang"
                    },
                    variant = DsBtnVariant.VIP_GOLD,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onConfirmPaid
                )
            }
        }
    }
}
