package co.abaye.mailtice.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.abaye.mailtice.di.AppGraphHolder
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.platform.androidContext
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "mail"
internal const val EXTRA_ACTION = "mailtice.action"
internal const val EXTRA_ACCOUNT = "mailtice.account"
internal const val EXTRA_MESSAGE = "mailtice.message"
internal const val ACTION_OPEN = "open"
internal const val ACTION_OPEN_ACCOUNT = "openAccount"

/** NotificationCompat on one "mail" channel. Taps open the app; "mark read" runs without it. */
class AndroidNotifier(private val context: Context) : Notifier {
    private val manager = NotificationManagerCompat.from(context)

    override val available: Boolean
        get() = manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    private fun ensureChannel(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun showMessage(account: Account, message: MailMessage, texts: NotificationTexts) {
        ensureChannel(texts.open)
        val id = (account.id + message.id).hashCode()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(message.sender)
            .setContentText(message.subject.ifBlank { message.snippet })
            .setSubText(account.displayName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${message.subject}\n${message.snippet}"))
            .setAutoCancel(true)
            .setContentIntent(openIntent(id, ACTION_OPEN, account.id, message.id))
            .setGroup(account.id)
        if (account.capabilities.markRead) {
            val markRead = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, MarkReadReceiver::class.java)
                    .putExtra(EXTRA_ACCOUNT, account.id).putExtra(EXTRA_MESSAGE, message.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, texts.markRead, markRead)
        }
        notify(id, builder)
    }

    override fun showSummary(account: Account, count: Int, texts: NotificationTexts) {
        ensureChannel(texts.open)
        val id = account.id.hashCode()
        notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(texts.summaryTitle)
                .setContentText(texts.summaryBody)
                .setNumber(count)
                .setAutoCancel(true)
                .setContentIntent(openIntent(id, ACTION_OPEN_ACCOUNT, account.id, null)),
        )
    }

    private fun openIntent(requestCode: Int, action: String, accountId: String, messageId: String?): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ACTION, action)
            .putExtra(EXTRA_ACCOUNT, accountId)
            .putExtra(EXTRA_MESSAGE, messageId)
        return PendingIntent.getActivity(context, requestCode, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission") // checked in available
    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (available) manager.notify(id, builder.build())
    }
}

/** Called by AppActivity with the intent that opened it; turns a notification tap into an action. */
fun handleNotificationIntent(intent: Intent?) {
    val accountId = intent?.getStringExtra(EXTRA_ACCOUNT) ?: return
    when (intent.getStringExtra(EXTRA_ACTION)) {
        ACTION_OPEN -> intent.getStringExtra(EXTRA_MESSAGE)?.let { NotificationActions.post(NotificationAction.Open(accountId, it)) }
        ACTION_OPEN_ACCOUNT -> NotificationActions.post(NotificationAction.OpenAccount(accountId))
    }
    intent.removeExtra(EXTRA_ACTION)
}

/**
 * Work a notification action starts after its receiver returned (goAsync). One process-wide scope,
 * so every such job has a home instead of an orphan scope per broadcast.
 */
@StructuredScope
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** "Mark as read" from the notification, without opening the app. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT) ?: return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        NotificationManagerCompat.from(context).cancel((accountId + messageId).hashCode())
        val pending = goAsync()
        receiverScope.launch {
            try {
                val graph = AppGraphHolder.graph
                graph.repository.message(accountId, messageId)?.let { graph.sync.setRead(it, read = true) }
            } catch (e: Exception) {
                println("MarkRead from notification failed: ${e::class.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun createAndroidNotifier(): Notifier = AndroidNotifier(androidContext())
