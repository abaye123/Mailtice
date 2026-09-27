package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.dev.DemoMailProvider
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.MailProvider
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.sync.ActionQueuedException
import co.abaye.mailtice.sync.OfflinePrefs
import co.abaye.mailtice.sync.SyncEngine
import co.abaye.mailtice.sync.isNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineModeTest {
    private val account = Account("acc", ProviderKind.Gmail, "a@example.com", retentionDays = 30)

    /** A server that can be cut off; it records what reached it. */
    private class Server : MailProvider by DemoMailProvider() {
        var online = false
        val reads = mutableListOf<String>()
        var sinceSeen: Long? = -1
        override suspend fun listFolders(account: Account) = listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox))
        override suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean) {
            if (!online) throw UnknownHostException("gmail.googleapis.com")
            reads += "${message.id}:$read"
        }
        override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch {
            if (!online) throw IOException("no route")
            sinceSeen = sinceMillis
            return SyncBatch()
        }
        override suspend fun fetchBody(account: Account, message: MailMessage) = MailBody("body of ${message.id}", "", emptyList())
    }

    @Test
    fun actionsWaitForTheConnectionAndBodiesComeAhead() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(account)
        repo.mergeFolders("acc", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox)))
        val msg = RemoteMessage("m1", "m1", 0, "", "", "", "Hi", "", System.currentTimeMillis(), true, false, false, 0, setOf("INBOX"), null)
        repo.applyBatch("acc", SyncBatch(newMessages = listOf(msg)))
        val server = Server()
        val engine = SyncEngine(repo, providers = { server })
        val scope = CoroutineScope(Dispatchers.Unconfined)
        engine.reconcile(scope, emptyList(), 60, smart = false, offline = OfflinePrefs(enabled = true, attachmentLimitBytes = 0))

        runBlocking {
            val message = repo.message("acc", "m1")!!
            // Offline: the read sticks here and is queued.
            assertFailsWith<ActionQueuedException> { engine.setRead(message, read = true) }
            assertFalse(repo.message("acc", "m1")!!.unread)
            assertTrue(server.reads.isEmpty())

            // Back online: the queued read goes first, the whole mailbox is asked for, the body comes ahead.
            server.online = true
            engine.syncOnce(account)
            assertEquals(listOf("m1:true"), server.reads)
            assertEquals(null, server.sinceSeen)
            assertEquals("body of m1", repo.body("acc", "m1")?.text)
            assertTrue(repo.pending("acc").isEmpty())
        }
        scope.cancel()
    }

    @Test
    fun networkErrorsAreToldApart() {
        assertTrue(isNetworkError(UnknownHostException("x")))
        assertTrue(isNetworkError(RuntimeException("wrapped", java.net.SocketTimeoutException())))
        assertFalse(isNetworkError(IllegalStateException("bad request")))
    }
}
