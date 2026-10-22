package id.distream.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import id.distream.studio.model.StudioThemeMode

// Palet Ergonomis Creator Studio (Refined Graphite & Clean Daylight — Tanpa Nuansa Cyber/Neon)
val BrandBlue = Color(0xFF2563EB)
val BrandBlueSoft = Color(0xFF60A5FA)
val LiveRed = Color(0xFFEF4444)
val HealthyGreen = Color(0xFF10B981)
val CautionAmber = Color(0xFFF59E0B)

private val DiStreamDarkScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueSoft,
    onSecondary = Color(0xFF0B192C),
    background = Color(0xFF121316),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF191B20),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF22252C),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2D313A)
)

private val DiStreamLightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF1D4ED8),
    onSecondary = Color.White,
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB)
)

@Composable
fun DiStreamTheme(
    themeMode: StudioThemeMode = StudioThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val scheme = if (themeMode == StudioThemeMode.LIGHT) DiStreamLightScheme else DiStreamDarkScheme
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
