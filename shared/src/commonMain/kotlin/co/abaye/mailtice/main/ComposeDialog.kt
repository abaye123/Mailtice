package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeDraft
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.provider.parseAddressList
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.compose_bcc
import mailtice.shared.generated.resources.compose_body
import mailtice.shared.generated.resources.compose_cc
import mailtice.shared.generated.resources.compose_cc_bcc
import mailtice.shared.generated.resources.compose_discard
import mailtice.shared.generated.resources.compose_forward_title
import mailtice.shared.generated.resources.compose_from
import mailtice.shared.generated.resources.compose_invalid_address
import mailtice.shared.generated.resources.compose_new_title
import mailtice.shared.generated.resources.compose_reply_title
import mailtice.shared.generated.resources.compose_send
import mailtice.shared.generated.resources.compose_send_hint
import mailtice.shared.generated.resources.compose_subject
import mailtice.shared.generated.resources.compose_to
import org.jetbrains.compose.resources.stringResource

private val LtrField = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr)

/**
 * The message editor: a floating window on wide screens, the whole screen on phones. Plain text
 * only. Ctrl+Enter (Cmd+Enter on macOS) sends; closing discards the draft.
 */
@Composable
fun ComposeDialog(state: AppState, onIntent: (AppIntent) -> Unit) {
    val draft = state.compose ?: return
    val compact = LocalCompactLayout.current
    Dialog(
        onDismissRequest = { onIntent(AppIntent.CloseCompose) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = if (compact) {
                Modifier.fillMaxSize()
            } else {
                Modifier.widthIn(min = 560.dp, max = 760.dp).fillMaxWidth(0.7f).heightIn(min = 480.dp, max = 720.dp)
            }.onPreviewKeyEvent { e ->
                val send = e.type == KeyEventType.KeyDown && e.key == Key.Enter && (e.isCtrlPressed || e.isMetaPressed)
                if (send) onIntent(AppIntent.SendCompose)
                send
            },
            shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
        ) {
            ComposeContent(draft, state, onIntent)
        }
    }
}

@Composable
private fun ComposeContent(draft: ComposeDraft, state: AppState, onIntent: (AppIntent) -> Unit) {
    fun update(block: (ComposeDraft) -> ComposeDraft) = onIntent(AppIntent.UpdateCompose(block(draft)))
    val enabled = !draft.sending
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (draft.mode) {
                    ComposeMode.New -> stringResource(Res.string.compose_new_title)
                    ComposeMode.Reply, ComposeMode.ReplyAll -> stringResource(Res.string.compose_reply_title)
                    ComposeMode.Forward -> stringResource(Res.string.compose_forward_title)
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.compose_discard), { onIntent(AppIntent.CloseCompose) }, enabled = enabled)
        }
        FromPicker(draft, state.sendingAccounts, enabled) { id -> update { it.copy(accountId = id) } }
        AddressField(
            label = stringResource(Res.string.compose_to),
            value = draft.to,
            showError = draft.invalidAddresses && parseAddressList(draft.to).isNullOrEmpty(),
            enabled = enabled,
            trailing = if (draft.showCcBcc) {
                null
            } else {
                { TextButton(onClick = { update { it.copy(showCcBcc = true) } }) { Text(stringResource(Res.string.compose_cc_bcc)) } }
            },
        ) { v -> update { it.copy(to = v) } }
        if (draft.showCcBcc) {
            AddressField(
                stringResource(Res.string.compose_cc),
                draft.cc,
                showError = draft.invalidAddresses && parseAddressList(draft.cc) == null,
                enabled = enabled,
            ) { v -> update { it.copy(cc = v) } }
            AddressField(
                stringResource(Res.string.compose_bcc),
                draft.bcc,
                showError = draft.invalidAddresses && parseAddressList(draft.bcc) == null,
                enabled = enabled,
            ) { v -> update { it.copy(bcc = v) } }
        }
        OutlinedTextField(
            value = draft.subject,
            onValueChange = { v -> update { it.copy(subject = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.compose_subject)) },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.merge(ContentDirection),
        )
        OutlinedTextField(
            value = draft.body,
            onValueChange = { v -> update { it.copy(body = v) } },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(Res.string.compose_body)) },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.merge(ContentDirection),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tooltip(stringResource(Res.string.compose_send_hint)) {
                Button(onClick = { onIntent(AppIntent.SendCompose) }, enabled = enabled) {
                    if (draft.sending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp))
                    }
                    Text(stringResource(Res.string.compose_send), Modifier.padding(start = 8.dp))
                }
            }
            TextButton(onClick = { onIntent(AppIntent.CloseCompose) }, enabled = enabled) {
                Text(stringResource(Res.string.compose_discard))
            }
            if (draft.invalidAddresses) {
                Text(
                    stringResource(Res.string.compose_invalid_address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FromPicker(draft: ComposeDraft, accounts: List<Account>, enabled: Boolean, onPick: (String) -> Unit) {
    val current = accounts.firstOrNull { it.id == draft.accountId } ?: return
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.compose_from), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            TextButton(onClick = { open = true }, enabled = enabled && accounts.size > 1) {
                AccountAvatar(current, size = 22.dp)
                Text(current.email, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium.merge(LtrField))
                if (accounts.size > 1) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        leadingIcon = { AccountAvatar(account, size = 22.dp) },
                        text = { Text(account.email, style = LtrField) },
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

@Composable
private fun AddressField(
    label: String,
    value: String,
    showError: Boolean,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = showError,
        trailingIcon = trailing,
        // Addresses are always left-to-right, even in the Hebrew interface.
        textStyle = MaterialTheme.typography.bodyLarge.merge(LtrField),
    )
}
