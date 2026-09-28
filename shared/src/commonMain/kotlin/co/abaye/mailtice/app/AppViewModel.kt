package co.abaye.mailtice.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import co.abaye.mailtice.auth.AuthCancelledException
import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.auth.Authorizer
import co.abaye.mailtice.auth.OAuthProvider
import co.abaye.mailtice.auth.ReauthRequiredException
import co.abaye.mailtice.data.InboxQuery
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.ScheduledMail
import co.abaye.mailtice.data.SecretStore
import co.abaye.mailtice.data.SettingsStore
import co.abaye.mailtice.data.seedData
import co.abaye.mailtice.dev.DemoAccounts
import co.abaye.mailtice.dev.DemoMode
import co.abaye.mailtice.di.Io
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.ListFractionRange
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.OfflineAttachmentLimits
import co.abaye.mailtice.domain.PollIntervals
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.RetentionOptions
import co.abaye.mailtice.domain.SenderIdentity
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.domain.conversationKey
import co.abaye.mailtice.export.ExportLabels
import co.abaye.mailtice.export.ThreadExport
import co.abaye.mailtice.main.PreviewKind
import co.abaye.mailtice.main.cidRefs
import co.abaye.mailtice.main.fileUrl
import co.abaye.mailtice.main.previewKindOf
import co.abaye.mailtice.main.previewPage
import co.abaye.mailtice.main.splitQuote
import co.abaye.mailtice.notify.MailNotifications
import co.abaye.mailtice.notify.NotificationAction
import co.abaye.mailtice.notify.NotificationActions
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import co.abaye.mailtice.platform.localizedString
import co.abaye.mailtice.platform.safeFileName
import co.abaye.mailtice.platform.systemUiLanguage
import co.abaye.mailtice.provider.HtmlText
import co.abaye.mailtice.provider.ImapAutoConfig
import co.abaye.mailtice.provider.ImapBackend
import co.abaye.mailtice.provider.ImapLoginException
import co.abaye.mailtice.provider.OlderQuery
import co.abaye.mailtice.provider.OutgoingAttachment
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.gmail.GmailApi
import co.abaye.mailtice.provider.parseAddressList
import co.abaye.mailtice.search.MailSearch
import co.abaye.mailtice.sync.ActionQueuedException
import co.abaye.mailtice.sync.OfflinePrefs
import co.abaye.mailtice.sync.SyncEngine
import co.abaye.mailtice.sync.isNetworkError
import co.abaye.mailtice.translate.Translator
import co.abaye.mailtice.translate.htmlTextSegments
import co.abaye.mailtice.translate.replaceSegments
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.compose_attach
import mailtice.shared.generated.resources.compose_forward_date
import mailtice.shared.generated.resources.compose_forward_from
import mailtice.shared.generated.resources.compose_forward_header
import mailtice.shared.generated.resources.compose_forward_subject
import mailtice.shared.generated.resources.compose_forward_to
import mailtice.shared.generated.resources.compose_quote_header
import mailtice.shared.generated.resources.export_attachments
import mailtice.shared.generated.resources.export_date
import mailtice.shared.generated.resources.export_exported
import mailtice.shared.generated.resources.export_from
import mailtice.shared.generated.resources.export_to
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val SEARCH_DEBOUNCE_MS = 200L

/** Quiet time after the last keystroke before the draft is saved. */
private const val DRAFT_SAVE_DELAY_MS = 2_000L

/** How often the scheduled-send queue is checked. */
private const val SCHEDULE_TICK_MS = 20_000L

/** How often the home dashboard is rebuilt when nothing else asked for it. */
private const val DIGEST_REFRESH_MS = 60_000L

/** Gmail's limit on a message with its attachments. */
private const val MAX_ATTACHMENTS_BYTES = 25L * 1024 * 1024

