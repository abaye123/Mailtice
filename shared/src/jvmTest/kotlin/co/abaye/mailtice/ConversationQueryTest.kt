package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.conversationKey
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationQueryTest {
    private fun remote(id: String, thread: String, subject: String, at: Long, folder: String) =
        RemoteMessage(id, thread, 0, "", "x@y.com", "", subject, "", at, false, false, false, 0, setOf(folder), null)

    private fun repo(): MailRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        return MailRepository(driver, Dispatchers.Unconfined)
    }

    @Test
    fun gmailConversationsCarryTheSentRepliesAndTheirFolders() {
        val repo = repo()
        repo.addAccount(Account("g", ProviderKind.Gmail, "me@y.com"))
        repo.mergeFolders("g", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("SENT", "Sent", FolderRole.Sent)))
        repo.applyBatch(
            "g",
            SyncBatch(
                listOf(
                    remote("t1", "t1", "Plan", 1, "SENT"),
                    remote("m2", "t1", "Re: Plan", 2, "INBOX"),
                    remote("m3", "t2", "Other", 3, "INBOX"),
                ),
            ),
        )
        val listed = repo.message("g", "m2")!!
        val thread = repo.conversationsOf(listOf(listed)).getValue(conversationKey(listed))
        assertEquals(listOf("t1", "m2"), thread.map { it.id })
        assertEquals(listOf(listOf("SENT"), listOf("INBOX")), thread.map { it.folderIds })
    }

    @Test
    fun imapConversationsGroupBySubjectInOneQuery() {
        val repo = repo()
        repo.addAccount(Account("i", ProviderKind.Imap, "me@y.com"))
        repo.mergeFolders("i", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("Sent", "Sent", FolderRole.Sent)))
        repo.applyBatch(
            "i",
            SyncBatch(
                listOf(
                    remote("Sent/1", "", "Kitchen quote", 1, "Sent"),
                    remote("INBOX/2", "", "RE: kitchen quote", 2, "INBOX"),
                    remote("INBOX/3", "", "Kitchen quote v2", 3, "INBOX"),
                    remote("INBOX/4", "", "", 4, "INBOX"),
                ),
            ),
        )
        val listed = listOf(repo.message("i", "INBOX/2")!!, repo.message("i", "INBOX/4")!!)
        val found = repo.conversationsOf(listed)
        assertEquals(listOf("Sent/1", "INBOX/2"), found.getValue(conversationKey(listed[0])).map { it.id })
        // A message without a subject has no conversation to look up.
        assertEquals(setOf(conversationKey(listed[0])), found.keys)
    }
}
