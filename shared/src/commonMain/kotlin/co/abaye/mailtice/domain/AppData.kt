package co.abaye.mailtice.domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { System, Light, Dark }

/** How much room each message row and sidebar entry takes. */
enum class ListDensity { Compact, Comfortable, Spacious }

/** Where sunset is computed for the Hebrew date; a few minutes apart across Israel, far apart abroad. */
enum class SunsetCity(val latitude: Double, val longitude: Double, val zoneId: String) {
    Jerusalem(31.7683, 35.2137, "Asia/Jerusalem"),
    TelAviv(32.0853, 34.7818, "Asia/Jerusalem"),
    Haifa(32.7940, 34.9896, "Asia/Jerusalem"),
    BeerSheva(31.2518, 34.7913, "Asia/Jerusalem"),
    Eilat(29.5577, 34.9519, "Asia/Jerusalem"),
    NewYork(40.7128, -74.0060, "America/New_York"),
    London(51.5074, -0.1278, "Europe/London"),
}

/** The interface typeface. All three are bundled and cover Hebrew and Latin; Rubik is the default. */
enum class AppFont(val displayName: String) { Rubik("Rubik"), Heebo("Heebo"), Noto("Noto Sans") }

/**
 * How the panes are set apart. [Cards]: rounded surfaces floating on a tinted background, no lines
 * (the approved design). [Lines]: flat panes separated by dividers, rows by hairlines.
 */
enum class PaneStyle { Cards, Lines }

/**
 * Where an opened message shows on a wide window. [Split]: beside the list. [Off]: Gmail's default,
 * the list takes the whole width and an opened message replaces it until "back".
 */
enum class ReadingPane { Split, Off }

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

/** How far the list / reader divider may be dragged. */
val ListFractionRange: ClosedFloatingPointRange<Float> = 0.25f..0.7f

/** Allowed poll intervals. Gmail quota is generous, but a desktop app has no reason to go faster. */
val PollIntervals: List<Int> = listOf(30, 60, 120, 300)

@Immutable
data class UserSettings(
    val theme: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Flag,
    val density: ListDensity = ListDensity.Comfortable,
    val font: AppFont = AppFont.Rubik,
    /** The Hebrew date next to the civil one, in the list and the reader. */
    val showHebrewDate: Boolean = true,
    /** The Hebrew date turns at sunset in [sunsetCity]; otherwise at midnight with the civil date. */
    val hebrewDateAtSunset: Boolean = true,
    val sunsetCity: SunsetCity = SunsetCity.Jerusalem,
    val paneStyle: PaneStyle = PaneStyle.Cards,
    /** Sidebar reduced to icons. */
    val sidebarCollapsed: Boolean = false,
    /** Share of the width the message list takes next to the reader, [ListFractionRange]. */
    val listFraction: Float = 0.42f,
    val readingPane: ReadingPane = ReadingPane.Split,
    /**
     * Sidebar entries the user hid (they move under "More"): "view:<MailView>" for a standard folder,
     * "<accountId>/<folderId>" for a label or custom folder.
     */
    val hiddenFolders: Set<String> = emptySet(),
    /** The browser profile picked for the last sign-in ([co.abaye.mailtice.auth.BrowserProfile.key]), "" = default browser. */
    val browserProfile: String = "",
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
