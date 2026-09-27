package co.abaye.mailtice.sync

import co.abaye.mailtice.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.io.IOException

/**
 * Offline mode: every message kept whatever the account's window, every body downloaded ahead of
 * time, attachments up to [attachmentLimitBytes] (0 = none, [Long.MAX_VALUE] = all), and actions
 * taken without a connection queued for when it comes back.
 */
data class OfflinePrefs(val enabled: Boolean = false, val attachmentLimitBytes: Long = 10L * 1024 * 1024)

/** What the user did while there was no connection, waiting to be sent to the server. */
enum class PendingKind { Read, Unread, Archive, Trash }

/** Thrown by an action that could not reach the server and was queued instead (offline mode). */
class ActionQueuedException : Exception("Queued until the connection is back")

/**
 * Whether [e] means "no connection" rather than "the server said no": a socket or DNS failure, a
 * timeout, a 429/5xx, anywhere in its cause chain (IMAP and SMTP wrap them).
 */
fun isNetworkError(e: Throwable): Boolean {
    var cause: Throwable? = e
    var depth = 0
    while (cause != null && depth < 8) {
        if (cause is IOException || cause is ProviderException.Transient) return true
        cause = cause.cause
        depth++
    }
    return false
}

/**
 * A cheap look at whether the internet is there, so a connection that shows up for a few minutes
 * is used at once instead of at the next scheduled round. Any answer at all counts as online (a
 * filtering proxy may answer with its own page); only no answer counts as offline.
 */
class NetworkProbe(private val http: HttpClient) {
    suspend fun online(): Boolean = runCatching {
        http.get("https://www.gstatic.com/generate_204") { timeout { requestTimeoutMillis = 5_000 } }
        true
    }.getOrDefault(false)
}
