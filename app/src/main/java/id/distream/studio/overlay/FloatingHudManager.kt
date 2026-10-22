package id.distream.studio.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import id.distream.studio.model.LiveEventType
import id.distream.studio.model.LiveFeedEvent
import id.distream.studio.model.SoundFxPad
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamStatus

/**
 * Panel Kontrol Melayang DiStream (In-Game Creator Bar).
 *
 * Dirancang bersih, ringan, mudah digeser, dan non-fokus (`FLAG_NOT_FOCUSABLE`)
 * sehingga kontrol analog & tombol skill game tetap 100% responsif.
 * Dilengkapi ekspansi cepat Mini SoundFX (Horn, Victory, Rimshot, Sensor 1kHz) langsung di atas game.
 */
class FloatingHudManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onToggleMute: () -> Unit,
    private val onTogglePrivacyShield: () -> Unit,
    private val onToggleFacecam: () -> Unit,
    private val onTriggerQuickSfx: (SoundFxPad) -> Unit,
    private val onStopStream: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var hudContainer: LinearLayout? = null
    private var statusBadgeText: TextView? = null
    private var muteButton: TextView? = null
    private var shieldButton: TextView? = null
    private var camButton: TextView? = null
    private var quickSfxBar: LinearLayout? = null
    private var chatPanel: LinearLayout? = null
    private var facecamWrapper: FrameLayout? = null
    private var previewView: PreviewView? = null

    private var isSfxExpanded = false
    private var isChatExpanded = true

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show(showFacecamInitially: Boolean, facecamSizeIndex: Int) {
        if (!canDrawOverlays() || hudContainer != null) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(14)
            y = dp(80)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Bar Kontrol Utama (Ergonomis, Sudut Halus 12dp)
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = panelDrawable("#EB141519", "#30FFFFFF", dp(12))
        }

        val telemetryText = TextView(context).apply {
            text = "Live 00:00 • 0 kbps"
            setTextColor(Color.parseColor("#F9FAFB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(0, 0, dp(10), 0)
        }
        statusBadgeText = telemetryText

        val btnMute = createPillButton("Mic") { onToggleMute() }
        val btnShield = createPillButton("Privasi") { onTogglePrivacyShield() }
        val btnCam = createPillButton("Kamera") { onToggleFacecam() }
        val btnSfx = createPillButton("SoundFX") {
            isSfxExpanded = !isSfxExpanded
            quickSfxBar?.visibility = if (isSfxExpanded) View.VISIBLE else View.GONE
        }
        val btnChat = createPillButton("Chat") {
            isChatExpanded = !isChatExpanded
            chatPanel?.visibility = if (isChatExpanded) View.VISIBLE else View.GONE
        }
        val btnStop = createPillButton("Selesai", bgHex = "#EF4444", textHex = "#FFFFFF") {
            onStopStream()
        }

        muteButton = btnMute
        shieldButton = btnShield
        camButton = btnCam

        topBar.addView(telemetryText)
        listOf(btnMute, btnShield, btnCam, btnSfx, btnChat, btnStop).forEachIndexed { idx, view ->
            if (idx > 0) topBar.addView(spacer(5))
            topBar.addView(view)
        }

        // Drag halus untuk memindahkan posisi bar di layar game
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        topBar.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (ev.rawX - touchX).toInt()
                    params.y = initY + (ev.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                else -> false
            }
        }

        // 2. Drawer Cepat SoundFX di dalam Game
        val sfxDrawer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = panelDrawable("#E6191B20", "#26FFFFFF", dp(10))
            visibility = View.GONE
        }
        listOf(
            SoundFxPad.AIRHORN to "Horn",
            SoundFxPad.VICTORY to "Victory",
            SoundFxPad.RIMSHOT to "Komedi",
            SoundFxPad.CENSOR_BEEP to "Sensor 1kHz"
        ).forEachIndexed { i, (pad, title) ->
            if (i > 0) sfxDrawer.addView(spacer(5))
            sfxDrawer.addView(
                createPillButton(
                    label = title,
                    bgHex = if (pad == SoundFxPad.CENSOR_BEEP) "#3B1C1C" else "#22252C"
                ) { onTriggerQuickSfx(pad) }
            )
        }
        quickSfxBar = sfxDrawer

        // 3. Bingkai Facecam PiP (CameraX)
        val (camW, camH) = resolveFacecamSizeDp(facecamSizeIndex)
        val camFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(camW), dp(camH)).apply {
                topMargin = dp(6)
            }
            background = panelDrawable("#FF121316", "#55FFFFFF", dp(12))
            clipToOutline = true
            visibility = View.GONE
        }
        val camPreview = PreviewView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        camFrame.addView(camPreview)
        facecamWrapper = camFrame
        previewView = camPreview

        // 4. Kotak Komentar & Notifikasi Gift
        val feedBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(265), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = panelDrawable("#D9141519", "#24FFFFFF", dp(12))
        }
        chatPanel = feedBox

        root.addView(topBar)
        root.addView(sfxDrawer)
        root.addView(camFrame)
        root.addView(feedBox)

        runCatching {
            windowManager.addView(root, params)
            hudContainer = root
            if (showFacecamInitially) setFacecamEnabled(true, facecamSizeIndex)
        }
    }

    fun setFacecamEnabled(enabled: Boolean, sizeIndex: Int = 1) {
        val (wDp, hDp) = resolveFacecamSizeDp(sizeIndex)
        facecamWrapper?.layoutParams = LinearLayout.LayoutParams(dp(wDp), dp(hDp)).apply {
            topMargin = dp(6)
        }
        facecamWrapper?.visibility = if (enabled) View.VISIBLE else View.GONE
        camButton?.background = panelDrawable(
            if (enabled) "#2563EB" else "#22252C",
            "#33FFFFFF",
            dp(8)
        )

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                if (enabled && previewView != null) {
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView?.surfaceProvider
                    }
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun renderState(state: StreamRuntimeState) {
        val mins = state.uptimeSeconds / 60
        val secs = state.uptimeSeconds % 60
        val clock = String.format("%02d:%02d", mins, secs)
        val statusText = when {
            state.isPrivacyShieldActive -> "Privasi Aktif"
            state.status == StreamStatus.LIVE -> "Live $clock"
            state.status == StreamStatus.RECONNECTING -> "Menyambung..."
            else -> "Siap"
        }
        val mbps = String.format("%.1f Mbps", state.currentBitrateKbps / 1000f)
        statusBadgeText?.text = "$statusText • $mbps • ${state.deviceTempCelsius.toInt()}°C"

        muteButton?.text = if (state.isMicMuted) "Mic Mati" else "Mic"
        muteButton?.setTextColor(
            Color.parseColor(if (state.isMicMuted) "#FCA5A5" else "#F9FAFB")
        )
        shieldButton?.setTextColor(
            Color.parseColor(if (state.isPrivacyShieldActive) "#FCD34D" else "#F9FAFB")
        )

        renderEvents(state.liveEvents.takeLast(4))
    }

    private fun renderEvents(events: List<LiveFeedEvent>) {
        val box = chatPanel ?: return
        box.removeAllViews()
        if (events.isEmpty()) {
            box.addView(TextView(context).apply {
                text = "Menunggu komentar & dukungan masuk..."
                setTextColor(Color.parseColor("#9CA3AF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            return
        }
        events.forEach { ev ->
            val lineColor = when (ev.type) {
                LiveEventType.GIFT, LiveEventType.DONATION -> "#93C5FD"
                LiveEventType.SYSTEM -> "#9CA3AF"
                else -> "#F3F4F6"
            }
            box.addView(TextView(context).apply {
                text = "${ev.senderName}: ${ev.message}"
                setTextColor(Color.parseColor(lineColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), 0, dp(2))
            })
        }
    }

    fun dismiss() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        hudContainer?.let { runCatching { windowManager.removeView(it) } }
        hudContainer = null
    }

    private fun resolveFacecamSizeDp(index: Int): Pair<Int, Int> = when (index) {
        0 -> 86 to 114
        2 -> 136 to 180
        else -> 108 to 144
    }

    private fun createPillButton(
        label: String,
        bgHex: String = "#22252C",
        textHex: String = "#F9FAFB",
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = panelDrawable(bgHex, "#28FFFFFF", dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun spacer(wDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(wDp), 1)
    }

    private fun panelDrawable(fillHex: String, strokeHex: String, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
