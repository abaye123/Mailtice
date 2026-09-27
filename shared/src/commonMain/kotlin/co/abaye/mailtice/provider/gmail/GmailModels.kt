package co.abaye.mailtice.provider.gmail

import kotlinx.serialization.Serializable

// Only the fields the app reads. The client's Json ignores everything else.

@Serializable
data class GmailProfile(val emailAddress: String, val historyId: String = "")

@Serializable
data class LabelColor(val textColor: String = "", val backgroundColor: String = "")

@Serializable
data class GmailLabel(
    val id: String,
    val name: String,
    val type: String = "user",
    val color: LabelColor? = null,
    val labelListVisibility: String? = null,
    val messageListVisibility: String? = null,
)

/** The body of labels.create and labels.update; a missing colour clears it on update. */
@Serializable
data class LabelWrite(
    val name: String,
    val color: LabelColor? = null,
    val labelListVisibility: String = "labelShow",
    val messageListVisibility: String = "show",
    val id: String? = null,
)

@Serializable
data class LabelList(val labels: List<GmailLabel> = emptyList())

@Serializable
data class MessageRef(val id: String, val threadId: String = "", val labelIds: List<String> = emptyList())

@Serializable
data class MessageList(val messages: List<MessageRef> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class Header(val name: String, val value: String)

@Serializable
data class PartBody(val size: Long = 0, val data: String? = null, val attachmentId: String? = null)

@Serializable
data class MessagePart(
    val mimeType: String = "",
    val filename: String = "",
    val headers: List<Header> = emptyList(),
    val body: PartBody? = null,
    val parts: List<MessagePart> = emptyList(),
)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String = "",
    val labelIds: List<String> = emptyList(),
    val snippet: String = "",
    val internalDate: String = "0",
    val sizeEstimate: Long = 0,
    val payload: MessagePart? = null,
) {
    fun header(name: String): String = payload?.headers?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value.orEmpty()
}

@Serializable
data class MessageEnvelope(val message: MessageRef)

@Serializable
data class LabelChange(val message: MessageRef, val labelIds: List<String> = emptyList())

@Serializable
data class HistoryRecord(
    val id: String = "",
    val messagesAdded: List<MessageEnvelope> = emptyList(),
    val messagesDeleted: List<MessageEnvelope> = emptyList(),
    val labelsAdded: List<LabelChange> = emptyList(),
    val labelsRemoved: List<LabelChange> = emptyList(),
)

@Serializable
data class HistoryList(val history: List<HistoryRecord> = emptyList(), val nextPageToken: String? = null, val historyId: String = "")

@Serializable
data class AttachmentBody(val size: Long = 0, val data: String = "")

@Serializable
data class RawMessage(val id: String, val raw: String = "")

@Serializable
data class DraftMessage(val raw: String, val threadId: String? = null)

@Serializable
data class DraftRequest(val message: DraftMessage)

@Serializable
data class DraftRef(val id: String, val message: MessageRef? = null)

@Serializable
data class DraftList(val drafts: List<DraftRef> = emptyList(), val nextPageToken: String? = null)

/** `raw` is the whole RFC 5322 message in base64url. */
@Serializable
data class SendRequest(val raw: String, val threadId: String? = null)

@Serializable
data class ModifyRequest(val addLabelIds: List<String> = emptyList(), val removeLabelIds: List<String> = emptyList())

const val LABEL_INBOX = "INBOX"
const val LABEL_UNREAD = "UNREAD"
const val LABEL_STARRED = "STARRED"

/** Where each touched message ends up after a run of history records (last record wins). */
data class HistoryDelta(
    /** id -> current label set, for messages added or relabelled. */
    val labels: Map<String, List<String>>,
    val added: Set<String>,
    val deleted: Set<String>,
    val historyId: String,
)

fun reduceHistory(records: List<HistoryRecord>, historyId: String): HistoryDelta {
    val labels = linkedMapOf<String, List<String>>()
    val added = mutableSetOf<String>()
    val deleted = mutableSetOf<String>()
    for (record in records) {
        record.messagesAdded.forEach {
            added += it.message.id
            deleted -= it.message.id
            labels[it.message.id] = it.message.labelIds
        }
        record.labelsAdded.forEach { labels[it.message.id] = it.message.labelIds }
        record.labelsRemoved.forEach { labels[it.message.id] = it.message.labelIds }
        record.messagesDeleted.forEach {
            deleted += it.message.id
            labels.remove(it.message.id)
            added -= it.message.id
        }
    }
    return HistoryDelta(labels, added, deleted, historyId)
}
