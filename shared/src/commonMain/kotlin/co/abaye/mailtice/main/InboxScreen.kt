package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.ListFractionRange
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.ResizeHorizontalIcon
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.LocalDensitySpec
import co.abaye.mailtice.ui.Pane
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_reconnect
import mailtice.shared.generated.resources.compose_new
import mailtice.shared.generated.resources.empty_search_action
import mailtice.shared.generated.resources.inbox_all
import mailtice.shared.generated.resources.inbox_all_folders
import mailtice.shared.generated.resources.inbox_archive
import mailtice.shared.generated.resources.inbox_attachments_only
import mailtice.shared.generated.resources.inbox_mark_read
import mailtice.shared.generated.resources.inbox_mark_unread
import mailtice.shared.generated.resources.inbox_reauth_banner
import mailtice.shared.generated.resources.inbox_refresh
import mailtice.shared.generated.resources.inbox_search_all
import mailtice.shared.generated.resources.inbox_search_in
import mailtice.shared.generated.resources.inbox_trash
import mailtice.shared.generated.resources.inbox_unread_only
import mailtice.shared.generated.resources.selection_all
import mailtice.shared.generated.resources.selection_check
import mailtice.shared.generated.resources.selection_clear
import mailtice.shared.generated.resources.selection_count
import mailtice.shared.generated.resources.selection_download
import mailtice.shared.generated.resources.selection_uncheck
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Subject and snippet take their direction from their own first strong character. */
internal val ContentDirection = TextStyle(textDirection = TextDirection.Content)

@Composable
fun InboxScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    if (state.accounts.isEmpty()) {
        WelcomeEmptyState(onIntent, modifier)
        return
    }
    val compact = LocalCompactLayout.current
    val cards = cardPanes()
    val saved = state.data.settings.listFraction
    // Local while dragging (smooth), written to the settings once the drag ends.
    var fraction by remember(saved) { mutableFloatStateOf(saved) }
    var totalWidth by remember { mutableFloatStateOf(1f) }
    Row(modifier.fillMaxSize().onSizeChanged { totalWidth = it.width.toFloat().coerceAtLeast(1f) }) {
        Pane(rounded = cards, modifier = if (compact) Modifier.fillMaxSize() else Modifier.weight(fraction).fillMaxHeight()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) { MessageList(state, onIntent) }
                // Phones have no sidebar, so the compose button floats over the list.
                if (compact && state.sendingAccounts.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { onIntent(AppIntent.StartCompose(ComposeMode.New)) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        icon = { Icon(Icons.Outlined.Edit, null) },
                        text = { Text(stringResource(Res.string.compose_new)) },
                    )
                }
            }
        }
        if (!compact) {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            SplitHandle(
                lines = !cards,
                onDrag = { dx ->
                    // The list sits at the start: in RTL that is the right side, so dragging left grows it.
                    val delta = (if (rtl) -dx else dx) / totalWidth
                    fraction = (fraction + delta).coerceIn(ListFractionRange)
                },
                onDragEnd = { onIntent(AppIntent.SetListFraction(fraction)) },
            )
            Pane(rounded = cards, modifier = Modifier.weight(1f - fraction).fillMaxHeight()) {
                val reader = state.reader
                if (reader == null) {
                    ReaderEmptyState()
                } else {
                    ReaderPane(reader, state.account(reader.message.accountId), onIntent, working = state.working)
                }
            }
        }
    }
}

/**
 * The gap between list and reader, draggable to resize them. Card style: the 8dp gap itself, with a
 * small grip on hover. Line style: the divider line, with the same 8dp to grab.
 */
