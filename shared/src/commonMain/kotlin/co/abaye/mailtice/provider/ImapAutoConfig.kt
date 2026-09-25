package co.abaye.mailtice.provider

import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.ImapServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Guesses the IMAP server for an address: built-in table, then Thunderbird's ISPDB, then
 * imap.<domain>:993. The user can always correct the result before it is tested.
 */
class ImapAutoConfig(private val http: HttpClient) {

    suspend fun lookup(email: String): ImapServer {
        val domain = email.substringAfter('@', "").lowercase().trim()
        KNOWN[domain]?.let { return it }
        ispdb(domain)?.let { return it }
        return ImapServer("imap.$domain", 993, ImapSecurity.Tls)
    }

    private suspend fun ispdb(domain: String): ImapServer? = runCatching {
        val response = http.get("https://autoconfig.thunderbird.net/v1.1/$domain")
        if (!response.status.isSuccess()) return null
        parseAutoconfig(response.bodyAsText())
    }.getOrNull()

    companion object {
        val KNOWN: Map<String, ImapServer> = mapOf(
            "outlook.com" to ImapServer("outlook.office365.com"),
            "hotmail.com" to ImapServer("outlook.office365.com"),
            "live.com" to ImapServer("outlook.office365.com"),
            "yahoo.com" to ImapServer("imap.mail.yahoo.com"),
            "aol.com" to ImapServer("imap.aol.com"),
            "icloud.com" to ImapServer("imap.mail.me.com"),
            "me.com" to ImapServer("imap.mail.me.com"),
            "gmx.com" to ImapServer("imap.gmx.com"),
            "gmx.net" to ImapServer("imap.gmx.net"),
            "zoho.com" to ImapServer("imap.zoho.com"),
            "fastmail.com" to ImapServer("imap.fastmail.com"),
            "walla.co.il" to ImapServer("imap.walla.co.il"),
            "012.net.il" to ImapServer("imap.012.net.il"),
        )

        private val incoming = Regex("(?s)<incomingServer\\s+type=\"imap\">(.*?)</incomingServer>")
        private fun tag(block: String, name: String) = Regex("<$name>([^<]+)</$name>").find(block)?.groupValues?.get(1)?.trim()

        /** First IMAP server in an ISPDB / autoconfig document. */
        fun parseAutoconfig(xml: String): ImapServer? {
            val block = incoming.find(xml)?.groupValues?.get(1) ?: return null
            val host = tag(block, "hostname") ?: return null
            val port = tag(block, "port")?.toIntOrNull() ?: 993
            val security = if (tag(block, "socketType").equals("STARTTLS", ignoreCase = true)) ImapSecurity.StartTls else ImapSecurity.Tls
            return ImapServer(host, port, security)
        }
    }
}