@AssistedInject
class AppViewModel(
    private val store: SettingsStore,
    private val secrets: SecretStore,
    private val repo: MailRepository,
    private val auth: AuthManager,
    private val authorizer: Authorizer,
    private val gmail: GmailApi,
    private val imap: ImapBackend,
    private val autoConfig: ImapAutoConfig,
    private val sync: SyncEngine,
    private val notifications: MailNotifications,
    private val translator: Translator,
    @Io private val io: CoroutineDispatcher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    @Assisted private val onQuit: () -> Unit = {},
) : ViewModel() {

    @AssistedFactory
    fun interface Factory {
        fun create(onQuit: () -> Unit): AppViewModel
    }

    private val job = SupervisorJob()

    @StructuredScope
    private val scope = CoroutineScope(job + dispatcher)

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val backStack: NavBackStack<AppKey> = NavBackStack(if (_state.value.data.settings.openHomeAtStart) AppKey.Home else AppKey.Inbox)

    private val _raiseWindow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Asks the desktop host to show and focus the window (a notification was clicked). */
    val raiseWindow: SharedFlow<Unit> = _raiseWindow.asSharedFlow()

    private var saveJob: Job? = null
    private var signInJob: Job? = null

    init {
        observeDatabase()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sync.newMail.collect { notifications.announce(it, _state.value.data.settings) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            NotificationActions.events.collect(::onNotificationAction)
        }
        scope.launch { sync.statuses.collect { s -> mutate { it.copy(statuses = s) } } }
        scope.launch { sync.errors.collect { e -> mutate { it.copy(syncErrors = e) } } }
        scope.launch { sync.plans.collect { p -> mutate { it.copy(pollPlans = p) } } }
        refreshStorage()
        scope.launch {
            val profiles = withContext(io) { runCatching { authorizer.browserProfiles() }.getOrDefault(emptyList()) }
            mutate { it.copy(browserProfiles = profiles) }
        }
    }

    override fun onCleared() {
        sync.stopAll()
        job.cancel()
        super.onCleared()
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeDatabase() {
        scope.launch {
            repo.accounts.collect { accounts ->
                mutate { it.copy(accounts = accounts) }
                reconcileSync(accounts)
            }
        }
        scope.launch {
            repo.allFolders.collect { folders -> mutate { it.copy(folders = folders.groupBy { f -> f.accountId }) } }
        }
        scope.launch {
            repo.unreadCounts.collect { counts ->
                mutate { it.copy(unread = counts) }
                refreshDigests()
            }
        }
        scope.launch { sync.lastSynced.collect { t -> mutate { it.copy(lastSynced = t) } } }
        scope.launch { repo.identities.collect { ids -> mutate { it.copy(identities = ids) } } }
        // Rounds that bring nothing new change no count; the dashboard still wants "checked" and the week fresh.
        scope.launch {
            while (true) {
                delay(DIGEST_REFRESH_MS)
                refreshDigests()
            }
        }
        scope.launch { repo.unreadByFolder.collect { counts -> mutate { it.copy(unreadByFolder = counts) } } }
        scope.launch { repo.scheduled.collect { queue -> mutate { it.copy(scheduled = queue) } } }
        // Drafts save themselves two seconds after the typing stops.
        scope.launch {
            _state.map { s -> s.compose?.let { it.draftId to it.draftSignature() } }
                .distinctUntilChanged()
                .debounce(DRAFT_SAVE_DELAY_MS)
                .collect { saveDraft() }
        }
        // The scheduled-send queue goes out from here while the app runs (tray included).
        scope.launch {
            while (true) {
                sendDueScheduled()
                delay(SCHEDULE_TICK_MS)
            }
        }
        scope.launch {
            _state.map { Triple(it.filter, it.localLimit, it.conversationView) }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { (f, limit, conversations) ->
                    val custom = f.folderId.isNotEmpty()
                    if (!custom && f.view == MailView.Scheduled) return@flatMapLatest flowOf(emptyList<MailMessage>() to emptyMap())
                    repo.inbox(
                        InboxQuery(
                            accountId = f.accountId,
                            folderId = f.folderId,
                            unreadOnly = f.unreadOnly,
                            attachmentsOnly = f.attachmentsOnly,
                            flaggedOnly = !custom && f.view == MailView.Starred,
                            role = if (custom) "" else f.view.role?.name.orEmpty(),
                            search = MailSearch.parse(f.query, Platform.now()),
                            limit = limit,
                        ),
                    ).map { list ->
                        // Conversation view: each row's whole stored conversation, read with the rows.
                        list to if (conversations) withContext(io) { repo.conversationsOf(list) } else emptyMap()
                    }
                }
                .collect { (list, members) ->
                    mutate { s ->
                        val keys = (list + s.older.items).map { it.key }.toSet()
                        // A message that got stored (a sync caught up with it) is no longer "older".
                        val stored = list.map { it.key }.toSet()
                        s.copy(
                            inbox = list,
                            threadMembers = members,
                            reader = s.reader?.let { r -> refreshThread(r, members) },
                            older = s.older.copy(items = s.older.items.filter { it.key !in stored }),
                            selection = s.selection.filterTo(mutableSetOf()) { it in keys },
                        )
                    }
                }
        }
    }

    fun onIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.Quit -> onQuit()

            is AppIntent.Navigate -> navigate(intent.destination)

            AppIntent.Back -> back()

            AppIntent.StartAddAccount -> mutate { it.copy(addAccount = AddAccountStep.ChooseProvider, message = null) }

            is AppIntent.ChooseProvider -> chooseProvider(intent.kind)

            is AppIntent.UpdateImapForm -> mutate { s -> s.copy(addAccount = AddAccountStep.Imap(intent.form.copy(error = null))) }

            AppIntent.DetectImapServer -> detectServer()

            AppIntent.SubmitImapForm -> submitImap()

            AppIntent.CloseAddAccount -> {
                signInJob?.cancel()
                mutate { it.copy(addAccount = null) }
            }

            is AppIntent.Reconnect -> reconnect(intent.accountId)

            AppIntent.CancelSignIn -> {
                signInJob?.cancel()
                // Adding: back to the provider list. Reconnecting: there is nothing to go back to.
                mutate { s ->
                    val step = s.addAccount as? AddAccountStep.SignIn
                    s.copy(addAccount = if (step?.reconnectId != null) null else AddAccountStep.ChooseProvider)
                }
            }

            AppIntent.RetrySignIn -> {
                val step = _state.value.addAccount as? AddAccountStep.SignIn ?: return
                val existing = step.reconnectId?.let { _state.value.account(it) }
                if (DemoMode.enabled && existing == null) addDemoAccount(step.kind) else signInOAuth(step.kind, existing, step.profileKey)
            }

            is AppIntent.ChooseBrowser -> chooseBrowser(intent.profileKey)

            is AppIntent.OpenAccount -> {
                mutate { it.copy(addAccount = null) }
                navigate(AppKey.Inbox)
                onIntent(AppIntent.SetFilterAccount(intent.accountId))
            }

            is AppIntent.RemoveAccount -> mutate { it.copy(dialog = AppDialog.ConfirmRemove(intent.accountId)) }

            is AppIntent.ClearAccountCache -> mutate { it.copy(dialog = AppDialog.ConfirmClearCache(intent.accountId)) }

            is AppIntent.SetAccountLabel -> updateAccount(intent.accountId) { it.copy(label = intent.label) }

            is AppIntent.CycleAccountColor -> updateAccount(intent.accountId) { it.copy(color = it.color.next()) }

            is AppIntent.SetAccountNotify -> updateAccount(intent.accountId) { it.copy(notify = intent.on) }

            is AppIntent.SetRetention -> setRetention(intent.accountId, intent.days)

            is AppIntent.SetFolderPrefs -> setFolderPrefs(intent)

            is AppIntent.RefreshFolders -> background {
                _state.value.account(intent.accountId)?.let { sync.refreshFolders(it) }
            }

            is AppIntent.SetFilterAccount -> {
                mutate { it.copy(filter = it.filter.copy(accountId = intent.accountId, folderId = ""), selection = emptySet()) }
                ensureSynced()
            }

            is AppIntent.SetFilterFolder -> {
                mutate { it.copy(filter = it.filter.copy(folderId = intent.folderId), selection = emptySet()) }
                ensureSynced()
            }

            AppIntent.LoadOlder -> loadOlder()

            is AppIntent.SetView -> {
                mutate { it.copy(filter = it.filter.copy(view = intent.view, folderId = ""), selection = emptySet()) }
                ensureSynced()
            }

            is AppIntent.SetUnreadOnly -> mutate { it.copy(filter = it.filter.copy(unreadOnly = intent.on)) }

            is AppIntent.SetAttachmentsOnly -> mutate { it.copy(filter = it.filter.copy(attachmentsOnly = intent.on)) }

            is AppIntent.Trash -> trash(intent.message)

            AppIntent.ToggleSidebar -> settings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }

            is AppIntent.SetListFraction -> settings { it.copy(listFraction = intent.fraction.coerceIn(ListFractionRange)) }

            is AppIntent.ToggleSelect -> mutate { s ->
                val key = intent.message.key
                s.copy(selection = if (key in s.selection) s.selection - key else s.selection + key)
            }

            is AppIntent.ToggleSelectMany -> mutate { s ->
                val keys = intent.messages.map { it.key }.toSet()
                s.copy(selection = if (s.selection.containsAll(keys)) s.selection - keys else s.selection + keys)
            }

            AppIntent.SelectAll -> mutate { s -> s.copy(selection = s.visibleMessages.map { it.key }.toSet()) }

            AppIntent.ClearSelection -> mutate { it.copy(selection = emptySet()) }

            is AppIntent.BulkSetRead -> {
                updateOlder(_state.value.selection) { it.copy(unread = !intent.read) }
                bulk(whole = true) { m, caps -> if (caps.markRead && m.unread == intent.read) sync.setRead(m, intent.read) }
            }

            AppIntent.BulkArchive -> {
                updateOlder(_state.value.selection) { null }
                bulk(closeReader = true) { m, caps -> if (caps.archive) sync.archive(m) }
            }

            AppIntent.BulkTrash -> {
                updateOlder(_state.value.selection) { null }
                bulk(closeReader = true, whole = true) { m, caps -> if (caps.trash) sync.trash(m) }
            }

            AppIntent.BulkDownloadAttachments -> {
                val messages = _state.value.selectedMessages.filter { it.hasAttachments }
                downloadAll(messages, folder = "selection-${Platform.now() / 1000}")
            }

            is AppIntent.DownloadAttachments -> downloadOne(intent.message, intent.index)

            is AppIntent.DownloadThreadAttachments -> working {
                val thread = withContext(io) { repo.threadOf(intent.message) }.filter { it.hasAttachments }
                saveAttachments(thread, folder = ThreadExport.baseSubject(intent.message.subject))
            }

            is AppIntent.ExportThread -> exportThread(intent.message, intent.format)

            is AppIntent.StartCompose -> startCompose(intent.mode, intent.message)

            is AppIntent.UpdateCompose -> mutate { s ->
                val current = s.compose ?: return@mutate s
                // Sending, threading, body, quote and files are owned here; the UI edits the fields.
                s.copy(
                    compose = intent.draft.copy(
                        draftId = current.draftId,
                        sending = current.sending,
                        inReplyTo = current.inReplyTo,
                        references = current.references,
                        threadId = current.threadId,
                        body = current.body,
                        html = current.html,
                        quote = current.quote,
                        quoteHtml = current.quoteHtml,
                        attachments = current.attachments,
                        scheduledId = current.scheduledId,
                        initialHtml = current.initialHtml,
                        fromEmail = current.fromEmail,
                        editorVersion = current.editorVersion,
                        draftHandle = current.draftHandle,
                        draftAccountId = current.draftAccountId,
                        draftSave = current.draftSave,
                        savedSignature = current.savedSignature,
                        invalidAddresses = false,
                    ),
                )
            }

            is AppIntent.ComposeBody -> mutate { s -> s.copy(compose = s.compose?.copy(body = intent.text, html = intent.html)) }

            is AppIntent.ComposeWindow -> mutate { s -> s.copy(compose = s.compose?.copy(window = intent.mode)) }

            is AppIntent.ComposeSuggest -> suggestContacts(intent.query)

            AppIntent.ComposeAttach -> attachFiles()

            is AppIntent.ComposeAddFiles -> addFiles(intent.files)

            is AppIntent.ComposeRemoveAttachment -> mutate { s ->
                s.copy(compose = s.compose?.let { d -> d.copy(attachments = d.attachments.filter { it.id != intent.id }) })
            }

            is AppIntent.ScheduleCompose -> scheduleCompose(intent.sendAt)

            is AppIntent.SendScheduledNow -> background {
                _state.value.scheduled.firstOrNull { it.id == intent.id }?.let { repo.schedule(it.copy(sendAt = Platform.now())) }
                sendDueScheduled()
            }

            is AppIntent.CancelScheduled -> background {
                repo.unschedule(intent.id)
                mutate { it.copy(message = AppMessage.ScheduleCancelled) }
            }

            is AppIntent.EditScheduled -> editScheduled(intent.id)

            AppIntent.SendCompose -> sendCompose()

            AppIntent.CloseCompose -> closeCompose(discard = false)

            AppIntent.DiscardCompose -> closeCompose(discard = true)

            is AppIntent.SetSearchQuery -> mutate { it.copy(filter = it.filter.copy(query = intent.query)) }

            is AppIntent.OpenMail -> openMail(intent.message)

            is AppIntent.ExpandInThread -> _state.value.reader?.let {
                showMessage(intent.message, it.thread, markRead = listOf(intent.message), it.bodies)
            }

            is AppIntent.SetConversationRead -> intent.messages.filter { it.unread != intent.read }.forEach { m ->
                updateOlder(setOf(m.key)) { it.copy(unread = !intent.read) }
                serverAction { sync.setRead(m, intent.read) }
            }

            is AppIntent.ArchiveConversation -> {
                val s = _state.value
                val inInbox = intent.messages.filter { m ->
                    val inbox = s.foldersOf(m.accountId).filter { it.role == FolderRole.Inbox }.map { it.id }
                    m.folderIds.any { it in inbox }
                }
                if (inInbox.isEmpty()) return
                if (intent.messages.any { it.key in s.readerKeys }) onIntent(AppIntent.CloseReader)
                inInbox.forEach(::archive)
            }

            is AppIntent.TrashConversation -> {
                if (intent.messages.any { it.key in _state.value.readerKeys }) onIntent(AppIntent.CloseReader)
                intent.messages.forEach(::trash)
            }

            AppIntent.CloseReader -> {
                mutate { it.copy(reader = null) }
                if (backStack.lastOrNull() == AppKey.Reader) back()
            }

            is AppIntent.SetRead -> {
                updateOlder(setOf(intent.message.key)) { it.copy(unread = !intent.read) }
                serverAction { sync.setRead(intent.message, intent.read) }
            }

            is AppIntent.Archive -> archive(intent.message)

            is AppIntent.OpenInWeb -> openInWeb(intent.message)

            is AppIntent.OpenHtml -> background {
                sync.body(intent.message).html.takeIf { it.isNotBlank() }?.let(Platform::openHtml)
            }

            AppIntent.RefreshNow -> sync.refreshNow()

            is AppIntent.SetTheme -> settings { it.copy(theme = intent.mode) }

            is AppIntent.SetAccent -> settings { it.copy(accent = intent.accent) }

            is AppIntent.SetDensity -> settings { it.copy(density = intent.density) }

            is AppIntent.SetFont -> settings { it.copy(font = intent.font) }

            is AppIntent.SetShowHebrewDate -> settings { it.copy(showHebrewDate = intent.show) }

            is AppIntent.SetHebrewDateAtSunset -> settings { it.copy(hebrewDateAtSunset = intent.atSunset) }

            is AppIntent.SetSunsetCity -> settings { it.copy(sunsetCity = intent.city) }

            is AppIntent.SetOfferTranslation -> settings { it.copy(offerTranslation = intent.offer) }

            is AppIntent.SetLoadRemoteImages -> settings { it.copy(loadRemoteImages = intent.load) }

            is AppIntent.SetDownloadFolder -> settings { it.copy(downloadFolder = intent.path) }

            is AppIntent.ChooseDownloadFolder -> scope.launch {
                Platform.pickFolder(intent.title)?.let { path -> settings { it.copy(downloadFolder = path) } }
            }

            is AppIntent.PreviewAttachment -> previewAttachment(intent.message, intent.index)

            AppIntent.ClosePreview -> closePreview()

            AppIntent.SavePreview -> savePreview()

            AppIntent.OpenPreviewExternally -> _state.value.preview?.let { p ->
                if (p.tempPath.isNotEmpty()) {
                    previewOpenedOutside = true
                    if (!Platform.openFile(p.tempPath)) mutate { it.copy(message = AppMessage.ActionFailed) }
                }
            }

            AppIntent.TranslateMessage -> translateOpenMessage()

            is AppIntent.ShowOriginal -> _state.value.reader?.let { reader ->
                reader.translation?.let { setTranslation(reader.message, it.copy(showOriginal = intent.original)) }
            }

            is AppIntent.SetPaneStyle -> settings { it.copy(paneStyle = intent.style) }

            is AppIntent.SetReadingPane -> settings { it.copy(readingPane = intent.pane) }

            is AppIntent.SetFolderHidden -> settings {
                it.copy(hiddenFolders = if (intent.hidden) it.hiddenFolders + intent.key else it.hiddenFolders - intent.key)
            }

            is AppIntent.SetLabelPinned -> settings {
                it.copy(pinnedLabels = if (intent.pinned) it.pinnedLabels + intent.key else it.pinnedLabels - intent.key)
            }

            is AppIntent.ToggleAccountExpanded -> settings {
                val folded = intent.accountId in it.collapsedAccounts
                it.copy(
                    collapsedAccounts = if (folded) it.collapsedAccounts - intent.accountId else it.collapsedAccounts + intent.accountId,
                )
            }

            is AppIntent.OpenView -> {
                navigateToMail()
                mutate {
                    it.copy(
                        filter = it.filter.copy(accountId = intent.accountId, view = intent.view, folderId = ""),
                        selection = emptySet(),
                    )
                }
                ensureSynced()
            }

            is AppIntent.OpenLabel -> {
                navigateToMail()
                mutate {
                    it.copy(filter = it.filter.copy(accountId = intent.accountId, folderId = intent.folderId), selection = emptySet())
                }
                ensureSynced()
            }

            is AppIntent.CreateLabel -> labelAction(intent.accountId) { account ->
                sync.createLabel(account, intent.name.trim(), intent.color)
            }

            is AppIntent.UpdateLabel -> labelAction(intent.accountId) { account ->
                val newId = sync.updateLabel(account, intent.folderId, intent.name.trim(), intent.color)
                if (newId != intent.folderId) moveLabelKeys(account.id, intent.folderId, newId)
            }

            is AppIntent.DeleteLabel -> labelAction(intent.accountId) { account ->
                sync.deleteLabel(account, intent.folderId)
                moveLabelKeys(account.id, intent.folderId, null)
            }

            is AppIntent.SetUiLanguage -> settings {
                it.copy(uiLanguage = intent.language ?: systemUiLanguage(), uiLanguageAuto = intent.language == null)
            }

            is AppIntent.SetPollInterval -> {
                settings { it.copy(pollSeconds = intent.seconds.takeIf { s -> s in PollIntervals } ?: it.pollSeconds) }
                reconcileSync()
            }

            is AppIntent.SetOpenHomeAtStart -> settings { it.copy(openHomeAtStart = intent.on) }

            is AppIntent.SetConversationView -> {
                mutate { it.copy(selection = emptySet()) }
                settings { it.copy(conversationView = intent.on) }
            }

            is AppIntent.MoveAccount -> {
                val ids = _state.value.accounts.map { it.id }.toMutableList()
                val from = ids.indexOf(intent.accountId)
                val to = if (intent.up) from - 1 else from + 1
                if (from >= 0 && to in ids.indices) {
                    ids.add(to, ids.removeAt(from))
                    background { repo.reorderAccounts(ids) }
                }
            }

            is AppIntent.SetComposeFrom -> setComposeFrom(intent.accountId, intent.email)

            is AppIntent.SetSmartPolling -> {
                settings { it.copy(smartPolling = intent.smart) }
                reconcileSync()
            }

            is AppIntent.SetOfflineMode -> {
                settings { it.copy(offlineMode = intent.on) }
                reconcileSync()
                if (intent.on) sync.refreshNow()
            }

            is AppIntent.SetOfflineAttachments -> {
                settings {
                    it.copy(
                        offlineAttachmentsMb =
                        intent.mb.takeIf { mb -> mb in OfflineAttachmentLimits } ?: it.offlineAttachmentsMb,
                    )
                }
                reconcileSync()
            }

            is AppIntent.SetNotifications -> settings { it.copy(notificationsEnabled = intent.on) }

            is AppIntent.SetCloseToTray -> settings { it.copy(closeToTray = intent.on) }

            is AppIntent.SetLaunchAtLogin -> setLaunchAtLogin(intent.on)

            AppIntent.RefreshStorage -> refreshStorage()

            AppIntent.CompactDatabase -> background {
                repo.vacuum()
                refreshStorage()
                mutate { it.copy(message = AppMessage.DatabaseCompacted) }
            }

            is AppIntent.OpenUrl -> Platform.openUrl(intent.url)

            AppIntent.ResetApp -> mutate { it.copy(dialog = AppDialog.ConfirmReset) }

            AppIntent.ConfirmDialog -> confirmDialog()

            AppIntent.DismissDialog -> mutate { it.copy(dialog = AppDialog.Hidden) }

            AppIntent.DismissMessage -> mutate { it.copy(message = null) }
        }
    }

    private fun restore(): AppState {
        var data = store.load()
        if (data.settings.uiLanguageAuto) {
            data = data.copy(settings = data.settings.copy(uiLanguage = systemUiLanguage()))
        }
        return AppState(data = data, availableProviders = availableProviders())
    }

    /**
     * Hide what cannot work: an OAuth provider with no client id, or one this platform cannot sign in to.
     * Demo mode signs in to nothing, so every provider is offered.
     */
    private fun availableProviders(): List<ProviderKind> = ProviderKind.entries.filter { kind ->
        if (DemoMode.enabled) return@filter true
        val provider = OAuthProvider.of(kind) ?: return@filter true
        provider.clientId.isNotBlank() && authorizer.supports(provider)
    }

    // ---- navigation -------------------------------------------------------------------------

    private fun navigate(key: AppKey) {
        mutate { it.copy(message = null) }
        if (key.isMain()) {
            if (backStack.size == 1 && backStack[0] == key) return
            // One snapshot, so NavDisplay never sees an empty stack.
            Snapshot.withMutableSnapshot {
                backStack.clear()
                backStack.add(key)
            }
        } else if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    private fun back() {
        // removeAt, not removeLast(): the latter resolves to a Java 21 List method.
        if (backStack.size > 1) {
            if (backStack.last() == AppKey.Reader) mutate { it.copy(reader = null) }
            backStack.removeAt(backStack.lastIndex)
        }
    }

    // ---- adding accounts --------------------------------------------------------------------

    private fun chooseProvider(kind: ProviderKind) {
        if (kind == ProviderKind.Imap) {
            mutate { it.copy(addAccount = AddAccountStep.Imap(ImapForm())) }
            return
        }
        // Several browser profiles: ask which one first (demo too, so the picker can be seen).
        if (_state.value.browserProfiles.size > 1) {
            mutate { it.copy(addAccount = AddAccountStep.ChooseBrowser(kind)) }
            return
        }
        if (DemoMode.enabled) {
            addDemoAccount(kind)
            return
        }
        signInOAuth(kind, existing = null)
    }

    private fun chooseBrowser(profileKey: String?) {
        val step = _state.value.addAccount as? AddAccountStep.ChooseBrowser ?: return
        settings { it.copy(browserProfile = profileKey.orEmpty()) }
        val existing = step.reconnectId?.let { _state.value.account(it) }
        if (DemoMode.enabled && existing == null) addDemoAccount(step.kind) else signInOAuth(step.kind, existing, profileKey)
    }

    /** Demo mode: an OAuth provider "signs in" at once with a made-up address. */
    private fun addDemoAccount(kind: ProviderKind) {
        val index = _state.value.accounts.count { it.kind == kind } + 1
        val account = Account(
            id = newId(),
            kind = kind,
            email = "demo.${kind.name.lowercase()}$index@example.com",
            color = nextColor(),
            capabilities = if (kind == ProviderKind.Gmail) Capabilities.Gmail else DemoAccounts.imapCapabilities(),
            imap = oauthServer(kind),
        )
        signInJob?.cancel()
        signInJob = scope.launch {
            signInStep(AddAccountStep.SignIn(kind, SignInPhase.Browser))
            delay(1_500)
            signInStep(AddAccountStep.SignIn(kind, SignInPhase.Connecting))
            delay(700)
            withContext(io) { repo.addAccount(account) }
            signInStep(AddAccountStep.SignIn(kind, SignInPhase.Done, accountId = account.id, email = account.email))
        }
    }

    /** Moves the dialog to [step], unless the user closed it in the meantime. */
    private fun signInStep(step: AddAccountStep.SignIn) = mutate { s ->
        if (s.addAccount == null && step.phase != SignInPhase.Browser) s else s.copy(addAccount = step)
    }

    private fun signInOAuth(kind: ProviderKind, existing: Account?, profileKey: String? = null) {
        val provider = OAuthProvider.of(kind) ?: return
        if (!provider.isConfigured) {
            mutate { it.copy(message = AppMessage.NotConfigured, addAccount = null) }
            return
        }
        if (!authorizer.supports(provider)) {
            mutate { it.copy(message = AppMessage.ProviderNotSupported, addAccount = null) }
            return
        }
        signInJob?.cancel()
        val reconnectId = existing?.id
        val profile = _state.value.browserProfiles.firstOrNull { it.key == profileKey }
        signInJob = scope.launch {
            signInStep(AddAccountStep.SignIn(kind, SignInPhase.Browser, reconnectId, profileKey = profileKey))
            try {
                val code = authorizer.authorize(provider, loginHint = existing?.email, profile = profile)
                // Back from the browser: Mailtice comes to the front while it finishes.
                _raiseWindow.tryEmit(Unit)
                signInStep(AddAccountStep.SignIn(kind, SignInPhase.Connecting, reconnectId, profileKey = profileKey))
                val tokens = auth.exchange(provider, code)
                val email = when (kind) {
                    ProviderKind.Gmail -> gmail.profile(tokens.accessToken).emailAddress
                    else -> auth.emailFromIdToken(tokens.idToken)
                } ?: error("Provider returned no address")
                val known = existing ?: _state.value.accounts.firstOrNull { it.kind == kind && it.email.equals(email, ignoreCase = true) }
                val id = known?.id ?: newId()
                auth.rememberTokens(id, tokens, code.webClient)
                withContext(io) {
                    if (known == null) {
                        repo.addAccount(
                            Account(
                                id = id,
                                kind = kind,
                                email = email,
                                color = nextColor(),
                                capabilities = if (kind == ProviderKind.Gmail) Capabilities.Gmail else Capabilities(),
                                imap = oauthServer(kind),
                                username = email,
                            ),
                        )
                    }
                }
                if (known != null) sync.restart(scope, known.id)
                signInStep(
                    AddAccountStep.SignIn(kind, SignInPhase.Done, reconnectId, accountId = id, email = email, profileKey = profileKey),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthCancelledException) {
                // Closed the browser tab or denied consent: offer the same provider again.
                signInStep(AddAccountStep.SignIn(kind, SignInPhase.Failed, reconnectId, profileKey = profileKey))
            } catch (e: Exception) {
                println("Sign-in failed: ${e::class.simpleName}")
                signInStep(AddAccountStep.SignIn(kind, SignInPhase.Failed, reconnectId, profileKey = profileKey))
            }
        }
    }

    private fun oauthServer(kind: ProviderKind): ImapServer? = when (kind) {
        ProviderKind.Microsoft -> ImapServer("outlook.office365.com")
        ProviderKind.Yahoo -> ImapServer("imap.mail.yahoo.com")
        else -> null
    }

    private fun reconnect(accountId: String) {
        val account = _state.value.account(accountId) ?: return
        if (DemoMode.enabled && account.kind.oauth) {
            sync.restart(scope, account.id)
            mutate { it.copy(message = AppMessage.AccountReconnected) }
        } else if (account.kind.oauth && _state.value.browserProfiles.size > 1) {
            mutate { it.copy(addAccount = AddAccountStep.ChooseBrowser(account.kind, reconnectId = account.id)) }
        } else if (account.kind.oauth) {
            signInOAuth(account.kind, existing = account)
        } else {
            val server = account.imap ?: return
            mutate {
                it.copy(
                    addAccount = AddAccountStep.Imap(
                        ImapForm(
                            email = account.email,
                            host = server.host,
                            port = server.port.toString(),
                            security = server.security,
                            username = account.username,
                            reconnectId = account.id,
                        ),
                    ),
                )
            }
        }
    }

    private fun imapForm(): ImapForm? = (_state.value.addAccount as? AddAccountStep.Imap)?.form

    private fun setForm(block: (ImapForm) -> ImapForm) = mutate { s ->
        val step = s.addAccount as? AddAccountStep.Imap ?: return@mutate s
        s.copy(addAccount = step.copy(form = block(step.form)))
    }

    private fun detectServer() {
        val form = imapForm() ?: return
        if ('@' !in form.email) return
        scope.launch {
            setForm { it.copy(busy = true) }
            val server = if (DemoMode.enabled) demoServer(form.email) else autoConfig.lookup(form.email)
            setForm {
                it.copy(
                    busy = false,
                    host = server.host,
                    port = server.port.toString(),
                    security = server.security,
                    username = it.username.ifBlank { it.email },
                )
            }
        }
    }

    private fun submitImap() {
        val form = imapForm() ?: return
        if (form.busy || '@' !in form.email || form.password.isBlank()) return
        scope.launch {
            setForm { it.copy(busy = true, error = null) }
            try {
                val server = if (form.host.isBlank()) {
                    if (DemoMode.enabled) demoServer(form.email) else autoConfig.lookup(form.email)
                } else {
                    ImapServer(form.host.trim(), form.port.toIntOrNull() ?: 993, form.security)
                }
                val username = form.username.ifBlank { form.email }.trim()
                val capabilities = if (DemoMode.enabled) DemoAccounts.imapCapabilities() else imap.verify(server, username, form.password)
                val id = form.reconnectId ?: newId()
                auth.rememberPassword(id, form.password)
                withContext(io) {
                    if (form.reconnectId == null) {
                        repo.addAccount(
                            Account(
                                id = id,
                                kind = ProviderKind.Imap,
                                email = form.email.trim(),
                                color = nextColor(),
                                capabilities = capabilities,
                                imap = server,
                                username = username,
                            ),
                        )
                    } else {
                        repo.updateServer(id, server, username)
                        repo.updateCapabilities(id, capabilities)
                    }
                }
                if (form.reconnectId != null) sync.restart(scope, id)
                mutate {
                    it.copy(
                        addAccount = AddAccountStep.SignIn(
                            ProviderKind.Imap,
                            SignInPhase.Done,
                            form.reconnectId,
                            accountId = id,
                            email = form.email.trim(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImapLoginException) {
                setForm { it.copy(busy = false, error = e.reason) }
            } catch (e: Exception) {
                println("IMAP add failed: ${e::class.simpleName}")
                setForm { it.copy(busy = false, error = ImapLoginException.Reason.Other) }
            }
        }
    }

    private fun demoServer(email: String) = ImapServer("imap.${email.substringAfter('@')}")

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()

    private fun nextColor(): AccountColor {
        val used = _state.value.accounts.map { it.color }.toSet()
        return AccountColor.entries.firstOrNull { it !in used }
            ?: AccountColor.entries[_state.value.accounts.size % AccountColor.entries.size]
    }

    // ---- account settings -------------------------------------------------------------------

    private fun updateAccount(accountId: String, block: (Account) -> Account) {
        val account = _state.value.account(accountId) ?: return
        background { repo.updatePrefs(block(account)) }
    }

    private fun setRetention(accountId: String, days: Int) {
        if (days !in RetentionOptions) return
        val account = _state.value.account(accountId) ?: return
        val updated = account.copy(retentionDays = days)
        background {
            repo.updatePrefs(updated)
            // Shorter: drop what fell out now. Longer: the next round fetches the older mail.
            repo.prune(updated)
            if (days == 0 || days > account.retentionDays) repo.resetCursor(accountId)
            refreshStorage()
            sync.refreshNow()
        }
    }

    private fun setFolderPrefs(intent: AppIntent.SetFolderPrefs) {
        val account = _state.value.account(intent.accountId) ?: return
        val before = _state.value.foldersOf(intent.accountId).firstOrNull { it.id == intent.folderId }
        background {
            repo.setFolderPrefs(intent.accountId, intent.folderId, intent.sync, intent.notify && intent.sync)
            if (before != null && before.sync && !intent.sync) {
                repo.clearFolderLinksFor(intent.accountId, intent.folderId)
                repo.prune(account)
                refreshStorage()
            }
            if (before != null && !before.sync && intent.sync) {
                // Gmail lists labels on a full pass only; IMAP picks a new folder up by itself.
                if (account.kind == ProviderKind.Gmail) repo.resetCursor(account.id)
                sync.refreshNow()
            }
        }
    }

    private fun removeAccount(accountId: String) {
        val account = _state.value.account(accountId) ?: return
        mutate { s ->
            s.copy(
                filter = if (s.filter.accountId == accountId) InboxFilter() else s.filter,
                reader = s.reader?.takeIf { it.message.accountId != accountId },
            )
        }
        if (backStack.lastOrNull() == AppKey.AccountDetail(accountId)) back()
        scope.launch {
            sync.forget(account)
            withContext(io) { repo.deleteAccount(accountId) }
            runCatching { auth.revoke(account) }
            refreshStorage()
        }
    }

    private fun clearCache(accountId: String) {
        val account = _state.value.account(accountId) ?: return
        background {
            repo.clearAccountMail(account)
            repo.vacuum()
            refreshStorage()
            mutate { it.copy(message = AppMessage.CacheCleared) }
            sync.refreshNow()
        }
    }

    // ---- inbox & reader ---------------------------------------------------------------------

    /**
     * Opens [message] - in conversation view, its whole conversation, with the first unread message
     * open (the newest when all are read) and the conversation marked read, as Gmail does.
     */
    private fun openMail(message: MailMessage) {
        if (isDraft(message)) {
            openDraft(message)
            return
        }
        val s = _state.value
        if (!s.conversationView) {
            showMessage(message, emptyList(), markRead = listOf(message))
            return
        }
        val key = conversationKey(message)
        val thread = (s.visibleMessages.filter { conversationKey(it) == key } + s.threadMembers[key].orEmpty())
            .distinctBy { it.key }.sortedBy { it.receivedAt }.ifEmpty { listOf(message) }
        val focus = thread.firstOrNull { it.unread } ?: thread.last()
        showMessage(focus, thread, markRead = thread)
    }

    /**
     * Shows [message] in the reader, [thread] folded around it, loads its body and marks [markRead]
     * read. A body among [bodies] (read ahead) shows at once; the rest of the conversation's stored
     * bodies are read ahead for the next message opened.
     */
    private fun showMessage(
        message: MailMessage,
        thread: List<MailMessage>,
        markRead: List<MailMessage>,
        bodies: Map<String, MailBody> = emptyMap(),
    ) {
        val ready = bodies[message.key]
        val translated = translations[translationKey(message, _state.value.data.settings.uiLanguage.code)]
        val opened = Reader(message, ready, translation = translated.takeIf { ready != null }, thread = thread, bodies = bodies)
        mutate { it.copy(reader = opened) }
        if (!Platform.isDesktop) navigate(AppKey.Reader)
        if (thread.size > 1 && bodies.isEmpty()) {
            scope.launch {
                val stored = withContext(io) { thread.mapNotNull { m -> repo.body(m.accountId, m.id)?.let { m.key to it } }.toMap() }
                mutate { s -> s.copy(reader = s.reader?.takeIf { it.thread.isNotEmpty() }?.let { it.copy(bodies = stored + it.bodies) }) }
            }
        }
        scope.launch {
            val body = ready ?: runCatching { withContext(io) { sync.body(message) } }.getOrNull()
            mutate { s ->
                val current = s.reader
                if (current?.message?.key != message.key) {
                    s
                } else {
                    // A message translated earlier this session opens translated again.
                    val again = translations[translationKey(message, s.data.settings.uiLanguage.code)]
                    val known = if (body != null) current.bodies + (message.key to body) else current.bodies
                    s.copy(
                        reader = current.copy(body = body, failed = body == null, translation = again, bodies = known),
                    )
                }
            }
            if (body != null) scope.launch { loadInlineImages(message, body) }
            // Opening means reading, where the account can say so.
            val unread = markRead.filter { m -> m.unread && _state.value.account(m.accountId)?.capabilities?.markRead == true }
            if (unread.isEmpty()) return@launch
            val keys = unread.map { it.key }.toSet()
            updateOlder(keys) { it.copy(unread = false) }
            mutate { s ->
                s.copy(
                    reader = s.reader?.let { r ->
                        r.copy(
                            thread = r.thread.map {
                                if (it.key in
                                    keys
                                ) {
                                    it.copy(unread = false)
                                } else {
                                    it
                                }
                            },
                        )
                    },
                )
            }
            unread.forEach { m -> runCatching { sync.setRead(m, read = true) } }
        }
    }

    /**
     * The open conversation with what the list just read from the database (a new reply, a change);
     * the open message stays even if it has left the database (older mail shown from the server).
     */
    private fun refreshThread(reader: Reader, members: Map<String, List<MailMessage>>): Reader {
        if (reader.thread.isEmpty()) return reader
        val fresh = members[conversationKey(reader.message)] ?: return reader
        val thread = (fresh + reader.thread.filter { it.key == reader.message.key }).distinctBy { it.key }.sortedBy { it.receivedAt }
        return reader.copy(thread = thread)
    }

    private fun archive(message: MailMessage) {
        val account = _state.value.account(message.accountId) ?: return
        if (!account.capabilities.archive) return
        updateOlder(setOf(message.key)) { null }
        if (_state.value.reader?.message?.id == message.id) onIntent(AppIntent.CloseReader)
        serverAction { sync.archive(message) }
    }

    private fun trash(message: MailMessage) {
        val account = _state.value.account(message.accountId) ?: return
        if (!account.capabilities.trash) return
        updateOlder(setOf(message.key)) { null }
        if (_state.value.reader?.message?.id == message.id) onIntent(AppIntent.CloseReader)
        scope.launch {
            try {
                withContext(io) { sync.trash(message) }
                mutate { it.copy(message = AppMessage.MovedToTrash) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ActionQueuedException) {
                mutate { it.copy(message = AppMessage.ActionQueued) }
            } catch (e: Exception) {
                println("Trash failed: ${e::class.simpleName}")
                mutate { it.copy(message = AppMessage.ActionFailed) }
            }
        }
    }

    // ---- older mail from the server --------------------------------------------------------

    /**
     * The end of the list: first more stored rows (the list reads them [LOCAL_PAGE] at a time), then
     * the server, per account in scope, for mail older than the oldest row of that account already
     * shown - or than its retention window when nothing is shown. An account that returns nothing
     * is done for this list. A result for a list the user has since left is dropped.
     */
    private fun loadOlder() {
        val s = _state.value
        if (s.older.loading) return
        if (s.inbox.size >= s.localLimit) {
            mutate { it.copy(localLimit = it.localLimit + LOCAL_PAGE) }
            return
        }
        val filter = s.filter
        val accounts = s.scopeAccounts.filter { it.id !in s.older.exhausted }
        if (accounts.isEmpty()) return
        val shown = s.visibleMessages
        mutate { it.copy(older = it.older.copy(loading = true, failed = false)) }
        scope.launch {
            val results = accounts.map { account ->
                async { account.id to runCatching { olderFor(s, account, filter, shown) }.getOrNull() }
            }.awaitAll()
            mutate { st ->
                if (st.filter != filter) return@mutate st
                val known = st.visibleMessages.map { it.key }.toSet()
                val fresh = results.flatMap { it.second.orEmpty() }.filter { it.key !in known }.distinctBy { it.key }
                st.copy(
                    older = st.older.copy(
                        items = st.older.items + fresh,
                        loading = false,
                        exhausted = st.older.exhausted + results.filter { it.second?.isEmpty() == true }.map { it.first },
                        failed = results.any { it.second == null },
                    ),
                )
            }
        }
    }

    private suspend fun olderFor(s: AppState, account: Account, filter: InboxFilter, shown: List<MailMessage>): List<MailMessage> {
        val starred = filter.folderId.isEmpty() && filter.view == MailView.Starred
        val folders = when {
            filter.folderId.isNotEmpty() -> s.foldersOf(account.id).filter { it.id == filter.folderId }
            starred -> emptyList()
            else -> s.foldersOf(account.id).filter { it.role == filter.view.role }
        }
        if (folders.isEmpty() && !starred) return emptyList()
        val oldest = shown.filter { it.accountId == account.id }.minOfOrNull { it.receivedAt }
        val floor = account.retentionDays.takeIf { it > 0 }?.let { Platform.now() - it * MailRepository.DAY_MS }
        val query = OlderQuery(
            before = oldest ?: floor,
            search = MailSearch.parse(filter.query, Platform.now()),
            unreadOnly = filter.unreadOnly,
            attachmentsOnly = filter.attachmentsOnly,
            flaggedOnly = starred,
        )
        return withContext(io) { sync.olderMessages(account, folders, query) }
    }

    /** Applies [change] to older (unstored) rows among [keys]; returning null drops the row. */
    private fun updateOlder(keys: Set<String>, change: (MailMessage) -> MailMessage?) = mutate { s ->
        if (s.older.items.none { it.key in keys }) return@mutate s
        s.copy(older = s.older.copy(items = s.older.items.mapNotNull { if (it.key in keys) change(it) else it }))
    }

    /**
     * Only the inbox syncs by default. Opening another folder (Sent, Spam, a label...) turns its sync
     * on for the accounts in scope - quietly, without notifications - and asks for a round, so the
     * folder fills within seconds and stays up to date from then on.
     */
    private fun ensureSynced() {
        val s = _state.value
        val f = s.filter
        val targets = s.scopeAccounts.flatMap { account ->
            s.foldersOf(account.id).filter { folder ->
                !folder.sync && if (f.folderId.isNotEmpty()) folder.id == f.folderId else f.view.role != null && folder.role == f.view.role
            }
        }
        targets.forEach { folder -> onIntent(AppIntent.SetFolderPrefs(folder.accountId, folder.id, sync = true, notify = false)) }
    }

    // ---- selection, downloads, export --------------------------------------------------------

    /**
     * Runs [action] for every selected message whose account allows it, then clears the selection.
     * Each message is its own optimistic change, so one refusal does not undo the others.
     */
    private fun bulk(closeReader: Boolean = false, whole: Boolean = false, action: suspend (MailMessage, Capabilities) -> Unit) {
        val s = _state.value
        // [whole]: in conversation view the rest of each checked conversation too (replies in Sent).
        val chosen = if (whole) s.wholeConversations(s.selectedMessages) else s.selectedMessages
        val targets = chosen.mapNotNull { m -> s.account(m.accountId)?.let { m to it.capabilities } }
        if (targets.isEmpty()) return
        if (closeReader && targets.any { it.first.key in s.readerKeys }) onIntent(AppIntent.CloseReader)
        mutate { it.copy(selection = emptySet()) }
        targets.forEach { (m, caps) -> serverAction { action(m, caps) } }
    }

    /** One download or export at a time; [block] reports its own result message. */
    private fun working(block: suspend () -> Unit) {
        if (_state.value.working) return
        mutate { it.copy(working = true, message = AppMessage.Downloading) }
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Download failed: ${e::class.simpleName}")
                mutate { it.copy(message = AppMessage.DownloadFailed) }
            } finally {
                mutate { it.copy(working = false) }
            }
        }
    }

    private fun downloadOne(message: MailMessage, index: Int?) = working {
        saveAttachments(listOf(message), folder = if (index == null) message.subject else "", index = index)
    }

    private fun downloadAll(messages: List<MailMessage>, folder: String) = working { saveAttachments(messages, folder) }

    /** Fetches and saves; on desktop the folder opens with the (first) file selected. */
    private suspend fun saveAttachments(messages: List<MailMessage>, folder: String, index: Int? = null) {
        val saved = mutableListOf<String>()
        messages.forEach { m ->
            val files = withContext(io) { sync.attachments(m, index?.let { setOf(it) }) }
            files.forEach { f -> withContext(io) { Platform.saveDownload(folder, f.name, f.bytes, downloadRoot()) }?.let(saved::add) }
        }
        if (saved.isEmpty()) {
            mutate { it.copy(message = if (messages.isEmpty()) AppMessage.NoAttachments else AppMessage.DownloadFailed) }
            return
        }
        Platform.revealDownload(saved.first())
        mutate { it.copy(message = AppMessage.FilesSaved) }
    }

    private fun exportThread(message: MailMessage, format: ExportFormat) = working {
        val thread = withContext(io) { repo.threadOf(message) }
        val base = safeFileName(ThreadExport.baseSubject(message.subject), "conversation")
        val location = when (format) {
            ExportFormat.Html -> {
                val bodies = thread.map { m -> m to runCatching { withContext(io) { sync.body(m) } }.getOrNull() }
                val language = _state.value.data.settings.uiLanguage.code
                val labels = ExportLabels(
                    from = localizedString(language, Res.string.export_from),
                    to = localizedString(language, Res.string.export_to),
                    date = localizedString(language, Res.string.export_date),
                    attachments = localizedString(language, Res.string.export_attachments),
                    exported = localizedString(language, Res.string.export_exported, thread.size),
                )
                val html = ThreadExport.html(message.subject, bodies, labels) { co.abaye.mailtice.main.formatTime(it, withDate = true) }
                withContext(io) { Platform.saveDownload("", "$base.html", html.encodeToByteArray(), downloadRoot()) }
            }

            ExportFormat.Mail -> {
                val raws = thread.map { m -> m to withContext(io) { sync.rawMessage(m) } }
                if (raws.size == 1) {
                    withContext(io) { Platform.saveDownload("", "$base.eml", raws.first().second, downloadRoot()) }
                } else {
                    withContext(io) { Platform.saveDownload("", "$base.mbox", ThreadExport.mbox(raws), downloadRoot()) }
                }
            }
        }
        if (location == null) {
            mutate { it.copy(message = AppMessage.DownloadFailed) }
        } else {
            Platform.revealDownload(location)
            mutate { it.copy(message = AppMessage.Exported) }
        }
    }

    // ---- compose ----------------------------------------------------------------------------

    /**
     * Opens the editor. A reply goes from the account that received the message, to its sender (and,
     * for reply-all, everyone else on the To line except ourselves); a forward quotes the original
     * with its headers. The original's body and threading headers are fetched in the background and
     * filled in when they arrive, so the editor opens at once.
     */
    private fun startCompose(mode: ComposeMode, message: MailMessage?) {
        val s = _state.value
        val account = message?.let { s.account(it.accountId) }?.takeIf { it.capabilities.send }
            ?: s.account(s.filter.accountId)?.takeIf { it.capabilities.send }
            ?: s.sendingAccounts.firstOrNull()
        if (account == null) {
            mutate { it.copy(message = AppMessage.NoSendingAccount) }
            return
        }
        if (message == null || mode == ComposeMode.New) {
            val identity = s.identitiesOf(account).let { all -> all.firstOrNull { it.isDefault } ?: all.first() }
            mutate {
                it.copy(
                    compose = ComposeDraft(
                        accountId = account.id,
                        draftId = Platform.now(),
                        fromEmail = identity.email,
                        initialHtml = signatureHtml(identity),
                    ),
                )
            }
            return
        }
        // A reply goes out from the address the message was sent to, as Gmail does.
        val identities = s.identitiesOf(account)
        val replyFrom = identities.firstOrNull { message.toLine.contains(it.email, ignoreCase = true) }
            ?: identities.firstOrNull { it.isDefault } ?: identities.first()
        val to = when (mode) {
            ComposeMode.Forward -> ""

            ComposeMode.Reply -> message.fromAddress

            else -> {
                val others = message.toLine.split(',').map { it.trim() }.filter { entry ->
                    entry.isNotEmpty() && identities.none { entry.contains(it.email, ignoreCase = true) } &&
                        !entry.contains(message.fromAddress, ignoreCase = true)
                }
                (listOf(message.fromAddress) + others).joinToString(", ")
            }
        }
        val prefix = if (mode == ComposeMode.Forward) "Fwd: " else "Re: "
        val alreadyPrefixed = message.subject.trimStart().startsWith(prefix.trim(), ignoreCase = true)
        val draft = ComposeDraft(
            mode = mode,
            draftId = Platform.now(),
            accountId = account.id,
            to = to,
            subject = if (alreadyPrefixed) message.subject else prefix + message.subject,
            threadId = message.threadId.takeIf { account.kind == ProviderKind.Gmail && it.isNotBlank() },
            fromEmail = replyFrom.email,
            initialHtml = signatureHtml(replyFrom),
        )
        mutate { it.copy(compose = draft) }
        scope.launch {
            val body = _state.value.reader?.takeIf { it.message.id == message.id }?.body
                ?: runCatching { withContext(io) { sync.body(message) } }.getOrNull()
            val (quote, quoteHtml) = quoteFor(mode, message, body?.text.orEmpty().ifBlank { message.snippet })
            val headers = if (mode == ComposeMode.Forward) null else withContext(io) { sync.threadHeaders(message) }
            mutate { st ->
                val current = st.compose?.takeIf { it.draftId == draft.draftId } ?: return@mutate st
                st.copy(
                    compose = current.copy(
                        // Kept out of the editor (shown folded, like Gmail) and added on send.
                        quote = quote,
                        quoteHtml = quoteHtml,
                        inReplyTo = headers?.messageId,
                        references = headers?.let { h -> listOfNotNull(h.references, h.messageId).joinToString(" ").ifBlank { null } },
                    ),
                )
            }
        }
    }

    /** The quoted original, as plain text and as HTML (a blockquote, the way mail clients fold it). */
    private suspend fun quoteFor(mode: ComposeMode, message: MailMessage, text: String): Pair<String, String> {
        val language = _state.value.data.settings.uiLanguage.code
        val date = co.abaye.mailtice.main.formatTime(message.receivedAt, withDate = true)
        val sender = if (message.fromName.isBlank()) message.fromAddress else "${message.fromName} <${message.fromAddress}>"
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun lines(s: String) = s.lines().joinToString("<br>") { esc(it) }
        return if (mode == ComposeMode.Forward) {
            val header = localizedString(language, Res.string.compose_forward_header)
            val meta = listOf(
                localizedString(language, Res.string.compose_forward_from, sender),
                localizedString(language, Res.string.compose_forward_date, date),
                localizedString(language, Res.string.compose_forward_subject, message.subject),
                localizedString(language, Res.string.compose_forward_to, message.toLine),
            )
            val plain = "\n\n$header\n" + meta.joinToString("\n") + "\n\n" + text
            val html = "<br><div dir=\"auto\">$header<br>${meta.joinToString("<br>") { esc(it) }}<br><br>${lines(text)}</div>"
            plain to html
        } else {
            val header = localizedString(language, Res.string.compose_quote_header, date, sender)
            val plain = "\n\n$header\n" + text.lines().joinToString("\n") { "> $it" }
            val html = "<br><div dir=\"auto\">${esc(header)}<blockquote style=\"margin:0 0 0 .8ex;border-inline-start:2px solid #ccc;" +
                "padding-inline-start:1ex\">${lines(text)}</blockquote></div>"
            plain to html
        }
    }

    /** The draft as a message, or null (and the address fields flagged) when an address is wrong. */
    private fun outgoing(draft: ComposeDraft): OutgoingMail? {
        val to = parseAddressList(draft.to)
        val cc = parseAddressList(draft.cc)
        val bcc = parseAddressList(draft.bcc)
        if (to.isNullOrEmpty() || cc == null || bcc == null) {
            mutate { it.copy(compose = draft.copy(invalidAddresses = true)) }
            return null
        }
        val html = draft.html?.takeIf { draft.body.isNotBlank() || draft.quoteHtml != null }
        val identity = _state.value.identityOf(draft)
        val account = _state.value.account(draft.accountId)
        // The account's own address with no name of its own is left to the provider, as before.
        val from = identity?.takeIf { it.name.isNotBlank() || !it.email.equals(account?.email, ignoreCase = true) }?.formatted
        val replyTo = identity?.replyTo?.takeIf { it.isNotBlank() }
        return OutgoingMail(
            to = to, cc = cc, bcc = bcc,
            subject = draft.subject.trim(),
            text = draft.body + draft.quote.orEmpty(),
            html = html?.let { "<div dir=\"auto\">$it</div>" + draft.quoteHtml.orEmpty() },
            attachments = draft.attachments.map { OutgoingAttachment(it.name, it.mimeType, it.bytes) },
            inReplyTo = draft.inReplyTo,
            references = draft.references,
            threadId = draft.threadId,
            from = from,
            replyTo = replyTo,
        )
    }

    /**
     * Switches the address the open draft sends from. Its signature comes along: while the body is
     * still just the old signature (nothing typed), the editor is reloaded with the new one.
     */
    private fun setComposeFrom(accountId: String, email: String) {
        mutate { s ->
            val draft = s.compose ?: return@mutate s
            val old = s.identityOf(draft)
            val next = draft.copy(accountId = accountId, fromEmail = email)
            val new = s.identityOf(next)
            val untouched = draft.body.isBlank() || draft.body.trim() == HtmlText.toText(old?.signature.orEmpty()).trim()
            if (untouched && old?.signature != new?.signature) {
                s.copy(
                    compose = next.copy(initialHtml = signatureHtml(new), html = null, body = "", editorVersion = draft.editorVersion + 1),
                )
            } else {
                s.copy(compose = next)
            }
        }
    }

    /** The editor's opening content for [identity]: two empty lines, then its signature, if it has one. */
    private fun signatureHtml(identity: SenderIdentity?): String =
        identity?.signature?.takeIf { it.isNotBlank() }?.let { "<p></p><p></p><div>$it</div>" } ?: ""

    private fun sendCompose() {
        val draft = _state.value.compose ?: return
        if (draft.sending) return
        val account = _state.value.account(draft.accountId) ?: return
        val mail = outgoing(draft) ?: return
        mutate { it.copy(compose = draft.copy(sending = true)) }
        scope.launch {
            try {
                withContext(io) { sync.send(account, mail) }
                draft.scheduledId?.let { id -> withContext(io) { repo.unschedule(id) } }
                dropServerDraft(latestDraftFor(draft))
                mutate { it.copy(compose = null, message = AppMessage.Sent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException.SendNotAllowed) {
                mutate { it.copy(compose = it.compose?.copy(sending = false), message = AppMessage.SendNeedsReauth) }
            } catch (e: Exception) {
                if (isNetworkError(e)) {
                    // No connection: the message waits in the queue and goes out as soon as it is back.
                    val item =
                        ScheduledMail(id = draft.scheduledId ?: newId(), accountId = draft.accountId, sendAt = Platform.now(), mail = mail)
                    withContext(io) { repo.schedule(item) }
                    dropServerDraft(latestDraftFor(draft))
                    mutate { it.copy(compose = null, message = AppMessage.SendQueued) }
                } else {
                    println("Send failed: ${e::class.simpleName}")
                    mutate { it.copy(compose = it.compose?.copy(sending = false), message = AppMessage.SendFailed) }
                }
            }
        }
    }

    // ---- compose extras: files, suggestions, scheduling ---------------------------------------

    private fun attachFiles() {
        scope.launch {
            val title = localizedString(_state.value.data.settings.uiLanguage.code, Res.string.compose_attach)
            addFiles(withContext(io) { Platform.pickFiles(title) })
        }
    }

    /** Adds files to the open draft, within Gmail's 25 MB for a message. */
    private fun addFiles(picked: List<co.abaye.mailtice.platform.PickedFile>) {
        if (picked.isEmpty()) return
        mutate { s ->
            val draft = s.compose ?: return@mutate s
            val added = picked.map { DraftAttachment(newId(), it.name, it.mimeType, it.bytes) }
            val total = (draft.attachments + added).sumOf { it.size }
            if (total > MAX_ATTACHMENTS_BYTES) {
                s.copy(message = AppMessage.AttachmentsTooLarge)
            } else {
                s.copy(compose = draft.copy(attachments = draft.attachments + added))
            }
        }
    }

    private var suggestJob: Job? = null

    private fun suggestContacts(query: String) {
        suggestJob?.cancel()
        if (query.isBlank()) {
            mutate { it.copy(contactSuggestions = emptyList()) }
            return
        }
        suggestJob = scope.launch {
            delay(120)
            val found = withContext(io) { repo.contacts(query) }
            mutate { it.copy(contactSuggestions = found) }
        }
    }

    private fun scheduleCompose(sendAt: Long) {
        val draft = _state.value.compose ?: return
        val mail = outgoing(draft) ?: return
        val item = ScheduledMail(id = draft.scheduledId ?: newId(), accountId = draft.accountId, sendAt = sendAt, mail = mail)
        background {
            repo.schedule(item)
            dropServerDraft(latestDraftFor(draft))
            mutate { it.copy(compose = null, message = AppMessage.Scheduled) }
        }
    }

    /** Opens a queued message for editing; it stays queued until sent or rescheduled from the editor. */
    private fun editScheduled(id: String) {
        val item = _state.value.scheduled.firstOrNull { it.id == id } ?: return
        val m = item.mail
        mutate {
            it.copy(
                compose = ComposeDraft(
                    draftId = Platform.now(),
                    accountId = item.accountId,
                    to = m.to.joinToString(", "),
                    cc = m.cc.joinToString(", "),
                    bcc = m.bcc.joinToString(", "),
                    showCcBcc = m.cc.isNotEmpty() || m.bcc.isNotEmpty(),
                    subject = m.subject,
                    body = m.text,
                    html = m.html,
                    initialHtml = m.html?.removePrefix("<div dir=\"auto\">")?.substringBeforeLast("</div>") ?: "",
                    attachments = m.attachments.map { a -> DraftAttachment(newId(), a.name, a.mimeType, a.bytes) },
                    scheduledId = item.id,
                    inReplyTo = m.inReplyTo,
                    references = m.references,
                    threadId = m.threadId,
                ),
            )
        }
    }

    /** Sends what is due; a failure waits longer each time (2, 4, 8... up to 60 minutes) and says why. */
    private suspend fun sendDueScheduled() {
        val due = withContext(io) { runCatching { repo.dueScheduled(Platform.now()) }.getOrDefault(emptyList()) }
        due.forEach { item ->
            val account = withContext(io) { repo.account(item.accountId) }
            if (account == null) {
                withContext(io) { repo.unschedule(item.id) }
                return@forEach
            }
            try {
                withContext(io) { sync.send(account, item.mail) }
                withContext(io) { repo.unschedule(item.id) }
                mutate { it.copy(message = AppMessage.ScheduledSent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No connection is not the message's fault: try again in a minute, not in growing waits.
                val wait = if (isNetworkError(e)) 60_000L else (2L shl item.attempts.coerceAtMost(5)).coerceAtMost(60) * 60_000L
                val why = listOfNotNull(e::class.simpleName, e.message?.lineSequence()?.firstOrNull()).joinToString(": ")
                withContext(io) { repo.scheduledFailed(item.id, why, Platform.now() + wait) }
            }
        }
    }

    // ---- drafts on the server ---------------------------------------------------------------

    private val draftMutex = Mutex()

    /** The newest state of [draft] (a save may have finished since it was read). */
    private fun latestDraftFor(draft: ComposeDraft): ComposeDraft = _state.value.compose?.takeIf { it.draftId == draft.draftId } ?: draft

    /**
     * Saves the open draft when it changed since the last save. Addresses are taken as typed (a draft
     * may hold a half-written one). Switching the "from" account moves the draft to that account.
     */
    private suspend fun saveDraft(snapshot: ComposeDraft? = _state.value.compose): ComposeDraft? = draftMutex.withLock {
        val draft = snapshot?.let(::latestDraftFor) ?: return@withLock null
        if (draft.sending || draft.isBlank || draft.draftSignature() == draft.savedSignature) return@withLock draft
        val account = _state.value.account(draft.accountId)?.takeIf { it.capabilities.drafts } ?: return@withLock draft
        val signature = draft.draftSignature()
        fun tokens(s: String) = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val mail = OutgoingMail(
            to = tokens(draft.to), cc = tokens(draft.cc), bcc = tokens(draft.bcc),
            subject = draft.subject,
            text = draft.body + draft.quote.orEmpty(),
            html = draft.html?.let { "<div dir=\"auto\">$it</div>" + draft.quoteHtml.orEmpty() },
            attachments = draft.attachments.map { OutgoingAttachment(it.name, it.mimeType, it.bytes) },
            inReplyTo = draft.inReplyTo, references = draft.references, threadId = draft.threadId,
        )
        markDraft(draft.draftId) { it.copy(draftSave = DraftSave.Saving) }
        try {
            val sameAccount = draft.draftAccountId == account.id
            val handle = withContext(io) { sync.saveDraft(account, mail, draft.draftHandle.takeIf { sameAccount }) }
            if (!sameAccount) {
                val old = draft.draftAccountId?.let { _state.value.account(it) }
                if (old != null && draft.draftHandle != null) runCatching { withContext(io) { sync.deleteDraft(old, draft.draftHandle) } }
            }
            markDraft(draft.draftId) {
                it.copy(draftHandle = handle, draftAccountId = account.id, draftSave = DraftSave.Saved, savedSignature = signature)
            }
            draft.copy(draftHandle = handle, draftAccountId = account.id, savedSignature = signature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Draft save failed: ${e::class.simpleName}")
            markDraft(draft.draftId) { it.copy(draftSave = DraftSave.Failed) }
            draft
        }
    }

    private fun markDraft(draftId: Long, change: (ComposeDraft) -> ComposeDraft) = mutate { s ->
        val d = s.compose?.takeIf { it.draftId == draftId } ?: return@mutate s
        s.copy(compose = change(d))
    }

    /** Deletes the server copy of [draft], if it has one (sent, scheduled or discarded). */
    private suspend fun dropServerDraft(draft: ComposeDraft) {
        val handle = draft.draftHandle ?: return
        val account = draft.draftAccountId?.let { _state.value.account(it) } ?: return
        runCatching { withContext(io) { sync.deleteDraft(account, handle) } }
    }

    /**
     * Closing keeps the draft: whatever changed since the last save is saved now. The trash icon
     * ([discard]) deletes it from the server instead.
     */
    private fun closeCompose(discard: Boolean) {
        val draft = _state.value.compose ?: return
        if (draft.sending) return
        mutate { it.copy(compose = null, contactSuggestions = emptyList()) }
        scope.launch {
            if (discard) {
                draftMutex.withLock { dropServerDraft(draft) }
                if (draft.draftHandle != null) mutate { it.copy(message = AppMessage.DraftDiscarded) }
                return@launch
            }
            val canSave = _state.value.account(draft.accountId)?.capabilities?.drafts == true
            if (draft.isBlank || !canSave) return@launch
            val saved = saveDraft(draft)
            if (saved?.draftHandle != null) mutate { it.copy(message = AppMessage.DraftSaved) }
        }
    }

    /** A message in Drafts opens in the compose window, carrying on the same server draft. */
    private fun openDraft(message: MailMessage) {
        val account = _state.value.account(message.accountId)?.takeIf { it.capabilities.send } ?: return
        scope.launch {
            val body = runCatching { withContext(io) { sync.body(message) } }.getOrNull()
            val handle = withContext(io) { sync.draftHandle(message) }
            val html = body?.html?.takeIf { it.isNotBlank() }
            val draft = ComposeDraft(
                draftId = Platform.now(),
                accountId = account.id,
                to = message.toLine.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") { "$it, " },
                subject = message.subject,
                body = body?.text.orEmpty(),
                html = html,
                initialHtml = html ?: body?.text.orEmpty().lines().joinToString("<br>") {
                    it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                },
                draftHandle = handle,
                draftAccountId = account.id,
                draftSave = DraftSave.Saved,
            )
            mutate { it.copy(compose = draft.copy(savedSignature = draft.draftSignature())) }
        }
    }

    private fun isDraft(message: MailMessage): Boolean {
        val s = _state.value
        val draftFolders = s.foldersOf(message.accountId).filter { it.role == FolderRole.Drafts }.map { it.id }.toSet()
        return message.folderIds.any { it in draftFolders } ||
            (s.filter.folderId.isEmpty() && s.filter.view == MailView.Drafts)
    }

    private fun openInWeb(message: MailMessage) {
        val account = _state.value.account(message.accountId) ?: return
        if (!account.capabilities.openInWeb) return
        Platform.openUrl("https://mail.google.com/mail/u/${account.email}/#all/${message.id}")
    }

    private fun onNotificationAction(action: NotificationAction) {
        when (action) {
            is NotificationAction.Open -> scope.launch {
                withContext(io) { repo.message(action.accountId, action.messageId) }?.let { message ->
                    navigate(AppKey.Inbox)
                    openMail(message)
                }
                _raiseWindow.tryEmit(Unit)
            }

            is NotificationAction.MarkRead -> scope.launch {
                withContext(io) { repo.message(action.accountId, action.messageId) }?.let { serverAction { sync.setRead(it, read = true) } }
            }

            is NotificationAction.OpenAccount -> {
                navigate(AppKey.Inbox)
                onIntent(AppIntent.SetFilterAccount(action.accountId))
                _raiseWindow.tryEmit(Unit)
            }
        }
    }

    /** Server actions: the database already changed optimistically; a refusal is only reported. */
    private fun serverAction(call: suspend () -> Unit) {
        scope.launch {
            try {
                withContext(io) { call() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReauthRequiredException) {
                mutate { it.copy(message = AppMessage.ActionFailed) }
            } catch (e: ActionQueuedException) {
                mutate { it.copy(message = AppMessage.ActionQueued) }
            } catch (e: Exception) {
                println("Mail action failed: ${e::class.simpleName}")
                mutate { it.copy(message = AppMessage.ActionFailed) }
            }
        }
    }

    // ---- settings & storage -----------------------------------------------------------------

    private fun refreshStorage() {
        scope.launch {
            val usage = withContext(io) { runCatching { repo.storage() }.getOrNull() } ?: return@launch
            mutate { it.copy(storage = usage) }
        }
    }

    private fun setLaunchAtLogin(on: Boolean) {
        scope.launch {
            val ok = Platform.setLaunchAtLogin(on)
            if (ok) settings { it.copy(launchAtLogin = on) } else mutate { it.copy(message = AppMessage.LaunchAtLoginFailed) }
        }
    }

    private fun confirmDialog() {
        val dialog = _state.value.dialog
        mutate { it.copy(dialog = AppDialog.Hidden) }
        when (dialog) {
            is AppDialog.ConfirmRemove -> removeAccount(dialog.accountId)
            is AppDialog.ConfirmClearCache -> clearCache(dialog.accountId)
            AppDialog.ConfirmReset -> resetApp()
            AppDialog.Hidden -> Unit
        }
    }

    private fun resetApp() {
        val accounts = _state.value.accounts
        sync.stopAll()
        scope.launch {
            accounts.forEach { account ->
                sync.forget(account)
                runCatching { auth.revoke(account) }
                withContext(io) { repo.deleteAccount(account.id) }
            }
            secrets.clear()
            store.clear()
            withContext(io) { repo.vacuum() }
            mutate { AppState(data = seedData(), availableProviders = it.availableProviders, message = AppMessage.ResetDone) }
            navigate(AppKey.Inbox)
            refreshStorage()
        }
    }

    private var digestJob: Job? = null

    /** Rebuilds the dashboard's view of every account (at most one rebuild running at a time). */
    private fun refreshDigests() {
        digestJob?.cancel()
        digestJob = scope.launch {
            val accounts = _state.value.accounts
            val now = Platform.now()
            val zone = TimeZone.currentSystemDefault()
            val digests = withContext(io) {
                accounts.mapNotNull { a -> runCatching { a.id to repo.digest(a.id, now, zone) }.getOrNull() }.toMap()
            }
            mutate { it.copy(digests = digests) }
        }
    }

    private fun reconcileSync(accounts: List<Account> = _state.value.accounts) {
        val settings = _state.value.data.settings
        val attachments = when (settings.offlineAttachmentsMb) {
            -1 -> Long.MAX_VALUE
            else -> settings.offlineAttachmentsMb * 1024L * 1024L
        }
        sync.reconcile(scope, accounts, settings.pollSeconds, settings.smartPolling, OfflinePrefs(settings.offlineMode, attachments))
    }

    // ---- attachment viewer ------------------------------------------------------------------

    private fun downloadRoot(): String = _state.value.data.settings.downloadFolder

    /** Set once the file went to another app, which may still be reading it when the viewer closes. */
    private var previewOpenedOutside = false

    private fun previewAttachment(message: MailMessage, index: Int) {
        val attachment = _state.value.reader?.body?.attachments?.getOrNull(index) ?: return
        previewOpenedOutside = false
        mutate { it.copy(preview = AttachmentPreview(message, index, attachment)) }
        scope.launch {
            try {
                val file = withContext(io) { sync.attachments(message, setOf(index)) }.firstOrNull() ?: error("Attachment not found")
                var path = ""
                var page = ""
                if (Platform.isDesktop) {
                    withContext(io) {
                        val dir = joinPath(Platform.appDir(), "preview")
                        val name = safeFileName(file.name, "attachment")
                        path = joinPath(dir, name)
                        Platform.writeBytes(path, file.bytes)
                        val kind = previewKindOf(attachment.name, attachment.mimeType)
                        page = when (kind) {
                            PreviewKind.Pdf -> fileUrl(path)

                            PreviewKind.Audio, PreviewKind.Video -> {
                                val player = joinPath(dir, "player.html")
                                Platform.writeText(player, previewPage(kind, name).orEmpty())
                                fileUrl(player)
                            }

                            else -> ""
                        }
                    }
                }
                updatePreview(message, index) { it.copy(bytes = file.bytes, tempPath = path, pageUrl = page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Preview failed: ${e::class.simpleName}")
                updatePreview(message, index) { it.copy(failed = true) }
            }
        }
    }

    private fun updatePreview(message: MailMessage, index: Int, block: (AttachmentPreview) -> AttachmentPreview) {
        mutate { s ->
            val p = s.preview
            if (p == null || p.message.key != message.key || p.index != index) s else s.copy(preview = block(p))
        }
    }

    private fun closePreview() {
        val p = _state.value.preview ?: return
        mutate { it.copy(preview = null) }
        // The copy is only for viewing; one handed to another app stays for it.
        if (p.tempPath.isNotEmpty() && !previewOpenedOutside) background { Platform.delete(p.tempPath) }
    }

    private fun savePreview() {
        val p = _state.value.preview ?: return
        val bytes = p.bytes ?: return
        scope.launch {
            val location = withContext(io) { Platform.saveDownload("", p.attachment.name, bytes, downloadRoot()) }
            if (location == null) {
                mutate { it.copy(message = AppMessage.DownloadFailed) }
            } else {
                Platform.revealDownload(location)
                mutate { it.copy(message = AppMessage.FilesSaved) }
            }
        }
    }

    // ---- translation ------------------------------------------------------------------------

    /** Finished translations this session, so reopening a message costs no second request. */
    private val translations = mutableMapOf<String, ReaderTranslation>()

    private fun translationKey(message: MailMessage, target: String) = "${message.key}|$target"

    private fun translateOpenMessage() {
        val reader = _state.value.reader ?: return
        val body = reader.body ?: return
        val message = reader.message
        val target = _state.value.data.settings.uiLanguage.code
        val key = translationKey(message, target)
        translations[key]?.let {
            setTranslation(message, it.copy(showOriginal = false))
            return
        }
        setTranslation(message, ReaderTranslation(target, TranslationState.Loading))
        scope.launch {
            try {
                val done = withContext(io) { translate(message, body, target) }
                translations[key] = done
                setTranslation(message, done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Translation failed: ${e::class.simpleName}: ${e.message?.take(200)}")
                setTranslation(message, ReaderTranslation(target, TranslationState.Failed))
            }
        }
    }

    /**
     * HTML mail is translated in place: its text nodes (before the quoted part) go out one per
     * line and come back into the same places, so the layout, images and links stay. Should the
     * lines not come back one for one, the plain text is translated instead.
     */
    private suspend fun translate(message: MailMessage, body: MailBody, target: String): ReaderTranslation {
        val segments = if (body.html.isNotBlank()) htmlTextSegments(body.html) else emptyList()
        val plain = splitQuote(body.text.ifBlank { message.snippet }).main
        val first = translator.translate(listOf(message.subject, segments.joinToString("\n") { it.text }.ifEmpty { plain }), target)
        val lines = first.texts[1].split('\n')
        if (segments.isNotEmpty() && lines.size == segments.size) {
            return ReaderTranslation(
                target = target,
                state = TranslationState.Done,
                sourceLanguage = first.sourceLanguage,
                subject = first.texts[0],
                html = replaceSegments(body.html, segments, lines),
            )
        }
        val text = if (segments.isEmpty()) first.texts[1] else translator.translate(listOf(plain), target).texts[0]
        return ReaderTranslation(target, TranslationState.Done, first.sourceLanguage, first.texts[0], text)
    }

    /**
     * Images the HTML shows by Content-ID are parts of the message: download them and hand them to
     * the page as data: URIs. A body stored before Content-IDs were kept is read again first.
     */
    private suspend fun loadInlineImages(message: MailMessage, stored: MailBody) {
        val refs = cidRefs(stored.html)
        if (refs.isEmpty()) return
        try {
            val body = if (stored.attachments.none {
                    it.contentId.isNotEmpty()
                }
            ) {
                withContext(io) { sync.body(message, refresh = true) }
            } else {
                stored
            }
            val wanted = body.attachments.withIndex().filter { it.value.contentId in refs }
            if (wanted.isEmpty()) return
            val files = withContext(io) { sync.attachments(message, wanted.map { it.index }.toSet()) }
            val images = files.associate { file ->
                val part = body.attachments[file.index]
                part.contentId to "data:${part.mimeType.ifBlank { "image/png" }};base64,${Base64.encode(file.bytes)}"
            }
            mutate { s ->
                val reader = s.reader
                if (reader?.message?.key != message.key) s else s.copy(reader = reader.copy(body = body, inlineImages = images))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Inline images failed: ${e::class.simpleName}")
        }
    }

    /** Only while [message] is still the one open; a translation finishing after the reader moved on is dropped. */
    private fun setTranslation(message: MailMessage, translation: ReaderTranslation) {
        mutate { s ->
            val reader = s.reader
            if (reader?.message?.key != message.key) s else s.copy(reader = reader.copy(translation = translation))
        }
    }

    // ---- labels -----------------------------------------------------------------------------

    private fun labelAction(accountId: String, block: suspend (Account) -> Unit) {
        val account = _state.value.account(accountId) ?: return
        if (!account.capabilities.manageLabels) return
        background { block(account) }
    }

    /**
     * A label's id changed (an IMAP rename) or it is gone ([newId] null): its pin and hidden marks
     * follow it, and a list showing it moves along or falls back to the inbox.
     */
    private fun moveLabelKeys(accountId: String, oldId: String, newId: String?) {
        val old = "$accountId/$oldId"
        val new = newId?.let { "$accountId/$it" }
        fun Set<String>.moved() = if (old in this) (this - old) + listOfNotNull(new) else this
        settings { it.copy(hiddenFolders = it.hiddenFolders.moved(), pinnedLabels = it.pinnedLabels.moved()) }
        mutate {
            if (it.filter.accountId != accountId || it.filter.folderId != oldId) {
                it
            } else {
                it.copy(filter = it.filter.copy(folderId = newId.orEmpty(), view = if (newId == null) MailView.Inbox else it.filter.view))
            }
        }
    }

    /** Folders open over the mail list; picking one from a settings page goes back to the mail. */
    private fun navigateToMail() {
        val top = backStack.lastOrNull()
        if (top != AppKey.Inbox && top != AppKey.Reader) onIntent(AppIntent.Navigate(AppKey.Inbox))
    }

    // ---- plumbing ---------------------------------------------------------------------------

    private fun background(block: suspend () -> Unit) {
        scope.launch {
            try {
                withContext(io) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Background task failed: ${e::class.simpleName}")
                mutate { it.copy(message = AppMessage.ActionFailed) }
            }
        }
    }

    private fun settings(block: (UserSettings) -> UserSettings) {
        mutate { it.copy(data = it.data.copy(settings = block(it.data.settings))) }
        persist()
    }

    private fun persist() {
        saveJob?.cancel()
        val snapshot = _state.value.data
        saveJob = scope.launch {
            delay(300)
            withContext(io) { store.save(snapshot) }
        }
    }

    private fun mutate(block: (AppState) -> AppState) = _state.update { old ->
        val new = block(old)
        if (new.filter != old.filter) new.copy(older = OlderMail(), localLimit = LOCAL_PAGE) else new
    }
}
