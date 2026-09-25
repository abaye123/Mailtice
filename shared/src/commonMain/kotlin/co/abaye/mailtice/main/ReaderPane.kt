package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.ReplyAll
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.app.ExportFormat
import co.abaye.mailtice.app.Reader
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.AutoLinkedText
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.inbox_archive
import mailtice.shared.generated.resources.inbox_mark_unread
import mailtice.shared.generated.resources.reader_back
import mailtice.shared.generated.resources.inbox_trash
import mailtice.shared.generated.resources.reader_body_failed
import mailtice.shared.generated.resources.reader_download_all
import mailtice.shared.generated.resources.reader_download_one
import mailtice.shared.generated.resources.reader_download_thread
import mailtice.shared.generated.resources.reader_export_html
import mailtice.shared.generated.resources.reader_export_mail
import mailtice.shared.generated.resources.reader_forward
import mailtice.shared.generated.resources.reader_more
import mailtice.shared.generated.resources.reader_open_html
import mailtice.shared.generated.resources.reader_open_web
import mailtice.shared.generated.resources.reader_reply
import mailtice.shared.generated.resources.reader_reply_all
import mailtice.shared.generated.resources.reader_to
import org.jetbrains.compose.resources.stringResource

/** The reader as a full page (narrow layouts). */
@Composable
fun ReaderScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val reader = state.reader ?: return
    ReaderPane(
        reader, state.account(reader.message.accountId), onIntent, modifier,
        showBack = true, working = state.working, labels = state.labelsOf(reader.message),
    )
}

/**
 * After the approved design: an action bar, a large subject with the account as a chip, the sender
 * with an avatar, the body at a comfortable reading width and attachments as cards.
 */
@Composable
fun ReaderPane(
    reader: Reader,
    account: Account?,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    working: Boolean = false,
    labels: List<Folder> = emptyList(),
) {
    val message = reader.message
    val colors = MaterialTheme.colorScheme
    val cards = cardStyle()
    Column(modifier.fillMaxSize()) {
        ReaderActions(reader, account, onIntent, showBack, working)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (!cards) HorizontalDivider(color = colors.outlineVariant)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (cards) 32.dp else 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer {
                    Text(
                        message.subject,
                        style = MaterialTheme.typography.headlineSmall.merge(ContentDirection),
                        fontWeight = FontWeight.Normal,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (account != null) AccountChip(account)
                    labels.forEach { LabelChip(it, small = false) }
                }
            }
            SenderLine(message, account)
            val body = reader.body
            if (body != null && body.attachments.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    body.attachments.forEachIndexed { index, attachment ->
                        AttachmentCard(attachment, enabled = !working) { onIntent(AppIntent.DownloadAttachments(message, index)) }
                    }
                    if (body.attachments.size > 1) {
                        TextButton(onClick = { onIntent(AppIntent.DownloadAttachments(message)) }, enabled = !working) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                            Text(stringResource(Res.string.reader_download_all), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            if (!cards) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Box(Modifier.widthIn(max = 680.dp)) {
                when {
                    body != null -> SelectionContainer {
                        AutoLinkedText(
                            body.text.ifBlank { message.snippet },
                            style = MaterialTheme.typography.bodyLarge.merge(ContentDirection),
                            onOpen = { onIntent(AppIntent.OpenUrl(it)) },
                        )
                    }
                    reader.failed -> Text(stringResource(Res.string.reader_body_failed), color = colors.error)
                    else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            if (account?.capabilities?.send == true) ReplyButtons(message, onIntent)
        }
    }
}

@Composable
private fun ReplyButtons(message: MailMessage, onIntent: (AppIntent) -> Unit) {
    FlowRow(
        Modifier.padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onIntent(AppIntent.StartCompose(ComposeMode.Reply, message)) }) {
            Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.reader_reply), Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = { onIntent(AppIntent.StartCompose(ComposeMode.ReplyAll, message)) }) {
            Icon(Icons.AutoMirrored.Outlined.ReplyAll, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.reader_reply_all), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { onIntent(AppIntent.StartCompose(ComposeMode.Forward, message)) }) {
            Icon(Icons.AutoMirrored.Outlined.Forward, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.reader_forward), Modifier.padding(start = 8.dp))
        }
    }
}

