package co.abaye.mailtice.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.ReplyAll
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Web
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import co.abaye.mailtice.domain.HtmlView
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.translate.LocalTranslationOffer
import co.abaye.mailtice.translate.languageName
import co.abaye.mailtice.translate.looksForeign
import co.abaye.mailtice.ui.AutoLinkedText
import co.abaye.mailtice.ui.Tooltip
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import kotlinx.coroutines.delay
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.inbox_archive
import mailtice.shared.generated.resources.inbox_mark_unread
import mailtice.shared.generated.resources.inbox_trash
import mailtice.shared.generated.resources.reader_back
import mailtice.shared.generated.resources.reader_body_failed
import mailtice.shared.generated.resources.reader_close
import mailtice.shared.generated.resources.reader_download_all
import mailtice.shared.generated.resources.reader_download_one
import mailtice.shared.generated.resources.reader_download_thread
import mailtice.shared.generated.resources.reader_export_html
import mailtice.shared.generated.resources.reader_export_mail
import mailtice.shared.generated.resources.reader_forward
import mailtice.shared.generated.resources.reader_full_view
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
import mailtice.shared.generated.resources.reader_simple_view
import mailtice.shared.generated.resources.reader_to
import mailtice.shared.generated.resources.thread_me
import mailtice.shared.generated.resources.thread_more
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
    showClose: Boolean = false,
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
    // Simple HTML reads as styled text; the webview is for mail laid out with tables and images,
    // or when asked for ("full view"), or always (the setting).
    var fullView by remember(message.key) { mutableStateOf(false) }
    val simple = remember(html) { html != null && isSimpleHtml(html) }
    val wantsWeb = html != null && when (prefs.htmlView) {
        HtmlView.Full -> true
        HtmlView.Simple -> fullView
        HtmlView.Auto -> !simple || fullView
    }
    // While the next message loads, the layout stays as it was: leaving the webview for a moment and
    // coming back would start the browser engine again for every message opened.
    var lastWeb by remember { mutableStateOf(false) }
    val loading = body == null && !reader.failed
    val useWeb = if (loading) lastWeb else wantsWeb
    SideEffect { if (!loading) lastWeb = wantsWeb }
    val padding = Modifier.padding(horizontal = if (cards) 32.dp else 24.dp)
    // A conversation: the messages before the open one fold above it, the later ones below.
    val index = reader.thread.indexOfFirst { it.key == message.key }
    val older = if (reader.isConversation && index >= 0) reader.thread.take(index) else emptyList()
    val newer = if (reader.isConversation && index >= 0) reader.thread.drop(index + 1) else emptyList()
    Column(modifier.fillMaxSize()) {
        ReaderActions(reader, account, onIntent, showBack, working, showClose)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (!cards) HorizontalDivider(color = colors.outlineVariant)
        if (useWeb) {
            Column(padding.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderHeader(reader, account, labels, working, onIntent, older)
                // Loading: an empty page keeps the webview alive until the body arrives.
                val page = html.orEmpty()
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val remote = remember(page) { htmlLoadsRemote(page) }
                if (remote && !showImages) ImagesBar { showImages = true }
                val quoted = remember(page) { htmlHasQuote(page) }
                val inline = reader.inlineImages
                val document = remember(page, showQuoted, showImages, inline) {
                    emailDocument(page, hideQuotes = quoted && !showQuoted, remoteImages = showImages, inlineImages = inline)
                }
                // As tall as the message, so what follows it (the rest of the conversation, the reply
                // buttons) comes right after a short one; a long one fills the pane and scrolls inside.
                // Until measured it is short; if it cannot be measured, it fills the pane as before.
                var measured by remember(document) { mutableStateOf<Int?>(null) }
                var unmeasurable by remember(document) { mutableStateOf(false) }
                LaunchedEffect(document) {
                    delay(MEASURE_TIMEOUT_MS)
                    if (measured == null) unmeasurable = true
                }
                val height by animateDpAsState((measured ?: PENDING_HEIGHT).dp, label = "page height")
                val pageModifier = if (unmeasurable && measured == null) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier.weight(1f, fill = false).fillMaxWidth().height(height)
                }
                // A native view draws above Compose: while the viewer is open over it, the page steps aside.
                if (webPaused) {
                    Box(pageModifier)
                } else {
                    HtmlBody(
                        document,
                        onOpenUrl = { onIntent(AppIntent.OpenUrl(it)) },
                        modifier = pageModifier,
                        onContentHeight = { measured = it },
                    )
                }
                if (newer.isNotEmpty()) FoldedMessages(newer, account, onIntent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (account?.capabilities?.send == true) ReplyButtons(message, onIntent)
                    Spacer(Modifier.weight(1f))
                    if (quoted) QuoteToggle(showQuoted) { showQuoted = !showQuoted }
                    // Opened in full on request: the way back to the quick view.
                    if (simple && fullView) ViewToggle(full = true) { fullView = false }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(padding).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ReaderHeader(reader, account, labels, working, onIntent, older)
                when {
                    body != null && html != null -> SimpleHtmlBody(html, showQuoted, {
                        showQuoted = !showQuoted
                    }, { fullView = true }, onIntent)

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
                if (newer.isNotEmpty()) FoldedMessages(newer, account, onIntent)
                if (account?.capabilities?.send == true) ReplyButtons(message, onIntent)
            }
        }
    }
}

