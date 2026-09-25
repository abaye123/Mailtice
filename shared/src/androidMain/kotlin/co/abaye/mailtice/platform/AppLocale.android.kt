package co.abaye.mailtice.platform

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
internal actual fun ApplyAppLocale(language: String, content: @Composable () -> Unit) {
    Platform.applyLocale(language)
    val newConfig = Configuration(LocalConfiguration.current).apply {
        setLocale(Locale.forLanguageTag(language))
    }
    CompositionLocalProvider(LocalConfiguration provides newConfig, content = content)
}