/** Only what this account supports - nothing is offered that would fail. Every icon has a tooltip. */
@Composable
private fun ReaderActions(reader: Reader, account: Account?, onIntent: (AppIntent) -> Unit, showBack: Boolean, working: Boolean) {
    val message = reader.message
    val caps = account?.capabilities
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showBack) {
            TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.reader_back), { onIntent(AppIntent.CloseReader) })
        }
        if (caps?.archive == true) {
            TooltipIconButton(Icons.Outlined.Archive, stringResource(Res.string.inbox_archive), { onIntent(AppIntent.Archive(message)) })
        }
        if (caps?.trash == true) {
            TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.inbox_trash), { onIntent(AppIntent.Trash(message)) })
        }
        if (caps?.markRead == true) {
            TooltipIconButton(Icons.Outlined.MarkEmailUnread, stringResource(Res.string.inbox_mark_unread), {
                onIntent(AppIntent.SetRead(message, read = false))
            })
        }
        if (caps?.send == true) {
            TooltipIconButton(Icons.AutoMirrored.Outlined.Reply, stringResource(Res.string.reader_reply), {
                onIntent(AppIntent.StartCompose(ComposeMode.Reply, message))
            })
        }
        Box(Modifier.weight(1f))
        if (Platform.isDesktop && reader.body?.html?.isNotBlank() == true) {
            TooltipIconButton(Icons.Outlined.Code, stringResource(Res.string.reader_open_html), { onIntent(AppIntent.OpenHtml(message)) })
        }
        if (caps?.openInWeb == true) {
            TooltipIconButton(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(Res.string.reader_open_web), { onIntent(AppIntent.OpenInWeb(message)) })
        }
        MoreActions(message, enabled = !working, onIntent)
    }
}

/** Conversation-wide actions: every attachment in the thread, and export. */
@Composable
private fun MoreActions(message: MailMessage, enabled: Boolean, onIntent: (AppIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(Icons.Outlined.MoreVert, stringResource(Res.string.reader_more), { open = true }, enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reader_download_thread)) },
                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                onClick = {
                    open = false
                    onIntent(AppIntent.DownloadThreadAttachments(message))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reader_export_html)) },
                leadingIcon = { Icon(Icons.Outlined.Html, null) },
                onClick = {
                    open = false
                    onIntent(AppIntent.ExportThread(message, ExportFormat.Html))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.reader_export_mail)) },
                leadingIcon = { Icon(Icons.Outlined.Mail, null) },
                onClick = {
                    open = false
                    onIntent(AppIntent.ExportThread(message, ExportFormat.Mail))
                },
            )
        }
    }
}

@Composable
private fun AccountChip(account: Account) {
    val colors = MaterialTheme.colorScheme
    Tooltip(account.email) {
        Row(
            Modifier.height(28.dp).background(colors.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AccountDot(account)
            Text(account.displayName, style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer)
        }
    }
}

@Composable
private fun SenderLine(message: MailMessage, account: Account?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LetterAvatar(message.sender, account?.color?.color ?: colors.primary, size = 40.dp)
        Column(Modifier.weight(1f)) {
            SelectionContainer {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.sender,
                        style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (message.fromName.isNotBlank()) {
                        Text(
                            "<${message.fromAddress}>",
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val to = if (message.toLine.isNotBlank()) "${stringResource(Res.string.reader_to)} ${message.toLine} · " else ""
            Text(
                to + formatTime(message.receivedAt, withDate = true),
                style = MaterialTheme.typography.bodySmall.merge(ContentDirection),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The design's attachment card: a type badge, the name and the size. A click downloads the file. */
@Composable
private fun AttachmentCard(attachment: Attachment, enabled: Boolean, onDownload: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extension = attachment.name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "FILE" }
    Tooltip(stringResource(Res.string.reader_download_one, attachment.name)) {
        Row(
            Modifier.widthIn(min = 200.dp, max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onDownload)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(colors.primaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(extension, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatBytes(attachment.size), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
    }
}
