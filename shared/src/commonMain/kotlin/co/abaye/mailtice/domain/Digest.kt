package co.abaye.mailtice.domain

import androidx.compose.runtime.Immutable

/**
 * One account at a glance, for the home dashboard: its unread inbox mail, how fresh the newest of
 * it is, a few of the newest unread messages, and the inbox arrivals of the last seven days.
 */
@Immutable
data class AccountDigest(
    val accountId: String,
    val unread: Int,
    /** Unread that arrived in the last 24 hours. */
    val unreadRecent: Int,
    val newestUnreadAt: Long?,
    val newestAt: Long?,
    val recentUnread: List<MailMessage>,
    /** Inbox arrivals per day, oldest first; the last entry is today. */
    val perDay: List<Int>,
)
