package co.abaye.mailtice.domain

import androidx.compose.runtime.Immutable
import co.abaye.mailtice.export.ThreadExport

/**
 * One row of the list in conversation view: the messages of one conversation that the list shows,
 * and the whole stored conversation they belong to (the user's own replies in Sent included).
 */
@Immutable
data class MailThread(
    val key: String,
    /** The conversation's rows in the current list, newest first; never empty. */
    val messages: List<MailMessage>,
    /** Every stored message of the conversation, oldest first; at least [messages]. */
    val all: List<MailMessage>,
) {
    /** The row stands for its newest message: its time, subject and snippet. */
    val latest: MailMessage get() = messages.first()
    val unread: Boolean get() = all.any { it.unread }
    val flagged: Boolean get() = all.any { it.flagged }
    val hasAttachments: Boolean get() = all.any { it.hasAttachments }
    val size: Int get() = all.size
    val keys: Set<String> get() = messages.mapTo(mutableSetOf()) { it.key }

    /** Everyone who wrote in the conversation, in order of their first message; [me] for [ownAddress]. */
    fun participants(ownAddress: String, me: String): List<String> = all
        .map { if (it.fromAddress.equals(ownAddress, ignoreCase = true)) me else it.sender.substringBefore(' ').ifBlank { it.sender } }
        .distinct()
}

/**
 * The conversation a message belongs to: the provider's thread id where it has one (Gmail), otherwise
 * the subject without its "Re:"/"Fwd:" prefixes, as [co.abaye.mailtice.data.MailRepository.threadOf]
 * groups it. A message with neither stands alone.
 */
fun conversationKey(message: MailMessage): String {
    if (message.threadId.isNotBlank()) return "${message.accountId}/t/${message.threadId}"
    val base = ThreadExport.baseSubject(message.subject).lowercase()
    return if (base.isBlank()) message.key else "${message.accountId}/s/$base"
}

/**
 * The list as conversations, in the order of each conversation's newest row. [stored] holds each
 * conversation's full stored thread by key; one missing (mail shown from the server only) is just
 * its rows.
 */
fun groupConversations(list: List<MailMessage>, stored: Map<String, List<MailMessage>>): List<MailThread> =
    list.groupBy(::conversationKey).map { (key, rows) ->
        // The list's rows first: they carry their labels, which the stored copies do not.
        val all = (rows + stored[key].orEmpty()).distinctBy { it.key }.sortedBy { it.receivedAt }
        MailThread(key, rows, all)
    }
