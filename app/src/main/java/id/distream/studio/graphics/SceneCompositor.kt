package id.distream.studio.graphics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * SceneKompositor DiStream — satu pohon View yang menjadi SATU sumber untuk:
 *
 *  1. Lapisan 0  : WebView overlay milik provider donasi (Saweria / Sociabuzz / BagiBagi),
 *                  dimuat LANGSUNG lewat `loadUrl()` — bukan lagi `<iframe>` seperti versi v4.0.0
 *                  yang gagal total karena `X-Frame-Options` / CSP dari halaman provider.
 *  2. Lapisan 1  : WebView widget DiStream (goal bar, love, subathon, watermark, ticker, teks kustom).
 *  3. Lapisan 2  : Kamera facecam (CameraX `PreviewView` mode COMPATIBLE = TextureView,
 *                  karena SurfaceView tidak bisa digambar ke kanvas GL encoder).
 *
 * Seluruh pohon ini diukur/dilayout manual pada resolusi stream, lalu diserahkan ke
 * `ViewFilterRender` milik RootEncoder agar ikut ter-encode ke RTMP.
 *
 * Catatan penting:
 * - `PreviewView` hanya bisa menghasilkan frame jika View ini TERPASANG di sebuah Window.
 *   Karena itu disediakan [attachOffscreenWindow] yang menempelkan pohon View ke jendela
 *   overlay (alpha 0.01, tidak bisa disentuh). Tanpa izin `SYSTEM_ALERT_WINDOW`, komposisi
 *   kamera tidak diaktifkan dan pemanggil harus jatuh kembali ke `Camera2Source` encoder.
 * - Audio dari WebView SELALU dimatikan: suara alert harus lewat `ProBroadcastDspEffect`
 *   supaya benar-benar terdengar penonton di jalur RTMP.
 */
class SceneCompositor(private val context: Context) {

