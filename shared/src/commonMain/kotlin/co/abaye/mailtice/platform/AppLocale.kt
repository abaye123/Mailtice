package co.abaye.mailtice.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Runtime locale for Compose Resources.
 *
 * 1. [ApplyAppLocale] writes the locale where
 *    [androidx.compose.ui.text.intl.Locale.current] and [getString] look
 *    (`Locale.setDefault` on Android/JVM, `window.__customLocale` on web).
 * 2. [key] remounts the tree so composables re-read [Locale.current].
 *
 * Web also needs `Navigator.prototype.languages` / `language` patched in
 * `index.html` before the app script; Compose and [getString] both go through
 * those getters.
 */
@Composable
internal fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    ApplyAppLocale(language) {
        key(language) {
            content()
        }
    }
}

internal suspend fun localizedString(language: String, resource: StringResource, vararg args: Any): String {
    Platform.applyLocale(language)
    return getString(resource, *args)
}

@Composable
internal expect fun ApplyAppLocale(language: String, content: @Composable () -> Unit)
