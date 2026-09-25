package co.abaye.mailtice.provider

import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val LINE = 76

/** Max UTF-8 bytes per RFC 2047 encoded word, so each "=?UTF-8?B?...?=" stays within 75 chars. */
private const val WORD_BYTES = 45

/**
 * Builds a plain-text RFC 5322 message for providers that take raw MIME (the Gmail API). Common code
 * with no Jakarta Mail, so it runs on every target; the SMTP path builds its message with Jakarta.
 *
 * Non-ASCII headers become RFC 2047 encoded words; the body is UTF-8 in base64, which survives any
 * transport and keeps Hebrew intact.
 */
object MimeBuilder {
    @OptIn(ExperimentalTime::class)
    fun build(from: String, mail: OutgoingMail, epochMillis: Long): String = buildString {
        fun header(name: String, value: String) {
            append(name).append(": ").append(value).append("\r\n")
        }
        header("From", encodeAddress(from))
        header("To", mail.to.joinToString(", ") { encodeAddress(it) })
        if (mail.cc.isNotEmpty()) header("Cc", mail.cc.joinToString(", ") { encodeAddress(it) })
        // The Gmail API reads Bcc from the raw message, delivers to it and strips the header.
        if (mail.bcc.isNotEmpty()) header("Bcc", mail.bcc.joinToString(", ") { encodeAddress(it) })
        header("Subject", encodeWords(mail.subject))
        header("Date", rfc5322Date(epochMillis))
        mail.inReplyTo?.let { header("In-Reply-To", it) }
        (mail.references ?: mail.inReplyTo)?.let { header("References", it) }
        header("MIME-Version", "1.0")
        when {
            mail.attachments.isNotEmpty() -> {
                // multipart/mixed: the text part (plain, or plain + HTML), then one part per file.
                val mixed = boundary(epochMillis, "m")
                header("Content-Type", "multipart/mixed; boundary=\"$mixed\"")
                append("\r\n")
                append("--$mixed\r\n")
                textParts(mail, epochMillis)
                mail.attachments.forEach { file ->
                    append("--$mixed\r\n")
                    val name = encodeWords(file.name.replace("\"", "'"))
                    append("Content-Type: ${file.mimeType}; name=\"$name\"\r\n")
                    append("Content-Disposition: attachment; filename=\"$name\"\r\n")
                    append("Content-Transfer-Encoding: base64\r\n\r\n")
                    base64Lines(file.bytes)
                }
                append("--$mixed--\r\n")
            }
            else -> textParts(mail, epochMillis)
        }
    }

    /** Headers and body of the text: text/plain alone, or multipart/alternative with an HTML twin. */
    private fun StringBuilder.textParts(mail: OutgoingMail, epochMillis: Long) {
        val html = mail.html
        if (html == null) {
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n\r\n")
            base64Lines(crlf(mail.text).encodeToByteArray())
            return
        }
        val alt = boundary(epochMillis, "a")
        append("Content-Type: multipart/alternative; boundary=\"$alt\"\r\n\r\n")
        append("--$alt\r\n")
        append("Content-Type: text/plain; charset=UTF-8\r\n")
        append("Content-Transfer-Encoding: base64\r\n\r\n")
        base64Lines(crlf(mail.text).encodeToByteArray())
        append("--$alt\r\n")
        append("Content-Type: text/html; charset=UTF-8\r\n")
        append("Content-Transfer-Encoding: base64\r\n\r\n")
        base64Lines(html.encodeToByteArray())
        append("--$alt--\r\n")
    }

    private fun StringBuilder.base64Lines(bytes: ByteArray) {
        Base64.Default.encode(bytes).chunked(LINE).forEach { append(it).append("\r\n") }
    }

    private fun crlf(text: String) = text.replace("\r\n", "\n").replace("\n", "\r\n")

    /** Unique enough per message and never found in base64 or the headers ("=_" cannot occur in base64). */
    private fun boundary(epochMillis: Long, tag: String) = "=_mailtice_${tag}_${epochMillis.toString(36)}_${(0..99999).random()}"

    /** "Name <a@b>" with a non-ASCII name gets the name encoded; a bare address passes through. */
    internal fun encodeAddress(raw: String): String {
        val trimmed = raw.trim()
        val open = trimmed.lastIndexOf('<')
        if (open <= 0 || !trimmed.endsWith(">")) return trimmed
        val name = trimmed.substring(0, open).trim().removeSurrounding("\"")
        val address = trimmed.substring(open)
        if (name.isEmpty()) return address
        return if (name.all { it.code < 128 }) "\"${name.replace("\"", "")}\" $address" else "${encodeWords(name)} $address"
    }

    /** RFC 2047 "B" encoding, split on character boundaries so no word breaks a UTF-8 sequence. */
    internal fun encodeWords(text: String): String {
        if (text.all { it.code in 32..126 }) return text
        val words = mutableListOf<String>()
        val chunk = StringBuilder()
        var i = 0
        while (i < text.length) {
            val step = if (text[i].isHighSurrogate() && i + 1 < text.length) 2 else 1
            val piece = text.substring(i, i + step)
            if ((chunk.toString() + piece).encodeToByteArray().size > WORD_BYTES) {
                words += chunk.toString()
                chunk.clear()
            }
            chunk.append(piece)
            i += step
        }
        if (chunk.isNotEmpty()) words += chunk.toString()
        return words.joinToString("\r\n ") { "=?UTF-8?B?${Base64.Default.encode(it.encodeToByteArray())}?=" }
    }

    @OptIn(ExperimentalTime::class)
    private fun rfc5322Date(epochMillis: Long): String {
        val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
        val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[t.dayOfWeek.isoDayNumber - 1]
        val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[t.month.number - 1]
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "$day, ${t.day} $month ${t.year} ${two(t.hour)}:${two(t.minute)}:${two(t.second)} +0000"
    }
}

/** Splits what the user typed in an address field; returns null when any entry is not an address. */
fun parseAddressList(raw: String): List<String>? {
    val entries = raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val valid = entries.all { entry ->
        val address = entry.substringAfterLast('<').substringBefore('>').trim()
        val at = address.indexOf('@')
        at > 0 && at < address.lastIndex && ' ' !in address && '.' in address.substring(at)
    }
    return if (valid) entries else null
}
