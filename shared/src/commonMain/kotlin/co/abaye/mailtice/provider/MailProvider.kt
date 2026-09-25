package co.abaye.mailtice.provider

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage

/** [color] is the label's "#rrggbb" background where the provider has one (Gmail), "" otherwise. */
data class RemoteFolder(val id: String, val name: String, val role: FolderRole, val color: String = "")

/** A message as the server reports it, ready to insert. */
data class RemoteMessage(
    val id: String,
    val threadId: String,
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val toLine: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val unread: Boolean,
    val flagged: Boolean,
    val hasAttachments: Boolean,
    val sizeBytes: Long,
    val folderIds: Set<String>,
    val body: MailBody?,
)

/** A message to send. Addresses are already validated; [bcc] never appears in the sent headers. */
data class OutgoingMail(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val text: String,
    /** Message-ID of the message being answered, so every client threads the reply. */
    val inReplyTo: String? = null,
    val references: String? = null,
    /** Gmail conversation id: keeps a reply in the original thread there. */
    val threadId: String? = null,
)

/** One downloaded attachment. [index] is its position in [co.abaye.mailtice.domain.MailBody.attachments]. */
class AttachmentFile(val index: Int, val name: String, val bytes: ByteArray)

/** The threading headers of a stored message; null when the server did not say. */
data class ThreadHeaders(val messageId: String? = null, val references: String? = null)

data class FlagChange(val messageId: String, val unread: Boolean, val flagged: Boolean)

data class FolderLinkChange(val messageId: String, val folderId: String, val linked: Boolean)

/** IMAP bookkeeping per folder; Gmail keeps its cursor on the account instead. */
data class FolderState(val folderId: String, val uidValidity: Long, val uidNext: Long, val highestModSeq: Long)

/** Everything one sync round learned. Applied to the database in a single transaction. */
data class SyncBatch(
    val newMessages: List<RemoteMessage> = emptyList(),
    val flagChanges: List<FlagChange> = emptyList(),
    val linkChanges: List<FolderLinkChange> = emptyList(),
    val deletedIds: Set<String> = emptySet(),
    /** Folders to empty locally before applying (IMAP UIDVALIDITY changed, or a first sync). */
    val resetFolders: Set<String> = emptySet(),
    val folderStates: List<FolderState> = emptyList(),
    /** New Gmail historyId; null = unchanged. */
    val cursor: String? = null,
    /** First sync of the account: nothing in it may notify. */
    val initial: Boolean = false,
    /** Only part of the work fit in this round (a large first sync); run the next one right away. */
    val more: Boolean = false,
)

/**
 * One implementation per protocol. Implementations never touch the database - they take what is
 * stored and return what changed - so the same code runs in the desktop loop, the Android app and
 * the Android background worker.
 */
interface MailProvider {
    suspend fun capabilities(account: Account): Capabilities

    suspend fun listFolders(account: Account): List<RemoteFolder>

    /**
     * Brings [folders] (the ones with sync on) up to date for messages received after [sinceMillis]
     * (null = everything). [knownIds] are the messages already stored, so bodies are only fetched
     * for what is new.
     */
    suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch

    suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean)

    /** Only called when [Capabilities.archive] is true. */
    suspend fun archive(account: Account, message: MailMessage, folders: List<Folder>)

    /** Only called when [Capabilities.trash] is true. Moves the message to the trash; never deletes outright. */
    suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>)

    /** Only called when [Capabilities.send] is true. */
    suspend fun send(account: Account, mail: OutgoingMail, folders: List<Folder>)

    /**
     * Downloads the attachments of [message] whose positions are in [indices] (null = all), in the
     * order the body parser lists them.
     */
    suspend fun fetchAttachments(account: Account, message: MailMessage, indices: Set<Int>?): List<AttachmentFile>

    /** The message exactly as the server holds it (RFC 5322), for .eml / .mbox export. */
    suspend fun rawMessage(account: Account, message: MailMessage): ByteArray

    /** Message-ID and References of [message], read when a reply is started. */
    suspend fun threadHeaders(account: Account, message: MailMessage): ThreadHeaders

    /** Fetches a body the sync did not store (e.g. after the cache was cleared). */
    suspend fun fetchBody(account: Account, message: MailMessage): MailBody

    /** Releases connections held for [account]. */
    suspend fun close(account: Account) = Unit
}

/** Transport failures, split by what the caller should do about them. */
sealed class ProviderException(message: String) : Exception(message) {
    /** 401 / auth failure on a request: refresh once and retry. */
    class Unauthorized(message: String) : ProviderException(message)

    /** Gmail 404 on history (cursor too old), or an IMAP folder that vanished. Resync. */
    class NotFound(message: String) : ProviderException(message)

    /** 429 / 5xx / network: back off and retry. */
    class Transient(message: String) : ProviderException(message)

    /** Other 4xx: a bug in the request. */
    class Client(message: String) : ProviderException(message)

    /**
     * The server refused to send with the credentials we hold. For Microsoft accounts added before
     * sending existed this means the SMTP.Send permission is missing: signing in again grants it.
     */
    class SendNotAllowed(message: String) : ProviderException(message)

    companion object {
        fun of(status: Int, body: String): ProviderException {
            val msg = "HTTP $status: ${body.take(300)}"
            return when {
                status == 401 -> Unauthorized(msg)
                status == 404 -> NotFound(msg)
                status == 429 || status >= 500 -> Transient(msg)
                else -> Client(msg)
            }
        }
    }
}

/** Picks the implementation for an account kind. The IMAP one lives in the JVM-shared source set. */
fun interface MailProviders {
    fun forAccount(account: Account): MailProvider
}
