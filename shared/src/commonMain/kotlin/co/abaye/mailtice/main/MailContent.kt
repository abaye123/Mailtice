package co.abaye.mailtice.main

/**
 * What a message body is made of for the reader: the new text, and the earlier messages it quotes,
 * which stay folded until asked for (and are not sent to translation).
 */
data class SplitBody(val main: String, val quoted: String) {
    val hasQuote: Boolean get() = quoted.isNotBlank()
}

private val BidiMarks = Regex("[‎‏‪-‮⁦-⁩]")

/** The line that opens a quoted reply, in the forms Gmail, Apple Mail, Outlook and Thunderbird write. */
private val QuoteHeaders = listOf(
    Regex("""^On .{3,300} wrote:$""", RegexOption.IGNORE_CASE),
    Regex("""^Le .{3,300} a écrit\s?:$""", RegexOption.IGNORE_CASE),
    Regex("""^Am .{3,300} schrieb .{1,200}:$""", RegexOption.IGNORE_CASE),
    // Gmail in Hebrew: "בתאריך יום ה׳, 25 בספט׳ 2026 ב-10:00 מאת דנה <d@x.com>:"
    Regex("""^בתאריך .{3,300} מאת .{1,200}:$"""),
    Regex("""^ב[-־]?.{3,300} (כתב|כתבה|כתב/ה)\s?:$"""),
    Regex("""^-{2,}\s*(Original Message|הודעה מקורית)\s*-{2,}$""", RegexOption.IGNORE_CASE),
)

/** Outlook's reply header: "From:" followed closely by "Sent:" or "Date:". */
private val OutlookFrom = Regex("""^(From|מאת)\s?:\s.+""", RegexOption.IGNORE_CASE)
private val OutlookNext = Regex("""^(Sent|Date|To|Subject|נשלח|תאריך|אל|נושא)\s?:\s.*""", RegexOption.IGNORE_CASE)

/** A forward is the message itself, not a quote to fold away. */
private val ForwardMarkers = listOf("Forwarded message", "Begin forwarded message", "הודעה שהועברה", "הודעה שהועברה ---", "Message transféré")

fun isForward(text: String): Boolean = ForwardMarkers.any { text.contains(it, ignoreCase = true) }

/**
 * Splits a plain-text body where the quoted earlier messages begin: at a reply header ("On ...
 * wrote:" and its translations, Outlook's "From: / Sent:" block, "Original Message") or where a
 * run of "> " lines takes over to the end. A forward, or a body that is nothing but quote, stays
 * whole.
 */
fun splitQuote(text: String): SplitBody {
    if (isForward(text)) return SplitBody(text, "")
    val lines = text.split('\n')
    val clean = lines.map { it.replace(BidiMarks, "").trim() }
    fun header(i: Int): Boolean {
        val line = clean[i]
        if (QuoteHeaders.any { it.matches(line) }) return true
        if (OutlookFrom.matches(line)) return (i + 1..minOf(i + 4, clean.lastIndex)).any { OutlookNext.matches(clean[it]) }
        if (line.matches(Regex("^_{10,}$"))) return i < clean.lastIndex && OutlookFrom.matches(clean.getOrElse(i + 1) { "" })
        return false
    }
    for (i in lines.indices) {
        // Gmail wraps a long "On ... wrote:" over two lines.
        val start = when {
            header(i) -> i
            i > 0 && QuoteHeaders.any { it.matches(clean[i - 1] + " " + clean[i]) } -> i - 1
            clean[i].startsWith(">") && clean.drop(i).all { it.isEmpty() || it.startsWith(">") } -> i
            else -> continue
        }
        val main = lines.take(start).joinToString("\n").trimEnd()
        if (main.isBlank()) return SplitBody(text, "")
        return SplitBody(main, lines.drop(start).joinToString("\n"))
    }
    return SplitBody(text, "")
}

/** Where mail clients put the quoted earlier messages in HTML. */
private val HtmlQuoteSelectors = listOf(
    ".gmail_quote",
    ".gmail_quote_container",
    "blockquote[type=\"cite\"]",
    ".yahoo_quoted",
    ".moz-cite-prefix",
    ".moz-cite-prefix ~ blockquote",
    "#appendonsend ~ *",
    "#divRplyFwdMsg",
    "#divRplyFwdMsg ~ *",
    "div[style*=\"border-top:solid #E1E1E1\"]",
    "div[style*=\"border-top:solid #E1E1E1\"] ~ *",
)

