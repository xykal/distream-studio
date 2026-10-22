package id.distream.studio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import id.distream.studio.model.StudioThemeMode

@Immutable
data class DiStreamPalette(
    val isDark: Boolean,
    val bgCanvas: Color,
    val surfaceLow: Color,
    val surfaceMid: Color,
    val surfaceHigh: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val liveCrimson: Color,
    val okEmerald: Color,
    val warnAmber: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onAccent: Color
)

val ObsidianDarkPalette = DiStreamPalette(
    isDark = true,
    bgCanvas = Color(0xFF0A0C10),
    surfaceLow = Color(0xFF11151D),
    surfaceMid = Color(0xFF181D28),
    surfaceHigh = Color(0xFF212837),
    borderSubtle = Color(0xFF262F42),
    borderStrong = Color(0xFF394661),
    accentPrimary = Color(0xFF3B82F6),
    accentSecondary = Color(0xFF60A5FA),
    liveCrimson = Color(0xFFF43F5E),
    okEmerald = Color(0xFF10B981),
    warnAmber = Color(0xFFF59E0B),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF8492A6),
    onAccent = Color(0xFFFFFFFF)
)

val DaylightCleanPalette = DiStreamPalette(
    isDark = false,
    bgCanvas = Color(0xFFF2F5FA),
    surfaceLow = Color(0xFFFFFFFF),
    surfaceMid = Color(0xFFE9EEF6),
    surfaceHigh = Color(0xFFDFE6F2),
    borderSubtle = Color(0xFFD5DFEE),
    borderStrong = Color(0xFFB4C3DC),
    accentPrimary = Color(0xFF2563EB),
    accentSecondary = Color(0xFF1D4ED8),
    liveCrimson = Color(0xFFE11D48),
    okEmerald = Color(0xFF059669),
    warnAmber = Color(0xFFD97706),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    onAccent = Color(0xFFFFFFFF)
)

val RoyalGoldVipPalette = DiStreamPalette(
    isDark = true,
    bgCanvas = Color(0xFF0B0A08),
    surfaceLow = Color(0xFF15130F),
    surfaceMid = Color(0xFF1E1A14),
    surfaceHigh = Color(0xFF29231A),
    borderSubtle = Color(0xFF3A3122),
    borderStrong = Color(0xFF5C4B2E),
    accentPrimary = Color(0xFFD99B26),
    accentSecondary = Color(0xFFFBBF24),
    liveCrimson = Color(0xFFF43F5E),
    okEmerald = Color(0xFF10B981),
    warnAmber = Color(0xFFF59E0B),
    textPrimary = Color(0xFFFEFCE8),
    textSecondary = Color(0xFFE7E2D3),
    textMuted = Color(0xFFA39B88),
    onAccent = Color(0xFF120F07)
)

val LocalDiStreamPalette = staticCompositionLocalOf { ObsidianDarkPalette }

@Composable
fun DiStreamTheme(
    themeMode: StudioThemeMode = StudioThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        StudioThemeMode.LIGHT -> DaylightCleanPalette
        StudioThemeMode.ROYAL_GOLD -> RoyalGoldVipPalette
        StudioThemeMode.DARK -> ObsidianDarkPalette
    }
    CompositionLocalProvider(LocalDiStreamPalette provides palette) {
        content()
    }
}
