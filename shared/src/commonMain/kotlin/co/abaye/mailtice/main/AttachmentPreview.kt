package co.abaye.mailtice.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AttachmentPreview
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.preview_close
import mailtice.shared.generated.resources.preview_download
import mailtice.shared.generated.resources.preview_failed
import mailtice.shared.generated.resources.preview_open_external
import mailtice.shared.generated.resources.preview_unsupported
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/** What the preview can draw for a file, from its type and name. */
enum class PreviewKind { Image, Text, Html, Pdf, Audio, Video, None }

private val ImageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val TextExt = setOf(
    "txt", "csv", "tsv", "log", "md", "json", "xml", "yaml", "yml", "ini", "cfg", "conf", "ics", "vcf",
    "kt", "java", "py", "js", "ts", "css", "sh", "bat", "ps1", "sql", "c", "cpp", "h", "cs", "go", "rs", "swift",
)
private val AudioExt = setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus")
private val VideoExt = setOf("mp4", "webm", "mov", "m4v", "ogv")

/**
 * Images and text are drawn by the app itself on every platform; HTML goes through the same locked
 * down webview as mail. PDF, audio and video lean on the desktop browser engine (WebView2 has a
 * PDF viewer and plays media), so on Android they fall back to "no preview".
 */
fun previewKindOf(name: String, mimeType: String): PreviewKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = mimeType.lowercase()
    return when {
        ext in ImageExt || (mime.startsWith("image/") && mime != "image/svg+xml") -> PreviewKind.Image
        ext == "html" || ext == "htm" || mime == "text/html" -> PreviewKind.Html
        ext in TextExt || mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> PreviewKind.Text
        !Platform.isDesktop -> PreviewKind.None
        ext == "pdf" || mime == "application/pdf" -> PreviewKind.Pdf
        ext in AudioExt || mime.startsWith("audio/") -> PreviewKind.Audio
        ext in VideoExt || mime.startsWith("video/") -> PreviewKind.Video
        else -> PreviewKind.None
    }
}

/**
 * A file opened from a message, over everything on a dark see-through layer, like Gmail's viewer:
 * its name, a download button and "open with" along the top, the file itself in the middle, or a
 * plain card when there is no way to show it. A click on the dark area or Escape closes it.
 */
@Composable
fun AttachmentPreviewOverlay(preview: AttachmentPreview, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onIntent(AppIntent.ClosePreview) }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    onIntent(AppIntent.ClosePreview)
                    true
                } else {
                    false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PreviewTopBar(preview, onIntent)
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                PreviewContent(preview, onIntent)
            }
        }
    }
}

@Composable
private fun PreviewTopBar(preview: AttachmentPreview, onIntent: (AppIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.preview_close), {
            onIntent(AppIntent.ClosePreview)
        }, tint = Color.White)
        Column(Modifier.weight(1f)) {
            Text(
                preview.attachment.name,
                style = MaterialTheme.typography.titleMedium.merge(ContentDirection),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatBytes(preview.attachment.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
        val ready = preview.bytes != null
        if (Platform.canOpenFiles) {
            TooltipIconButton(
                Icons.AutoMirrored.Outlined.OpenInNew,
                stringResource(Res.string.preview_open_external),
                { onIntent(AppIntent.OpenPreviewExternally) },
                enabled = ready,
                tint = Color.White,
            )
        }
        Button(onClick = { onIntent(AppIntent.SavePreview) }, enabled = ready) {
            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.preview_download), Modifier.padding(start = 8.dp))
        }
    }
}

/** The middle of the viewer; clicks inside it do not reach the dark layer that closes it. */
@Composable
private fun PreviewContent(preview: AttachmentPreview, onIntent: (AppIntent) -> Unit) {
    val bytes = preview.bytes
    val swallow = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    when {
        preview.failed -> NoPreview(preview, stringResource(Res.string.preview_failed), onIntent, swallow)

        bytes == null -> CircularProgressIndicator(color = Color.White)

        else -> when (previewKindOf(preview.attachment.name, preview.attachment.mimeType)) {
            PreviewKind.Image -> {
                val image = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                if (image != null) {
                    Image(image, preview.attachment.name, swallow.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    NoPreview(preview, stringResource(Res.string.preview_unsupported), onIntent, swallow)
                }
            }

            PreviewKind.Text -> {
                val text = remember(bytes) { bytes.copyOf(minOf(bytes.size, MAX_TEXT_BYTES)).decodeToString() }
                Surface(swallow.fillMaxSize().widthIn(max = 1000.dp), shape = RoundedCornerShape(12.dp)) {
                    SelectionContainer {
                        Text(
                            text,
                            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium.merge(ContentDirection).copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            PreviewKind.Html -> {
                val document = remember(bytes) { emailDocument(bytes.decodeToString(), hideQuotes = false, remoteImages = false) }
                HtmlBody(document, onOpenUrl = { onIntent(AppIntent.OpenUrl(it)) }, modifier = swallow.fillMaxSize())
            }

            PreviewKind.Pdf, PreviewKind.Audio, PreviewKind.Video ->
                if (preview.pageUrl.isNotEmpty()) {
                    FileBody(
                        preview.pageUrl,
                        swallow.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }

            PreviewKind.None -> NoPreview(preview, stringResource(Res.string.preview_unsupported), onIntent, swallow)
        }
    }
}

private const val MAX_TEXT_BYTES = 1_000_000

@Composable
private fun NoPreview(preview: AttachmentPreview, message: String, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 420.dp), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                preview.attachment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Platform.canOpenFiles && preview.bytes != null) {
                    FilledTonalButton(onClick = {
                        onIntent(AppIntent.OpenPreviewExternally)
                    }) { Text(stringResource(Res.string.preview_open_external)) }
                }
                Button(onClick = { onIntent(AppIntent.SavePreview) }, enabled = preview.bytes != null) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                    Text(stringResource(Res.string.preview_download), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The page a PDF, audio or video file is shown in: the PDF itself, or a player around the media. */
fun previewPage(kind: PreviewKind, fileName: String): String? = when (kind) {
    PreviewKind.Audio ->
        playerPage("#202124", "<audio controls autoplay style=\"width:min(640px,90vw)\" src=\"${fileName.urlEncodedPath()}\"></audio>")

    PreviewKind.Video ->
        playerPage(
            "#000",
            "<video controls autoplay style=\"max-width:100%;max-height:100vh\" src=\"${fileName.urlEncodedPath()}\"></video>",
        )

    else -> null
}

/** A page that centres [player] on a [background] filling the window. */
private fun playerPage(background: String, player: String): String =
    "<!DOCTYPE html><html><body style=\"margin:0;height:100vh;display:flex;align-items:center;" +
        "justify-content:center;background:$background\">$player</body></html>"

private fun String.urlEncodedPath(): String = buildString {
    for (c in this@urlEncodedPath) {
        if ((c.isLetterOrDigit() && c.code < 128) || c in "-_.~") {
            append(c)
        } else {
            c.toString().encodeToByteArray().forEach { b ->
                append(
                    '%',
                ).append(((b.toInt() and 0xFF) shr 4).toString(16).uppercase()).append((b.toInt() and 0x0F).toString(16).uppercase())
            }
        }
    }
}
