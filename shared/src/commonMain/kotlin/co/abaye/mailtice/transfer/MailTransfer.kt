package co.abaye.mailtice.transfer

import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.fileSize
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.gmail.gmailFolder
import co.abaye.mailtice.provider.gmail.toRemoteMessage
import co.abaye.mailtice.sync.OfflineFiles
import kotlinx.serialization.SerializationException

/** What an export or an import went through. [newAccounts] only counts on import. */
data class TransferSummary(val accounts: Int, val messages: Int, val files: Int, val newAccounts: Int = 0)

/**
 * Writes every stored message of [MailExporter.write]'s accounts, with its body where one is
 * stored and the attachments offline mode downloaded, into a transfer file ([TransferFormat]).
 * Only what this device holds goes in: with offline mode on, that is the whole mailbox.
 */
internal class MailExporter(
    private val repo: MailRepository,
    private val readFile: (String) -> ByteArray? = Platform::readBytes,
    private val sizeOf: (String) -> Long = ::fileSize,
) {
    fun write(sink: ArchiveSink, accounts: List<Account>, generator: String, now: Long, progress: (Int) -> Unit = {}): TransferSummary {
        val keys = accounts.mapIndexed { i, _ -> "a${i + 1}" }
        sink.put(TransferFormat.MANIFEST, encode(TransferManifest(createdAt = now, generator = generator, accounts = keys)))
        var messages = 0
        var files = 0
        accounts.forEachIndexed { i, account ->
            val key = keys[i]
            val info = TransferAccount(
                key = key,
                kind = account.kind.name,
                email = account.email,
                label = account.label,
                color = account.color.name,
                folders = repo.foldersNow(account.id).map { TransferFolder(it.id, it.name, it.role.name, it.color) },
                imapHost = account.imap?.host.orEmpty(),
                imapPort = account.imap?.port ?: 0,
                imapSecurity = account.imap?.security?.name.orEmpty(),
                username = account.username,
                messages = repo.messageCount(account.id).toInt(),
            )
            sink.put(TransferFormat.accountEntry(key), encode(info))
            var n = 0
            var chunk = 0
            var offset = 0L
            while (true) {
                val page = repo.messagePage(account.id, offset, TransferFormat.CHUNK.toLong())
                if (page.isEmpty()) break
                val withFiles = mutableListOf<Triple<Int, String, List<Int>>>()
                val lines = page.map { m ->
                    val body = repo.body(account.id, m.id)
                    val present = body?.attachments?.indices?.filter { sizeOf(OfflineFiles.attachment(account.id, m.id, it)) > 0 }.orEmpty()
                    val number = n++
                    if (present.isNotEmpty()) withFiles += Triple(number, m.id, present)
                    encodeLine(m.toTransfer(number, body, present))
                }
                sink.put(TransferFormat.chunkEntry(key, chunk++), lines.joinToString("\n").encodeToByteArray())
                // Read one file at a time: a page of messages may carry far more than memory holds.
                withFiles.forEach { (number, messageId, present) ->
                    present.forEach { index ->
                        readFile(OfflineFiles.attachment(account.id, messageId, index))?.let { bytes ->
                            sink.put(TransferFormat.fileEntry(key, number, index), bytes, compress = false)
                            files++
                        }
                    }
                }
                offset += page.size
                messages += page.size
                progress(messages)
            }
        }
        return TransferSummary(accounts.size, messages, files)
    }

    private fun MailMessage.toTransfer(n: Int, body: MailBody?, files: List<Int>) = TransferMessage(
        n = n,
        id = id,
        threadId = threadId,
        uid = uid,
        fromName = fromName,
        fromAddress = fromAddress,
        toLine = toLine,
        subject = subject,
        snippet = snippet,
        receivedAt = receivedAt,
        unread = unread,
        flagged = flagged,
        hasAttachments = hasAttachments,
        sizeBytes = sizeBytes,
        folderIds = folderIds,
        body = body?.let { b ->
            TransferBody(b.text, b.html, b.attachments.map { TransferAttachment(it.name, it.size, it.contentId, it.mimeType) }, b.dynamic)
        },
        files = files,
    )

    private fun encode(manifest: TransferManifest) =
        TransferFormat.json.encodeToString(TransferManifest.serializer(), manifest).encodeToByteArray()

    private fun encode(account: TransferAccount) =
        TransferFormat.json.encodeToString(TransferAccount.serializer(), account).encodeToByteArray()

    private fun encodeLine(message: TransferMessage) = TransferFormat.json.encodeToString(TransferMessage.serializer(), message)
}

