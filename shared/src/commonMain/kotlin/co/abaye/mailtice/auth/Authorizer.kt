package co.abaye.mailtice.auth

/**
 * What the interactive step hands back.
 * [codeVerifier] / [redirectUri] are null for Google on Android, where Play services returns a
 * server auth code that is exchanged with the Web client instead (see [webClient]).
 */
data class AuthCode(
    val code: String,
    val codeVerifier: String?,
    val redirectUri: String?,
    val webClient: Boolean = false,
)

/** The user closed the browser, denied consent, or the flow timed out. Not an error to report. */
class AuthCancelledException(message: String) : Exception(message)

/** Runs the interactive part of OAuth. Desktop: loopback server. Android: Play services / Custom Tabs. */
interface Authorizer {
    fun supports(provider: OAuthProvider): Boolean = true

    /** Browser profiles the sign-in page can open in; empty where the choice does not exist (Android). */
    fun browserProfiles(): List<BrowserProfile> = emptyList()

    /** [profile] null = the default browser. */
    suspend fun authorize(provider: OAuthProvider, loginHint: String? = null, profile: BrowserProfile? = null): AuthCode
}
