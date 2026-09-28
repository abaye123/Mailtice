package co.abaye.mailtice.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppKey
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.app.icon
import co.abaye.mailtice.app.label
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.LocalDensitySpec
import co.abaye.mailtice.ui.LocalPaneStyle
import co.abaye.mailtice.ui.Pane
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.account_collapse
import mailtice.shared.generated.resources.account_expand
import mailtice.shared.generated.resources.app_name
import mailtice.shared.generated.resources.close_to_mail
import mailtice.shared.generated.resources.compose_new
import mailtice.shared.generated.resources.compose_new_hint
import mailtice.shared.generated.resources.nav_accounts
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
                        ContentArea(destination, onIntent, Modifier.weight(1f).fillMaxHeight(), content)
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
private fun ContentArea(
    destination: AppKey,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cards = cardPanes()
    // Placed in one of two branches below; movable, so a screen keeps its state if the branch changes.
    val screen = remember(content) { movableContentOf(content) }
    val splitsItself = destination == AppKey.Inbox || destination == AppKey.Reader
    // Home is a place of its own, not a page over the mail: no "back to mail" pill there.
    val overMail = destination != AppKey.Home
    Box(
        modifier
            .background(if (cards) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
            .then(if (cards) Modifier.padding(top = 8.dp, bottom = 8.dp, end = 8.dp) else Modifier),
    ) {
        if (splitsItself) {
            screen()
        } else {
            Pane(rounded = cards, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    screen()
                    // Settings, accounts and about lay over the mail; this pill floats at the far end of
                    // their title row (titles sit at the leading edge) and goes straight back to it.
                    if (overMail) {
                        Button(
                            onClick = { onIntent(AppIntent.Navigate(AppKey.Inbox)) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 18.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        ) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
                            Text(stringResource(Res.string.close_to_mail), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: AppKey, unread: Int, onIntent: (AppIntent) -> Unit) {
    NavigationBar {
        listOf(AppKey.Home, AppKey.Inbox, AppKey.Accounts, AppKey.Settings, AppKey.About).forEach { key ->
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
 * The wide layout's sidebar: the compose button, the standard folders across every account, then
 * each account as a tree of its own - its standard folders, its labels (pinned first, hidden ones
 * left out) and a row to manage them. Clicking the account row folds its whole tree. Account management,
 * settings and about sit at the foot. It collapses to an icon rail; every icon then names itself on
 * hover.
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
    val inMail = selected == AppKey.Inbox || selected == AppKey.Reader
    val filter = state.filter
    Column(
        modifier.width(width).fillMaxHeight()
            .background(if (cards) colors.surfaceContainerLow else colors.surfaceContainer)
            .then(LocalWindowDrag.current)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidebarHeader(
            collapsed = collapsed,
            canCompose = state.sendingAccounts.isNotEmpty(),
            onToggle = { onIntent(AppIntent.ToggleSidebar) },
            onCompose = { onIntent(AppIntent.StartCompose(ComposeMode.New)) },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            fun viewOpen(accountId: String, view: MailView) =
                inMail && filter.accountId == accountId && filter.folderId.isEmpty() && filter.view == view

            SidebarItem(
                label = AppKey.Home.label(),
                selected = selected == AppKey.Home,
                collapsed = collapsed,
                leading = { _ -> HomeTile() },
            ) { onIntent(AppIntent.Navigate(AppKey.Home)) }

            // Every account together; the standard folders are always there and cannot be hidden.
            state.viewsFor("").forEach { view ->
                SidebarItem(
                    label = view.label(),
                    selected = viewOpen("", view),
                    collapsed = collapsed,
                    count = state.viewCount(view, ""),
                    leading = { tint -> Icon(view.icon(), null, tint = tint) },
                ) { onIntent(AppIntent.OpenView("", view)) }
            }
            if (state.accounts.isNotEmpty()) SidebarSection(stringResource(Res.string.nav_accounts), collapsed)
            state.accounts.forEach { account ->
                val expanded = !collapsed && account.id !in state.data.settings.collapsedAccounts
                AccountSidebarItem(
                    account = account,
                    status = state.status(account.id),
                    error = state.syncErrors[account.id],
                    unread = state.unread[account.id]?.toInt() ?: 0,
                    // Folded, the account row stands for whatever of it is open.
                    selected = inMail && filter.accountId == account.id && !expanded,
                    collapsed = collapsed,
                    expanded = expanded,
                ) {
                    // The account row folds and unfolds its tree; on the icon rail, with no tree to
                    // show, it opens the account's inbox instead.
                    if (collapsed) {
                        onIntent(AppIntent.OpenView(account.id, MailView.Inbox))
                    } else {
                        onIntent(AppIntent.ToggleAccountExpanded(account.id))
                    }
                }
                AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AccountTree(state, account, inMail, onIntent)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = if (collapsed) 8.dp else 16.dp, vertical = 6.dp), color = colors.outlineVariant)
        // Account management, settings and about take one row, leaving the height to the folders.
        val footer = listOf(
            Triple(
                AppKey.Accounts,
                stringResource(Res.string.nav_manage_accounts),
                selected == AppKey.Accounts || selected is AppKey.AccountDetail,
            ),
            Triple(AppKey.Settings, AppKey.Settings.label(), selected == AppKey.Settings),
            Triple(AppKey.About, AppKey.About.label(), selected == AppKey.About),
        )
        if (collapsed) {
            footer.forEach { (key, label, isSelected) ->
                SidebarItem(label = label, selected = isSelected, collapsed = true, leading = { tint ->
                    Icon(key.icon(), null, tint = tint)
                }) {
                    onIntent(AppIntent.Navigate(key))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                footer.forEach { (key, label, isSelected) ->
                    TooltipIconButton(
                        key.icon(),
                        label,
                        { onIntent(AppIntent.Navigate(key)) },
                        tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                VersionLabel(Modifier.padding(end = 8.dp))
            }
        }
    }
}

/** One account unfolded: its standard folders, its labels, and the row that manages them. */
@Composable
private fun AccountTree(state: AppState, account: Account, inMail: Boolean, onIntent: (AppIntent) -> Unit) {
    val filter = state.filter
    val settings = state.data.settings
    state.viewsFor(account.id).forEach { view ->
        SidebarItem(
            label = view.label(),
            selected = inMail && filter.accountId == account.id && filter.folderId.isEmpty() && filter.view == view,
            collapsed = false,
            count = state.viewCount(view, account.id),
            indent = TreeIndent,
            leading = { tint -> Icon(view.icon(), null, tint = tint) },
        ) { onIntent(AppIntent.OpenView(account.id, view)) }
    }
    val counts = state.unreadByFolder[account.id].orEmpty()
    // A hidden label stays while it is the one open, so the highlight is never lost.
    state.sidebarLabels(account.id, withHidden = true)
        .filter { it.key !in settings.hiddenFolders || (filter.accountId == account.id && filter.folderId == it.id) }
        .forEach { folder ->
            var menuOpen by remember { mutableStateOf(false) }
            SidebarItem(
                label = folder.name,
                selected = inMail && filter.accountId == account.id && filter.folderId == folder.id,
                collapsed = false,
                count = (counts[folder.id] ?: 0L).toInt(),
                indent = TreeIndent,
                // The label's own colour where the provider has one (Gmail), like its web client.
                leading = { tint -> Icon(FolderIcon, null, tint = folder.labelColor() ?: tint) },
                hoverTrailing = {
                    LabelControls(account, folder, pinned = folder.key in settings.pinnedLabels, onIntent = onIntent, onMenuOpen = {
                        menuOpen =
                            it
                    })
                },
                keepHoverTrailing = menuOpen,
            ) { onIntent(AppIntent.OpenLabel(account.id, folder.id)) }
        }
    SidebarItem(
        label = account.manageLabelsTitle(),
        selected = false,
        collapsed = false,
        indent = TreeIndent,
        muted = true,
        leading = { tint -> Icon(Icons.Outlined.Tune, null, tint = tint) },
    ) { onIntent(AppIntent.Navigate(AppKey.Labels(account.id))) }
}

/** How far an account's folders sit in from the account row. */
private val TreeIndent = 12.dp

/**
 * The top of the sidebar: the menu button and "New email" on one row - the menu at the leading
 * edge, the compose button pushed to the far end. Collapsed there is no room for both, so they
 * stack, each centred on the 40dp axis the item icons below share. The compose button is a single
 * extended FAB that folds into its square form with the sidebar instead of swapping components.
 */
@Composable
private fun SidebarHeader(collapsed: Boolean, canCompose: Boolean, onToggle: () -> Unit, onCompose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val toggle: @Composable () -> Unit = {
        TooltipIconButton(
            if (collapsed) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.MenuOpen,
            stringResource(if (collapsed) Res.string.sidebar_expand else Res.string.sidebar_collapse),
            onToggle,
            // 4dp in from the 12dp padding centres the 48dp button on the 40dp axis.
            modifier = Modifier.padding(start = 4.dp),
            tint = colors.onSurfaceVariant,
        )
    }
    val compose: @Composable () -> Unit = {
        if (canCompose) {
            val label = stringResource(Res.string.compose_new)
            Tooltip(stringResource(Res.string.compose_new_hint)) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    expanded = !collapsed,
                    icon = { Icon(Icons.Outlined.Edit, label) },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    // Flat at rest like the rest of the sidebar; it lifts a little under the pointer.
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 1.dp,
                        focusedElevation = 1.dp,
                        hoveredElevation = 3.dp,
                    ),
                )
            }
        }
    }
    if (collapsed) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            toggle()
            Spacer(Modifier.height(10.dp))
            compose()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            toggle()
            Spacer(Modifier.weight(1f))
            compose()
        }
    }
}

/** A divider with a small heading; collapsed, the divider alone. */
@Composable
private fun SidebarSection(title: String, collapsed: Boolean) {
    Column {
        val colors = MaterialTheme.colorScheme
        HorizontalDivider(Modifier.padding(horizontal = if (collapsed) 8.dp else 16.dp, vertical = 10.dp), color = colors.outlineVariant)
        if (!collapsed) {
            Text(
                title,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountSidebarItem(
    account: Account,
    status: AccountStatus,
    error: String?,
    unread: Int,
    selected: Boolean,
    collapsed: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val problem = when (status) {
        AccountStatus.NeedsReauth -> stringResource(Res.string.status_needs_reauth)
        AccountStatus.Offline -> stringResource(Res.string.status_offline)
        else -> null
    }
    // The full address on hover: the label may be a nickname, or the address cut short.
    val detail = error?.takeIf { status == AccountStatus.Offline || status == AccountStatus.Syncing }
    val hint = listOfNotNull(if (collapsed) account.displayName else null, account.email, problem, detail).distinct().joinToString("\n")
    Tooltip(hint) {
        SidebarItem(
            label = account.displayName,
            selected = selected,
            collapsed = collapsed,
            // Unfolded, the inbox row under it carries the count.
            count = if (expanded) 0 else unread,
            leading = { AccountAvatar(account, size = 28.dp) },
            trailing = {
                if (problem != null) {
                    Icon(Icons.Outlined.ErrorOutline, problem, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            },
            end = {
                // The whole row folds and unfolds; the arrow only shows which way it will go.
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(if (expanded) Res.string.account_collapse else Res.string.account_expand),
                    Modifier.padding(horizontal = 8.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            // Collapsed, the tooltip above already names the account.
            tooltip = false,
            onClick = onClick,
        )
    }
}

/**
 * A sidebar row. [hoverTrailing] takes the place of the count while the pointer is over the row
 * (or while [keepHoverTrailing], say with its menu open); [end] is always at the far end.
 */
// The leading slot is used in one of two exclusive branches (rail or full row), so there is no slot
// state to carry across.
@Suppress("ktlint:compose:content-slot-reused", "ContentSlotReused")
@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    collapsed: Boolean,
    leading: @Composable (tint: Color) -> Unit,
    count: Int = 0,
    indent: Dp = 0.dp,
    muted: Boolean = false,
    trailing: @Composable () -> Unit = {},
    hoverTrailing: (@Composable () -> Unit)? = null,
    keepHoverTrailing: Boolean = false,
    end: (@Composable () -> Unit)? = null,
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
    // Rows inside an account's tree are a step shorter, so a few unfolded accounts still fit.
    val rowHeight = if (indent > 0.dp) (height - 8.dp).coerceAtLeast(32.dp) else height
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val showHover = hoverTrailing != null && (hovered || keepHoverTrailing)
    Row(
        Modifier.fillMaxWidth().padding(start = indent).height(rowHeight).clip(RoundedCornerShape(rowHeight / 2)).background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 16.dp, end = if (showHover || end != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading(foreground)
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || count > 0) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                selected -> foreground
                muted -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
        if (showHover) {
            hoverTrailing()
        } else if (count > 0) {
            Text(countText, style = MaterialTheme.typography.labelLarge, color = foreground)
        }
        end?.invoke()
    }
}

@Composable
private fun VersionLabel(modifier: Modifier = Modifier) {
    val version = Platform.appVersion
    if (version.isEmpty()) return
    Text(version, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The home entry stands out from the folders: its icon on the accent gradient of the home page's cards. */
@Composable
private fun HomeTile() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppKey.Home.icon(), null, Modifier.size(18.dp), tint = colors.onPrimary)
    }
}
