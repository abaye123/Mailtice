package co.abaye.mailtice.domain

import androidx.compose.runtime.Immutable

enum class ProviderKind(val oauth: Boolean) {
    /** Gmail / Google Workspace over the Gmail REST API. */
    Gmail(oauth = true),

    /** Outlook.com / Microsoft 365 over IMAP with XOAUTH2. */
    Microsoft(oauth = true),

    /** Yahoo / AOL over IMAP with XOAUTH2 (requires Yahoo's mail-scope approval). */
    Yahoo(oauth = true),

    /** Any other provider: IMAP with username + (app) password. */
    Imap(oauth = false),
}

enum class ImapSecurity { Tls, StartTls }

enum class FolderRole { Inbox, Sent, Archive, Drafts, Trash, Spam, Other }

/**
 * What an account can actually do. The UI reads this and never shows an action that is false here,
 * so nothing is offered only to fail.
 */
@Immutable
data class Capabilities(
    val markRead: Boolean = true,
    val archive: Boolean = false,
    /** Gmail labels: one message may sit in several "folders". */
    val labels: Boolean = false,
    val openInWeb: Boolean = false,
    /** IMAP CONDSTORE / Gmail history: flag changes come cheaply. */
    val incremental: Boolean = false,
    /** IMAP IDLE advertised (used in phase 2 for push on desktop). */
    val idle: Boolean = false,
    /** New mail, replies and forwards (Gmail API, or SMTP next to the IMAP server). */
    val send: Boolean = false,
    /** Move to the provider's trash (Gmail trash, or an IMAP folder with the Trash role). */
    val trash: Boolean = false,
) {
    fun encode(): String = listOf(markRead, archive, labels, openInWeb, incremental, idle, send, trash)
        .joinToString("") { if (it) "1" else "0" }

    companion object {
        val Gmail = Capabilities(markRead = true, archive = true, labels = true, openInWeb = true, incremental = true, send = true, trash = true)

        /** Older snapshots have fewer digits; the missing capabilities read as false until the next refresh. */
        fun decode(raw: String): Capabilities {
            fun at(i: Int) = raw.getOrNull(i) == '1'
            if (raw.isEmpty()) return Capabilities()
            return Capabilities(at(0), at(1), at(2), at(3), at(4), at(5), at(6), at(7))
        }
    }
}

@Immutable
data class ImapServer(val host: String, val port: Int = 993, val security: ImapSecurity = ImapSecurity.Tls)

@Immutable
data class Account(
    val id: String,
    val kind: ProviderKind,
    val email: String,
    val label: String = "",
    val color: AccountColor = AccountColor.Blue,
    val notify: Boolean = true,
    /** 0 = keep everything. */
    val retentionDays: Int = 30,
    val capabilities: Capabilities = Capabilities(),
    /** Null for Gmail. For OAuth IMAP providers the host is fixed by the provider. */
    val imap: ImapServer? = null,
    /** IMAP login name; usually the address. */
    val username: String = email,
    /** Gmail historyId. */
    val syncCursor: String = "",
) {
    val displayName: String get() = label.ifBlank { email }
}

@Immutable
data class Folder(
    val accountId: String,
    val id: String,
    val name: String,
    val role: FolderRole,
    val sync: Boolean,
    val notify: Boolean,
    val uidValidity: Long = 0,
    val uidNext: Long = 0,
    val highestModSeq: Long = 0,
)

/** A row in the unified list. */
@Immutable
data class MailMessage(
    val accountId: String,
    val id: String,
    val threadId: String,
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val toLine: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val unread: Boolean,
    val flagged: Boolean,
    val hasAttachments: Boolean,
    val sizeBytes: Long,
) {
    val sender: String get() = fromName.ifBlank { fromAddress }

    /** Unique across accounts; used for list keys and the selection. */
    val key: String get() = "$accountId/$id"
}

@Immutable
data class Attachment(val name: String, val size: Long)

@Immutable
data class MailBody(val text: String, val html: String, val attachments: List<Attachment>)

enum class AccountStatus {
    Idle,
    Syncing,
    Ok,

    /** Network or server trouble; the engine is backing off and will retry on its own. */
    Offline,

    /** Credentials rejected. Only signing in again (or a new password) fixes it. */
    NeedsReauth,
}

/** Retention choices offered in the UI. 0 = everything. */
val RetentionOptions: List<Int> = listOf(7, 30, 90, 365, 0)

@Immutable
data class StorageUsage(
    /** Size of the database files on disk plus settings. */
    val totalBytes: Long = 0,
    /** Estimated bytes per account id. */
    val perAccount: Map<String, Long> = emptyMap(),
    val messagesPerAccount: Map<String, Long> = emptyMap(),
)
