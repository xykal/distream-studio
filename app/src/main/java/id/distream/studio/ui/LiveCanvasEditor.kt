package id.distream.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.distream.studio.model.ColorGradePreset
import id.distream.studio.model.LoveTapPreset
import id.distream.studio.model.SaasPlanTier
import id.distream.studio.model.StreamOrientation
import id.distream.studio.model.StreamRuntimeState
import id.distream.studio.model.StreamSettings
import id.distream.studio.model.StudioScene
import id.distream.studio.ui.theme.LocalDiStreamPalette
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private fun parseColorSafe(hex: String, fallback: Color = Color(0xFF3B82F6)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
private fun HardwareCameraSurface(
    useFrontLens: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasCamPerm = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasCamPerm) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(
                Brush.linearGradient(listOf(Color(0xFF162033), Color(0xFF0E1420)))
            )
        ) {
            DsText(
                text = "Kamera Siap (Berikan Izin Kamera untuk Live Feed)",
                size = 10.5.sp,
                color = Color(0xFF94A3B8)
            )
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, useFrontLens) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontLens) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { future.get().unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

/**
 * Pratinjau sumber media latar di kanvas editor — pantulan dari lapisan yang sama
 * di `SceneCompositor`, supaya apa yang digeser di sini sama dengan hasil siaran.
 */
@Composable
private fun BackgroundMediaPreview(
    uriString: String,
    isVideo: Boolean,
    fitCenter: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (isVideo) {
        val textureView = remember { TextureView(context) }
        DisposableEffect(uriString) {
            val player = MediaPlayer()
            runCatching {
                player.setDataSource(context, Uri.parse(uriString))
                player.isLooping = true
                player.setVolume(0f, 0f)
                if (textureView.isAvailable) {
                    player.setSurface(Surface(textureView.surfaceTexture))
                } else {
                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: android.graphics.SurfaceTexture,
                            w: Int,
                            h: Int
                        ) {
                            runCatching { player.setSurface(Surface(st)) }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: android.graphics.SurfaceTexture,
                            w: Int,
                            h: Int
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(
                            st: android.graphics.SurfaceTexture
                        ): Boolean = true

                        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                    }
                }
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            }.onFailure { runCatching { player.release() } }
            onDispose {
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
        }
        AndroidView(factory = { textureView }, modifier = modifier)
        return
    }

    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(modifier = modifier.background(Color(0xFF111827)))
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Media latar",
            contentScale = if (fitCenter) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier
        )
    }
}

/**
 * Interactive OBS x TikTok Live Studio WYSIWYG Stage Editor v4.0.
 * Ketuk & geser langsung elemen apa pun di panggung preview untuk mengatur posisi, ukuran, warna, dan isi teks secara real-time!
 */
