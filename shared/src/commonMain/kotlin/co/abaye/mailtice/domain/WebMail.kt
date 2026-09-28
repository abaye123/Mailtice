package co.abaye.mailtice.domain

private val OutlookConsumerDomains = listOf("outlook.", "hotmail.", "live.", "msn.com")

/**
 * The provider's own web mail for [account], opened in the browser; null where there is none to
 * open (a plain IMAP server). Microsoft keeps personal and work mail on different sites.
 */
fun webMailUrl(account: Account): String? = when (account.kind) {
    ProviderKind.Gmail -> gmailUrl(account.email, "inbox")

    ProviderKind.Microsoft -> {
        val domain = account.email.substringAfter('@').lowercase()
        if (OutlookConsumerDomains.any { domain.startsWith(it) }) "https://outlook.live.com/mail/0/" else "https://outlook.office.com/mail/"
    }

    ProviderKind.Yahoo -> if (account.email.lowercase().endsWith("@aol.com")) "https://mail.aol.com/" else "https://mail.yahoo.com/"

    else -> null
}

/**
 * Gmail for [email] at [view] ("inbox", "all/<message id>"). Gmail no longer takes an address in the
 * path (/mail/u/<address>/ shows an error page), only the account's index among those signed in;
 * `authuser` takes the address and redirects to the right index, or to Google's account chooser
 * when that account is not signed in to this browser profile.
 */
fun gmailUrl(email: String, view: String): String = "https://mail.google.com/mail/?authuser=${queryEscape(email)}#$view"

/** Percent-encodes what a query value cannot carry as is: "+" in an address would read as a space. */
private fun queryEscape(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val c = byte.toInt().toChar()
        val plain = (c.code < 128 && c.isLetterOrDigit()) || c in "-._~@"
        if (plain) {
            append(c)
        } else {
            append("%" + (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}
