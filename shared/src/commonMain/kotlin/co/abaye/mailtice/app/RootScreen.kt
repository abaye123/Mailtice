package co.abaye.mailtice.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import co.abaye.mailtice.main.AboutScreen
import co.abaye.mailtice.main.AccountDetailScreen
import co.abaye.mailtice.main.ReaderScreen
import co.abaye.mailtice.main.AccountsScreen
import co.abaye.mailtice.main.ComposeWindow
import co.abaye.mailtice.main.InboxScreen
import co.abaye.mailtice.main.MainShell
import co.abaye.mailtice.main.SettingsScreen
import co.abaye.mailtice.ui.AppDialogHost
import co.abaye.mailtice.ui.LocalDensitySpec
import co.abaye.mailtice.ui.LocalPaneStyle
import co.abaye.mailtice.ui.MessageBar
import co.abaye.mailtice.ui.spec

@Composable
fun RootScreen(state: AppState, backStack: NavBackStack<AppKey>, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val settings = state.data.settings
        CompositionLocalProvider(
            LocalDensitySpec provides settings.density.spec(),
            LocalPaneStyle provides settings.paneStyle,
        ) {
            MainShell(state = state, destination = backStack.last(), onIntent = onIntent) {
                AppNavDisplay(backStack, state, onIntent)
            }
        }
        MessageBar(
            message = state.message,
            onDismiss = remember(onIntent) { { onIntent(AppIntent.DismissMessage) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AppDialogHost(state = state, onIntent = onIntent)
        ComposeWindow(state = state, onIntent = onIntent)
    }
}

@Composable
private fun AppNavDisplay(backStack: NavBackStack<AppKey>, state: AppState, onIntent: (AppIntent) -> Unit) {
    val transform = pageFade()
    NavDisplay(
        backStack = backStack,
        onBack = { onIntent(AppIntent.Back) },
        transitionSpec = { transform },
        popTransitionSpec = { transform },
        predictivePopTransitionSpec = { transform },
        entryProvider = entryProvider {
            entry<AppKey.Inbox> { InboxScreen(state, onIntent) }
            entry<AppKey.Accounts> { AccountsScreen(state, onIntent) }
            entry<AppKey.Settings> { SettingsScreen(state, onIntent) }
            entry<AppKey.About> { AboutScreen() }
            entry<AppKey.AccountDetail> { key -> AccountDetailScreen(key.accountId, state, onIntent) }
            entry<AppKey.Reader> { ReaderScreen(state, onIntent) }
        },
    )
}

private val PageFadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private fun pageFade(): ContentTransform =
    fadeIn(tween(150, easing = PageFadeEasing)) togetherWith fadeOut(tween(150, easing = PageFadeEasing))
