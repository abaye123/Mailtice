package co.abaye.mailtice.data

import co.abaye.mailtice.sync.PendingKind
import co.abaye.mailtice.sync.ActivityEvent
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.db.Message_body
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.export.ThreadExport
import co.abaye.mailtice.search.MailSearch
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.StorageUsage
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.SyncBatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Inbox filter. Empty strings mean "no filter" (that is how the SQL is written). */
data class InboxQuery(
    val accountId: String = "",
    val folderId: String = "",
    val unreadOnly: Boolean = false,
    val attachmentsOnly: Boolean = false,
    val flaggedOnly: Boolean = false,
    /** Folder role name, "" = any synced folder. */
    val role: String = "",
    /** The parsed search; its unread / starred / attachment parts combine with the flags above. */
    val search: MailSearch = MailSearch(),
    val limit: Long = 500,
)

/**
 * The only class that touches SQL. Sync writes through [applyBatch]; the UI reads Flows that
 * re-emit whenever a table they read changes.
 */
class MailRepository(
    private val driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) {
    private val db = MailDatabase(driver)
    private val q = db.mailQueries

    // ---- accounts -------------------------------------------------------------------------

    val accounts: Flow<List<Account>> = q.selectAccounts(::mapAccount).asFlow().mapToList(dispatcher)

    fun accountsNow(): List<Account> = q.selectAccounts(::mapAccount).executeAsList()

    fun account(id: String): Account? = q.selectAccount(id, ::mapAccount).executeAsOneOrNull()

    fun addAccount(account: Account) {
        q.insertAccount(
            co.abaye.mailtice.db.Account(
                id = account.id,
                kind = account.kind.name,
                email = account.email,
                label = account.label,
                color = account.color.name,
                notify = if (account.notify) 1 else 0,
                retentionDays = account.retentionDays.toLong(),
                capabilities = account.capabilities.encode(),
                imapHost = account.imap?.host,
                imapPort = account.imap?.port?.toLong(),
                imapSecurity = account.imap?.security?.name,
                username = account.username,
                syncCursor = account.syncCursor,
                sortOrder = accountsNow().size.toLong(),
                createdAt = Platform.now(),
            ),
        )
    }

    fun updatePrefs(account: Account) {
        q.updateAccountPrefs(account.label, account.color.name, if (account.notify) 1 else 0, account.retentionDays.toLong(), account.id)
    }

    fun updateServer(accountId: String, server: ImapServer, username: String) {
        q.updateAccountServer(server.host, server.port.toLong(), server.security.name, username, accountId)
    }

    fun updateCapabilities(accountId: String, capabilities: Capabilities) {
        q.updateAccountCapabilities(capabilities.encode(), accountId)
    }

    /** Cascades to folders, messages, links and bodies. */
    fun deleteAccount(accountId: String) {
        q.deleteAccount(accountId)
    }

    // ---- folders --------------------------------------------------------------------------

    fun folders(accountId: String): Flow<List<Folder>> = q.selectFolders(accountId, ::mapFolder).asFlow().mapToList(dispatcher)

    val allFolders: Flow<List<Folder>> = q.selectAllFolders(::mapFolder).asFlow().mapToList(dispatcher)

    fun foldersNow(accountId: String): List<Folder> = q.selectFolders(accountId, ::mapFolder).executeAsList()

    /**
     * Brings the local folder list in line with the server. New folders start unsynced, except the
     * inbox (synced, with notifications); folders gone from the server are dropped with their links.
     */
    fun mergeFolders(accountId: String, remote: List<RemoteFolder>) {
        db.transaction {
            val local = foldersNow(accountId).associateBy { it.id }
            remote.forEach { f ->
                val inbox = f.role == FolderRole.Inbox
                // Every folder syncs; only the inbox notifies until the user says otherwise.
                q.insertFolderIfMissing(accountId, f.id, f.name, f.role.name, 1, if (inbox) 1 else 0, f.color)
                if (f.id in local) q.renameFolder(f.name, f.role.name, f.color, accountId, f.id)
            }
            val remoteIds = remote.map { it.id }.toSet()
            (local.keys - remoteIds).forEach { gone ->
                q.clearFolderLinks(accountId, gone)
                q.deleteFolder(accountId, gone)
            }
        }
    }

    fun setFolderPrefs(accountId: String, folderId: String, sync: Boolean, notify: Boolean) {
        q.setFolderPrefs(if (sync) 1 else 0, if (notify) 1 else 0, accountId, folderId)
    }

    // ---- messages -------------------------------------------------------------------------

    fun inbox(query: InboxQuery): Flow<List<MailMessage>> {
        val s = query.search
        fun w(i: Int) = s.words.getOrElse(i) { "" }
        fun x(i: Int) = s.excluded.getOrElse(i) { "" }
        // Words and exclusions beyond what the SQL takes are checked on the rows it returns.
        val extraWords = s.words.drop(4)
        val extraExcluded = s.excluded.drop(2)
        return q.selectInbox(
            folderId = query.folderId,
            accountId = query.accountId,
            role = query.role,
            unreadOnly = if (query.unreadOnly || s.unread == true) 1 else 0,
            flaggedOnly = if (query.flaggedOnly || s.starred) 1 else 0,
            readOnly = if (s.unread == false) 1 else 0,
            fromQ = s.from,
            toQ = s.to,
            subjectQ = s.subject,
            after = s.after ?: 0,
            before = s.before ?: 0,
            w1 = w(0), w2 = w(1), w3 = w(2), w4 = w(3),
            x1 = x(0), x2 = x(1),
            attachmentsOnly = if (query.attachmentsOnly || s.hasAttachment) 1 else 0,
            limit = query.limit,
            mapper = ::mapInboxMessage,
        ).asFlow().mapToList(dispatcher).map { rows ->
            if (extraWords.isEmpty() && extraExcluded.isEmpty()) {
                rows
            } else {
                rows.filter { m ->
                    val hay = "${m.subject} ${m.fromName} ${m.fromAddress} ${m.snippet}"
                    extraWords.all { hay.contains(it, ignoreCase = true) } && extraExcluded.none { hay.contains(it, ignoreCase = true) }
                }
            }
        }
    }

    val unreadCounts: Flow<Map<String, Long>> =
        q.unreadCounts().asFlow().mapToList(dispatcher).map { rows -> rows.associate { it.accountId to it.unread } }

    /** accountId -> folderId -> unread messages in it (synced folders only). */
    val unreadByFolder: Flow<Map<String, Map<String, Long>>> =
        q.unreadByFolder().asFlow().mapToList(dispatcher).map { rows ->
            rows.groupBy { it.accountId }.mapValues { (_, list) -> list.associate { it.folderId to it.unread } }
        }

    fun message(accountId: String, id: String): MailMessage? = q.selectMessage(accountId, id, ::mapMessage).executeAsOneOrNull()

    // ---- offline mode -----------------------------------------------------------------------

    fun queuePending(accountId: String, messageId: String, kind: PendingKind) {
        q.insertPending(accountId, messageId, kind.name, Platform.now())
    }

    fun pending(accountId: String): List<Triple<Long, String, PendingKind>> = q.selectPending(accountId).executeAsList().mapNotNull { r ->
        PendingKind.entries.firstOrNull { it.name == r.kind }?.let { Triple(r.id, r.messageId, it) }
    }

    fun donePending(id: Long) {
        q.deletePending(id)
    }

    fun messageIdsWithoutBody(accountId: String, limit: Long): List<String> = q.messageIdsWithoutBody(accountId, limit).executeAsList()

    fun messageIdsWithAttachments(accountId: String): List<String> = q.messageIdsWithAttachments(accountId).executeAsList()

    fun activity(accountId: String, since: Long): List<ActivityEvent> =
        q.activityTimes(accountId, since) { at, sent -> ActivityEvent(at, sent) }.executeAsList()

    /** The newest sent and received times of an account, null when it has none. */
    fun latestActivity(accountId: String): Pair<Long?, Long?> =
        q.latestActivity(accountId).executeAsOneOrNull()?.let { it.lastSent to it.lastIncoming } ?: (null to null)

    fun knownIds(accountId: String, folderIds: List<String>): Set<String> =
        folderIds.flatMap { q.messageIdsInFolder(accountId, it).executeAsList() }.toSet()

    fun body(accountId: String, messageId: String): MailBody? =
        q.selectBody(accountId, messageId).executeAsOneOrNull()?.let {
            MailBody(it.textBody, it.htmlBody, decodeAttachments(it.attachments))
        }

    fun saveBody(accountId: String, messageId: String, body: MailBody) {
        q.upsertBody(Message_body(accountId, messageId, body.text, body.html, encodeAttachments(body.attachments)))
    }

    /**
     * The stored conversation [message] belongs to, oldest first: by thread id where the provider
     * has one (Gmail), otherwise by subject once "Re:"/"Fwd:" prefixes are dropped.
     */
    fun threadOf(message: MailMessage): List<MailMessage> {
        if (message.threadId.isNotBlank() && message.threadId != message.id) {
            return q.selectThread(message.accountId, message.threadId, ::mapMessage).executeAsList().ifEmpty { listOf(message) }
        }
        val base = ThreadExport.baseSubject(message.subject)
        if (base.isBlank()) return listOf(message)
        return q.selectBySubject(message.accountId, base, ::mapMessage).executeAsList()
            .filter { ThreadExport.baseSubject(it.subject).equals(base, ignoreCase = true) }
            .ifEmpty { listOf(message) }
    }

    // ---- scheduled send ---------------------------------------------------------------------

    val scheduled: Flow<List<ScheduledMail>> = q.selectScheduled().asFlow().mapToList(dispatcher).map { rows ->
        rows.mapNotNull { r ->
            runCatching { ScheduledMail(r.id, r.accountId, r.sendAt, ScheduledCodec.decode(r.payload), r.attempts.toInt(), r.lastError) }.getOrNull()
        }
    }

    fun dueScheduled(now: Long): List<ScheduledMail> = q.selectDueScheduled(now).executeAsList().mapNotNull { r ->
        runCatching { ScheduledMail(r.id, r.accountId, r.sendAt, ScheduledCodec.decode(r.payload), r.attempts.toInt(), r.lastError) }.getOrNull()
    }

    fun schedule(item: ScheduledMail) {
        q.insertScheduled(item.id, item.accountId, item.sendAt, ScheduledCodec.encode(item.mail))
    }

    fun unschedule(id: String) {
        q.deleteScheduled(id)
    }

    fun scheduledFailed(id: String, error: String, retryAt: Long) {
        q.failScheduled(error.take(300), retryAt, id)
    }

    fun contacts(query: String): List<Contact> =
        q.selectContacts(query.trim()).executeAsList().map { Contact(it.fromName, it.fromAddress) }

    fun folderIdsOf(accountId: String, messageId: String): List<String> =
        q.folderIdsOfMessage(accountId, messageId).executeAsList()

    fun deleteMessage(accountId: String, messageId: String) {
        q.deleteMessage(accountId, messageId)
    }

    fun setUnread(accountId: String, messageId: String, unread: Boolean) {
        q.setUnread(if (unread) 1 else 0, accountId, messageId)
    }

    /** Local side of an archive: drop the inbox link so the row leaves the inbox at once. */
    fun unlink(accountId: String, messageId: String, folderId: String) {
        q.unlinkFolder(accountId, messageId, folderId)
    }

    fun link(accountId: String, messageId: String, folderId: String) {
        q.linkFolder(accountId, messageId, folderId)
    }

    /** One sync round, all or nothing. */
    fun applyBatch(accountId: String, batch: SyncBatch) {
        db.transaction {
            batch.resetFolders.forEach { q.clearFolderLinks(accountId, it) }
            batch.newMessages.forEach { m ->
                q.insertMessageIfMissing(
                    accountId, m.id, m.threadId, m.uid, m.fromName, m.fromAddress, m.toLine, m.subject, m.snippet,
                    m.receivedAt, if (m.unread) 1 else 0, if (m.flagged) 1 else 0, if (m.hasAttachments) 1 else 0, m.sizeBytes,
                )
                q.setFlags(if (m.unread) 1 else 0, if (m.flagged) 1 else 0, accountId, m.id)
                m.folderIds.forEach { folderId -> q.linkFolder(accountId, m.id, folderId) }
                m.body?.let { body -> q.upsertBody(Message_body(accountId, m.id, body.text, body.html, encodeAttachments(body.attachments))) }
            }
            batch.flagChanges.forEach { q.setFlags(if (it.unread) 1 else 0, if (it.flagged) 1 else 0, accountId, it.messageId) }
            batch.linkChanges.forEach {
                if (it.linked) q.linkFolder(accountId, it.messageId, it.folderId) else q.unlinkFolder(accountId, it.messageId, it.folderId)
            }
            batch.deletedIds.forEach { q.deleteMessage(accountId, it) }
            batch.folderStates.forEach { q.setFolderState(it.uidValidity, it.uidNext, it.highestModSeq, accountId, it.folderId) }
            batch.cursor?.let { q.updateAccountCursor(it, accountId) }
            // A first sync never notifies: everything already there counts as seen.
            if (batch.initial) q.markAllNotified(Platform.now(), accountId)
        }
    }

    fun resetCursor(accountId: String) {
        q.updateAccountCursor("", accountId)
    }

    // ---- notifications --------------------------------------------------------------------

    fun toNotify(accountId: String): List<MailMessage> = q.selectToNotify(accountId, ::mapMessage).executeAsList()

    fun markNotified(accountId: String, ids: List<String>) {
        val now = Platform.now()
        db.transaction { ids.forEach { q.markNotified(now, accountId, it) } }
    }

    // ---- retention & storage --------------------------------------------------------------

    /** Removes mail outside the retention window and mail no synced folder holds any more. */
    fun prune(account: Account) {
        db.transaction {
            if (account.retentionDays > 0) {
                q.pruneOlderThan(account.id, Platform.now() - account.retentionDays * DAY_MS)
            }
            q.pruneOrphans(account.id)
        }
    }

    fun clearFolderLinksFor(accountId: String, folderId: String) {
        q.clearFolderLinks(accountId, folderId)
    }

    fun pruneOrphansFor(account: Account) {
        q.pruneOrphans(account.id)
    }

    /** "Clear cache": all local mail of the account goes; the next round fetches the window again. */
    fun clearAccountMail(account: Account) {
        db.transaction {
            q.clearAccountMail(account.id)
            q.updateAccountCursor("", account.id)
            foldersNow(account.id).forEach { q.setFolderState(0, 0, 0, account.id, it.id) }
            // Everything re-downloaded is old news.
        }
    }

    fun storage(): StorageUsage {
        val rows = q.storagePerAccount().executeAsList()
        return StorageUsage(
            totalBytes = databaseFiles().sumOf { fileSize(it) },
            perAccount = rows.associate { it.accountId to ((it.bytes as Number?)?.toLong() ?: 0L) },
            messagesPerAccount = rows.associate { it.accountId to it.messages },
        )
    }

    /** Gives freed pages back to the OS after large deletions. */
    fun vacuum() {
        driver.execute(null, "VACUUM", 0)
    }

    // ---- mapping --------------------------------------------------------------------------

    @Suppress("LongParameterList")
    private fun mapAccount(
        id: String, kind: String, email: String, label: String, color: String, notify: Long, retentionDays: Long,
        capabilities: String, imapHost: String?, imapPort: Long?, imapSecurity: String?, username: String?,
        syncCursor: String, @Suppress("UNUSED_PARAMETER") sortOrder: Long, @Suppress("UNUSED_PARAMETER") createdAt: Long,
    ) = Account(
        id = id,
        kind = ProviderKind.entries.firstOrNull { it.name == kind } ?: ProviderKind.Imap,
        email = email,
        label = label,
        color = AccountColor.entries.firstOrNull { it.name == color } ?: AccountColor.Blue,
        notify = notify != 0L,
        retentionDays = retentionDays.toInt(),
        capabilities = Capabilities.decode(capabilities),
        imap = imapHost?.let {
            ImapServer(it, imapPort?.toInt() ?: 993, ImapSecurity.entries.firstOrNull { s -> s.name == imapSecurity } ?: ImapSecurity.Tls)
        },
        username = username ?: email,
        syncCursor = syncCursor,
    )

    @Suppress("LongParameterList")
    private fun mapFolder(
        accountId: String, id: String, name: String, role: String, sync: Long, notify: Long,
        uidValidity: Long, uidNext: Long, highestModSeq: Long, color: String,
    ) = Folder(
        accountId, id, name, FolderRole.entries.firstOrNull { it.name == role } ?: FolderRole.Other,
        sync != 0L, notify != 0L, uidValidity, uidNext, highestModSeq, color,
    )

    @Suppress("LongParameterList")
    private fun mapInboxMessage(
        accountId: String, id: String, threadId: String, uid: Long, fromName: String, fromAddress: String,
        toLine: String, subject: String, snippet: String, receivedAt: Long, unread: Long, flagged: Long,
        hasAttachments: Long, sizeBytes: Long, notifiedAt: Long?, folderIds: String?,
    ) = mapMessage(
        accountId, id, threadId, uid, fromName, fromAddress, toLine, subject, snippet, receivedAt, unread, flagged,
        hasAttachments, sizeBytes, notifiedAt,
    ).copy(folderIds = folderIds?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty())

    @Suppress("LongParameterList")
    private fun mapMessage(
        accountId: String, id: String, threadId: String, uid: Long, fromName: String, fromAddress: String,
        toLine: String, subject: String, snippet: String, receivedAt: Long, unread: Long, flagged: Long,
        hasAttachments: Long, sizeBytes: Long, @Suppress("UNUSED_PARAMETER") notifiedAt: Long?,
    ) = MailMessage(
        accountId, id, threadId, uid, fromName, fromAddress, toLine, subject, snippet, receivedAt,
        unread != 0L, flagged != 0L, hasAttachments != 0L, sizeBytes,
    )

    // "name|size|contentId|mimeType"; rows stored before the last two existed have just two fields.
    private fun encodeAttachments(list: List<Attachment>) = list.joinToString("\n") { a ->
        listOf(a.name, a.size.toString(), a.contentId, a.mimeType).joinToString("|") { it.replace('|', ' ').replace('\n', ' ') }
    }

    private fun decodeAttachments(raw: String) = raw.lines().filter { '|' in it }.map { line ->
        val f = line.split('|')
        Attachment(f[0], f.getOrNull(1)?.toLongOrNull() ?: 0, f.getOrElse(2) { "" }, f.getOrElse(3) { "" })
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
