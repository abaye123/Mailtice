package co.abaye.mailtice.auth

import co.abaye.mailtice.domain.ProviderKind
import io.ktor.http.encodeURLParameter

/**
 * Everything that differs between the OAuth providers. Client ids come from the generated
 * [OAuthSecrets]; a provider with no client id is not configured and is not offered in the UI.
 */
enum class OAuthProvider(
    val kind: ProviderKind,
    val authUrl: String,
    val tokenUrl: String,
    val revokeUrl: String?,
    val scopes: List<String>,
    /** Host used for the desktop loopback redirect. Google wants the IP, Microsoft wants "localhost". */
    val loopbackHost: String,
) {
    Google(
        kind = ProviderKind.Gmail,
        authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        revokeUrl = "https://oauth2.googleapis.com/revoke",
        scopes = listOf("https://www.googleapis.com/auth/gmail.modify"),
        loopbackHost = "127.0.0.1",
    ),
    Microsoft(
        kind = ProviderKind.Microsoft,
        authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        revokeUrl = null,
        // SMTP.Send came with sending; accounts signed in before it must sign in again to send.
        scopes = listOf(
            "https://outlook.office.com/IMAP.AccessAsUser.All",
            "https://outlook.office.com/SMTP.Send",
            "offline_access",
            "openid",
            "email",
        ),
        loopbackHost = "localhost",
    ),
    Yahoo(
        kind = ProviderKind.Yahoo,
        authUrl = "https://api.login.yahoo.com/oauth2/request_auth",
        tokenUrl = "https://api.login.yahoo.com/oauth2/get_token",
        revokeUrl = null,
        scopes = listOf("openid", "mail-w"),
        loopbackHost = "127.0.0.1",
    ),
    ;

    val clientId: String
        get() = when (this) {
            Google -> OAuthSecrets.GOOGLE_CLIENT_ID
            Microsoft -> OAuthSecrets.MICROSOFT_CLIENT_ID
            Yahoo -> OAuthSecrets.YAHOO_CLIENT_ID
        }

    /** Microsoft is a public client with no secret. Google "Desktop" secrets are not secret by design. */
    val clientSecret: String?
        get() = when (this) {
            Google -> OAuthSecrets.GOOGLE_CLIENT_SECRET
            Microsoft -> null
            Yahoo -> OAuthSecrets.YAHOO_CLIENT_SECRET
        }?.takeIf { it.isNotBlank() }

    /**
     * Yahoo only accepts registered https redirects, so desktop sign-in goes through a small relay
     * page (see website/oauth-relay.html) that forwards the code to the loopback port carried in
     * `state`. Null = use the loopback directly.
     */
    val fixedRedirect: String?
        get() = if (this == Yahoo) OAuthSecrets.YAHOO_REDIRECT_URI.takeIf { it.isNotBlank() } else null

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && (this != Yahoo || fixedRedirect != null)

    fun authUrl(redirectUri: String, codeChallenge: String, state: String, loginHint: String?): String {
        val params = buildList {
            add("client_id" to clientId)
            add("redirect_uri" to redirectUri)
            add("response_type" to "code")
            add("scope" to scopes.joinToString(" "))
            add("code_challenge" to codeChallenge)
            add("code_challenge_method" to "S256")
            add("state" to state)
            if (this@OAuthProvider == Google) {
                add("access_type" to "offline")
                // Without consent Google skips the refresh token on a second sign-in of the same account.
                add("prompt" to "consent")
            }
            if (this@OAuthProvider == Microsoft) add("prompt" to "select_account")
            if (!loginHint.isNullOrBlank()) add("login_hint" to loginHint)
        }
        return authUrl + "?" + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
    }

    companion object {
        fun of(kind: ProviderKind): OAuthProvider? = entries.firstOrNull { it.kind == kind }
    }
}
