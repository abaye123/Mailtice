package co.abaye.mailtice.data

import co.abaye.mailtice.provider.OutgoingAttachment
import co.abaye.mailtice.provider.OutgoingMail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** A message waiting in the scheduled-send queue. */
data class ScheduledMail(
    val id: String,
    val accountId: String,
    val sendAt: Long,
    val mail: OutgoingMail,
    val attempts: Int = 0,
    val lastError: String = "",
)

/** One contact the compose window can suggest. */
data class Contact(val name: String, val address: String) {
    /** "Name <address>", or the bare address when there is no name. */
    val formatted: String get() = if (name.isBlank()) address else "$name <$address>"
}

/**
 * The queued message as JSON in the database. Attachments travel as base64 inside it, so a queued
 * message is complete on its own and survives a restart.
 */
internal object ScheduledCodec {
    @Serializable
    private data class File(val name: String, val mimeType: String, val data: String)

    @Serializable
    private data class Payload(
        val to: List<String>,
        val cc: List<String> = emptyList(),
        val bcc: List<String> = emptyList(),
        val subject: String,
        val text: String,
        val html: String? = null,
        val inReplyTo: String? = null,
        val references: String? = null,
        val threadId: String? = null,
        val files: List<File> = emptyList(),
        val from: String? = null,
        val replyTo: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(mail: OutgoingMail): String = json.encodeToString(
        Payload.serializer(),
        Payload(
            mail.to, mail.cc, mail.bcc, mail.subject, mail.text, mail.html, mail.inReplyTo, mail.references, mail.threadId,
            mail.attachments.map { File(it.name, it.mimeType, Base64.Default.encode(it.bytes)) },
            mail.from,
            mail.replyTo,
        ),
    )

    fun decode(raw: String): OutgoingMail {
        val p = json.decodeFromString(Payload.serializer(), raw)
        return OutgoingMail(
            to = p.to, cc = p.cc, bcc = p.bcc, subject = p.subject, text = p.text, html = p.html,
            attachments = p.files.map { OutgoingAttachment(it.name, it.mimeType, Base64.Default.decode(it.data)) },
            inReplyTo = p.inReplyTo, references = p.references, threadId = p.threadId, from = p.from, replyTo = p.replyTo,
        )
    }
}
