package co.abaye.mailtice.main.html

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.abaye.mailtice.main.decodeHtmlText
import co.abaye.mailtice.main.unescapeCid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * A small renderer for the HTML subset mail is written in: tables (at any depth), images, inline
 * styles for colour, size, spacing, backgrounds and borders, links and link buttons, lists and
 * quotes. What it does not do - floats, flex and grid, positioned boxes, background images, forms,
 * media - is what sends a message to the webview instead ([htmlTier]).
 *
 * Designed mail is drawn on white "paper" with its own colours, the way webmail shows it, dark
 * mode included: its colours were chosen against white.
 */

private val PaperText = Color(0xFF202124)
private val DefaultLink = Color(0xFF1A73E8)
private val Placeholder = Color(0xFFF1F3F4)
private val RuleColor = Color(0xFFDADCE0)
private const val BASE_FONT = 15f
private const val PLACEHOLDER_MAX = 200f

/** What text inherits down the tree. Sizes in CSS pixels (dp / sp alike). */
@Immutable
private data class TextProps(
    val color: Color = PaperText,
    val fontSize: Float = BASE_FONT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val align: TextAlign? = null,
    val lineHeight: Float? = null,
    val link: String? = null,
)

@Immutable
private class RenderContext(val remoteImages: Boolean, val inlineImages: Map<String, String>, val onOpen: (String) -> Unit)

private val InlineTags = setOf(
    "a", "span", "b", "strong", "i", "em", "u", "s", "strike", "del", "ins", "font", "small", "big", "sub", "sup", "code", "tt",
    "kbd", "samp", "br", "abbr", "cite", "mark", "label", "q", "time", "wbr", "nobr", "o:p", "bdi", "bdo",
)

private sealed interface Segment {
    class Inline(val nodes: List<HtmlNode>) : Segment

    class Block(val element: HtmlElement) : Segment
}

/**
 * [html] drawn natively, for mail [htmlTier] judged [HtmlTier.Rich]. Remote images load only when
 * [remoteImages]; [inlineImages] holds the message's cid: parts as data: URIs. Links go to [onOpen].
 */
@Composable
fun RichHtmlBody(
    html: String,
    remoteImages: Boolean,
    inlineImages: Map<String, String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document by produceState<HtmlElement?>(null, html) { value = withContext(Dispatchers.Default) { parseHtml(html) } }
    val root = document
    if (root == null) {
        Box(modifier.fillMaxWidth().height(96.dp))
        return
    }
    val direction = remember(root) { documentDirection(root) }
    val context = remember(remoteImages, inlineImages, onOpen) { RenderContext(remoteImages, inlineImages, onOpen) }
    // HTML lays out left to right unless it says otherwise, whatever the app's own language.
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
            SelectionContainer { Children(root, TextProps(), context) }
        }
    }
}

/** [element]'s content, full width - or, when [wrap] (a link button, an inline-block box), as wide as it needs. */
@Composable
private fun Children(element: HtmlElement, props: TextProps, context: RenderContext, wrap: Boolean = false) {
    val segments = remember(element) { segmentsOf(element) }
    Column(if (wrap) Modifier else Modifier.fillMaxWidth(), horizontalAlignment = props.align.horizontal()) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Inline -> InlineRun(segment.nodes, props, context, wrap)
                is Segment.Block -> Block(segment.element, props, context)
            }
        }
    }
}

private fun TextAlign?.horizontal(): Alignment.Horizontal = when (this) {
    TextAlign.Center -> Alignment.CenterHorizontally
    TextAlign.Right -> AbsoluteAlignment.Right
    TextAlign.Left -> AbsoluteAlignment.Left
    else -> Alignment.Start
}

/** Runs of inline content, and the blocks between them. Images and styled boxes count as blocks. */
private fun segmentsOf(element: HtmlElement): List<Segment> {
    val out = mutableListOf<Segment>()
    val run = mutableListOf<HtmlNode>()
    fun flush() {
        if (run.isNotEmpty()) out += Segment.Inline(run.toList())
        run.clear()
    }
    for (child in element.children) {
        if (child is HtmlTextNode || (child is HtmlElement && isInlineFlow(child))) {
            run += child
        } else if (child is HtmlElement) {
            flush()
            out += Segment.Block(child)
        }
    }
    flush()
    return out
}

