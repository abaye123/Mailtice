package co.abaye.mailtice.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import co.abaye.mailtice.provider.HtmlText

/*
 * Most personal mail is HTML only in name: paragraphs, bold, links and the quoted earlier message.
 * That reads as well - and opens instantly, themed, with no browser engine started - as styled
 * text. Newsletters, receipts and anything laid out with tables, images or positioned boxes still
 * go to the webview, which is the only thing that draws them the way their sender built them.
 */

/** Blocks whose content is never shown. */
private val HiddenBlocks = Regex("(?is)<(head|style|script|title|xml)\\b.*?</\\1\\s*>|<!--.*?-->")

/** Tags a browser is needed for: layout, media, embedded documents, forms. */
private val LayoutTags = Regex(
    "(?i)<(table|svg|video|audio|iframe|object|embed|form|input|button|select|textarea|canvas|map|picture|frameset)\\b",
)
private val ImageTag = Regex("(?i)<img\\b[^>]*>")

/** A tracking pixel or hidden image: nothing a reader would miss. */
private val InvisibleImage =
    Regex("(?i)(width\\s*=\\s*[\"']?[01][\"'\\s>/]|height\\s*=\\s*[\"']?[01][\"'\\s>/]|display\\s*:\\s*none|width\\s*:\\s*[01]px)")

/** Inline styles that build a layout rather than dress text. */
private val LayoutStyle = Regex(
    "(?i)(position\\s*:\\s*(absolute|fixed)|float\\s*:|display\\s*:\\s*(grid|flex|inline-block)|background(-image)?\\s*:[^;\"']*url\\(|columns\\s*:)",
)
private val BackgroundAttribute = Regex("(?i)\\s(bgcolor|background)\\s*=")

private const val SIMPLE_LIMIT = 200_000

/**
 * How [html] is best shown. [Simple]: as themed text ([simpleHtmlText]). [Rich]: laid out with
 * tables, images and inline styles, which [co.abaye.mailtice.main.html.RichHtmlBody] draws natively.
 * [Web]: needs a browser - floats, flex or grid, positioned boxes, background images, forms, media.
 */
enum class HtmlTier { Simple, Rich, Web }

private val BrowserOnlyTags = Regex(
    "(?i)<(svg|video|audio|iframe|object|embed|form|input|button|select|textarea|canvas|map|frameset)\\b",
)
private val BrowserOnlyStyle = Regex(
    "(?i)(position\\s*:\\s*(absolute|fixed)|float\\s*:\\s*(left|right)|display\\s*:\\s*(grid|flex)|background(-image)?\\s*:[^;\"']*url\\(|\\sbackground\\s*=)",
)
private const val RICH_LIMIT = 400_000

fun htmlTier(html: String): HtmlTier {
    if (isSimpleHtml(html)) return HtmlTier.Simple
    if (html.length > RICH_LIMIT) return HtmlTier.Web
    val shown = html.replace(HiddenBlocks, "")
    if (BrowserOnlyTags.containsMatchIn(shown) || BrowserOnlyStyle.containsMatchIn(shown)) return HtmlTier.Web
    return HtmlTier.Rich
}

/** Whether [html] reads fine as styled text, without a browser: no tables, images, media or layout styles. */
fun isSimpleHtml(html: String): Boolean {
    if (html.length > SIMPLE_LIMIT) return false
    val shown = html.replace(HiddenBlocks, "")
    if (LayoutTags.containsMatchIn(shown)) return false
    if (ImageTag.findAll(shown).any { !InvisibleImage.containsMatchIn(it.value) }) return false
    if (LayoutStyle.containsMatchIn(shown) || BackgroundAttribute.containsMatchIn(shown)) return false
    return true
}

