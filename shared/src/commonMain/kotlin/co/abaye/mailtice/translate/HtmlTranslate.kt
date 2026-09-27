package co.abaye.mailtice.translate

import co.abaye.mailtice.main.htmlQuoteStart
import co.abaye.mailtice.provider.HtmlText

/** One run of visible text in an HTML document: where it sits, and its text on one line. */
data class HtmlSegment(val start: Int, val end: Int, val text: String)

// Markup to step over: comments, blocks whose text is never shown, and every tag.
private val Markup = Regex("(?is)<!--.*?-->|<(style|script|head|title)\\b.*?</\\1\\s*>|<[^>]+>")
private val Space = Regex("\\s+")

/**
 * The visible text runs of [html] worth translating: between tags, outside style/script/head,
 * with letters in them, and before the quoted earlier messages (those stay as they were, and are
 * not sent). Each is decoded and folded onto one line, so a whole document can go out as lines.
 */
fun htmlTextSegments(html: String): List<HtmlSegment> {
    val stop = htmlQuoteStart(html) ?: html.length
    val out = mutableListOf<HtmlSegment>()
    var cursor = 0
    fun text(from: Int, to: Int) {
        if (from >= to || from >= stop) return
        val end = minOf(to, stop)
        val decoded = HtmlText.decodeEntities(html.substring(from, end)).replace(Space, " ").trim()
        if (decoded.any { it.isLetter() }) out += HtmlSegment(from, end, decoded)
    }
    for (m in Markup.findAll(html)) {
        text(cursor, m.range.first)
        cursor = m.range.last + 1
    }
    text(cursor, html.length)
    return out
}

/** [html] with each of [segments] replaced by its line of [translations], escaped, keeping the spaces around it. */
fun replaceSegments(html: String, segments: List<HtmlSegment>, translations: List<String>): String {
    val sb = StringBuilder(html.length + 256)
    var cursor = 0
    segments.forEachIndexed { i, seg ->
        sb.append(html, cursor, seg.start)
        val original = html.substring(seg.start, seg.end)
        val lead = original.takeWhile { it.isWhitespace() }
        val trail = original.takeLastWhile { it.isWhitespace() }
        sb.append(lead).append(escapeHtml(translations[i].trim())).append(trail)
        cursor = seg.end
    }
    sb.append(html, cursor, html.length)
    return sb.toString()
}

private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
