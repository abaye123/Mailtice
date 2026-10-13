package co.abaye.mailtice.diag

import co.abaye.mailtice.platform.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val KEPT_LINES = 300

/**
 * The app's console lines, printed as before and the last [KEPT_LINES] also kept in memory for the
 * diagnostics file. By convention no line carries an address, a subject or message content; what a
 * server put in an error message is not ours to vouch for, so [redact] strips addresses, links and
 * host names from every kept line as well.
 */
object AppLog {
    private val lines = MutableStateFlow<List<Pair<Long, String>>>(emptyList())

    fun log(line: String) {
        println(line)
        val kept = Platform.now() to redact(line)
        lines.update { (it + kept).takeLast(KEPT_LINES) }
    }

    /** Oldest first, each with when it was logged. */
    fun recent(): List<Pair<Long, String>> = lines.value
}

private val Email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
private val Url = Regex("(?i)\\b[a-z][a-z0-9+.-]*://\\S+")
private val Ip = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
private val Host = Regex("(?i)\\b([a-z0-9-]+\\.)+[a-z]{2,}\\b")

/** [line] without addresses, links, IP addresses or host names. */
internal fun redact(line: String): String = line
    .replace(Email, "<address>")
    .replace(Url, "<link>")
    .replace(Ip, "<ip>")
    .replace(Host, "<host>")
