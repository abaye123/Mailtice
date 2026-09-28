package co.abaye.mailtice.auth

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import co.abaye.mailtice.platform.androidContext
import co.abaye.mailtice.platform.currentActivity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val REDIRECT = "co.abaye.mailtice://oauth"
private const val TIMEOUT_MS = 5 * 60_000L

/**
 * Google: the Authorization API in Play services (Google allows neither loopback nor custom schemes
 * for Android clients). It returns a server auth code, exchanged with the Web client credentials.
 * Microsoft: Custom Tabs + PKCE + the co.abaye.mailtice://oauth redirect.
 * Yahoo: needs an https redirect verified as an App Link - not wired yet, so not offered.
 */
class AndroidAuthorizer : Authorizer {

    override fun supports(provider: OAuthProvider): Boolean = when (provider) {
        OAuthProvider.Google -> OAuthSecrets.GOOGLE_WEB_CLIENT_ID.isNotBlank()
        OAuthProvider.Microsoft -> true
        OAuthProvider.Yahoo -> false
    }

    override suspend fun authorize(provider: OAuthProvider, loginHint: String?, profile: BrowserProfile?): AuthCode = when (provider) {
        OAuthProvider.Google -> google(loginHint)
        else -> browser(provider, loginHint)
    }

    private suspend fun google(loginHint: String?): AuthCode {
        val activity = currentActivity() ?: throw AuthCancelledException("No foreground activity")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(OAuthProvider.Google.scopes.map { Scope(it) })
            .requestOfflineAccess(OAuthSecrets.GOOGLE_WEB_CLIENT_ID, true)
            .apply { loginHint?.let { setAccount(android.accounts.Account(it, "com.google")) } }
            // TODO(multi-account): without setAccount Play services may reuse the last account. Pick
            //  the account first with Credential Manager (GetGoogleIdOption) to add a second one.
            .build()
        val client = Identity.getAuthorizationClient(activity)
        var result = client.authorize(request).await()
        if (result.hasResolution()) {
            val sender = result.pendingIntent?.intentSender ?: throw AuthCancelledException("No resolution")
            val deferred = CompletableDeferred<Intent?>()
            AuthBridge.pendingResolution = deferred
            activity.startActivity(
                Intent(activity, AuthResolutionActivity::class.java).putExtra(AuthResolutionActivity.EXTRA_SENDER, sender),
            )
            val data = deferred.await() ?: throw AuthCancelledException("Consent declined")
            result = client.getAuthorizationResultFromIntent(data)
        }
        val code = result.serverAuthCode ?: throw AuthCancelledException("No server auth code")
        return AuthCode(code = code, codeVerifier = null, redirectUri = null, webClient = true)
    }

    private suspend fun browser(provider: OAuthProvider, loginHint: String?): AuthCode {
        val random = SecureRandom()
        fun token(n: Int) = ByteArray(n).also(random::nextBytes).let {
            Base64.encodeToString(
                it,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        }
        val state = token(24)
        val verifier = token(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val deferred = CompletableDeferred<Uri>()
        AuthBridge.pendingRedirect = deferred
        val url = provider.authUrl(REDIRECT, challenge, state, loginHint)
        val launcher = currentActivity() ?: androidContext()
        CustomTabsIntent.Builder().build().apply {
            if (launcher !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.launchUrl(launcher, Uri.parse(url))
        val uri = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: throw AuthCancelledException("Timed out")
        uri.getQueryParameter("error")?.let { throw AuthCancelledException(it) }
        if (uri.getQueryParameter("state") != state) throw AuthCancelledException("State mismatch")
        val code = uri.getQueryParameter("code") ?: throw AuthCancelledException("No code")
        return AuthCode(code = code, codeVerifier = verifier, redirectUri = REDIRECT)
    }
}

private suspend fun Task<AuthorizationResult>.await(): AuthorizationResult = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
