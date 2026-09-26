package co.abaye.mailtice.main

import co.abaye.mailtice.calendar.dateLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeDraft
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.app.ComposeWindowMode
import co.abaye.mailtice.app.DraftSave
import co.abaye.mailtice.data.Contact
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.fileDropTarget
import co.abaye.mailtice.provider.parseAddressList
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.compose_attach
import mailtice.shared.generated.resources.compose_bcc
import mailtice.shared.generated.resources.compose_cc
import mailtice.shared.generated.resources.compose_close
import mailtice.shared.generated.resources.compose_delete_draft
import mailtice.shared.generated.resources.compose_drop_hint
import mailtice.shared.generated.resources.compose_formatting
import mailtice.shared.generated.resources.compose_forward_title
import mailtice.shared.generated.resources.compose_from
import mailtice.shared.generated.resources.compose_invalid_address
import mailtice.shared.generated.resources.compose_maximize
import mailtice.shared.generated.resources.compose_minimize
import mailtice.shared.generated.resources.compose_new_title
import mailtice.shared.generated.resources.compose_reply_title
import mailtice.shared.generated.resources.compose_restore
import mailtice.shared.generated.resources.compose_schedule
import mailtice.shared.generated.resources.compose_send
import mailtice.shared.generated.resources.compose_send_hint
import mailtice.shared.generated.resources.compose_show_quote
import mailtice.shared.generated.resources.compose_subject
import mailtice.shared.generated.resources.compose_to
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.draft_failed
import mailtice.shared.generated.resources.draft_saved
import mailtice.shared.generated.resources.draft_saving
import mailtice.shared.generated.resources.fmt_bold
import mailtice.shared.generated.resources.fmt_bullets
import mailtice.shared.generated.resources.fmt_italic
import mailtice.shared.generated.resources.fmt_link
import mailtice.shared.generated.resources.fmt_numbers
import mailtice.shared.generated.resources.fmt_strike
import mailtice.shared.generated.resources.fmt_underline
import mailtice.shared.generated.resources.fmt_unlink
import mailtice.shared.generated.resources.link_add
import mailtice.shared.generated.resources.link_text
import mailtice.shared.generated.resources.link_url
import mailtice.shared.generated.resources.schedule_confirm
import mailtice.shared.generated.resources.schedule_next_week
import mailtice.shared.generated.resources.schedule_note
import mailtice.shared.generated.resources.schedule_pick
import mailtice.shared.generated.resources.schedule_pick_time
import mailtice.shared.generated.resources.schedule_tomorrow_afternoon
import mailtice.shared.generated.resources.schedule_tomorrow_morning
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val LtrText = TextStyle(textDirection = TextDirection.Ltr)

/**
 * Gmail's compose window: docked at the bottom corner and not modal (the mail stays usable behind
 * it), with minimise / maximise / close in its title bar. Recipients become chips with suggestions
 * from mail already received, the body is a rich-text editor, a reply's quote stays folded under
 * "⋯", files can be attached, and "Send" has a menu to schedule it. Phones get the whole screen.
 */
@Composable
fun ComposeWindow(state: AppState, onIntent: (AppIntent) -> Unit) {
    val draft = state.compose ?: return
    val compact = LocalCompactLayout.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight
        when {
            compact -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                ComposeSheet(draft, state, onIntent, docked = false)
            }
            draft.window == ComposeWindowMode.Maximized -> {
                // A soft scrim, like Gmail's full-screen compose; clicking it shrinks the window back.
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onIntent(AppIntent.ComposeWindow(ComposeWindowMode.Normal))
                        },
                )
                Surface(
                    Modifier.align(Alignment.Center).widthIn(max = 1000.dp).fillMaxWidth(0.84f).fillMaxHeight(0.88f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 16.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) { ComposeSheet(draft, state, onIntent, docked = false) }
            }
            // Floating a little above the bottom edge, rounded all round, rather than glued to it.
            draft.window == ComposeWindowMode.Minimized -> Surface(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp).width(320.dp),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 12.dp,
            ) { ComposeHeader(draft, onIntent) }
            else -> Surface(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp).width(560.dp).height(minOf(640.dp, maxH - 40.dp)),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
            ) { ComposeSheet(draft, state, onIntent, docked = true) }
        }
    }
}

