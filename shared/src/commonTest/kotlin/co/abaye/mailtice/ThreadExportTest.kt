package co.abaye.mailtice

import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.export.ExportLabels
import co.abaye.mailtice.export.ThreadExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadExportTest {
    private fun message(id: String, at: Long, from: String = "a@example.com") =
        MailMessage("acc", id, "", 1, "", from, "me@example.com", "Hello", "", at, false, false, false, 0)

    @Test
    fun replyPrefixesAreIgnored() {
        assertEquals("Budget", ThreadExport.baseSubject("Re: Fwd: RE: Budget"))
        assertEquals("תקציב", ThreadExport.baseSubject("השב: תקציב"))
        assertEquals("Re-org plan", ThreadExport.baseSubject("Re-org plan"))
    }

    @Test
    fun mboxSeparatesAndEscapes() {
        val raw = "Subject: x\r\n\r\nFrom here on\r\n>From quoted\r\nplain\r\n".encodeToByteArray()
        val out = ThreadExport.mbox(listOf(message("2", 2_000) to raw, message("1", 0, "b@example.com") to raw)).decodeToString()
        val separators = out.lines().filter { it.startsWith("From ") }
        assertEquals(2, separators.size)
        // Oldest first.
        assertTrue(separators.first().startsWith("From b@example.com Thu Jan  1 00:00:00 1970"))
        assertTrue(">From here on" in out)
        assertTrue(">>From quoted" in out)
        assertFalse('\r' in out)
    }

    @Test
    fun htmlDropsScriptsAndKeepsOrder() {
        val labels = ExportLabels("From", "To", "Date", "Attachments", "2 messages")
        val html = ThreadExport.html(
            "Hello",
            listOf(
                message("2", 2_000) to MailBody("second", "<p onclick=\"x()\">second</p><script>alert(1)</script>", emptyList()),
                message("1", 1_000) to MailBody("first <b>", "", emptyList()),
            ),
            labels,
        ) { it.toString() }
        assertFalse("<script" in html)
        assertFalse("onclick" in html)
        assertTrue("first &lt;b&gt;" in html)
        assertTrue(html.indexOf("first") < html.indexOf("second"))
        assertTrue("Content-Security-Policy" in html)
    }
}
