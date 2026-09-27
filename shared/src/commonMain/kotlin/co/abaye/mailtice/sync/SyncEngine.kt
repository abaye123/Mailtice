package co.abaye.mailtice.sync

import co.abaye.mailtice.platform.safeFileName
import co.abaye.mailtice.platform.joinPath
import co.abaye.mailtice.provider.MailProvider
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import co.abaye.mailtice.domain.LabelColors
import co.abaye.mailtice.auth.ReauthRequiredException
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.MailProviders
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.provider.AttachmentFile
import co.abaye.mailtice.provider.OlderQuery
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.ThreadHeaders
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** New, unread, not-yet-announced mail from one round. */
data class NewMail(val account: Account, val messages: List<MailMessage>)

private const val BACKOFF_BASE_MS = 60_000L
private const val BACKOFF_MAX_MS = 15 * 60_000L
private const val FOLDER_REFRESH_MS = 60 * 60_000L




/** Offline mode: the longest wait between failed rounds, and how much it downloads ahead per round. */
private const val OFFLINE_BACKOFF_MAX_MS = 30_000L
private const val BACKFILL_BATCH = 40
private const val BACKFILL_ATTACHMENT_BATCH = 10

/** How far back the smart check looks, and how long a learned week is kept before it is relearned. */
private const val PROFILE_WINDOW_MS = 60L * 24 * 60 * 60_000
private const val PROFILE_TTL_MS = 6L * 60 * 60_000

/** Failed rounds in a row a first sync rides out before the account is shown as offline. */
private const val FIRST_SYNC_PATIENCE = 5

/**
 * One sync round per account ([syncOnce]) - used by the desktop loop, the open Android app and the
 * Android background worker alike - plus the loops that repeat it. A per-account mutex keeps the
 * worker and the app from syncing the same mailbox at the same time.
 */
