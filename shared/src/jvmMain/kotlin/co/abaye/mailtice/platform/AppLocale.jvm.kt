package co.abaye.mailtice.platform

import androidx.compose.runtime.Composable

@Composable
internal actual fun ApplyAppLocale(language: String, content: @Composable () -> Unit) {
    Platform.applyLocale(language)
    content()
}
