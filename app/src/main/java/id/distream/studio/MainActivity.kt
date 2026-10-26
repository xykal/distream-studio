package id.distream.studio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import id.distream.studio.security.StreamKeyVault
import id.distream.studio.service.DiStreamService
import id.distream.studio.ui.MainViewModel
import id.distream.studio.ui.StudioHomeScreen
import id.distream.studio.ui.theme.DiStreamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var canDrawOverlayState by mutableStateOf(false)
    private var hasCameraPermState by mutableStateOf(false)
    private var pendingFloatingTestAfterPerm by mutableStateOf(false)

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val cameraOnlyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermState = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasCameraPermState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
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
            Toast.makeText(this, "Izin tangkap layar dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionFlags()

        startService(Intent(this, DiStreamService::class.java))

        // Minta izin Kamera & Mikrofon langsung saat pembukaan agar Live Stage Preview & Mic langsung aktif!
        if (!hasCameraPermState) {
            cameraOnlyPermissionLauncher.launch(
                buildList {
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            )
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val runtimeState by viewModel.runtimeState.collectAsState()
            val pendingOrder by viewModel.pendingOrder.collectAsState()
            val saasNotice by viewModel.saasNotice.collectAsState()
            val localAlert by viewModel.localPreviewAlert.collectAsState()
            val isCreatingOrder by viewModel.isCreatingOrder.collectAsState()

            // FLAG_SECURE tidak lagi dipasang global (menyebabkan window app tampil hitam di siaran
            // screen-capture). Proteksi sekarang dicakup per-permukaan sensitif: lihat
            // SecureWindowScope() di StudioHomeScreen.

            DiStreamTheme(themeMode = settings.themeMode) {
                StudioHomeScreen(
                    settings = settings,
                    runtimeState = runtimeState,
                    pendingOrder = pendingOrder,
                    saasNotice = saasNotice,
                    localAlertBanner = localAlert,
                    isCreatingOrder = isCreatingOrder,
                    hasOverlayPermission = canDrawOverlayState,
                    hasCameraPermission = hasCameraPermState,
                    onToggleTheme = viewModel::cycleThemeMode,
                    onUpdateSettings = viewModel::updateSettings,
                    onStartCheckout = viewModel::startSaasCheckout,
                    onConfirmOrderPaid = viewModel::confirmPendingOrderPayment,
                    onDismissOrder = viewModel::dismissPendingOrder,
                    onRedeemLicenseToken = viewModel::redeemManualLicenseToken,
                    onSaveReplayClip = viewModel::saveInstantReplayClip,
                    onAddCustomText = viewModel::addCustomTextLayer,
                    onUpdateCustomText = viewModel::updateCustomTextLayer,
                    onDeleteCustomText = viewModel::removeCustomTextLayer,
                    onResetOverlayLayout = viewModel::resetAllOverlayPositions,
                    onSelectScene = viewModel::selectScene,
                    onPlaySoundFx = viewModel::playSoundFx,
                    onStopAllSoundFx = viewModel::stopAllSoundFx,
                    onToggleCensorBeep = viewModel::setLiveCensorBeep,
                    onTriggerPanic = viewModel::triggerPanicBlackout,
                    onToggleFloatingTest = {
                        viewModel.toggleFloatingTestWindow()
                    },
                    onRequestOverlayPermission = {
                        pendingFloatingTestAfterPerm = true
                        openOverlayPermissionScreen()
                    },
                    onRequestCameraPermission = {
                        cameraOnlyPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    },
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
        refreshPermissionFlags()
        if (pendingFloatingTestAfterPerm && canDrawOverlayState) {
            pendingFloatingTestAfterPerm = false
            viewModel.toggleFloatingTestWindow()
        }
    }

    private fun refreshPermissionFlags() {
        canDrawOverlayState = Settings.canDrawOverlays(this)
        hasCameraPermState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun validateAndStartLiveFlow() {
        val current = viewModel.settings.value
        if (current.streamKey.isBlank()) {
            Toast.makeText(
                this,
                "Tempel Stream Key terlebih dahulu di tab Keamanan & Pro",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (current.dndGuardWarning && !StreamKeyVault.isDoNotDisturbActive(this)) {
            Toast.makeText(
                this,
                "Pengingat Keamanan: Aktifkan Jangan Ganggu (DND) agar notifikasi pribadi tidak tampil",
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
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun openOverlayPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
