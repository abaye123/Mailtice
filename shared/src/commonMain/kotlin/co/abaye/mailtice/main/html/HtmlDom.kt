package co.abaye.mailtice.main.html

/** A node of a parsed mail document. */
sealed interface HtmlNode

class HtmlTextNode(val text: String) : HtmlNode

class HtmlElement(val tag: String, val attrs: Map<String, String>, val children: MutableList<HtmlNode> = mutableListOf()) : HtmlNode {
    fun attr(name: String): String? = attrs[name]

    /** The element children only, looking through the row groups a table may or may not have. */
    fun elements(): List<HtmlElement> = children.filterIsInstance<HtmlElement>()
}

private val HiddenBlocks = Regex("(?is)<(head|style|script|title|xml|noscript)\\b.*?</\\1\\s*>|<!--.*?-->|<!\\[CDATA\\[.*?]]>")
private val Token = Regex("(?s)<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>|<[!?][^>]*>|([^<]+)|<")
private val Attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?")

private val VoidTags = setOf("br", "img", "hr", "meta", "link", "input", "col", "area", "base", "wbr", "source", "embed", "param", "track")

/** Tags that end an open paragraph, as a browser would. */
private val ClosesParagraph = setOf(
    "p", "div", "table", "ul", "ol", "dl", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "hr", "center", "section",
    "article", "header", "footer", "address", "figure",
)

/**
 * Mail HTML as a tree, forgiving the way browsers are: unclosed paragraphs, list items, cells and
 * rows close themselves, stray closing tags are ignored, and row groups (tbody, thead) may be there
 * or not. Head, styles, scripts and comments are dropped; text keeps its entities for the renderer.
 */
fun parseHtml(html: String): HtmlElement {
    val root = HtmlElement("body", emptyMap())
    val stack = ArrayDeque<HtmlElement>().apply { addLast(root) }

    fun current() = stack.last()
    fun closeUpTo(tag: String, stopAt: Set<String>) {
        val at = stack.indexOfLast { it.tag == tag || it.tag in stopAt }
        if (at > 0 && stack[at].tag == tag) while (stack.size > at) stack.removeLast()
    }
    fun open(element: HtmlElement) {
        when (element.tag) {
            "li" -> closeUpTo("li", setOf("ul", "ol"))

            "dt", "dd" -> {
                closeUpTo("dt", setOf("dl"))
                closeUpTo("dd", setOf("dl"))
            }

            "tr" -> {
                closeUpTo("td", setOf("table"))
                closeUpTo("th", setOf("table"))
                closeUpTo("tr", setOf("table"))
            }

            "td", "th" -> {
                closeUpTo("td", setOf("tr", "table"))
                closeUpTo("th", setOf("tr", "table"))
            }

            "tbody", "thead", "tfoot" -> listOf("td", "th", "tr", "tbody", "thead", "tfoot").forEach { closeUpTo(it, setOf("table")) }
        }
        if (element.tag in ClosesParagraph) closeUpTo("p", setOf("td", "th", "li", "blockquote", "div", "table"))
        current().children += element
        if (element.tag !in VoidTags) stack.addLast(element)
    }

    for (m in Token.findAll(html.replace(HiddenBlocks, ""))) {
        m.groups[4]?.value?.let { text ->
            current().children += HtmlTextNode(text)
            continue
        }
        val name = m.groupValues[2].lowercase()
        if (name.isEmpty()) {
            if (m.value == "<") current().children += HtmlTextNode("<")
            continue
        }
        if (name == "html" || name == "body" || name == "head") continue
        if (m.groupValues[1] == "/") {
            val at = stack.indexOfLast { it.tag == name }
            if (at > 0) while (stack.size > at) stack.removeLast()
            continue
        }
        val raw = m.groupValues[3]
        val attrs = Attribute.findAll(raw).associate { a ->
            a.groupValues[1].lowercase() to a.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
        }
        val element = HtmlElement(name, attrs)
        open(element)
        if (raw.trimEnd().endsWith("/") && element.tag !in VoidTags && stack.last() === element) stack.removeLast()
    }
    return root
}
