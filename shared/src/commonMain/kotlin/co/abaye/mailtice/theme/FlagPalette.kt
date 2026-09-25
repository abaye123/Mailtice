package co.abaye.mailtice.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The "Flag" palette: a saturated royal blue in the shade of the Israeli flag, on cool near-white
 * surfaces. Values come from the approved design sheet (MD3 standard tones: primary at tone 40,
 * containers at tone 90). Roles the sheet does not list (error, inverse, onSecondary...) keep the
 * values MaterialKolor derives from the same seed, so the scheme stays complete and consistent.
 */
internal fun ColorScheme.withFlagPalette(isDark: Boolean): ColorScheme = if (isDark) flagDark() else flagLight()

private fun ColorScheme.flagLight(): ColorScheme = copy(
    primary = Color(0xFF2D53D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF5A5D72),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF621300),
    tertiaryContainer = Color(0xFF891F01),
    onTertiaryContainer = Color(0xFFFF9B82),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F2FD),
    surfaceContainer = Color(0xFFEEEDF8),
    surfaceContainerHigh = Color(0xFFE8E7F2),
    surfaceContainerHighest = Color(0xFFE2E1EC),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurface = Color(0xFF1A1B23),
    onSurfaceVariant = Color(0xFF444654),
    outline = Color(0xFF747685),
    outlineVariant = Color(0xFFC4C5D6),
    inversePrimary = Color(0xFFB7C4FF),
)

private fun ColorScheme.flagDark(): ColorScheme = copy(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002583),
    primaryContainer = Color(0xFF0038B7),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC2C5DD),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFFFB4A1),
    tertiaryContainer = Color(0xFF891F01),
    onTertiaryContainer = Color(0xFFFF9B82),
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE2E1EC),
    surface = Color(0xFF12131A),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceContainerLow = Color(0xFF1A1B23),
    surfaceContainer = Color(0xFF1E1F27),
    surfaceContainerHigh = Color(0xFF282931),
    surfaceContainerHighest = Color(0xFF33343D),
    surfaceVariant = Color(0xFF33343D),
    onSurface = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFFC4C5D6),
    outline = Color(0xFF8E90A0),
    outlineVariant = Color(0xFF444654),
    inversePrimary = Color(0xFF2D53D0),
)
