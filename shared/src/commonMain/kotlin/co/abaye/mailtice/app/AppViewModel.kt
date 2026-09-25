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
import co.abaye.mailtice.data.SecretStore
import co.abaye.mailtice.data.SettingsStore
import co.abaye.mailtice.data.seedData
import co.abaye.mailtice.dev.DemoAccounts
import co.abaye.mailtice.dev.DemoMode
import co.abaye.mailtice.di.Io
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.ListFractionRange
import co.abaye.mailtice.domain.PollIntervals
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.RetentionOptions
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.notify.MailNotifications
import co.abaye.mailtice.notify.NotificationAction
import co.abaye.mailtice.notify.NotificationActions
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.localizedString
import co.abaye.mailtice.platform.safeFileName
import co.abaye.mailtice.export.ExportLabels
import co.abaye.mailtice.export.ThreadExport
import co.abaye.mailtice.platform.systemUiLanguage
import co.abaye.mailtice.provider.ImapAutoConfig
import co.abaye.mailtice.provider.ImapBackend
import co.abaye.mailtice.provider.ImapLoginException
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.parseAddressList
import co.abaye.mailtice.provider.gmail.GmailApi
import co.abaye.mailtice.sync.SyncEngine
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mailtice.shared.generated.resources.Res
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val SEARCH_DEBOUNCE_MS = 200L

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

    val backStack: NavBackStack<AppKey> = NavBackStack(AppKey.Inbox)

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
        refreshStorage()
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
                sync.reconcile(scope, accounts, _state.value.data.settings.pollSeconds)
            }
        }
        scope.launch {
            repo.allFolders.collect { folders -> mutate { it.copy(folders = folders.groupBy { f -> f.accountId }) } }
        }
        scope.launch { repo.unreadCounts.collect { counts -> mutate { it.copy(unread = counts) } } }
        scope.launch { repo.unreadByFolder.collect { counts -> mutate { it.copy(unreadByFolder = counts) } } }
        scope.launch {
            _state.map { it.filter }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { f ->
                    val custom = f.folderId.isNotEmpty()
                    repo.inbox(
                        InboxQuery(
                            accountId = f.accountId,
                            folderId = f.folderId,
                            unreadOnly = f.unreadOnly,
                            attachmentsOnly = f.attachmentsOnly,
                            flaggedOnly = !custom && f.view == MailView.Starred,
                            role = if (custom) "" else f.view.role?.name.orEmpty(),
                            text = f.query,
                        ),
                    )
                }
                .collect { list ->
                    mutate { s ->
                        val keys = list.map { it.key }.toSet()
                        s.copy(inbox = list, selection = s.selection.filterTo(mutableSetOf()) { it in keys })
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
                mutate { it.copy(addAccount = null, signIn = SignInState.Idle) }
            }
            is AppIntent.Reconnect -> reconnect(intent.accountId)
            AppIntent.CancelSignIn -> signInJob?.cancel()

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
            AppIntent.SelectAll -> mutate { s -> s.copy(selection = s.inbox.map { it.key }.toSet()) }
            AppIntent.ClearSelection -> mutate { it.copy(selection = emptySet()) }
            is AppIntent.BulkSetRead -> bulk { m, caps -> if (caps.markRead && m.unread == intent.read) sync.setRead(m, intent.read) }
            AppIntent.BulkArchive -> bulk(closeReader = true) { m, caps -> if (caps.archive) sync.archive(m) }
            AppIntent.BulkTrash -> bulk(closeReader = true) { m, caps -> if (caps.trash) sync.trash(m) }
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
                // Sending and threading state is owned here; the UI only edits the text fields.
                s.copy(
                    compose = intent.draft.copy(
                        sending = current.sending,
                        inReplyTo = current.inReplyTo,
                        references = current.references,
                        threadId = current.threadId,
                        invalidAddresses = false,
                    ),
                )
            }
            AppIntent.SendCompose -> sendCompose()
            AppIntent.CloseCompose -> mutate { if (it.compose?.sending == true) it else it.copy(compose = null) }
            is AppIntent.SetSearchQuery -> mutate { it.copy(filter = it.filter.copy(query = intent.query)) }
            is AppIntent.OpenMail -> openMail(intent.message)
            AppIntent.CloseReader -> {
                mutate { it.copy(reader = null) }
                if (backStack.lastOrNull() == AppKey.Reader) back()
            }
            is AppIntent.SetRead -> serverAction { sync.setRead(intent.message, intent.read) }
            is AppIntent.Archive -> archive(intent.message)
            is AppIntent.OpenInWeb -> openInWeb(intent.message)
            is AppIntent.OpenHtml -> background {
                sync.body(intent.message).html.takeIf { it.isNotBlank() }?.let(Platform::openHtml)
            }
            AppIntent.RefreshNow -> sync.refreshNow()

            is AppIntent.SetTheme -> settings { it.copy(theme = intent.mode) }
            is AppIntent.SetAccent -> settings { it.copy(accent = intent.accent) }
            is AppIntent.SetDensity -> settings { it.copy(density = intent.density) }
            is AppIntent.SetPaneStyle -> settings { it.copy(paneStyle = intent.style) }
            is AppIntent.SetUiLanguage -> settings {
                it.copy(uiLanguage = intent.language ?: systemUiLanguage(), uiLanguageAuto = intent.language == null)
            }
            is AppIntent.SetPollInterval -> {
                settings { it.copy(pollSeconds = intent.seconds.takeIf { s -> s in PollIntervals } ?: it.pollSeconds) }
                sync.reconcile(scope, _state.value.accounts, _state.value.data.settings.pollSeconds)
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
        if (DemoMode.enabled) {
            addDemoAccount(kind)
            return
        }
        signInOAuth(kind, existing = null)
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
        background {
            repo.addAccount(account)
            mutate { it.copy(addAccount = null, message = AppMessage.AccountAdded) }
        }
    }

    private fun signInOAuth(kind: ProviderKind, existing: Account?) {
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
        signInJob = scope.launch {
            mutate { it.copy(signIn = SignInState.Waiting) }
            try {
                val code = authorizer.authorize(provider, loginHint = existing?.email)
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
                mutate {
                    it.copy(
                        signIn = SignInState.Idle,
                        addAccount = null,
                        message = if (known == null) AppMessage.AccountAdded else AppMessage.AccountReconnected,
                    )
                }
            } catch (e: CancellationException) {
                mutate { it.copy(signIn = SignInState.Idle) }
                throw e
            } catch (e: AuthCancelledException) {
                mutate { it.copy(signIn = SignInState.Idle) }
            } catch (e: Exception) {
                println("Sign-in failed: ${e::class.simpleName}")
                mutate { it.copy(signIn = SignInState.Idle, message = AppMessage.SignInFailed) }
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
        } else if (account.kind.oauth) {
            signInOAuth(account.kind, existing = account)
        } else {
            val server = account.imap ?: return
            mutate {
                it.copy(
                    addAccount = AddAccountStep.Imap(
                        ImapForm(
                            email = account.email, host = server.host, port = server.port.toString(),
                            security = server.security, username = account.username, reconnectId = account.id,
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
                    busy = false, host = server.host, port = server.port.toString(), security = server.security,
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
                                id = id, kind = ProviderKind.Imap, email = form.email.trim(), color = nextColor(),
                                capabilities = capabilities, imap = server, username = username,
                            ),
                        )
                    } else {
                        repo.updateServer(id, server, username)
                        repo.updateCapabilities(id, capabilities)
                    }
                }
                if (form.reconnectId != null) sync.restart(scope, id)
                mutate {
                    it.copy(addAccount = null, message = if (form.reconnectId == null) AppMessage.AccountAdded else AppMessage.AccountReconnected)
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
        return AccountColor.entries.firstOrNull { it !in used } ?: AccountColor.entries[_state.value.accounts.size % AccountColor.entries.size]
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

    private fun openMail(message: MailMessage) {
        mutate { it.copy(reader = Reader(message)) }
        if (!Platform.isDesktop) navigate(AppKey.Reader)
        scope.launch {
            val body = runCatching { withContext(io) { sync.body(message) } }.getOrNull()
            mutate { s ->
                if (s.reader?.message?.id != message.id) s else s.copy(reader = Reader(message, body, failed = body == null))
            }
            // Opening means reading, where the account can say so.
            val account = _state.value.account(message.accountId)
            if (message.unread && account?.capabilities?.markRead == true) {
                runCatching { sync.setRead(message, read = true) }
            }
        }
    }

    private fun archive(message: MailMessage) {
        val account = _state.value.account(message.accountId) ?: return
        if (!account.capabilities.archive) return
        if (_state.value.reader?.message?.id == message.id) onIntent(AppIntent.CloseReader)
        serverAction { sync.archive(message) }
    }

    private fun trash(message: MailMessage) {
        val account = _state.value.account(message.accountId) ?: return
        if (!account.capabilities.trash) return
        if (_state.value.reader?.message?.id == message.id) onIntent(AppIntent.CloseReader)
        scope.launch {
            try {
                withContext(io) { sync.trash(message) }
                mutate { it.copy(message = AppMessage.MovedToTrash) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Trash failed: ${e::class.simpleName}")
                mutate { it.copy(message = AppMessage.ActionFailed) }
            }
        }
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
    private fun bulk(closeReader: Boolean = false, action: suspend (MailMessage, Capabilities) -> Unit) {
        val s = _state.value
        val targets = s.selectedMessages.mapNotNull { m -> s.account(m.accountId)?.let { m to it.capabilities } }
        if (targets.isEmpty()) return
        if (closeReader && targets.any { it.first.id == s.reader?.message?.id }) onIntent(AppIntent.CloseReader)
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
            files.forEach { f -> withContext(io) { Platform.saveDownload(folder, f.name, f.bytes) }?.let(saved::add) }
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
                withContext(io) { Platform.saveDownload("", "$base.html", html.encodeToByteArray()) }
            }
            ExportFormat.Mail -> {
                val raws = thread.map { m -> m to withContext(io) { sync.rawMessage(m) } }
                if (raws.size == 1) {
                    withContext(io) { Platform.saveDownload("", "$base.eml", raws.first().second) }
                } else {
                    withContext(io) { Platform.saveDownload("", "$base.mbox", ThreadExport.mbox(raws)) }
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
            mutate { it.copy(compose = ComposeDraft(accountId = account.id)) }
            return
        }
        val to = when (mode) {
            ComposeMode.Forward -> ""
            ComposeMode.Reply -> message.fromAddress
            else -> {
                val others = message.toLine.split(',').map { it.trim() }.filter { entry ->
                    entry.isNotEmpty() && !entry.contains(account.email, ignoreCase = true) &&
                        !entry.contains(message.fromAddress, ignoreCase = true)
                }
                (listOf(message.fromAddress) + others).joinToString(", ")
            }
        }
        val prefix = if (mode == ComposeMode.Forward) "Fwd: " else "Re: "
        val alreadyPrefixed = message.subject.trimStart().startsWith(prefix.trim(), ignoreCase = true)
        val draft = ComposeDraft(
            mode = mode,
            accountId = account.id,
            to = to,
            subject = if (alreadyPrefixed) message.subject else prefix + message.subject,
            threadId = message.threadId.takeIf { account.kind == ProviderKind.Gmail && it.isNotBlank() },
        )
        mutate { it.copy(compose = draft) }
        scope.launch {
            val body = _state.value.reader?.takeIf { it.message.id == message.id }?.body
                ?: runCatching { withContext(io) { sync.body(message) } }.getOrNull()
            val quote = quoteFor(mode, message, body?.text.orEmpty().ifBlank { message.snippet })
            val headers = if (mode == ComposeMode.Forward) null else withContext(io) { sync.threadHeaders(message) }
            mutate { st ->
                val current = st.compose?.takeIf { it.accountId == draft.accountId && it.mode == mode } ?: return@mutate st
                st.copy(
                    compose = current.copy(
                        // The user may have started typing; the quote goes under whatever is there.
                        body = current.body + quote,
                        inReplyTo = headers?.messageId,
                        references = headers?.let { h -> listOfNotNull(h.references, h.messageId).joinToString(" ").ifBlank { null } },
                    ),
                )
            }
        }
    }

    private suspend fun quoteFor(mode: ComposeMode, message: MailMessage, text: String): String {
        val language = _state.value.data.settings.uiLanguage.code
        val date = co.abaye.mailtice.main.formatTime(message.receivedAt, withDate = true)
        val sender = if (message.fromName.isBlank()) message.fromAddress else "${message.fromName} <${message.fromAddress}>"
        return if (mode == ComposeMode.Forward) {
            val header = localizedString(language, Res.string.compose_forward_header)
            "\n\n$header\n" +
                localizedString(language, Res.string.compose_forward_from, sender) + "\n" +
                localizedString(language, Res.string.compose_forward_date, date) + "\n" +
                localizedString(language, Res.string.compose_forward_subject, message.subject) + "\n" +
                localizedString(language, Res.string.compose_forward_to, message.toLine) + "\n\n" + text
        } else {
            val header = localizedString(language, Res.string.compose_quote_header, date, sender)
            "\n\n$header\n" + text.lines().joinToString("\n") { "> $it" }
        }
    }

    private fun sendCompose() {
        val draft = _state.value.compose ?: return
        if (draft.sending) return
        val account = _state.value.account(draft.accountId) ?: return
        val to = parseAddressList(draft.to)
        val cc = parseAddressList(draft.cc)
        val bcc = parseAddressList(draft.bcc)
        if (to.isNullOrEmpty() || cc == null || bcc == null) {
            mutate { it.copy(compose = draft.copy(invalidAddresses = true)) }
            return
        }
        val mail = OutgoingMail(
            to = to, cc = cc, bcc = bcc,
            subject = draft.subject.trim(),
            text = draft.body,
            inReplyTo = draft.inReplyTo,
            references = draft.references,
            threadId = draft.threadId,
        )
        mutate { it.copy(compose = draft.copy(sending = true)) }
        scope.launch {
            try {
                withContext(io) { sync.send(account, mail) }
                mutate { it.copy(compose = null, message = AppMessage.Sent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException.SendNotAllowed) {
                mutate { it.copy(compose = it.compose?.copy(sending = false), message = AppMessage.SendNeedsReauth) }
            } catch (e: Exception) {
                println("Send failed: ${e::class.simpleName}")
                mutate { it.copy(compose = it.compose?.copy(sending = false), message = AppMessage.SendFailed) }
            }
        }
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

    private fun mutate(block: (AppState) -> AppState) = _state.update(block)
}