private val HtmlQuoteMarkers = listOf(
    "gmail_quote", "type=\"cite\"", "type=cite", "yahoo_quoted", "moz-cite-prefix", "appendonsend", "divRplyFwdMsg", "border-top:solid #E1E1E1",
)

/** Whether [html] has a quoted earlier message the reader can fold away. */
fun htmlHasQuote(html: String): Boolean = htmlQuoteStart(html) != null

/** Where the quoted earlier messages begin in [html]: the start of the tag that marks them; null if none (or a forward). */
fun htmlQuoteStart(html: String): Int? {
    if (isForward(html)) return null
    val marker = HtmlQuoteMarkers.mapNotNull { m -> html.indexOf(m, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: return null
    return html.lastIndexOf('<', marker).takeIf { it >= 0 } ?: marker
}

private val CidRef = Regex("(?i)cid:([^\"'\\s)>]+)")

/** The Content-IDs [html] shows inline ("cid:..." references), unescaped. */
fun cidRefs(html: String): Set<String> = CidRef.findAll(html).map { unescapeCid(it.groupValues[1]) }.toSet()

private fun unescapeCid(raw: String): String = raw.replace("%40", "@").replace("%2E", ".", ignoreCase = true).replace("&amp;", "&")

/** The target attribute of a link or image-map area, whatever its quoting. */
private val LinkTarget = Regex("(?i)(<(?:a|area)\\b[^>]*?)\\s+target\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)")

private const val BASE_CSS = """
html, body { margin: 0; padding: 0; background: #ffffff; }
body { padding: 4px 2px 16px; color: #202124; font: 15px/1.5 "Segoe UI", system-ui, -apple-system, Roboto, Arial, sans-serif;
  overflow-wrap: anywhere; word-wrap: break-word; }
img { max-width: 100%; height: auto; }
table { max-width: 100%; }
pre { white-space: pre-wrap; }
a { color: #1a73e8; }
"""

/**
 * [html] as a page the reader's webview can show safely: scripts, frames and forms never run
 * (a content policy on top of stripping them), remote images, styles and fonts load only when
 * [remoteImages] (they can track opens), links navigate the page itself so the reader can send
 * them to the browser instead, and quoted earlier messages are hidden when [hideQuotes].
 */
fun emailDocument(html: String, hideQuotes: Boolean, remoteImages: Boolean, inlineImages: Map<String, String> = emptyMap()): String {
    val remote = if (remoteImages) " https: http:" else ""
    val policy = "default-src 'none'; img-src data: cid:$remote; style-src 'unsafe-inline'$remote; font-src data:$remote; " +
        "media-src 'none'; script-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'"
    val quoteCss = if (hideQuotes) HtmlQuoteSelectors.joinToString(", ") + " { display: none !important; }" else ""
    val head = "<meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"$policy\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><base target=\"_self\">" +
        "<style>$BASE_CSS$quoteCss</style>"
    val withImages = if (inlineImages.isEmpty()) html else html.replace(CidRef) { m -> inlineImages[unescapeCid(m.groupValues[1])] ?: m.value }
    val clean = withImages
        .replace(Regex("(?is)<script\\b.*?</script\\s*>"), "")
        .replace(Regex("(?is)<(iframe|object|embed)\\b.*?(</\\1\\s*>|/?>)"), "")
        // A link with target="_blank" asks WebView2 for a new window, which bypasses the navigation
        // check that hands links to the browser; without a target every click is a navigation.
        .replace(LinkTarget, "$1")
    val headTag = Regex("(?i)<head\\b[^>]*>").find(clean)
    if (headTag != null) return clean.replaceRange(headTag.range.last + 1, headTag.range.last + 1, head)
    val htmlTag = Regex("(?i)<html\\b[^>]*>").find(clean)
    if (htmlTag != null) return clean.replaceRange(htmlTag.range.last + 1, htmlTag.range.last + 1, "<head>$head</head>")
    return "<!DOCTYPE html><html><head>$head</head><body><div dir=\"auto\">$clean</div></body></html>"
}
