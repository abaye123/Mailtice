package co.abaye.mailtice.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import co.abaye.mailtice.domain.AccentColor
import com.materialkolor.rememberDynamicColorScheme

/**
 * The whole Material 3 scheme is generated from the accent the user picked, so the app has one
 * knob for colour instead of a hand-maintained pair of light and dark palettes. The one exception
 * is [AccentColor.Flag], whose designed palette is laid over the generated scheme.
 *
 * Exposed on its own so the desktop window can paint its title bar with the same scheme the
 * content uses - the chrome lives outside [AppTheme]'s composition.
 *
 * Typography is Material 3 default on Android and desktop. The web actual swaps in Noto Sans
 * Hebrew so the Skiko canvas has a face for that script (the Noto downloader is Compose 1.12).
 */
@Composable
fun rememberAppColorScheme(accent: AccentColor = AccentColor.Flag, isDark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val generated = rememberDynamicColorScheme(seedColor = accent.seed, isDark = isDark)
    return if (accent == AccentColor.Flag) remember(generated, isDark) { generated.withFlagPalette(isDark) } else generated
}

@Composable
fun AppTheme(accent: AccentColor, isDark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = rememberAppColorScheme(accent, isDark),
        typography = appTypography(),
    ) {
        Surface(modifier = modifier, content = content)
    }
}
