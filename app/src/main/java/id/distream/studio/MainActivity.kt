package id.distream.studio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.distream.studio.model.StreamStatus
import id.distream.studio.security.StreamKeyVault
import id.distream.studio.service.DiStreamService
import id.distream.studio.ui.MainViewModel
import id.distream.studio.ui.StudioHomeScreen
import id.distream.studio.ui.theme.DiStreamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var canDrawOverlayState by mutableStateOf(false)

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        requestScreenCaptureAndGoLive()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val projectionData = result.data
        if (result.resultCode == RESULT_OK && projectionData != null) {
            val service = DiStreamService.INSTANCE
            if (service != null) {
                viewModel.bindServiceCallbacks()
                service.startBroadcast(
                    resultCode = result.resultCode,
                    data = projectionData,
                    settings = viewModel.settings.value
                )
            } else {
                Toast.makeText(
                    this,
                    "Layanan sedang disiapkan, tekan Mulai Siaran sekali lagi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(this, "Izin rekam layar dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        canDrawOverlayState = Settings.canDrawOverlays(this)

        startService(Intent(this, DiStreamService::class.java))

        setContent {
            val settings by viewModel.settings.collectAsState()
            val runtimeState by viewModel.runtimeState.collectAsState()
            val pendingOrder by viewModel.pendingOrder.collectAsState()
            val saasNotice by viewModel.saasNotice.collectAsState()

            LaunchedEffect(settings.protectAppWindowFromCapture) {
                StreamKeyVault.applyWindowScreenCaptureGuard(
                    this@MainActivity,
                    settings.protectAppWindowFromCapture
                )
            }

            DiStreamTheme(themeMode = settings.themeMode) {
                StudioHomeScreen(
                    settings = settings,
                    runtimeState = runtimeState,
                    pendingOrder = pendingOrder,
                    saasNotice = saasNotice,
                    hasOverlayPermission = canDrawOverlayState,
                    onToggleTheme = viewModel::toggleThemeMode,
                    onUpdateSettings = viewModel::updateSettings,
                    onStartCheckout = viewModel::startSaasCheckout,
                    onConfirmOrderPaid = viewModel::confirmPendingOrderPayment,
                    onDismissOrder = viewModel::dismissPendingOrder,
                    onRedeemLicenseToken = viewModel::redeemManualLicenseToken,
                    onSaveReplayClip = viewModel::saveInstantReplayClip,
                    onAddCustomText = viewModel::addCustomTextLayer,
                    onMoveCustomText = viewModel::updateCustomTextPosition,
                    onToggleCustomText = viewModel::toggleCustomTextVisibility,
                    onDeleteCustomText = viewModel::removeCustomTextLayer,
                    onSelectScene = viewModel::selectScene,
                    onPlaySoundFx = viewModel::playSoundFx,
                    onStopAllSoundFx = viewModel::stopAllSoundFx,
                    onToggleCensorBeep = viewModel::setLiveCensorBeep,
                    onTriggerPanic = viewModel::triggerPanicBlackout,
                    onRequestOverlayPermission = { openOverlayPermissionScreen() },
                    onStartLiveClick = { validateAndStartLiveFlow() },
                    onStopLiveClick = viewModel::stopLiveStream,
                    onToggleMuteClick = viewModel::toggleMicMute,
                    onToggleShieldClick = viewModel::togglePrivacyShield,
                    onToggleRecordClick = viewModel::toggleLocalRecord,
                    onCaptureSnapshotClick = viewModel::captureSnapshot,
                    onAddMarkerClick = viewModel::addStreamMarker,
                    onSwitchCameraClick = viewModel::switchCameraLens,
                    onToggleTorchClick = viewModel::toggleTorch,
                    onAddGoalProgress = viewModel::addGoalProgress,
                    onReloadOverlayClick = viewModel::reloadCompositor,
                    onTestGiftClick = viewModel::triggerTestGift,
                    onTestDonationClick = viewModel::triggerTestDonation
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDrawOverlayState = Settings.canDrawOverlays(this)
    }

    private fun validateAndStartLiveFlow() {
        val current = viewModel.settings.value
        if (current.streamKey.isBlank()) {
            Toast.makeText(
                this,
                "Tempel Stream Key terlebih dahulu di tab Keamanan",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (current.dndGuardWarning && !StreamKeyVault.isDoNotDisturbActive(this)) {
            Toast.makeText(
                this,
                "Pengingat Keamanan: Aktifkan mode Jangan Ganggu (DND) agar pop-up WA/OTP tidak muncul di live",
                Toast.LENGTH_LONG
            ).show()
        }

        val neededPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        runtimePermissionLauncher.launch(neededPermissions.toTypedArray())
    }

    private fun requestScreenCaptureAndGoLive() {
        if (DiStreamService.INSTANCE == null) {
            startService(Intent(this, DiStreamService::class.java))
        }
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun openOverlayPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        val service = DiStreamService.INSTANCE
        if (isFinishing && service != null &&
            DiStreamService.runtimeState.value.status == StreamStatus.IDLE
        ) {
            stopService(Intent(this, DiStreamService::class.java))
        }
    }
}
