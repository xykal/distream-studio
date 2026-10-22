package id.distream.studio.ui

import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.distream.studio.model.LoveTapPreset
import id.distream.studio.model.OverlayFramePreset
import id.distream.studio.model.RtmpDestinationPreset
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamOrientation
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StreamStatus
import id.distream.studio.model.StudioScene
import id.distream.studio.model.StudioThemeMode
import id.distream.studio.model.VideoCodecOption
import id.distream.studio.model.VideoQualityPreset
import id.distream.studio.saas.SaasCheckoutOrder
import id.distream.studio.saas.SaasSubscriptionManager
import id.distream.studio.service.DiStreamService
import id.distream.studio.ui.theme.BrandBlue
import id.distream.studio.ui.theme.CautionAmber
import id.distream.studio.ui.theme.HealthyGreen
import id.distream.studio.ui.theme.LiveRed
import kotlin.math.roundToInt

@Composable
fun StudioHomeScreen(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    pendingOrder: SaasCheckoutOrder?,
    saasNotice: String,
    hasOverlayPermission: Boolean,
    onToggleTheme: () -> Unit,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onStartCheckout: (SaasPlanTier, String) -> Unit,
    onConfirmOrderPaid: () -> Unit,
    onDismissOrder: () -> Unit,
    onRedeemLicenseToken: (String) -> Unit,
    onSaveReplayClip: () -> Unit,
    onAddCustomText: (String) -> Unit,
    onMoveCustomText: (String, Int, Int) -> Unit,
    onToggleCustomText: (String) -> Unit,
    onDeleteCustomText: (String) -> Unit,
    onSelectScene: (StudioScene) -> Unit,
    onPlaySoundFx: (SoundFxPad) -> Unit,
    onStopAllSoundFx: () -> Unit,
    onToggleCensorBeep: (Boolean) -> Unit,
    onTriggerPanic: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStopPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    val activeTier = SaasSubscriptionManager.resolveActiveTier(settings)
    val isLive = runtimeState.status == StreamStatus.LIVE ||
        runtimeState.status == StreamStatus.CONNECTING ||
        runtimeState.status == StreamStatus.RECONNECTING

    // QRIS Payment Checkout Dialog (Pakasir Gateway)
    if (pendingOrder != null) {
        AlertDialog(
            onDismissRequest = onDismissOrder,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = BrandBlue)
                    Text("Pembayaran QRIS — ${pendingOrder.planTier.title}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Order ID: ${pendingOrder.orderId}\nTotal Tagihan: Rp ${pendingOrder.totalPaymentIdr} (termasuk biaya layanan QRIS)",
                        fontSize = 12.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("String QRIS / Link Pakasir:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                pendingOrder.hostedPaymentUrl,
                                fontSize = 11.sp,
                                color = BrandBlue
                            )
                        }
                    }
                    Text(
                        "Scan QRIS melalui GoPay, OVO, DANA, ShopeePay, atau Mobile Banking, lalu tekan tombol Verifikasi Pembayaran.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = onConfirmOrderPaid) {
                    Text("Verifikasi Lunas")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissOrder) {
                    Text("Batal")
                }
            }
        )
    }

    if (showStopPinDialog) {
        AlertDialog(
            onDismissRequest = { showStopPinDialog = false },
            title = { Text("Konfirmasi Hentikan Siaran", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Masukkan PIN keamanan (${settings.stopStreamPin.length} digit) agar siaran tidak tertutup akibat salah sentuh.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("PIN Keamanan") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == settings.stopStreamPin) {
                            showStopPinDialog = false
                            pinInput = ""
                            onStopLiveClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                ) {
                    Text("Hentikan Live")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopPinDialog = false; pinInput = "" }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DiStreamHeaderBar(
                settings = settings,
                activeTier = activeTier,
                runtimeState = runtimeState,
                isLive = isLive,
                onOpenProTab = { selectedTab = 2 },
                onToggleTheme = onToggleTheme,
                onToggleSafeZone = {
                    onUpdateSettings { it.copy(showSafeZoneGuide = !it.showSafeZoneGuide) }
                },
                onToggleShield = onToggleShieldClick
            )
        },
        bottomBar = {
            DiStreamBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                isLive = isLive,
                isMuted = runtimeState.isMicMuted,
                isRecording = runtimeState.isRecordingLocal,
                onStartLive = onStartLiveClick,
                onRequestStopLive = {
                    if (settings.confirmPinBeforeStop) {
                        showStopPinDialog = true
                    } else {
                        onStopLiveClick()
                    }
                },
                onToggleMute = onToggleMuteClick,
                onToggleRecord = onToggleRecordClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LiveCanvasStage(
                settings = settings,
                activeTier = activeTier,
                isLive = isLive
            )

            SceneSelectorRow(
                activeScene = settings.activeScene,
                activeTier = activeTier,
                onSelectScene = onSelectScene
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> OverlayAndTextTab(
                        settings = settings,
                        activeTier = activeTier,
                        hasOverlayPermission = hasOverlayPermission,
                        onUpdateSettings = onUpdateSettings,
                        onAddCustomText = onAddCustomText,
                        onMoveCustomText = onMoveCustomText,
                        onToggleCustomText = onToggleCustomText,
                        onDeleteCustomText = onDeleteCustomText,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onAddGoal = onAddGoalProgress,
                        onReloadCompositor = onReloadOverlayClick,
                        onTestGift = onTestGiftClick,
                        onOpenUpgrade = { selectedTab = 2 }
                    )
                    1 -> AudioAndSoundFxTab(
                        settings = settings,
                        activeTier = activeTier,
                        runtimeState = runtimeState,
                        onUpdateSettings = onUpdateSettings,
                        onPlaySoundFx = onPlaySoundFx,
                        onStopAllSoundFx = onStopAllSoundFx,
                        onToggleCensorBeep = onToggleCensorBeep,
                        onToggleMute = onToggleMuteClick,
                        onOpenUpgrade = { selectedTab = 2 }
                    )
                    2 -> SaasProSubscriptionTab(
                        settings = settings,
                        activeTier = activeTier,
                        runtimeState = runtimeState,
                        saasNotice = saasNotice,
                        onUpdateSettings = onUpdateSettings,
                        onStartCheckout = onStartCheckout,
                        onRedeemLicenseToken = onRedeemLicenseToken,
                        onSaveReplayClip = onSaveReplayClip
                    )
                    3 -> BroadcastAndSecurityTab(
                        settings = settings,
                        runtimeState = runtimeState,
                        onUpdateSettings = onUpdateSettings,
                        onTriggerPanic = onTriggerPanic,
                        onSnapshot = onCaptureSnapshotClick,
                        onAddMarker = onAddMarkerClick,
                        onFlipCamera = onSwitchCameraClick,
                        onToggleTorch = onToggleTorchClick
                    )
                    4 -> LiveInteractionTab(
                        settings = settings,
                        runtimeState = runtimeState,
                        onUpdateSettings = onUpdateSettings,
                        onTestGift = onTestGiftClick,
                        onTestDonation = onTestDonationClick
                    )
                }
            }
        }
    }
}