/** Text-level all the way down: no images, no blocks, no box of its own (background, padding, border). */
private fun isInlineFlow(element: HtmlElement): Boolean {
    if (element.tag !in InlineTags) return false
    val css = styleOf(element)
    val display = css["display"]?.lowercase()
    if (display == "block" || display == "inline-block" || display == "table") return false
    if (boxSides(css, "padding")?.isZero == false && (css.containsKey("background") || css.containsKey("background-color"))) return false
    return element.children.all { it is HtmlTextNode || (it is HtmlElement && isInlineFlow(it)) }
}

private fun documentDirection(root: HtmlElement): LayoutDirection {
    val queue = ArrayDeque<Pair<HtmlElement, Int>>().apply { add(root to 0) }
    while (queue.isNotEmpty()) {
        val (element, depth) = queue.removeFirst()
        val dir = element.attr("dir") ?: styleOf(element)["direction"]
        when (dir?.lowercase()) {
            "rtl" -> return LayoutDirection.Rtl
            "ltr" -> return LayoutDirection.Ltr
        }
        if (depth < 4) element.elements().forEach { queue.add(it to depth + 1) }
    }
    // No direction given: Hebrew or Arabic mail that forgot to say so still reads right to left.
    var rtl = 0
    var ltr = 0
    fun count(node: HtmlNode) {
        if (rtl + ltr > 2_000) return
        when (node) {
            is HtmlTextNode -> node.text.forEach { c ->
                if (c in '֐'..'ۿ') {
                    rtl++
                } else if (c in 'a'..'z' || c in 'A'..'Z') {
                    ltr++
                }
            }

            is HtmlElement -> node.children.forEach(::count)
        }
    }
    count(root)
    return if (rtl > ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
}

private val HeadingScale = mapOf("h1" to 1.8f, "h2" to 1.5f, "h3" to 1.25f, "h4" to 1.1f, "h5" to 1f, "h6" to 0.9f)
private val FontSizes = mapOf("1" to 10f, "2" to 13f, "3" to 15f, "4" to 18f, "5" to 24f, "6" to 32f, "7" to 48f)
private val SizeKeywords = mapOf(
    "xx-small" to 9f,
    "x-small" to 10f,
    "small" to 13f,
    "medium" to 15f,
    "large" to 18f,
    "x-large" to 24f,
    "xx-large" to 32f,
)

private fun TextProps.inherit(element: HtmlElement, css: Map<String, String>): TextProps {
    val tag = element.tag
    val size = cssLength(css["font-size"], fontSize)
        ?: SizeKeywords[css["font-size"]?.trim()?.lowercase()]
        ?: (if (tag == "font") FontSizes[element.attr("size")?.trim()] else null)
        ?: HeadingScale[tag]?.let { it * fontSize }
        ?: (if (tag == "small") fontSize * 0.85f else null)
        ?: fontSize
    val weight = css["font-weight"]?.trim()?.lowercase()
    val isBold = when {
        weight == "bold" || weight == "bolder" -> true
        weight?.toIntOrNull()?.let { it >= 600 } == true -> true
        weight == "normal" || weight?.toIntOrNull()?.let { it < 600 } == true -> false
        tag in setOf("b", "strong", "th") || tag in HeadingScale -> true
        else -> bold
    }
    val decoration = css["text-decoration"]?.lowercase() ?: css["text-decoration-line"]?.lowercase()
    val height = css["line-height"]?.trim()?.lowercase()?.let { lh ->
        lh.toFloatOrNull() ?: cssPercent(lh)?.div(100f) ?: cssLength(lh, size)?.div(size)
    }
    return copy(
        color = cssColor(css["color"]) ?: (if (tag == "font") cssColor(element.attr("color")) else null) ?: color,
        fontSize = size,
        bold = isBold,
        italic = when (css["font-style"]?.lowercase()) {
            "italic", "oblique" -> true
            "normal" -> false
            else -> italic || tag in setOf("i", "em", "cite")
        },
        underline = when {
            decoration?.contains("none") == true -> false
            decoration?.contains("underline") == true || tag == "u" || tag == "ins" -> true
            else -> underline
        },
        strike = decoration?.contains("line-through") == true || tag in setOf("s", "strike", "del") || strike,
        align = cssAlign(css["text-align"]) ?: cssAlign(element.attr("align")) ?: (
            if (tag ==
                "center"
            ) {
                TextAlign.Center
            } else {
                null
            }
            ) ?: align,
        lineHeight = height ?: lineHeight,
    )
}

private fun safeHref(element: HtmlElement): String? = element.attr("href")?.let(::decodeHtmlText)?.trim()
    ?.takeIf { href -> listOf("http://", "https://", "mailto:").any { href.startsWith(it, ignoreCase = true) } }

@Composable
private fun Block(element: HtmlElement, parent: TextProps, context: RenderContext) {
    val css = remember(element) { styleOf(element) }
    if (isHidden(element, css)) return
    val link = if (element.tag == "a") safeHref(element) ?: parent.link else parent.link
    val props = remember(element, parent) { parent.inherit(element, css).copy(link = link) }
    when (element.tag) {
        "table" -> Table(element, css, props, context)

        "img" -> MailImage(element, css, props, context)

        "hr" -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = RuleColor)

        "ul", "ol" -> ListBlock(element, css, props, context)

        else -> {
            val wrap = css["display"]?.lowercase() == "inline-block"
            Box(boxModifier(element, css, props, context)) { Children(element, props, context, wrap) }
        }
    }
}

