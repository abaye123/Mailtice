package co.abaye.mailtice.notify

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.MailMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Localised labels, resolved by the caller in the user's interface language. */
data class NotificationTexts(val open: String, val markRead: String, val summaryTitle: String, val summaryBody: String)

/** What the user did with a notification. Desktop posts these from callbacks, Android from intents. */
sealed interface NotificationAction {
    data class Open(val accountId: String, val messageId: String) : NotificationAction
    data class MarkRead(val accountId: String, val messageId: String) : NotificationAction
    data class OpenAccount(val accountId: String) : NotificationAction
}

/**
 * Process-wide channel from the OS notification back into the app. A channel, not a SharedFlow:
 * on Android the tap can arrive before the view model exists, and it must wait, once, until one does.
 */
object NotificationActions {
    private val channel = Channel<NotificationAction>(capacity = Channel.BUFFERED)
    val events: Flow<NotificationAction> = channel.receiveAsFlow()

    fun post(action: NotificationAction) {
        channel.trySend(action)
    }
}

interface Notifier {
    val available: Boolean

    fun showMessage(account: Account, message: MailMessage, texts: NotificationTexts)

    fun showSummary(account: Account, count: Int, texts: NotificationTexts)
}

object NoNotifier : Notifier {
    override val available: Boolean = false
    override fun showMessage(account: Account, message: MailMessage, texts: NotificationTexts) = Unit
    override fun showSummary(account: Account, count: Int, texts: NotificationTexts) = Unit
}
