package co.abaye.mailtice

import co.abaye.mailtice.data.decodeSnapshot
import co.abaye.mailtice.data.encodeSnapshot
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.domain.conversationKey
import co.abaye.mailtice.domain.groupConversations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationsTest {
    private fun m(
        id: String,
        subject: String,
        at: Long,
        thread: String = "",
        from: String = "a@x.com",
        unread: Boolean = false,
        account: String = "acc",
    ) = MailMessage(account, id, thread, 0, from.substringBefore('@'), from, "", subject, "", at, unread, false, false, 0)

    @Test
    fun gmailThreadsByThreadIdIncludingTheFirstMessage() {
        // Gmail gives a conversation's first message its own id as the thread id.
        val first = m("t1", "Plan", 1, thread = "t1")
        val reply = m("m2", "Re: Plan", 2, thread = "t1")
        assertEquals(conversationKey(first), conversationKey(reply))
        assertTrue(conversationKey(m("m3", "Plan", 3, thread = "t9")) != conversationKey(first))
    }

    @Test
    fun imapThreadsBySubjectWithoutPrefixes() {
        assertEquals(conversationKey(m("INBOX/1", "Kitchen quote", 1)), conversationKey(m("Sent/7", "RE: Re: kitchen quote", 2)))
        // Another account's mail with the same subject is another conversation.
        assertTrue(conversationKey(m("INBOX/1", "Kitchen quote", 1)) != conversationKey(m("INBOX/1", "Kitchen quote", 1, account = "b")))
        // No subject: the message stands alone.
        assertEquals("acc/INBOX/5", conversationKey(m("INBOX/5", "  ", 1)))
    }

    @Test
    fun groupsKeepListOrderAndAddTheStoredRest() {
        val newest = m("m3", "Re: Plan", 30, thread = "t1", from = "yossi@x.com", unread = true)
        val other = m("m9", "Lunch", 20, thread = "t2")
        val older = m("m1", "Plan", 10, thread = "t1", from = "noa@x.com")
        val sent = m("m2", "Re: Plan", 15, thread = "t1", from = "noa@x.com")
        val threads = groupConversations(listOf(newest, other, older), mapOf(conversationKey(newest) to listOf(older, sent, newest)))
        assertEquals(listOf("m3", "m9"), threads.map { it.latest.id })
        val plan = threads.first()
        assertEquals(listOf("m1", "m2", "m3"), plan.all.map { it.id })
        assertEquals(listOf("m3", "m1"), plan.messages.map { it.id })
        assertEquals(3, plan.size)
        assertTrue(plan.unread)
        assertEquals(listOf("me", "yossi"), plan.participants("noa@x.com", "me"))
        assertFalse(threads.last().unread)
    }

    @Test
    fun conversationViewIsOnByDefaultAndPersists() {
        assertTrue(UserSettings().conversationView)
        val off = AppData(UserSettings(conversationView = false))
        assertFalse(decodeSnapshot(encodeSnapshot(off)).settings.conversationView)
        assertTrue(decodeSnapshot("").settings.conversationView)
    }
}
