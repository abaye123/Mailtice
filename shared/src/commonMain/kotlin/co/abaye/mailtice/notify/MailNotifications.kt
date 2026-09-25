package co.abaye.mailtice.notify

import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.platform.localizedString
import co.abaye.mailtice.sync.NewMail
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.notif_mark_read
import mailtice.shared.generated.resources.notif_open
import mailtice.shared.generated.resources.notif_summary_body
import mailtice.shared.generated.resources.notif_summary_title

/** More new messages than this in one round collapse into a single summary. */
private const val INDIVIDUAL_MAX = 5

/** Turns a round's [NewMail] into OS notifications. Shared by the app and the Android worker. */
class MailNotifications(private val notifier: Notifier) {
    suspend fun announce(newMail: NewMail, settings: UserSettings) {
        if (!notifier.available || !settings.notificationsEnabled || !newMail.account.notify) return
        val language = settings.uiLanguage.code
        val count = newMail.messages.size
        val texts = NotificationTexts(
            open = localizedString(language, Res.string.notif_open),
            markRead = localizedString(language, Res.string.notif_mark_read),
            summaryTitle = localizedString(language, Res.string.notif_summary_title, count),
            summaryBody = localizedString(language, Res.string.notif_summary_body, newMail.account.displayName),
        )
        if (count > INDIVIDUAL_MAX) {
            notifier.showSummary(newMail.account, count, texts)
        } else {
            newMail.messages.forEach { notifier.showMessage(newMail.account, it, texts) }
        }
    }
}
