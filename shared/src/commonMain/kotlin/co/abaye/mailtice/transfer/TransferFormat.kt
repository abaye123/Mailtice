package co.abaye.mailtice.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The transfer file: mail carried on a USB stick to a computer without internet. A zip archive,
 * written by the app (Settings, "Export to a file") and read by its "Import from a file". With a
 * password the archive is wrapped by TransferCrypto. Entries, in this order:
 *
 * - `manifest.json` - [TransferManifest], always first.
 * - `accounts/<key>/account.json` - [TransferAccount], before anything else of that account.
 * - `accounts/<key>/messages/<chunk>.jsonl` - one [TransferMessage] per line.
 * - `accounts/<key>/files/<n>/<index>` - the bytes of attachment `index` of message `n`, after the
 *   chunk that holds message `n`.
 *
 * The order lets the importer read the archive as a stream, one entry at a time, however large it
 * is.
 */
internal object TransferFormat {
    const val FORMAT = "mailtice-transfer"

    /** Bumped only for a change an older importer would misread; new optional fields need no bump. */
    const val VERSION = 1
    const val EXTENSION = "mailtice"
    const val MANIFEST = "manifest.json"

    /** Messages per `.jsonl` entry: small enough to read one at a time, big enough to stay few. */
    const val CHUNK = 200

    /** A chunk of long messages ends early at this many characters, to stay small enough to read. */
    const val CHUNK_CHARS = 32 shl 20

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
    /** "Mailtice 1.7.0 (Windows)": shown nowhere, kept for bug reports. */
    val generator: String = "",
    val accounts: List<String> = emptyList(),
    /** The oldest date exported, in epoch milliseconds; 0 = everything the device stored. */
    val since: Long = 0,
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
    val folders: List<TransferFolder> = emptyList(),
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
 * One message. [n] numbers it within its account; its attachment files are named by it. [files]
 * lists the attachment indexes the archive carries.
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
    fun put(name: String, bytes: ByteArray)

    /** The file at [path] as an entry, streamed: it may be far larger than memory. */
    fun putFile(name: String, path: String)
}

/** One entry of an archive. Good until the next [ArchiveSource.next]. */
interface ArchiveEntry {
    val name: String

    /** The whole entry, for the ones that are text; null when it is too long to be one of ours. */
    fun readBytes(): ByteArray?

    /** Streams the entry to [path]; false when it is too long and nothing was kept. */
    fun saveTo(path: String): Boolean
}

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

    /** Encrypted, and the password given does not open it. */
    WrongPassword,

    /** Could not read or write the file (disk full, removed stick...). */
    Failed,
}

class TransferException(val error: TransferError, cause: Throwable? = null) : Exception(error.name, cause)
