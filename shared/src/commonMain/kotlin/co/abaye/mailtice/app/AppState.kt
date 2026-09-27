package co.abaye.mailtice.app

import co.abaye.mailtice.domain.AccountDigest
import co.abaye.mailtice.sync.PollPlan
import co.abaye.mailtice.domain.Attachment
import androidx.compose.runtime.Immutable
import co.abaye.mailtice.auth.BrowserProfile
import co.abaye.mailtice.data.Contact
import co.abaye.mailtice.data.ScheduledMail
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.StorageUsage
import co.abaye.mailtice.provider.ImapLoginException

@Immutable
sealed interface AppDialog {
    data object Hidden : AppDialog
    data object ConfirmReset : AppDialog
    data class ConfirmRemove(val accountId: String) : AppDialog
    data class ConfirmClearCache(val accountId: String) : AppDialog
}

/** Where a sign-in stands; the add-account dialog shows one screen per phase. */
enum class SignInPhase {
    /** The provider's page is open in the browser (or Play services); waiting for the redirect. */
    Browser,

    /** The code came back; exchanging it and reading the account address. */
    Connecting,

    /** The account is connected. */
    Done,

    /** Something went wrong; the user can try again. */
    Failed,
}

/** The IMAP form, also used to enter a new password for an existing account. */
@Immutable
data class ImapForm(
    val email: String = "",
    val password: String = "",
    val host: String = "",
    val port: String = "993",
    val security: ImapSecurity = ImapSecurity.Tls,
    val username: String = "",
    /** Set when fixing the password of an existing account; the other fields are then read-only. */
    val reconnectId: String? = null,
    val busy: Boolean = false,
    val error: ImapLoginException.Reason? = null,
)

@Immutable
sealed interface AddAccountStep {
    data object ChooseProvider : AddAccountStep
    data class Imap(val form: ImapForm) : AddAccountStep

    /** Several browser profiles exist: which one should the sign-in page open in. */
    data class ChooseBrowser(val kind: ProviderKind, val reconnectId: String? = null) : AddAccountStep

    /**
     * The sign-in in progress, shown inside the same dialog. [reconnectId] is set when an existing
     * account signs in again; [accountId] and [email] are filled once the account is known.
     */
    data class SignIn(
        val kind: ProviderKind,
        val phase: SignInPhase,
        val reconnectId: String? = null,
        val accountId: String = "",
        val email: String = "",
        /** The browser profile used, so "try again" opens the same one. */
        val profileKey: String? = null,
    ) : AddAccountStep
}

@Immutable
data class InboxFilter(
    val accountId: String = "",
    /** A standard folder across the accounts in scope. Ignored while [folderId] is set. */
    val view: MailView = MailView.Inbox,
    /** One particular folder (a label or custom IMAP folder) of the account in scope. */
    val folderId: String = "",
    val unreadOnly: Boolean = false,
    val attachmentsOnly: Boolean = false,
    val query: String = "",
)

enum class ComposeMode { New, Reply, ReplyAll, Forward }

/** Where the draft's copy on the server stands, for the compose window's title bar. */
enum class DraftSave { None, Saving, Saved, Failed }

/** The compose window's size: docked at the bottom corner, just its title bar, or large and centred. */
enum class ComposeWindowMode { Normal, Minimized, Maximized }

/** A file attached in the compose window. */
@Immutable
class DraftAttachment(val id: String, val name: String, val mimeType: String, val bytes: ByteArray) {
    val size: Long get() = bytes.size.toLong()
}

/** [Html]: a readable page. [Mail]: .eml for one message, .mbox for a conversation. */
enum class ExportFormat { Html, Mail }

/**
 * The message being written. Address fields hold what the user typed (comma separated) and are
 * only parsed on send; [invalidAddresses] flags a field that did not parse.
 */
