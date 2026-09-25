import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import co.abaye.mailtice.App
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppViewModel
import co.abaye.mailtice.dev.DemoMode
import co.abaye.mailtice.dev.enableDemoModeFromEnvironment
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.main.DesktopUpdate
import co.abaye.mailtice.main.LocalHostHasTitleBar
import co.abaye.mailtice.main.LocalWindowDrag
import co.abaye.mailtice.main.UpdateButton
import co.abaye.mailtice.main.UpdateRestartDialog
import co.abaye.mailtice.main.rememberDesktopUpdate
import co.abaye.mailtice.theme.rememberAppColorScheme
import dev.nucleusframework.application.SingleInstanceRestoreEffect
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.windowDragArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.app_icon
import mailtice.shared.generated.resources.app_name
import mailtice.shared.generated.resources.demo_badge
import mailtice.shared.generated.resources.tray_open
import mailtice.shared.generated.resources.tray_quit
import mailtice.shared.generated.resources.tray_refresh
import mailtice.shared.generated.resources.tray_tooltip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CHROME_HEIGHT = 44.dp

fun main(args: Array<String>) {
    // Before anything reads the app directory: demo mode swaps it for a throwaway one.
    enableDemoModeFromEnvironment()
    nucleusApplication(args) {
        // Launched by the OS at login -> start hidden in the tray. Asked inside the application
        // loop: AutoLaunch only answers reliably once it is running.
        val startHidden = remember { runCatching { AutoLaunch.wasStartedAtLogin(args) }.getOrDefault(false) }
        val systemDark = isSystemInDarkTheme()
        var dark by remember { mutableStateOf(systemDark) }
        var rtl by remember { mutableStateOf(true) }
        var accent by remember { mutableStateOf(AccentColor.Flag) }
        var vm by remember { mutableStateOf<AppViewModel?>(null) }
        var visible by remember { mutableStateOf(!startHidden) }
        val colors = rememberAppColorScheme(accent, dark)
        val update = rememberDesktopUpdate()
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            width = 1180.dp,
            height = 780.dp,
        )

        // Downloaded updates install on the way out - the quiet path.
        val quit = {
            update.installOnExit()
            exitApplication()
        }
        val show = {
            visible = true
            windowState.isMinimized = false
        }

        val state = vm?.state?.collectAsState()?.value
        val closeToTray = state?.data?.settings?.closeToTray ?: true
        val unread = state?.unreadTotal ?: 0
        val paneStyle = state?.data?.settings?.paneStyle ?: PaneStyle.Cards

        // A second launch (or a click on a summary notification) brings the hidden window back.
        SingleInstanceRestoreEffect { show() }
        LaunchedEffect(vm) { vm?.raiseWindow?.collect { show() } }
        // Off the UI thread: the Windows and Linux badges go through native calls.
        LaunchedEffect(unread) { withContext(Dispatchers.IO) { TaskbarBadge.show(unread) } }

        // The menu builder is not a composable scope, so labels are resolved here and captured.
        val openLabel = stringResource(Res.string.tray_open)
        val refreshLabel = stringResource(Res.string.tray_refresh)
        val quitLabel = stringResource(Res.string.tray_quit)
        Tray(
            icon = painterResource(Res.drawable.app_icon),
            tooltip = stringResource(Res.string.tray_tooltip, unread),
            primaryAction = { show() },
        ) {
            Item(openLabel) { show() }
            Item(refreshLabel) { vm?.onIntent(AppIntent.RefreshNow) }
            Divider()
            Item(quitLabel) { quit() }
        }

        MaterialTheme(colorScheme = colors) {
            MaterialDecoratedWindow(
                onCloseRequest = { if (closeToTray) visible = false else quit() },
                state = windowState,
                visible = visible,
                title = stringResource(Res.string.app_name),
                icon = if (Platform.Current == Platform.Windows) painterResource(Res.drawable.app_icon) else null,
                minimumSize = DpSize(720.dp, 520.dp),
            ) {
                val windowScope = this
                WindowBackground(colors.background)
                WindowAppearance(if (dark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)

                val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                WindowScaffold(
                    modifier = Modifier.macOSLargeCornerRadius(),
                    controlButtonsDirection = if (rtl) ControlButtonsDirection.Rtl else ControlButtonsDirection.Ltr,
                    titleBar = {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) { windowScope.AppChrome(update, paneStyle) }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalHostHasTitleBar provides true,
                            LocalWindowDrag provides Modifier.windowDragArea(),
                        ) {
                            App(
                                onThemeChange = { isDark -> SideEffect { dark = isDark } },
                                onLayoutDirectionChange = { isRtl -> SideEffect { rtl = isRtl } },
                                onAccentChange = { picked -> SideEffect { accent = picked } },
                                onViewModel = { model -> SideEffect { if (vm !== model) vm = model } },
                                onQuit = quit,
                            )
                        }
                        UpdateRestartDialog(update)
                    }
                }
            }
        }
    }
}

/** Window chrome: app title at the leading edge, update button and caption buttons at the trailing one. */
@Composable
private fun DecoratedWindowScope.AppChrome(update: DesktopUpdate, paneStyle: PaneStyle) {
    val colors = MaterialTheme.colorScheme
    val insets = LocalWindowChromeInsets.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(CHROME_HEIGHT)
            // Same tone as the sidebar under it, so chrome and sidebar read as one surface.
            .background(if (paneStyle == PaneStyle.Cards) colors.surfaceContainerLow else colors.surfaceContainer)
            .windowDragArea(),
    ) {
        // Absolute values: the scaffold already mirrored the reserve off controlButtonsDirection.
        val reserve = insets.controlsInsets
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .absolutePadding(
                    left = reserve.calculateLeftPadding(LayoutDirection.Ltr),
                    right = reserve.calculateRightPadding(LayoutDirection.Ltr),
                )
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
            // Hard to miss, so a demo window is never mistaken for the real mailbox.
            if (DemoMode.enabled) {
                Text(
                    stringResource(Res.string.demo_badge),
                    Modifier
                        .padding(start = 8.dp)
                        .background(colors.tertiaryContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onTertiaryContainer,
                )
            }
        }
        val mac = Platform.Current == Platform.MacOS
        Row(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpdateButton(update)
            if (!mac) WindowControls(Modifier.fillMaxHeight())
        }
    }
}
