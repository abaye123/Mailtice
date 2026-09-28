package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.calendar.dateLabel
import co.abaye.mailtice.data.ScheduledMail
import co.abaye.mailtice.ui.EmptyContent
import co.abaye.mailtice.ui.EmptyState
import co.abaye.mailtice.ui.Illustration
import co.abaye.mailtice.ui.TooltipIconButton
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.schedule_note
import mailtice.shared.generated.resources.scheduled_at
import mailtice.shared.generated.resources.scheduled_cancel
import mailtice.shared.generated.resources.scheduled_edit
import mailtice.shared.generated.resources.scheduled_empty_title
import mailtice.shared.generated.resources.scheduled_failed
import mailtice.shared.generated.resources.scheduled_send_now
import org.jetbrains.compose.resources.stringResource

/**
 * The scheduled-send queue as a list: who it goes to, when, and why a failed attempt failed, with
 * send now / edit / cancel on each row. Queued messages live in Mailtice, not on the server.
 */
@Composable
internal fun ScheduledList(state: AppState, onIntent: (AppIntent) -> Unit) {
    val items = state.scheduled.filter { state.filter.accountId.isEmpty() || it.accountId == state.filter.accountId }
    if (items.isEmpty()) {
        EmptyState(
            EmptyContent(
                Illustration.InboxZero,
                title = stringResource(Res.string.scheduled_empty_title),
                body = stringResource(Res.string.schedule_note),
            ),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                stringResource(Res.string.schedule_note),
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items, key = { it.id }) { item -> ScheduledRow(item, state.account(item.accountId), onIntent) }
    }
}

@Composable
private fun ScheduledRow(item: ScheduledMail, account: co.abaye.mailtice.domain.Account?, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (account != null) AccountAvatar(account, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.mail.to.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.mail.subject,
                style = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = colors.primary)
                Text(
                    stringResource(Res.string.scheduled_at, dateLabel(item.sendAt, withDate = true)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
            }
            if (item.lastError.isNotBlank()) {
                Text(
                    stringResource(Res.string.scheduled_failed, item.lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TooltipIconButton(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.scheduled_send_now), {
            onIntent(AppIntent.SendScheduledNow(item.id))
        })
        TooltipIconButton(Icons.Outlined.Edit, stringResource(Res.string.scheduled_edit), { onIntent(AppIntent.EditScheduled(item.id)) })
        TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.scheduled_cancel), {
            onIntent(AppIntent.CancelScheduled(item.id))
        })
    }
}
