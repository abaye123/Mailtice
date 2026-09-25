package co.abaye.mailtice.provider

/**
 * HTML mail to readable plain text: block tags become line breaks, links keep their target in
 * brackets, scripts/styles/images disappear. Deliberately small - the MVP reader shows text, and
 * never loads remote content (so tracking pixels stay dead).
 */
object HtmlText {
    private val dropBlocks = Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>")
    private val links = Regex("(?is)<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>")
    private val breaks = Regex("(?i)<(br|/p|/div|/tr|/li|/h[1-6])\\s*/?>")
    private val listItem = Regex("(?i)<li[^>]*>")
    private val tags = Regex("<[^>]+>")
    private val blankLines = Regex("\n{3,}")
    private val spaces = Regex("[ \\t\\u00A0]+")

    fun toText(html: String): String {
        var s = html.replace(dropBlocks, "")
        s = s.replace(links) { m ->
            val text = m.groupValues[2].replace(tags, "").trim()
            val href = m.groupValues[1]
            if (text.isEmpty() || text == href || href.startsWith("mailto:")) text.ifEmpty { href } else "$text [$href]"
        }
        s = s.replace(breaks, "\n").replace(listItem, "\n• ").replace(tags, "")
        s = decodeEntities(s)
        return s.lines().joinToString("\n") { it.replace(spaces, " ").trim() }.replace(blankLines, "\n\n").trim()
    }

    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    fun decodeEntities(text: String): String = text
        .replace(numericEntity) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else 16
            m.groupValues[2].toIntOrNull(radix)?.takeIf { it in 1..0xFFFF }?.let { Char(it).toString() } ?: m.value
        }
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    /** First ~200 characters of readable text, for the list row. */
    fun snippet(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(200)
}
