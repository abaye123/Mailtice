package co.abaye.mailtice.app

import co.abaye.mailtice.domain.LabelColors
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.AppFont
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.SunsetCity
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.domain.ReadingPane
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

    /** The profile picker's answer; null = the default browser. */
    data class ChooseBrowser(val profileKey: String?) : AppIntent

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

    /** The list reached its end: read more stored rows, or ask the server for older mail. */
    data object LoadOlder : AppIntent
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
    /** Closes the window; a draft with content stays saved on the server (Gmail's behaviour). */
    data object CloseCompose : AppIntent

    /** The trash icon: closes the window and deletes the saved draft. */
    data object DiscardCompose : AppIntent

    /** The rich editor changed: its plain text and HTML. */
    data class ComposeBody(val text: String, val html: String) : AppIntent
    data class ComposeWindow(val mode: ComposeWindowMode) : AppIntent
    data class ComposeSuggest(val query: String) : AppIntent
    data object ComposeAttach : AppIntent

    /** Files dropped onto the compose window. */
    data class ComposeAddFiles(val files: List<co.abaye.mailtice.platform.PickedFile>) : AppIntent
    data class ComposeRemoveAttachment(val id: String) : AppIntent

    /** Queue the draft to go out at [sendAt] (epoch millis). */
    data class ScheduleCompose(val sendAt: Long) : AppIntent
    data class SendScheduledNow(val id: String) : AppIntent
    data class CancelScheduled(val id: String) : AppIntent
    data class EditScheduled(val id: String) : AppIntent
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
    data class SetFont(val font: AppFont) : AppIntent
    data class SetShowHebrewDate(val show: Boolean) : AppIntent
    data class SetHebrewDateAtSunset(val atSunset: Boolean) : AppIntent
    data class SetSunsetCity(val city: SunsetCity) : AppIntent
    data class SetOfferTranslation(val offer: Boolean) : AppIntent

    /** Translates the open message into the interface language. */
    data object TranslateMessage : AppIntent

    /** Between the translation of the open message and its original. */
    data class ShowOriginal(val original: Boolean) : AppIntent
    data class SetPaneStyle(val style: PaneStyle) : AppIntent
    data class SetReadingPane(val pane: ReadingPane) : AppIntent

    /** Shows or hides a sidebar entry (see [co.abaye.mailtice.domain.UserSettings.hiddenFolders]). */
    data class SetFolderHidden(val key: String, val hidden: Boolean) : AppIntent
    data class SetLabelPinned(val key: String, val pinned: Boolean) : AppIntent

    /** Folds or unfolds an account's folder tree in the sidebar. */
    data class ToggleAccountExpanded(val accountId: String) : AppIntent

    /** Opens a standard folder of one account ("" = every account). */
    data class OpenView(val accountId: String, val view: MailView) : AppIntent

    /** Opens one account's label or custom folder. */
    data class OpenLabel(val accountId: String, val folderId: String) : AppIntent
    data class CreateLabel(val accountId: String, val name: String, val color: LabelColors?) : AppIntent
    data class UpdateLabel(val accountId: String, val folderId: String, val name: String, val color: LabelColors?) : AppIntent
    data class DeleteLabel(val accountId: String, val folderId: String) : AppIntent

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
