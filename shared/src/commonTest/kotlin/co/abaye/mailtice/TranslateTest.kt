package co.abaye.mailtice

import co.abaye.mailtice.translate.blocks
import co.abaye.mailtice.translate.htmlTextSegments
import co.abaye.mailtice.translate.looksForeign
import co.abaye.mailtice.translate.parseGtx
import co.abaye.mailtice.translate.replaceSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslateTest {
    @Test
    fun gtxAnswerParses() {
        // Trimmed from a real answer of the endpoint.
        val body =
            """[[["שלום דנה,\n","Hello Dana,\n",null,null,3],["מצורפת הצעת המחיר.\n\n","The quote is attached.\n\n",null,null,3],""" +
                """["תודה","Thanks",null,null,2]],null,"en",null,null,null,1,[],[["en"],null,[1],["en"]]]"""
        assertEquals("en" to "שלום דנה,\nמצורפת הצעת המחיר.\n\nתודה", parseGtx(body))
    }

    @Test
    fun longTextSplitsAtLines() {
        val text = "a".repeat(30) + "\n" + "b".repeat(30) + "\n" + "c".repeat(30)
        val parts = blocks(text, max = 70)
        assertEquals(listOf("a".repeat(30) + "\n" + "b".repeat(30), "c".repeat(30)), parts)
        assertEquals(text, parts.joinToString("\n"))
        // One line longer than a block is cut at spaces.
        assertEquals(listOf("one two", "three"), blocks("one two three", max = 8))
    }

    @Test
    fun foreignIsJudgedByScript() {
        assertTrue(looksForeign("Your order has shipped and will arrive on Tuesday.", "he"))
        // Hebrew mail full of English names and links is still Hebrew.
        assertFalse(looksForeign("שלום, מצרף את הקישור https://github.com/example/project לגבי ה-Pull Request", "he"))
        assertFalse(looksForeign("Your order has shipped and will arrive on Tuesday.", "en"))
        assertTrue(looksForeign("Ваш заказ отправлен и прибудет во вторник.", "en"))
        // Too few letters to judge.
        assertFalse(looksForeign("OK 👍", "he"))
    }

    @Test
    fun htmlIsTranslatedInPlace() {
        val html = "<html><head><style>p{color:red}</style><title>T</title></head><body>" +
            "<p>Hello <b>Dana</b>,</p><p>Tom &amp; Jerry</p><img src=\"x.png\"><p> 42 </p>" +
            "<div class=\"gmail_quote\">Old message</div></body></html>"
        val segments = htmlTextSegments(html)
        // Style, title, numbers and the quoted part are left alone.
        assertEquals(listOf("Hello", "Dana", ",", "Tom & Jerry").filter { it.any(Char::isLetter) }, segments.map { it.text })
        val out = replaceSegments(html, segments, listOf("שלום", "דנה", "טום & ג'רי"))
        assertTrue("<p>שלום <b>דנה</b>,</p><p>טום &amp; ג'רי</p>" in out)
        assertTrue("p{color:red}" in out && "<div class=\"gmail_quote\">Old message</div>" in out)
    }
}
