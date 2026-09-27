package co.abaye.mailtice.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val UrlPattern = Regex("""https?://[^\s<>"\]\[)]+""")

/** Plain mail text with every http(s) address clickable; [onOpen] decides what opening means. */
@Composable
fun AutoLinkedText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, onOpen: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            var cursor = 0
            UrlPattern.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                val url = match.value.trimEnd('.', ',', ';', ':')
                withLink(LinkAnnotation.Clickable(url, styles) { onOpen(url) }) { append(url) }
                append(match.value.substring(url.length))
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    Text(annotated, modifier, style = style)
}

/**
 * 1536 -> "1.5 KB". Binary units, one decimal from KB up. Wrapped in a left-to-right isolate so a
 * Hebrew line shows "5.0 MB", not "MB 5.0".
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "⁦$bytes B⁩"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "⁦$rounded ${units[unit]}⁩"
}
