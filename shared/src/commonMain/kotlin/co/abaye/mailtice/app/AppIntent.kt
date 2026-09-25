package co.abaye.mailtice.app

import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.ThemeMode
import co.abaye.mailtice.domain.UiLanguage

sealed interface AppIntent {
    data class Navigate(val destination: AppKey) : AppIntent
    data object Back : AppIntent
    data object Quit : AppIntent

    // Adding / fixing accounts
    data object StartAddAccount : AppIntent
    data class ChooseProvider(val kind: ProviderKind) : AppIntent
    data class UpdateImapForm(val form: ImapForm) : AppIntent
    data object DetectImapServer : AppIntent
    data object SubmitImapForm : AppIntent
    data object CloseAddAccount : AppIntent
    data class Reconnect(val accountId: String) : AppIntent
    data object CancelSignIn : AppIntent

    /** From the "sign-in failed" screen: the same provider (and account, when reconnecting) again. */
    data object RetrySignIn : AppIntent

    /** From the "connected" screen: close the dialog and show the new account's mail. */
    data class OpenAccount(val accountId: String) : AppIntent

    // Account settings
    data class RemoveAccount(val accountId: String) : AppIntent
    data class SetAccountLabel(val accountId: String, val label: String) : AppIntent
    data class CycleAccountColor(val accountId: String) : AppIntent
    data class SetAccountNotify(val accountId: String, val on: Boolean) : AppIntent
    data class SetRetention(val accountId: String, val days: Int) : AppIntent
    data class SetFolderPrefs(val accountId: String, val folderId: String, val sync: Boolean, val notify: Boolean) : AppIntent
    data class RefreshFolders(val accountId: String) : AppIntent
    data class ClearAccountCache(val accountId: String) : AppIntent

    // Inbox & reader
    /** Empty string = every account / every folder. */
    data class SetFilterAccount(val accountId: String) : AppIntent
    data class SetFilterFolder(val folderId: String) : AppIntent
    data class SetUnreadOnly(val on: Boolean) : AppIntent
    data class SetAttachmentsOnly(val on: Boolean) : AppIntent
    data class SetView(val view: MailView) : AppIntent
    data class Trash(val message: MailMessage) : AppIntent

    data object ToggleSidebar : AppIntent
    data class SetListFraction(val fraction: Float) : AppIntent

    data class ToggleSelect(val message: MailMessage) : AppIntent
    data object SelectAll : AppIntent
    data object ClearSelection : AppIntent
    data class BulkSetRead(val read: Boolean) : AppIntent
    data object BulkArchive : AppIntent
    data object BulkTrash : AppIntent
    data object BulkDownloadAttachments : AppIntent

    /** [index] is the attachment's position in the message body; null = every attachment. */
    data class DownloadAttachments(val message: MailMessage, val index: Int? = null) : AppIntent
    data class DownloadThreadAttachments(val message: MailMessage) : AppIntent
    data class ExportThread(val message: MailMessage, val format: ExportFormat) : AppIntent

    /** [message] is the one answered or forwarded; null for a new message. */
    data class StartCompose(val mode: ComposeMode, val message: MailMessage? = null) : AppIntent
    data class UpdateCompose(val draft: ComposeDraft) : AppIntent
    data object SendCompose : AppIntent
    data object CloseCompose : AppIntent
    data class SetSearchQuery(val query: String) : AppIntent
    data class OpenMail(val message: MailMessage) : AppIntent
    data object CloseReader : AppIntent
    data class SetRead(val message: MailMessage, val read: Boolean) : AppIntent
    data class Archive(val message: MailMessage) : AppIntent
    data class OpenInWeb(val message: MailMessage) : AppIntent
    data class OpenHtml(val message: MailMessage) : AppIntent
    data object RefreshNow : AppIntent

    // Settings
    data class SetTheme(val mode: ThemeMode) : AppIntent
    data class SetAccent(val accent: AccentColor) : AppIntent
    data class SetDensity(val density: ListDensity) : AppIntent
    data class SetPaneStyle(val style: PaneStyle) : AppIntent

    /** `null` follows the OS language. */
    data class SetUiLanguage(val language: UiLanguage?) : AppIntent
    data class SetPollInterval(val seconds: Int) : AppIntent
    data class SetNotifications(val on: Boolean) : AppIntent
    data class SetCloseToTray(val on: Boolean) : AppIntent
    data class SetLaunchAtLogin(val on: Boolean) : AppIntent
    data object RefreshStorage : AppIntent
    data object CompactDatabase : AppIntent

    data class OpenUrl(val url: String) : AppIntent

    data object ResetApp : AppIntent
    data object ConfirmDialog : AppIntent
    data object DismissDialog : AppIntent
    data object DismissMessage : AppIntent
}
