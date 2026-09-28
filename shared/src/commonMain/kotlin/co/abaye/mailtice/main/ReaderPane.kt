package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ComposeMode
import co.abaye.mailtice.app.ExportFormat
import co.abaye.mailtice.app.Reader
import co.abaye.mailtice.app.TranslationState
import co.abaye.mailtice.calendar.dateLabel
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.translate.LocalTranslationOffer
import co.abaye.mailtice.translate.languageName
import co.abaye.mailtice.translate.looksForeign
import co.abaye.mailtice.ui.AutoLinkedText
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.inbox_archive
import mailtice.shared.generated.resources.inbox_mark_unread
import mailtice.shared.generated.resources.inbox_trash
import mailtice.shared.generated.resources.reader_back
import mailtice.shared.generated.resources.reader_body_failed
import mailtice.shared.generated.resources.reader_download_all
import mailtice.shared.generated.resources.reader_download_one
import mailtice.shared.generated.resources.reader_download_thread
import mailtice.shared.generated.resources.reader_export_html
import mailtice.shared.generated.resources.reader_export_mail
import mailtice.shared.generated.resources.reader_forward
import mailtice.shared.generated.resources.reader_hide_quoted
import mailtice.shared.generated.resources.reader_images_hidden
import mailtice.shared.generated.resources.reader_more
import mailtice.shared.generated.resources.reader_open_html
import mailtice.shared.generated.resources.reader_open_web
import mailtice.shared.generated.resources.reader_preview_one
import mailtice.shared.generated.resources.reader_reply
import mailtice.shared.generated.resources.reader_reply_all
import mailtice.shared.generated.resources.reader_show_images
import mailtice.shared.generated.resources.reader_show_quoted
import mailtice.shared.generated.resources.reader_to
import mailtice.shared.generated.resources.translate_action
import mailtice.shared.generated.resources.translate_done
import mailtice.shared.generated.resources.translate_failed
import mailtice.shared.generated.resources.translate_loading
import mailtice.shared.generated.resources.translate_menu
import mailtice.shared.generated.resources.translate_offer
import mailtice.shared.generated.resources.translate_retry
import mailtice.shared.generated.resources.translate_show_original
import mailtice.shared.generated.resources.translate_show_translation
import mailtice.shared.generated.resources.translate_showing_original
import org.jetbrains.compose.resources.stringResource

/** The reader as a full page (narrow layouts). */
@Composable
fun ReaderScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val reader = state.reader ?: return
    ReaderPane(
        reader,
        state.account(reader.message.accountId),
        onIntent,
        modifier,
        showBack = true,
        working = state.working,
        labels = state.labelsOf(reader.message),
        webPaused = state.preview != null,
    )
}

