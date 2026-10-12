package co.abaye.mailtice

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.notify.MailNotifications
import co.abaye.mailtice.notify.NotificationTexts
import co.abaye.mailtice.notify.Notifier
import co.abaye.mailtice.sync.NewMail
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MailNotificationsTest {
    /** What reached the OS: "message:<account>:<id>" or "summary:<account>:<count>". */
    private class Recorder : Notifier {
        val shown = mutableListOf<String>()
        override val available = true

        override fun showMessage(account: Account, message: MailMessage, texts: NotificationTexts) {
            shown += "message:${account.id}:${message.id}"
        }

        override fun showSummary(account: Account, count: Int, texts: NotificationTexts) {
            shown += "summary:${account.id}:$count"
        }
    }

    private fun account(id: String) = Account(id, ProviderKind.Gmail, "$id@example.com")

    private fun round(accountId: String, vararg ids: String) = NewMail(
        account(accountId),
        ids.map { MailMessage(accountId, it, it, 0, "", "", "", "", "", 0, true, false, false, 0) },
    )

    private fun announce(vararg rounds: NewMail): List<String> {
        val recorder = Recorder()
        runBlocking { MailNotifications(recorder).announce(rounds.toList(), UserSettings()) }
        return recorder.shown
    }

    @Test
    fun aFewMessagesAreAnnouncedOneByOne() {
        assertEquals(listOf("message:a:1", "message:a:2", "message:b:3"), announce(round("a", "1", "2"), round("b", "3")))
    }

    // A day away: every account brings a few, together more than five.
    @Test
    fun manyAcrossAccountsBecomeOneSummaryEach() {
        assertEquals(
            listOf("summary:a:3", "summary:b:3"),
            announce(round("a", "1", "2", "3"), round("b", "4", "5", "6")),
        )
    }

    // A long catch-up comes in pages, each a round of its own.
    @Test
    fun thePagesOfOneAccountAddUp() {
        assertEquals(listOf("summary:a:6"), announce(round("a", "1", "2", "3"), round("a", "4", "5", "6")))
    }

    @Test
    fun aQuietAccountIsLeftOut() {
        val quiet = NewMail(account("q").copy(notify = false), round("q", "9").messages)
        assertEquals(listOf("message:a:1"), announce(round("a", "1"), quiet))
    }
}
