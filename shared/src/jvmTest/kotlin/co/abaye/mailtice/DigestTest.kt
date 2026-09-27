package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestTest {
    private val hour = 3_600_000L
    private fun msg(id: String, at: Long, unread: Boolean, folder: String = "INBOX") =
        RemoteMessage(id, id, 0, "Sender $id", "", "", "Subject $id", "", at, unread, false, false, 0, setOf(folder), null)

    @Test
    fun digestTellsFreshFromOld() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(Account("a", ProviderKind.Gmail, "a@x.com"))
        repo.mergeFolders("a", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("SENT", "Sent", FolderRole.Sent)))
        val now = 1_790_000_000_000L
        repo.applyBatch(
            "a",
            SyncBatch(
                newMessages = listOf(
                    msg("fresh", now - hour / 3, unread = true),
                    msg("old", now - 50 * hour, unread = true),
                    msg("read", now - 2 * hour, unread = false),
                    msg("sent", now - hour, unread = true, folder = "SENT"),
                ),
            ),
        )
        val d = repo.digest("a", now, TimeZone.UTC)
        assertEquals(2, d.unread)
        assertEquals(1, d.unreadRecent)
        assertEquals(now - hour / 3, d.newestUnreadAt)
        assertEquals(listOf("fresh", "old"), d.recentUnread.map { it.id })
        assertEquals(3, d.perDay.sum())
    }
}
