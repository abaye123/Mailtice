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
import kotlin.test.Test
import kotlin.test.assertEquals

/** The smart check reads an account's history straight from the database. */
class ActivityQueryTest {
    private fun msg(id: String, at: Long, folder: String) = RemoteMessage(
        id = id, threadId = id, uid = 0, fromName = "", fromAddress = "", toLine = "", subject = "", snippet = "",
        receivedAt = at, unread = false, flagged = false, hasAttachments = false, sizeBytes = 0, folderIds = setOf(folder), body = null,
    )

    @Test
    fun sentAndReceivedAreTold() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(Account("a", ProviderKind.Gmail, "a@x.com"))
        repo.mergeFolders("a", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("SENT", "Sent", FolderRole.Sent)))
        repo.applyBatch("a", SyncBatch(newMessages = listOf(msg("1", 1_000, "INBOX"), msg("2", 2_000, "SENT"), msg("3", 3_000, "INBOX"))))

        assertEquals(
            listOf(1_000L to false, 2_000L to true, 3_000L to false),
            repo.activity("a", 0).map {
                it.at to it.sent
            }.sortedBy { it.first },
        )
        assertEquals(2_000L to 3_000L, repo.latestActivity("a"))
        assertEquals(listOf(3_000L), repo.activity("a", 2_500).map { it.at })
    }
}
