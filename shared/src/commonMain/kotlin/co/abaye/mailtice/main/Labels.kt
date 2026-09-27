package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.LabelColors
import co.abaye.mailtice.domain.LabelPalette
import co.abaye.mailtice.domain.isUserLabel
import co.abaye.mailtice.ui.SectionHeader
import co.abaye.mailtice.ui.TooltipIconButton
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.folder_delete_body
import mailtice.shared.generated.resources.folder_delete_title
import mailtice.shared.generated.resources.folder_edit_title
import mailtice.shared.generated.resources.folders_empty
import mailtice.shared.generated.resources.folders_manage
import mailtice.shared.generated.resources.folders_new
import mailtice.shared.generated.resources.label_color
import mailtice.shared.generated.resources.label_delete
import mailtice.shared.generated.resources.label_delete_body
import mailtice.shared.generated.resources.label_delete_title
import mailtice.shared.generated.resources.label_edit
import mailtice.shared.generated.resources.label_edit_title
import mailtice.shared.generated.resources.label_hide
import mailtice.shared.generated.resources.label_more
import mailtice.shared.generated.resources.label_name
import mailtice.shared.generated.resources.label_no_color
import mailtice.shared.generated.resources.label_pin
import mailtice.shared.generated.resources.label_save
import mailtice.shared.generated.resources.label_show
import mailtice.shared.generated.resources.label_unpin
import mailtice.shared.generated.resources.labels_empty
import mailtice.shared.generated.resources.labels_hint
import mailtice.shared.generated.resources.labels_manage
import mailtice.shared.generated.resources.labels_new
import mailtice.shared.generated.resources.labels_section_hidden
import mailtice.shared.generated.resources.labels_section_pinned
import mailtice.shared.generated.resources.labels_section_shown
import mailtice.shared.generated.resources.reader_back
import org.jetbrains.compose.resources.stringResource

/** The settings key of a label or folder: "<accountId>/<folderId>". */
val Folder.key: String get() = "$accountId/$id"

/**
 * An account's labels in sidebar order: pinned ones first, the rest as the server lists them.
 * Hidden ones are left out unless [withHidden].
 */
fun AppState.sidebarLabels(accountId: String, withHidden: Boolean = false): List<Folder> {
    val settings = data.settings
    return customFolders(accountId)
        .filter { withHidden || it.key !in settings.hiddenFolders }
        .sortedBy { if (it.key in settings.pinnedLabels) 0 else 1 }
}

/** "Labels" on Gmail, "folders" on IMAP: the word each provider uses for its own. */
@Composable
fun Account.manageLabelsTitle(): String =
    stringResource(if (capabilities.labels) Res.string.labels_manage else Res.string.folders_manage)

/**
 * The pin and the three dots a label shows at the far end of its sidebar row on hover: pin to the
 * top of the account's list, and a menu to edit, hide or delete it. The dialogs live here too, so a
 * row carries everything it needs.
 */
@Composable
internal fun LabelControls(account: Account, folder: Folder, pinned: Boolean, onIntent: (AppIntent) -> Unit, onMenuOpen: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val editable = account.capabilities.manageLabels && folder.isUserLabel
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    fun setMenu(open: Boolean) {
        menu = open
        onMenuOpen(open)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TooltipIconButton(
            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            stringResource(if (pinned) Res.string.label_unpin else Res.string.label_pin),
            { onIntent(AppIntent.SetLabelPinned(folder.key, !pinned)) },
            modifier = Modifier.size(32.dp),
            tint = if (pinned) MaterialTheme.colorScheme.primary else tint,
        )
        Box {
            TooltipIconButton(Icons.Outlined.MoreVert, stringResource(Res.string.label_more), { setMenu(true) }, modifier = Modifier.size(32.dp), tint = tint)
            DropdownMenu(expanded = menu, onDismissRequest = { setMenu(false) }) {
                if (editable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.label_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            setMenu(false)
                            editing = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.label_hide)) },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                    onClick = {
                        setMenu(false)
                        onIntent(AppIntent.SetFolderHidden(folder.key, true))
                    },
                )
                if (editable) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.label_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            setMenu(false)
                            deleting = true
                        },
                    )
                }
            }
        }
    }
    if (editing) {
        LabelDialog(account, folder, onDismiss = { editing = false }) { name, color ->
            onIntent(AppIntent.UpdateLabel(account.id, folder.id, name, color))
        }
    }
    if (deleting) {
        DeleteLabelDialog(account, folder, onDismiss = { deleting = false }) {
            onIntent(AppIntent.DeleteLabel(account.id, folder.id))
        }
    }
}

