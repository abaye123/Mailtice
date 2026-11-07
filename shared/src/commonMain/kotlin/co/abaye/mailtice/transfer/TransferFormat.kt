package co.abaye.mailtice.transfer

import co.abaye.mailtice.provider.gmail.GmailLabel
import co.abaye.mailtice.provider.gmail.GmailMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The transfer file: mail carried on a USB stick to a computer without internet. A zip archive,
 * written by the app (Settings, "Export to a file") or by the Mailtice browser extension, and read
 * by the app's "Import from a file". Entries, in this order:
 *
 * - `manifest.json` - [TransferManifest], always first.
 * - `accounts/<key>/account.json` - [TransferAccount], before anything else of that account.
 * - `accounts/<key>/messages/<chunk>.jsonl` - one [TransferMessage] per line.
 * - `accounts/<key>/files/<n>/<index>` - the bytes of attachment `index` of message `n`, after the
 *   chunk that holds message `n`.
 *
 * The order lets the importer read the archive as a stream, one entry at a time, however large it
 * is. The extension writes the same layout (`extension/src/zip.js`, `extension/src/exporter.js`).
 */
internal object TransferFormat {
    const val FORMAT = "mailtice-transfer"

    /** Bumped only for a change an older importer would misread; new optional fields need no bump. */
    const val VERSION = 1
    const val EXTENSION = "mailtice"
    const val MANIFEST = "manifest.json"

    /** Messages per `.jsonl` entry: small enough to read one at a time, big enough to stay few. */
    const val CHUNK = 200

    fun accountEntry(key: String) = "accounts/$key/account.json"

    fun chunkEntry(key: String, chunk: Int) = "accounts/$key/messages/${chunk.toString().padStart(5, '0')}.jsonl"

    fun fileEntry(key: String, n: Int, index: Int) = "accounts/$key/files/$n/$index"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
}

@Serializable
internal data class TransferManifest(
    val format: String = TransferFormat.FORMAT,
    val version: Int = TransferFormat.VERSION,
    val createdAt: Long = 0,
    /** "Mailtice 1.4.0 (Windows)", "Mailtice extension 1.0.0": shown nowhere, kept for bug reports. */
    val generator: String = "",
    val accounts: List<String> = emptyList(),
)

@Serializable
internal data class TransferAccount(
    val key: String,
    /** [co.abaye.mailtice.domain.ProviderKind] name. */
    val kind: String,
    val email: String,
    val label: String = "",
    /** [co.abaye.mailtice.domain.AccountColor] name, "" = any. */
    val color: String = "",
    /** The app's folders. */
    val folders: List<TransferFolder> = emptyList(),
    /** The extension's: Gmail's labels as its API returns them, mapped here the way the sync maps them. */
    val gmailLabels: List<GmailLabel> = emptyList(),
    /** IMAP server and login (never the password), so the copy can be connected later. */
    val imapHost: String = "",
    val imapPort: Int = 0,
    val imapSecurity: String = "",
    val username: String = "",
    val messages: Int = 0,
)

@Serializable
internal data class TransferFolder(val id: String, val name: String, val role: String, val color: String = "")

/**
 * One message: the app's own fields, or - from the extension - [gmail], the message in Gmail's full
 * format, which the importer reads with the same code the Gmail sync uses. [n] numbers the message
 * within its account; its attachment files are named by it. [files] lists the attachment indexes
 * the archive carries.
 */
@Serializable
internal data class TransferMessage(
    val n: Int,
    val id: String = "",
    val threadId: String = "",
    val uid: Long = 0,
    val fromName: String = "",
    val fromAddress: String = "",
    val toLine: String = "",
    val subject: String = "",
    val snippet: String = "",
    val receivedAt: Long = 0,
    val unread: Boolean = false,
    val flagged: Boolean = false,
    val hasAttachments: Boolean = false,
    val sizeBytes: Long = 0,
    val folderIds: List<String> = emptyList(),
    val body: TransferBody? = null,
    val gmail: GmailMessage? = null,
    val files: List<Int> = emptyList(),
)

@Serializable
internal data class TransferBody(
    val text: String = "",
    val html: String = "",
    val attachments: List<TransferAttachment> = emptyList(),
    val dynamic: Boolean = false,
)

@Serializable
internal data class TransferAttachment(val name: String, val size: Long = 0, val contentId: String = "", val mimeType: String = "")

/** Writes archive entries; see [TransferFormat] for the order the importer expects. */
interface ArchiveSink {
    /** [compress] false for content that is compressed already (most attachments). */
    fun put(name: String, bytes: ByteArray, compress: Boolean = true)
}

class ArchiveEntry(val name: String, val bytes: ByteArray)

/** Reads archive entries in the order they were written; null at the end. */
interface ArchiveSource : AutoCloseable {
    fun next(): ArchiveEntry?
}

/** Why an import stopped. */
enum class TransferError {
    /** Not a zip, or a zip without a Mailtice manifest. */
    NotATransferFile,

    /** Written by a newer Mailtice whose format this one cannot read. */
    TooNew,

    /** Could not read or write the file (disk full, removed stick...). */
    Failed,
}

class TransferException(val error: TransferError, cause: Throwable? = null) : Exception(error.name, cause)
