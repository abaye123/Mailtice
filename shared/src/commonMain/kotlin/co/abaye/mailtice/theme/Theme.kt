package co.abaye.mailtice.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.AppFont
import com.materialkolor.rememberDynamicColorScheme

/**
 * The whole Material 3 scheme is generated from the accent the user picked, so the app has one
 * knob for colour instead of a hand-maintained pair of light and dark palettes. The one exception
 * is [AccentColor.Flag], whose designed palette is laid over the generated scheme.
 *
 * Exposed on its own so the desktop window can paint its title bar with the same scheme the
 * content uses - the chrome lives outside [AppTheme]'s composition.
 *
 * Typography is the bundled face the user picked ([AppFont], Rubik by default), see [appTypography].
 */
@Composable
fun rememberAppColorScheme(accent: AccentColor = AccentColor.Flag, isDark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val generated = rememberDynamicColorScheme(seedColor = accent.seed, isDark = isDark)
    return if (accent == AccentColor.Flag) remember(generated, isDark) { generated.withFlagPalette(isDark) } else generated
}

@Composable
fun AppTheme(
    accent: AccentColor,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    font: AppFont = AppFont.Rubik,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = rememberAppColorScheme(accent, isDark),
        typography = appTypography(font),
    ) {
        Surface(modifier = modifier, content = content)
    }
}
