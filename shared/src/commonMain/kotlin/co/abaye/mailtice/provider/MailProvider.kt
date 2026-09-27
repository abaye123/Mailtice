package co.abaye.mailtice.provider

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.LabelColors
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.search.MailSearch

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

/** A file attached to an outgoing message. */
class OutgoingAttachment(val name: String, val mimeType: String, val bytes: ByteArray)

/** A message to send. Addresses are already validated; [bcc] never appears in the sent headers. */
data class OutgoingMail(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    /** The plain-text version; always present (clients without HTML, previews, search). */
    val text: String,
    /** The formatted version from the editor; null = plain text only. */
    val html: String? = null,
    val attachments: List<OutgoingAttachment> = emptyList(),
    /** Message-ID of the message being answered, so every client threads the reply. */
    val inReplyTo: String? = null,
    val references: String? = null,
    /** Gmail conversation id: keeps a reply in the original thread there. */
    val threadId: String? = null,
)

/** One downloaded attachment. [index] is its position in [co.abaye.mailtice.domain.MailBody.attachments]. */
class AttachmentFile(val index: Int, val name: String, val bytes: ByteArray)

/**
 * A look at the server beyond what is stored: messages received before [before] (epoch millis, null
 * = now) matching the list's filters, newest first, at most [limit].
 */
data class OlderQuery(
    val before: Long?,
    /** The user's search (words, from:, subject:...), applied on the server. */
    val search: MailSearch = MailSearch(),
    val unreadOnly: Boolean = false,
    val attachmentsOnly: Boolean = false,
    val flaggedOnly: Boolean = false,
    val limit: Int = 50,
)

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

    /**
     * Messages in [folders] older than what is stored, straight from the server and without bodies
     * (fetched when opened). Nothing here is written to the database: the disk keeps only the
     * retention window, older mail is only looked at. [folders] empty with [OlderQuery.flaggedOnly]
     * means starred mail anywhere.
     */
    suspend fun olderMessages(account: Account, folders: List<Folder>, query: OlderQuery): List<RemoteMessage>

    /** The message exactly as the server holds it (RFC 5322), for .eml / .mbox export. */
    suspend fun rawMessage(account: Account, message: MailMessage): ByteArray

    /**
     * Only called when [Capabilities.drafts] is true. Saves [mail] as a draft on the server, replacing
     * [previous] (a handle this returned earlier) when given, and returns the handle of the new one.
     */
    suspend fun saveDraft(account: Account, mail: OutgoingMail, folders: List<Folder>, previous: String?): String

    /** Removes a draft saved by [saveDraft] (the message was sent, scheduled or discarded). */
    suspend fun deleteDraft(account: Account, handle: String)

    /** The [saveDraft] handle of a draft already on the server, opened from the Drafts folder; null if unknown. */
    suspend fun draftHandle(account: Account, message: MailMessage): String?

    /** Message-ID and References of [message], read when a reply is started. */
    suspend fun threadHeaders(account: Account, message: MailMessage): ThreadHeaders

    /** Fetches a body the sync did not store (e.g. after the cache was cleared). */
    suspend fun fetchBody(account: Account, message: MailMessage): MailBody

    /** Releases connections held for [account]. */
    suspend fun close(account: Account) = Unit

    /** A new label (Gmail) or folder (IMAP) named [name]. Only when [Capabilities.manageLabels]. */
    suspend fun createLabel(account: Account, name: String, color: LabelColors?): Unit =
        throw UnsupportedOperationException("Labels cannot be managed here")

    /**
     * Renames and recolours label [id] ([color] null = no colour). Returns its id afterwards, which
     * changes with the name on IMAP, where the folder's name is its id.
     */
    suspend fun updateLabel(account: Account, id: String, name: String, color: LabelColors?): String =
        throw UnsupportedOperationException("Labels cannot be managed here")

    /** Deletes label [id]. On Gmail the messages stay; on IMAP the folder goes with its mail. */
    suspend fun deleteLabel(account: Account, id: String): Unit = throw UnsupportedOperationException("Labels cannot be managed here")
}

private val RATE_LIMIT_REASONS = listOf("rateLimitExceeded", "userRateLimitExceeded", "RATE_LIMIT_EXCEEDED", "quotaExceeded")

/** Transport failures, split by what the caller should do about them. */
sealed class ProviderException(message: String) : Exception(message) {
    /** 401 / auth failure on a request: refresh once and retry. */
    class Unauthorized(message: String) : ProviderException(message)

    /** Gmail 404 on history (cursor too old), or an IMAP folder that vanished. Resync. */
    class NotFound(message: String) : ProviderException(message)

    /** 429 / 5xx / a rate limit sent as 403 / network: back off and retry. */
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
                // Gmail reports its per-user quota as 403 rateLimitExceeded as often as 429.
                status == 403 && RATE_LIMIT_REASONS.any { it in body } -> Transient(msg)
                else -> Client(msg)
            }
        }
    }
}

/** Picks the implementation for an account kind. The IMAP one lives in the JVM-shared source set. */
fun interface MailProviders {
    fun forAccount(account: Account): MailProvider
}