private val DefaultMargins = mapOf(
    "p" to Sides(0f, 0f, 12f, 0f),
    "h1" to Sides(10f, 0f, 10f, 0f),
    "h2" to Sides(10f, 0f, 8f, 0f),
    "h3" to Sides(8f, 0f, 6f, 0f),
    "blockquote" to Sides(4f, 0f, 4f, 12f),
    "pre" to Sides(4f, 0f, 8f, 0f),
)

/** Margins outside, then width, background, border and the link, then padding inside - the CSS box, in order. */
private fun boxModifier(element: HtmlElement, css: Map<String, String>, props: TextProps, context: RenderContext): Modifier {
    val margin = boxSides(css, "margin", props.fontSize) ?: DefaultMargins[element.tag] ?: Sides.Zero
    var m: Modifier = Modifier.padding(
        start = margin.start.coerceAtLeast(0f).dp,
        top = margin.top.coerceAtLeast(0f).dp,
        end = margin.end.coerceAtLeast(0f).dp,
        bottom = margin.bottom.coerceAtLeast(0f).dp,
    )
    val width = cssLength(css["width"]) ?: element.attr("width")?.takeIf { !it.endsWith("%") }?.toFloatOrNull()
    val percent = cssPercent(css["width"]) ?: cssPercent(element.attr("width"))
    val maxWidth = cssLength(css["max-width"])
    val inlineBlock = css["display"]?.lowercase() == "inline-block"
    m = when {
        width != null -> m.widthIn(max = width.dp).fillMaxWidth()

        percent != null -> m.fillMaxWidth(percent.coerceIn(1f, 100f) / 100f)

        maxWidth != null -> m.widthIn(max = maxWidth.dp).fillMaxWidth()

        // A link button or chip is as wide as its content, placed by the parent's alignment.
        inlineBlock -> m

        else -> m.fillMaxWidth()
    }
    val radius = cssLength(css["border-radius"])
    val shape = if (radius != null && radius > 0f) RoundedCornerShape(radius.dp) else RectangleShape
    if (radius != null && radius > 0f) m = m.clip(shape)
    val background = cssColor(css["background-color"]) ?: cssColor(css["background"]) ?: cssColor(element.attr("bgcolor"))
    if (background != null) m = m.background(background, shape)
    border(css["border"])?.let { (w, c) -> m = m.border(w.dp, c, shape) }
    // A bar on one side, the way quotes and callouts are marked.
    listOf("border-left" to true, "border-right" to false).forEach { (name, left) ->
        border(css[name])?.let { (w, c) ->
            m = m.drawBehind {
                val x = if (left) w.dp.toPx() / 2 else size.width - w.dp.toPx() / 2
                drawLine(c, Offset(x, 0f), Offset(x, size.height), w.dp.toPx())
            }
        }
    }
    if (element.tag == "blockquote" && css["border-left"] == null) {
        m = m.drawBehind { drawLine(RuleColor, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
    }
    props.link?.takeIf { element.tag == "a" }?.let { url -> m = m.clickable { context.onOpen(url) } }
    val padding = boxSides(css, "padding", props.fontSize) ?: if (element.tag == "blockquote") Sides(0f, 0f, 0f, 10f) else Sides.Zero
    if (!padding.isZero) {
        m = m.padding(
            start = padding.start.coerceAtLeast(0f).dp,
            top = padding.top.coerceAtLeast(0f).dp,
            end = padding.end.coerceAtLeast(0f).dp,
            bottom = padding.bottom.coerceAtLeast(0f).dp,
        )
    }
    return m
}

/** A `border` shorthand's width and colour ("1px solid #ccc"); null for none or zero. */
private fun border(value: String?): Pair<Float, Color>? {
    val parts = value?.trim()?.lowercase()?.split(Regex("\\s+")) ?: return null
    if ("none" in parts || "hidden" in parts) return null
    val width = parts.firstNotNullOfOrNull { cssLength(it) } ?: 1f
    val color = parts.firstNotNullOfOrNull { cssColor(it) } ?: return null
    return if (width <= 0f) null else width to color
}

@Composable
private fun InlineRun(nodes: List<HtmlNode>, props: TextProps, context: RenderContext, wrap: Boolean = false) {
    val text = remember(nodes, props) { inlineText(nodes, props, context) }
    if (text.text.isBlank()) return
    Text(
        text,
        if (wrap) Modifier else Modifier.fillMaxWidth(),
        style = TextStyle(
            color = props.color,
            fontSize = props.fontSize.sp,
            fontWeight = if (props.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (props.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration(props.underline, props.strike),
            textAlign = props.align ?: TextAlign.Start,
            lineHeight = (props.lineHeight ?: 1.4f).coerceIn(1f, 3f).em,
            textDirection = TextDirection.Content,
        ),
    )
}

private fun decoration(underline: Boolean, strike: Boolean): TextDecoration? = when {
    underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strike -> TextDecoration.LineThrough
    else -> null
}

private val Whitespace = Regex("[ \\t\\r\\n\\u000C]+")

/** Inline nodes as styled text: whitespace collapsed as a browser does, styles and links as spans. */
private fun inlineText(nodes: List<HtmlNode>, base: TextProps, context: RenderContext): AnnotatedString {
    val out = AnnotatedString.Builder()
    var last = '\n'
    fun put(text: String) {
        if (text.isEmpty()) return
        out.append(text)
        last = text.last()
    }
    fun walk(node: HtmlNode, props: TextProps) {
        when (node) {
            is HtmlTextNode -> {
                val t = decodeHtmlText(node.text).replace(Whitespace, " ")
                put(if (last == '\n' || last == ' ') t.trimStart() else t)
            }

            is HtmlElement -> {
                if (node.tag == "br") {
                    put("\n")
                    return
                }
                val css = styleOf(node)
                if (isHidden(node, css)) return
                val own = props.inherit(node, css)
                val span = SpanStyle(
                    color = if (own.color != props.color) own.color else Color.Unspecified,
                    fontSize = if (own.fontSize != props.fontSize) own.fontSize.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    fontWeight = if (own.bold != props.bold) (if (own.bold) FontWeight.Bold else FontWeight.Normal) else null,
                    fontStyle = if (own.italic != props.italic) (if (own.italic) FontStyle.Italic else FontStyle.Normal) else null,
                    textDecoration = if (own.underline != props.underline || own.strike != props.strike) {
                        decoration(own.underline, own.strike) ?: TextDecoration.None
                    } else {
                        null
                    },
                    fontFamily = if (node.tag in setOf("code", "tt", "kbd", "samp")) FontFamily.Monospace else null,
                    background = cssColor(css["background-color"]) ?: Color.Unspecified,
                )
                val styled = out.pushStyle(span)
                val href = if (node.tag == "a") safeHref(node) else null
                val linked = href?.let { url ->
                    val color = cssColor(css["color"]) ?: DefaultLink
                    val underline = css["text-decoration"]?.lowercase()?.contains("none") != true
                    val style = SpanStyle(color = color, textDecoration = if (underline) TextDecoration.Underline else null)
                    out.pushLink(LinkAnnotation.Clickable(url, TextLinkStyles(style)) { context.onOpen(url) })
                }
                node.children.forEach { walk(it, own) }
                linked?.let { out.pop(it) }
                out.pop(styled)
            }
        }
    }
    nodes.forEach { walk(it, base) }
    val built = out.toAnnotatedString()
    return built.subSequence(0, built.text.trimEnd().length)
}

@Composable
private fun ListBlock(element: HtmlElement, css: Map<String, String>, props: TextProps, context: RenderContext) {
    val ordered = element.tag == "ol"
    val first = element.attr("start")?.toIntOrNull() ?: 1
    val items = remember(element) { element.elements().filter { it.tag == "li" } }
    Column(boxModifier(element, css, props, context).padding(start = 4.dp)) {
        items.forEachIndexed { i, item ->
            Row {
                Text(
                    if (ordered) "${first + i}. " else "• ",
                    style = TextStyle(color = props.color, fontSize = props.fontSize.sp, lineHeight = (props.lineHeight ?: 1.4f).em),
                )
                Box(Modifier.weight(1f)) { Children(item, remember(item, props) { props.inherit(item, styleOf(item)) }, context) }
            }
        }
    }
}

// ---- tables --------------------------------------------------------------------------------

/** The rows of [table], looking through tbody / thead / tfoot. */
private fun rowsOf(table: HtmlElement): List<HtmlElement> = table.elements().flatMap { child ->
    when (child.tag) {
        "tr" -> listOf(child)
        "tbody", "thead", "tfoot" -> child.elements().filter { it.tag == "tr" }
        else -> emptyList()
    }
}

/** A mail layout's usual width, for tables that give cells pixel widths but none of their own. */
private const val ASSUMED_TABLE_WIDTH = 600f

/**
 * How a row's width is shared: percentages as given, pixel widths as a share of the table's width
 * (or of a usual 600 px), and cells without a width splitting what is left. Each is a weight, so
 * a narrow reader scales the whole row rather than cutting it.
 */
private fun cellWeights(cells: List<HtmlElement>, tableWidth: Float?): List<Float> {
    val total = tableWidth ?: ASSUMED_TABLE_WIDTH
    val known = cells.map { cell ->
        val css = styleOf(cell)
        val span = cell.attr("colspan")?.toIntOrNull()?.coerceIn(1, 12) ?: 1
        val pct = cssPercent(css["width"]) ?: cssPercent(cell.attr("width"))
        val px = cssLength(css["width"]) ?: cell.attr("width")?.takeIf { !it.endsWith("%") }?.toFloatOrNull()
        when {
            pct != null -> pct
            px != null -> px / total * 100f
            else -> null
        } to span
    }
    val used = known.sumOf { (it.first ?: 0f).toDouble() }.toFloat()
    val free = known.filter { it.first == null }.sumOf { it.second }
    val share = if (free == 0) 0f else ((100f - used) / free).coerceAtLeast(100f / (cells.size * 4))
    return known.map { (w, span) -> (w ?: share * span).coerceAtLeast(0.5f) }
}

@Composable
private fun Table(element: HtmlElement, css: Map<String, String>, props: TextProps, context: RenderContext) {
    val rows = remember(element) { rowsOf(element) }
    val padding = element.attr("cellpadding")?.toFloatOrNull() ?: 0f
    val spacing = element.attr("cellspacing")?.toFloatOrNull() ?: 0f
    val tableWidth = cssLength(css["width"]) ?: element.attr("width")?.takeIf { !it.endsWith("%") }?.toFloatOrNull()
    val percent = cssPercent(css["width"]) ?: cssPercent(element.attr("width"))
    // No width: as wide as its content, like a browser's table (a link button, a small box). A single
    // column simply wraps its cell; several are measured, which a lone column never needs.
    val shrink = tableWidth == null && percent == null
    val singleColumn = remember(element) { rows.all { row -> row.elements().count { it.tag == "td" || it.tag == "th" } <= 1 } }
    var m: Modifier = when {
        tableWidth != null -> Modifier.widthIn(max = tableWidth.dp).fillMaxWidth()
        percent != null -> Modifier.fillMaxWidth(percent.coerceIn(1f, 100f) / 100f)
        singleColumn -> Modifier
        else -> Modifier.width(IntrinsicSize.Max)
    }
    val margin = boxSides(css, "margin", props.fontSize)
    if (margin != null) m = Modifier.padding(top = margin.top.coerceAtLeast(0f).dp, bottom = margin.bottom.coerceAtLeast(0f).dp).then(m)
    val radius = cssLength(css["border-radius"])
    val shape = if (radius != null && radius > 0f) RoundedCornerShape(radius.dp) else RectangleShape
    if (radius != null && radius > 0f) m = m.clip(shape)
    val background = cssColor(css["background-color"]) ?: cssColor(css["background"]) ?: cssColor(element.attr("bgcolor"))
    if (background != null) m = m.background(background, shape)
    val framed = (element.attr("border")?.toFloatOrNull() ?: 0f) > 0f
    (border(css["border"]) ?: if (framed) 1f to RuleColor else null)?.let { (w, c) -> m = m.border(w.dp, c, shape) }
    boxSides(css, "padding", props.fontSize)?.takeIf { !it.isZero }?.let { p ->
        m = m.padding(start = p.start.dp, top = p.top.dp, end = p.end.dp, bottom = p.bottom.dp)
    }
    // Text alignment stops at a table, as mail clients (and browsers in quirks mode) have it: an outer
    // cell's align="center" places the table, it does not centre every word inside.
    val inside = remember(props, css) { props.copy(align = cssAlign(css["text-align"])) }
    val table: @Composable () -> Unit = {
        Column(m, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
            rows.forEach { row -> TableRow(row, inside, context, padding, spacing, tableWidth, framed, wrap = shrink && singleColumn) }
        }
    }
    // align="center" places the table itself, not its text.
    when (cssAlign(element.attr("align"))) {
        TextAlign.Center -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { table() }
        TextAlign.Right -> Box(Modifier.fillMaxWidth(), contentAlignment = AbsoluteAlignment.TopRight) { table() }
        else -> table()
    }
}

@Composable
private fun TableRow(
    row: HtmlElement,
    parent: TextProps,
    context: RenderContext,
    padding: Float,
    spacing: Float,
    tableWidth: Float?,
    framed: Boolean,
    wrap: Boolean,
) {
    val css = remember(row) { styleOf(row) }
    if (isHidden(row, css)) return
    val cells = remember(row) { row.elements().filter { it.tag == "td" || it.tag == "th" } }
    if (cells.isEmpty()) return
    val props = remember(row, parent) { parent.inherit(row, css) }
    val weights = remember(row, tableWidth) { cellWeights(cells, tableWidth) }
    // Cells with their own background have to reach the full row height, as in a browser.
    val stretch = remember(row) {
        cells.any { c ->
            styleOf(c).let { it.containsKey("background-color") || it.containsKey("background") } ||
                c.attrs.containsKey("bgcolor")
        }
    }
    val background = cssColor(css["background-color"]) ?: cssColor(css["background"]) ?: cssColor(row.attr("bgcolor"))
    var m = if (wrap) Modifier else Modifier.fillMaxWidth()
    if (stretch && !wrap) m = m.height(IntrinsicSize.Min)
    if (background != null) m = m.background(background)
    cssLength(css["height"] ?: row.attr("height"))?.let { m = m.heightIn(min = it.dp) }
    Row(m, horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
        cells.forEachIndexed { i, cell ->
            TableCell(cell, if (wrap) null else weights[i], props, context, padding, stretch && !wrap, framed)
        }
    }
}

@Composable
private fun RowScope.TableCell(
    cell: HtmlElement,
    weight: Float?,
    parent: TextProps,
    context: RenderContext,
    padding: Float,
    stretch: Boolean,
    framed: Boolean,
) {
    val css = remember(cell) { styleOf(cell) }
    if (isHidden(cell, css)) {
        if (weight != null) Spacer(Modifier.weight(weight))
        return
    }
    val base = if (cell.tag == "th" && parent.align == null) parent.copy(align = TextAlign.Center) else parent
    val props = remember(cell, base) { base.inherit(cell, css) }
    val valign = (cell.attr("valign") ?: css["vertical-align"])?.lowercase()
    val vertical = when (valign) {
        "top" -> Alignment.Top
        "bottom" -> Alignment.Bottom
        else -> Alignment.CenterVertically
    }
    // No weight: the cell of a single-column table without a width, as wide as its content.
    var m: Modifier = if (weight != null) Modifier.weight(weight) else Modifier
    m = if (stretch) m.fillMaxHeight() else m.align(vertical)
    val background = cssColor(css["background-color"]) ?: cssColor(css["background"]) ?: cssColor(cell.attr("bgcolor"))
    val radius = cssLength(css["border-radius"])
    val shape = if (radius != null && radius > 0f) RoundedCornerShape(radius.dp) else RectangleShape
    if (radius != null && radius > 0f) m = m.clip(shape)
    if (background != null) m = m.background(background, shape)
    (border(css["border"]) ?: if (framed) 1f to RuleColor else null)?.let { (w, c) -> m = m.border(w.dp, c, shape) }
    listOf("border-top" to true, "border-bottom" to false).forEach { (name, top) ->
        border(css[name])?.let { (w, c) ->
            m = m.drawBehind {
                val y = if (top) w.dp.toPx() / 2 else size.height - w.dp.toPx() / 2
                drawLine(c, Offset(0f, y), Offset(size.width, y), w.dp.toPx())
            }
        }
    }
    val inner = boxSides(css, "padding", props.fontSize) ?: Sides(padding, padding, padding, padding)
    m = m.padding(start = inner.start.dp, top = inner.top.dp, end = inner.end.dp, bottom = inner.bottom.dp)
    cssLength(css["height"] ?: cell.attr("height"))?.let { m = m.heightIn(min = it.coerceAtMost(PLACEHOLDER_MAX * 2).dp) }
    val content = when (valign) {
        "top" -> Alignment.TopStart
        "bottom" -> Alignment.BottomStart
        else -> Alignment.CenterStart
    }
    Box(m, contentAlignment = content) { Children(cell, props, context, wrap = weight == null) }
}

// ---- images --------------------------------------------------------------------------------

@Composable
private fun MailImage(element: HtmlElement, css: Map<String, String>, props: TextProps, context: RenderContext) {
    val src = element.attr("src").orEmpty().trim()
    val width = cssLength(css["width"]) ?: element.attr("width")?.takeIf { !it.endsWith("%") }?.toFloatOrNull()
    val height = cssLength(css["height"]) ?: element.attr("height")?.takeIf { !it.endsWith("%") }?.toFloatOrNull()
    // Tracking pixels and spacer GIFs draw nothing worth a slot.
    if ((width != null && width <= 2f) || (height != null && height <= 2f)) return
    val percent = cssPercent(css["width"]) ?: cssPercent(element.attr("width"))
    val image by produceState<ImageBitmap?>(null, src, context.remoteImages, context.inlineImages) {
        value = when {
            src.startsWith("cid:", ignoreCase = true) ->
                context.inlineImages[unescapeCid(src.substring(4))]?.let { uri ->
                    withContext(Dispatchers.Default) { MailImages.decodeDataUri(uri) }
                }

            src.startsWith("data:", ignoreCase = true) -> withContext(Dispatchers.Default) { MailImages.decodeDataUri(src) }

            (src.startsWith("https://", ignoreCase = true) || src.startsWith("http://", ignoreCase = true)) && context.remoteImages ->
                MailImages.load(src)

            else -> null
        }
    }
    val link = props.link
    var m: Modifier = Modifier
    boxSides(css, "margin")?.let { mg -> m = m.padding(top = mg.top.coerceAtLeast(0f).dp, bottom = mg.bottom.coerceAtLeast(0f).dp) }
    if (link != null) m = m.clickable { context.onOpen(link) }
    val bitmap = image
    if (bitmap == null) {
        // Not loaded (or not allowed): keep the layout's shape, not a hole, when the size is known.
        if (width != null && height != null) {
            Box(m.width(width.dp).height(height.coerceAtMost(PLACEHOLDER_MAX).dp).background(Placeholder))
        }
        return
    }
    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    val sized = when {
        percent != null -> m.fillMaxWidth(percent.coerceIn(1f, 100f) / 100f)
        width != null -> m.width(width.dp)
        height != null -> m.width((height * ratio).dp)
        else -> m.width(bitmap.width.dp)
    }
    val radius = cssLength(css["border-radius"])
    Image(
        bitmap,
        contentDescription = element.attr("alt"),
        modifier = sized.aspectRatio(ratio).then(
            if (radius != null &&
                radius > 0f
            ) {
                Modifier.clip(RoundedCornerShape(radius.dp))
            } else {
                Modifier
            },
        ),
        contentScale = ContentScale.Fit,
    )
}
