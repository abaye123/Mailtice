package co.abaye.mailtice.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppKey
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.app.icon
import co.abaye.mailtice.app.label
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.LocalDensitySpec
import co.abaye.mailtice.ui.LocalPaneStyle
import co.abaye.mailtice.ui.Pane
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.app_name
import mailtice.shared.generated.resources.compose_new
import mailtice.shared.generated.resources.nav_accounts
import mailtice.shared.generated.resources.nav_all_inboxes
import mailtice.shared.generated.resources.nav_manage_accounts
import mailtice.shared.generated.resources.sidebar_collapse
import mailtice.shared.generated.resources.sidebar_expand
import mailtice.shared.generated.resources.status_needs_reauth
import mailtice.shared.generated.resources.status_offline
import org.jetbrains.compose.resources.stringResource

/**
 * Wide: the sidebar (unified inbox, each account, settings) on the start side, the current screen
 * beside it. Narrow (phones): the screen
 * with a bottom navigation bar under it, inside the system-bar insets.
 */
@Composable
fun MainShell(
    state: AppState,
    destination: AppKey,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 720.dp
        CompositionLocalProvider(LocalCompactLayout provides compact) {
            if (compact) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    BottomBar(destination, state.unreadTotal, onIntent)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (!LocalHostHasTitleBar.current) BrandBar()
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Sidebar(state, destination, onIntent)
                        ContentArea(destination, Modifier.weight(1f).fillMaxHeight(), content)
                    }
                }
            }
        }
    }
}

/**
 * Where the screens go on wide windows. In the card style every screen but the inbox (which lays
 * out its own list and reader cards) sits on one rounded pane with a margin around it.
 */
