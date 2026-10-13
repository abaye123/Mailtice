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
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * A message stored here that the server no longer has (deleted for good, its trash emptied) used
 * to stay forever: every action got a 404 and put the row back, and an incremental sync never
 * mentions it again. Each action now drops the stale copy instead.
 */
class GoneMessageTest {
    private val account = Account("acc", ProviderKind.Gmail, "a@example.com", capabilities = Capabilities.Gmail)
    private val folders = listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("TRASH", "Trash", FolderRole.Trash))

    /** Knows the folders, and no message at all. */
    private class EmptyServer : MailProvider by DemoMailProvider() {
        override suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean) =
            throw ProviderException.NotFound("Requested entity was not found.")

        override suspend fun archive(account: Account, message: MailMessage, folders: List<Folder>) =
            throw ProviderException.NotFound("Requested entity was not found.")

        override suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>) =
            throw ProviderException.NotFound("Requested entity was not found.")
    }

    private fun stale(): Pair<MailRepository, SyncEngine> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(account)
        repo.mergeFolders("acc", folders)
        val now = System.currentTimeMillis()
        val message = RemoteMessage("m1", "m1", 0, "", "", "", "Hi", "", now, true, false, false, 0, setOf("INBOX"), null)
        repo.applyBatch("acc", SyncBatch(newMessages = listOf(message)))
        return repo to SyncEngine(repo, providers = { EmptyServer() })
    }

    @Test
    fun readingAMessageTheServerLostDropsIt() {
        val (repo, engine) = stale()
        runBlocking { engine.setRead(repo.message("acc", "m1")!!, read = true) }
        assertNull(repo.message("acc", "m1"))
    }

    @Test
    fun archivingAMessageTheServerLostDropsIt() {
        val (repo, engine) = stale()
        runBlocking { engine.archive(repo.message("acc", "m1")!!) }
        assertNull(repo.message("acc", "m1"))
    }

    @Test
    fun deletingAMessageTheServerLostDropsIt() {
        val (repo, engine) = stale()
        runBlocking { engine.trash(repo.message("acc", "m1")!!) }
        assertNull(repo.message("acc", "m1"))
    }
}
