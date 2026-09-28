package co.abaye.mailtice.app

import androidx.compose.runtime.Composable
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.message_account_added
import mailtice.shared.generated.resources.message_account_reconnected
import mailtice.shared.generated.resources.message_action_failed
import mailtice.shared.generated.resources.message_action_queued
import mailtice.shared.generated.resources.message_attachments_too_large
import mailtice.shared.generated.resources.message_cache_cleared
import mailtice.shared.generated.resources.message_db_compacted
import mailtice.shared.generated.resources.message_download_failed
import mailtice.shared.generated.resources.message_downloading
import mailtice.shared.generated.resources.message_draft_discarded
import mailtice.shared.generated.resources.message_draft_saved
import mailtice.shared.generated.resources.message_exported
import mailtice.shared.generated.resources.message_files_saved
import mailtice.shared.generated.resources.message_launch_at_login_failed
import mailtice.shared.generated.resources.message_moved_to_trash
import mailtice.shared.generated.resources.message_no_attachments
import mailtice.shared.generated.resources.message_no_sending_account
import mailtice.shared.generated.resources.message_not_configured
import mailtice.shared.generated.resources.message_provider_not_supported
import mailtice.shared.generated.resources.message_reset_done
import mailtice.shared.generated.resources.message_schedule_cancelled
import mailtice.shared.generated.resources.message_scheduled
import mailtice.shared.generated.resources.message_scheduled_sent
import mailtice.shared.generated.resources.message_send_failed
import mailtice.shared.generated.resources.message_send_needs_reauth
import mailtice.shared.generated.resources.message_send_queued
import mailtice.shared.generated.resources.message_sent
import mailtice.shared.generated.resources.message_sign_in_failed
import org.jetbrains.compose.resources.stringResource

/** A one-line notice shown in the bar at the bottom of the window. */
enum class AppMessage {
    NotConfigured,
    SignInFailed,
    AccountAdded,
    AccountReconnected,
    ActionFailed,
    LaunchAtLoginFailed,
    ResetDone,
    CacheCleared,
    DatabaseCompacted,
    ProviderNotSupported,
    Sent,
    SendFailed,
    SendNeedsReauth,
    MovedToTrash,
    NoSendingAccount,
    Downloading,
    FilesSaved,
    NoAttachments,
    DownloadFailed,
    Exported,
    Scheduled,
    ScheduledSent,

    /** No connection: the message waits in the outgoing queue. */
    SendQueued,

    /** Offline mode, no connection: done here, sent to the server when it is back. */
    ActionQueued,
    ScheduleCancelled,
    AttachmentsTooLarge,
    DraftSaved,
    DraftDiscarded,
}

@Composable
fun AppMessage.text(): String = when (this) {
    AppMessage.NotConfigured -> stringResource(Res.string.message_not_configured)
    AppMessage.SignInFailed -> stringResource(Res.string.message_sign_in_failed)
    AppMessage.AccountAdded -> stringResource(Res.string.message_account_added)
    AppMessage.AccountReconnected -> stringResource(Res.string.message_account_reconnected)
    AppMessage.ActionFailed -> stringResource(Res.string.message_action_failed)
    AppMessage.LaunchAtLoginFailed -> stringResource(Res.string.message_launch_at_login_failed)
    AppMessage.ResetDone -> stringResource(Res.string.message_reset_done)
    AppMessage.CacheCleared -> stringResource(Res.string.message_cache_cleared)
    AppMessage.DatabaseCompacted -> stringResource(Res.string.message_db_compacted)
    AppMessage.ProviderNotSupported -> stringResource(Res.string.message_provider_not_supported)
    AppMessage.Sent -> stringResource(Res.string.message_sent)
    AppMessage.SendFailed -> stringResource(Res.string.message_send_failed)
    AppMessage.SendNeedsReauth -> stringResource(Res.string.message_send_needs_reauth)
    AppMessage.MovedToTrash -> stringResource(Res.string.message_moved_to_trash)
    AppMessage.NoSendingAccount -> stringResource(Res.string.message_no_sending_account)
    AppMessage.Downloading -> stringResource(Res.string.message_downloading)
    AppMessage.FilesSaved -> stringResource(Res.string.message_files_saved)
    AppMessage.NoAttachments -> stringResource(Res.string.message_no_attachments)
    AppMessage.DownloadFailed -> stringResource(Res.string.message_download_failed)
    AppMessage.Exported -> stringResource(Res.string.message_exported)
    AppMessage.Scheduled -> stringResource(Res.string.message_scheduled)
    AppMessage.ScheduledSent -> stringResource(Res.string.message_scheduled_sent)
    AppMessage.SendQueued -> stringResource(Res.string.message_send_queued)
    AppMessage.ActionQueued -> stringResource(Res.string.message_action_queued)
    AppMessage.ScheduleCancelled -> stringResource(Res.string.message_schedule_cancelled)
    AppMessage.AttachmentsTooLarge -> stringResource(Res.string.message_attachments_too_large)
    AppMessage.DraftSaved -> stringResource(Res.string.message_draft_saved)
    AppMessage.DraftDiscarded -> stringResource(Res.string.message_draft_discarded)
}
