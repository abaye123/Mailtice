package co.abaye.mailtice.dev

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.AttachmentFile
import co.abaye.mailtice.provider.MailProvider
import co.abaye.mailtice.provider.MimeBuilder
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.provider.ThreadHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A new message arrives every this many sync rounds per account (Refresh counts as a round). */
private const val ROUNDS_PER_INCOMING = 3

/** Enough to see the "syncing" state flash by without slowing tests down. */
private const val SYNC_LATENCY_MS = 350L

/**
 * An in-memory mail server for demo mode. It behaves like the real providers from the sync
 * engine's point of view - folders, first sync, incremental rounds, read and archive - so the
 * whole pipeline (database, notifications, capabilities) runs for real without any network.
 *
 * Mailboxes are built lazily from [DemoAccounts.mailFor] the first time an account is seen and
 * live only for the process, matching the demo data directory that is wiped on every launch.
 */
class DemoMailProvider(private val clock: () -> Long = { Platform.now() }) : MailProvider {
    private val mutex = Mutex()
    private val mailboxes = mutableMapOf<String, MutableMap<String, RemoteMessage>>()
    private val rounds = mutableMapOf<String, Int>()
    private var nextUid = 1_000L
    private var incomingIndex = 0

    override suspend fun capabilities(account: Account): Capabilities =
        if (account.kind == ProviderKind.Gmail) Capabilities.Gmail else DemoAccounts.imapCapabilities()

    override suspend fun listFolders(account: Account): List<RemoteFolder> =
        DemoFolders.of(account.kind).map { RemoteFolder(folderId(account, it.key), it.name, it.role) }

    override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch {
        delay(SYNC_LATENCY_MS)
        return mutex.withLock {
            val box = mailbox(account)
            val round = (rounds[account.id] ?: 0) + 1
            rounds[account.id] = round
            // The first round only loads the fixture; later rounds occasionally deliver something new.
            if (round > 1 && round % ROUNDS_PER_INCOMING == 0) deliverIncoming(account, box)

            val synced = folders.map { it.id }.toSet()
            val fresh = box.values.filter { m ->
                m.id !in knownIds && m.folderIds.any { it in synced } && (sinceMillis == null || m.receivedAt >= sinceMillis)
            }
            SyncBatch(newMessages = fresh, initial = knownIds.isEmpty())
        }
    }