@Composable
private fun ComposeHeader(draft: ComposeDraft, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val minimized = draft.window == ComposeWindowMode.Minimized
    val title = when {
        draft.subject.isNotBlank() -> draft.subject
        draft.mode == ComposeMode.Forward -> stringResource(Res.string.compose_forward_title)
        draft.mode == ComposeMode.New -> stringResource(Res.string.compose_new_title)
        else -> stringResource(Res.string.compose_reply_title)
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(colors.surfaceContainerHighest)
            .clickable { onIntent(AppIntent.ComposeWindow(if (minimized) ComposeWindowMode.Normal else ComposeWindowMode.Minimized)) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.merge(ContentDirection),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Gmail's quiet "Draft saved" under the title.
            val status = when (draft.draftSave) {
                DraftSave.Saving -> stringResource(Res.string.draft_saving)
                DraftSave.Saved -> stringResource(Res.string.draft_saved)
                DraftSave.Failed -> stringResource(Res.string.draft_failed)
                DraftSave.None -> null
            }
            if (status != null && !minimized) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draft.draftSave == DraftSave.Failed) colors.error else colors.onSurfaceVariant,
                )
            }
        }
        if (!LocalCompactLayout.current) {
            TooltipIconButton(
                if (minimized) Icons.Outlined.ExpandMore else Icons.Outlined.Remove,
                stringResource(if (minimized) Res.string.compose_restore else Res.string.compose_minimize),
                { onIntent(AppIntent.ComposeWindow(if (minimized) ComposeWindowMode.Normal else ComposeWindowMode.Minimized)) },
            )
            val maximized = draft.window == ComposeWindowMode.Maximized
            TooltipIconButton(
                if (maximized) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                stringResource(if (maximized) Res.string.compose_restore else Res.string.compose_maximize),
                { onIntent(AppIntent.ComposeWindow(if (maximized) ComposeWindowMode.Normal else ComposeWindowMode.Maximized)) },
            )
        }
        TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.compose_close), { onIntent(AppIntent.CloseCompose) }, enabled = !draft.sending)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ComposeSheet(draft: ComposeDraft, state: AppState, onIntent: (AppIntent) -> Unit, docked: Boolean) {
    val current by rememberUpdatedState(draft)
    fun update(block: (ComposeDraft) -> ComposeDraft) = onIntent(AppIntent.UpdateCompose(block(current)))
    val rich = remember(draft.draftId) { RichTextState().apply { if (draft.initialHtml.isNotBlank()) setHtml(draft.initialHtml) } }
    // The editor reports its text and HTML a moment after typing stops.
    LaunchedEffect(rich) {
        snapshotFlow { rich.annotatedString }.debounce(150).distinctUntilChanged().collect {
            onIntent(AppIntent.ComposeBody(rich.toText(), rich.toHtml()))
        }
    }
    var formatting by remember { mutableStateOf(false) }
    var showQuote by remember(draft.draftId) { mutableStateOf(draft.mode == ComposeMode.Forward) }
    var linkDialog by remember { mutableStateOf(false) }
    // A file dragged over the body or the bottom bar: both attach it (images included, as files).
    var dropHover by remember { mutableStateOf(false) }
    val onDropped: (List<co.abaye.mailtice.platform.PickedFile>) -> Unit = { onIntent(AppIntent.ComposeAddFiles(it)) }
    val enabled = !draft.sending
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            val send = e.type == KeyEventType.KeyDown && e.key == Key.Enter && (e.isCtrlPressed || e.isMetaPressed)
            if (send) onIntent(AppIntent.SendCompose)
            send
        },
    ) {
        ComposeHeader(draft, onIntent)
        Column(Modifier.padding(horizontal = 16.dp)) {
            FromRow(draft, state.sendingAccounts, enabled) { id -> update { it.copy(accountId = id) } }
            RecipientField(
                label = stringResource(Res.string.compose_to),
                value = draft.to,
                suggestions = state.contactSuggestions,
                enabled = enabled,
                error = draft.invalidAddresses && parseAddressList(draft.to).isNullOrEmpty(),
                onQuery = { onIntent(AppIntent.ComposeSuggest(it)) },
                trailing = {
                    if (!draft.showCcBcc) {
                        TextButton(onClick = { update { it.copy(showCcBcc = true) } }) {
                            Text("${stringResource(Res.string.compose_cc)} / ${stringResource(Res.string.compose_bcc)}")
                        }
                    }
                },
            ) { v -> update { it.copy(to = v) } }
            if (draft.showCcBcc) {
                RecipientField(
                    stringResource(Res.string.compose_cc), draft.cc, state.contactSuggestions, enabled,
                    error = draft.invalidAddresses && parseAddressList(draft.cc) == null,
                    onQuery = { onIntent(AppIntent.ComposeSuggest(it)) },
                ) { v -> update { it.copy(cc = v) } }
                RecipientField(
                    stringResource(Res.string.compose_bcc), draft.bcc, state.contactSuggestions, enabled,
                    error = draft.invalidAddresses && parseAddressList(draft.bcc) == null,
                    onQuery = { onIntent(AppIntent.ComposeSuggest(it)) },
                ) { v -> update { it.copy(bcc = v) } }
            }
            LineField(stringResource(Res.string.compose_subject), draft.subject, enabled) { v -> update { it.copy(subject = v) } }
        }
        // The body: editor, the folded quote, then the attached files.
        Box(Modifier.weight(1f).fillMaxWidth().fileDropTarget(onHover = { dropHover = it }, onFiles = onDropped)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            BasicRichTextEditor(
                state = rich,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (docked) 160.dp else 280.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.merge(ContentDirection).copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
            )
            val quote = draft.quote
            if (quote != null) {
                Tooltip(stringResource(Res.string.compose_show_quote)) {
                    Box(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHigh)
                            .clickable { showQuote = !showQuote }.padding(horizontal = 8.dp),
                    ) { Icon(Icons.Outlined.MoreHoriz, stringResource(Res.string.compose_show_quote), tint = colors.onSurfaceVariant) }
                }
                if (showQuote) {
                    Text(
                        quote.trim(),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (draft.attachments.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.attachments.forEach { a ->
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHigh).padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            Text(
                                "${a.name} (${formatBytes(a.size)})",
                                Modifier.padding(horizontal = 6.dp).widthIn(max = 220.dp),
                                style = MaterialTheme.typography.labelMedium.merge(ContentDirection),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { onIntent(AppIntent.ComposeRemoveAttachment(a.id)) }, Modifier.size(32.dp), enabled = enabled) {
                                Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
            if (dropHover) DropHint()
        }
        if (draft.invalidAddresses) {
            Text(
                stringResource(Res.string.compose_invalid_address),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
        if (formatting) FormattingBar(rich, onLink = { linkDialog = true })
        Box(Modifier.fileDropTarget(onHover = { dropHover = it }, onFiles = onDropped)) {
            BottomBar(
                draft = draft,
                formatting = formatting,
                onToggleFormatting = { formatting = !formatting },
                onLink = { linkDialog = true },
                onIntent = onIntent,
            )
        }
    }
    if (linkDialog) LinkDialog(rich) { linkDialog = false }
}

/** Over the body while a file is dragged across it. */
@Composable
private fun DropHint() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp))
            .background(colors.primaryContainer.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AttachFile, null, tint = colors.onPrimaryContainer)
            Text(stringResource(Res.string.compose_drop_hint), color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun FromRow(draft: ComposeDraft, accounts: List<Account>, enabled: Boolean, onPick: (String) -> Unit) {
    val current = accounts.firstOrNull { it.id == draft.accountId } ?: return
    var open by remember { mutableStateOf(false) }
    FieldRow(stringResource(Res.string.compose_from)) {
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled && accounts.size > 1) { open = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AccountAvatar(current, size = 20.dp)
                Text(current.email, style = MaterialTheme.typography.bodyMedium.merge(LtrText))
                if (accounts.size > 1) Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        leadingIcon = { AccountAvatar(account, size = 22.dp) },
                        text = { Text(account.email, style = LtrText) },
                        onClick = {
                            open = false
                            onPick(account.id)
                        },
                    )
                }
            }
        }
    }
}

