package co.abaye.mailtice.domain

private val OutlookConsumerDomains = listOf("outlook.", "hotmail.", "live.", "msn.com")

/**
 * The provider's own web mail for [account], opened in the browser; null where there is none to
 * open (a plain IMAP server). Gmail takes the address in the path, so the right one of several
 * signed-in accounts opens; Microsoft keeps personal and work mail on different sites.
 */
fun webMailUrl(account: Account): String? = when (account.kind) {
    ProviderKind.Gmail -> "https://mail.google.com/mail/u/${account.email}/#inbox"

    ProviderKind.Microsoft -> {
        val domain = account.email.substringAfter('@').lowercase()
        if (OutlookConsumerDomains.any { domain.startsWith(it) }) "https://outlook.live.com/mail/0/" else "https://outlook.office.com/mail/"
    }

    ProviderKind.Yahoo -> if (account.email.lowercase().endsWith("@aol.com")) "https://mail.aol.com/" else "https://mail.yahoo.com/"

    else -> null
}