/**
 * Reads a transfer file into the database. An account is matched by provider and address: one
 * connected here only gains the messages (and bodies) it did not have, since its own server knows
 * better; any other becomes - or stays - a read-only copy ([Account.fileOnly]) whose mail follows
 * the file, flags and folders included. Nothing imported is ever announced as new mail.
 */
internal class MailImporter(
    private val repo: MailRepository,
    private val newId: () -> String,
    /** The colour for a new account: the file's when it is a known one and free, else the next free one. */
    private val pickColor: (preferred: String) -> AccountColor,
    private val writeFile: (String, ByteArray) -> Unit = Platform::writeBytes,
) {
    private class Target(val account: Account, val overwrite: Boolean, val folderIds: MutableSet<String>) {
        /** Message number -> (id, attachments it has, files written so far). */
        val numbers = mutableMapOf<Int, Triple<String, Int, MutableSet<Int>>>()
        var archiveId: String? = null
    }

    fun read(source: ArchiveSource, progress: (Int) -> Unit = {}): TransferSummary {
        val first = source.next() ?: throw TransferException(TransferError.NotATransferFile)
        if (first.name != TransferFormat.MANIFEST) throw TransferException(TransferError.NotATransferFile)
        val manifest = decode(first) { TransferFormat.json.decodeFromString(TransferManifest.serializer(), it) }
        if (manifest.format != TransferFormat.FORMAT) throw TransferException(TransferError.NotATransferFile)
        if (manifest.version > TransferFormat.VERSION) throw TransferException(TransferError.TooNew)

        val targets = mutableMapOf<String, Target>()
        var messages = 0
        var files = 0
        var newAccounts = 0
        while (true) {
            val entry = source.next() ?: break
            val parts = entry.name.split('/')
            if (parts.size < 3 || parts[0] != "accounts") continue
            val key = parts[1]
            when {
                parts.size == 3 && parts[2] == "account.json" -> {
                    val info = decode(entry) { TransferFormat.json.decodeFromString(TransferAccount.serializer(), it) }
                    val (target, created) = resolve(info) ?: continue
                    if (created) newAccounts++
                    targets[key] = target
                }

                parts.size == 4 && parts[2] == "messages" -> {
                    val target = targets[key] ?: continue
                    val batch = entry.bytes.decodeToString().lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                        val record = runCatching { TransferFormat.json.decodeFromString(TransferMessage.serializer(), line) }.getOrNull()
                        record?.let { toRemote(target, it) }
                    }.toList()
                    repo.importMessages(target.account.id, batch, target.overwrite)
                    messages += batch.size
                    progress(messages)
                }

                parts.size == 5 && parts[2] == "files" -> {
                    val target = targets[key] ?: continue
                    val n = parts[3].toIntOrNull() ?: continue
                    val index = parts[4].toIntOrNull() ?: continue
                    val (id, expected, written) = target.numbers[n] ?: continue
                    if (index !in 0 until expected) continue
                    writeFile(OfflineFiles.attachment(target.account.id, id, index), entry.bytes)
                    written += index
                    files++
                }
            }
        }
        // Every file of a message is here: offline mode need not download them again once connected.
        targets.values.forEach { target ->
            target.numbers.values.filter { (_, expected, written) -> expected > 0 && written.size == expected }.forEach { (id, _, _) ->
                writeFile(OfflineFiles.done(target.account.id, id), ByteArray(0))
            }
        }
        return TransferSummary(targets.size, messages, files, newAccounts)
    }

    /** The account [info] goes into, and whether it was made just now; null for a provider this version does not know. */
    private fun resolve(info: TransferAccount): Pair<Target, Boolean>? {
        val kind = ProviderKind.entries.firstOrNull { it.name == info.kind } ?: return null
        if (info.email.isBlank()) return null
        val remoteFolders = if (info.gmailLabels.isNotEmpty()) {
            info.gmailLabels.mapNotNull(::gmailFolder)
        } else {
            info.folders.map { f ->
                RemoteFolder(f.id, f.name, FolderRole.entries.firstOrNull { it.name == f.role } ?: FolderRole.Other, f.color)
            }
        }
        val existing = repo.accountsNow().firstOrNull { it.kind == kind && it.email.equals(info.email, ignoreCase = true) }
        val account = existing ?: Account(
            id = newId(),
            kind = kind,
            email = info.email.trim(),
            label = info.label,
            color = pickColor(info.color),
            // Kept whatever its age: on a computer without internet this is the only copy.
            retentionDays = 0,
            // Read and star here, nothing that needs the server.
            capabilities = Capabilities(markRead = true, labels = kind == ProviderKind.Gmail),
            imap = imapOf(kind, info),
            username = info.username.ifBlank { info.email.trim() },
            fileOnly = true,
        ).also(repo::addAccount)
        // A connected account takes its folders from its server; a copy takes them from the file.
        if (account.fileOnly) repo.addFolders(account.id, remoteFolders)
        val folderIds = repo.foldersNow(account.id).map { it.id }.toMutableSet()
        val target = Target(account, overwrite = account.fileOnly, folderIds = folderIds)
        target.archiveId = repo.foldersNow(account.id).firstOrNull { it.role == FolderRole.Archive }?.id
        return target to (existing == null)
    }

    private fun imapOf(kind: ProviderKind, info: TransferAccount): ImapServer? = when (kind) {
        ProviderKind.Gmail -> null

        ProviderKind.Microsoft -> ImapServer("outlook.office365.com")

        ProviderKind.Yahoo -> ImapServer("imap.mail.yahoo.com")

        ProviderKind.Imap -> info.imapHost.takeIf { it.isNotBlank() }?.let { host ->
            ImapServer(
                host,
                info.imapPort.takeIf { it > 0 } ?: 993,
                ImapSecurity.entries.firstOrNull { it.name == info.imapSecurity } ?: ImapSecurity.Tls,
            )
        }
    }

    /**
     * The stored form of [record]; null when it has no id, or (on a connected account) is in no
     * folder the account has. On a copy, mail in no known folder - Gmail's archived mail, which no
     * label holds - goes to an Archive folder made for it, so it can still be found.
     */
    private fun toRemote(target: Target, record: TransferMessage): RemoteMessage? {
        val gmail = record.gmail
        val remote = if (gmail != null) {
            gmail.toRemoteMessage(gmail.labelIds.toSet(), withBody = gmail.payload != null)
        } else {
            RemoteMessage(
                id = record.id,
                threadId = record.threadId,
                uid = record.uid,
                fromName = record.fromName,
                fromAddress = record.fromAddress,
                toLine = record.toLine,
                subject = record.subject,
                snippet = record.snippet,
                receivedAt = record.receivedAt,
                unread = record.unread,
                flagged = record.flagged,
                hasAttachments = record.hasAttachments,
                sizeBytes = record.sizeBytes,
                folderIds = record.folderIds.toSet(),
                body = record.body?.let { b ->
                    MailBody(b.text, b.html, b.attachments.map { Attachment(it.name, it.size, it.contentId, it.mimeType) }, b.dynamic)
                },
            )
        }
        if (remote.id.isBlank()) return null
        var folders = remote.folderIds.filter { it in target.folderIds }.toSet()
        if (folders.isEmpty()) {
            if (!target.overwrite) return null
            folders = setOf(archiveFolder(target))
        }
        val attachments = remote.body?.attachments?.size ?: 0
        if (record.files.isNotEmpty() && attachments > 0) target.numbers[record.n] = Triple(remote.id, attachments, mutableSetOf())
        return remote.copy(folderIds = folders)
    }

    private fun archiveFolder(target: Target): String = target.archiveId ?: ARCHIVE_ID.also { id ->
        repo.addFolders(target.account.id, listOf(RemoteFolder(id, "Archive", FolderRole.Archive)))
        target.folderIds += id
        target.archiveId = id
    }

    private fun <T> decode(entry: ArchiveEntry, parse: (String) -> T): T = try {
        parse(entry.bytes.decodeToString())
    } catch (e: SerializationException) {
        throw TransferException(TransferError.NotATransferFile, e)
    } catch (e: IllegalArgumentException) {
        throw TransferException(TransferError.NotATransferFile, e)
    }

    companion object {
        /** The folder made for imported mail no folder holds; a real Archive folder is used when there is one. */
        const val ARCHIVE_ID = "mailtice:archive"
    }
}
