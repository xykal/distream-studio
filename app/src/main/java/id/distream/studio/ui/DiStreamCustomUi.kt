package id.distream.studio.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.distream.studio.ui.theme.LocalDiStreamPalette
import kotlin.math.roundToInt

/**
 * 100% Custom Non-Material Broadcast Design System DiStream Studio v4.0.
 * Dibangun murni di atas Compose Foundation + Canvas + Physics Spring tanpa komponen Material bawaan Android.
 */

@Composable
fun DsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalDiStreamPalette.current.textPrimary,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Medium,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            lineHeight = (size.value * 1.32f).sp
        )
    )
}

@Composable
fun DsLogoMark(size: Dp = 30.dp) {
    val p = LocalDiStreamPalette.current
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    listOf(p.accentPrimary, p.liveCrimson)
                )
            )
            .padding(6.dp)
    ) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset.Zero,
            size = Size(w * 0.44f, h),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.24f,
            center = Offset(w * 0.64f, h * 0.50f)
        )
        drawCircle(
            color = p.liveCrimson,
            radius = w * 0.09f,
            center = Offset(w * 0.64f, h * 0.50f)
        )
    }
}

@Composable
fun DsPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalDiStreamPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (elevated) p.surfaceMid else p.surfaceLow)
            .border(1.dp, accentBorder ?: p.borderSubtle, shape)
            .padding(padding),
        content = content
    )
}

enum class DsBtnVariant { PRIMARY, LIVE_DANGER, SURFACE, GHOST, VIP_GOLD }

@Composable
fun DsButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DsBtnVariant = DsBtnVariant.SURFACE,
    compact: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val p = LocalDiStreamPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.955f else 1f,
        animationSpec = spring(stiffness = 620f, dampingRatio = 0.72f),
        label = "btnScale"
    )

    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    val bgBrush = when {
        !enabled -> Brush.horizontalGradient(listOf(p.surfaceMid.copy(alpha = 0.5f), p.surfaceMid.copy(alpha = 0.5f)))
        variant == DsBtnVariant.PRIMARY -> Brush.horizontalGradient(listOf(p.accentPrimary, p.accentSecondary))
        variant == DsBtnVariant.LIVE_DANGER -> Brush.horizontalGradient(listOf(p.liveCrimson, Color(0xFFBE123C)))
        variant == DsBtnVariant.VIP_GOLD -> Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B)))
        active -> Brush.horizontalGradient(listOf(p.accentPrimary.copy(alpha = 0.24f), p.accentSecondary.copy(alpha = 0.18f)))
        variant == DsBtnVariant.GHOST -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        else -> Brush.horizontalGradient(listOf(p.surfaceMid, p.surfaceHigh))
    }

    val borderColor = when {
        active -> p.accentPrimary
        variant == DsBtnVariant.PRIMARY || variant == DsBtnVariant.LIVE_DANGER || variant == DsBtnVariant.VIP_GOLD ->
            Color.White.copy(alpha = 0.16f)
        else -> p.borderSubtle
    }

    val textColor = when {
        !enabled -> p.textMuted
        variant == DsBtnVariant.PRIMARY -> p.onAccent
        variant == DsBtnVariant.LIVE_DANGER -> Color.White
        variant == DsBtnVariant.VIP_GOLD -> Color(0xFF140F05)
        active -> p.accentSecondary
        else -> p.textPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 7.dp else 10.dp
            )
    ) {
        DsText(
            text = label,
            color = textColor,
            size = if (compact) 11.5.sp else 12.5.sp,
            weight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun DsToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null
) {
    val p = LocalDiStreamPalette.current
    val knobBias by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f),
        label = "toggleBias"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceMid.copy(alpha = 0.55f))
            .border(1.dp, p.borderSubtle, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsText(text = title, size = 12.5.sp, weight = FontWeight.SemiBold)
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.accentPrimary.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        DsText(badge, size = 9.5.sp, weight = FontWeight.Bold, color = p.accentSecondary)
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                DsText(subtitle, size = 11.sp, color = p.textMuted)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(if (checked) p.accentPrimary else p.surfaceHigh)
                .border(1.dp, if (checked) p.accentSecondary else p.borderStrong, CircleShape)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((knobBias * 20.dp.toPx()).roundToInt(), 0) }
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun DsFader(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    onValueChange: (Float) -> Unit,
    accentColor: Color = LocalDiStreamPalette.current.accentPrimary
) {
    val p = LocalDiStreamPalette.current
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = ((value - valueRange.start) /
        (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)

    fun updateFromX(xPx: Float) {
        val ratio = (xPx / widthPx.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val mapped = valueRange.start + ratio * (valueRange.endInclusive - valueRange.start)
        onValueChange(mapped)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DsText(label, size = 12.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.surfaceHigh)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                DsText(formattedValue, size = 11.sp, weight = FontWeight.Bold, mono = true, color = accentColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(valueRange) {
                    detectTapGestures { offset -> updateFromX(offset.x) }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                }
        ) {
            val trackH = 8.dp.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                color = p.surfaceHigh,
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            val fillW = (size.width * fraction).coerceAtLeast(trackH)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.8f), accentColor)),
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(fillW, trackH),
                cornerRadius = CornerRadius(trackH, trackH)
            )
            val knobX = (size.width * fraction).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
            drawCircle(color = accentColor, radius = 9.dp.toPx(), center = Offset(knobX, centerY))
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(knobX, centerY))
        }
    }
}

@Composable
fun DsInputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val p = LocalDiStreamPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            DsText(label, size = 11.5.sp, weight = FontWeight.SemiBold, color = p.textSecondary)
            Spacer(Modifier.height(5.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(p.surfaceMid)
                .border(1.dp, p.borderStrong.copy(alpha = 0.7f), RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                DsText(placeholder, size = 12.5.sp, color = p.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(p.accentPrimary),
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    color = p.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (isSecret) FontFamily.Monospace else FontFamily.SansSerif
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DsVuBar(label: String, level: Float, color: Color) {
    val p = LocalDiStreamPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DsText(label, size = 10.5.sp, mono = true, color = p.textMuted, modifier = Modifier.width(42.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(p.surfaceHigh)
        ) {
            val activeW = size.width * level.coerceIn(0.02f, 1f)
            drawRoundRect(
                color = color,
                size = Size(activeW, size.height),
                cornerRadius = CornerRadius(size.height, size.height)
            )
            // Peak tick marks
            for (seg in 1..9) {
                val x = size.width * (seg / 10f)
                drawLine(
                    color = p.bgCanvas.copy(alpha = 0.45f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }
        }
    }
}

@Composable
fun DsModalScrim(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalDiStreamPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.surfaceLow)
                .border(1.5.dp, p.borderStrong, RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(18.dp),
            content = content
        )
    }
}
