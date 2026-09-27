package co.abaye.mailtice

import co.abaye.mailtice.main.cidRefs
import co.abaye.mailtice.main.emailDocument
import co.abaye.mailtice.main.htmlHasQuote
import co.abaye.mailtice.main.splitQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailContentTest {
    @Test
    fun gmailReplySplits() {
        val body = "Sounds good, see you then.\n\nOn Mon, 21 Sep 2026 at 10:00, Dana Cohen <dana@x.com> wrote:\n> Shall we meet on Tuesday?\n> Dana"
        val split = splitQuote(body)
        assertEquals("Sounds good, see you then.", split.main)
        assertTrue(split.quoted.startsWith("On Mon"))
    }

    @Test
    fun wrappedAndHebrewHeadersSplit() {
        val wrapped = "Thanks!\n\nOn Mon, 21 Sep 2026 at 10:00, Dana Cohen <\ndana@x.com> wrote:\n> Hi"
        assertEquals("Thanks!", splitQuote(wrapped).main)
        val hebrew = "מעולה, תודה.\n\n‫בתאריך יום ב׳, 21 בספט׳ 2026 ב-10:00 מאת דנה כהן <dana@x.com>:‬\n> נפגש ביום שלישי?"
        assertEquals("מעולה, תודה.", splitQuote(hebrew).main)
    }

    @Test
    fun outlookHeaderSplits() {
        val body = "Approved.\n\n________________________________\nFrom: Dana Cohen <dana@x.com>\nSent: Monday, September 21, 2026 10:00\nTo: Noa\nSubject: Budget"
        assertEquals("Approved.", splitQuote(body).main)
    }

    @Test
    fun forwardsAndBareQuotesStayWhole() {
        val forward = "FYI\n\n---------- Forwarded message ---------\nFrom: Dana <dana@x.com>\nDate: Mon\n\nThe report"
        assertFalse(splitQuote(forward).hasQuote)
        assertFalse(splitQuote("> only a quote\n> nothing else").hasQuote)
        assertFalse(splitQuote("A plain note with no quote at all.").hasQuote)
    }

    @Test
    fun documentIsLockedDown() {
        val doc = emailDocument("<p>Hi</p><script>alert(1)</script><img src=\"https://t.example/p.gif\">", hideQuotes = false, remoteImages = false)
        assertFalse("<script" in doc)
        assertTrue("script-src 'none'" in doc)
        // Remote images are not allowed by the policy unless asked for.
        assertFalse("img-src data: cid: https:" in doc)
        assertTrue("img-src data: cid: https:" in emailDocument("<p>Hi</p>", hideQuotes = false, remoteImages = true))
    }

    @Test
    fun documentKeepsTheSendersHeadAndHidesQuotes() {
        val html = "<html><head><title>x</title></head><body><div>New</div><div class=\"gmail_quote\">Old</div></body></html>"
        assertTrue(htmlHasQuote(html))
        val doc = emailDocument(html, hideQuotes = true, remoteImages = true)
        assertTrue(doc.indexOf("Content-Security-Policy") < doc.indexOf("<title>"))
        assertTrue(".gmail_quote" in doc && "display: none" in doc)
        assertFalse(htmlHasQuote("<div class=\"gmail_quote\">---------- Forwarded message ---------</div>"))
    }

    @Test
    fun inlineImagesAreSwappedIn() {
        val html = "<p>Logo</p><img src=\"cid:image001.png%4001DA\"><img src=\"cid:missing\">"
        assertEquals(setOf("image001.png@01DA", "missing"), cidRefs(html))
        val doc = emailDocument(html, hideQuotes = false, remoteImages = false, inlineImages = mapOf("image001.png@01DA" to "data:image/png;base64,AAAA"))
        assertTrue("src=\"data:image/png;base64,AAAA\"" in doc)
        assertTrue("cid:missing" in doc)
    }

    @Test
    fun linksNeverAskForANewWindow() {
        val doc = emailDocument(
            "<a href=\"https://x.com\" target=\"_blank\" class=\"b\">X</a><a target='_new' href='https://y.com'>Y</a><area href=\"https://z.com\" target=_blank>",
            hideQuotes = false,
            remoteImages = false,
        )
        assertFalse("target=" in doc.substringAfter("</head>"))
        assertTrue("<a href=\"https://x.com\" class=\"b\">X</a>" in doc)
    }
}