@Composable
private fun SplitHandle(lines: Boolean, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.width(8.dp).fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(ResizeHorizontalIcon)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    },
                ) { change, dx ->
                    change.consume()
                    onDrag(dx)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (lines) VerticalDivider(color = if (hovered || dragging) colors.primary else colors.outlineVariant)
        if (hovered || dragging) {
            Box(Modifier.width(4.dp).height(40.dp).background(if (dragging) colors.primary else colors.outline, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun MessageList(state: AppState, onIntent: (AppIntent) -> Unit) {
    val cards = cardStyle()
    if (state.selection.isEmpty()) Toolbar(state, onIntent) else SelectionBar(state, onIntent)
    if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    state.needsReauth.forEach { account -> ReauthBanner(account, onIntent) }
    Filters(state, onIntent)
    if (!cards) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (state.inbox.isEmpty()) {
        MessageListEmptyState(state, onIntent)
        return
    }
    val accounts = state.accounts.associateBy { it.id }
    val gap = LocalDensitySpec.current.rowGap
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = if (cards) PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp) else PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(if (cards) gap else 0.dp),
    ) {
        items(state.inbox, key = { it.key }) { message ->
            MailRow(
                message,
                accounts[message.accountId],
                opened = state.reader?.message?.id == message.id,
                checked = message.key in state.selection,
                selecting = state.selection.isNotEmpty(),
                onIntent = onIntent,
            )
            if (!cards) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Toolbar(state: AppState, onIntent: (AppIntent) -> Unit) {
    val cards = cardStyle()
    val scope = state.account(state.filter.accountId)
    val placeholder = if (scope == null) {
        stringResource(Res.string.inbox_search_all)
    } else {
        stringResource(Res.string.inbox_search_in, scope.displayName)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val query = state.filter.query
        val clear: @Composable () -> Unit = {
            if (query.isNotEmpty()) {
                TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.empty_search_action), { onIntent(AppIntent.SetSearchQuery("")) })
            }
        }
        if (cards) {
            // The design's search pill: filled, fully rounded, no underline.
            TextField(
                value = query,
                onValueChange = { onIntent(AppIntent.SetSearchQuery(it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = clear,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { onIntent(AppIntent.SetSearchQuery(it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = clear,
            )
        }
        TooltipIconButton(Icons.Outlined.Refresh, stringResource(Res.string.inbox_refresh), { onIntent(AppIntent.RefreshNow) })
    }
}

/**
 * Replaces the search bar while rows are checked: how many, and what can be done to all of them.
 * An action shows only when at least one checked message's account supports it.
 */
@Composable
private fun SelectionBar(state: AppState, onIntent: (AppIntent) -> Unit) {
    val selected = state.selectedMessages
    val caps = selected.mapNotNull { state.account(it.accountId)?.capabilities }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.selection_clear), { onIntent(AppIntent.ClearSelection) })
        Text(
            stringResource(Res.string.selection_count, selected.size),
            Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (selected.size < state.inbox.size) {
            TooltipIconButton(Icons.Outlined.SelectAll, stringResource(Res.string.selection_all), { onIntent(AppIntent.SelectAll) })
        }
        if (caps.any { it.markRead }) {
            TooltipIconButton(Icons.Outlined.MarkEmailRead, stringResource(Res.string.inbox_mark_read), { onIntent(AppIntent.BulkSetRead(true)) })
            TooltipIconButton(Icons.Outlined.MarkEmailUnread, stringResource(Res.string.inbox_mark_unread), { onIntent(AppIntent.BulkSetRead(false)) })
        }
        if (caps.any { it.archive }) {
            TooltipIconButton(Icons.Outlined.Archive, stringResource(Res.string.inbox_archive), { onIntent(AppIntent.BulkArchive) })
        }
        if (caps.any { it.trash }) {
            TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.inbox_trash), { onIntent(AppIntent.BulkTrash) })
        }
        if (selected.any { it.hasAttachments }) {
            TooltipIconButton(
                Icons.Outlined.Download,
                stringResource(Res.string.selection_download),
                { onIntent(AppIntent.BulkDownloadAttachments) },
                enabled = !state.working,
            )
        }
    }
}

@Composable
private fun ReauthBanner(account: Account, onIntent: (AppIntent) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.inbox_reauth_banner, account.displayName), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onIntent(AppIntent.Reconnect(account.id)) }) {
                Text(stringResource(Res.string.accounts_reconnect))
            }
        }
    }
}

/**
 * Wide windows pick the account in the sidebar, so the chips only switch between all mail and unread
 * (and the folder, inside one account). Phones have no sidebar and keep a chip per account.
 */