/**
 * After the approved design: an action bar, a large subject with the account as a chip, the sender
 * with an avatar, then the body across the full width. HTML mail is drawn by the platform's own
 * browser engine and scrolls inside its own area under the fixed header; plain text scrolls with
 * the header. Quoted earlier messages start folded either way.
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
    webPaused: Boolean = false,
) {
    val message = reader.message
    val colors = MaterialTheme.colorScheme
    val cards = cardStyle()
    val prefs = LocalReaderPrefs.current
    val body = reader.body
    val translation = reader.translation
    // HTML is translated in place; only a message translated as plain text falls back to text.
    val html = if (translation?.showing == true) translation.html.takeIf { it.isNotBlank() } else body?.html?.takeIf { it.isNotBlank() }
    var showQuoted by remember(message.key) { mutableStateOf(false) }
    var showImages by remember(message.key) { mutableStateOf(prefs.loadRemoteImages) }
    val padding = Modifier.padding(horizontal = if (cards) 32.dp else 24.dp)
    Column(modifier.fillMaxSize()) {
        ReaderActions(reader, account, onIntent, showBack, working)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (!cards) HorizontalDivider(color = colors.outlineVariant)
        if (html != null) {
            Column(padding.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderHeader(reader, account, labels, working, onIntent)
                val remote = remember(html) { htmlLoadsRemote(html) }
                if (remote && !showImages) ImagesBar { showImages = true }
                val quoted = remember(html) { htmlHasQuote(html) }
                val inline = reader.inlineImages
                val document = remember(html, showQuoted, showImages, inline) {
                    emailDocument(html, hideQuotes = quoted && !showQuoted, remoteImages = showImages, inlineImages = inline)
                }
                // A native view draws above Compose: while the viewer is open over it, the page steps aside.
                if (webPaused) {
                    Box(Modifier.weight(1f).fillMaxWidth())
                } else {
                    HtmlBody(document, onOpenUrl = { onIntent(AppIntent.OpenUrl(it)) }, modifier = Modifier.weight(1f).fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (account?.capabilities?.send == true) ReplyButtons(message, onIntent)
                    Spacer(Modifier.weight(1f))
                    if (quoted) QuoteToggle(showQuoted) { showQuoted = !showQuoted }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(padding).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ReaderHeader(reader, account, labels, working, onIntent)
                when {
                    body != null -> {
                        val split = remember(body) { splitQuote(body.text.ifBlank { message.snippet }) }
                        SelectionContainer {
                            AutoLinkedText(
                                if (translation?.showing == true) translation.body else split.main,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge.merge(ContentDirection),
                                onOpen = { onIntent(AppIntent.OpenUrl(it)) },
                            )
                        }
                        if (split.hasQuote) {
                            QuoteToggle(showQuoted) { showQuoted = !showQuoted }
                            if (showQuoted) {
                                SelectionContainer {
                                    AutoLinkedText(
                                        split.quoted,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium.merge(
                                            ContentDirection,
                                        ).copy(color = colors.onSurfaceVariant),
                                        onOpen = { onIntent(AppIntent.OpenUrl(it)) },
                                    )
                                }
                            }
                        }
                    }

                    reader.failed -> Text(stringResource(Res.string.reader_body_failed), color = colors.error)

                    else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                if (account?.capabilities?.send == true) ReplyButtons(message, onIntent)
            }
        }
    }
}

/** Subject (with its translation in brackets), account and labels, sender, attachments and the translation bar. */
@Composable
private fun ReaderHeader(reader: Reader, account: Account?, labels: List<Folder>, working: Boolean, onIntent: (AppIntent) -> Unit) {
    val message = reader.message
    val colors = MaterialTheme.colorScheme
    val cards = cardStyle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectionContainer {
                val translation = reader.translation
                // Translated, the subject keeps its original and adds the translation in brackets.
                val subject = if (translation?.showing == true && translation.subject.isNotBlank()) {
                    buildAnnotatedString {
                        append(message.subject)
                        withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(" (${translation.subject})") }
                    }
                } else {
                    AnnotatedString(message.subject)
                }
                Text(
                    subject,
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
        // Images the HTML shows inline are part of the message, not attachments to list (as in Gmail).
        val inlineIds = remember(body) { body?.html?.let(::cidRefs).orEmpty() }
        val files = body?.attachments.orEmpty().withIndex().filter { it.value.contentId.isEmpty() || it.value.contentId !in inlineIds }
        if (body != null && files.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                files.forEach { (index, attachment) ->
                    AttachmentCard(attachment, enabled = !working) { onIntent(AppIntent.PreviewAttachment(message, index)) }
                }
                if (files.size > 1) {
                    TextButton(onClick = { onIntent(AppIntent.DownloadAttachments(message)) }, enabled = !working) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                        Text(stringResource(Res.string.reader_download_all), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (!cards) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
        if (body != null) TranslateBar(reader, onIntent)
    }
}

/** Remote images, styles or fonts: what a sender can use to see that the message was opened. */
private fun htmlLoadsRemote(html: String): Boolean =
    Regex("(?i)(<img[^>]+src\\s*=\\s*[\"']?https?:|url\\(\\s*['\"]?https?:|<link[^>]+href\\s*=\\s*[\"']?https?:)").containsMatchIn(html)

/** Gmail's three dots at the end of a message: fold or unfold the earlier messages it quotes. */
@Composable
private fun QuoteToggle(shown: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(18.dp))
        Text(stringResource(if (shown) Res.string.reader_hide_quoted else Res.string.reader_show_quoted), Modifier.padding(start = 6.dp))
    }
}

/** Remote images are held back (the setting): say so, and let this message load them. */
@Composable
private fun ImagesBar(onShow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.HideImage, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        Text(
            stringResource(Res.string.reader_images_hidden),
            Modifier.weight(1f).padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        TextButton(onClick = onShow) { Text(stringResource(Res.string.reader_show_images)) }
    }
}

@Composable
private fun ReplyButtons(message: MailMessage, onIntent: (AppIntent) -> Unit) {
    FlowRow(
        Modifier.padding(top = 4.dp, bottom = 12.dp),
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
            TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.reader_back), {
                onIntent(AppIntent.CloseReader)
            })
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
            TooltipIconButton(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(Res.string.reader_open_web), {
                onIntent(AppIntent.OpenInWeb(message))
            })
        }
        MoreActions(message, enabled = !working, onIntent)
    }
}