class SyncEngine(
    private val repo: MailRepository,
    private val providers: MailProviders,
    private val probe: NetworkProbe? = null,
) {
    private val _statuses = MutableStateFlow<Map<String, AccountStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, AccountStatus>> = _statuses.asStateFlow()

    private val _newMail = MutableSharedFlow<NewMail>(extraBufferCapacity = 32)

    /** Announced by whoever runs the loops (the view model); the worker uses [syncOnce]'s result. */
    val newMail: SharedFlow<NewMail> = _newMail.asSharedFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val locks = mutableMapOf<String, Mutex>()
    private val lastFolderRefresh = mutableMapOf<String, Long>()
    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Accounts whose last round left work for the next one (a first sync in pages). */
    private val pending = mutableSetOf<String>()

    private val _lastSynced = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** When each account last finished a round without an error. */
    val lastSynced: StateFlow<Map<String, Long>> = _lastSynced.asStateFlow()

    /** Rounds in a row that failed, per account; reset by the first one that succeeds. */
    private val failuresInRow = mutableMapOf<String, Int>()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Why the last round of an account failed ("" once a round succeeds), for the UI and the log. */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private var intervalMs = 60_000L

    /** Smart checking: each account's pace comes from [PollPlanner] instead of [intervalMs]. */
    private var smart = true

    private var offline = OfflinePrefs()
    private var monitor: Job? = null

    private val _plans = MutableStateFlow<Map<String, PollPlan>>(emptyMap())

    /** The pace each account is checked at right now, and why (smart checking only). */
    val plans: StateFlow<Map<String, PollPlan>> = _plans.asStateFlow()

    /** Learned weeks, rebuilt every few hours: (built at, profile). */
    private val profiles = mutableMapOf<String, Pair<Long, ActivityProfile>>()

    /** Mail sent from this app, before the sync has seen it in Sent. */
    private val sentHere = mutableMapOf<String, Long>()

    // ---- loops ------------------------------------------------------------------------------

    /** Makes the running loops match [accounts]; restarts all when the pacing changed. */
    fun reconcile(
        @StructuredScope scope: CoroutineScope,
        accounts: List<Account>,
        intervalSeconds: Int,
        smart: Boolean,
        offline: OfflinePrefs = OfflinePrefs(),
    ) {
        val newInterval = intervalSeconds * 1000L
        if (newInterval != intervalMs || smart != this.smart || offline != this.offline) {
            intervalMs = newInterval
            this.smart = smart
            this.offline = offline
            if (!smart) _plans.value = emptyMap()
            stopAll()
            monitor?.cancel()
            monitor = if (offline.enabled && probe != null) scope.launch { watchConnection(probe) } else null
        }
        val wanted = accounts.map { it.id }.toSet()
        (jobs.keys - wanted).forEach { stop(it) }
        accounts.filter { jobs[it.id]?.isActive != true }.forEach { account ->
            jobs[account.id] = scope.launch { loop(account.id) }
        }
    }

    fun restart(@StructuredScope scope: CoroutineScope, accountId: String) {
        stop(accountId)
        jobs[accountId] = scope.launch { loop(accountId) }
    }

    fun refreshNow() {
        kicks.tryEmit(Unit)
    }

    fun stop(accountId: String) {
        jobs.remove(accountId)?.cancel()
    }

    fun stopAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun loop(accountId: String) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val account = repo.account(accountId) ?: return
            try {
                val fresh = syncOnce(account)
                if (fresh.isNotEmpty()) _newMail.emit(NewMail(account, fresh))
                failures = 0
                // A first sync in pages goes straight on to the next page.
                if (pending.remove(accountId)) continue
                withTimeoutOrNull(nextDelay(accountId)) { kicks.first() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReauthRequiredException) {
                println("Sync: credentials rejected, loop stopped until the user signs in again")
                return
            } catch (e: Exception) {
                failures++
                println("Sync: round failed, attempt $failures: ${describe(e)}")
                // Offline mode waits for the connection watcher instead, so a short window is not missed.
                val cap = if (offline.enabled) OFFLINE_BACKOFF_MAX_MS else BACKOFF_MAX_MS
                val backoff = (BACKOFF_BASE_MS shl (failures - 1).coerceAtMost(4)).coerceAtMost(cap)
                withTimeoutOrNull(backoff) { kicks.first() }
            }
        }
    }

    /**
     * How long an account waits for its next round: the fixed interval, or with smart checking
     * what [PollPlanner] makes of its learned week and its latest sent and received mail.
     */
    private fun nextDelay(accountId: String): Long {
        if (!smart) return intervalMs
        return runCatching {
            val now = Platform.now()
            val zone = TimeZone.currentSystemDefault()
            val profile = profiles[accountId]?.takeIf { now - it.first < PROFILE_TTL_MS }?.second
                ?: ActivityProfile.from(repo.activity(accountId, now - PROFILE_WINDOW_MS), now, zone).also { profiles[accountId] = now to it }
            val (sent, incoming) = repo.latestActivity(accountId)
            val lastSent = listOfNotNull(sent, sentHere[accountId]).maxOrNull()
            PollPlanner.plan(profile, now, zone, lastSent, incoming).also { plan -> _plans.update { it + (accountId to plan) } }.delayMs
        }.getOrDefault(intervalMs)
    }

    // ---- one round --------------------------------------------------------------------------

    /**
     * Syncs one account and returns the messages to announce (already marked as announced, so
     * nobody announces them twice). Throws what the provider throws; statuses are updated here.
     */
    suspend fun syncOnce(account: Account): List<MailMessage> = lock(account.id).withLock {
        val provider = providers.forAccount(account)
        setStatus(account.id, if (repo.foldersNow(account.id).isEmpty()) AccountStatus.Syncing else statusOf(account.id))
        try {
            val now = Platform.now()
            // What the user did offline goes first, so the round does not undo it with the server's state.
            replayPending(account, provider)
            if (repo.foldersNow(account.id).isEmpty() || now - (lastFolderRefresh[account.id] ?: 0L) > FOLDER_REFRESH_MS) {
                repo.mergeFolders(account.id, provider.listFolders(account))
                repo.updateCapabilities(account.id, provider.capabilities(account))
                refreshIdentities(account, provider)
                lastFolderRefresh[account.id] = now
            }
            val stored = repo.account(account.id) ?: return@withLock emptyList()
            // Offline mode keeps the whole mailbox, whatever the account's window.
            val current = if (offline.enabled) stored.copy(retentionDays = 0) else stored
            val synced = repo.foldersNow(account.id).filter { it.sync }
            val since = if (current.retentionDays > 0) now - current.retentionDays * MailRepository.DAY_MS else null
            val batch = provider.sync(current, synced, since, repo.knownIds(account.id, synced.map { it.id }))
            repo.applyBatch(account.id, batch)
            repo.prune(current)
            if (batch.more) pending += account.id
            if (!batch.more && offline.enabled && backfill(current, provider)) pending += account.id
            // Still "syncing" while a first sync has pages left, so the UI keeps saying so.
            setStatus(account.id, if (batch.more) AccountStatus.Syncing else AccountStatus.Ok)
            _errors.update { it - account.id }
            failuresInRow.remove(account.id)
            _lastSynced.update { it + (account.id to Platform.now()) }

            val pending = repo.toNotify(account.id)
            repo.markNotified(account.id, pending.map { it.id })
            if (current.notify) pending else emptyList()
        } catch (e: ReauthRequiredException) {
            setStatus(account.id, AccountStatus.NeedsReauth)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A first sync (or one that has not started) that hiccups is still under way: the loop
            // retries it, and the user is told the mail is on its way, not that the connection is
            // gone. Only a few failures in a row, or one on an account already synced, mean offline.
            val failures = (failuresInRow[account.id] ?: 0) + 1
            failuresInRow[account.id] = failures
            val firstSync = statusOf(account.id).let { it == AccountStatus.Syncing || it == AccountStatus.Idle }
            setStatus(account.id, if (firstSync && failures < FIRST_SYNC_PATIENCE) AccountStatus.Syncing else AccountStatus.Offline)
            _errors.update { it + (account.id to describe(e)) }
            throw e
        }
    }

    /** Exception type and message, trimmed; provider messages carry the HTTP status and Google's reason. */
    private fun describe(e: Throwable): String =
        listOfNotNull(e::class.simpleName, e.message?.lineSequence()?.firstOrNull()?.take(240)).joinToString(": ")

    /** Background worker entry point: every account once, failures isolated per account. */
    suspend fun syncAllOnce(): List<NewMail> = repo.accountsNow().mapNotNull { account ->
        runCatching { syncOnce(account) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { NewMail(account, it) }
    }

    // ---- actions ----------------------------------------------------------------------------

    /**
     * Optimistic: the database changes first; a server refusal puts it back and rethrows. In offline
     * mode a missing connection keeps the change and queues it ([ActionQueuedException]).
     */
    suspend fun setRead(message: MailMessage, read: Boolean) {
        val account = repo.account(message.accountId) ?: return
        repo.setUnread(message.accountId, message.id, !read)
        try {
            providers.forAccount(account).setRead(account, message, repo.foldersNow(account.id), read)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            queueIfOffline(e, message, if (read) PendingKind.Read else PendingKind.Unread)
            repo.setUnread(message.accountId, message.id, message.unread)
            throw e
        }
    }

    /** Queues [kind] and throws [ActionQueuedException] when [e] is a lost connection in offline mode. */
    private fun queueIfOffline(e: Exception, message: MailMessage, kind: PendingKind) {
        if (!offline.enabled || !isNetworkError(e)) return
        repo.queuePending(message.accountId, message.id, kind)
        throw ActionQueuedException()
    }

    suspend fun archive(message: MailMessage) {
        val account = repo.account(message.accountId) ?: return
        check(account.capabilities.archive) { "Archive is not available for this account" }
        val folders = repo.foldersNow(account.id)
        val inbox = folders.firstOrNull { it.role == FolderRole.Inbox } ?: return
        repo.unlink(account.id, message.id, inbox.id)
        try {
            providers.forAccount(account).archive(account, message, folders)
            // IMAP moved it: the old "<folder>/<uid>" id no longer exists; the next round picks up the copy.
            if (!account.capabilities.labels) repo.pruneOrphansFor(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            queueIfOffline(e, message, PendingKind.Archive)
            repo.link(account.id, message.id, inbox.id)
            throw e
        }
    }

    /**
     * Optimistic like archive: the row leaves every list at once (its folder links go), the server
     * moves it to the trash, and only then is the local copy deleted. A refusal puts the links back.
     */
    suspend fun trash(message: MailMessage) {
        val account = repo.account(message.accountId) ?: return
        check(account.capabilities.trash) { "Trash is not available for this account" }
        val links = repo.folderIdsOf(account.id, message.id)
        links.forEach { repo.unlink(account.id, message.id, it) }
        try {
            providers.forAccount(account).trash(account, message, repo.foldersNow(account.id))
            repo.deleteMessage(account.id, message.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            queueIfOffline(e, message, PendingKind.Trash)
            links.forEach { repo.link(account.id, message.id, it) }
            throw e
        }
    }

    /** Sends, then asks for a round so the sent copy (where the Sent folder is synced) shows up. */
    suspend fun send(account: Account, mail: OutgoingMail) {
        check(account.capabilities.send) { "Sending is not available for this account" }
        providers.forAccount(account).send(account, mail, repo.foldersNow(account.id))
        // A reply may come soon: the smart pace speeds up for this account right away.
        sentHere[account.id] = Platform.now()
        kicks.tryEmit(Unit)
    }

    /** From the offline copy when every file asked for is there, otherwise from the server. */
    suspend fun attachments(message: MailMessage, indices: Set<Int>?): List<AttachmentFile> {
        val account = repo.account(message.accountId) ?: return emptyList()
        repo.body(message.accountId, message.id)?.let { body ->
            val wanted = indices ?: body.attachments.indices.toSet()
            val cached = wanted.mapNotNull { i ->
                val name = body.attachments.getOrNull(i)?.name ?: return@mapNotNull null
                Platform.readBytes(attachmentPath(message.accountId, message.id, i))?.let { AttachmentFile(i, name, it) }
            }
            if (cached.size == wanted.size && cached.isNotEmpty()) return cached
        }
        return providers.forAccount(account).fetchAttachments(account, message, indices)
    }

    /** The send-as addresses; best effort, a failure keeps the ones already known. */
    private suspend fun refreshIdentities(account: Account, provider: MailProvider) {
        try {
            val identities = provider.identities(account)
            if (identities.isNotEmpty()) repo.replaceIdentities(account.id, identities)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Sync: send-as addresses not read: ${describe(e)}")
        }
    }

    // ---- offline mode -------------------------------------------------------------------------

    /**
     * Watches for the internet every 15 seconds while it is away (every minute while it is there),
     * and on its return starts a round of every account at once: someone online for a few minutes
     * every few days gets all of it in that window.
     */
    private suspend fun watchConnection(probe: NetworkProbe) {
        var wasOnline = true
        while (currentCoroutineContext().isActive) {
            val online = probe.online()
            if (online && !wasOnline) {
                println("Sync: connection is back, syncing everything now")
                kicks.tryEmit(Unit)
            }
            wasOnline = online
            delay(if (online) 60_000L else 15_000L)
        }
    }

    /** Sends the queued offline actions in order; a server refusal drops one, a lost connection stops. */
    private suspend fun replayPending(account: Account, provider: MailProvider) {
        for ((id, messageId, kind) in repo.pending(account.id)) {
            val message = repo.message(account.id, messageId)
            if (message == null) {
                repo.donePending(id)
                continue
            }
            try {
                val folders = repo.foldersNow(account.id)
                when (kind) {
                    PendingKind.Read -> provider.setRead(account, message, folders, true)
                    PendingKind.Unread -> provider.setRead(account, message, folders, false)
                    PendingKind.Archive -> provider.archive(account, message, folders)
                    PendingKind.Trash -> {
                        provider.trash(account, message, folders)
                        repo.deleteMessage(account.id, message.id)
                    }
                }
                repo.donePending(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isNetworkError(e)) throw e
                println("Sync: queued ${kind.name} refused, dropped: ${describe(e)}")
                repo.donePending(id)
            }
        }
    }

    /**
     * Downloads ahead what offline mode keeps: bodies, newest first, a batch per round, then the
     * attachments within the size limit. True while there is more, so the loop goes straight on.
     */
    private suspend fun backfill(account: Account, provider: MailProvider): Boolean {
        val missing = repo.messageIdsWithoutBody(account.id, BACKFILL_BATCH.toLong())
        for (id in missing) {
            val message = repo.message(account.id, id) ?: continue
            repo.saveBody(account.id, id, provider.fetchBody(account, message))
        }
        if (missing.size >= BACKFILL_BATCH) return true
        val limit = offline.attachmentLimitBytes
        if (limit <= 0) return false
        var fetched = 0
        for (id in repo.messageIdsWithAttachments(account.id)) {
            if (Platform.readBytes(doneMarker(account.id, id)) != null) continue
            val message = repo.message(account.id, id) ?: continue
            val body = repo.body(account.id, id) ?: continue
            val wanted = body.attachments.withIndex().filter { it.value.size <= limit }.map { it.index }.toSet()
            if (wanted.isNotEmpty()) {
                provider.fetchAttachments(account, message, wanted).forEach { f ->
                    Platform.writeBytes(attachmentPath(account.id, id, f.index), f.bytes)
                }
            }
            Platform.writeBytes(doneMarker(account.id, id), ByteArray(0))
            if (++fetched >= BACKFILL_ATTACHMENT_BATCH) return true
        }
        return false
    }

    private fun offlineDir(accountId: String, messageId: String): String =
        joinPath(joinPath(joinPath(Platform.appDir(), "offline"), safeFileName(accountId, "account")), safeFileName(messageId, "message"))

    private fun attachmentPath(accountId: String, messageId: String, index: Int) = joinPath(offlineDir(accountId, messageId), index.toString())

    private fun doneMarker(accountId: String, messageId: String) = joinPath(offlineDir(accountId, messageId), "done")

    /** Older mail straight from the server, as list rows. Never stored (see [MailProvider.olderMessages]). */
    suspend fun olderMessages(account: Account, folders: List<Folder>, query: OlderQuery): List<MailMessage> =
        providers.forAccount(account).olderMessages(account, folders, query).map { r ->
            MailMessage(
                account.id, r.id, r.threadId, r.uid, r.fromName, r.fromAddress, r.toLine, r.subject, r.snippet,
                r.receivedAt, r.unread, r.flagged, r.hasAttachments, r.sizeBytes, folderIds = r.folderIds.toList(),
            )
        }

    suspend fun saveDraft(account: Account, mail: OutgoingMail, previous: String?): String =
        providers.forAccount(account).saveDraft(account, mail, repo.foldersNow(account.id), previous)

    suspend fun deleteDraft(account: Account, handle: String) {
        providers.forAccount(account).deleteDraft(account, handle)
        kicks.tryEmit(Unit)
    }

    suspend fun draftHandle(message: MailMessage): String? {
        val account = repo.account(message.accountId) ?: return null
        return runCatching { providers.forAccount(account).draftHandle(account, message) }.getOrNull()
    }

    suspend fun rawMessage(message: MailMessage): ByteArray {
        val account = repo.account(message.accountId) ?: return ByteArray(0)
        return providers.forAccount(account).rawMessage(account, message)
    }

    /** Threading headers for a reply; empty when the server cannot say (the reply still goes out). */
    suspend fun threadHeaders(message: MailMessage): ThreadHeaders {
        val account = repo.account(message.accountId) ?: return ThreadHeaders()
        return try {
            providers.forAccount(account).threadHeaders(account, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ThreadHeaders()
        }
    }

    /**
     * Stored body, or fetched now and stored (after a cache clear, or a body the sync skipped).
     * [refresh] reads it from the server even when stored, replacing the stored copy.
     */
    suspend fun body(message: MailMessage, refresh: Boolean = false): MailBody {
        if (!refresh) repo.body(message.accountId, message.id)?.let { return it }
        val account = repo.account(message.accountId) ?: return MailBody("", "", emptyList())
        val body = providers.forAccount(account).fetchBody(account, message)
        // Older mail looked at from the server has no stored row, so its body is not stored either.
        if (repo.message(message.accountId, message.id) != null) repo.saveBody(message.accountId, message.id, body)
        return body
    }

    /** Label management: the change goes to the server, then the folder list is read back. */
    suspend fun createLabel(account: Account, name: String, color: LabelColors?) {
        providers.forAccount(account).createLabel(account, name, color)
        refreshFolders(account)
    }

    suspend fun updateLabel(account: Account, id: String, name: String, color: LabelColors?): String {
        val newId = providers.forAccount(account).updateLabel(account, id, name, color)
        refreshFolders(account)
        return newId
    }

    suspend fun deleteLabel(account: Account, id: String) {
        providers.forAccount(account).deleteLabel(account, id)
        refreshFolders(account)
    }

    /** Re-reads the folder list now (account details screen). */
    suspend fun refreshFolders(account: Account) {
        val provider = providers.forAccount(account)
        repo.mergeFolders(account.id, provider.listFolders(account))
        repo.updateCapabilities(account.id, provider.capabilities(account))
        refreshIdentities(account, provider)
        lastFolderRefresh[account.id] = Platform.now()
    }

    suspend fun forget(account: Account) {
        stop(account.id)
        runCatching { providers.forAccount(account).close(account) }
        _statuses.update { it - account.id }
        lastFolderRefresh.remove(account.id)
    }

    private fun lock(accountId: String): Mutex = locks.getOrPut(accountId) { Mutex() }

    private fun statusOf(accountId: String) = _statuses.value[accountId] ?: AccountStatus.Idle

    private fun setStatus(accountId: String, status: AccountStatus) {
        _statuses.update { if (it[accountId] == status) it else it + (accountId to status) }
    }
}
