package co.abaye.mailtice.auth

import androidx.compose.runtime.Immutable

/**
 * A browser profile the sign-in page can open in (desktop Chromium browsers). [key] is stable across
 * launches ("Chrome|Profile 1") and is what the settings remember.
 */
@Immutable
data class BrowserProfile(
    val browser: String,
    val directory: String,
    val name: String,
    val email: String = "",
) {
    val key: String get() = "$browser|$directory"
}
