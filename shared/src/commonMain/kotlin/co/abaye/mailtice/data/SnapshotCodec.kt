package co.abaye.mailtice.data

import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.AppFont
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.ListFractionRange
import co.abaye.mailtice.domain.OfflineAttachmentLimits
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.domain.PollIntervals
import co.abaye.mailtice.domain.ReadingPane
import co.abaye.mailtice.domain.SunsetCity
import co.abaye.mailtice.domain.ThemeMode
import co.abaye.mailtice.domain.UiLanguage
import co.abaye.mailtice.domain.UserSettings

/**
 * A flat `key=value` snapshot of everything the app remembers between launches. Deliberately not
 * JSON: the file is small, hand-readable, and an unknown or malformed key falls back to its default
 * instead of taking the whole snapshot down with it.
 *
 * Only settings live here; accounts and mail are in SQLite.
 */
private const val KEY_THEME = "theme"
private const val KEY_ACCENT = "accent"
private const val KEY_DENSITY = "density"
private const val KEY_FONT = "font"
private const val KEY_HEBREW_DATE = "hebrewDate"
private const val KEY_HEBREW_AT_SUNSET = "hebrewDateAtSunset"
private const val KEY_SUNSET_CITY = "sunsetCity"
private const val KEY_OFFER_TRANSLATION = "offerTranslation"
private const val KEY_REMOTE_IMAGES = "loadRemoteImages"
private const val KEY_DOWNLOAD_FOLDER = "downloadFolder"
private const val KEY_PANE_STYLE = "paneStyle"
private const val KEY_SIDEBAR_COLLAPSED = "sidebarCollapsed"
private const val KEY_LIST_FRACTION = "listFraction"
private const val KEY_BROWSER_PROFILE = "browserProfile"
private const val KEY_READING_PANE = "readingPane"
private const val KEY_HIDDEN_FOLDERS = "hiddenFolders"
private const val KEY_PINNED_LABELS = "pinnedLabels"
private const val KEY_COLLAPSED_ACCOUNTS = "collapsedAccounts"

/** Separates the hidden folder keys; never part of a folder id (ids are label ids or IMAP names). */
private const val LIST_SEPARATOR = '\u001F'
private const val KEY_LANGUAGE = "language"
private const val KEY_LANGUAGE_AUTO = "languageAuto"
private const val KEY_POLL = "pollSeconds"
private const val KEY_SMART_POLL = "smartPolling"
private const val KEY_OPEN_HOME = "openHomeAtStart"
private const val KEY_OFFLINE = "offlineMode"
private const val KEY_OFFLINE_ATTACHMENTS = "offlineAttachmentsMb"
private const val KEY_NOTIFICATIONS = "notifications"
private const val KEY_CLOSE_TO_TRAY = "closeToTray"
private const val KEY_LAUNCH_AT_LOGIN = "launchAtLogin"

fun encodeSnapshot(data: AppData): String {
    val s = data.settings
    return buildList {
        add("$KEY_THEME=${s.theme.name}")
        add("$KEY_ACCENT=${s.accent.name}")
        add("$KEY_DENSITY=${s.density.name}")
        add("$KEY_FONT=${s.font.name}")
        add("$KEY_HEBREW_DATE=${s.showHebrewDate}")
        add("$KEY_HEBREW_AT_SUNSET=${s.hebrewDateAtSunset}")
        add("$KEY_SUNSET_CITY=${s.sunsetCity.name}")
        add("$KEY_OFFER_TRANSLATION=${s.offerTranslation}")
        add("$KEY_REMOTE_IMAGES=${s.loadRemoteImages}")
        add("$KEY_DOWNLOAD_FOLDER=${s.downloadFolder}")
        add("$KEY_PANE_STYLE=${s.paneStyle.name}")
        add("$KEY_SIDEBAR_COLLAPSED=${s.sidebarCollapsed}")
        add("$KEY_LIST_FRACTION=${s.listFraction}")
        add("$KEY_BROWSER_PROFILE=${s.browserProfile}")
        add("$KEY_READING_PANE=${s.readingPane.name}")
        add("$KEY_HIDDEN_FOLDERS=${s.hiddenFolders.joinToString(LIST_SEPARATOR.toString())}")
        add("$KEY_PINNED_LABELS=${s.pinnedLabels.joinToString(LIST_SEPARATOR.toString())}")
        add("$KEY_COLLAPSED_ACCOUNTS=${s.collapsedAccounts.joinToString(LIST_SEPARATOR.toString())}")
        add("$KEY_LANGUAGE=${s.uiLanguage.code}")
        add("$KEY_LANGUAGE_AUTO=${s.uiLanguageAuto}")
        add("$KEY_POLL=${s.pollSeconds}")
        add("$KEY_SMART_POLL=${s.smartPolling}")
        add("$KEY_OPEN_HOME=${s.openHomeAtStart}")
        add("$KEY_OFFLINE=${s.offlineMode}")
        add("$KEY_OFFLINE_ATTACHMENTS=${s.offlineAttachmentsMb}")
        add("$KEY_NOTIFICATIONS=${s.notificationsEnabled}")
        add("$KEY_CLOSE_TO_TRAY=${s.closeToTray}")
        add("$KEY_LAUNCH_AT_LOGIN=${s.launchAtLogin}")
    }.joinToString("\n")
}