@Composable
private fun ContentArea(destination: AppKey, modifier: Modifier, content: @Composable () -> Unit) {
    val cards = cardPanes()
    val splitsItself = destination == AppKey.Inbox || destination == AppKey.Reader
    Box(
        modifier
            .background(if (cards) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
            .then(if (cards) Modifier.padding(top = 8.dp, bottom = 8.dp, end = 8.dp) else Modifier),
    ) {
        if (splitsItself) {
            content()
        } else {
            Pane(rounded = cards, modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun BottomBar(selected: AppKey, unread: Int, onIntent: (AppIntent) -> Unit) {
    NavigationBar {
        listOf(AppKey.Inbox, AppKey.Accounts, AppKey.Settings, AppKey.About).forEach { key ->
            val isSelected = key == selected || (key == AppKey.Inbox && selected == AppKey.Reader) ||
                (key == AppKey.Accounts && selected is AppKey.AccountDetail)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onIntent(AppIntent.Navigate(key)) },
                icon = {
                    if (key == AppKey.Inbox && unread > 0) {
                        BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) { Icon(key.icon(), null) }
                    } else {
                        Icon(key.icon(), null)
                    }
                },
                label = { Text(key.label()) },
            )
        }
    }
}

@Composable
private fun BrandBar(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(52.dp).background(colors.surfaceContainer).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
    }
}

/**
 * The wide layout's sidebar, after the approved design: the compose button, the unified inbox, then
 * every account on its own (its inbox alone, with its unread count), then account management,
 * settings and about. It collapses to an icon rail; every icon then names itself on hover.
 */
@Composable
private fun Sidebar(state: AppState, selected: AppKey, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val cards = LocalPaneStyle.current == PaneStyle.Cards
    val collapsed = state.data.settings.sidebarCollapsed
    val width by animateDpAsState(
        when {
            collapsed -> 80.dp
            cards -> 264.dp
            else -> 240.dp
        },
        tween(220),
        label = "sidebar-width",
    )
    val inInbox = selected == AppKey.Inbox || selected == AppKey.Reader
    val filtered = state.filter.accountId
    Column(
        modifier.width(width).fillMaxHeight()
            .background(if (cards) colors.surfaceContainerLow else colors.surfaceContainer)
            .then(LocalWindowDrag.current)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val toggleLabel = stringResource(if (collapsed) Res.string.sidebar_expand else Res.string.sidebar_collapse)
        Box(Modifier.fillMaxWidth(), contentAlignment = if (collapsed) Alignment.Center else Alignment.CenterStart) {
            TooltipIconButton(
                if (collapsed) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.MenuOpen,
                toggleLabel,
                { onIntent(AppIntent.ToggleSidebar) },
                modifier = Modifier.padding(start = if (collapsed) 0.dp else 4.dp),
            )
        }
        if (state.sendingAccounts.isNotEmpty()) {
            val composeLabel = stringResource(Res.string.compose_new)
            val onCompose = { onIntent(AppIntent.StartCompose(ComposeMode.New)) }
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                contentAlignment = if (collapsed) Alignment.Center else Alignment.CenterStart,
            ) {
                if (collapsed) {
                    Tooltip(composeLabel) {
                        FloatingActionButton(
                            onClick = onCompose,
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 1.dp),
                        ) { Icon(Icons.Outlined.Edit, composeLabel) }
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = onCompose,
                        modifier = Modifier.padding(start = 4.dp),
                        icon = { Icon(Icons.Outlined.Edit, null) },
                        text = { Text(composeLabel) },
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 1.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidebarItem(
                label = stringResource(Res.string.nav_all_inboxes),
                selected = inInbox && filtered.isEmpty(),
                collapsed = collapsed,
                count = state.unreadTotal,
                leading = { tint -> Icon(Icons.Outlined.AllInbox, null, tint = tint) },
            ) {
                onIntent(AppIntent.Navigate(AppKey.Inbox))
                onIntent(AppIntent.SetFilterAccount(""))
            }
            if (state.accounts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = if (collapsed) 8.dp else 16.dp, vertical = 10.dp), color = colors.outlineVariant)
                if (!collapsed) {
                    Text(
                        stringResource(Res.string.nav_accounts),
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            state.accounts.forEach { account ->
                AccountSidebarItem(
                    account = account,
                    status = state.status(account.id),
                    unread = state.unread[account.id]?.toInt() ?: 0,
                    selected = inInbox && filtered == account.id,
                    collapsed = collapsed,
                ) {
                    onIntent(AppIntent.Navigate(AppKey.Inbox))
                    onIntent(AppIntent.SetFilterAccount(account.id))
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = if (collapsed) 8.dp else 16.dp, vertical = 8.dp), color = colors.outlineVariant)
        SidebarItem(
            label = stringResource(Res.string.nav_manage_accounts),
            selected = selected == AppKey.Accounts || selected is AppKey.AccountDetail,
            collapsed = collapsed,
            leading = { tint -> Icon(AppKey.Accounts.icon(), null, tint = tint) },
        ) { onIntent(AppIntent.Navigate(AppKey.Accounts)) }
        SidebarItem(
            label = AppKey.Settings.label(),
            selected = selected == AppKey.Settings,
            collapsed = collapsed,
            leading = { tint -> Icon(AppKey.Settings.icon(), null, tint = tint) },
        ) { onIntent(AppIntent.Navigate(AppKey.Settings)) }
        SidebarItem(
            label = AppKey.About.label(),
            selected = selected == AppKey.About,
            collapsed = collapsed,
            leading = { tint -> Icon(AppKey.About.icon(), null, tint = tint) },
        ) { onIntent(AppIntent.Navigate(AppKey.About)) }
        if (!collapsed) VersionLabel(Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

@Composable
private fun AccountSidebarItem(
    account: Account,
    status: AccountStatus,
    unread: Int,
    selected: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val problem = when (status) {
        AccountStatus.NeedsReauth -> stringResource(Res.string.status_needs_reauth)
        AccountStatus.Offline -> stringResource(Res.string.status_offline)
        else -> null
    }
    // The full address on hover: the label may be a nickname, or the address cut short.
    val hint = listOfNotNull(if (collapsed) account.displayName else null, account.email, problem).distinct().joinToString("\n")
    Tooltip(hint) {
        SidebarItem(
            label = account.displayName,
            selected = selected,
            collapsed = collapsed,
            count = unread,
            leading = { AccountAvatar(account, size = 28.dp) },
            trailing = {
                if (problem != null) {
                    Icon(Icons.Outlined.ErrorOutline, problem, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            },
            // Collapsed, the tooltip above already names the account.
            tooltip = false,
            onClick = onClick,
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    collapsed: Boolean,
    count: Int = 0,
    leading: @Composable (tint: Color) -> Unit,
    trailing: @Composable () -> Unit = {},
    tooltip: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val easing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
    val background by animateColorAsState(
        if (selected) colors.secondaryContainer else Color.Transparent,
        tween(280, easing = easing),
        label = "nav-bg",
    )
    val foreground by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        tween(280, easing = easing),
        label = "nav-fg",
    )
    val height = LocalDensitySpec.current.navItem
    val countText = if (count > 999) "999+" else count.toString()
    if (collapsed) {
        val item: @Composable () -> Unit = {
            Box(
                Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(background)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (count > 0) {
                    BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else countText) } }) { leading(foreground) }
                } else {
                    leading(foreground)
                }
            }
        }
        if (tooltip) Tooltip(if (count > 0) "$label ($countText)" else label) { item() } else item()
        return
    }
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(background)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading(foreground)
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || count > 0) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) foreground else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
        if (count > 0) Text(countText, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
private fun VersionLabel(modifier: Modifier = Modifier) {
    val version = Platform.appVersion
    if (version.isEmpty()) return
    Text(version, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
