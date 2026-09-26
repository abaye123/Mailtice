package co.abaye.mailtice.provider.gmail

import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.auth.Credential
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.provider.AttachmentFile
import co.abaye.mailtice.provider.FlagChange
import co.abaye.mailtice.provider.FolderLinkChange
import co.abaye.mailtice.provider.HtmlText
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.MailProvider
import co.abaye.mailtice.provider.MimeBuilder
import co.abaye.mailtice.provider.OlderQuery
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.provider.ThreadHeaders
import co.abaye.mailtice.provider.decodeText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val FETCH_PARALLELISM = 6

/** Above this many raw bytes a message is sent through the upload endpoint (the JSON body limit is ~5 MB). */
private const val JSON_SEND_LIMIT = 3_500_000

/** Metadata is small, so more of it can be in flight at once. */
private const val METADATA_PARALLELISM = 10

/** Messages stored per round of a first sync; the rest follow in the next rounds. */
private const val FULL_SYNC_PAGE = 300

/** Attempts per request on a rate limit (429) or a Gmail server error before the round gives up. */
private const val TRANSIENT_RETRIES = 4

/** Labels that are states, not places - never offered as folders. */
private val HIDDEN_LABELS = setOf("UNREAD", "STARRED", "IMPORTANT", "CHAT")

