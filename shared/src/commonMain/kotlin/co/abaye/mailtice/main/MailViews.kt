package co.abaye.mailtice.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailView
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.view_archive
import mailtice.shared.generated.resources.view_drafts
import mailtice.shared.generated.resources.view_inbox
import mailtice.shared.generated.resources.view_scheduled
import mailtice.shared.generated.resources.view_sent
import mailtice.shared.generated.resources.view_spam
import mailtice.shared.generated.resources.view_starred
import mailtice.shared.generated.resources.view_trash
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailView.label(): String = when (this) {
    MailView.Inbox -> stringResource(Res.string.view_inbox)
    MailView.Starred -> stringResource(Res.string.view_starred)
    MailView.Sent -> stringResource(Res.string.view_sent)
    MailView.Drafts -> stringResource(Res.string.view_drafts)
    MailView.Archive -> stringResource(Res.string.view_archive)
    MailView.Spam -> stringResource(Res.string.view_spam)
    MailView.Trash -> stringResource(Res.string.view_trash)
    MailView.Scheduled -> stringResource(Res.string.view_scheduled)
}

fun MailView.icon(): ImageVector = when (this) {
    MailView.Inbox -> Icons.Outlined.Inbox
    MailView.Starred -> Icons.Outlined.StarOutline
    MailView.Sent -> Icons.AutoMirrored.Outlined.Send
    MailView.Drafts -> Icons.Outlined.Drafts
    MailView.Archive -> Icons.Outlined.Archive
    MailView.Spam -> Icons.Outlined.Report
    MailView.Trash -> Icons.Outlined.Delete
    MailView.Scheduled -> Icons.Outlined.Schedule
}

val FolderIcon: ImageVector get() = Icons.AutoMirrored.Outlined.Label

/** The account's own folders beyond the standard views: Gmail labels, custom IMAP folders. */
fun AppState.customFolders(accountId: String): List<Folder> = foldersOf(accountId).filter { it.role == FolderRole.Other }

/** What the list is showing, for the phone's folder chip and the empty screen. */
@Composable
fun AppState.currentFolderName(): String {
    val custom = filter.folderId.takeIf { it.isNotEmpty() }?.let { id -> foldersOf(filter.accountId).firstOrNull { it.id == id } }
    return custom?.name ?: filter.view.label()
}