/**
 * Name and colour of a label, new ([folder] null) or existing. The colours are Gmail's own
 * palette; IMAP folders have no colour, so they get the name alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelDialog(account: Account, folder: Folder?, onDismiss: () -> Unit, onSave: (String, LabelColors?) -> Unit) {
    val gmail = account.capabilities.labels
    var name by remember { mutableStateOf(folder?.name.orEmpty()) }
    var color by remember { mutableStateOf(LabelPalette.firstOrNull { it.background.equals(folder?.color, ignoreCase = true) }) }
    val taken = name.isNotBlank() && name.trim() != folder?.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        folder == null && gmail -> Res.string.labels_new
                        folder == null -> Res.string.folders_new
                        gmail -> Res.string.label_edit_title
                        else -> Res.string.folder_edit_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (account.capabilities.labelColors) {
                    Text(stringResource(Res.string.label_color), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSwatch(null, selected = color == null) { color = null }
                        LabelPalette.forEach { c -> ColorSwatch(c, selected = color == c) { color = c } }
                    }
                }
            }
        },
        confirmButton = {
            // An unchanged name with a changed colour still saves; an empty name never does.
            val changed = taken || color?.background != folder?.color?.takeIf { it.isNotEmpty() }
            Button(
                onClick = {
                    onSave(name.trim(), color)
                    onDismiss()
                },
                enabled = name.isNotBlank() && (folder == null || changed),
            ) { Text(stringResource(Res.string.label_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun ColorSwatch(color: LabelColors?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill = color?.let { hexColor(it.background) } ?: colors.surfaceContainerHighest
    val label = if (color == null) stringResource(Res.string.label_no_color) else color.background
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(fill)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onSurface else colors.outlineVariant, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = color?.let { hexColor(it.text) } ?: colors.onSurface)
            color == null -> Icon(Icons.Outlined.FormatColorReset, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}

private fun hexColor(hex: String): Color = Color(0xFF000000 or (hex.removePrefix("#").toLongOrNull(16) ?: 0L))

@Composable
internal fun DeleteLabelDialog(account: Account, folder: Folder, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val gmail = account.capabilities.labels
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (gmail) Res.string.label_delete_title else Res.string.folder_delete_title, folder.name)) },
        text = { Text(stringResource(if (gmail) Res.string.label_delete_body else Res.string.folder_delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(Res.string.label_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/**
 * One account's labels (Gmail) or folders (IMAP), hidden ones included: the only place a hidden
 * label can be brought back, and where new ones are made. Grouped as the sidebar shows them.
 */
@Composable
fun LabelsScreen(accountId: String, state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val account = state.account(accountId) ?: return
    val settings = state.data.settings
    val all = state.customFolders(account.id)
    val pinned = all.filter { it.key in settings.pinnedLabels && it.key !in settings.hiddenFolders }
    val hidden = all.filter { it.key in settings.hiddenFolders }
    val shown = all - pinned.toSet() - hidden.toSet()
    val gmail = account.capabilities.labels
    var creating by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.reader_back), { onIntent(AppIntent.Back) })
                AccountDot(account, Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.manageLabelsTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Clear of the "back to mail" pill that floats over the top end of these pages.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.labels_hint),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.capabilities.manageLabels) {
                    Button(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Text(stringResource(if (gmail) Res.string.labels_new else Res.string.folders_new), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (all.isEmpty()) {
                Text(
                    stringResource(if (gmail) Res.string.labels_empty else Res.string.folders_empty),
                    Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            listOf(
                Res.string.labels_section_pinned to pinned,
                Res.string.labels_section_shown to shown,
                Res.string.labels_section_hidden to hidden,
            ).forEach { (title, folders) ->
                if (folders.isNotEmpty()) {
                    SectionHeader(stringResource(title), Modifier.padding(top = 12.dp))
                    folders.forEach { folder ->
                        LabelManageRow(
                            account = account,
                            folder = folder,
                            pinned = folder.key in settings.pinnedLabels,
                            hidden = folder.key in settings.hiddenFolders,
                            unread = (state.unreadByFolder[account.id]?.get(folder.id) ?: 0L).toInt(),
                            onIntent = onIntent,
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        LabelDialog(account, null, onDismiss = { creating = false }) { name, color ->
            onIntent(AppIntent.CreateLabel(account.id, name, color))
        }
    }
}

@Composable
private fun LabelManageRow(account: Account, folder: Folder, pinned: Boolean, hidden: Boolean, unread: Int, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val editable = account.capabilities.manageLabels && folder.isUserLabel
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !hidden) { onIntent(AppIntent.OpenLabel(account.id, folder.id)) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(FolderIcon, null, tint = folder.labelColor() ?: colors.onSurfaceVariant)
        Text(
            folder.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (hidden) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unread > 0) Text(unread.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        if (!hidden) {
            TooltipIconButton(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                stringResource(if (pinned) Res.string.label_unpin else Res.string.label_pin),
                { onIntent(AppIntent.SetLabelPinned(folder.key, !pinned)) },
                tint = if (pinned) colors.primary else colors.onSurfaceVariant,
            )
        } else {
            // A hidden label cannot be pinned; the gap keeps the other buttons in their columns.
            Spacer(Modifier.size(48.dp))
        }
        TooltipIconButton(
            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            stringResource(if (hidden) Res.string.label_show else Res.string.label_hide),
            { onIntent(AppIntent.SetFolderHidden(folder.key, !hidden)) },
            tint = if (hidden) colors.outline else colors.onSurfaceVariant,
        )
        if (editable) {
            TooltipIconButton(Icons.Outlined.Edit, stringResource(Res.string.label_edit), { editing = true }, tint = colors.onSurfaceVariant)
            TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.label_delete), { deleting = true }, tint = colors.onSurfaceVariant)
        }
    }
    if (editing) {
        LabelDialog(account, folder, onDismiss = { editing = false }) { name, color ->
            onIntent(AppIntent.UpdateLabel(account.id, folder.id, name, color))
        }
    }
    if (deleting) {
        DeleteLabelDialog(account, folder, onDismiss = { deleting = false }) { onIntent(AppIntent.DeleteLabel(account.id, folder.id)) }
    }
}
