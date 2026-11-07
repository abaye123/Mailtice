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
import co.abaye.mailtice.sync.OfflineFiles
import co.abaye.mailtice.sync.SyncEngine
import co.abaye.mailtice.transfer.MailExporter
import co.abaye.mailtice.transfer.MailImporter
import co.abaye.mailtice.transfer.TransferError
import co.abaye.mailtice.transfer.TransferException
import co.abaye.mailtice.transfer.TransferSummary
import co.abaye.mailtice.transfer.ZipSink
import co.abaye.mailtice.transfer.ZipSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    /** Files on "disk", by path. */
    private val files = mutableMapOf<String, ByteArray>()

    private fun export(source: MailRepository, accounts: List<Account>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipSink(out).use { sink ->
            MailExporter(source, readFile = { files[it] }, sizeOf = { files[it]?.size?.toLong() ?: 0L }).write(sink, accounts, "test", 1L)
        }
        return out.toByteArray()
    }

    private fun import(target: MailRepository, bytes: ByteArray): TransferSummary {
        var n = 0
        return ZipSource(ByteArrayInputStream(bytes)).use { source ->
            MailImporter(target, newId = {
                "new-${++n}"
            }, pickColor = { AccountColor.Blue }, writeFile = { path, data -> files[path] = data })
                .read(source)
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
        files[OfflineFiles.attachment(gmail.id, "m1", 0)] = byteArrayOf(1, 2, 3, 4)
        return source
    }

    @Test
    fun anExportComesBackWholeAsAReadOnlyCopy() {
        val bytes = export(stocked(), listOf(gmail))
        files.clear()
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
        assertContentEquals(byteArrayOf(1, 2, 3, 4), files[OfflineFiles.attachment(copy.id, "m1", 0)])
        assertNotNull(files[OfflineFiles.done(copy.id, "m1")])
        assertEquals(setOf("INBOX", "Label_7"), target.folderIdsOf(copy.id, "m2").toSet())
        // Imported mail is old news: nothing to announce.
        assertTrue(target.toNotify(copy.id).isEmpty())

        // The same file again changes nothing.
        import(target, bytes)
        assertEquals(1, target.accountsNow().size)
        assertEquals(2L, target.messageCount(copy.id))
    }

    @Test
    fun aConnectedAccountOnlyGainsWhatItLacked() {
        val bytes = export(stocked(), listOf(gmail))
        val target = repo()
        val here = gmail.copy(id = "local")
        target.addAccount(here)
        target.mergeFolders(here.id, folders)
        // Read here already; the file still has it unread.
        target.applyBatch(here.id, SyncBatch(newMessages = listOf(message("m1", 1_000, unread = false, folders = setOf("INBOX")))))

        val summary = import(target, bytes)
        assertEquals(0, summary.newAccounts)
        assertFalse(target.account("local")!!.fileOnly)
        assertFalse(target.message("local", "m1")!!.unread)
        assertNotNull(target.message("local", "m2"))
        assertEquals("Hello", target.body("local", "m1")?.text)
    }

    @Test
    fun theExtensionsGmailMessagesReadLikeTheSync() {
        val text = Base64.getUrlEncoder().withoutPadding().encodeToString("Shalom".toByteArray())
        val from = """{"name":"From","value":"\"Levi, Moshe\" <moshe@example.com>"}"""
        val textPart = """{"mimeType":"text/plain","headers":[{"name":"Content-Type","value":"text/plain; charset=utf-8"}],""" +
            """"body":{"size":6,"data":"$text"}}"""
        val filePart = """{"mimeType":"application/pdf","filename":"a.pdf","body":{"size":3,"attachmentId":"att"}}"""
        val payload = """{"mimeType":"multipart/mixed","headers":[$from,{"name":"Subject","value":"Hi"}],"parts":[$textPart,$filePart]}"""
        val lines = listOf(
            """{"n":0,"files":[0],"gmail":{"id":"x1","threadId":"tx","labelIds":["INBOX","UNREAD","STARRED"],""" +
                """"snippet":"a &amp; b","internalDate":"5000","sizeEstimate":10,"payload":$payload}}""",
            // Archived: in no label at all.
            """{"n":1,"gmail":{"id":"x2","threadId":"x2","labelIds":["IMPORTANT"],"snippet":"","internalDate":"6000",""" +
                """"payload":{"mimeType":"text/plain","body":{"size":0}}}}""",
        )
        val out = ByteArrayOutputStream()
        ZipSink(out).use { sink ->
            sink.put(
                "manifest.json",
                """{"format":"mailtice-transfer","version":1,"generator":"Mailtice extension","accounts":["a1"]}""".toByteArray(),
            )
            val account =
                """{"key":"a1","kind":"Gmail","email":"moshe@example.com","gmailLabels":[{"id":"INBOX","name":"INBOX","type":"system"},""" +
                    """{"id":"UNREAD","name":"UNREAD","type":"system"},{"id":"Label_1","name":"Work","type":"user","color":{"backgroundColor":"#4a86e8"}}]}"""
            sink.put("accounts/a1/account.json", account.toByteArray())
            sink.put("accounts/a1/messages/00000.jsonl", lines.joinToString("\n").toByteArray())
            sink.put("accounts/a1/files/0/0", byteArrayOf(9, 9, 9), compress = false)
        }
        val target = repo()
        val summary = import(target, out.toByteArray())
        assertEquals(2, summary.messages)
        val account = target.accountsNow().single()
        assertEquals(setOf("INBOX", "Label_1", MailImporter.ARCHIVE_ID), target.foldersNow(account.id).map { it.id }.toSet())
        assertEquals(FolderRole.Archive, target.foldersNow(account.id).first { it.id == MailImporter.ARCHIVE_ID }.role)
        val x1 = assertNotNull(target.message(account.id, "x1"))
        assertEquals("Levi, Moshe", x1.fromName)
        assertEquals("moshe@example.com", x1.fromAddress)
        assertEquals("a & b", x1.snippet)
        assertTrue(x1.unread && x1.flagged && x1.hasAttachments)
        assertEquals(5000L, x1.receivedAt)
        assertEquals("Shalom", target.body(account.id, "x1")?.text)
        assertContentEquals(byteArrayOf(9, 9, 9), files[OfflineFiles.attachment(account.id, "x1", 0)])
        assertEquals(listOf(MailImporter.ARCHIVE_ID), target.folderIdsOf(account.id, "x2"))
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
            ZipSink(out).use { sink -> entries.forEach { (name, text) -> sink.put(name, text.toByteArray()) } }
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