class GmailProvider(
    private val api: GmailApi,
    private val auth: AuthManager,
) : MailProvider {

    override suspend fun capabilities(account: Account): Capabilities = Capabilities.Gmail

    override suspend fun listFolders(account: Account): List<RemoteFolder> = withToken(account) { token ->
        api.labels(token)
            .filter { it.id !in HIDDEN_LABELS }
            .map { RemoteFolder(it.id, friendlyName(it), roleOf(it.id), it.color?.backgroundColor.orEmpty()) }
    }

    override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch =
        withToken(account) { token ->
            if (account.syncCursor.isEmpty()) {
                fullSync(token, account, folders, sinceMillis, knownIds)
            } else {
                try {
                    incremental(token, account, folders, sinceMillis, knownIds)
                } catch (e: ProviderException.NotFound) {
                    // History older than Gmail keeps (about a week): list everything again.
                    println("Gmail: history expired, full resync")
                    fullSync(token, account, folders, sinceMillis, knownIds)
                }
            }
        }

    /**
     * The first sync (or a resync). It lists what the synced labels hold, then stores it a page at a
     * time, inbox first and newest first, returning [SyncBatch.more] until everything is in - so a
     * big mailbox fills in steadily and a hiccup costs one page, not the whole download. Only
     * metadata is fetched here; a body is downloaded when the message is opened. The history cursor
     * is saved with the last page, which ends the first sync.
     */
    private suspend fun fullSync(token: String, account: Account, folders: List<Folder>, since: Long?, known: Set<String>): SyncBatch {
        val profile = retrying { api.profile(token) }
        val after = since?.let { it / 1000 }
        val membership = linkedMapOf<String, MutableSet<String>>()
        folders.sortedBy { if (it.role == FolderRole.Inbox) 0 else 1 }.forEach { folder ->
            retrying { api.messageIds(token, folder.id, after) }.forEach { ref -> membership.getOrPut(ref.id) { mutableSetOf() } += folder.id }
        }
        val unread = retrying { api.messageIds(token, LABEL_UNREAD, after) }.map { it.id }.toSet()
        val starred = retrying { api.messageIds(token, LABEL_STARRED, after) }.map { it.id }.toSet()
        // Metadata carries no MIME parts, so which messages have files comes from a search.
        val withFiles = retrying { api.messageIds(token, null, after, query = "has:attachment") }.map { it.id }.toSet()

        val fresh = membership.keys.filter { it !in known }
        val page = fresh.take(FULL_SYNC_PAGE)
        val more = fresh.size > page.size
        val newMessages = fetchMetadata(token, page).map { m ->
            m.toRemote(membership[m.id].orEmpty(), withBody = false).copy(hasAttachments = m.id in withFiles)
        }
        val existing = membership.keys.filter { it in known }
        if (more) println("Gmail: first sync stored ${known.size + newMessages.size} of ${membership.size}, continuing")
        return SyncBatch(
            newMessages = newMessages,
            flagChanges = existing.map { FlagChange(it, unread = it in unread, flagged = it in starred) },
            linkChanges = existing.flatMap { id -> membership.getValue(id).map { FolderLinkChange(id, it, linked = true) } },
            resetFolders = folders.map { it.id }.toSet(),
            cursor = if (more) null else profile.historyId,
            initial = account.syncCursor.isEmpty(),
            more = more,
        )
    }

    /** Headers, labels and snippet only; a message deleted in between is skipped. */
    private suspend fun fetchMetadata(token: String, ids: List<String>): List<GmailMessage> = coroutineScope {
        ids.chunked(METADATA_PARALLELISM).flatMap { chunk ->
            chunk.map { id ->
                async {
                    try {
                        retrying { api.message(token, id, full = false) }
                    } catch (e: ProviderException.NotFound) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Gmail search does the filtering: "before:", the text, is:unread, has:attachment, is:starred. */
    override suspend fun olderMessages(account: Account, folders: List<Folder>, query: OlderQuery): List<RemoteMessage> =
        withToken(account) { token ->
            // The paging cursor and the search's own before: - whichever is earlier - bound the page.
            val cursor = listOfNotNull(query.before, query.search.before).minOrNull()
            val q = listOfNotNull(
                cursor?.let { "before:${it / 1000}" },
                query.search.copy(before = null).toGmailQuery().takeIf { it.isNotEmpty() },
                "is:unread".takeIf { query.unreadOnly },
                "has:attachment".takeIf { query.attachmentsOnly },
                "is:starred".takeIf { query.flaggedOnly },
            ).joinToString(" ")
            val labels: List<String?> = folders.map { it.id }.ifEmpty { listOf(null) }
            val ids = labels.flatMap { label ->
                retrying { api.messageIds(token, label, afterEpochSeconds = null, max = query.limit, query = q) }.map { it.id }
            }.distinct()
            fetchMetadata(token, ids)
                .map { m ->
                    // Metadata has no parts; a multipart/mixed top level is how attachments show up.
                    val mixed = m.payload?.mimeType == "multipart/mixed"
                    m.toRemote(m.labelIds.toSet(), withBody = false).copy(hasAttachments = query.attachmentsOnly || mixed)
                }
                .sortedByDescending { it.receivedAt }
                .take(query.limit)
        }

    /** Gmail answers bursts with 429 and occasional 5xx: wait a little longer each time and try again. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = 1_000L
        repeat(TRANSIENT_RETRIES - 1) {
            try {
                return block()
            } catch (e: ProviderException.Transient) {
                delay(wait)
                wait *= 2
            }
        }
        return block()
    }

    private suspend fun incremental(token: String, account: Account, folders: List<Folder>, since: Long?, known: Set<String>): SyncBatch {
        val delta = api.history(token, account.syncCursor)
        val synced = folders.map { it.id }.toSet()
        val flagChanges = mutableListOf<FlagChange>()
        val linkChanges = mutableListOf<FolderLinkChange>()
        val toFetch = mutableListOf<Pair<String, Set<String>>>()
        delta.labels.forEach { (id, labels) ->
            val inSynced = labels.filter { it in synced }.toSet()
            if (id in known) {
                flagChanges += FlagChange(id, unread = LABEL_UNREAD in labels, flagged = LABEL_STARRED in labels)
                synced.forEach { folderId -> linkChanges += FolderLinkChange(id, folderId, linked = folderId in inSynced) }
            } else if (inSynced.isNotEmpty()) {
                toFetch += id to inSynced
            }
        }
        val fetched = fetchFull(token, toFetch.map { it.first }).associateBy { it.id }
        val newMessages = toFetch.mapNotNull { (id, labels) ->
            fetched[id]?.toRemote(labels)?.takeIf { since == null || it.receivedAt >= since }
        }
        return SyncBatch(
            newMessages = newMessages,
            flagChanges = flagChanges,
            linkChanges = linkChanges,
            deletedIds = delta.deleted,
            cursor = delta.historyId.takeIf { it != account.syncCursor },
        )
    }

    override suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean) {
        withToken(account) { token ->
            if (read) api.modify(token, message.id, remove = listOf(LABEL_UNREAD)) else api.modify(token, message.id, add = listOf(LABEL_UNREAD))
        }
    }

    override suspend fun archive(account: Account, message: MailMessage, folders: List<Folder>) {
        withToken(account) { token -> api.modify(token, message.id, remove = listOf(LABEL_INBOX)) }
    }

    override suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>) {
        withToken(account) { token -> api.trash(token, message.id) }
    }

    /** Gmail files the sent copy under SENT by itself and threads it by [OutgoingMail.threadId]. */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun send(account: Account, mail: OutgoingMail, folders: List<Folder>) {
        val bytes = MimeBuilder.build(account.email, mail, Platform.now()).encodeToByteArray()
        withToken(account) { token ->
            if (bytes.size > JSON_SEND_LIMIT) api.sendLarge(token, bytes) else api.send(token, Base64.UrlSafe.encode(bytes), mail.threadId)
        }
    }

    /** Same walk as [body]: the n-th part with a file name is attachment n. */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetchAttachments(account: Account, message: MailMessage, indices: Set<Int>?): List<AttachmentFile> =
        withToken(account) { token ->
            val parts = mutableListOf<MessagePart>()
            fun walk(part: MessagePart) {
                if (part.filename.isNotEmpty()) parts += part
                part.parts.forEach(::walk)
            }
            api.message(token, message.id, full = true).payload?.let(::walk)
            parts.withIndex().filter { indices == null || it.index in indices }.map { (i, part) ->
                val data = part.body?.data ?: part.body?.attachmentId?.let { api.attachment(token, message.id, it).data }.orEmpty()
                AttachmentFile(i, part.filename, base64Url.decode(data))
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun rawMessage(account: Account, message: MailMessage): ByteArray =
        withToken(account) { token -> base64Url.decode(api.raw(token, message.id).raw) }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun saveDraft(account: Account, mail: OutgoingMail, folders: List<Folder>, previous: String?): String {
        val bytes = MimeBuilder.build(account.email, mail, Platform.now()).encodeToByteArray()
        val large = bytes.size > JSON_SEND_LIMIT
        return withToken(account) { token ->
            try {
                api.saveDraft(token, previous, bytes, if (large) "" else Base64.UrlSafe.encode(bytes), mail.threadId, large)
            } catch (e: ProviderException.NotFound) {
                // The old draft was sent or deleted elsewhere: start a new one.
                api.saveDraft(token, null, bytes, if (large) "" else Base64.UrlSafe.encode(bytes), mail.threadId, large)
            }
        }
    }

    override suspend fun deleteDraft(account: Account, handle: String) {
        withToken(account) { token -> api.deleteDraft(token, handle) }
    }

    override suspend fun draftHandle(account: Account, message: MailMessage): String? =
        withToken(account) { token -> api.draftIdOf(token, message.id) }

    override suspend fun threadHeaders(account: Account, message: MailMessage): ThreadHeaders = withToken(account) { token ->
        val headers = api.threadHeaders(token, message.id)
        ThreadHeaders(
            messageId = headers.header("Message-ID").ifBlank { null },
            references = headers.header("References").ifBlank { null },
        )
    }

    override suspend fun fetchBody(account: Account, message: MailMessage): MailBody =
        withToken(account) { token -> api.message(token, message.id, full = true).body() }

    /** Full format, a few at a time; a message deleted in between is skipped. */
    private suspend fun fetchFull(token: String, ids: List<String>): List<GmailMessage> = coroutineScope {
        ids.chunked(FETCH_PARALLELISM).flatMap { chunk ->
            chunk.map { id ->
                async {
                    try {
                        retrying { api.message(token, id, full = true) }
                    } catch (e: ProviderException.NotFound) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Runs [block] with a valid token; on a 401 refreshes once and retries. */
    private suspend fun <T> withToken(account: Account, block: suspend (String) -> T): T {
        val token = (auth.credential(account) as Credential.Token).accessToken
        return try {
            block(token)
        } catch (e: ProviderException.Unauthorized) {
            block((auth.credential(account, forceRefresh = true) as Credential.Token).accessToken)
        }
    }

    private fun GmailMessage.toRemote(folderIds: Set<String>, withBody: Boolean = true): RemoteMessage {
        val body = body()
        val (name, address) = splitAddress(header("From"))
        return RemoteMessage(
            id = id,
            threadId = threadId.ifEmpty { id },
            uid = 0,
            fromName = name,
            fromAddress = address,
            toLine = header("To"),
            subject = header("Subject"),
            snippet = HtmlText.decodeEntities(snippet),
            receivedAt = internalDate.toLongOrNull() ?: 0L,
            unread = LABEL_UNREAD in labelIds,
            flagged = LABEL_STARRED in labelIds,
            hasAttachments = body.attachments.isNotEmpty(),
            sizeBytes = sizeEstimate,
            folderIds = folderIds,
            body = body.takeIf { withBody },
        )
    }

    private fun friendlyName(label: GmailLabel): String = when (label.id) {
        "INBOX" -> "Inbox"
        "SENT" -> "Sent"
        "DRAFT" -> "Drafts"
        "TRASH" -> "Trash"
        "SPAM" -> "Spam"
        else -> label.name.removePrefix("CATEGORY_").lowercase().replaceFirstChar { it.uppercase() }
            .takeIf { label.type == "system" } ?: label.name
    }

    private fun roleOf(id: String): FolderRole = when (id) {
        "INBOX" -> FolderRole.Inbox
        "SENT" -> FolderRole.Sent
        "DRAFT" -> FolderRole.Drafts
        "TRASH" -> FolderRole.Trash
        "SPAM" -> FolderRole.Spam
        else -> FolderRole.Other
    }
}

/** "Dana Cohen <dana@x.com>" -> ("Dana Cohen", "dana@x.com"). */
fun splitAddress(raw: String): Pair<String, String> {
    val lt = raw.indexOf('<')
    if (lt < 0) return "" to raw.trim()
    val name = raw.substring(0, lt).trim().trim('"')
    val address = raw.substring(lt + 1).substringBefore('>').trim()
    return name to address
}

@OptIn(ExperimentalEncodingApi::class)
private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** Walks the MIME tree Gmail returns: first text/plain, first text/html, and every named part. */
@OptIn(ExperimentalEncodingApi::class)
internal fun GmailMessage.body(): MailBody {
    var text: String? = null
    var html: String? = null
    val attachments = mutableListOf<Attachment>()
    fun charsetOf(part: MessagePart): String = part.headers
        .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value
        ?.substringAfter("charset=", "utf-8")?.substringBefore(';') ?: "utf-8"

    fun walk(part: MessagePart) {
        val data = part.body?.data
        when {
            part.filename.isNotEmpty() -> attachments += Attachment(part.filename, part.body?.size ?: 0)
            part.mimeType == "text/plain" && text == null && data != null -> text = decodeText(base64Url.decode(data), charsetOf(part))
            part.mimeType == "text/html" && html == null && data != null -> html = decodeText(base64Url.decode(data), charsetOf(part))
        }
        part.parts.forEach(::walk)
    }
    payload?.let(::walk)
    val htmlBody = html.orEmpty()
    return MailBody(text = text ?: HtmlText.toText(htmlBody), html = htmlBody, attachments = attachments)
}
