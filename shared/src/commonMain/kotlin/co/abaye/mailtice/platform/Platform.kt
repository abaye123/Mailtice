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

    /**
     * Saves [bytes] as [fileName] under Downloads/Mailtice/[folder] (the folder may be empty). A name
     * that already exists gets " (2)", " (3)"... Returns a path or URI for [revealDownload], or null
     * when the file could not be written.
     */
    fun saveDownload(folder: String, fileName: String, bytes: ByteArray): String?

    /** Shows where [location] (from [saveDownload]) was saved: the folder in the file manager. */
    fun revealDownload(location: String)

    /** The OS "open files" dialog, several files at once; empty when cancelled or unsupported. */
    suspend fun pickFiles(title: String): List<PickedFile>

    /** False where [pickFiles] has no dialog yet (Android): the compose window hides "attach". */
    val canPickFiles: Boolean
}

/** A file the user picked to attach. */
class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray)

/** A file or folder name every OS accepts: no separators or reserved characters, not too long. */
internal fun safeFileName(raw: String, fallback: String = "Mailtice"): String {
    val cleaned = raw.map { c -> if (c in "\\/:*?\"<>|" || c.code < 32) '_' else c }.joinToString("")
        .trim().trimEnd('.').take(80).trim()
    return cleaned.ifBlank { fallback }
}

internal fun systemUiLanguage(): UiLanguage = UiLanguage.fromCode(Platform.systemLanguage())

internal fun pathSeparator(path: String): Char = if (path.contains('\\') && !path.contains('/')) '\\' else '/'

internal fun joinPath(dir: String, name: String): String {
    val sep = pathSeparator(dir)
    return if (dir.endsWith('/') || dir.endsWith('\\')) dir + name else dir + sep + name
}
