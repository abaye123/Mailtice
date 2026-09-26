package co.abaye.mailtice

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import co.abaye.mailtice.app.AppViewModel
import co.abaye.mailtice.calendar.HebrewDateStyle
import co.abaye.mailtice.calendar.LocalHebrewDate
import co.abaye.mailtice.app.RootScreen
import co.abaye.mailtice.di.AppGraph
import co.abaye.mailtice.di.createAppGraph
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.ThemeMode
import co.abaye.mailtice.domain.UiLanguage
import co.abaye.mailtice.platform.ProvideAppLocale
import co.abaye.mailtice.theme.AppTheme

/**
 * The whole app. The desktop host owns the window chrome and the tray, which live outside this
 * composition, so the values they need are reported back through the `on…` callbacks.
 */
@Composable
fun App(
    graph: AppGraph? = null,
    provided: AppViewModel? = null,
    onThemeChange: @Composable (isDark: Boolean) -> Unit = {},
    onLayoutDirectionChange: @Composable (isRtl: Boolean) -> Unit = {},
    onAccentChange: @Composable (accent: AccentColor) -> Unit = {},
    /** Hands the host the view model so the tray menu can send intents and read the unread count. */
    onViewModel: @Composable (AppViewModel) -> Unit = {},
    onQuit: () -> Unit = {},
) {
    val appGraph = graph ?: remember { createAppGraph() }
    val vm = provided ?: viewModel { appGraph.viewModelFactory.create(onQuit) }
    onViewModel(vm)
    val state by vm.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val settings = state.data.settings
    val isDark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    onThemeChange(isDark)
    onAccentChange(settings.accent)
    val language = settings.uiLanguage
    onLayoutDirectionChange(language.rtl)
    val direction = if (language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ProvideAppLocale(language.code) {
        AppTheme(accent = settings.accent, isDark = isDark, font = settings.font) {
            val hebrewDate = remember(settings.showHebrewDate, settings.hebrewDateAtSunset, settings.sunsetCity, language) {
                if (!settings.showHebrewDate) {
                    null
                } else {
                    HebrewDateStyle(settings.hebrewDateAtSunset, settings.sunsetCity, hebrewLetters = language == UiLanguage.Hebrew)
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction, LocalHebrewDate provides hebrewDate) {
                RootScreen(state = state, backStack = vm.backStack, onIntent = vm::onIntent)
            }
        }
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}