fun decodeSnapshot(raw: String): AppData {
    val map = raw.lineSequence()
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        .toMap()

    fun flag(key: String, fallback: Boolean) = map[key]?.toBooleanStrictOrNull() ?: fallback
    fun list(key: String) = map[key]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() }

    val defaults = UserSettings()
    val settings = UserSettings(
        theme = map[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
        accent = map[KEY_ACCENT]?.let { name -> AccentColor.entries.firstOrNull { it.name == name } } ?: defaults.accent,
        density = map[KEY_DENSITY]?.let { name -> ListDensity.entries.firstOrNull { it.name == name } } ?: defaults.density,
        font = map[KEY_FONT]?.let { name -> AppFont.entries.firstOrNull { it.name == name } } ?: defaults.font,
        showHebrewDate = flag(KEY_HEBREW_DATE, defaults.showHebrewDate),
        hebrewDateAtSunset = flag(KEY_HEBREW_AT_SUNSET, defaults.hebrewDateAtSunset),
        offerTranslation = flag(KEY_OFFER_TRANSLATION, defaults.offerTranslation),
        loadRemoteImages = flag(KEY_REMOTE_IMAGES, defaults.loadRemoteImages),
        downloadFolder = map[KEY_DOWNLOAD_FOLDER].orEmpty(),
        sunsetCity = map[KEY_SUNSET_CITY]?.let { name -> SunsetCity.entries.firstOrNull { it.name == name } } ?: defaults.sunsetCity,
        paneStyle = map[KEY_PANE_STYLE]?.let { name -> PaneStyle.entries.firstOrNull { it.name == name } } ?: defaults.paneStyle,
        sidebarCollapsed = flag(KEY_SIDEBAR_COLLAPSED, defaults.sidebarCollapsed),
        listFraction = map[KEY_LIST_FRACTION]?.toFloatOrNull()?.takeIf { it in ListFractionRange } ?: defaults.listFraction,
        browserProfile = map[KEY_BROWSER_PROFILE].orEmpty(),
        readingPane = map[KEY_READING_PANE]?.let { name -> ReadingPane.entries.firstOrNull { it.name == name } } ?: defaults.readingPane,
        // "view:" keys hid standard folders once; those can no longer be hidden.
        hiddenFolders = list(KEY_HIDDEN_FOLDERS)?.filterNot { it.startsWith("view:") }?.toSet() ?: defaults.hiddenFolders,
        pinnedLabels = list(KEY_PINNED_LABELS)?.toSet() ?: defaults.pinnedLabels,
        collapsedAccounts = list(KEY_COLLAPSED_ACCOUNTS)?.toSet() ?: defaults.collapsedAccounts,
        uiLanguage = map[KEY_LANGUAGE]?.let { UiLanguage.fromCode(it) } ?: defaults.uiLanguage,
        uiLanguageAuto = flag(KEY_LANGUAGE_AUTO, defaults.uiLanguageAuto),
        smartPolling = flag(KEY_SMART_POLL, defaults.smartPolling),
        openHomeAtStart = flag(KEY_OPEN_HOME, defaults.openHomeAtStart),
        offlineMode = flag(KEY_OFFLINE, defaults.offlineMode),
        offlineAttachmentsMb =
        map[KEY_OFFLINE_ATTACHMENTS]?.toIntOrNull()?.takeIf { it in OfflineAttachmentLimits } ?: defaults.offlineAttachmentsMb,
        pollSeconds = map[KEY_POLL]?.toIntOrNull()?.takeIf { it in PollIntervals } ?: defaults.pollSeconds,
        notificationsEnabled = flag(KEY_NOTIFICATIONS, defaults.notificationsEnabled),
        closeToTray = flag(KEY_CLOSE_TO_TRAY, defaults.closeToTray),
        launchAtLogin = flag(KEY_LAUNCH_AT_LOGIN, defaults.launchAtLogin),
    )
    return AppData(settings = settings)
}
