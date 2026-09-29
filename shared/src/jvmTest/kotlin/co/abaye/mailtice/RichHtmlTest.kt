package co.abaye.mailtice

import androidx.compose.ui.graphics.Color
import co.abaye.mailtice.main.HtmlTier
import co.abaye.mailtice.main.html.HtmlElement
import co.abaye.mailtice.main.html.HtmlTextNode
import co.abaye.mailtice.main.html.Sides
import co.abaye.mailtice.main.html.boxSides
import co.abaye.mailtice.main.html.cssColor
import co.abaye.mailtice.main.html.cssLength
import co.abaye.mailtice.main.html.isHidden
import co.abaye.mailtice.main.html.parseHtml
import co.abaye.mailtice.main.html.styleOf
import co.abaye.mailtice.main.htmlTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichHtmlTest {
    private fun HtmlElement.first(tag: String): HtmlElement? {
        if (this.tag == tag) return this
        return elements().firstNotNullOfOrNull { it.first(tag) }
    }

    @Test
    fun theParserForgivesWhatMailGetsWrong() {
        // Unclosed cells, rows and paragraphs, no tbody, a stray closer, head and comments.
        val root =
            parseHtml(
                "<html><head><style>p{}</style></head><body><!-- x --><table><tr><td>a<td>b<tr><td><p>one<p>two</div></table>after</body>",
            )
        val table = root.first("table")!!
        val rows = table.elements()
        assertEquals(2, rows.size)
        assertEquals(listOf("td", "td"), rows[0].elements().map { it.tag })
        val cell = rows[1].elements().single()
        assertEquals(listOf("p", "p"), cell.elements().map { it.tag })
        assertTrue(root.children.last() is HtmlTextNode)
        assertNull(root.first("style"))
    }

    @Test
    fun attributesInEveryQuotingAndVoidTags() {
        val root = parseHtml("<a href=\"https://x.com/?a=1&amp;b=2\" title='t' data-x=plain>link</a><br><img src=cid:logo width=120>")
        val a = root.first("a")!!
        assertEquals("https://x.com/?a=1&amp;b=2", a.attr("href"))
        assertEquals("t", a.attr("title"))
        assertEquals("plain", a.attr("data-x"))
        // A void tag opens nothing: the image is a sibling, not inside the <br>.
        assertEquals(listOf("a", "br", "img"), root.elements().map { it.tag })
    }

    @Test
    fun stylesColoursAndLengths() {
        val css =
            styleOf(parseHtml("<div style=\"COLOR: #1A73E8; padding: 4px 8px; padding-left: 20px !important\">x</div>").first("div")!!)
        assertEquals(Color(0xFF1A73E8), cssColor(css["color"]))
        assertEquals(Sides(4f, 8f, 4f, 20f), boxSides(css, "padding"))
        assertEquals(Color(0xFFAABBCC), cssColor("#abc"))
        assertEquals(Color(1f, 0f, 0f, 0.5f), cssColor("rgba(255, 0, 0, 0.5)"))
        assertNull(cssColor("transparent"))
        assertEquals(16f, cssLength("12pt"))
        assertEquals(30f, cssLength("2em"))
    }

    @Test
    fun hiddenTheWaysNewslettersHide() {
        fun hidden(style: String) = parseHtml("<div style=\"$style\">x</div>").first("div")!!.let { isHidden(it, styleOf(it)) }
        assertTrue(hidden("display:none"))
        assertTrue(hidden("max-height:0;overflow:hidden"))
        assertTrue(hidden("mso-hide:all"))
        assertTrue(hidden("font-size:0px"))
        assertFalse(hidden("max-height:40px;overflow:hidden"))
    }

    @Test
    fun tiersSendOnlyBrowserLayoutsToTheWebview() {
        assertEquals(HtmlTier.Simple, htmlTier("<div>Hi <b>there</b></div>"))
        assertEquals(HtmlTier.Rich, htmlTier("<table width=600><tr><td bgcolor=#eee><img src=cid:a width=600></td></tr></table>"))
        assertEquals(HtmlTier.Rich, htmlTier("<a style=\"display:inline-block;background:#1a73e8;padding:8px\">Go</a>"))
        assertEquals(HtmlTier.Web, htmlTier("<div style=\"display:flex\"><div>a</div></div>"))
        assertEquals(HtmlTier.Web, htmlTier("<td style=\"background-image:url(x.png)\">a</td>"))
        assertEquals(HtmlTier.Web, htmlTier("<form><input></form>"))
    }
}
