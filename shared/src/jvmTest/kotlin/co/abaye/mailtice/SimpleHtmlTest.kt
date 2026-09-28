package co.abaye.mailtice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import co.abaye.mailtice.main.isSimpleHtml
import co.abaye.mailtice.main.simpleHtmlText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleHtmlTest {
    private fun render(html: String, opened: MutableList<String> = mutableListOf()) =
        simpleHtmlText(html, Color.Blue, Color.Gray) { url -> opened.add(url) }

    @Test
    fun personalMailIsSimpleAndLayoutIsNot() {
        // A Gmail reply: divs, a line break, a link, the quote - and a tracking pixel.
        val reply = """<div dir="ltr">Hi,<br>see <a href="https://x.com">this</a></div><img width="1" height="1" src="https://t.co/p">
            <div class="gmail_quote"><blockquote>earlier</blockquote></div>"""
        assertTrue(isSimpleHtml(reply))
        // Outlook's head full of CSS does not count against it.
        assertTrue(
            isSimpleHtml(
                "<html><head><style>p.MsoNormal{margin:0}</style></head><body><p class=MsoNormal>Hello<o:p></o:p></p></body></html>",
            ),
        )
        assertFalse(isSimpleHtml("<table><tr><td>Newsletter</td></tr></table>"))
        assertFalse(isSimpleHtml("<p>Photo</p><img src=\"cid:photo1\" width=\"600\">"))
        assertFalse(isSimpleHtml("<div style=\"float:left\">side</div>"))
        assertFalse(isSimpleHtml("<body bgcolor=\"#eeeeee\">x</body>"))
    }

    @Test
    fun blocksBreakLinesAndSpacesCollapse() {
        val text = render("<div>Hello   <b>world</b></div><div>second&nbsp;line</div><p>third</p><br><br><br><br><p>after</p>").text
        assertEquals("Hello world\nsecond line\nthird\n\nafter", text)
    }

    @Test
    fun listsLinksAndStylesSurvive() {
        val opened = mutableListOf<String>()
        val text = render(
            "<ul><li>one</li><li>two</li></ul><ol><li>first</li><li>second</li></ol>" +
                "<a href=\"https://example.com/a?b=1&amp;c=2\">link</a> <a href=\"javascript:alert(1)\">bad</a> <b>bold</b>",
            opened,
        )
        assertEquals("• one\n• two\n1. first\n2. second\nlink bad bold", text.text)
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Clickable).tag }
        // Only http(s) and mailto become links; the entity in the address is decoded.
        assertEquals(listOf("https://example.com/a?b=1&c=2"), links)
        val bold = text.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals("bold", text.text.substring(bold.start, bold.end))
    }

    @Test
    fun hiddenPartsAndEntitiesAreHandled() {
        val text = render("<head><title>t</title><style>x{}</style></head><!-- note --><p>It&rsquo;s &quot;fine&quot; &#1513;</p>").text
        assertEquals("It’s \"fine\" ש", text)
    }
}
