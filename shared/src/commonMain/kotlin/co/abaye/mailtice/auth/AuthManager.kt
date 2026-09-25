package co.abaye.mailtice.auth

import co.abaye.mailtice.data.SecretStore
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
)

/** How an account authenticates to its mail server. */
sealed interface Credential {
    data class Token(val accessToken: String) : Credential
    data class Password(val password: String) : Credential
}

/** Credentials rejected (invalid_grant, bad password). Only the user can fix it. */
class ReauthRequiredException(val accountId: String) : Exception("Re-authorization required")

private const val EXPIRY_MARGIN_MS = 60_000L
private const val CLIENT_WEB = "web"
private const val CLIENT_NATIVE = "native"

/**
 * Owns every credential: OAuth code exchange, access-token cache and refresh, revoke, IMAP
 * passwords. Nothing secret goes to the database - refresh tokens and passwords live in [SecretStore]
 * under the account id.
 */
class AuthManager(
    private val http: HttpClient,
    private val secrets: SecretStore,
) {
    private data class Cached(val token: String, val expiresAt: Long)

    private val cache = mutableMapOf<String, Cached>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchange(provider: OAuthProvider, code: AuthCode): TokenResponse {
        val form = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code.code)
            code.codeVerifier?.let { append("code_verifier", it) }
            append("redirect_uri", code.redirectUri.orEmpty())
            appendClient(provider, code.webClient)
        }
        return tokenRequest(provider, form, accountId = null)
    }

    /**
     * The address behind an id_token (Microsoft / Yahoo). Google is identified through the Gmail
     * profile instead, since its scope set has no openid.
     */
    fun emailFromIdToken(idToken: String?): String? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        val padded = payload.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        val claims = runCatching { json.parseToJsonElement(padded.decodeBase64String()) as JsonObject }.getOrNull() ?: return null
        return listOf("email", "preferred_username")
            .firstNotNullOfOrNull { key -> claims[key]?.jsonPrimitive?.content?.takeIf { '@' in it } }
    }

    suspend fun rememberTokens(accountId: String, tokens: TokenResponse, webClient: Boolean) {
        tokens.refreshToken?.let { secrets.put(refreshKey(accountId), "${if (webClient) CLIENT_WEB else CLIENT_NATIVE}|$it") }
        mutex.withLock { cache[accountId] = Cached(tokens.accessToken, expiryOf(tokens)) }
    }

    fun rememberPassword(accountId: String, password: String) {
        secrets.put(passwordKey(accountId), password)
    }

    suspend fun credential(account: Account, forceRefresh: Boolean = false): Credential {
        if (!account.kind.oauth) {
            val password = secrets.get(passwordKey(account.id)) ?: throw ReauthRequiredException(account.id)
            return Credential.Password(password)
        }
        return Credential.Token(accessToken(account, forceRefresh))
    }

    suspend fun accessToken(account: Account, forceRefresh: Boolean = false): String = mutex.withLock {
        val cached = cache[account.id]
        if (!forceRefresh && cached != null && cached.expiresAt > Platform.now()) return@withLock cached.token
        val provider = OAuthProvider.of(account.kind) ?: error("Not an OAuth account")
        val stored = secrets.get(refreshKey(account.id)) ?: throw ReauthRequiredException(account.id)
        val webClient = stored.substringBefore('|') == CLIENT_WEB
        val refresh = stored.substringAfter('|')
        val form = Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refresh)
            appendClient(provider, webClient)
        }
        val tokens = tokenRequest(provider, form, account.id)
        // Microsoft and Yahoo rotate refresh tokens; keep the newest.
        tokens.refreshToken?.let { secrets.put(refreshKey(account.id), "${stored.substringBefore('|')}|$it") }
        cache[account.id] = Cached(tokens.accessToken, expiryOf(tokens))
        tokens.accessToken
    }

    /** Best effort revoke at the provider, then forget locally either way. */
    suspend fun revoke(account: Account) {
        val provider = OAuthProvider.of(account.kind)
        val refresh = secrets.get(refreshKey(account.id))?.substringAfter('|')
        if (provider?.revokeUrl != null && refresh != null) {
            runCatching { http.submitForm(provider.revokeUrl, Parameters.build { append("token", refresh) }) }
        }
        forget(account.id)
    }

    suspend fun forget(accountId: String) {
        secrets.remove(refreshKey(accountId))
        secrets.remove(passwordKey(accountId))
        mutex.withLock { cache.remove(accountId) }
    }

    private fun io.ktor.http.ParametersBuilder.appendClient(provider: OAuthProvider, webClient: Boolean) {
        if (webClient) {
            append("client_id", OAuthSecrets.GOOGLE_WEB_CLIENT_ID)
            append("client_secret", OAuthSecrets.GOOGLE_WEB_CLIENT_SECRET)
        } else {
            append("client_id", provider.clientId)
            provider.clientSecret?.let { append("client_secret", it) }
        }
    }

    private suspend fun tokenRequest(provider: OAuthProvider, form: Parameters, accountId: String?): TokenResponse {
        val response = http.submitForm(provider.tokenUrl, form)
        if (response.status.isSuccess()) return response.body()
        val body = response.bodyAsText()
        if (accountId != null && ("invalid_grant" in body || response.status.value == 401)) throw ReauthRequiredException(accountId)
        throw ProviderException.of(response.status.value, body)
    }

    private fun expiryOf(tokens: TokenResponse): Long = Platform.now() + tokens.expiresIn * 1000 - EXPIRY_MARGIN_MS

    private fun refreshKey(accountId: String) = "refresh:$accountId"

    private fun passwordKey(accountId: String) = "password:$accountId"
}