/** Subject (with its translation in brackets), account and labels, sender, attachments and the translation bar. */
@Composable
private fun ReaderHeader(
    reader: Reader,
    account: Account?,
    labels: List<Folder>,
    working: Boolean,
    onIntent: (AppIntent) -> Unit,
    older: List<MailMessage> = emptyList(),
) {
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
        if (older.isNotEmpty()) FoldedMessages(older, account, onIntent)
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

private const val MEASURE_TIMEOUT_MS = 1_500L
private const val PENDING_HEIGHT = 120

/**
 * Messages of the conversation other than the open one, one line each: who, the start of the text and
 * when; a click opens that one instead. Four or more fold further, as in Gmail: the first, a count of
 * the ones between, and the last.
 */
@Composable
private fun FoldedMessages(messages: List<MailMessage>, account: Account?, onIntent: (AppIntent) -> Unit) {
    var unfolded by remember(messages.firstOrNull()?.key, messages.size) { mutableStateOf(false) }
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (messages.size < 4 || unfolded) {
            messages.forEach { FoldedMessage(it, account, onIntent) }
        } else {
            FoldedMessage(messages.first(), account, onIntent)
            TextButton(onClick = { unfolded = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(18.dp))
                Text(stringResource(Res.string.thread_more, messages.size - 2), Modifier.padding(start = 6.dp))
            }
            FoldedMessage(messages.last(), account, onIntent)
        }
    }
}

@Composable
private fun FoldedMessage(message: MailMessage, account: Account?, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val own = account != null && message.fromAddress.equals(account.email, ignoreCase = true)
    val weight = if (message.unread) FontWeight.SemiBold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLow)
            .clickable { onIntent(AppIntent.ExpandInThread(message)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LetterAvatar(message.sender, account?.color?.color ?: colors.primary, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (own) stringResource(Res.string.thread_me) else message.sender,
                style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                fontWeight = if (message.unread) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                message.snippet,
                style = MaterialTheme.typography.bodySmall.merge(ContentDirection),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            dateLabel(message.receivedAt),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = weight,
            color = if (message.unread) colors.primary else colors.onSurfaceVariant,
        )
    }
}

/** Remote images, styles or fonts: what a sender can use to see that the message was opened. */
private fun htmlLoadsRemote(html: String): Boolean =
    Regex("(?i)(<img[^>]+src\\s*=\\s*[\"']?https?:|url\\(\\s*['\"]?https?:|<link[^>]+href\\s*=\\s*[\"']?https?:)").containsMatchIn(html)

/**
 * HTML mail shown as styled text ([simpleHtmlText]): the message itself, its quoted earlier
 * messages folded behind the toggle, and a way to open it in the webview as its sender built it.
 */
@Composable
private fun SimpleHtmlBody(
    html: String,
    showQuoted: Boolean,
    onToggleQuote: () -> Unit,
    onFullView: () -> Unit,
    onIntent: (AppIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val open: (String) -> Unit = { onIntent(AppIntent.OpenUrl(it)) }
    val (main, quoted) = remember(html) {
        val at = htmlQuoteStart(html)
        if (at == null || at == 0) html to "" else html.substring(0, at) to html.substring(at)
    }
    val linkColor = colors.primary
    val dim = colors.onSurfaceVariant
    val mainText = remember(main, linkColor, dim) { simpleHtmlText(main, linkColor, dim, open) }
    SelectionContainer {
        Text(mainText, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge.merge(ContentDirection))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (quoted.isNotEmpty()) QuoteToggle(showQuoted, onToggleQuote)
        Spacer(Modifier.weight(1f))
        ViewToggle(full = false, onClick = onFullView)
    }
    if (quoted.isNotEmpty() && showQuoted) {
        val quotedText = remember(quoted, linkColor, dim) { simpleHtmlText(quoted, linkColor, dim, open) }
        SelectionContainer {
            Text(
                quotedText,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium.merge(ContentDirection).copy(color = dim),
            )
        }
    }
}

/** Switches a message between the quick text view and the full (webview) one. */
@Composable
private fun ViewToggle(full: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(if (full) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.Web, null, Modifier.size(18.dp))
        Text(stringResource(if (full) Res.string.reader_simple_view else Res.string.reader_full_view), Modifier.padding(start = 6.dp))
    }
}

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
private fun ReaderActions(
    reader: Reader,
    account: Account?,
    onIntent: (AppIntent) -> Unit,
    showBack: Boolean,
    working: Boolean,
    showClose: Boolean = false,
) {
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
        } else if (showClose) {
            // Beside the list: the pane goes back to its empty state.
            TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.reader_close), { onIntent(AppIntent.CloseReader) })
        }
        // An open conversation is archived or deleted whole.
        val conversation = reader.isConversation
        if (caps?.archive == true) {
            TooltipIconButton(Icons.Outlined.Archive, stringResource(Res.string.inbox_archive), {
                onIntent(if (conversation) AppIntent.ArchiveConversation(reader.thread) else AppIntent.Archive(message))
            })
        }
        if (caps?.trash == true) {
            TooltipIconButton(Icons.Outlined.Delete, stringResource(Res.string.inbox_trash), {
                onIntent(if (conversation) AppIntent.TrashConversation(reader.thread) else AppIntent.Trash(message))
            })
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
    val hebrew = offer.target == "he" || offer.target == "yi"
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
