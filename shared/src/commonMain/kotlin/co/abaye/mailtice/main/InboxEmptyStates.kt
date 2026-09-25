package co.abaye.mailtice.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppKey
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.ui.EmptyContent
import co.abaye.mailtice.ui.EmptyState
import co.abaye.mailtice.ui.Illustration
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.empty_account_body
import mailtice.shared.generated.resources.empty_attachments_body
import mailtice.shared.generated.resources.empty_attachments_title
import mailtice.shared.generated.resources.empty_folder_body
import mailtice.shared.generated.resources.empty_folder_title
import mailtice.shared.generated.resources.empty_inbox_body
import mailtice.shared.generated.resources.empty_offline_action
import mailtice.shared.generated.resources.empty_offline_body
import mailtice.shared.generated.resources.empty_offline_title
import mailtice.shared.generated.resources.empty_reader_body
import mailtice.shared.generated.resources.empty_search_action
import mailtice.shared.generated.resources.empty_search_body
import mailtice.shared.generated.resources.empty_search_title
import mailtice.shared.generated.resources.empty_syncing_body
import mailtice.shared.generated.resources.empty_syncing_title
import mailtice.shared.generated.resources.empty_unread_action
import mailtice.shared.generated.resources.empty_unread_body
import mailtice.shared.generated.resources.empty_unread_title
import mailtice.shared.generated.resources.empty_welcome_body
import mailtice.shared.generated.resources.empty_welcome_title
import mailtice.shared.generated.resources.inbox_empty
import mailtice.shared.generated.resources.inbox_no_accounts_action
import mailtice.shared.generated.resources.inbox_select_message
import org.jetbrains.compose.resources.stringResource

/** Why the message list is empty, most specific reason first. */
private enum class ListEmptyReason { Search, FirstSync, Offline, Attachments, Unread, Folder, Account, Inbox }

/** The empty list is explained by a sync that has not caught up (or cannot), not by an empty folder. */
internal fun AppState.emptyBecauseOfSync(): Boolean = listEmptyReason().let { it == ListEmptyReason.FirstSync || it == ListEmptyReason.Offline }

private fun AppState.listEmptyReason(): ListEmptyReason {
    val f = filter
    val relevant = if (f.accountId.isEmpty()) accounts else accounts.filter { it.id == f.accountId }
    val statuses = relevant.map { status(it.id) }
    return when {
        f.query.isNotBlank() -> ListEmptyReason.Search
        // Idle = not synced yet this session; Syncing is only reported for an account's first sync.
        statuses.any { it == AccountStatus.Syncing || it == AccountStatus.Idle } -> ListEmptyReason.FirstSync
        statuses.isNotEmpty() && statuses.all { it == AccountStatus.Offline } -> ListEmptyReason.Offline
        f.attachmentsOnly -> ListEmptyReason.Attachments
        f.unreadOnly -> ListEmptyReason.Unread
        f.folderId.isNotEmpty() || f.view != MailView.Inbox -> ListEmptyReason.Folder
        f.accountId.isNotEmpty() -> ListEmptyReason.Account
        else -> ListEmptyReason.Inbox
    }
}

/** Onboarding: shown instead of the whole inbox until the first account is connected. */
@Composable
internal fun WelcomeEmptyState(onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        EmptyContent(
            Illustration.Welcome,
            title = stringResource(Res.string.empty_welcome_title),
            body = stringResource(Res.string.empty_welcome_body),
            action = stringResource(Res.string.inbox_no_accounts_action),
        ),
        modifier = modifier,
        illustrationSize = 200.dp,
        onAction = {
            onIntent(AppIntent.Navigate(AppKey.Accounts))
            onIntent(AppIntent.StartAddAccount)
        },
    )
}

@Composable
internal fun MessageListEmptyState(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val filter = state.filter
    val content = when (state.listEmptyReason()) {
        ListEmptyReason.Search -> EmptyContent(
            Illustration.NoResults,
            title = stringResource(Res.string.empty_search_title),
            body = stringResource(Res.string.empty_search_body, filter.query.trim()),
            action = stringResource(Res.string.empty_search_action),
        )
        ListEmptyReason.FirstSync -> EmptyContent(
            Illustration.Syncing,
            title = stringResource(Res.string.empty_syncing_title),
            body = stringResource(Res.string.empty_syncing_body),
        )
        ListEmptyReason.Offline -> EmptyContent(
            Illustration.Offline,
            title = stringResource(Res.string.empty_offline_title),
            body = stringResource(Res.string.empty_offline_body),
            action = stringResource(Res.string.empty_offline_action),
        )
        ListEmptyReason.Attachments -> EmptyContent(
            Illustration.NoResults,
            title = stringResource(Res.string.empty_attachments_title),
            body = stringResource(Res.string.empty_attachments_body),
            secondaryAction = stringResource(Res.string.empty_unread_action),
        )
        ListEmptyReason.Unread -> EmptyContent(
            Illustration.AllRead,
            title = stringResource(Res.string.empty_unread_title),
            body = stringResource(Res.string.empty_unread_body),
            secondaryAction = stringResource(Res.string.empty_unread_action),
        )
        ListEmptyReason.Folder -> {
            val folder = state.currentFolderName()
            EmptyContent(
                Illustration.InboxZero,
                title = stringResource(Res.string.empty_folder_title),
                body = stringResource(Res.string.empty_folder_body, folder),
            )
        }
        ListEmptyReason.Account -> EmptyContent(
            Illustration.InboxZero,
            title = stringResource(Res.string.inbox_empty),
            body = stringResource(Res.string.empty_account_body, state.account(filter.accountId)?.displayName.orEmpty()),
        )
        ListEmptyReason.Inbox -> EmptyContent(
            Illustration.InboxZero,
            title = stringResource(Res.string.inbox_empty),
            body = stringResource(Res.string.empty_inbox_body),
        )
    }
    EmptyState(
        content,
        modifier = modifier,
        illustrationSize = if (LocalCompactLayout.current) 160.dp else 176.dp,
        onAction = {
            when (content.illustration) {
                Illustration.NoResults -> onIntent(AppIntent.SetSearchQuery(""))
                Illustration.Offline -> onIntent(AppIntent.RefreshNow)
                else -> Unit
            }
        },
        onSecondaryAction = {
            onIntent(AppIntent.SetUnreadOnly(false))
            onIntent(AppIntent.SetAttachmentsOnly(false))
        },
    )
}

/** Desktop reader pane before a message is picked. */
@Composable
internal fun ReaderEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        EmptyContent(
            Illustration.SelectMessage,
            title = stringResource(Res.string.inbox_select_message),
            body = stringResource(Res.string.empty_reader_body),
        ),
        modifier = modifier,
        illustrationSize = 160.dp,
    )
}
