package co.abaye.mailtice.search

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.ExperimentalTime

/**
 * A search as Gmail understands it: free words (all must match), "exact phrases", -excluded words,
 * and operators. Parsed once from what the user typed; the local database, the Gmail API and IMAP
 * SEARCH each get it in their own form.
 *
 * Operators: from: to: subject: has:attachment is:unread is:read is:starred after: before:
 * (YYYY/MM/DD or YYYY-MM-DD) newer_than: older_than: (7d, 2w, 3m, 1y). Hebrew aliases: מאת: אל:
 * נושא: אחרי: לפני:. A value with spaces goes in quotes: from:"Dana Cohen".
 */
data class MailSearch(
    val words: List<String> = emptyList(),
    val excluded: List<String> = emptyList(),
    val from: String = "",
    val to: String = "",
    val subject: String = "",
    val hasAttachment: Boolean = false,
    val unread: Boolean? = null,
    val starred: Boolean = false,
    /** Epoch millis, inclusive lower bound. */
    val after: Long? = null,
    /** Epoch millis, exclusive upper bound. */
    val before: Long? = null,
) {
    val isEmpty: Boolean get() = this == MailSearch()

    /** The same search in Gmail's syntax (Hebrew aliases and relative dates resolved). */
    fun toGmailQuery(): String = buildList {
        words.forEach { add(quoteIfNeeded(it)) }
        excluded.forEach { add("-" + quoteIfNeeded(it)) }
        if (from.isNotEmpty()) add("from:" + quoteIfNeeded(from))
        if (to.isNotEmpty()) add("to:" + quoteIfNeeded(to))
        if (subject.isNotEmpty()) add("subject:" + quoteIfNeeded(subject))
        if (hasAttachment) add("has:attachment")
        unread?.let { add(if (it) "is:unread" else "is:read") }
        if (starred) add("is:starred")
        // Gmail reads epoch seconds in after:/before: as exact instants.
        after?.let { add("after:${it / 1000}") }
        before?.let { add("before:${it / 1000}") }
    }.joinToString(" ")

    companion object {
        private val ALIASES = mapOf(
            "from" to "from", "מאת" to "from", "מ" to "from",
            "to" to "to", "אל" to "to",
            "subject" to "subject", "נושא" to "subject",
            "has" to "has", "is" to "is",
            "after" to "after", "אחרי" to "after",
            "before" to "before", "לפני" to "before",
            "newer_than" to "newer_than", "older_than" to "older_than",
        )

        /** [now] anchors newer_than: / older_than:. Unknown operators are searched as plain words. */
        fun parse(raw: String, now: Long): MailSearch {
            var s = MailSearch()
            val words = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            tokens(raw).forEach { token ->
                val negated = token.startsWith("-") && token.length > 1
                val body = if (negated) token.substring(1) else token
                val colon = body.indexOf(':')
                val key = if (colon > 0) ALIASES[body.substring(0, colon).lowercase()] else null
                val value = if (colon > 0) body.substring(colon + 1).removeSurrounding("\"") else ""
                if (key == null || value.isEmpty()) {
                    val word = body.removeSurrounding("\"")
                    if (word.isNotEmpty()) (if (negated) excluded else words) += word
                    return@forEach
                }
                s = when (key) {
                    "from" -> s.copy(from = value)
                    "to" -> s.copy(to = value)
                    "subject" -> s.copy(subject = value)
                    "has" -> if (value.equals("attachment", true)) s.copy(hasAttachment = true) else s.also { words += body }
                    "is" -> when (value.lowercase()) {
                        "unread" -> s.copy(unread = true)
                        "read" -> s.copy(unread = false)
                        "starred" -> s.copy(starred = true)
                        else -> s.also { words += body }
                    }
                    "after" -> date(value)?.let { s.copy(after = it) } ?: s.also { words += body }
                    "before" -> date(value)?.let { s.copy(before = it) } ?: s.also { words += body }
                    "newer_than" -> span(value)?.let { s.copy(after = now - it) } ?: s.also { words += body }
                    "older_than" -> span(value)?.let { s.copy(before = now - it) } ?: s.also { words += body }
                    else -> s
                }
            }
            return s.copy(words = words, excluded = excluded)
        }

        /** Splits on spaces, keeping "quoted phrases" (and key:"quoted values") whole. */
        internal fun tokens(raw: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            raw.forEach { c ->
                when {
                    c == '"' -> {
                        quoted = !quoted
                        current.append(c)
                    }
                    c.isWhitespace() && !quoted -> {
                        if (current.isNotEmpty()) out += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) out += current.toString()
            return out
        }

        @OptIn(ExperimentalTime::class)
        private fun date(value: String): Long? {
            val parts = value.split('/', '-', '.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return null
            // Gmail writes year first; a day-first date (25/09/2026) is common in Israel too.
            val (y, m, d) = if (parts[0] > 31) Triple(parts[0], parts[1], parts[2]) else Triple(parts[2], parts[1], parts[0])
            return runCatching { LocalDate(y, m, d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }.getOrNull()
        }

        private fun span(value: String): Long? {
            val n = value.dropLast(1).toLongOrNull() ?: return null
            val day = 24 * 60 * 60_000L
            return when (value.last().lowercaseChar()) {
                'd' -> n * day
                'w' -> n * 7 * day
                'm' -> n * 30 * day
                'y' -> n * 365 * day
                else -> null
            }
        }

        private fun quoteIfNeeded(v: String) = if (v.any { it.isWhitespace() }) "\"$v\"" else v
    }
}