    override suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean) {
        mutex.withLock {
            val box = mailbox(account)
            box[message.id]?.let { box[message.id] = it.copy(unread = !read) }
        }
    }

    override suspend fun archive(account: Account, message: MailMessage, folders: List<Folder>) {
        mutex.withLock {
            val box = mailbox(account)
            val stored = box[message.id] ?: return@withLock
            val inbox = folderId(account, DemoFolders.INBOX)
            if (account.kind == ProviderKind.Gmail) {
                // Gmail archive = drop the INBOX label; the message keeps its other labels.
                box[message.id] = stored.copy(folderIds = stored.folderIds - inbox)
            } else {
                // IMAP archive = MOVE: the old "<folder>/<uid>" id disappears and a copy appears in Archive.
                box.remove(message.id)
                val uid = nextUid++
                val archive = folderId(account, DemoFolders.ARCHIVE)
                box["$archive/$uid"] = stored.copy(id = "$archive/$uid", uid = uid, unread = false, folderIds = setOf(archive))
            }
        }
    }

    override suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>) {
        mutex.withLock {
            val box = mailbox(account)
            val stored = box[message.id] ?: return@withLock
            val trash = folderId(account, DemoFolders.TRASH)
            if (account.kind == ProviderKind.Gmail) {
                box[message.id] = stored.copy(folderIds = setOf(trash))
            } else {
                box.remove(message.id)
                val uid = nextUid++
                box["$trash/$uid"] = stored.copy(id = "$trash/$uid", uid = uid, folderIds = setOf(trash))
            }
        }
    }

    /** "Sends" into the Sent folder, so the result can be checked by syncing that folder. */
    override suspend fun send(account: Account, mail: OutgoingMail, folders: List<Folder>) {
        delay(SYNC_LATENCY_MS)
        mutex.withLock {
            val box = mailbox(account)
            val sent = DemoMail(
                fromName = account.displayName,
                fromAddress = account.email,
                subject = mail.subject,
                text = mail.text,
                minutesAgo = 0,
                folders = listOf(DemoFolders.SENT),
            )
            val message = remote(account, sent, receivedAt = clock()).copy(toLine = mail.to.joinToString(", "))
            box[message.id] = message
        }
        println("Demo send: ${mail.to.size} to, ${mail.cc.size} cc, ${mail.bcc.size} bcc, reply=${mail.inReplyTo != null}")
    }

    /** Small text files standing in for the real content, named like the fixture attachments. */
    override suspend fun fetchAttachments(account: Account, message: MailMessage, indices: Set<Int>?): List<AttachmentFile> {
        delay(SYNC_LATENCY_MS)
        val attachments = mutex.withLock { mailbox(account)[message.id]?.body?.attachments }.orEmpty()
        return attachments.withIndex().filter { indices == null || it.index in indices }.map { (i, a) ->
            AttachmentFile(i, a.name, "Mailtice demo attachment: ${a.name}\nFrom: ${message.sender}\n".encodeToByteArray())
        }
    }

    override suspend fun rawMessage(account: Account, message: MailMessage): ByteArray {
        val text = mutex.withLock { mailbox(account)[message.id]?.body?.text } ?: message.snippet
        val mail = OutgoingMail(to = listOf(account.email), subject = message.subject, text = text)
        return MimeBuilder.build("${message.fromName} <${message.fromAddress}>", mail, message.receivedAt).encodeToByteArray()
    }

    override suspend fun threadHeaders(account: Account, message: MailMessage): ThreadHeaders =
        ThreadHeaders(messageId = "<${message.id}@demo.mailtice>")

    override suspend fun fetchBody(account: Account, message: MailMessage): MailBody =
        mutex.withLock { mailbox(account)[message.id]?.body } ?: MailBody(message.snippet, "", emptyList())

    override suspend fun close(account: Account) {
        mutex.withLock {
            mailboxes.remove(account.id)
            rounds.remove(account.id)
        }
    }

    // ---- fixture ----------------------------------------------------------------------------

    private fun mailbox(account: Account): MutableMap<String, RemoteMessage> = mailboxes.getOrPut(account.id) {
        val now = clock()
        DemoAccounts.mailFor(account.id)
            .map { remote(account, it, receivedAt = now - it.minutesAgo * 60_000L) }
            .associateByTo(mutableMapOf()) { it.id }
    }

    private fun deliverIncoming(account: Account, box: MutableMap<String, RemoteMessage>) {
        // The quiet account stays empty, so its "inbox zero" screen can be tested.
        if (account.id == DemoAccounts.QUIET_ID) return
        val mail = incomingMail[incomingIndex++ % incomingMail.size]
        val message = remote(account, mail, receivedAt = clock())
        box[message.id] = message
    }

    private fun remote(account: Account, mail: DemoMail, receivedAt: Long): RemoteMessage {
        val uid = nextUid++
        val folderIds = mail.folders.map { folderId(account, it) }.toSet()
        // Gmail ids are opaque; IMAP ids are "<folder>/<uid>" like the real backend.
        val id = if (account.kind == ProviderKind.Gmail) "demo${uid.toString(16)}" else "${folderIds.first()}/$uid"
        return RemoteMessage(
            id = id,
            threadId = id,
            uid = uid,
            fromName = mail.fromName,
            fromAddress = mail.fromAddress,
            toLine = account.email,
            subject = mail.subject,
            snippet = mail.text.take(160),
            receivedAt = receivedAt,
            unread = mail.unread,
            flagged = mail.flagged,
            hasAttachments = mail.attachments.isNotEmpty(),
            sizeBytes = mail.text.length * 2L + mail.attachments.sumOf { it.size },
            folderIds = folderIds,
            body = MailBody(text = mail.text, html = if (mail.html) htmlFor(mail) else "", attachments = mail.attachments),
        )
    }

    private fun htmlFor(mail: DemoMail): String =
        "<div dir=\"auto\" style=\"font-family:sans-serif;max-width:560px\">" +
            "<h2 style=\"color:#0038B8\">${mail.subject.escapeHtml()}</h2>" +
            "<p>${mail.text.escapeHtml()}</p>" +
            "<p style=\"color:#747685;font-size:12px\">Mailtice demo message</p></div>"

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Gmail: system labels are upper case ("INBOX"), user labels "Label_…". IMAP: the folder name. */
    private fun folderId(account: Account, key: String): String = when {
        account.kind != ProviderKind.Gmail -> key
        key == DemoFolders.INBOX || key == DemoFolders.SENT -> key
        else -> "Label_$key"
    }
}