/**
 * Gmail's translation bar, between the sender and the body: an offer when the message looks like
 * another language, then progress, then what it was translated from with a way back to the
 * original. Hidden for a message in the reader's own language that nobody asked to translate.
 */
@Composable
private fun TranslateBar(reader: Reader, onIntent: (AppIntent) -> Unit) {
    val offer = LocalTranslationOffer.current
    val translation = reader.translation
    val body = reader.body ?: return
    val hebrew = offer.target == "he"
    if (translation == null && (!offer.enabled || !looksForeign(reader.message.subject + "\n" + body.text, offer.target))) return
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (translation?.state == TranslationState.Loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Translate, null, Modifier.size(20.dp), tint = colors.primary)
        }
        val target = languageName(offer.target, hebrew)
        val (text, action) = when {
            translation == null -> stringResource(Res.string.translate_offer) to
                (stringResource(Res.string.translate_action, target) to AppIntent.TranslateMessage)

            translation.state == TranslationState.Loading -> stringResource(Res.string.translate_loading) to null

            translation.state == TranslationState.Failed -> stringResource(Res.string.translate_failed) to
                (stringResource(Res.string.translate_retry) to AppIntent.TranslateMessage)

            translation.showOriginal -> stringResource(Res.string.translate_showing_original) to
                (stringResource(Res.string.translate_show_translation) to AppIntent.ShowOriginal(false))

            else -> stringResource(Res.string.translate_done, languageName(translation.sourceLanguage, hebrew)) to
                (stringResource(Res.string.translate_show_original) to AppIntent.ShowOriginal(true))
        }
        Text(
            text,
            Modifier.weight(1f).padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (action != null) TextButton(onClick = { onIntent(action.second) }) { Text(action.first) }
    }
}

/** Conversation-wide actions: every attachment in the thread, export, and translation on request. */
@Composable
private fun MoreActions(message: MailMessage, enabled: Boolean, onIntent: (AppIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(Icons.Outlined.MoreVert, stringResource(Res.string.reader_more), { open = true }, enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // For a language the bar cannot tell apart (French for an English reader), or with the offer off.
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.translate_menu)) },
                leadingIcon = { Icon(Icons.Outlined.Translate, null) },
                onClick = {
                    open = false
                    onIntent(AppIntent.TranslateMessage)
                },
            )
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
                to + dateLabel(message.receivedAt, withDate = true),
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
    Tooltip(stringResource(Res.string.reader_preview_one, attachment.name)) {
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
                Text(
                    extension,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                )
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
            Icon(Icons.Outlined.Visibility, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
    }
}
