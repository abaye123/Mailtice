package co.abaye.mailtice.provider

import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.ImapServer

/** IMAP plus the one thing only IMAP needs: checking a server/password before the account is saved. */
interface ImapBackend : MailProvider {
    /** Connects and logs in once. Throws [ImapLoginException] with a reason the UI can show. */
    suspend fun verify(server: ImapServer, username: String, password: String): Capabilities
}

class ImapLoginException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { HostNotFound, Tls, Credentials, Other }
}

/** Implemented once for the JVM and Android in jvmSharedMain (Angus Mail). */
expect fun createImapBackend(auth: AuthManager): ImapBackend
