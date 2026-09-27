package co.abaye.mailtice.main

import mailtice.shared.generated.resources.home_sync_now
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.calendar.LocalHebrewDate
import co.abaye.mailtice.calendar.hebrewDate
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountDigest
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.MailView
import co.abaye.mailtice.platform.Platform
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_reconnect
import mailtice.shared.generated.resources.home_all_read
import mailtice.shared.generated.resources.home_checked
import mailtice.shared.generated.resources.home_evening
import mailtice.shared.generated.resources.home_fresh
import mailtice.shared.generated.resources.home_morning
import mailtice.shared.generated.resources.home_needs_attention
import mailtice.shared.generated.resources.home_new_today
import mailtice.shared.generated.resources.home_night
import mailtice.shared.generated.resources.home_noon
import mailtice.shared.generated.resources.home_not_checked
import mailtice.shared.generated.resources.home_old_unread
import mailtice.shared.generated.resources.home_open_inbox
import mailtice.shared.generated.resources.home_unread
import mailtice.shared.generated.resources.home_unread_total
import mailtice.shared.generated.resources.home_week
import mailtice.shared.generated.resources.rel_days
import mailtice.shared.generated.resources.rel_hours
import mailtice.shared.generated.resources.rel_minutes
import mailtice.shared.generated.resources.rel_now
import mailtice.shared.generated.resources.status_needs_reauth
import mailtice.shared.generated.resources.status_offline
import mailtice.shared.generated.resources.status_ok
import mailtice.shared.generated.resources.status_syncing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/**
 * The home dashboard: the app's point, several mailboxes watched at once, in one look. A greeting
 * and the totals across accounts, then a card per account in its own colour - whether it is
 * connected, how fresh its newest unread mail is ("new mail 21 minutes ago" against "12 unread,
 * nothing new in two days"), its newest unread messages and its week of incoming mail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    // Relative times ("21 minutes ago") move on by themselves.
    var now by remember { mutableLongStateOf(Platform.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Platform.now()
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
        HomeHeader(state, now, onIntent)
        Spacer(Modifier.height(20.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 16.dp
            val columns = ((maxWidth + gap) / (340.dp + gap)).toInt().coerceIn(1, 3)
            // A hair under the exact share, so rounding never pushes the last card to a row of its own.
            val cardWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                state.accounts.forEach { account ->
                    AccountCard(
                        account = account,
                        digest = state.digests[account.id],
                        status = state.status(account.id),
                        lastSynced = state.lastSynced[account.id],
                        now = now,
                        onIntent = onIntent,
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun HomeHeader(state: AppState, now: Long, onIntent: (AppIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
    val greeting = stringResource(
        when (local.hour) {
            in 5..11 -> Res.string.home_morning
            in 12..16 -> Res.string.home_noon
            in 17..21 -> Res.string.home_evening
            else -> Res.string.home_night
        },
    )
    val hebrew = LocalHebrewDate.current?.let { " · " + hebrewDate(now, it, withYear = true) }.orEmpty()
    val date = "${local.day}/${local.month.number}/${local.year}$hebrew"
    val unread = state.digests.values.sumOf { it.unread }
    val today = state.digests.values.sumOf { it.unreadRecent }
    val attention = state.accounts.count { state.status(it.id) == AccountStatus.Offline || state.status(it.id) == AccountStatus.NeedsReauth }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        // At the far end of the title row (the left in Hebrew): every account checked right now.
        val syncing = state.accounts.any { state.status(it.id) == AccountStatus.Syncing }
        FilledTonalButton(onClick = { onIntent(AppIntent.RefreshNow) }) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
            }
            Text(stringResource(Res.string.home_sync_now), Modifier.padding(start = 8.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryChip(stringResource(Res.string.home_unread_total, unread), colors.primaryContainer, colors.onPrimaryContainer)
        SummaryChip(stringResource(Res.string.home_new_today, today), colors.secondaryContainer, colors.onSecondaryContainer)
        if (attention > 0) SummaryChip(stringResource(Res.string.home_needs_attention, attention), colors.errorContainer, colors.onErrorContainer)
    }
}

@Composable
private fun SummaryChip(text: String, background: Color, content: Color) {
    Text(
        text,
        Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = content,
    )
}

@Composable
private fun AccountCard(
    account: Account,
    digest: AccountDigest?,
    status: AccountStatus,
    lastSynced: Long?,
    now: Long,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = account.color.color
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerLow, tonalElevation = 1.dp, shadowElevation = 2.dp) {
        Column {
            // The account's colour, bright to soft, with its name and whether it is connected.
            Box(
                Modifier.fillMaxWidth().height(92.dp)
                    .background(Brush.linearGradient(listOf(tint, lerp(tint, Color.White, 0.35f)))),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.92f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            account.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(account.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusPill(status)
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                UnreadLine(digest, tint, now)
                if (digest != null && digest.recentUnread.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        digest.recentUnread.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onIntent(AppIntent.OpenView(account.id, MailView.Inbox))
                                        onIntent(AppIntent.OpenMail(m))
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(8.dp).background(tint, CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(m.sender, style = MaterialTheme.typography.bodyMedium.merge(ContentDirection), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(m.subject, style = MaterialTheme.typography.bodySmall.merge(ContentDirection), color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(relativeTime(m.receivedAt, now), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (digest != null) WeekBars(digest.perDay, tint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lastSynced?.let { stringResource(Res.string.home_checked, relativeTime(it, now)) } ?: stringResource(Res.string.home_not_checked),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    if (status == AccountStatus.NeedsReauth) {
                        TextButton(onClick = { onIntent(AppIntent.Reconnect(account.id)) }) { Text(stringResource(Res.string.accounts_reconnect)) }
                    }
                    TextButton(onClick = { onIntent(AppIntent.OpenView(account.id, MailView.Inbox)) }) { Text(stringResource(Res.string.home_open_inbox)) }
                }
            }
        }
    }
}

/**
 * The headline of a card: how many are unread and how fresh the newest of them is. Fresh mail
 * (under an hour) gets a gently pulsing dot; unread mail that has been sitting for two days and
 * more is said to be old, so a long-ignored pile does not look like news.
 */
