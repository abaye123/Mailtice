package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.sync.OfflinePaths
import co.abaye.mailtice.sync.SyncEngine
import co.abaye.mailtice.transfer.MailExporter
import co.abaye.mailtice.transfer.MailImporter
import co.abaye.mailtice.transfer.TransferCrypto
import co.abaye.mailtice.transfer.TransferError
import co.abaye.mailtice.transfer.TransferException
import co.abaye.mailtice.transfer.TransferSummary
import co.abaye.mailtice.transfer.zipSink
import co.abaye.mailtice.transfer.zipSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The transfer file: what the app exports comes back whole on the other side, and the extension's Gmail format reads the same way. */
class TransferFileTest {
    private val gmail =
        Account("g1", ProviderKind.Gmail, "dana@example.com", label = "Dana", color = AccountColor.Green, capabilities = Capabilities.Gmail)
    private val folders = listOf(
        RemoteFolder("INBOX", "Inbox", FolderRole.Inbox),
        RemoteFolder("Label_7", "Family", FolderRole.Other, "#16a766"),
    )

    private fun repo(): MailRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        return MailRepository(driver, Dispatchers.Unconfined)
    }

    private fun message(id: String, at: Long, unread: Boolean, folders: Set<String>, body: MailBody? = null) = RemoteMessage(
        id, "t-$id", 0, "Dana Cohen", "dana@example.com", "me@example.com", "Subject $id", "snippet $id", at, unread, false,
        body != null && body.attachments.isNotEmpty(), 1000, folders, body,
    )

    /** Offline attachments in a folder of the test's own, never the real app folder. */
    private val root: File = Files.createTempDirectory("transfer-test").toFile().apply { deleteOnExit() }
    private val paths = object : OfflinePaths {
        override fun attachment(accountId: String, messageId: String, index: Int) = "${root.path}/$accountId/$messageId/$index"

        override fun done(accountId: String, messageId: String) = "${root.path}/$accountId/$messageId/done"
    }

    private fun exists(path: String) = File(path).isFile

    private fun export(source: MailRepository, accounts: List<Account>, password: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        zipSink(out, password).use { sink ->
            MailExporter(source, paths, sizeOf = { File(it).length() }).write(sink, accounts, "test", 0)
        }
        return out.toByteArray()
    }

    private fun import(target: MailRepository, bytes: ByteArray, password: String? = null): TransferSummary {
        var n = 0
        return zipSource(ByteArrayInputStream(bytes), password).use { source ->
            MailImporter(target, newId = { "new-${++n}" }, pickColor = { AccountColor.Blue }, paths = paths, writeFile = { path, data ->
                File(path).apply { parentFile.mkdirs() }.writeBytes(data)
            }).read(source)
        }
    }

    private fun stocked(): MailRepository {
        val source = repo()
        source.addAccount(gmail)
        source.mergeFolders(gmail.id, folders)
        val body = MailBody("Hello", "<p>Hello</p>", listOf(Attachment("plan.pdf", 4, "", "application/pdf")))
        source.applyBatch(
            gmail.id,
            SyncBatch(
                newMessages = listOf(
                    message("m1", 1_000, unread = true, folders = setOf("INBOX"), body = body),
                    message("m2", 2_000, unread = false, folders = setOf("INBOX", "Label_7")),
                ),
                initial = true,
            ),
        )
        File(paths.attachment(gmail.id, "m1", 0)).apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3, 4))
        return source
    }

    @Test
    fun anExportComesBackWholeAsAReadOnlyCopy() {
        val bytes = export(stocked(), listOf(gmail))
        root.deleteRecursively()
        val target = repo()
        val summary = import(target, bytes)

        assertEquals(TransferSummary(accounts = 1, messages = 2, files = 1, newAccounts = 1), summary)
        val copy = target.accountsNow().single()
        assertTrue(copy.fileOnly)
        assertEquals("dana@example.com", copy.email)
        assertEquals("Dana", copy.label)
        assertEquals(0, copy.retentionDays)
        assertFalse(copy.capabilities.send)
        assertEquals(setOf("INBOX", "Label_7"), target.foldersNow(copy.id).map { it.id }.toSet())
        assertEquals("#16a766", target.foldersNow(copy.id).first { it.id == "Label_7" }.color)

        val m1 = assertNotNull(target.message(copy.id, "m1"))
        assertTrue(m1.unread)
        assertEquals("Dana Cohen", m1.fromName)
        val body = assertNotNull(target.body(copy.id, "m1"))
        assertEquals("<p>Hello</p>", body.html)
        assertEquals("plan.pdf", body.attachments.single().name)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), File(paths.attachment(copy.id, "m1", 0)).readBytes())
        assertTrue(exists(paths.done(copy.id, "m1")))
        assertEquals(setOf("INBOX", "Label_7"), target.folderIdsOf(copy.id, "m2").toSet())
        // Imported mail is old news: nothing to announce.
        assertTrue(target.toNotify(copy.id).isEmpty())

        // The same file again changes nothing.
        import(target, bytes)
        assertEquals(1, target.accountsNow().size)
        assertEquals(2L, target.messageCount(copy.id))
    }

    @Test
    fun aConnectedAccountIsLeftAlone() {
        val bytes = export(stocked(), listOf(gmail))
        val target = repo()
        val here = gmail.copy(id = "local")
        target.addAccount(here)
        target.mergeFolders(here.id, folders)
        // Read here already; the file still has it unread, and has a message the server does not.
        target.applyBatch(
            here.id,
            SyncBatch(newMessages = listOf(message("m1", 1_000, unread = false, folders = setOf("INBOX"))), initial = true),
        )

        val summary = import(target, bytes)
        assertEquals(TransferSummary(accounts = 0, messages = 0, files = 0, newAccounts = 0, skippedAccounts = 1), summary)
        assertFalse(target.account("local")!!.fileOnly)
        assertFalse(target.message("local", "m1")!!.unread)
        assertNull(target.message("local", "m2"))
        assertFalse(exists(paths.attachment("local", "m1", 0)))
    }

    @Test
    fun aLongMailboxPagesThroughEveryMessageOnce() {
        val source = repo()
        source.addAccount(gmail)
        source.mergeFolders(gmail.id, folders)
        // Same timestamp across a page boundary: the paging must not lose or repeat any of them.
        val all = (1..450).map { message("m$it", (it / 100).toLong(), unread = false, folders = setOf("INBOX")) }
        source.applyBatch(gmail.id, SyncBatch(newMessages = all, initial = true))
        val target = repo()
        assertEquals(450, import(target, export(source, listOf(gmail))).messages)
        assertEquals(450L, target.messageCount(target.accountsNow().single().id))
    }

    @Test
    fun aPasswordProtectsTheFile() {
        val plain = export(stocked(), listOf(gmail))
        val sealed = export(stocked(), listOf(gmail), password = "correct horse")

        assertTrue(TransferCrypto.isEncrypted(ByteArrayInputStream(sealed)))
        assertFalse(TransferCrypto.isEncrypted(ByteArrayInputStream(plain)))
        // No trace of the mail in the bytes.
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("dana@example.com"))

        val wrong = assertFailsWith<TransferException> { import(repo(), sealed, password = "wrong") }
        assertEquals(TransferError.WrongPassword, wrong.error)

        val target = repo()
        assertEquals(2, import(target, sealed, password = "correct horse").messages)
        val copy = target.accountsNow().single()
        assertEquals("Hello", target.body(copy.id, "m1")?.text)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), File(paths.attachment(copy.id, "m1", 0)).readBytes())
    }

    @Test
    fun aDamagedSealedFileIsRefused() {
        val sealed = export(stocked(), listOf(gmail), password = "pw")
        val cut = sealed.copyOf(sealed.size - 20)
        assertFailsWith<TransferException> { import(repo(), cut, password = "pw") }
        val flipped = sealed.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 1).toByte() }
        assertFailsWith<TransferException> { import(repo(), flipped, password = "pw") }
    }

    @Test
    fun aLongStreamCrossesFramesIntact() {
        val data = ByteArray(5 * 1024 * 1024 + 123) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        TransferCrypto.encrypt(out, "pw").use { it.write(data) }
        val back = TransferCrypto.decrypt(ByteArrayInputStream(out.toByteArray()), "pw").use { it.readBytes() }
        assertContentEquals(data, back)
    }

    @Test
    fun aCopyFromAFileIsNeverSynced() {
        val target = repo()
        import(target, export(stocked(), listOf(gmail)))
        val engine = SyncEngine(target, providers = { error("a copy from a file must not reach a server") })
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            engine.reconcile(scope, target.accountsNow(), 60, smart = false)
            val copy = target.accountsNow().single()
            assertEquals(AccountStatus.FromFile, engine.statuses.value[copy.id])
            runBlocking {
                engine.setRead(target.message(copy.id, "m1")!!, read = true)
                assertEquals("Hello", engine.body(target.message(copy.id, "m1")!!).text)
                assertEquals("snippet m2", engine.body(target.message(copy.id, "m2")!!).text)
            }
            assertFalse(target.message(copy.id, "m1")!!.unread)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun otherFilesAreRefused() {
        fun zip(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { out ->
            zipSink(out, null).use { sink -> entries.forEach { (name, text) -> sink.put(name, text.toByteArray()) } }
        }.toByteArray()
        val notOurs = assertFailsWith<TransferException> { import(repo(), zip("readme.txt" to "hello")) }
        assertEquals(TransferError.NotATransferFile, notOurs.error)
        val newer = assertFailsWith<TransferException> {
            import(repo(), zip("manifest.json" to """{"format":"mailtice-transfer","version":99}"""))
        }
        assertEquals(TransferError.TooNew, newer.error)
        val garbage = assertFailsWith<TransferException> { import(repo(), "not a zip at all".toByteArray()) }
        assertEquals(TransferError.NotATransferFile, garbage.error)
    }
}
