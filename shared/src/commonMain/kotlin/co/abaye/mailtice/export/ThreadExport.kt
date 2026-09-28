package co.abaye.mailtice.export

import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The localised words the HTML export needs; resolved by the caller, so this stays plain code. */
data class ExportLabels(val from: String, val to: String, val date: String, val attachments: String, val exported: String)

/**
 * Builds the files a conversation is exported to: a readable HTML page, or the standard mail formats
 * (one message = .eml, several = .mbox, which every mail client can import).
 */
object ThreadExport {
    /** "Re: Re: Fwd: x" and "השב: x" all belong to the conversation "x". */
    fun baseSubject(subject: String): String {
        var s = subject.trim()
        val prefix = Regex("^(re|fw|fwd|aw|sv|השב|הועבר)\\s*(\\[\\d+])?\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
        while (true) {
            val next = s.replaceFirst(prefix, "")
            if (next == s) return s
            s = next
        }
    }

    /**
     * One self-contained page, oldest message first. HTML bodies are embedded as they came, with a
     * Content-Security-Policy that blocks scripts and every remote load (tracking pixels included);
     * plain bodies are escaped and keep their line breaks.
     */
    fun html(subject: String, messages: List<Pair<MailMessage, MailBody?>>, labels: ExportLabels, dateOf: (Long) -> String): String =
        buildString {
            append("<!DOCTYPE html>\n<html dir=\"auto\"><head><meta charset=\"utf-8\">")
            append(
                "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data: cid:; style-src 'unsafe-inline'\">",
            )
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>").append(escape(subject)).append("</title>")
            append(
                "<style>body{margin:0;background:#F3F2FD;color:#1A1B23;font:15px/1.6 'Segoe UI',Rubik,system-ui,sans-serif}" +
                    "main{max-width:820px;margin:0 auto;padding:32px 20px}h1{font-size:24px;font-weight:500;color:#001452;margin:0 0 4px}" +
                    ".meta{color:#444654;font-size:13px;margin-bottom:24px}.msg{background:#fff;border:1px solid #C4C5D6;" +
                    "border-radius:16px;padding:18px 22px;margin-bottom:16px}.head{border-bottom:1px solid #E2E1EC;padding-bottom:10px;" +
                    "margin-bottom:12px;font-size:13px;color:#444654}.head b{color:#1A1B23;font-size:14px}.body{overflow-wrap:anywhere}" +
                    ".plain{white-space:pre-wrap}.att{margin-top:12px;font-size:13px;color:#444654}" +
                    "@media(prefers-color-scheme:dark){body{background:#12131A;color:#E2E1EC}h1{color:#B7C4FF}" +
                    ".msg{background:#1E1F27;border-color:#444654}.head,.meta,.att{color:#C4C5D6}.head b{color:#E2E1EC}}</style>",
            )
            append("</head><body><main>")
            append("<h1 dir=\"auto\">").append(escape(subject)).append("</h1>")
            append("<div class=\"meta\">").append(escape(labels.exported)).append("</div>")
            messages.sortedBy { it.first.receivedAt }.forEach { (m, body) ->
                append("<section class=\"msg\"><div class=\"head\" dir=\"auto\">")
                append("<b>").append(escape(m.sender)).append("</b>")
                if (m.fromName.isNotBlank()) append(" &lt;").append(escape(m.fromAddress)).append("&gt;")
                append("<br>").append(escape(labels.to)).append(": ").append(escape(m.toLine))
                append("<br>").append(escape(labels.date)).append(": ").append(escape(dateOf(m.receivedAt)))
                append("</div>")
                val html = body?.html.orEmpty()
                if (html.isNotBlank()) {
                    append("<div class=\"body\" dir=\"auto\">").append(stripScripts(html)).append("</div>")
                } else {
                    append("<div class=\"body plain\" dir=\"auto\">").append(
                        escape(
                            body?.text?.ifBlank {
                                null
                            } ?: m.snippet,
                        ),
                    ).append("</div>")
                }
                val attachments = body?.attachments.orEmpty()
                if (attachments.isNotEmpty()) {
                    append("<div class=\"att\">").append(escape(labels.attachments)).append(": ")
                    append(attachments.joinToString(", ") { escape(it.name) }).append("</div>")
                }
                append("</section>")
            }
            append("</main></body></html>")
        }

    /**
     * mboxrd: each message is preceded by a "From " separator line, and body lines that already begin
     * with "From " (after any ">") get one more ">" so they cannot be read as a separator.
     */
    @OptIn(ExperimentalTime::class)
    fun mbox(messages: List<Pair<MailMessage, ByteArray>>): ByteArray = buildString {
        messages.sortedBy { it.first.receivedAt }.forEach { (m, raw) ->
            val t = Instant.fromEpochMilliseconds(m.receivedAt).toLocalDateTime(TimeZone.UTC)
            val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[t.dayOfWeek.isoDayNumber - 1]
            val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[t.month.number - 1]
            fun two(n: Int) = n.toString().padStart(2, '0')
            val sender = m.fromAddress.ifBlank { "MAILER-DAEMON" }
            append("From ").append(sender).append(' ')
                .append("$day $month ${t.day.toString().padStart(2, ' ')} ${two(t.hour)}:${two(t.minute)}:${two(t.second)} ${t.year}\n")
            raw.decodeToString().replace("\r\n", "\n").lineSequence().forEach { line ->
                append(if (Regex("^>*From ").containsMatchIn(line)) ">$line" else line).append('\n')
            }
            append('\n')
        }
    }.encodeToByteArray()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** The CSP already blocks scripts; removing them as well keeps the file clean in any viewer. */
    private fun stripScripts(html: String) = html
        .replace(Regex("(?is)<script.*?</script>"), "")
        .replace(Regex("(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), "")
}
