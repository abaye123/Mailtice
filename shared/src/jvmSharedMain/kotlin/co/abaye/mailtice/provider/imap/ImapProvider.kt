package co.abaye.mailtice.provider.imap

import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.auth.Credential
import co.abaye.mailtice.auth.ReauthRequiredException
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.MailBody
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.AttachmentFile
import co.abaye.mailtice.provider.FlagChange
import co.abaye.mailtice.provider.FolderState
import co.abaye.mailtice.provider.HtmlText
import co.abaye.mailtice.provider.ImapBackend
import co.abaye.mailtice.provider.ImapLoginException
import co.abaye.mailtice.provider.OlderQuery
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.provider.ThreadHeaders
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.FolderNotFoundException
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.mail.search.AndTerm
import jakarta.mail.search.BodyTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.FlagTerm
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.NotTerm
import jakarta.mail.search.RecipientStringTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SubjectTerm
import jakarta.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import java.io.IOException
import java.net.UnknownHostException
import java.util.Date
import java.util.Properties
import javax.net.ssl.SSLException
import jakarta.mail.Folder as JFolder

private const val TIMEOUT_MS = "20000"

/** Names that mean "archive" on servers without SPECIAL-USE. */
private val ARCHIVE_NAMES = setOf("archive", "archives", "ארכיון", "[gmail]/all mail")

/**
 * IMAP over Angus Mail, for the JVM and Android alike. One connected store per account, reused
 * between rounds and reopened when the server dropped it. Every blocking call runs on IO.
 *
 * Message ids are "<folder full name>/<uid>", so an id alone says where the message lives.
 */
class ImapProvider(private val auth: AuthManager) : ImapBackend {

    private val stores = mutableMapOf<String, IMAPStore>()
    private val mutex = Mutex()

    override suspend fun verify(server: ImapServer, username: String, password: String): Capabilities = io {
        val store = try {
            open(server, username, password, oauth = false)
        } catch (e: AuthenticationFailedException) {
            throw ImapLoginException(ImapLoginException.Reason.Credentials, e.message.orEmpty())
        } catch (e: MessagingException) {
            throw loginFailure(e)
        }
        try {
            capabilitiesOf(store, listFoldersOf(store))
        } finally {
            runCatching { store.close() }
        }
    }

    override suspend fun capabilities(account: Account): Capabilities = withStore(account) { store ->
        capabilitiesOf(store, listFoldersOf(store))
    }

    override suspend fun listFolders(account: Account): List<RemoteFolder> = withStore(account) { listFoldersOf(it) }

