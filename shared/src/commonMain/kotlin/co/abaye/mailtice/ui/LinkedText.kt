package co.abaye.mailtice.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * A sentence with some of its words turned into links.
 *
 * Takes the finished, already-localised sentence and a label → URL map, so translators keep whole
 * sentences to work with instead of fragments glued together at runtime. Each label is matched
 * once, left to right; a label the translation dropped simply yields no link rather than throwing.
 */
@Composable
fun LinkedText(
    text: String,
    links: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val annotated = buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val next = links
                .mapNotNull { (label, url) ->
                    val index = if (label.isEmpty()) -1 else text.indexOf(label, cursor)
                    if (index < 0) null else Triple(index, label, url)
                }
                .minByOrNull { it.first }
            if (next == null) {
                append(text.substring(cursor))
                break
            }
            val (index, label, url) = next
            append(text.substring(cursor, index))
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(label) }
            cursor = index + label.length
        }
    }
    Text(annotated, modifier, color = color, style = style)
}
