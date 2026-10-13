package co.abaye.mailtice.main

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/** The newest release's page, where a portable copy gets a new version. */
internal const val RELEASES_URL = "https://github.com/abaye123/Mailtice/releases/latest"

/** Where the self-update stands, for the settings. */
@Immutable
sealed interface UpdateStatus {
    /** Not an installed build (run from source): there is no installer to replace. */
    data object Unsupported : UpdateStatus

    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    /** The newest release is the one running; [checkedAt] is when that was last confirmed. */
    data class UpToDate(val checkedAt: Long) : UpdateStatus

    data class Downloading(val version: String, val percent: Int) : UpdateStatus

    /** Downloaded and waiting: it installs on quit, or at once with a restart. */
    data class Ready(val version: String) : UpdateStatus

    /** A portable copy is not installed, so it is told of [version] and downloads it from [RELEASES_URL]. */
    data class Manual(val version: String) : UpdateStatus

    data object Failed : UpdateStatus
}

/**
 * The app's self-update, as the host provides it (desktop). The shared UI reads it through
 * [LocalAppUpdates]; where there is none (Android updates through its store) that is null.
 */
@Stable
interface AppUpdates {
    val currentVersion: String
    val status: UpdateStatus

    /** Checks now, unless a check or download is already running. */
    fun checkNow()

    /** Installs the downloaded update and starts the new version. */
    fun restartNow()
}

val LocalAppUpdates = staticCompositionLocalOf<AppUpdates?> { null }