    override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch =
        withStore(account) { store ->
            val condstore = store.hasCapability("CONDSTORE")
            val newMessages = mutableListOf<RemoteMessage>()
            val flagChanges = mutableListOf<FlagChange>()
            val deleted = mutableSetOf<String>()
            val resets = mutableSetOf<String>()
            val states = mutableListOf<FolderState>()
            var initial = true

            for (folder in folders) {
                val f = store.getFolder(folder.id) as IMAPFolder
                try {
                    f.open(JFolder.READ_ONLY)
                } catch (e: FolderNotFoundException) {
                    println("IMAP: folder vanished on the server, skipping")
                    continue
                }
                try {
                    val uidValidity = f.uidValidity
                    val uidNext = f.uidNext
                    val modSeq = if (condstore) runCatching { f.highestModSeq }.getOrDefault(0L) else 0L
                    val reset = folder.uidValidity != uidValidity
                    if (folder.uidValidity != 0L) initial = false
                    if (reset) resets += folder.id
                    val unchanged = !reset && condstore && modSeq != 0L && modSeq == folder.highestModSeq && uidNext == folder.uidNext
                    if (!unchanged) {
                        val prefix = "${folder.id}/"
                        val known = if (reset) emptySet() else knownIds.filter { it.startsWith(prefix) }.toSet()
                        val messages = if (sinceMillis != null) {
                            f.search(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMillis)))
                        } else {
                            f.messages
                        }
                        f.fetch(
                            messages,
                            FetchProfile().apply {
                                add(FetchProfile.Item.ENVELOPE)
                                add(FetchProfile.Item.FLAGS)
                                add(FetchProfile.Item.SIZE)
                                add(UIDFolder.FetchProfileItem.UID)
                            },
                        )
                        val present = mutableSetOf<String>()
                        for (m in messages) {
                            val uid = f.getUID(m)
                            val id = prefix + uid
                            present += id
                            val unread = !m.isSet(Flags.Flag.SEEN)
                            val flagged = m.isSet(Flags.Flag.FLAGGED)
                            if (id in known) {
                                flagChanges += FlagChange(id, unread, flagged)
                            } else {
                                newMessages += m.toRemote(id, uid, folder.id, unread, flagged)
                            }
                        }
                        deleted += known - present
                    }
                    states += FolderState(folder.id, uidValidity, uidNext, modSeq)
                } finally {
                    runCatching { f.close(false) }
                }
            }
            SyncBatch(
                newMessages = newMessages,
                flagChanges = flagChanges,
                deletedIds = deleted,
                resetFolders = resets,
                folderStates = states,
                initial = initial,
            )
        }

    override suspend fun setRead(account: Account, message: MailMessage, folders: List<Folder>, read: Boolean) {
        withStore(account) { store ->
            withMessage(store, message) { _, m -> m.setFlag(Flags.Flag.SEEN, read) }
        }
    }

    override suspend fun archive(account: Account, message: MailMessage, folders: List<Folder>) {
        val target = folders.firstOrNull { it.role == FolderRole.Archive } ?: error("No archive folder")
        move(account, message, target.id)
    }

    override suspend fun trash(account: Account, message: MailMessage, folders: List<Folder>) {
        val target = folders.firstOrNull { it.role == FolderRole.Trash } ?: error("No trash folder")
        move(account, message, target.id)
    }

    private suspend fun move(account: Account, message: MailMessage, targetFolderId: String) {
        withStore(account) { store ->
            withMessage(store, message) { folder, m ->
                val destination = store.getFolder(targetFolderId)
                if (store.hasCapability("MOVE")) {
                    folder.moveMessages(arrayOf(m), destination)
                } else {
                    folder.copyMessages(arrayOf(m), destination)
                    m.setFlag(Flags.Flag.DELETED, true)
                    folder.expunge()
                }
            }
        }
    }

    /** Same walk as [parseBody], so indices line up with the stored attachment list. */
    override suspend fun fetchAttachments(account: Account, message: MailMessage, indices: Set<Int>?): List<AttachmentFile> =
        withStore(account) { store ->
            val out = mutableListOf<AttachmentFile>()
            withMessage(store, message, readOnly = true) { _, m ->
                var index = 0
                fun walk(part: Part) {
                    val fileName = part.fileName?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
                    when {
                        fileName != null || Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) -> {
                            if (indices == null || index in indices) {
                                out += AttachmentFile(index, fileName ?: "attachment", part.inputStream.use { it.readBytes() })
                            }
                            index++
                        }
                        part.isMimeType("multipart/*") -> {
                            val mp = part.content as Multipart
                            for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                        }
                        part.isMimeType("message/rfc822") -> (part.content as? Part)?.let(::walk)
                    }
                }
                walk(m)
            }
            out
        }

    /**
     * IMAP SEARCH per folder (the server filters by date, text and flags), then only envelopes and
     * flags for the newest [OlderQuery.limit] hits - no bodies.
     */
    override suspend fun olderMessages(account: Account, folders: List<Folder>, query: OlderQuery): List<RemoteMessage> =
        withStore(account) { store ->
            val targets = folders.ifEmpty { listOf(Folder(account.id, "INBOX", "INBOX", FolderRole.Inbox, sync = false, notify = false)) }
            targets.flatMap { f ->
                val folder = store.getFolder(f.id) as IMAPFolder
                folder.open(JFolder.READ_ONLY)
                try {
                    val s = query.search
                    val terms = buildList<SearchTerm> {
                        listOfNotNull(query.before, s.before).minOrNull()?.let { add(ReceivedDateTerm(ComparisonTerm.LT, Date(it))) }
                        s.after?.let { add(ReceivedDateTerm(ComparisonTerm.GE, Date(it))) }
                        s.words.forEach { w -> add(OrTerm(arrayOf(SubjectTerm(w), FromStringTerm(w), BodyTerm(w)))) }
                        s.excluded.forEach { w -> add(NotTerm(OrTerm(arrayOf(SubjectTerm(w), FromStringTerm(w), BodyTerm(w))))) }
                        if (s.from.isNotEmpty()) add(FromStringTerm(s.from))
                        if (s.to.isNotEmpty()) add(RecipientStringTerm(Message.RecipientType.TO, s.to))
                        if (s.subject.isNotEmpty()) add(SubjectTerm(s.subject))
                        if (query.unreadOnly || s.unread == true) add(FlagTerm(Flags(Flags.Flag.SEEN), false))
                        if (s.unread == false) add(FlagTerm(Flags(Flags.Flag.SEEN), true))
                        if (query.flaggedOnly || s.starred) add(FlagTerm(Flags(Flags.Flag.FLAGGED), true))
                    }
                    val hits = when (terms.size) {
                        0 -> folder.messages
                        1 -> folder.search(terms.first())
                        else -> folder.search(AndTerm(terms.toTypedArray()))
                    }
                    // Sequence order is arrival order: the last hits are the newest.
                    val page = hits.takeLast(query.limit).toTypedArray()
                    folder.fetch(
                        page,
                        FetchProfile().apply {
                            add(FetchProfile.Item.ENVELOPE)
                            add(FetchProfile.Item.FLAGS)
                            add(FetchProfile.Item.CONTENT_INFO)
                            add(UIDFolder.FetchProfileItem.UID)
                        },
                    )
                    page.map { m ->
                        val uid = folder.getUID(m)
                        val from = m.from?.firstOrNull() as? InternetAddress
                        RemoteMessage(
                            id = "${folder.fullName}/$uid",
                            threadId = "",
                            uid = uid,
                            fromName = from?.personal.orEmpty(),
                            fromAddress = from?.address.orEmpty(),
                            toLine = m.getRecipients(Message.RecipientType.TO)
                                ?.joinToString(", ") { (it as? InternetAddress)?.toUnicodeString() ?: it.toString() }.orEmpty(),
                            subject = m.subject.orEmpty(),
                            snippet = "",
                            receivedAt = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                            unread = !m.isSet(Flags.Flag.SEEN),
                            flagged = m.isSet(Flags.Flag.FLAGGED),
                            hasAttachments = query.attachmentsOnly || query.search.hasAttachment || m.isMimeType("multipart/mixed"),
                            sizeBytes = m.size.toLong().coerceAtLeast(0),
                            folderIds = setOf(folder.fullName),
                            body = null,
                        )
                    }.filter { !(query.attachmentsOnly || query.search.hasAttachment) || it.hasAttachments }
                } finally {
                    runCatching { folder.close(false) }
                }
            }.sortedByDescending { it.receivedAt }.take(query.limit)
        }

    override suspend fun rawMessage(account: Account, message: MailMessage): ByteArray = withStore(account) { store ->
        var bytes = ByteArray(0)
        withMessage(store, message, readOnly = true) { _, m ->
            bytes = java.io.ByteArrayOutputStream().also { m.writeTo(it) }.toByteArray()
        }
        bytes
    }

    override suspend fun threadHeaders(account: Account, message: MailMessage): ThreadHeaders = withStore(account) { store ->
        var headers = ThreadHeaders()
        withMessage(store, message, readOnly = true) { _, m ->
            headers = ThreadHeaders(
                messageId = m.getHeader("Message-ID")?.firstOrNull(),
                references = m.getHeader("References")?.firstOrNull(),
            )
        }
        headers
    }

    // ---- drafts -----------------------------------------------------------------------------

    /** APPEND to the Drafts folder with \Draft, then remove the previous version; the handle is "<folder>/<uid>". */
    override suspend fun saveDraft(account: Account, mail: OutgoingMail, folders: List<Folder>, previous: String?): String {
        val drafts = folders.firstOrNull { it.role == FolderRole.Drafts } ?: error("No drafts folder")
        val message = io { mimeMessage(account, mail).apply { setFlag(Flags.Flag.DRAFT, true); setFlag(Flags.Flag.SEEN, true) } }
        val handle = withStore(account) { store ->
            val folder = store.getFolder(drafts.id) as IMAPFolder
            val uid = folder.appendUIDMessages(arrayOf(message)).firstOrNull()?.uid
            if (uid != null) "${folder.fullName}/$uid" else "${folder.fullName}/"
        }
        if (previous != null && previous != handle) runCatching { deleteDraft(account, previous) }
        return handle
    }

    override suspend fun deleteDraft(account: Account, handle: String) {
        val folderName = handle.substringBeforeLast('/')
        val uid = handle.substringAfterLast('/').toLongOrNull() ?: return
        withStore(account) { store ->
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(JFolder.READ_WRITE)
            try {
                folder.getMessageByUID(uid)?.let { m ->
                    m.setFlag(Flags.Flag.DELETED, true)
                    folder.expunge(arrayOf(m))
                }
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    /** A draft's own id already says where it lives. */
    override suspend fun draftHandle(account: Account, message: MailMessage): String? = message.id

    // ---- sending ----------------------------------------------------------------------------

    /**
     * SMTP next to the IMAP server, with the same credential (XOAUTH2 token or password). Outlook and
     * Yahoo file the sent copy themselves; a generic IMAP server usually does not, so the copy is
     * appended to its Sent folder here.
     */
    override suspend fun send(account: Account, mail: OutgoingMail, folders: List<Folder>) {
        val message = io { mimeMessage(account, mail) }
        try {
            smtpSend(account, message, forceRefresh = false)
        } catch (e: AuthenticationFailedException) {
            if (!account.kind.oauth) throw ProviderException.SendNotAllowed(e.message.orEmpty())
            try {
                smtpSend(account, message, forceRefresh = true)
            } catch (again: AuthenticationFailedException) {
                throw ProviderException.SendNotAllowed(again.message.orEmpty())
            }
        } catch (e: MessagingException) {
            throw ProviderException.Transient(e.message.orEmpty())
        } catch (e: IOException) {
            throw ProviderException.Transient(e.message.orEmpty())
        }
        val sent = folders.firstOrNull { it.role == FolderRole.Sent }
        if (account.kind == ProviderKind.Imap && sent != null) {
            // Best effort: the mail is already on its way; a missing Sent copy is not a failure.
            runCatching {
                withStore(account) { store ->
                    message.setFlag(Flags.Flag.SEEN, true)
                    store.getFolder(sent.id).appendMessages(arrayOf(message))
                }
            }
        }
    }

    private suspend fun smtpSend(account: Account, message: MimeMessage, forceRefresh: Boolean) {
        val (secret, oauth) = when (val credential = auth.credential(account, forceRefresh)) {
            is Credential.Token -> credential.accessToken to true
            is Credential.Password -> credential.password to false
        }
        io {
            var last: MessagingException? = null
            for (target in smtpCandidates(account)) {
                try {
                    deliver(target, account.username, secret, oauth, message)
                    return@io
                } catch (e: AuthenticationFailedException) {
                    throw e
                } catch (e: MessagingException) {
                    // Wrong port or host guess: try the next candidate before giving up.
                    last = e
                }
            }
            throw last ?: MessagingException("No SMTP server to try")
        }
    }

    /** Fixed for the OAuth providers; for other IMAP hosts, "smtp." in place of "imap.", then the IMAP host. */
    private fun smtpCandidates(account: Account): List<ImapServer> = when (account.kind) {
        ProviderKind.Microsoft -> listOf(ImapServer("smtp.office365.com", 587, ImapSecurity.StartTls))
        ProviderKind.Yahoo -> listOf(ImapServer("smtp.mail.yahoo.com", 465, ImapSecurity.Tls))
        else -> {
            val imapHost = account.imap?.host.orEmpty()
            val guessed = if (imapHost.startsWith("imap.")) "smtp." + imapHost.removePrefix("imap.") else imapHost
            listOf(guessed, imapHost).distinct().filter { it.isNotBlank() }.flatMap { host ->
                listOf(ImapServer(host, 465, ImapSecurity.Tls), ImapServer(host, 587, ImapSecurity.StartTls))
            }
        }
    }

    private fun deliver(target: ImapServer, username: String, secret: String, oauth: Boolean, message: MimeMessage) {
        val protocol = if (target.security == ImapSecurity.Tls) "smtps" else "smtp"
        val p = "mail.$protocol"
        val props = Properties().apply {
            put("$p.host", target.host)
            put("$p.port", target.port.toString())
            put("$p.auth", "true")
            put("$p.connectiontimeout", TIMEOUT_MS)
            put("$p.timeout", TIMEOUT_MS)
            put("$p.writetimeout", TIMEOUT_MS)
            if (target.security == ImapSecurity.StartTls) {
                put("$p.starttls.enable", "true")
                put("$p.starttls.required", "true")
            }
            platformSocketFactory()?.let { put("$p.ssl.socketFactory", it) }
            if (oauth) {
                put("$p.auth.mechanisms", "XOAUTH2")
                put("$p.auth.login.disable", "true")
                put("$p.auth.plain.disable", "true")
            }
        }
        val transport = Session.getInstance(props).getTransport(protocol)
        try {
            transport.connect(target.host, target.port, username, secret)
            transport.sendMessage(message, message.allRecipients)
        } finally {
            runCatching { transport.close() }
        }
    }

    private fun mimeMessage(account: Account, mail: OutgoingMail): MimeMessage =
        MimeMessage(Session.getInstance(Properties())).apply {
            setFrom(InternetAddress(account.email))
            if (mail.to.isNotEmpty()) setRecipients(Message.RecipientType.TO, addresses(mail.to))
            if (mail.cc.isNotEmpty()) setRecipients(Message.RecipientType.CC, addresses(mail.cc))
            if (mail.bcc.isNotEmpty()) setRecipients(Message.RecipientType.BCC, addresses(mail.bcc))
            setSubject(mail.subject, "UTF-8")
            setContent(mimeContent(mail))
            sentDate = Date()
            mail.inReplyTo?.let { setHeader("In-Reply-To", it) }
            (mail.references ?: mail.inReplyTo)?.let { setHeader("References", it) }
            saveChanges()
        }

    /** Plain text; or plain + HTML (alternative); with files, all of that inside multipart/mixed. */
    private fun mimeContent(mail: OutgoingMail): MimeMultipart {
        val text = MimeBodyPart().apply { setText(mail.text, "UTF-8") }
        val body = if (mail.html == null) {
            MimeMultipart(text)
        } else {
            MimeMultipart("alternative", text, MimeBodyPart().apply { setContent(mail.html, "text/html; charset=UTF-8") })
        }
        if (mail.attachments.isEmpty()) return body
        val mixed = MimeMultipart("mixed")
        mixed.addBodyPart(MimeBodyPart().apply { setContent(body) })
        mail.attachments.forEach { file ->
            mixed.addBodyPart(
                MimeBodyPart().apply {
                    dataHandler = jakarta.activation.DataHandler(jakarta.mail.util.ByteArrayDataSource(file.bytes, file.mimeType))
                    fileName = MimeUtility.encodeText(file.name, "UTF-8", "B")
                    disposition = Part.ATTACHMENT
                },
            )
        }
        return mixed
    }

    /** Re-creates each address with its display name encoded as UTF-8, so Hebrew names survive SMTP. */
    private fun addresses(list: List<String>): Array<InternetAddress> = list.map { raw ->
        val parsed = InternetAddress(raw.trim())
        if (parsed.personal.isNullOrBlank()) parsed else InternetAddress(parsed.address, parsed.personal, "UTF-8")
    }.toTypedArray()

    override suspend fun fetchBody(account: Account, message: MailMessage): MailBody = withStore(account) { store ->
        var body: MailBody? = null
        withMessage(store, message, readOnly = true) { _, m -> body = parseBody(m) }
        body ?: MailBody("", "", emptyList())
    }

    override suspend fun close(account: Account) {
        mutex.withLock { stores.remove(account.id) }?.let { store -> io { runCatching { store.close() } } }
    }

    // ---- connection -------------------------------------------------------------------------

    private suspend fun <T> withStore(account: Account, block: (IMAPStore) -> T): T {
        val server = account.imap ?: error("IMAP account without a server")
        return try {
            val store = connected(account, server, forceRefresh = false)
            io { block(store) }
        } catch (e: AuthenticationFailedException) {
            // OAuth: the token may have expired between rounds. One retry with a fresh one.
            if (!account.kind.oauth) throw ReauthRequiredException(account.id)
            mutex.withLock { stores.remove(account.id) }
            try {
                val store = connected(account, server, forceRefresh = true)
                io { block(store) }
            } catch (again: AuthenticationFailedException) {
                throw ReauthRequiredException(account.id)
            }
        } catch (e: FolderNotFoundException) {
            throw ProviderException.NotFound(e.message.orEmpty())
        } catch (e: ReauthRequiredException) {
            throw e
        } catch (e: MessagingException) {
            mutex.withLock { stores.remove(account.id) }
            throw ProviderException.Transient(e.message.orEmpty())
        } catch (e: IOException) {
            mutex.withLock { stores.remove(account.id) }
            throw ProviderException.Transient(e.message.orEmpty())
        }
    }

    private suspend fun connected(account: Account, server: ImapServer, forceRefresh: Boolean): IMAPStore {
        mutex.withLock { stores[account.id] }?.takeIf { it.isConnected }?.let { return it }
        val credential = auth.credential(account, forceRefresh)
        val store = io {
            when (credential) {
                is Credential.Token -> open(server, account.username, credential.accessToken, oauth = true)
                is Credential.Password -> open(server, account.username, credential.password, oauth = false)
            }
        }
        mutex.withLock { stores[account.id] = store }
        return store
    }

    private fun open(server: ImapServer, username: String, secret: String, oauth: Boolean): IMAPStore {
        val protocol = if (server.security == ImapSecurity.Tls) "imaps" else "imap"
        val p = "mail.$protocol"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("$p.host", server.host)
            put("$p.port", server.port.toString())
            put("$p.connectiontimeout", TIMEOUT_MS)
            put("$p.timeout", TIMEOUT_MS)
            // Read bodies with BODY.PEEK so syncing never marks anything as read.
            put("$p.peek", "true")
            if (server.security == ImapSecurity.StartTls) {
                put("$p.starttls.enable", "true")
                put("$p.starttls.required", "true")
            }
            platformSocketFactory()?.let { put("$p.ssl.socketFactory", it) }
            if (oauth) {
                // Angus Mail's built-in XOAUTH2: the access token goes where the password would.
                put("$p.auth.mechanisms", "XOAUTH2")
                put("$p.auth.login.disable", "true")
                put("$p.auth.plain.disable", "true")
            }
        }
        val store = Session.getInstance(props).getStore(protocol) as IMAPStore
        store.connect(server.host, server.port, username, secret)
        return store
    }

    private fun loginFailure(e: MessagingException): ImapLoginException {
        val cause = generateSequence<Throwable>(e) { it.cause }.toList()
        val reason = when {
            cause.any { it is UnknownHostException } -> ImapLoginException.Reason.HostNotFound
            cause.any { it is SSLException } -> ImapLoginException.Reason.Tls
            else -> ImapLoginException.Reason.Other
        }
        return ImapLoginException(reason, e.message.orEmpty())
    }

    // ---- folders ----------------------------------------------------------------------------

    private fun listFoldersOf(store: IMAPStore): List<RemoteFolder> =
        store.defaultFolder.list("*")
            .filterIsInstance<IMAPFolder>()
            .filter { (it.type and JFolder.HOLDS_MESSAGES) != 0 && "\\Noselect" !in it.attributes }
            .map { RemoteFolder(it.fullName, it.fullName, roleOf(it)) }

    private fun roleOf(folder: IMAPFolder): FolderRole {
        val attrs = folder.attributes.map { it.lowercase() }.toSet()
        val name = folder.fullName.lowercase()
        return when {
            name == "inbox" -> FolderRole.Inbox
            "\\sent" in attrs -> FolderRole.Sent
            "\\archive" in attrs || name in ARCHIVE_NAMES -> FolderRole.Archive
            "\\drafts" in attrs -> FolderRole.Drafts
            "\\trash" in attrs -> FolderRole.Trash
            "\\junk" in attrs -> FolderRole.Spam
            else -> FolderRole.Other
        }
    }

    private fun capabilitiesOf(store: IMAPStore, folders: List<RemoteFolder>) = Capabilities(
        markRead = true,
        archive = folders.any { it.role == FolderRole.Archive },
        send = true,
        trash = folders.any { it.role == FolderRole.Trash },
        drafts = folders.any { it.role == FolderRole.Drafts },
        labels = false,
        openInWeb = false,
        incremental = store.hasCapability("CONDSTORE"),
        idle = store.hasCapability("IDLE"),
    )

    // ---- messages ---------------------------------------------------------------------------

    private fun withMessage(store: IMAPStore, message: MailMessage, readOnly: Boolean = false, block: (IMAPFolder, Message) -> Unit) {
        val folderName = message.id.substringBeforeLast('/')
        val folder = store.getFolder(folderName) as IMAPFolder
        folder.open(if (readOnly) JFolder.READ_ONLY else JFolder.READ_WRITE)
        try {
            val m = folder.getMessageByUID(message.uid) ?: throw ProviderException.NotFound("Message is gone")
            block(folder, m)
        } finally {
            runCatching { folder.close(false) }
        }
    }

    private fun Message.toRemote(id: String, uid: Long, folderId: String, unread: Boolean, flagged: Boolean): RemoteMessage {
        val from = (from?.firstOrNull() as? InternetAddress)
        val body = runCatching { parseBody(this) }.getOrElse { MailBody("", "", emptyList()) }
        return RemoteMessage(
            id = id,
            threadId = "",
            uid = uid,
            fromName = from?.personal.orEmpty(),
            fromAddress = from?.address.orEmpty(),
            toLine = getRecipients(Message.RecipientType.TO)?.joinToString(", ") { (it as? InternetAddress)?.toUnicodeString() ?: it.toString() }.orEmpty(),
            subject = subject.orEmpty(),
            snippet = HtmlText.snippet(body.text),
            receivedAt = (receivedDate ?: sentDate)?.time ?: 0L,
            unread = unread,
            flagged = flagged,
            hasAttachments = body.attachments.isNotEmpty(),
            sizeBytes = size.toLong().coerceAtLeast(0),
            folderIds = setOf(folderId),
            body = body,
        )
    }

    /** First text/plain, first text/html, and every named part as an attachment (not downloaded). */
    private fun parseBody(message: Part): MailBody {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<Attachment>()
        fun walk(part: Part) {
            val fileName = part.fileName?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
            when {
                fileName != null || Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) ->
                    attachments += Attachment(fileName ?: "attachment", part.size.toLong().coerceAtLeast(0))
                part.isMimeType("text/plain") && text == null -> text = part.content as? String
                part.isMimeType("text/html") && html == null -> html = part.content as? String
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as Multipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
                part.isMimeType("message/rfc822") -> (part.content as? Part)?.let(::walk)
            }
        }
        walk(message)
        val htmlBody = html.orEmpty()
        return MailBody(text = text ?: HtmlText.toText(htmlBody), html = htmlBody, attachments = attachments)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
