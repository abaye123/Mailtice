package co.abaye.mailtice.app

import androidx.compose.runtime.Immutable
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

enum class SignInState {
    Idle,

    /** The browser (or Play services) is open and the app waits for the redirect. */
    Waiting,
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

/** [Html]: a readable page. [Mail]: .eml for one message, .mbox for a conversation. */
enum class ExportFormat { Html, Mail }

/**
 * The message being written. Address fields hold what the user typed (comma separated) and are
 * only parsed on send; [invalidAddresses] flags a field that did not parse.
 */
@Immutable
data class ComposeDraft(
    val mode: ComposeMode = ComposeMode.New,
    val accountId: String,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val showCcBcc: Boolean = false,
    val subject: String = "",
    val body: String = "",
    val inReplyTo: String? = null,
    val references: String? = null,
    val threadId: String? = null,
    val sending: Boolean = false,
    val invalidAddresses: Boolean = false,
)

@Immutable
data class Reader(val message: MailMessage, val body: MailBody? = null, val failed: Boolean = false)

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
    val filter: InboxFilter = InboxFilter(),
    val reader: Reader? = null,
    val compose: ComposeDraft? = null,
    /** Checked rows ("<accountId>/<messageId>"); non-empty turns the list toolbar into bulk actions. */
    val selection: Set<String> = emptySet(),
    /** A download or export is running; the UI shows progress and blocks a second one. */
    val working: Boolean = false,
    val storage: StorageUsage = StorageUsage(),
    val addAccount: AddAccountStep? = null,
    val signIn: SignInState = SignInState.Idle,
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

    val selectedMessages: List<MailMessage> get() = inbox.filter { it.key in selection }

    /** Accounts the list currently covers: the one picked in the sidebar, or all of them. */
    val scopeAccounts: List<Account> get() = if (filter.accountId.isEmpty()) accounts else accounts.filter { it.id == filter.accountId }

    /** Views some account in scope actually has a folder for (Inbox and Starred always). */
    val availableViews: List<MailView> get() {
        val roles = scopeAccounts.flatMap { foldersOf(it.id) }.map { it.role }.toSet()
        return MailView.entries.filter { it.role == null || it.role == FolderRole.Inbox || it.role in roles }
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
