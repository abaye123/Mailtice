package co.abaye.mailtice

import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.provider.MimeBuilder
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.parseAddressList
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MimeBuilderTest {
    private val mail = OutgoingMail(
        to = listOf("נועה לוי <noa@example.com>", "dan@example.com"),
        bcc = listOf("hidden@example.com"),
        subject = "שלום עולם - a subject long enough to need more than one encoded word in the header",
        text = "שורה ראשונה\nsecond line",
        inReplyTo = "<abc@example.com>",
    )

    @Test
    fun headersAreAsciiAndThreaded() {
        val raw = MimeBuilder.build("me@example.com", mail, epochMillis = 0)
        val headers = raw.substringBefore("\r\n\r\n")
        assertTrue(headers.all { it.code < 128 }, "headers must be 7-bit")
        assertTrue("In-Reply-To: <abc@example.com>" in headers)
        assertTrue("References: <abc@example.com>" in headers)
        assertTrue("Date: Thu, 1 Jan 1970 00:00:00 +0000" in headers)
        assertTrue("Bcc: hidden@example.com" in headers)
        assertTrue("=?UTF-8?B?" in headers.lines().first { it.startsWith("To:") })
    }

    @Test
    fun bodyRoundTripsAsUtf8() {
        val raw = MimeBuilder.build("me@example.com", mail, epochMillis = 0)
        val encoded = raw.substringAfter("\r\n\r\n").replace("\r\n", "")
        assertEquals("שורה ראשונה\r\nsecond line", Base64.Default.decode(encoded).decodeToString())
    }

    @Test
    fun encodedWordsStayShortAndDecodeBack() {
        val words = MimeBuilder.encodeWords(mail.subject).split("\r\n ")
        assertTrue(words.size > 1)
        assertTrue(words.all { it.length <= 75 })
        val decoded = words.joinToString("") { Base64.Default.decode(it.removePrefix("=?UTF-8?B?").removeSuffix("?=")).decodeToString() }
        assertEquals(mail.subject, decoded)
        assertEquals("plain ascii", MimeBuilder.encodeWords("plain ascii"))
    }

    @Test
    fun addressListsParse() {
        assertEquals(listOf("a@b.co", "Dana <d@x.org>"), parseAddressList("a@b.co, Dana <d@x.org>;"))
        assertEquals(emptyList(), parseAddressList("  "))
        assertNull(parseAddressList("a@b.co, not-an-address"))
        assertNull(parseAddressList("a@localhost"))
    }

    @Test
    fun sendAndTrashCapabilitiesPersist() {
        val caps = Capabilities(markRead = true, send = true, trash = true)
        assertEquals(caps, Capabilities.decode(caps.encode()))
        // A snapshot written before sending existed has six digits: the new ones read as false.
        val old = Capabilities.decode("110110")
        assertFalse(old.send)
        assertFalse(old.trash)
    }
}