@Immutable
data class ComposeDraft(
    val mode: ComposeMode = ComposeMode.New,
    /** Changes whenever a new draft opens, so the editor knows to start over. */
    val draftId: Long = 0,
    val accountId: String,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val showCcBcc: Boolean = false,
    val subject: String = "",
    /** Plain text of what the user wrote (the editor's text). */
    val body: String = "",
    /** The same as HTML from the rich editor; null until the editor reports. */
    val html: String? = null,
    /** HTML the editor starts with (editing a scheduled message); "" = empty. */
    val initialHtml: String = "",
    /** The quoted original of a reply / forward, kept apart from the editor and added on send. */
    val quote: String? = null,
    val quoteHtml: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
    val window: ComposeWindowMode = ComposeWindowMode.Normal,
    /** Set while editing a message from the scheduled queue; sending or rescheduling replaces it. */
    val scheduledId: String? = null,
    /** The draft saved on the server ([co.abaye.mailtice.provider.MailProvider.saveDraft]), and in which account. */
    val draftHandle: String? = null,
    val draftAccountId: String? = null,
    val draftSave: DraftSave = DraftSave.None,
    /** [draftSignature] of what was last saved, so an unchanged draft is not saved again. */
    val savedSignature: Int? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
    val threadId: String? = null,
    val sending: Boolean = false,
    val invalidAddresses: Boolean = false,
)

/**
 * Mail older than the stored window, fetched from the server for the current list (filter) as the
 * user scrolls to the end or searches. Kept in memory only; the disk holds the retention window.
 */
@Immutable
data class OlderMail(
    val items: List<MailMessage> = emptyList(),
    val loading: Boolean = false,
    /** Accounts whose server has nothing older for this list. */
    val exhausted: Set<String> = emptySet(),
    val failed: Boolean = false,
)

/** Stored rows the list reads at first; reaching the end reads [LocalPage] more before asking the server. */
const val LocalPage: Long = 500

/** Everything that makes a draft worth saving again when it changes. */
fun ComposeDraft.draftSignature(): Int =
    listOf(accountId, to, cc, bcc, subject, body, html, attachments.joinToString { it.id }).hashCode()

/** Nothing typed yet: not worth a draft on the server. */
val ComposeDraft.isBlank: Boolean
    get() = to.isBlank() && cc.isBlank() && bcc.isBlank() && subject.isBlank() && body.isBlank() && attachments.isEmpty()

@Immutable
data class Reader(
    val message: MailMessage,
    val body: MailBody? = null,
    val failed: Boolean = false,
    val translation: ReaderTranslation? = null,
    /** Inline images of the HTML, Content-ID to a data: URI, once downloaded. */
    val inlineImages: Map<String, String> = emptyMap(),
)

enum class TranslationState { Loading, Done, Failed }

/**
 * An attachment in the viewer: [bytes] once downloaded ([failed] if that did not work). On desktop
 * the file is also written to [tempPath], so it can open in its own app, and PDF, audio and video
 * load in the webview from [pageUrl].
 */
@Immutable
data class AttachmentPreview(
    val message: MailMessage,
    val index: Int,
    val attachment: Attachment,
    val bytes: ByteArray? = null,
    val failed: Boolean = false,
    val tempPath: String = "",
    val pageUrl: String = "",
)

/** The open message in another language: its subject and body in [target], or where that stands. */
@Immutable
data class ReaderTranslation(
    val target: String,
    val state: TranslationState,
    val sourceLanguage: String = "",
    val subject: String = "",
    val body: String = "",
    /** The HTML with its text translated in place; "" when only the plain text was translated. */
    val html: String = "",
    /** Translated, but the reader asked to see the original again. */
    val showOriginal: Boolean = false,
) {
    val showing: Boolean get() = state == TranslationState.Done && !showOriginal
}