@Composable
private fun Filters(state: AppState, onIntent: (AppIntent) -> Unit) {
    val filter = state.filter
    val compact = LocalCompactLayout.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            FilterChip(
                selected = filter.accountId.isEmpty(),
                onClick = { onIntent(AppIntent.SetFilterAccount("")) },
                label = { Text("${stringResource(Res.string.inbox_all)} (${state.unreadTotal})") },
            )
            state.accounts.forEach { account ->
                FilterChip(
                    selected = filter.accountId == account.id,
                    onClick = { onIntent(AppIntent.SetFilterAccount(account.id)) },
                    leadingIcon = { AccountDot(account) },
                    label = { Text("${account.displayName} (${state.unread[account.id] ?: 0})") },
                )
            }
        } else {
            FilterChip(
                selected = !filter.unreadOnly && !filter.attachmentsOnly,
                onClick = {
                    onIntent(AppIntent.SetUnreadOnly(false))
                    onIntent(AppIntent.SetAttachmentsOnly(false))
                },
                label = { Text(stringResource(Res.string.inbox_all)) },
            )
        }
        FilterChip(
            selected = filter.unreadOnly,
            onClick = { onIntent(AppIntent.SetUnreadOnly(!filter.unreadOnly)) },
            label = { Text(stringResource(Res.string.inbox_unread_only)) },
        )
        FilterChip(
            selected = filter.attachmentsOnly,
            onClick = { onIntent(AppIntent.SetAttachmentsOnly(!filter.attachmentsOnly)) },
            leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp)) },
            label = { Text(stringResource(Res.string.inbox_attachments_only)) },
        )
        // Folders only make sense within one account.
        if (filter.accountId.isNotEmpty()) FolderPicker(state, onIntent)
    }
}

@Composable
private fun FolderPicker(state: AppState, onIntent: (AppIntent) -> Unit) {
    val folders = state.foldersOf(state.filter.accountId).filter { it.sync }
    if (folders.size < 2) return
    var open by remember { mutableStateOf(false) }
    val current = folders.firstOrNull { it.id == state.filter.folderId }
    Box {
        FilterChip(
            selected = current != null,
            onClick = { open = true },
            label = { Text(current?.name ?: stringResource(Res.string.inbox_all_folders)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.inbox_all_folders)) },
                onClick = {
                    open = false
                    onIntent(AppIntent.SetFilterFolder(""))
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        open = false
                        onIntent(AppIntent.SetFilterFolder(folder.id))
                    },
                )
            }
        }
    }
}

@Composable
fun AccountDot(account: Account, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(account.color.color, CircleShape))
}

/**
 * One message. The density decides padding, avatar size and preview lines; the pane style decides
 * between rounded rows (cards) and flat rows with the account stripe (lines). The mark-read and
 * archive buttons appear on hover or when opened, covering the time, so the text never reflows.
 *
 * The avatar is the checkbox: clicking it checks the row. While anything is checked, a click
 * anywhere on a row checks or unchecks it instead of opening it.
 *
 * Text is aligned to the layout (right in Hebrew) even when it is written left to right, so an
 * English sender lines up with the Hebrew ones instead of hugging the far edge.
 */