    val root: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = true
        clipToPadding = true
    }

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var widgetView: WebView? = null
    private var providerView: WebView? = null
    private var facecamView: PreviewView? = null

    private var cameraBound = false
    private var frontLens = true
    private var facecamEnabled = false
    private var facecamFullScreen = false
    private var facecamLayout = FacecamLayout(72f, 58f, 28f)
    private var stageWidth = 1080
    private var stageHeight = 1920

    private var overlayAttached = false
    private var overlayParams: WindowManager.LayoutParams? = null

    data class FacecamLayout(
        /** posisi kiri dalam persen (0..100) */
        val xPercent: Float,
        /** posisi atas dalam persen (0..100) */
        val yPercent: Float,
        /** lebar facecam dalam persen dari lebar kanvas */
        val widthPercent: Float
    )

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildTransparentWebView(name: String): WebView =
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    muteAllMedia(view)
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            contentDescription = name
        }

    /** WebView widget DiStream (goal bar, love, ticker, teks kustom). */
    fun widgetWebView(): WebView {
        widgetView?.let { return it }
        val view = buildTransparentWebView("distream_widgets")
        widgetView = view
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return view
    }

    /** Memuat halaman overlay milik provider secara LANGSUNG (menggantikan pendekatan iframe). */
    @SuppressLint("SetJavaScriptEnabled")
    fun setProviderOverlay(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) {
            providerView?.let { existing ->
                runCatching { existing.loadUrl("about:blank") }
                root.removeView(existing)
                existing.destroy()
            }
            providerView = null
            return
        }
        val view = providerView ?: buildTransparentWebView("provider_overlay").also { created ->
            providerView = created
            // provider di bawah widget DiStream
            root.addView(
                created,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        if (view.url != clean) {
            runCatching { view.loadUrl(clean) }
        }
    }

    fun setProviderOverlayVisible(visible: Boolean) {
        providerView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun reloadProviderOverlay() {
        providerView?.let { v -> runCatching { v.reload() } }
    }

    private fun muteAllMedia(view: WebView?) {
        view ?: return
        runCatching {
            view.evaluateJavascript(
                """
                (function() {
                  try {
                    document.querySelectorAll('audio,video').forEach(function(m){
                      m.muted = true; m.volume = 0; m.removeAttribute('autoplay');
                    });
                    var unlock = function(){ try { if (window.AudioContext) new AudioContext().resume(); } catch(e){} };
                    document.addEventListener('click', unlock, { once: true });
                  } catch (e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun pushWidgetState(jsonPayload: String) {
        widgetView?.evaluateJavascript(
            "window.syncDiStreamLayers && window.syncDiStreamLayers($jsonPayload);",
            null
        )
    }

    fun runWidgetScript(script: String) {
        widgetView?.evaluateJavascript(script, null)
    }

    fun loadWidgetHtml(html: String) {
        widgetWebView().loadDataWithBaseURL(
            "https://compositor.distream.studio/",
            html,
            "text/html",
            "utf-8",
            null
        )
    }

    fun widgetPageReady(): Boolean = (widgetView?.progress ?: 0) >= 100

    // ---------------------------------------------------------------- Kamera

    /**
     * Menyalakan facecam di dalam pohon View (satu-satunya pemilik kamera saat mode komposit aktif).
     * @param fullScreen true = kamera memenuhi kanvas (scene IRL), false = PiP menghormati [facecamLayout].
     */
    fun enableFacecam(
        lifecycleOwner: LifecycleOwner,
        useFrontLens: Boolean,
        fullScreen: Boolean,
        layout: FacecamLayout
    ) {
        ensureFacecamView()
        facecamEnabled = true
        facecamFullScreen = fullScreen
        facecamLayout = layout
        if (frontLens != useFrontLens || !cameraBound) {
            frontLens = useFrontLens
            bindCamera(lifecycleOwner)
        }
        applyFacecamLayoutParams()
    }

    fun disableFacecam() {
        facecamEnabled = false
        facecamView?.visibility = View.GONE
        unbindCamera()
    }

    fun isFacecamEnabled(): Boolean = facecamEnabled

    fun switchLens(lifecycleOwner: LifecycleOwner) {
        frontLens = !frontLens
        if (facecamEnabled) bindCamera(lifecycleOwner)
    }

    private fun ensureFacecamView() {
        if (facecamView != null) return
        val preview = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.VISIBLE
        }
        facecamView = preview
        root.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val preview = facecamView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                val useCase = Preview.Builder().build().also {
                    it.surfaceProvider = preview.surfaceProvider
                }
                val selector = if (frontLens) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.bindToLifecycle(lifecycleOwner, selector, useCase)
                cameraBound = true
            }.onFailure {
                cameraBound = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbindCamera() {
        cameraBound = false
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    private fun applyFacecamLayoutParams() {
        val view = facecamView ?: return
        if (facecamFullScreen) {
            view.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        } else {
            val w = (stageWidth * (facecamLayout.widthPercent.coerceIn(12f, 100f) / 100f)).toInt()
                .coerceAtLeast(64)
            // rasio 3:4 potret untuk PiP facecam
            val h = (w * 4f / 3f).toInt().coerceAtLeast(64)
            val lp = FrameLayout.LayoutParams(w, h).apply {
                leftMargin = (stageWidth * (facecamLayout.xPercent.coerceIn(0f, 100f) / 100f)).toInt()
                topMargin = (stageHeight * (facecamLayout.yPercent.coerceIn(0f, 100f) / 100f)).toInt()
                gravity = Gravity.TOP or Gravity.START
            }
            view.layoutParams = lp
        }
        view.requestLayout()
    }

    // ---------------------------------------------------------------- Layout untuk GL

    /**
     * Mengukur & melayout seluruh pohon View pada resolusi stream. Wajib dipanggil sebelum
     * menyerahkan [root] ke `ViewFilterRender` (filter ini tidak pernah mengukur View sendiri).
     */
    fun layoutAt(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (stageWidth != width || stageHeight != height) {
            stageWidth = width
            stageHeight = height
            applyFacecamLayoutParams()
            updateOffscreenWindowSize()
        }
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        root.measure(wSpec, hSpec)
        root.layout(0, 0, width, height)
    }

    // ---------------------------------------------------------------- Window offscreen

    /**
     * Menempelkan pohon View ke jendela overlay berukuran sama dengan kanvas stream, alpha 0.01,
     * tidak bisa disentuh/difokus. Tujuannya semata agar `TextureView` kamera punya SurfaceTexture
     * yang hidup, sehingga frame kamera benar-benar sampai ke encoder.
     *
     * @return false bila izin "Gambar di atas aplikasi lain" belum diberikan.
     */
    fun attachOffscreenWindow(): Boolean {
        if (overlayAttached) return true
        val wm = windowManager ?: return false
        if (!canDrawOverlays()) return false

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            stageWidth,
            stageHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 0.01f
        }

        // pohon View hanya boleh punya satu parent
        (root.parent as? android.view.ViewGroup)?.removeView(root)
        overlayParams = params
        return runCatching {
            wm.addView(root, params)
            overlayAttached = true
            true
        }.getOrElse { false }
    }

    /** Menyamakan ukuran jendela overlay dengan resolusi kanvas (mis. 1920x1080 vs 1080x1920). */
    private fun updateOffscreenWindowSize() {
        if (!overlayAttached) return
        val wm = windowManager ?: return
        val params = overlayParams ?: return
        if (params.width == stageWidth && params.height == stageHeight) return
        params.width = stageWidth
        params.height = stageHeight
        runCatching { wm.updateViewLayout(root, params) }
    }

    fun detachOffscreenWindow() {
        if (!overlayAttached) return
        val wm = windowManager ?: return
        runCatching { wm.removeView(root) }
        overlayAttached = false
        overlayParams = null
    }

    fun isOverlayWindowAttached(): Boolean = overlayAttached

    private fun canDrawOverlays(): Boolean = canDrawOverlays(context)

    // ---------------------------------------------------------------- Lifecycle

    fun release() {
        disableFacecam()
        detachOffscreenWindow()
        runCatching { widgetView?.destroy() }
        runCatching { providerView?.destroy() }
        widgetView = null
        providerView = null
        facecamView = null
        root.removeAllViews()
    }

    companion object {
        /**
         * Komposisi kamera ke encoder hanya mungkin bila aplikasi boleh menggambar jendela overlay
         * (jendela itulah yang membuat TextureView kamera punya SurfaceTexture hidup).
         * Dipakai pemanggil untuk memilih jalur: komposit kamera ATAU Camera2Source encoder.
         */
        fun canDrawOverlays(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

        /**
         * Bitmap dasar netral untuk scene yang seluruh visualnya berasal dari kompositor
         * (mis. scene kamera penuh). RootEncoder mewajibkan tetap ada satu `VideoSource`.
         */
        fun createNeutralBaseBitmap(): Bitmap =
            Bitmap.createBitmap(64, 112, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLACK)
            }
    }
}