@Composable
private fun UnreadLine(digest: AccountDigest?, tint: Color, now: Long) {
    val colors = MaterialTheme.colorScheme
    if (digest == null) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        return
    }
    val age = digest.newestUnreadAt?.let { now - it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            digest.unread.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = if (digest.unread > 0) tint else colors.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.home_unread), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            when {
                digest.unread == 0 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF1E8E3E))
                    Text(stringResource(Res.string.home_all_read), style = MaterialTheme.typography.bodyMedium)
                }
                age != null && age < 2 * DAY -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (age < HOUR) PulseDot(tint) else Icon(Icons.Outlined.MarkEmailUnread, null, Modifier.size(16.dp), tint = tint)
                    Text(
                        stringResource(Res.string.home_fresh, relativeTime(digest.newestUnreadAt, now)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (age < HOUR) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                else -> Text(
                    stringResource(Res.string.home_old_unread, digest.newestUnreadAt?.let { relativeTime(it, now) }.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PulseDot(tint: Color) {
    val pulse = rememberInfiniteTransition(label = "fresh")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "fresh-alpha")
    Box(Modifier.size(10.dp).alpha(alpha).background(tint, CircleShape))
}

/** Seven small bars, today last, in the account's colour: how busy the inbox has been this week. */
@Composable
private fun WeekBars(perDay: List<Int>, tint: Color) {
    val colors = MaterialTheme.colorScheme
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.home_week, perDay.sum()), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (perDay.size - 1)) / perDay.size
            perDay.forEachIndexed { i, count ->
                val h = if (count == 0) 3.dp.toPx() else (size.height * count / max).coerceAtLeast(4.dp.toPx())
                // In right-to-left layouts the newest day still sits at the reading end.
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - (i + 1) * barWidth - i * gap else i * (barWidth + gap)
                drawRoundRect(
                    color = if (i == perDay.lastIndex) tint else tint.copy(alpha = 0.45f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: AccountStatus) {
    val (icon, text) = when (status) {
        AccountStatus.Ok -> Icons.Outlined.CheckCircle to stringResource(Res.string.status_ok)
        AccountStatus.Syncing, AccountStatus.Idle -> Icons.Outlined.Sync to stringResource(Res.string.status_syncing)
        AccountStatus.Offline -> Icons.Outlined.CloudOff to stringResource(Res.string.status_offline)
        AccountStatus.NeedsReauth -> Icons.Outlined.ErrorOutline to stringResource(Res.string.status_needs_reauth)
    }
    Row(
        Modifier.background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
        Text(text, Modifier.widthIn(max = 150.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "just now", "21 minutes ago", "2 hours ago", "3 days ago". */
@Composable
internal fun relativeTime(at: Long, now: Long): String {
    val diff = (now - at).coerceAtLeast(0)
    return when {
        diff < MINUTE -> stringResource(Res.string.rel_now)
        diff < HOUR -> (diff / MINUTE).toInt().let { pluralStringResource(Res.plurals.rel_minutes, it, it) }
        diff < DAY -> (diff / HOUR).toInt().let { pluralStringResource(Res.plurals.rel_hours, it, it) }
        else -> (diff / DAY).toInt().let { pluralStringResource(Res.plurals.rel_days, it, it) }
    }
}