/** A label and its content on one line, with Gmail's hairline under it. */
@Composable
private fun FieldRow(label: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.padding(end = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) { content() }
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun LineField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    FieldRow(label) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.merge(ContentDirection).copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

/**
 * Recipients as chips. The field's value keeps the classic "a@x, b@y, typing" form: every finished
 * address ends with ", " and whatever follows the last comma is still being typed - so the view
 * model parses the same string it always did, typed-but-unfinished address included.
 * Enter, comma or semicolon finish an address (Enter takes the first suggestion when what was typed
 * is not an address yet); Backspace in an empty field removes the last chip.
 */
@Composable
private fun RecipientField(
    label: String,
    value: String,
    suggestions: List<Contact>,
    enabled: Boolean,
    error: Boolean,
    onQuery: (String) -> Unit,
    trailing: @Composable () -> Unit = {},
    onChange: (String) -> Unit,
) {
    val finished = value.isBlank() || value.trimEnd().endsWith(",")
    val parts = value.split(',').map { it.trim() }
    val chips = (if (finished) parts else parts.dropLast(1)).filter { it.isNotEmpty() }
    val pending = if (finished) "" else parts.last()
    var focused by remember { mutableStateOf(false) }
    fun emit(newChips: List<String>, typing: String) = onChange(newChips.joinToString("") { "$it, " } + typing)
    fun commit(text: String) {
        val t = text.trim().trimEnd(',', ';').trim()
        if (t.isNotEmpty()) emit(chips + t, "")
        onQuery("")
    }
    val colors = MaterialTheme.colorScheme
    Box {
        FieldRow(label, trailing = trailing) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                chips.forEachIndexed { i, chip ->
                    val valid = parseAddressList(chip) != null
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (valid) colors.secondaryContainer else colors.errorContainer)
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chip,
                            style = MaterialTheme.typography.labelLarge.merge(LtrText),
                            color = if (valid) colors.onSecondaryContainer else colors.onErrorContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                        IconButton(onClick = { emit(chips.filterIndexed { j, _ -> j != i }, pending) }, Modifier.size(26.dp), enabled = enabled) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
                BasicTextField(
                    value = pending,
                    onValueChange = { t ->
                        if (t.endsWith(",") || t.endsWith(";")) {
                            commit(t)
                        } else {
                            emit(chips, t)
                            onQuery(t)
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.merge(LtrText)
                        .copy(color = if (error) colors.error else colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.widthIn(min = 140.dp).padding(vertical = 6.dp)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (!it.isFocused && pending.isNotBlank()) commit(pending)
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.key == Key.Enter && !(e.isCtrlPressed || e.isMetaPressed) && pending.isNotBlank() -> {
                                    val pick = suggestions.firstOrNull()?.takeIf { '@' !in pending }
                                    commit(pick?.formatted ?: pending)
                                    true
                                }
                                e.key == Key.Backspace && pending.isEmpty() && chips.isNotEmpty() -> {
                                    emit(chips.dropLast(1), "")
                                    true
                                }
                                else -> false
                            }
                        },
                )
            }
        }
        if (focused && pending.isNotBlank() && suggestions.isNotEmpty()) {
            // Not focusable, so typing carries on in the field while the list is open.
            Popup(offset = IntOffset(0, 120), properties = PopupProperties(focusable = false), onDismissRequest = { onQuery("") }) {
                Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, color = colors.surfaceContainer) {
                    Column(Modifier.width(360.dp).padding(vertical = 6.dp)) {
                        suggestions.take(6).forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { commit(c.formatted) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                LetterAvatar(c.name.ifBlank { c.address }, colors.primary, size = 28.dp)
                                Column(Modifier.weight(1f)) {
                                    if (c.name.isNotBlank()) {
                                        Text(c.name, style = MaterialTheme.typography.bodyMedium.merge(ContentDirection), maxLines = 1)
                                    }
                                    Text(c.address, style = MaterialTheme.typography.bodySmall.merge(LtrText), color = colors.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gmail's formatting row: bold, italic, underline, strikethrough, lists, link. */
@Composable
private fun FormattingBar(rich: RichTextState, onLink: () -> Unit) {
    val style = rich.currentSpanStyle
    val decoration = style.textDecoration ?: TextDecoration.None
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatButton(Icons.Outlined.FormatBold, stringResource(Res.string.fmt_bold), style.fontWeight == FontWeight.Bold) {
            rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        FormatButton(Icons.Outlined.FormatItalic, stringResource(Res.string.fmt_italic), style.fontStyle == FontStyle.Italic) {
            rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        FormatButton(Icons.Outlined.FormatUnderlined, stringResource(Res.string.fmt_underline), decoration.contains(TextDecoration.Underline)) {
            rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }
        FormatButton(Icons.Outlined.FormatStrikethrough, stringResource(Res.string.fmt_strike), decoration.contains(TextDecoration.LineThrough)) {
            rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }
        FormatButton(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(Res.string.fmt_bullets), rich.isUnorderedList) { rich.toggleUnorderedList() }
        FormatButton(Icons.Outlined.FormatListNumbered, stringResource(Res.string.fmt_numbers), rich.isOrderedList) { rich.toggleOrderedList() }
        if (rich.isLink) {
            FormatButton(Icons.Outlined.LinkOff, stringResource(Res.string.fmt_unlink), true) { rich.removeLink() }
        } else {
            FormatButton(Icons.Outlined.Link, stringResource(Res.string.fmt_link), false, onLink)
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Tooltip(label) {
        Box(
            Modifier.padding(2.dp).size(36.dp).clip(RoundedCornerShape(8.dp))
                .background(if (active) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant) }
    }
}

@Composable
private fun BottomBar(
    draft: ComposeDraft,
    formatting: Boolean,
    onToggleFormatting: () -> Unit,
    onLink: () -> Unit,
    onIntent: (AppIntent) -> Unit,
) {
    val enabled = !draft.sending
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SendButton(draft, onIntent)
        Spacer(Modifier.width(8.dp))
        TooltipIconButton(
            Icons.Outlined.TextFormat,
            stringResource(Res.string.compose_formatting),
            onToggleFormatting,
            tint = if (formatting) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
        if (Platform.canPickFiles) {
            TooltipIconButton(Icons.Outlined.AttachFile, stringResource(Res.string.compose_attach), { onIntent(AppIntent.ComposeAttach) }, enabled = enabled)
        }
        TooltipIconButton(Icons.Outlined.Link, stringResource(Res.string.fmt_link), onLink, enabled = enabled)
        Spacer(Modifier.weight(1f))
        TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.compose_delete_draft), { onIntent(AppIntent.DiscardCompose) }, enabled = enabled)
    }
}

/** Gmail's split button: "Send", and beside it the arrow that opens "Schedule send". */
@OptIn(ExperimentalTime::class)
@Composable
private fun SendButton(draft: ComposeDraft, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val enabled = !draft.sending
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.primary), verticalAlignment = Alignment.CenterVertically) {
        Tooltip(stringResource(Res.string.compose_send_hint)) {
            Row(
                Modifier.fillMaxHeight().clickable(enabled = enabled) { onIntent(AppIntent.SendCompose) }.padding(start = 18.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.sending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp), tint = colors.onPrimary)
                }
                Text(stringResource(Res.string.compose_send), color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(Modifier.width(1.dp).height(24.dp).background(colors.onPrimary.copy(alpha = 0.35f)))
        Box {
            Tooltip(stringResource(Res.string.compose_schedule)) {
                Box(
                    Modifier.fillMaxHeight().clickable(enabled = enabled) { menu = true }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowDropDown, stringResource(Res.string.compose_schedule), tint = colors.onPrimary) }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text(
                    stringResource(Res.string.compose_schedule),
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                schedulePresets().forEach { (label, at) ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                        text = {
                            Column {
                                Text(label)
                                Text(dateLabel(at, withDate = true), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            menu = false
                            onIntent(AppIntent.ScheduleCompose(at))
                        },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                    text = { Text(stringResource(Res.string.schedule_pick)) },
                    onClick = {
                        menu = false
                        picker = true
                    },
                )
                Text(
                    stringResource(Res.string.schedule_note),
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 280.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
    if (picker) ScheduleDialog(onPick = { at -> picker = false; onIntent(AppIntent.ScheduleCompose(at)) }, onDismiss = { picker = false })
}

/** Gmail's three presets: tomorrow morning, tomorrow afternoon, the start of next week (Sunday here). */
@OptIn(ExperimentalTime::class)
@Composable
private fun schedulePresets(): List<Pair<String, Long>> {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val tomorrow = today.plus(DatePeriod(days = 1))
    val daysToSunday = (7 - today.dayOfWeek.isoDayNumber % 7).let { if (it == 0) 7 else it }
    val sunday = today.plus(DatePeriod(days = daysToSunday))
    fun at(date: kotlinx.datetime.LocalDate, hour: Int) = date.atTime(LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()
    return listOf(
        stringResource(Res.string.schedule_tomorrow_morning) to at(tomorrow, 8),
        stringResource(Res.string.schedule_tomorrow_afternoon) to at(tomorrow, 13),
        stringResource(Res.string.schedule_next_week) to at(if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plus(DatePeriod(days = 7)) else sunday, 8),
    )
}

/** Any date, then any time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun ScheduleDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis = Clock.System.now().toEpochMilliseconds())
    var date by remember { mutableStateOf<Long?>(null) }
    if (date == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { date = dateState.selectedDateMillis }) { Text(stringResource(Res.string.schedule_pick_time)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
        ) { DatePicker(dateState) }
        return
    }
    val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.schedule_pick_time)) },
        text = { TimePicker(timeState) },
        confirmButton = {
            TextButton(onClick = {
                val zone = TimeZone.currentSystemDefault()
                // The picker's date is midnight UTC of the chosen day.
                val day = kotlin.time.Instant.fromEpochMilliseconds(date!!).toLocalDateTime(TimeZone.UTC).date
                val at = LocalDateTime(day, LocalTime(timeState.hour, timeState.minute)).toInstant(zone).toEpochMilliseconds()
                onPick(at)
            }) { Text(stringResource(Res.string.schedule_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** Link on the selected text, or a new linked text at the cursor. */
@Composable
private fun LinkDialog(rich: RichTextState, onDismiss: () -> Unit) {
    val collapsed = rich.selection.collapsed
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fmt_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (collapsed) OutlinedTextField(text, { text = it }, label = { Text(stringResource(Res.string.link_text)) }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(Res.string.link_url)) }, singleLine = true, textStyle = LtrText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = url.trim()
                    if (collapsed) rich.addLink(text.ifBlank { target }, target) else rich.addLinkToSelection(target)
                    onDismiss()
                },
                enabled = url.trim().length > "https://".length,
            ) { Text(stringResource(Res.string.link_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}
