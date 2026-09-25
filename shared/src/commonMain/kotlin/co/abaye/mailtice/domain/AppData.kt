package co.abaye.mailtice.domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { System, Light, Dark }

/** How much room each message row and sidebar entry takes. */
enum class ListDensity { Compact, Comfortable, Spacious }

/**
 * How the panes are set apart. [Cards]: rounded surfaces floating on a tinted background, no lines
 * (the approved design). [Lines]: flat panes separated by dividers, rows by hairlines.
 */
enum class PaneStyle { Cards, Lines }

enum class UiLanguage(val code: String, val label: String, val rtl: Boolean) {
    Hebrew("he", "עברית", true),
    English("en", "English", false),
    ;

    companion object {
        /** Java still reports the pre-1989 ISO 639 codes on some platforms ("iw" for Hebrew). */
        private val LEGACY_CODES = mapOf("iw" to "he", "ji" to "yi", "in" to "id")

        fun fromCode(raw: String): UiLanguage {
            val code = raw.substringBefore('-').substringBefore('_').lowercase()
            val current = LEGACY_CODES[code] ?: code
            // An OS language the app does not ship falls back to English, the widest-read option.
            return entries.firstOrNull { it.code == current } ?: English
        }
    }
}

/**
 * Seed colours for MaterialKolor. The whole scheme is derived from the one the user picks, except
 * [Flag], the default, which ships a hand-tuned palette (see `theme/FlagPalette.kt`).
 */
enum class AccentColor(val seed: Color) {
    Flag(Color(0xFF0038B8)),
    Indigo(Color(0xFF4C5BD4)),
    Teal(Color(0xFF00786B)),
    Amber(Color(0xFFB4690E)),
    Rose(Color(0xFFB3245C)),
    Violet(Color(0xFF7A4FCF)),
    Slate(Color(0xFF4F5B62)),
}

/** The stripe that tells accounts apart in the unified inbox. */
enum class AccountColor(val color: Color) {
    Blue(Color(0xFF1A73E8)),
    Green(Color(0xFF188038)),
    Orange(Color(0xFFE8710A)),
    Purple(Color(0xFF9334E6)),
    Red(Color(0xFFD93025)),
    Teal(Color(0xFF12A4AF)),
    ;

    fun next(): AccountColor = entries[(ordinal + 1) % entries.size]
}

/** Allowed poll intervals. Gmail quota is generous, but a desktop app has no reason to go faster. */
val PollIntervals: List<Int> = listOf(30, 60, 120, 300)

@Immutable
data class UserSettings(
    val theme: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Flag,
    val density: ListDensity = ListDensity.Comfortable,
    val paneStyle: PaneStyle = PaneStyle.Cards,
    val uiLanguage: UiLanguage = UiLanguage.Hebrew,
    /** `true` while the interface follows the OS language rather than an explicit pick (the default). */
    val uiLanguageAuto: Boolean = true,
    val pollSeconds: Int = 60,
    val notificationsEnabled: Boolean = true,
    /** Closing the window hides it to the tray instead of quitting. */
    val closeToTray: Boolean = true,
    val launchAtLogin: Boolean = false,
)

/** Everything the app keeps in the settings snapshot. Accounts and mail live in SQLite. */
@Immutable
data class AppData(val settings: UserSettings = UserSettings())