@Composable
fun LiveStudioCanvasStage(
    settings: StreamSettings,
    runtimeState: StreamRuntimeState,
    activeTier: SaasPlanTier,
    localAlertBanner: String,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onSelectScene: (StudioScene) -> Unit,
    onAddCustomText: (String) -> Unit,
    onUpdateCustomText: (id: String, content: String?, x: Int?, y: Int?, size: Int?, hex: String?, pill: Boolean?) -> Unit,
    onDeleteCustomText: (String) -> Unit,
    onResetLayout: () -> Unit,
    onSwitchLens: () -> Unit
) {
    val p = LocalDiStreamPalette.current
    var selectedLayerId by remember { mutableStateOf("GOAL") }
    var newTextDraft by remember { mutableStateOf("") }
    var stageW by remember { mutableIntStateOf(1) }
    var stageH by remember { mutableIntStateOf(1) }

    val animatedRatio by animateFloatAsState(
        targetValue = settings.orientation.aspectRatio,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f),
        label = "stageAspect"
    )

    val frameAccent = parseColorSafe(settings.framePreset.accentHex, p.accentPrimary)
    val cardBg = if (settings.framePreset.name == "MINIMAL_LIGHT") {
        Color(0xEBFFFFFF)
    } else {
        Color(0xDE10141D)
    }
    val cardTxt = if (settings.framePreset.name == "MINIMAL_LIGHT") {
        Color(0xFF0F172A)
    } else {
        Color(0xFFF8FAFC)
    }

    DsPanel(padding = 12.dp, elevated = true) {
        // Baris 1: Pengatur Orientasi Langsung (9:16, 16:9, 4:5, 1:1) + Safe Zone + Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamOrientation.entries.forEach { ori ->
                DsButton(
                    label = ori.ratioTag,
                    compact = true,
                    active = settings.orientation == ori,
                    onClick = { onUpdateSettings { it.copy(orientation = ori) } }
                )
            }
            DsButton(
                label = "Safe Zone",
                compact = true,
                active = settings.showSafeZoneGuide,
                onClick = { onUpdateSettings { it.copy(showSafeZoneGuide = !it.showSafeZoneGuide) } }
            )
            DsButton(
                label = "Grid 3x3",
                compact = true,
                active = settings.showGridGuide,
                onClick = { onUpdateSettings { it.copy(showGridGuide = !it.showGridGuide) } }
            )
            DsButton(
                label = if (settings.cameraFrontLens) "Lensa Depan" else "Lensa Belakang",
                compact = true,
                onClick = {
                    if (!hasCameraPermission) onRequestCameraPermission() else onSwitchLens()
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // PANGGUNG PREVIEW WYSIWYG INTERAKTIF (DRAG & RESIZE LANGSUNG!)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 210.dp, max = 430.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF06080C))
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(animatedRatio, matchHeightConstraintsFirst = settings.orientation.isPortrait)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, frameAccent.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .onSizeChanged {
                        stageW = it.width.coerceAtLeast(1)
                        stageH = it.height.coerceAtLeast(1)
                    }
            ) {
                // Layer 0: Latar Video / Kamera Real Hardware / Scene Slate
                when (settings.activeScene) {
                    StudioScene.IRL_CAMERA -> {
                        HardwareCameraSurface(
                            useFrontLens = settings.cameraFrontLens,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    StudioScene.STARTING_SOON -> {
                        SceneBackdropBox(
                            topTitle = "SIARAN SEGERA DIMULAI",
                            headline = settings.streamTitleHeadline,
                            sub = "Siapkan kopi & tap-tap layar sambil menunggu",
                            c1 = Color(0xFF0F172A),
                            c2 = Color(0xFF1E3A8A)
                        )
                    }
                    StudioScene.INTERMISSION_BRB -> {
                        SceneBackdropBox(
                            topTitle = "ISTIRAHAT SEJENAK (BRB)",
                            headline = settings.streamTitleHeadline,
                            sub = "Streamer kembali sebentar lagi — jangan keluar room!",
                            c1 = Color(0xFF1E1B4B),
                            c2 = Color(0xFF311042)
                        )
                    }
                    StudioScene.PRIVACY_SHIELD -> {
                        SceneBackdropBox(
                            topTitle = "LAYAR PRIVASI AKTIF",
                            headline = " Konten Layar Disembunyikan Sementara ",
                            sub = "OTP, Password & Chat Pribadi Terlindungi Aman",
                            c1 = Color(0xFF111827),
                            c2 = Color(0xFF090D16)
                        )
                    }
                    StudioScene.GAMEPLAY -> {
                        val gradeColors = when (settings.colorGrade) {
                            ColorGradePreset.VIVID_GAME -> listOf(Color(0xFF0F2942), Color(0xFF191338), Color(0xFF0B1324))
                            ColorGradePreset.CRISP_CONTRAST -> listOf(Color(0xFF090D16), Color(0xFF1E293B), Color(0xFF05070B))
                            ColorGradePreset.MONOCHROME -> listOf(Color(0xFF27272A), Color(0xFF18181B), Color(0xFF09090B))
                            ColorGradePreset.NATURAL -> listOf(Color(0xFF162032), Color(0xFF1E293B), Color(0xFF0F172A))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(gradeColors))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                val (rw, rh) = settings.qualityPreset.resolveDimensions(settings.orientation)
                                DsText(
                                    text = "KANVAS GAME / LAYAR UTAMA",
                                    size = 10.sp,
                                    weight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.52f)
                                )
                                DsText(
                                    text = "${rw}x${rh} • ${settings.manualFps}fps • ${String.format("%.1f", settings.manualBitrateKbps / 1000f)} Mbps",
                                    size = 9.5.sp,
                                    mono = true,
                                    color = Color.White.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }

                // Layer -1: Sumber media latar (gambar/video) — bisa digeser & dicubit di kanvas.
                if (settings.backgroundMediaUri.isNotBlank() &&
                    settings.activeScene != StudioScene.PRIVACY_SHIELD
                ) {
                    val density = LocalDensity.current
                    DraggableCanvasNode(
                        nodeId = "BG",
                        selectedId = selectedLayerId,
                        xPercent = settings.backgroundXPercent,
                        yPercent = settings.backgroundYPercent,
                        scale = settings.backgroundScale,
                        stageW = stageW,
                        stageH = stageH,
                        accentColor = frameAccent,
                        onSelect = { selectedLayerId = "BG" },
                        onDrag = { nx, ny ->
                            onUpdateSettings { it.copy(backgroundXPercent = nx, backgroundYPercent = ny) }
                        },
                        onScale = { sc -> onUpdateSettings { it.copy(backgroundScale = sc) } }
                    ) {
                        BackgroundMediaPreview(
                            uriString = settings.backgroundMediaUri,
                            isVideo = settings.backgroundMediaIsVideo,
                            fitCenter = settings.backgroundFitCenter,
                            modifier = with(density) {
                                Modifier.size(stageW.toDp(), stageH.toDp())
                            }
                        )
                    }
                }

                // Grid 3x3 & Safe Zone Guide TikTok
                if (settings.showGridGuide || settings.showSafeZoneGuide) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (settings.showGridGuide) {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            for (i in 1..2) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.22f),
                                    start = Offset(size.width * i / 3f, 0f),
                                    end = Offset(size.width * i / 3f, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.22f),
                                    start = Offset(0f, size.height * i / 3f),
                                    end = Offset(size.width, size.height * i / 3f),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )
                            }
                        }
                        if (settings.showSafeZoneGuide && settings.orientation.isPortrait) {
                            // Area kanan tombol Like/Gift TikTok & area bawah komentar TikTok
                            drawRect(
                                color = Color(0x28F43F5E),
                                topLeft = Offset(size.width * 0.84f, size.height * 0.36f),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.48f)
                            )
                            drawRect(
                                color = Color(0x223B82F6),
                                topLeft = Offset(0f, size.height * 0.78f),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.22f)
                            )
                        }
                    }
                }

                if (settings.overlayMasterEnabled && settings.activeScene != StudioScene.PRIVACY_SHIELD) {
                    // 1. Draggable Goal Bar Widget
                    if (settings.goalBarVisible) {
                        DraggableCanvasNode(
                            nodeId = "GOAL",
                            selectedId = selectedLayerId,
                            xPercent = settings.goalXPercent,
                            yPercent = settings.goalYPercent,
                            scale = settings.goalScale,
                            stageW = stageW,
                            stageH = stageH,
                            accentColor = frameAccent,
                            onSelect = { selectedLayerId = "GOAL" },
                            onDrag = { nx, ny ->
                                onUpdateSettings { it.copy(goalXPercent = nx, goalYPercent = ny) }
                            },
                            onScale = { sc -> onUpdateSettings { it.copy(goalScale = sc) } }
                        ) {
                            val pct = (settings.goalCurrent.toFloat() / settings.goalTarget.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cardBg)
                                    .border(1.dp, frameAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DsText(settings.goalTitle, size = 9.5.sp, weight = FontWeight.Bold, color = cardTxt, maxLines = 1)
                                    DsText("${settings.goalCurrent}/${settings.goalTarget}", size = 9.sp, mono = true, color = frameAccent)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pct)
                                            .height(5.dp)
                                            .clip(CircleShape)
                                            .background(frameAccent)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Draggable Tap-Tap Love / Top Supporter Badge
                    if (settings.loveTapPreset != LoveTapPreset.NONE) {
                        DraggableCanvasNode(
                            nodeId = "LOVE",
                            selectedId = selectedLayerId,
                            xPercent = settings.loveXPercent,
                            yPercent = settings.loveYPercent,
                            scale = settings.loveScale,
                            stageW = stageW,
                            stageH = stageH,
                            accentColor = frameAccent,
                            onSelect = { selectedLayerId = "LOVE" },
                            onDrag = { nx, ny ->
                                onUpdateSettings { it.copy(loveXPercent = nx, loveYPercent = ny) }
                            },
                            onScale = { sc -> onUpdateSettings { it.copy(loveScale = sc) } }
                        ) {
                            val mainTxt = when (settings.loveTapPreset) {
                                LoveTapPreset.TAP_LOVE_PILL -> "Tap-Tap Love"
                                LoveTapPreset.LIKE_TARGET_CARD -> "Target Like"
                                LoveTapPreset.TOP_SUPPORTER_BADGE -> "Top Sultan"
                                LoveTapPreset.NONE -> ""
                            }
                            val subTxt = when (settings.loveTapPreset) {
                                LoveTapPreset.TAP_LOVE_PILL -> "${runtimeState.currentLikesCount} suka"
                                LoveTapPreset.LIKE_TARGET_CARD -> "${runtimeState.currentLikesCount}/${settings.likeTargetCount}"
                                LoveTapPreset.TOP_SUPPORTER_BADGE -> runtimeState.topSupporterName
                                LoveTapPreset.NONE -> ""
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(cardBg)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(11.dp))
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF43F5E))
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    DsText(mainTxt, size = 9.5.sp, weight = FontWeight.Bold, color = cardTxt)
                                    DsText(subTxt, size = 8.5.sp, color = cardTxt.copy(alpha = 0.78f))
                                }
                            }
                        }
                    }

                    // 3. Draggable Subathon Timer Pill
                    if (settings.subathonTimerVisible) {
                        DraggableCanvasNode(
                            nodeId = "SUBATHON",
                            selectedId = selectedLayerId,
                            xPercent = settings.subathonXPercent,
                            yPercent = settings.subathonYPercent,
                            scale = settings.subathonScale,
                            stageW = stageW,
                            stageH = stageH,
                            accentColor = frameAccent,
                            onSelect = { selectedLayerId = "SUBATHON" },
                            onDrag = { nx, ny ->
                                onUpdateSettings { it.copy(subathonXPercent = nx, subathonYPercent = ny) }
                            },
                            onScale = { sc -> onUpdateSettings { it.copy(subathonScale = sc) } }
                        ) {
                            val s = runtimeState.subathonSecondsLeft
                            val formatted = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(cardBg)
                                    .border(1.dp, frameAccent.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                DsText("Subathon $formatted", size = 9.5.sp, weight = FontWeight.Bold, mono = true, color = cardTxt)
                            }
                        }
                    }

                    // 4. Draggable Live PiP Facecam Box (Real CameraX Feed!)
                    if (settings.floatingFacecamEnabled && settings.activeScene != StudioScene.IRL_CAMERA) {
                        DraggableCanvasNode(
                            nodeId = "FACECAM",
                            selectedId = selectedLayerId,
                            xPercent = settings.facecamXPercent,
                            yPercent = settings.facecamYPercent,
                            scale = settings.facecamScale,
                            stageW = stageW,
                            stageH = stageH,
                            accentColor = frameAccent,
                            onSelect = { selectedLayerId = "FACECAM" },
                            onDrag = { nx, ny ->
                                onUpdateSettings { it.copy(facecamXPercent = nx, facecamYPercent = ny) }
                            },
                            onScale = { sc -> onUpdateSettings { it.copy(facecamScale = sc) } }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(98.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, frameAccent, RoundedCornerShape(12.dp))
                            ) {
                                HardwareCameraSurface(
                                    useFrontLens = settings.cameraFrontLens,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 5. Draggable Custom Text Layers
                    settings.customTextLayers
                        .filter { it.visible }
                        .take(activeTier.maxCustomTextLayers)
                        .forEach { layer ->
                            val nodeKey = "TEXT:${layer.id}"
                            DraggableCanvasNode(
                                nodeId = nodeKey,
                                selectedId = selectedLayerId,
                                xPercent = layer.xPercent.toFloat(),
                                yPercent = layer.yPercent.toFloat(),
                                scale = 1.0f,
                                stageW = stageW,
                                stageH = stageH,
                                accentColor = frameAccent,
                                onSelect = { selectedLayerId = nodeKey },
                                onDrag = { nx, ny ->
                                    onUpdateCustomText(layer.id, null, nx.roundToInt(), ny.roundToInt(), null, null, null)
                                }
                            ) {
                                val txtCol = parseColorSafe(layer.accentHex, Color.White)
                                Box(
                                    modifier = if (layer.backgroundPill) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cardBg)
                                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    } else {
                                        Modifier.padding(2.dp)
                                    }
                                ) {
                                    DsText(
                                        text = layer.content,
                                        size = (layer.fontSizeSp * 0.78f).sp,
                                        weight = FontWeight.Bold,
                                        color = txtCol
                                    )
                                }
                            }
                        }

                    // 6. Draggable Lower-Third Running Ticker
                    if (settings.lowerThirdVisible) {
                        DraggableCanvasNode(
                            nodeId = "TICKER",
                            selectedId = selectedLayerId,
                            xPercent = 4f,
                            yPercent = settings.tickerYPercent,
                            scale = 1.0f,
                            stageW = stageW,
                            stageH = stageH,
                            accentColor = frameAccent,
                            onSelect = { selectedLayerId = "TICKER" },
                            onDrag = { _, ny ->
                                onUpdateSettings { it.copy(tickerYPercent = ny) }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cardBg)
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                DsText(
                                    text = settings.lowerThirdTickerText,
                                    size = 9.sp,
                                    weight = FontWeight.SemiBold,
                                    color = cardTxt,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 7. Watermark Badge (Otomatis hilang di Paket Harian/Mingguan/Bulanan!)
                    if (!activeTier.removeWatermark || settings.watermarkVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            val wm = if (!activeTier.removeWatermark) {
                                "DiStream Free"
                            } else if (settings.tiktokUsername.isNotBlank()) {
                                "@${settings.tiktokUsername.removePrefix("@")}"
                            } else {
                                "DiStream Pro"
                            }
                            DsText(wm, size = 8.5.sp, weight = FontWeight.Bold, color = cardTxt)
                        }
                    }

                    // 8. Live Alert Toast Banner (Saat Tes Gift / Saweria / Live Webhook Masuk!)
                    val bannerTitle = runtimeState.activeAlertBannerTitle.ifBlank {
                        localAlertBanner.substringBefore("|", "")
                    }
                    val bannerDetail = runtimeState.activeAlertBannerDetail.ifBlank {
                        localAlertBanner.substringAfter("|", "")
                    }
                    if (bannerTitle.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xF0111827))
                                .border(1.5.dp, frameAccent, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            DsText(bannerTitle, size = 11.sp, weight = FontWeight.Bold, color = frameAccent)
                            Spacer(Modifier.height(2.dp))
                            DsText(bannerDetail, size = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Baris 2: Pengganti Scene Instan (Seperti Scene Dock di OBS / TikTok Studio)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioScene.entries.forEach { sc ->
                DsButton(
                    label = sc.label + if (sc.requiresPro && !activeTier.unlockAllScenes) " [Pro]" else "",
                    compact = true,
                    active = settings.activeScene == sc,
                    onClick = { onSelectScene(sc) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Baris 3: Layer Pill Selector + Inspector Langsung untuk Elemen yang Sedang Dipilih di Kanvas!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pills = buildList {
                if (settings.backgroundMediaUri.isNotBlank()) add("BG" to "Media Latar")
                addAll(
                    listOf(
                        "GOAL" to "Target Gift",
                        "LOVE" to "Tap Love",
                        "SUBATHON" to "Subathon",
                        "FACECAM" to "Facecam PiP",
                        "TICKER" to "Running Text"
                    )
                )
            }
            pills.forEach { (id, title) ->
                DsButton(
                    label = title,
                    compact = true,
                    active = selectedLayerId == id,
                    onClick = { selectedLayerId = id }
                )
            }
            settings.customTextLayers.forEachIndexed { idx, layer ->
                DsButton(
                    label = "Teks ${idx + 1}",
                    compact = true,
                    active = selectedLayerId == "TEXT:${layer.id}",
                    onClick = { selectedLayerId = "TEXT:${layer.id}" }
                )
            }
            DsButton(label = "Reset Posisi", compact = true, onClick = onResetLayout)
        }

        Spacer(Modifier.height(8.dp))

        // Inspector Kontekstual: Edit Skala, Teks & Warna langsung di bawah Kanvas!
        SelectedLayerInspector(
            selectedLayerId = selectedLayerId,
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onUpdateCustomText = onUpdateCustomText,
            onDeleteCustomText = { id ->
                onDeleteCustomText(id)
                selectedLayerId = "GOAL"
            }
        )

        Spacer(Modifier.height(8.dp))

        // Tambah Teks Overlay Baru Cepat
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DsInputField(
                label = "Tambah Layer Teks ke Kanvas (Geser bebas di atas layar)",
                value = newTextDraft,
                placeholder = "Contoh: Open Mabar ID 82910422 / Saweria di Bio",
                onValueChange = { newTextDraft = it },
                modifier = Modifier.weight(1f)
            )
            DsButton(
                label = "+ Tambah",
                variant = DsBtnVariant.PRIMARY,
                onClick = {
                    if (newTextDraft.isNotBlank()) {
                        onAddCustomText(newTextDraft)
                        newTextDraft = ""
                    }
                }
            )
        }
    }
}

@Composable
private fun SelectedLayerInspector(
    selectedLayerId: String,
    settings: StreamSettings,
    onUpdateSettings: ((StreamSettings) -> StreamSettings) -> Unit,
    onUpdateCustomText: (id: String, content: String?, x: Int?, y: Int?, size: Int?, hex: String?, pill: Boolean?) -> Unit,
    onDeleteCustomText: (String) -> Unit
) {
    val p = LocalDiStreamPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceLow)
            .border(1.dp, p.borderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            selectedLayerId == "BG" -> {
                DsFader(
                    label = "Skala Media Latar (atau cubit dua jari langsung di kanvas)",
                    value = settings.backgroundScale,
                    valueRange = 0.45f..2.6f,
                    formattedValue = "${(settings.backgroundScale * 100).roundToInt()}%",
                    onValueChange = { sc -> onUpdateSettings { it.copy(backgroundScale = sc) } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DsButton(
                        label = if (settings.backgroundFitCenter) "Muat Pas (Fit)" else "Isi Penuh (Cover)",
                        compact = true,
                        active = settings.backgroundFitCenter,
                        onClick = { onUpdateSettings { it.copy(backgroundFitCenter = !it.backgroundFitCenter) } }
                    )
                    DsButton(
                        label = "Reset Posisi",
                        compact = true,
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    backgroundXPercent = 0f,
                                    backgroundYPercent = 0f,
                                    backgroundScale = 1f
                                )
                            }
                        }
                    )
                    DsButton(
                        label = "Hapus Media",
                        compact = true,
                        variant = DsBtnVariant.LIVE_DANGER,
                        onClick = {
                            onUpdateSettings {
                                it.copy(backgroundMediaUri = "", backgroundMediaIsVideo = false)
                            }
                        }
                    )
                }
                DsText(
                    "Geser satu jari untuk pindah, cubit dua jari untuk skala. Media latar tampil " +
                        "di belakang semua widget.",
                    size = 10.5.sp,
                    color = p.textMuted
                )
            }
            selectedLayerId == "GOAL" -> {
                DsFader(
                    label = "Ukuran Widget Target Gift (Geser langsung di kanvas untuk pindah posisi)",
                    value = settings.goalScale,
                    valueRange = 0.65f..1.55f,
                    formattedValue = "${(settings.goalScale * 100).roundToInt()}%",
                    onValueChange = { sc -> onUpdateSettings { it.copy(goalScale = sc) } }
                )
                DsInputField(
                    label = "Judul Target Gift",
                    value = settings.goalTitle,
                    placeholder = "Target Gift Malam Ini",
                    onValueChange = { v -> onUpdateSettings { it.copy(goalTitle = v) } }
                )
            }
            selectedLayerId == "LOVE" -> {
                DsFader(
                    label = "Ukuran Widget Tap-Tap Love",
                    value = settings.loveScale,
                    valueRange = 0.65f..1.60f,
                    formattedValue = "${(settings.loveScale * 100).roundToInt()}%",
                    onValueChange = { sc -> onUpdateSettings { it.copy(loveScale = sc) } }
                )
            }
            selectedLayerId == "SUBATHON" -> {
                DsFader(
                    label = "Ukuran Timer Subathon",
                    value = settings.subathonScale,
                    valueRange = 0.65f..1.60f,
                    formattedValue = "${(settings.subathonScale * 100).roundToInt()}%",
                    onValueChange = { sc -> onUpdateSettings { it.copy(subathonScale = sc) } }
                )
            }
            selectedLayerId == "FACECAM" -> {
                DsFader(
                    label = "Ukuran Jendela Facecam PiP",
                    value = settings.facecamScale,
                    valueRange = 0.60f..1.85f,
                    formattedValue = "${(settings.facecamScale * 100).roundToInt()}%",
                    onValueChange = { sc -> onUpdateSettings { it.copy(facecamScale = sc) } }
                )
            }
            selectedLayerId == "TICKER" -> {
                DsInputField(
                    label = "Teks Running Bar Bawah",
                    value = settings.lowerThirdTickerText,
                    placeholder = "Teks berjalan...",
                    onValueChange = { v -> onUpdateSettings { it.copy(lowerThirdTickerText = v) } }
                )
            }
            selectedLayerId.startsWith("TEXT:") -> {
                val id = selectedLayerId.removePrefix("TEXT:")
                val layer = settings.customTextLayers.find { it.id == id }
                if (layer != null) {
                    DsInputField(
                        label = "Edit Teks Layer Terpilih",
                        value = layer.content,
                        placeholder = "Tulis teks...",
                        onValueChange = { v -> onUpdateCustomText(layer.id, v, null, null, null, null, null) }
                    )
                    DsFader(
                        label = "Ukuran Huruf",
                        value = layer.fontSizeSp.toFloat(),
                        valueRange = 10f..28f,
                        formattedValue = "${layer.fontSizeSp} sp",
                        onValueChange = { sz ->
                            onUpdateCustomText(layer.id, null, null, null, sz.roundToInt(), null, null)
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("#FFFFFF", "#60A5FA", "#FBBF24", "#34D399", "#FB7185").forEach { hex ->
                            val c = parseColorSafe(hex, Color.White)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (layer.accentHex == hex) 2.5.dp else 1.dp,
                                        color = if (layer.accentHex == hex) p.accentPrimary else p.borderStrong,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onUpdateCustomText(layer.id, null, null, null, null, hex, null)
                                    }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        DsButton(
                            label = if (layer.backgroundPill) "Kapsul On" else "Teks Polos",
                            compact = true,
                            onClick = {
                                onUpdateCustomText(layer.id, null, null, null, null, null, !layer.backgroundPill)
                            }
                        )
                        DsButton(
                            label = "Hapus",
                            compact = true,
                            variant = DsBtnVariant.LIVE_DANGER,
                            onClick = { onDeleteCustomText(layer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneBackdropBox(
    topTitle: String,
    headline: String,
    sub: String,
    c1: Color,
    c2: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(c1, c2)))
            .padding(18.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DsText(topTitle, size = 10.sp, weight = FontWeight.Bold, color = Color(0xFF93C5FD))
            Spacer(Modifier.height(6.dp))
            DsText(headline, size = 15.sp, weight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            DsText(sub, size = 10.sp, color = Color(0xFFCBD5E1))
        }
    }
}

/** Magic snap: elemen "nempel" ke tepi kiri, tengah, atau kanan kanvas. Setara snap di OBS. */
private fun snapAxis(value: Float, anchors: FloatArray, tolerance: Float = 1.6f): Float {
    anchors.forEach { a -> if (abs(value - a) <= tolerance) return a }
    return value
}

private val SNAP_X = floatArrayOf(2f, 50f, 84f)
private val SNAP_Y = floatArrayOf(2f, 50f, 92f)

@Composable
private fun DraggableCanvasNode(
    nodeId: String,
    selectedId: String,
    xPercent: Float,
    yPercent: Float,
    scale: Float,
    stageW: Int,
    stageH: Int,
    accentColor: Color,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onScale: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val latestX by rememberUpdatedState(xPercent)
    val latestY by rememberUpdatedState(yPercent)
    val latestScale by rememberUpdatedState(scale)
    val isSelected = nodeId == selectedId

    val pxX = ((xPercent / 100f) * stageW).roundToInt()
    val pxY = ((yPercent / 100f) * stageH).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(pxX, pxY) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, accentColor, RoundedCornerShape(11.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onSelect() }
            // Satu jari = geser, dua jari = cubit untuk skala (persis drag & scale di OBS).
            .pointerInput(nodeId, stageW, stageH, onScale != null) {
                awaitEachGesture {
                    // Nilai awal dibaca ulang setiap gesture supaya tidak "liar" setelah reset.
                    var accX = latestX
                    var accY = latestY
                    var accScale = latestScale
                    awaitFirstDown(requireUnconsumed = false)
                    onSelect()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        if (pan != Offset.Zero) {
                            accX = (accX + (pan.x / stageW.coerceAtLeast(1)) * 100f)
                                .coerceIn(1f, 84f)
                            accY = (accY + (pan.y / stageH.coerceAtLeast(1)) * 100f)
                                .coerceIn(2f, 92f)
                            onDrag(snapAxis(accX, SNAP_X), snapAxis(accY, SNAP_Y))
                        }
                        if (zoom != 1f && onScale != null) {
                            accScale = (accScale * zoom).coerceIn(0.45f, 2.6f)
                            onScale.invoke(accScale)
                        }
                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent()
                    }
                }
            }
    ) {
        content()
    }
}
