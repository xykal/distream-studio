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
import id.distream.studio.model.StudioScene
import kotlin.math.abs

/**
 * Kapsul Studio Melayang Kustom v4.0 (Zero Material — Pure Low-Latency Broadcast Overlay).
 *
 * Fitur:
 * - Bisa dites langsung kapan saja ("Tes Melayang") di atas Home Screen / Mobile Legends / TikTok tanpa harus RTMP Live
 * - Mode Kapsul Penuh <-> Mode Gelembung Mini ("DS") 1-ketuk + Magnet Tepi Layar
 * - Live CameraX Facecam PiP (Depan / Belakang, Ukuran S / M / L)
 * - Pengganti Scene Cepat (Game / Kamera / Mulai / BRB / Privasi)
 * - Laci SoundFX Cepat 48kHz Real Audio + Pengatur Opasitas (95% / 75% / 55%)
 */
class FloatingHudManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onToggleMute: () -> Unit,
    private val onTogglePrivacyShield: () -> Unit,
    private val onToggleFacecam: () -> Unit,
    private val onSelectScene: (StudioScene) -> Unit,
    private val onTriggerQuickSfx: (SoundFxPad) -> Unit,
    private val onCloseOrStop: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var hudContainer: FrameLayout? = null
    private var expandedBox: LinearLayout? = null
    private var miniBubbleView: TextView? = null
    private var statusBadgeText: TextView? = null
    private var muteButton: TextView? = null
    private var shieldButton: TextView? = null
    private var camButton: TextView? = null
    private var sizeButton: TextView? = null
    private var opacityButton: TextView? = null
    private var closeButton: TextView? = null
    private var sceneRow: LinearLayout? = null
    private var quickSfxBar: LinearLayout? = null
    private var chatPanel: LinearLayout? = null
    private var facecamWrapper: FrameLayout? = null
    private var previewView: PreviewView? = null

    private var isCollapsedBubble = false
    private var isSfxExpanded = true
    private var isSceneExpanded = true
    private var isChatExpanded = false
    private var useFrontCamera = true
    private var currentSizeIdx = 1
    private var currentAlphaStep = 0
    private val alphaSteps = listOf(0.96f, 0.78f, 0.58f)

    val isShowing: Boolean
        get() = hudContainer != null

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show(showFacecamInitially: Boolean, facecamSizeIndex: Int, opacityPercent: Int = 94) {
        if (!canDrawOverlays()) return
        if (hudContainer != null) {
            setFacecamEnabled(showFacecamInitially, facecamSizeIndex)
            hudContainer?.alpha = (opacityPercent / 100f).coerceIn(0.45f, 1.0f)
            return
        }

        currentSizeIdx = facecamSizeIndex.coerceIn(0, 2)
        val screenW = context.resources.displayMetrics.widthPixels

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
            x = dp(12)
            y = dp(76)
        }

        val wrapperRoot = FrameLayout(context).apply {
            alpha = (opacityPercent / 100f).coerceIn(0.45f, 1.0f)
        }

        // Gelembung Mini saat dikecilkan agar tidak menghalangi analog game
        val miniBubble = TextView(context).apply {
            text = "DS LIVE"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = panelDrawable("#EC11151E", "#663B82F6", dp(999), strokePx = dp(2))
            visibility = View.GONE
        }
        miniBubbleView = miniBubble

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        expandedBox = root

        // 1. Kapsul Utama Draggable
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = panelDrawable("#EE10131B", "#3DFFFFFF", dp(16))
        }

        val miniBtn = createPillButton("Kecilkan", bgHex = "#1E293B", textHex = "#93C5FD") {
            isCollapsedBubble = true
            root.visibility = View.GONE
            miniBubble.visibility = View.VISIBLE
        }

        val telemetryText = TextView(context).apply {
            text = "Mode Melayang Siap"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
        }
        statusBadgeText = telemetryText

        val btnMute = createPillButton("Mic") { onToggleMute() }
        val btnCam = createPillButton("Facecam") { onToggleFacecam() }
        val btnShield = createPillButton("Privasi") { onTogglePrivacyShield() }
        val btnAlpha = createPillButton("96%") {
            currentAlphaStep = (currentAlphaStep + 1) % alphaSteps.size
            val nextAlpha = alphaSteps[currentAlphaStep]
            wrapperRoot.alpha = nextAlpha
            opacityButton?.text = "${(nextAlpha * 100).toInt()}%"
        }
        val btnClose = createPillButton("Tutup", bgHex = "#E11D48", textHex = "#FFFFFF") {
            onCloseOrStop()
        }

        muteButton = btnMute
        camButton = btnCam
        shieldButton = btnShield
        opacityButton = btnAlpha
        closeButton = btnClose

        topBar.addView(miniBtn)
        topBar.addView(telemetryText)
        listOf(btnMute, btnCam, btnShield, btnAlpha, btnClose).forEach { v ->
            topBar.addView(spacer(4))
            topBar.addView(v)
        }

        // Sub-bar Scene & Kontrol Kamera
        val secondBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = panelDrawable("#E6141923", "#26FFFFFF", dp(13))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }
        val btnSceneToggle = createPillButton("Scene", bgHex = "#2563EB") {
            isSceneExpanded = !isSceneExpanded
            sceneRow?.visibility = if (isSceneExpanded) View.VISIBLE else View.GONE
        }
        val btnSfxToggle = createPillButton("SoundFX", bgHex = "#7C3AED") {
            isSfxExpanded = !isSfxExpanded
            quickSfxBar?.visibility = if (isSfxExpanded) View.VISIBLE else View.GONE
        }
        val btnChatToggle = createPillButton("Feed") {
            isChatExpanded = !isChatExpanded
            chatPanel?.visibility = if (isChatExpanded) View.VISIBLE else View.GONE
        }
        val btnFlip = createPillButton("Balik Lensa") {
            useFrontCamera = !useFrontCamera
            bindCameraIfVisible()
        }
        val btnCamSize = createPillButton("Ukuran M") {
            currentSizeIdx = (currentSizeIdx + 1) % 3
            val tag = when (currentSizeIdx) { 0 -> "Ukuran S"; 2 -> "Ukuran L"; else -> "Ukuran M" }
            sizeButton?.text = tag
            updateFacecamDimensions(currentSizeIdx)
        }
        sizeButton = btnCamSize

        listOf(btnSceneToggle, btnSfxToggle, btnChatToggle, btnFlip, btnCamSize).forEachIndexed { i, v ->
            if (i > 0) secondBar.addView(spacer(4))
            secondBar.addView(v)
        }

        // Baris Pilih Scene Instan
        val scenes = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = panelDrawable("#E6141923", "#26FFFFFF", dp(12))
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }
        listOf(
            "Game" to StudioScene.GAMEPLAY,
            "Kamera" to StudioScene.IRL_CAMERA,
            "Mulai" to StudioScene.STARTING_SOON,
            "BRB" to StudioScene.INTERMISSION_BRB,
            "Privasi" to StudioScene.PRIVACY_SHIELD
        ).forEachIndexed { idx, (label, sc) ->
            if (idx > 0) scenes.addView(spacer(4))
            scenes.addView(createPillButton(label, bgHex = "#1E2534") { onSelectScene(sc) })
        }
        sceneRow = scenes

        // Laci SoundFX 48kHz Real Audio
        val sfxDrawer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = panelDrawable("#E6141923", "#26FFFFFF", dp(12))
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }
        listOf(
            "Tepuk" to SoundFxPad.APPLAUSE,
            "Horn" to SoundFxPad.AIRHORN,
            "Koin" to SoundFxPad.COIN_ALERT,
            "Menang" to SoundFxPad.VICTORY,
            "Komedi" to SoundFxPad.RIMSHOT,
            "Sensor" to SoundFxPad.CENSOR_BEEP
        ).forEachIndexed { idx, (title, pad) ->
            if (idx > 0) sfxDrawer.addView(spacer(4))
            val color = if (pad == SoundFxPad.CENSOR_BEEP) "#DC2626" else "#232B3D"
            sfxDrawer.addView(createPillButton(title, bgHex = color) { onTriggerQuickSfx(pad) })
        }
        quickSfxBar = sfxDrawer

        // Panel Live Chat / Event Ringkas
        val feedContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = panelDrawable("#DF11151E", "#24FFFFFF", dp(12))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(276), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
        }
        chatPanel = feedContainer

        // Jendela Facecam PiP Live CameraX
        val (camW, camH) = resolveFacecamSizeDp(currentSizeIdx)
        val camHolder = FrameLayout(context).apply {
            background = panelDrawable("#FF0D1017", "#663B82F6", dp(16), strokePx = dp(2))
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(camW), dp(camH)).apply {
                topMargin = dp(6)
            }
        }
        val camPreview = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        camHolder.addView(camPreview)
        previewView = camPreview
        facecamWrapper = camHolder

        // Touch Drag + Edge Magnet baik untuk kapsul penuh maupun gelembung mini
        fun attachDragGesture(handleView: View, onSingleTap: (() -> Unit)? = null) {
            var initX = 0
            var initY = 0
            var downX = 0f
            var downY = 0f
            var moved = false
            handleView.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x
                        initY = params.y
                        downX = ev.rawX
                        downY = ev.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                        params.x = (initX + dx.toInt()).coerceIn(dp(4), (screenW - dp(84)).coerceAtLeast(dp(40)))
                        params.y = (initY + dy.toInt()).coerceAtLeast(dp(28))
                        runCatching { windowManager.updateViewLayout(wrapperRoot, params) }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && onSingleTap != null) {
                            onSingleTap()
                        } else if (isCollapsedBubble) {
                            params.x = if (params.x + dp(40) < screenW / 2) dp(8) else screenW - dp(88)
                            runCatching { windowManager.updateViewLayout(wrapperRoot, params) }
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        attachDragGesture(topBar)
        attachDragGesture(camHolder)
        attachDragGesture(miniBubble) {
            isCollapsedBubble = false
            miniBubble.visibility = View.GONE
            root.visibility = View.VISIBLE
            params.x = dp(12)
            runCatching { windowManager.updateViewLayout(wrapperRoot, params) }
        }

        root.addView(topBar)
        root.addView(secondBar)
        root.addView(scenes)
        root.addView(sfxDrawer)
        root.addView(feedContainer)
        root.addView(camHolder)

        wrapperRoot.addView(root)
        wrapperRoot.addView(miniBubble)

        windowManager.addView(wrapperRoot, params)
        hudContainer = wrapperRoot

        setFacecamEnabled(showFacecamInitially, currentSizeIdx)
    }

    private fun updateFacecamDimensions(sizeIndex: Int) {
        val (wDp, hDp) = resolveFacecamSizeDp(sizeIndex)
        facecamWrapper?.layoutParams = LinearLayout.LayoutParams(dp(wDp), dp(hDp)).apply {
            topMargin = dp(6)
        }
    }

    fun setFacecamEnabled(enabled: Boolean, sizeIndex: Int = currentSizeIdx) {
        currentSizeIdx = sizeIndex.coerceIn(0, 2)
        updateFacecamDimensions(currentSizeIdx)
        facecamWrapper?.visibility = if (enabled) View.VISIBLE else View.GONE
        camButton?.background = panelDrawable(
            if (enabled) "#2563EB" else "#232B3D",
            "#33FFFFFF",
            dp(9)
        )
        if (enabled) bindCameraIfVisible() else unbindCameraSafe()
    }

    private fun bindCameraIfVisible() {
        if (facecamWrapper?.visibility != View.VISIBLE || previewView == null) return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView?.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun unbindCameraSafe() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    fun renderState(state: StreamRuntimeState) {
        val mins = state.uptimeSeconds / 60
        val secs = state.uptimeSeconds % 60
        val clock = String.format("%02d:%02d", mins, secs)
        val statusText = when {
            state.isPrivacyShieldActive -> "Privasi Aktif"
            state.status == StreamStatus.LIVE -> "LIVE $clock • ${String.format("%.1fM", state.currentBitrateKbps / 1000f)}"
            state.status == StreamStatus.RECONNECTING -> "Menyambung..."
            else -> "Melayang • ${state.activeScene.label}"
        }
        statusBadgeText?.text = "$statusText • ${state.deviceTempCelsius.toInt()}C"
        closeButton?.text = if (state.status == StreamStatus.LIVE) "Stop Live" else "Tutup"

        muteButton?.text = if (state.isMicMuted) "Mic Off" else "Mic On"
        muteButton?.setTextColor(
            Color.parseColor(if (state.isMicMuted) "#FDA4AF" else "#F8FAFC")
        )
        shieldButton?.setTextColor(
            Color.parseColor(if (state.isPrivacyShieldActive) "#FCD34D" else "#F8FAFC")
        )

        renderEvents(state.liveEvents.takeLast(4))
    }

    private fun renderEvents(events: List<LiveFeedEvent>) {
        val box = chatPanel ?: return
        box.removeAllViews()
        if (events.isEmpty()) {
            box.addView(TextView(context).apply {
                text = "Feed siap menerima chat & donasi live..."
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            return
        }
        events.forEach { ev ->
            val lineColor = when (ev.type) {
                LiveEventType.GIFT, LiveEventType.DONATION -> "#93C5FD"
                LiveEventType.SYSTEM -> "#94A3B8"
                else -> "#F1F5F9"
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
        unbindCameraSafe()
        hudContainer?.let { runCatching { windowManager.removeView(it) } }
        hudContainer = null
    }

    private fun resolveFacecamSizeDp(index: Int): Pair<Int, Int> = when (index) {
        0 -> 92 to 122
        2 -> 148 to 196
        else -> 116 to 154
    }

    private fun createPillButton(
        label: String,
        bgHex: String = "#232B3D",
        textHex: String = "#F8FAFC",
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = panelDrawable(bgHex, "#29FFFFFF", dp(9))
            setOnClickListener { onClick() }
        }
    }

    private fun panelDrawable(
        fillHex: String,
        strokeHex: String,
        radiusPx: Int,
        strokePx: Int = dp(1)
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(strokePx, Color.parseColor(strokeHex))
        }
    }

    private fun spacer(widthDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(1))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
