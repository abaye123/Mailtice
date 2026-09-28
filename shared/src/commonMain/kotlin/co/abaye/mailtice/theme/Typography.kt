package co.abaye.mailtice.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import co.abaye.mailtice.domain.AppFont
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.heebo_bold
import mailtice.shared.generated.resources.heebo_medium
import mailtice.shared.generated.resources.heebo_regular
import mailtice.shared.generated.resources.heebo_semibold
import mailtice.shared.generated.resources.noto_sans_hebrew_bold
import mailtice.shared.generated.resources.noto_sans_hebrew_medium
import mailtice.shared.generated.resources.noto_sans_hebrew_regular
import mailtice.shared.generated.resources.noto_sans_hebrew_semibold
import mailtice.shared.generated.resources.rubik_bold
import mailtice.shared.generated.resources.rubik_medium
import mailtice.shared.generated.resources.rubik_regular
import mailtice.shared.generated.resources.rubik_semibold
import org.jetbrains.compose.resources.Font

/**
 * The interface face, bundled rather than borrowed from the system. Rubik is the default; Heebo and
 * Noto Sans Hebrew are the alternatives in the settings.
 *
 * Shipping the face everywhere is what keeps the app looking like one app: the system default is
 * Roboto on Android, Segoe UI on Windows and SF on macOS, so the same screen used to render in
 * different faces, and a browser canvas has no system Hebrew face at all. Each file covers Hebrew
 * and Latin (with the French accents); anything outside it - a subject line in Cyrillic, say -
 * still falls back to the system face.
 */
@Composable
internal fun appTypography(font: AppFont = AppFont.Rubik): Typography {
    val family = fontFamily(font)
    return remember(family) { Typography().withFontFamily(family) }
}

/** The four weights of [font], also used on their own to preview each face in the settings. */
@Composable
internal fun fontFamily(font: AppFont): FontFamily {
    val files = when (font) {
        AppFont.Rubik -> listOf(Res.font.rubik_regular, Res.font.rubik_medium, Res.font.rubik_semibold, Res.font.rubik_bold)

        AppFont.Heebo -> listOf(Res.font.heebo_regular, Res.font.heebo_medium, Res.font.heebo_semibold, Res.font.heebo_bold)

        AppFont.Noto -> listOf(
            Res.font.noto_sans_hebrew_regular,
            Res.font.noto_sans_hebrew_medium,
            Res.font.noto_sans_hebrew_semibold,
            Res.font.noto_sans_hebrew_bold,
        )
    }
    val regular = Font(files[0], FontWeight.Normal)
    val medium = Font(files[1], FontWeight.Medium)
    val semibold = Font(files[2], FontWeight.SemiBold)
    val bold = Font(files[3], FontWeight.Bold)
    return remember(regular, medium, semibold, bold) { FontFamily(regular, medium, semibold, bold) }
}

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)