@Composable
fun DiStreamLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        drawRoundRect(
            color = BrandBlue,
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            size = size
        )
        val stroke = 2.6.dp.toPx()
        drawArc(
            color = Color.White,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.22f, size.height * 0.24f),
            size = Size(size.width * 0.36f, size.height * 0.52f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.40f, size.height * 0.24f),
            end = Offset(size.width * 0.40f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawArc(
            color = Color.White.copy(alpha = 0.72f),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width * 0.46f, size.height * 0.28f),
            size = Size(size.width * 0.34f, size.height * 0.44f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun DiStreamHeaderBar(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    runtimeState: StreamRuntimeState,
    isLive: Boolean,
    onOpenProTab: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSafeZone: () -> Unit,
    onToggleShield: () -> Unit
) {
    val hrs = runtimeState.uptimeSeconds / 3600
    val mins = (runtimeState.uptimeSeconds % 3600) / 60
    val secs = runtimeState.uptimeSeconds % 60
    val duration = String.format("%02d:%02d:%02d", hrs, mins, secs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiStreamLogoMark()
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("DiStream", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (activeTier == SaasPlanTier.FREE) MaterialTheme.colorScheme.surfaceVariant
                                    else CautionAmber.copy(alpha = 0.2f)
                                )
                                .clickable { onOpenProTab() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (activeTier == SaasPlanTier.FREE) "Upgrade Pro" else activeTier.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTier == SaasPlanTier.FREE) BrandBlue else CautionAmber
                            )
                        }
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(LiveRed)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("Live $duration", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                    Text(
                        text = "${runtimeState.currentBitrateKbps} kbps • ${settings.qualityPreset.shortCode} • ${runtimeState.deviceTempCelsius.toInt()}°C",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onToggleSafeZone, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.GridOn,
                        contentDescription = "Batas Aman UI TikTok",
                        tint = if (settings.showSafeZoneGuide) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onToggleShield, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Layar Privasi",
                        tint = if (runtimeState.isPrivacyShieldActive) CautionAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onToggleTheme, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (settings.themeMode == StudioThemeMode.DARK) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Tema",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LiveCanvasStage(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    isLive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val ratio = if (settings.orientation.isPortrait) 9f / 16f else 16f / 9f
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF16181F))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        ) {
            if (isLive) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            DiStreamService.INSTANCE?.attachStudioPreview(sv)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DisposableEffect(Unit) {
                    onDispose { DiStreamService.INSTANCE?.detachStudioPreview() }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!activeTier.removeWatermark || settings.watermarkVisible) {
                            Text(
                                if (!activeTier.removeWatermark) "DiStream Free" else "Pro Clean",
                                fontSize = 8.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        if (settings.loveTapPreset != LoveTapPreset.NONE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = LiveRed, modifier = Modifier.size(9.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Tap Love", fontSize = 8.sp, color = Color.White)
                            }
                        }
                    }

                    Text(
                        text = settings.activeScene.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    if (settings.lowerThirdVisible) {
                        Text(
                            text = settings.lowerThirdTickerText,
                            fontSize = 7.5.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (settings.showSafeZoneGuide && settings.orientation.isPortrait) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.76f)
                        .fillMaxHeight(0.27f)
                        .border(1.dp, CautionAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(CautionAmber.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {
                    Text("Area Chat TikTok", fontSize = 7.sp, color = CautionAmber)
                }
            }
        }
    }
}

@Composable
private fun SceneSelectorRow(
    activeScene: StudioScene,
    activeTier: SaasPlanTier,
    onSelectScene: (StudioScene) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StudioScene.entries) { scene ->
            val selected = scene == activeScene
            val locked = scene.requiresPro && !activeTier.unlockAllScenes
            val bg by animateColorAsState(
                if (selected) BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                label = "sceneBg"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelectScene(scene) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (locked) "${scene.label} (Pro)" else scene.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * TAB 2: PUSAT LANGGANAN SAAS (HARIAN, MINGGUAN, BULANAN) & FITUR EKSKLUSIF VIP
 */
@Composable
private fun SaasProSubscriptionTab(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    runtimeState: StreamRuntimeState,
    saasNotice: String,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onStartCheckout: (SaasPlanTier, String) -> Unit,
    onRedeemLicenseToken: (String) -> Unit,
    onSaveReplayClip: () -> Unit
) {
    var promoCodeInput by remember { mutableStateOf("") }
    var licenseInput by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardPanel(title = "Status Paket Langganan & Analitik Pendapatan") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Paket Aktif: ${activeTier.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (activeTier == SaasPlanTier.FREE) MaterialTheme.colorScheme.onSurface else BrandBlue
                        )
                        val remainingHrs = ((settings.saasExpiresAtMillis - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(0L)
                        Text(
                            text = if (activeTier == SaasPlanTier.FREE) {
                                "Upgrade mulai Rp 5.000/hari untuk buka fitur eksklusif tanpa watermark"
                            } else {
                                "Masa aktif tersisa sekitar $remainingHrs jam"
                            },
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (saasNotice.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(saasNotice, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HealthyGreen)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMiniBox("Estimasi Sesi", "Rp ${runtimeState.estimatedIdrRevenue}", Modifier.weight(1f))
                    StatMiniBox("Peak Bitrate", "${runtimeState.peakBitrateKbps} kbps", Modifier.weight(1f))
                    StatMiniBox("Klip Replay", "${runtimeState.replayClipsSaved} klip", Modifier.weight(1f))
                }
            }
        }

        item {
            CardPanel(title = "Pilih Paket Langganan (QRIS Otomatis / Kode Promo)") {
                OutlinedTextField(
                    value = promoCodeInput,
                    onValueChange = { promoCodeInput = it },
                    label = { Text("Kode Promo / Voucher (Contoh: XYKAL50 atau TRIALVIP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                PlanOptionCard(
                    tier = SaasPlanTier.DAILY,
                    highlights = "Hapus Watermark 100% • 1080p60 High-Bitrate + HEVC • Full 8 SoundFX • 5 Scene + Subathon Timer",
                    isCurrent = activeTier == SaasPlanTier.DAILY,
                    onSubscribe = { onStartCheckout(SaasPlanTier.DAILY, promoCodeInput) }
                )
                Spacer(Modifier.height(8.dp))
                PlanOptionCard(
                    tier = SaasPlanTier.WEEKLY,
                    highlights = "Semua fitur Harian + Cloud Multi-Stream Simulcast (Live 1x Upload ke TikTok + YouTube + Twitch hemat kuota 66%) • Replay Instan 30 Detik • Teks Kustom Tanpa Batas",
                    isCurrent = activeTier == SaasPlanTier.WEEKLY,
                    onSubscribe = { onStartCheckout(SaasPlanTier.WEEKLY, promoCodeInput) }
                )
                Spacer(Modifier.height(8.dp))
                PlanOptionCard(
                    tier = SaasPlanTier.MONTHLY,
                    highlights = "Semua fitur Mingguan + Smart TTS Pembaca Suara Donasi & Gift Otomatis • Cloud Anti-Disconnect Shield 60 Detik (Room TikTok Anti-Putus) • Analitik Pendapatan IDR",
                    isCurrent = activeTier == SaasPlanTier.MONTHLY,
                    onSubscribe = { onStartCheckout(SaasPlanTier.MONTHLY, promoCodeInput) }
                )
            }
        }

        item {
            CardPanel(title = "Fitur Eksklusif Cloud Pro & VIP") {
                SettingToggleRow(
                    title = "Cloud Multi-Stream Simulcast (Mingguan / Bulanan)",
                    subtitle = "1x upload dari HP langsung disiarkan serentak ke TikTok + YouTube + Twitch",
                    checked = settings.simulcastEnabled && activeTier.allowMultiStreamSimulcast,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(simulcastEnabled = v) } }
                )
                if (settings.simulcastEnabled) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = settings.simulcastYoutubeKey,
                        onValueChange = { k -> onUpdateSettings { it.copy(simulcastYoutubeKey = k) } },
                        label = { Text("Stream Key Tambahan YouTube Live") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = settings.simulcastTwitchKey,
                        onValueChange = { k -> onUpdateSettings { it.copy(simulcastTwitchKey = k) } },
                        label = { Text("Stream Key Tambahan Twitch") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Suara Pintar TTS Pembaca Donasi & Gift (VIP Bulanan)",
                    subtitle = "Membacakan nama pengirim & pesan Saweria/Gift otomatis dalam Bahasa Indonesia",
                    checked = settings.smartTtsDonationEnabled && activeTier.allowSmartTtsDonation,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(smartTtsDonationEnabled = v) } }
                )
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Cloud Anti-Disconnect Shield 60s (VIP Bulanan)",
                    subtitle = "Menjaga room TikTok LIVE tetap aktif saat sinyal HP pindah WiFi ke Data Seluler",
                    checked = settings.cloudFailoverShieldEnabled && activeTier.allowCloudFailoverShield,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(cloudFailoverShieldEnabled = v) } }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSaveReplayClip,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Replay30, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Replay Momen 30 Detik Terakhir")
                }
            }
        }

        item {
            CardPanel(title = "Aktivasi Token Lisensi Manual / Offline") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = licenseInput,
                        onValueChange = { licenseInput = it },
                        label = { Text("Tempel Token DST1...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onRedeemLicenseToken(licenseInput)
                            licenseInput = ""
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Aktifkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PlanOptionCard(
    tier: SaasPlanTier,
    highlights: String,
    isCurrent: Boolean,
    onSubscribe: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (tier == SaasPlanTier.MONTHLY) BrandBlue else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(tier.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(tier.periodLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = BrandBlue)
            }
            Button(
                onClick = onSubscribe,
                enabled = !isCurrent,
                shape = RoundedCornerShape(9.dp)
            ) {
                Text(if (isCurrent) "Paket Aktif" else "Pilih Paket", fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(highlights, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
    }
}

@Composable
private fun OverlayAndTextTab(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    hasOverlayPermission: Boolean,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onAddCustomText: (String) -> Unit,
    onMoveCustomText: (String, Int, Int) -> Unit,
    onToggleCustomText: (String) -> Unit,
    onDeleteCustomText: (String) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onAddGoal: (Int) -> Unit,
    onReloadCompositor: () -> Unit,
    onTestGift: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    var draftText by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardPanel(title = "Preset Tap-Tap Love & Tema Bingkai") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LoveTapPreset.entries) { item ->
                        SelectableChip(
                            label = item.label,
                            selected = settings.loveTapPreset == item,
                            onClick = { onUpdateSettings { it.copy(loveTapPreset = item) } }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OverlayFramePreset.entries) { preset ->
                        val locked = preset.requiresPro && !activeTier.removeWatermark
                        SelectableChip(
                            label = if (locked) "${preset.label} (Pro)" else preset.label,
                            selected = settings.framePreset == preset,
                            onClick = {
                                if (locked) onOpenUpgrade()
                                else onUpdateSettings { it.copy(framePreset = preset) }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Sembunyikan Watermark DiStream",
                    subtitle = if (activeTier.removeWatermark) "Aktif — tampilan bersih penuh" else "Tersedia mulai Paket Harian (Rp 5.000)",
                    checked = activeTier.removeWatermark && !settings.watermarkVisible,
                    onCheckedChange = { hide ->
                        if (!activeTier.removeWatermark) onOpenUpgrade()
                        else onUpdateSettings { it.copy(watermarkVisible = !hide) }
                    }
                )
            }
        }

        item {
            CardPanel(title = "Teks Kustom Bebas (${settings.customTextLayers.size}/${activeTier.maxCustomTextLayers})") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        placeholder = { Text("Tulis ID Mabar / Pengumuman...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddCustomText(draftText)
                            draftText = ""
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Tambah")
                    }
                }
                Spacer(Modifier.height(10.dp))
                settings.customTextLayers.forEach { layer ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(layer.content, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Row {
                                IconButton(onClick = { onToggleCustomText(layer.id) }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        imageVector = if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (layer.visible) HealthyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onDeleteCustomText(layer.id) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Slider(
                            value = layer.yPercent.toFloat(),
                            onValueChange = { y -> onMoveCustomText(layer.id, layer.xPercent, y.roundToInt()) },
                            valueRange = 4f..86f
                        )
                    }
                }
            }
        }

        item {
            CardPanel(title = "Layer Sumber OBS & Browser Source (Saweria / TikFinity)") {
                LayerVisibilityRow(
                    title = "Alert Donasi Saweria / SociaBuzz",
                    subtitle = settings.primaryOverlayUrl.ifBlank { "Menggunakan kartu alert bawaan DiStream" },
                    visible = settings.primaryOverlayVisible,
                    onToggle = { onUpdateSettings { it.copy(primaryOverlayVisible = !it.primaryOverlayVisible) } }
                )
                LayerVisibilityRow(
                    title = "Bar Target Gift (${settings.goalCurrent} / ${settings.goalTarget})",
                    subtitle = settings.goalTitle,
                    visible = settings.goalBarVisible,
                    onToggle = { onUpdateSettings { it.copy(goalBarVisible = !it.goalBarVisible) } }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.primaryOverlayUrl,
                    onValueChange = { v -> onUpdateSettings { it.copy(primaryOverlayUrl = v) } },
                    label = { Text("Link Browser Source Saweria / SociaBuzz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAddGoal(50) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("+50 Target")
                    }
                    OutlinedButton(onClick = onReloadCompositor, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Muat Ulang")
                    }
                    Button(onClick = onTestGift, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("Tes Alert")
                    }
                }
            }
        }

        item {
            CardPanel(title = "Panel Melayang di Atas Game & Facecam") {
                if (!hasOverlayPermission) {
                    Button(
                        onClick = onRequestOverlayPermission,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Izinkan Panel Melayang di Atas Game")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SettingToggleRow(
                    title = "Tampilkan Kontrol Melayang Saat Main",
                    subtitle = "Dilengkapi drawer cepat SoundFX & Sensor 1kHz",
                    checked = settings.floatingHudEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(floatingHudEnabled = v) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Aktifkan Facecam Kamera Depan",
                    subtitle = "Tampil di pojok layar dan ikut terekam ke siaran",
                    checked = settings.floatingFacecamEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(floatingFacecamEnabled = v) } }
                )
            }
        }
    }
}

@Composable
private fun AudioAndSoundFxTab(
    settings: StreamSettings,
    activeTier: SaasPlanTier,
    runtimeState: StreamRuntimeState,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onPlaySoundFx: (SoundFxPad) -> Unit,
    onStopAllSoundFx: () -> Unit,
    onToggleCensorBeep: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardPanel(title = "SoundFX Audio Deck (Langsung Masuk ke Stream)") {
                SoundFxPad.entries.chunked(4).forEach { rowPads ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPads.forEach { pad ->
                            val locked = pad.requiresPro && !activeTier.unlockAllSoundFx
                            val isPlaying = runtimeState.activeSfxName == pad.label
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isPlaying) BrandBlue else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        if (locked) onOpenUpgrade() else onPlaySoundFx(pad)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.MusicNote,
                                    contentDescription = pad.label,
                                    tint = if (isPlaying) Color.White else if (locked) CautionAmber else BrandBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = pad.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onStopAllSoundFx,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hentikan Efek")
                    }
                    Button(
                        onClick = { onToggleCensorBeep(!runtimeState.isCensorBeepActive) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runtimeState.isCensorBeepActive) LiveRed else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (runtimeState.isCensorBeepActive) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (runtimeState.isCensorBeepActive) "Sensor 1kHz Aktif" else "Sensor Kata Kasar")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Volume SoundFX Deck: ${(settings.sfxVolume * 100).roundToInt()}%", fontSize = 12.sp)
                Slider(
                    value = settings.sfxVolume,
                    onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } },
                    valueRange = 0f..1.5f
                )
            }
        }

        item {
            CardPanel(title = "Audio Mixer Multi-Jalur & DSP") {
                AudioMeterStrip(
                    title = "Mikrofon Streamer",
                    percent = (settings.micGain * 100).roundToInt(),
                    level = runtimeState.micLevelNormalized,
                    muted = runtimeState.isMicMuted,
                    value = settings.micGain,
                    range = 0f..3.0f,
                    onValueChange = { g -> onUpdateSettings { it.copy(micGain = g) } },
                    onMuteClick = onToggleMute
                )
                Spacer(Modifier.height(12.dp))
                AudioMeterStrip(
                    title = "Suara Internal Game",
                    percent = (settings.gameGain * 100).roundToInt(),
                    level = runtimeState.gameLevelNormalized,
                    muted = settings.gameGain <= 0.01f,
                    value = settings.gameGain,
                    range = 0f..2.0f,
                    onValueChange = { g -> onUpdateSettings { it.copy(gameGain = g) } },
                    onMuteClick = {
                        val next = if (settings.gameGain <= 0.01f) 1.0f else 0f
                        onUpdateSettings { it.copy(gameGain = next) }
                    }
                )
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Peredam Bising Kipas HP (Noise Gate)",
                    subtitle = "Memotong desis Funcooler otomatis saat diam",
                    checked = settings.noiseGateEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(noiseGateEnabled = v) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Prioritas Vokal (Auto Voice Ducking)",
                    subtitle = "Mengecilkan suara ledakan game otomatis saat bicara",
                    checked = settings.voiceDuckingEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(voiceDuckingEnabled = v) } }
                )
            }
        }
    }
}

@Composable
private fun BroadcastAndSecurityTab(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onTriggerPanic: () -> Unit,
    onSnapshot: () -> Unit,
    onAddMarker: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleTorch: () -> Unit
) {
    var revealKey by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardPanel(title = "Aksi Keamanan Cepat & Kontrol Perangkat") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard(Icons.Rounded.Lock, "Darurat Panik", "Kunci Layar+Mic", LiveRed, onTriggerPanic, Modifier.weight(1f))
                    QuickActionCard(Icons.Rounded.CameraAlt, "Tangkap PNG", runtimeState.lastSnapshotName.ifBlank { "Instan" }, null, onSnapshot, Modifier.weight(1f))
                    QuickActionCard(Icons.Rounded.BookmarkBorder, "Tandai Momen", "${runtimeState.markerCount} tanda", null, onAddMarker, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard(Icons.Rounded.Cameraswitch, "Balik Kamera", "Depan/Belakang", null, onFlipCamera, Modifier.weight(1f))
                    QuickActionCard(Icons.Rounded.FlashlightOn, "Lampu Flash", if (runtimeState.isTorchActive) "Nyala" else "Mati", null, onToggleTorch, Modifier.weight(1f))
                }
            }
        }

        item {
            CardPanel(title = "Tujuan Server RTMP / RTMPS Terenkripsi") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RtmpDestinationPreset.entries) { preset ->
                        SelectableChip(
                            label = preset.platformName,
                            selected = settings.destinationPreset == preset,
                            onClick = {
                                onUpdateSettings {
                                    it.copy(destinationPreset = preset, serverUrl = preset.defaultServerUrl)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.serverUrl,
                    onValueChange = { u -> onUpdateSettings { it.copy(serverUrl = u) } },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.streamKey,
                    onValueChange = { k -> onUpdateSettings { it.copy(streamKey = k) } },
                    label = { Text("Stream Key (Terenkripsi AES-256 Keystore)") },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            CardPanel(title = "Pusat Keamanan & Perlindungan Akun") {
                SettingToggleRow(
                    title = "Sembunyikan Layar Aplikasi dari Rekaman (FLAG_SECURE)",
                    subtitle = "Stream Key & PIN tidak akan pernah terlihat di video live",
                    checked = settings.protectAppWindowFromCapture,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(protectAppWindowFromCapture = v) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Kunci PIN Sebelum Menghentikan Live",
                    subtitle = "Mencegah siaran mati tidak sengaja akibat salah tekan",
                    checked = settings.confirmPinBeforeStop,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(confirmPinBeforeStop = v) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Hapus Otomatis Stream Key Selesai Siaran",
                    subtitle = "Keamanan ekstra apabila HP digunakan bersama orang lain",
                    checked = settings.autoWipeStreamKeyAfterLive,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoWipeStreamKeyAfterLive = v) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Filter Kata Kasar & Promosi Terlarang Otomatis",
                    subtitle = "Menyensor pesan chat/donasi berisiko pelanggaran akun TikTok",
                    checked = settings.profanityChatFilter,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(profanityChatFilter = v) } }
                )
            }
        }

        item {
            CardPanel(title = "Kualitas Encoder Video & Orientasi") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamOrientation.entries.forEach { o ->
                        SelectableChip(
                            label = o.label,
                            selected = settings.orientation == o,
                            onClick = { onUpdateSettings { it.copy(orientation = o) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCodecOption.entries.forEach { c ->
                        SelectableChip(
                            label = c.label,
                            selected = settings.videoCodec == c,
                            onClick = { onUpdateSettings { it.copy(videoCodec = c) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                VideoQualityPreset.entries.forEach { preset ->
                    LayerVisibilityRow(
                        title = preset.label,
                        subtitle = "Bitrate maks ${preset.maxBitrateBps / 1000} kbps • Adaptive Bitrate",
                        visible = settings.qualityPreset == preset,
                        onToggle = { onUpdateSettings { it.copy(qualityPreset = preset) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveInteractionTab(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onTestGift: () -> Unit,
    onTestDonation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardPanel(title = "Koneksi Pembaca Chat & Webhook Donasi") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settings.tiktokUsername,
                    onValueChange = { u -> onUpdateSettings { it.copy(tiktokUsername = u) } },
                    label = { Text("Username TikTok") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = settings.chatWebSocketUrl,
                    onValueChange = { w -> onUpdateSettings { it.copy(chatWebSocketUrl = w) } },
                    label = { Text("Server Event Bus") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestGift, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Simulasi Gift + Love")
                }
                OutlinedButton(onClick = onTestDonation, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Simulasi Saweria")
                }
            }
        }

        CardPanel(title = "Riwayat Interaksi Real-Time", modifier = Modifier.weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(runtimeState.liveEvents.reversed(), key = { it.id }) { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${ev.type.label}] ${ev.senderName}: ${ev.message}",
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ev.timestampLabel,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiStreamBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    isLive: Boolean,
    isMuted: Boolean,
    isRecording: Boolean,
    onStartLive: () -> Unit,
    onRequestStopLive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleRecord: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = "Mic",
                    tint = if (isMuted) LiveRed else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onToggleRecord,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = "Rekam",
                    tint = if (isRecording) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = if (isLive) onRequestStopLive else onStartLive,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) LiveRed else BrandBlue
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isLive) "Akhiri Siaran Live" else "Mulai Siaran Live",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) },
                icon = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                label = { Text("Overlay", fontSize = 11.sp) }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) },
                icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                label = { Text("SoundFX", fontSize = 11.sp) }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { onSelectTab(2) },
                icon = { Icon(Icons.Rounded.WorkspacePremium, contentDescription = null) },
                label = { Text("Pro VIP", fontSize = 11.sp) }
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { onSelectTab(3) },
                icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
                label = { Text("Keamanan", fontSize = 11.sp) }
            )
            NavigationBarItem(
                selected = selectedTab == 4,
                onClick = { onSelectTab(4) },
                icon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                label = { Text("Chat", fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun CardPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandBlue else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LayerVisibilityRow(
    title: String,
    subtitle: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = null,
            tint = if (visible) HealthyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun AudioMeterStrip(
    title: String,
    percent: Int,
    level: Float,
    muted: Boolean,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onMuteClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (muted) "Bisu" else "$percent%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = onMuteClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (muted) LiveRed else MaterialTheme.colorScheme.outline)
                ) {
                    Text(if (muted) "Nyalakan" else "Bisu", fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val activeBars = if (muted) 0 else (level * 20).roundToInt()
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 20) {
                val color = when {
                    i >= activeBars -> MaterialTheme.colorScheme.surfaceVariant
                    i >= 17 -> LiveRed
                    i >= 13 -> CautionAmber
                    else -> HealthyGreen
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    activeColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (activeColor != null) activeColor.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                activeColor ?: MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, contentDescription = title, tint = activeColor ?: BrandBlue, modifier = Modifier.size(19.dp))
        Column {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
