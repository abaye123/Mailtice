package co.abaye.mailtice

import co.abaye.mailtice.search.MailSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailSearchTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60_000L

    @Test
    fun operatorsWordsAndPhrases() {
        val s = MailSearch.parse("""invoice "kitchen quote" -spam from:dana to:"Dan Cohen" subject:Q4 has:attachment is:unread""", now)
        assertEquals(listOf("invoice", "kitchen quote"), s.words)
        assertEquals(listOf("spam"), s.excluded)
        assertEquals("dana", s.from)
        assertEquals("Dan Cohen", s.to)
        assertEquals("Q4", s.subject)
        assertTrue(s.hasAttachment)
        assertEquals(true, s.unread)
    }

    @Test
    fun hebrewAliasesAndDates() {
        val s = MailSearch.parse("מאת:יוסי נושא:\"הצעת מחיר\" אחרי:2026/01/31 older_than:2w", now)
        assertEquals("יוסי", s.from)
        assertEquals("הצעת מחיר", s.subject)
        assertTrue(s.after != null)
        assertEquals(now - 14 * day, s.before)
    }

    @Test
    fun unknownOperatorIsAWord() {
        val s = MailSearch.parse("label:work", now)
        assertEquals(listOf("label:work"), s.words)
        assertNull(s.after)
    }

    @Test
    fun gmailQueryRoundTrips() {
        val s = MailSearch.parse("מאת:dana \"two words\" -x newer_than:1d", now)
        assertEquals("\"two words\" -x from:dana after:${(now - day) / 1000}", s.toGmailQuery())
    }
}
