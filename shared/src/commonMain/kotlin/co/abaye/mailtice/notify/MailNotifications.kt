package co.abaye.mailtice.notify

import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.platform.localizedString
import co.abaye.mailtice.sync.NewMail
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.notif_delete
import mailtice.shared.generated.resources.notif_mark_read
import mailtice.shared.generated.resources.notif_open
import mailtice.shared.generated.resources.notif_summary_body
import mailtice.shared.generated.resources.notif_summary_title

/** More new messages than this, across every account at once, collapse into a summary per account. */
private const val INDIVIDUAL_MAX = 5

/** Turns [NewMail] into OS notifications. Shared by the app and the Android worker. */
class MailNotifications(private val notifier: Notifier) {
    /**
     * Announces rounds that came in together as one: every account syncing at startup, or the
     * pages of a long catch-up. A few messages each get their own notification; more than
     * [INDIVIDUAL_MAX] in all become one summary per account, not a flood of single ones.
     */
    suspend fun announce(rounds: List<NewMail>, settings: UserSettings) {
        if (!notifier.available || !settings.notificationsEnabled) return
        val byAccount = rounds.filter { it.account.notify && it.messages.isNotEmpty() }
            .groupBy { it.account.id }
            .map { (_, same) -> NewMail(same.last().account, same.flatMap { it.messages }.distinctBy { it.key }) }
        val total = byAccount.sumOf { it.messages.size }
        if (total == 0) return
        val language = settings.uiLanguage.code
        byAccount.forEach { newMail ->
            val count = newMail.messages.size
            val texts = NotificationTexts(
                open = localizedString(language, Res.string.notif_open),
                markRead = localizedString(language, Res.string.notif_mark_read),
                delete = localizedString(language, Res.string.notif_delete),
                summaryTitle = localizedString(language, Res.string.notif_summary_title, count),
                summaryBody = localizedString(language, Res.string.notif_summary_body, newMail.account.displayName),
            )
            if (total > INDIVIDUAL_MAX) {
                notifier.showSummary(newMail.account, count, texts)
            } else {
                newMail.messages.forEach { notifier.showMessage(newMail.account, it, texts) }
            }
        }
    }

    /** The open messages were seen: their notifications go, as Gmail does on opening a message. */
    fun dismiss(messages: List<MailMessage>) {
        messages.forEach { notifier.dismiss(it.accountId, it.id) }
    }

    /**
     * Takes back what no longer needs the user: a message that was read (here, in another app or
     * on another device) or deleted, and an account's summary once nothing in it is unread.
     */
    fun withdrawSeen(isUnread: (accountId: String, messageId: String) -> Boolean, unreadIn: (accountId: String) -> Long) {
        notifier.shown().forEach { shown ->
            val stale = shown.messageId?.let { !isUnread(shown.accountId, it) } ?: (unreadIn(shown.accountId) == 0L)
            if (stale) notifier.dismiss(shown.accountId, shown.messageId)
        }
    }
}
