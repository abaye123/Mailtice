package co.abaye.mailtice.platform

import java.io.File

/**
 * The file beside Mailtice.exe that makes a copy portable. A file of its own rather than the data
 * folder alone: a folder named "data" can sit next to the executable by chance (a downloads
 * folder), and the app must not move its mail there unasked.
 */
internal const val PORTABLE_MARKER = "Mailtice.portable"

/** The folder beside Mailtice.exe that holds everything a portable copy keeps. */
internal const val PORTABLE_DATA_DIR = "data"

/**
 * The portable build (Windows): with [PORTABLE_MARKER] beside Mailtice.exe, the database, settings
 * and secrets live in [PORTABLE_DATA_DIR] next to it, so the whole folder can sit on a USB stick
 * and run on any computer without writing to the user's profile. The release ships it as a zip
 * with the marker already in it; an installed copy has none, and runs as before.
 */
internal object PortableMode {
    /** The data folder, or null when this is not a portable copy. */
    val dataDir: File? by lazy {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return@lazy null
        portableDataDir(ProcessHandle.current().info().command().orElse(null))
    }

    val enabled: Boolean get() = dataDir != null
}

/**
 * [PORTABLE_DATA_DIR] beside the executable at [exePath] when [PORTABLE_MARKER] is there too (the
 * folder itself is made on first use). A JDK's java.exe is never portable: that is a run from
 * source, and the JDK's folder is no place for mail.
 */
internal fun portableDataDir(exePath: String?): File? {
    val exe = exePath?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
    if (exe.name.lowercase() in setOf("java.exe", "javaw.exe", "java")) return null
    val folder = exe.absoluteFile.parentFile ?: return null
    if (!File(folder, PORTABLE_MARKER).isFile) return null
    return File(folder, PORTABLE_DATA_DIR)
}
