package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.dev.DemoMailProvider
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.MailProvider
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.sync.SyncEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrashRaceTest {
    private val account = Account("acc", ProviderKind.Gmail, "a@example.com", capabilities = Capabilities.Gmail)
    private val folders = listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("TRASH", "Trash", FolderRole.Trash))

    private fun remote(folder: String) =
        RemoteMessage("m1", "m1", 0, "", "", "", "Hi", "", System.currentTimeMillis(), false, false, false, 0, setOf(folder), null)

    /** A server whose next round can be held after it has read the mailbox, before it answers. */
    private inner class Server : MailProvider by DemoMailProvider() {
        var answer = SyncBatch()
        var hold: CompletableDeferred<Unit>? = null
        val reading = CompletableDeferred<Unit>()
        override suspend fun capabilities(account: Account) = Capabilities.Gmail
        override suspend fun listFolders(account: Account) = folders
        override suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>) {
            answer = SyncBatch(newMessages = listOf(remote("TRASH")))
        }
        override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch {
            val read = answer
            reading.complete(Unit)
            hold?.await()
            return read
        }
    }

    @Test
    fun aRoundThatReadTheServerBeforeTheTrashDoesNotBringTheMessageBack() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(account)
        repo.mergeFolders("acc", folders)
        repo.applyBatch("acc", SyncBatch(newMessages = listOf(remote("INBOX"))))
        val server = Server()
        val engine = SyncEngine(repo, providers = { server })

        runBlocking {
            // The round reads the server (m1 still in the inbox there), then the trash goes through.
            server.answer = SyncBatch(newMessages = listOf(remote("INBOX")))
            server.hold = CompletableDeferred()
            val round = async(Dispatchers.Default) { engine.syncOnce(account) }
            server.reading.await()
            engine.trash(repo.message("acc", "m1")!!)
            server.hold!!.complete(Unit)
            round.await()
            assertNull(repo.message("acc", "m1"), "the stale round put the trashed message back")

            // The next round reads the server after the trash: the message lands in the trash.
            server.hold = null
            engine.syncOnce(account)
            assertEquals(setOf("TRASH"), repo.folderIdsOf("acc", "m1").toSet())
        }
    }
}
