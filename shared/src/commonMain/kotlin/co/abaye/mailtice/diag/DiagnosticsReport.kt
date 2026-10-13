package co.abaye.mailtice.diag

import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.dev.DemoMode
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.main.formatTime
import co.abaye.mailtice.platform.Platform

/** How many messages one folder holds here, by its role (a label is [FolderRole.Other]). */
internal data class FolderCount(val accountId: String, val role: String, val synced: Boolean, val total: Long, val unread: Long)

/** An account's stored messages; [withoutFolder] are rows no folder or label lists any more. */
internal data class MessageCount(val accountId: String, val total: Long, val unread: Long, val withoutFolder: Long)

/** What the database holds, as counts: [bodies] and [pending] are by account id, [pending] then by action. */
internal data class DiagnosticCounts(
    val folders: List<FolderCount>,
    val messages: List<MessageCount>,
    val bodies: Map<String, Long>,
    val pending: Map<String, Map<String, Long>>,
)

/**
 * The diagnostics file a user can attach to a bug report: versions, settings, and per account its
 * state and counts, then the recent log. Accounts are numbered, never named; folders appear by
 * role, labels only as a count; no address, subject, snippet or folder name is ever read into it.
 */
internal object DiagnosticsReport {
    fun build(state: AppState, counts: DiagnosticCounts, now: Long): String = buildString {
        val settings = state.data.settings
        appendLine("Mailtice diagnostics")
        appendLine("Counts and states only: no addresses, names, subjects or message content.")
        appendLine()
        appendLine("Created: ${formatTime(now, withDate = true)}")
        appendLine("Version: ${Platform.appVersion.ifBlank { "-" }} on ${Platform.osLabel}")
        if (Platform.isPortable) appendLine("Portable copy")
        if (DemoMode.enabled) appendLine("Demo mode")
        appendLine(
            "Settings: language ${settings.uiLanguage.code}${if (settings.uiLanguageAuto) " (auto)" else ""}" +
                ", conversations ${onOff(settings.conversationView)}" +
                ", offline mode ${onOff(settings.offlineMode)}" +
                ", smart checking ${onOff(settings.smartPolling)}, every ${settings.pollSeconds}s" +
                ", notifications ${onOff(settings.notificationsEnabled)}" +
                ", reading pane ${settings.readingPane.name}, html ${settings.htmlView.name}, accounts ${settings.accountsNav.name}",
        )
        appendLine("Accounts: ${state.accounts.size}")
        state.accounts.forEachIndexed { index, account ->
            appendLine()
            appendLine("Account ${index + 1} (${account.kind.name})")
            val synced = state.lastSynced[account.id]?.let { ago(now - it) } ?: "never"
            appendLine("  status ${state.status(account.id).name}, last sync $synced")
            val keeps = if (account.retentionDays == 0) "everything" else "${account.retentionDays} days"
            appendLine(
                "  history cursor ${if (account.syncCursor.isNotEmpty()) "yes" else "no"}, keeps $keeps, notifications ${onOff(
                    account.notify,
                )}",
            )
            state.syncErrors[account.id]?.let { appendLine("  last error: ${redact(it)}") }
            appendLine("  capabilities ${account.capabilities.encode()}")
            val messages = counts.messages.firstOrNull { it.accountId == account.id }
            appendLine(
                "  messages ${messages?.total ?: 0}, unread ${messages?.unread ?: 0}" +
                    ", in no folder ${messages?.withoutFolder ?: 0}, bodies stored ${counts.bodies[account.id] ?: 0}",
            )
            val folders = counts.folders.filter { it.accountId == account.id }
            folders.filter { it.role != FolderRole.Other.name }.forEach { folder ->
                appendLine("  ${folder.role}: ${folder.total}, unread ${folder.unread}${if (folder.synced) "" else ", not synced"}")
            }
            val labels = folders.filter { it.role == FolderRole.Other.name }
            if (labels.isNotEmpty()) {
                appendLine(
                    "  labels and other folders: ${labels.size} (${labels.count { it.synced }} synced)" +
                        ", ${labels.sumOf { it.total }} messages, unread ${labels.sumOf { it.unread }}",
                )
            }
            val pending = counts.pending[account.id].orEmpty()
            appendLine(
                "  waiting for the server: ${if (pending.isEmpty()) {
                    "nothing"
                } else {
                    pending.entries.joinToString {
                        "${it.key} ${it.value}"
                    }
                }}",
            )
        }
        appendLine()
        appendLine("Recent log (addresses, links and host names removed):")
        val log = AppLog.recent()
        if (log.isEmpty()) appendLine("  (empty)")
        log.forEach { (at, line) -> appendLine("  ${formatTime(at, withDate = true)}  $line") }
    }

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    private fun ago(millis: Long): String {
        val minutes = millis / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 48 * 60 -> "${minutes / 60} h ago"
            else -> "${minutes / (24 * 60)} days ago"
        }
    }
}