@Composable
private fun MailRow(
    message: MailMessage,
    account: Account?,
    opened: Boolean,
    checked: Boolean,
    selecting: Boolean,
    onIntent: (AppIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spec = LocalDensitySpec.current
    val cards = cardStyle()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accountColor = account?.color?.color ?: colors.outline
    val align = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
    val background = when {
        checked -> colors.primaryContainer
        opened -> colors.secondaryContainer
        hovered -> if (cards) colors.surfaceContainerHigh else colors.surfaceContainerLow
        else -> colors.surface
    }
    val weight = if (message.unread) FontWeight.SemiBold else FontWeight.Normal
    val senderStyle = if (spec.avatar >= 40.dp) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    Box(
        Modifier.fillMaxWidth().clip(if (cards) RoundedCornerShape(16.dp) else RectangleShape).background(background)
            .hoverable(interaction)
            .clickable { onIntent(if (selecting) AppIntent.ToggleSelect(message) else AppIntent.OpenMail(message)) },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            if (!cards) Box(Modifier.width(4.dp).fillMaxHeight().background(accountColor))
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = spec.rowPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = if (spec.snippetLines > 0) Alignment.Top else Alignment.CenterVertically,
            ) {
                if (spec.avatar > 0.dp) {
                    SelectableAvatar(message, accountColor, checked, spec.avatar) { onIntent(AppIntent.ToggleSelect(message)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // No avatar and no stripe (compact cards): a dot keeps the account visible.
                        if (cards && spec.avatar == 0.dp && account != null) AccountDot(account)
                        Text(
                            message.sender,
                            Modifier.weight(1f),
                            style = senderStyle.merge(ContentDirection),
                            textAlign = align,
                            fontWeight = weight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (message.hasAttachments) Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Text(
                            formatTime(message.receivedAt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = weight,
                            color = if (message.unread) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        message.subject,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                        textAlign = align,
                        fontWeight = weight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (spec.snippetLines > 0) {
                        Text(
                            message.snippet,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall.merge(ContentDirection),
                            textAlign = align,
                            color = colors.onSurfaceVariant,
                            maxLines = spec.snippetLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if ((hovered || opened) && !selecting && !LocalCompactLayout.current) {
            // Fades the text out under the buttons instead of cutting it off at a hard edge.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val fade = listOf(background.copy(alpha = 0f), background, background)
            RowActions(
                message,
                account,
                onIntent,
                Modifier.align(Alignment.TopEnd).padding(top = spec.rowPadding / 2)
                    .background(Brush.horizontalGradient(if (rtl) fade.reversed() else fade))
                    .padding(start = 24.dp, end = 4.dp),
            )
        }
    }
}

/** The sender's letter, or a check mark once checked; hovering hints that it can be clicked. */
@Composable
private fun SelectableAvatar(message: MailMessage, color: Color, checked: Boolean, size: Dp, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tooltip(stringResource(if (checked) Res.string.selection_uncheck else Res.string.selection_check)) {
        Box(
            Modifier.size(size).clip(CircleShape).hoverable(interaction).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            when {
                checked -> Box(Modifier.fillMaxSize().background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(size * 0.55f), tint = colors.onPrimary)
                }
                hovered -> Box(
                    Modifier.fillMaxSize().border(2.dp, colors.primary, CircleShape).background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, null, Modifier.size(size * 0.5f), tint = colors.primary) }
                else -> LetterAvatar(message.sender, color, size = size)
            }
        }
    }
}

/** Only what this account supports - nothing is offered that would fail. */
@Composable
private fun RowActions(message: MailMessage, account: Account?, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val caps = account?.capabilities ?: return
    Row(modifier) {
        if (caps.markRead) {
            if (message.unread) {
                TooltipIconButton(Icons.Outlined.MarkEmailRead, stringResource(Res.string.inbox_mark_read), {
                    onIntent(AppIntent.SetRead(message, read = true))
                })
            } else {
                TooltipIconButton(Icons.Outlined.MarkEmailUnread, stringResource(Res.string.inbox_mark_unread), {
                    onIntent(AppIntent.SetRead(message, read = false))
                })
            }
        }
        if (caps.archive) {
            TooltipIconButton(Icons.Outlined.Archive, stringResource(Res.string.inbox_archive), { onIntent(AppIntent.Archive(message)) })
        }
        if (caps.trash) {
            TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.inbox_trash), { onIntent(AppIntent.Trash(message)) })
        }
    }
}

/** "14:05" for today, "23/09" otherwise. */
@OptIn(ExperimentalTime::class)
internal fun formatTime(epochMillis: Long, withDate: Boolean = false): String {
    val zone = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(Platform.now()).toLocalDateTime(zone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    val time = "${two(then.hour)}:${two(then.minute)}"
    val date = "${two(then.day)}/${two(then.month.number)}/${then.year}"
    return when {
        withDate -> "$date $time"
        then.date == now.date -> time
        else -> "${two(then.day)}/${two(then.month.number)}"
    }
}
