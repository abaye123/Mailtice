package co.abaye.mailtice.app

import co.abaye.mailtice.main.HomeScreen
import co.abaye.mailtice.main.AttachmentPreviewOverlay
import co.abaye.mailtice.main.LabelsScreen
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import co.abaye.mailtice.main.AboutScreen
import co.abaye.mailtice.main.AccountDetailScreen
import co.abaye.mailtice.main.AccountsScreen
import co.abaye.mailtice.main.ComposeWindow
import co.abaye.mailtice.main.InboxScreen
import co.abaye.mailtice.main.LocalCompactLayout
import co.abaye.mailtice.main.MainShell
import co.abaye.mailtice.main.ReaderScreen
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
        state.preview?.let { AttachmentPreviewOverlay(it, onIntent) }
        MessageBar(
            message = state.message,
            onDismiss = remember(onIntent) { { onIntent(AppIntent.DismissMessage) } },
            // Bottom left, whatever the language direction: away from the sidebar in Hebrew and
            // where Gmail puts its own notices in English. A compose window docked in that corner
            // (Hebrew) keeps its place; the notice moves out beside it.
            modifier = Modifier.align(AbsoluteAlignment.BottomLeft).padding(start = toastInset(state)),
        )
        AppDialogHost(state = state, onIntent = onIntent)
        ComposeWindow(state = state, onIntent = onIntent)
    }
}

/** How far the notice moves right to clear a compose window docked at the bottom left (RTL only). */
@Composable
private fun toastInset(state: AppState): androidx.compose.ui.unit.Dp {
    val compose = state.compose ?: return 0.dp
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (!rtl || LocalCompactLayout.current) return 0.dp
    return when (compose.window) {
        ComposeWindowMode.Normal -> 24.dp + 560.dp
        ComposeWindowMode.Minimized -> 24.dp + 320.dp
        ComposeWindowMode.Maximized -> 0.dp
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
            entry<AppKey.Home> { HomeScreen(state, onIntent) }
            entry<AppKey.Inbox> { InboxScreen(state, onIntent) }
            entry<AppKey.Accounts> { AccountsScreen(state, onIntent) }
            entry<AppKey.Settings> { SettingsScreen(state, onIntent) }
            entry<AppKey.About> { AboutScreen() }
            entry<AppKey.AccountDetail> { key -> AccountDetailScreen(key.accountId, state, onIntent) }
            entry<AppKey.Labels> { key -> LabelsScreen(key.accountId, state, onIntent) }
            entry<AppKey.Reader> { ReaderScreen(state, onIntent) }
        },
    )
}

private val PageFadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private fun pageFade(): ContentTransform =
    fadeIn(tween(150, easing = PageFadeEasing)) togetherWith fadeOut(tween(150, easing = PageFadeEasing))
