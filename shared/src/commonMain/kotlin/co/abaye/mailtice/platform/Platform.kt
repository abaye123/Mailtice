package co.abaye.mailtice.platform

import co.abaye.mailtice.domain.UiLanguage

internal expect object Platform {
    val osLabel: String
    val appVersion: String

    /** Writable directory the app owns: settings, secrets and the database live here. */
    fun appDir(): String
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun delete(path: String): Boolean
    fun mkdir(path: String)
    fun now(): Long
    fun applyLocale(tag: String)

    /**
     * The OS language, captured at startup - [applyLocale] overwrites the default locale, so this
     * has to be read before the app ever applies its own.
     */
    fun systemLanguage(): String
    fun openUrl(url: String)

    /** Registers or removes the app from the OS login items. Returns false when the OS refused. */
    fun setLaunchAtLogin(enabled: Boolean): Boolean

    /** Desktop: window, tray, split-pane reader. False on Android. */
    val isDesktop: Boolean

    /** Shows an HTML mail body in the default browser (desktop). No-op where unsupported. */
    fun openHtml(html: String)
}

internal fun systemUiLanguage(): UiLanguage = UiLanguage.fromCode(Platform.systemLanguage())

internal fun pathSeparator(path: String): Char = if (path.contains('\\') && !path.contains('/')) '\\' else '/'

internal fun joinPath(dir: String, name: String): String {
    val sep = pathSeparator(dir)
    return if (dir.endsWith('/') || dir.endsWith('\\')) dir + name else dir + sep + name
}