private val Token = Regex("(?s)<(/?)([a-zA-Z][a-zA-Z0-9:]*)([^>]*)>|<[^>]*>|([^<]+)")
private val Href = Regex("(?i)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val Whitespace = Regex("[ \\t\\r\\n\\u000C]+")
private val ExtraBlankLines = Regex("\n{3,}")

/** Named entities beyond the five [HtmlText.decodeEntities] knows, as code points. */
private val NamedEntities = mapOf(
    "rsquo" to 0x2019, "lsquo" to 0x2018, "rdquo" to 0x201D, "ldquo" to 0x201C, "hellip" to 0x2026,
    "mdash" to 0x2014, "ndash" to 0x2013, "bull" to 0x2022, "middot" to 0x00B7, "copy" to 0x00A9,
    "reg" to 0x00AE, "trade" to 0x2122, "euro" to 0x20AC, "shy" to 0x00AD, "zwnj" to 0x200C,
    "zwj" to 0x200D, "lrm" to 0x200E, "rlm" to 0x200F, "laquo" to 0x00AB, "raquo" to 0x00BB, "times" to 0x00D7,
)
private val NamedEntity = Regex("&([a-zA-Z]+);")

/** Text from HTML with its character references decoded: numeric, the basic five and the common named ones. */
internal fun decodeHtmlText(text: String): String = HtmlText.decodeEntities(
    text.replace(NamedEntity) { m -> NamedEntities[m.groupValues[1].lowercase()]?.let { Char(it).toString() } ?: m.value },
)

private val BlockTags = setOf(
    "p", "div", "section", "article", "header", "footer", "main", "aside", "nav", "blockquote", "pre", "ul", "ol", "li",
    "h1", "h2", "h3", "h4", "h5", "h6", "hr", "address", "center", "dl", "dt", "dd", "figure", "figcaption",
)

/**
 * [html] (judged simple by [isSimpleHtml], though anything is accepted) as styled text: block
 * elements break lines, lists get bullets or numbers, bold, italic, underline, strike-through,
 * code and headings keep their look, quotes dim, and links call [onOpen] (http, https and mailto
 * only). The sender's colours, fonts and sizes are left out, so it follows the app's theme.
 */
fun simpleHtmlText(html: String, linkColor: Color, quoteColor: Color, onOpen: (String) -> Unit): AnnotatedString {
    val source = html.replace(HiddenBlocks, "")
    val out = AnnotatedString.Builder()
    val open = ArrayDeque<Pair<String, Int?>>()
    val lists = ArrayDeque<Int>() // 0 = bulleted, n > 0 = the next number of an ordered list
    var pre = 0
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))

    // The last character written, tracked here: reading it back from the builder would copy it each time.
    var last = '\n'
    fun put(text: String) {
        if (text.isEmpty()) return
        out.append(text)
        last = text.last()
    }
    fun lineBreak() {
        if (last != '\n') put("\n")
    }
    fun text(raw: String) {
        val decoded = decodeHtmlText(raw)
        if (pre > 0) {
            put(decoded)
            return
        }
        val t = decoded.replace(Whitespace, " ")
        put(if (last == '\n' || last == ' ') t.trimStart() else t)
    }
    fun style(tag: String, attrs: String): Int? = when (tag) {
        "b", "strong" -> out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))

        "i", "em", "cite" -> out.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))

        "u", "ins" -> out.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))

        "s", "strike", "del" -> out.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))

        "code", "tt", "kbd", "samp", "pre" -> out.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))

        "h1" -> out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.5.em))

        "h2" -> out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.3.em))

        "h3", "h4", "h5", "h6" -> out.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.1.em))

        "blockquote" -> out.pushStyle(SpanStyle(color = quoteColor))

        "a" -> {
            val m = Href.find(attrs)
            val href = m?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let(::decodeHtmlText)?.trim()
            val safe = href?.takeIf { h -> listOf("http://", "https://", "mailto:").any { h.startsWith(it, ignoreCase = true) } }
            safe?.let { url -> out.pushLink(LinkAnnotation.Clickable(url, linkStyles) { onOpen(url) }) }
        }

        else -> null
    }

    for (m in Token.findAll(source)) {
        val content = m.groups[4]?.value
        if (content != null) {
            text(content)
            continue
        }
        val name = m.groupValues[2].lowercase().ifEmpty { continue }
        val closing = m.groupValues[1] == "/"
        val attrs = m.groupValues[3]
        if (!closing) {
            when (name) {
                "br" -> put("\n")

                "hr" -> {
                    lineBreak()
                    put("\u2500\u2500\u2500\n")
                }

                else -> {
                    if (name in BlockTags) lineBreak()
                    when (name) {
                        "ul" -> lists.addLast(0)

                        "ol" -> lists.addLast(1)

                        "li" -> {
                            val depth = (lists.size - 1).coerceAtLeast(0)
                            val current = lists.removeLastOrNull() ?: 0
                            val marker = if (current == 0) "\u2022 " else "$current. "
                            lists.addLast(if (current == 0) 0 else current + 1)
                            put("    ".repeat(depth) + marker)
                        }

                        "pre" -> pre++
                    }
                    // Void and self-closed tags open nothing to close later.
                    if (!attrs.trimEnd().endsWith("/")) open.addLast(name to style(name, attrs))
                }
            }
        } else {
            // Close up to the matching tag; stray closers (common in mail) are ignored.
            val at = open.indexOfLast { it.first == name }
            if (at < 0) continue
            while (open.size > at) {
                val (tag, pushed) = open.removeLast()
                pushed?.let { out.pop(it) }
                when (tag) {
                    "ul", "ol" -> lists.removeLastOrNull()
                    "pre" -> pre--
                }
                if (tag in BlockTags) lineBreak()
            }
        }
    }
    val built = out.toAnnotatedString()
    // Trailing breaks and runs of empty lines are the sender's spacing, not content.
    val trimmedEnd = built.text.trimEnd().length
    val shortened = built.subSequence(0, trimmedEnd)
    return if (ExtraBlankLines.containsMatchIn(shortened.text)) collapseBlankLines(shortened) else shortened
}

/** [text] with every run of three or more line breaks cut to two, keeping the styles in place. */
private fun collapseBlankLines(text: AnnotatedString): AnnotatedString {
    val out = AnnotatedString.Builder()
    var cursor = 0
    ExtraBlankLines.findAll(text.text).forEach { run ->
        out.append(text.subSequence(cursor, run.range.first))
        out.append("\n\n")
        cursor = run.range.last + 1
    }
    out.append(text.subSequence(cursor, text.length))
    return out.toAnnotatedString()
}
