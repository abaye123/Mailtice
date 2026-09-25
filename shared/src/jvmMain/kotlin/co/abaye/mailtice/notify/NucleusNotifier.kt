package co.abaye.mailtice.notify

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.MailMessage
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.NotificationResult
import dev.nucleusframework.notification.common.notification

/**
 * Windows toast / macOS UserNotifications / Linux D-Bus through Nucleus notification-common.
 * Clicks go back into the app through [NotificationActions].
 */
class NucleusNotifier : Notifier {
    override val available: Boolean by lazy { runCatching { NotificationManager.isAvailable() }.getOrDefault(false) }

    override fun showMessage(account: Account, message: MailMessage, texts: NotificationTexts) {
        send(
            title = "${message.sender} · ${account.displayName}",
            body = message.subject.ifBlank { message.snippet },
            onClick = { NotificationActions.post(NotificationAction.Open(account.id, message.id)) },
        ) {
            button(texts.open) { NotificationActions.post(NotificationAction.Open(account.id, message.id)) }
            if (account.capabilities.markRead) {
                button(texts.markRead) { NotificationActions.post(NotificationAction.MarkRead(account.id, message.id)) }
            }
        }
    }

    override fun showSummary(account: Account, count: Int, texts: NotificationTexts) {
        send(
            title = texts.summaryTitle,
            body = texts.summaryBody,
            onClick = { NotificationActions.post(NotificationAction.OpenAccount(account.id)) },
        ) {}
    }

    private fun send(
        title: String,
        body: String,
        onClick: () -> Unit,
        buttons: dev.nucleusframework.notification.common.NotificationBuilder.() -> Unit,
    ) {
        if (!available) return
        val result = notification(
            title = title,
            message = body,
            onActivated = onClick,
            onFailed = { println("Notifier: OS refused to display the notification") },
        ) { buttons() }.send()
        if (result is NotificationResult.Failure) println("Notifier: not sent - ${result.reason}")
    }
}