@Immutable
data class AppState(
    val data: AppData = AppData(),
    val accounts: List<Account> = emptyList(),
    val folders: Map<String, List<Folder>> = emptyMap(),
    val inbox: List<MailMessage> = emptyList(),
    val unread: Map<String, Long> = emptyMap(),
    /** accountId -> folderId -> unread, for the counts next to folders. */
    val unreadByFolder: Map<String, Map<String, Long>> = emptyMap(),
    val statuses: Map<String, AccountStatus> = emptyMap(),
    /** Why an account's last sync round failed; shown next to its "offline" status. */
    val syncErrors: Map<String, String> = emptyMap(),
    /** The smart check's current pace per account, for the settings. */
    val pollPlans: Map<String, PollPlan> = emptyMap(),
    /** The home dashboard's view of each account. */
    val digests: Map<String, AccountDigest> = emptyMap(),
    /** When each account last synced without an error. */
    val lastSynced: Map<String, Long> = emptyMap(),
    val filter: InboxFilter = InboxFilter(),
    val reader: Reader? = null,
    /** The attachment open in the viewer over everything, or null. */
    val preview: AttachmentPreview? = null,
    val compose: ComposeDraft? = null,
    val older: OlderMail = OlderMail(),
    /** Address suggestions for the recipient being typed in the compose window. */
    val contactSuggestions: List<Contact> = emptyList(),
    /** The scheduled-send queue, soonest first. */
    val scheduled: List<ScheduledMail> = emptyList(),
    val localLimit: Long = LocalPage,
    /** Checked rows ("<accountId>/<messageId>"); non-empty turns the list toolbar into bulk actions. */
    val selection: Set<String> = emptySet(),
    /** A download or export is running; the UI shows progress and blocks a second one. */
    val working: Boolean = false,
    val storage: StorageUsage = StorageUsage(),
    val addAccount: AddAccountStep? = null,
    /** Browser profiles found on this computer (desktop); two or more bring up the profile picker. */
    val browserProfiles: List<BrowserProfile> = emptyList(),
    /** Providers the build has credentials for and this platform can sign in to. */
    val availableProviders: List<ProviderKind> = ProviderKind.entries,
    val dialog: AppDialog = AppDialog.Hidden,
    val message: AppMessage? = null,
) {
    val unreadTotal: Int get() = unread.values.sum().toInt()

    fun account(id: String): Account? = accounts.firstOrNull { it.id == id }

    fun status(accountId: String): AccountStatus = statuses[accountId] ?: AccountStatus.Idle

    fun foldersOf(accountId: String): List<Folder> = folders[accountId].orEmpty()

    /** Accounts that can send, in sidebar order; the compose "from" picker offers these. */
    val sendingAccounts: List<Account> get() = accounts.filter { it.capabilities.send }

    /** The list as shown: stored rows, then older ones from the server, newest first. */
    val visibleMessages: List<MailMessage> get() =
        if (older.items.isEmpty()) inbox else (inbox + older.items).sortedByDescending { it.receivedAt }

    /** The server has nothing older for any account in scope. */
    val olderExhausted: Boolean get() = scopeAccounts.all { it.id in older.exhausted }

    val selectedMessages: List<MailMessage> get() = visibleMessages.filter { it.key in selection }

    /** Accounts the list currently covers: the one picked in the sidebar, or all of them. */
    val scopeAccounts: List<Account> get() = if (filter.accountId.isEmpty()) accounts else accounts.filter { it.id == filter.accountId }

    /** Views some account in scope actually has a folder for (Inbox and Starred always). */
    val availableViews: List<MailView> get() = viewsFor(filter.accountId)

    /** The standard folders [accountId] ("" = any account) has. */
    fun viewsFor(accountId: String): List<MailView> {
        val accounts = if (accountId.isEmpty()) accounts else accounts.filter { it.id == accountId }
        val roles = accounts.flatMap { foldersOf(it.id) }.map { it.role }.toSet()
        return MailView.entries.filter {
            when (it) {
                MailView.Scheduled -> scheduled.any { s -> accountId.isEmpty() || s.accountId == accountId }
                else -> it.role == null || it.role == FolderRole.Inbox || it.role in roles
            }
        }
    }

    /** The count a standard folder shows in the sidebar for [accountId] ("" = every account). */
    fun viewCount(view: MailView, accountId: String): Int {
        val accounts = if (accountId.isEmpty()) accounts else accounts.filter { it.id == accountId }
        return when (view) {
            MailView.Inbox -> accounts.sumOf { unread[it.id] ?: 0L }.toInt()
            MailView.Spam -> accounts.sumOf { account ->
                val counts = unreadByFolder[account.id].orEmpty()
                foldersOf(account.id).filter { it.role == FolderRole.Spam }.sumOf { counts[it.id] ?: 0L }
            }.toInt()
            MailView.Scheduled -> scheduled.count { accountId.isEmpty() || it.accountId == accountId }
            else -> 0
        }
    }

    /** Unread in the folders of [view] across the accounts in scope. */
    fun unreadIn(view: MailView): Long {
        val role = view.role ?: return 0
        return scopeAccounts.sumOf { account ->
            val counts = unreadByFolder[account.id].orEmpty()
            foldersOf(account.id).filter { it.role == role }.sumOf { counts[it.id] ?: 0L }
        }
    }

    val needsReauth: List<Account> get() = accounts.filter { status(it.id) == AccountStatus.NeedsReauth }
}
