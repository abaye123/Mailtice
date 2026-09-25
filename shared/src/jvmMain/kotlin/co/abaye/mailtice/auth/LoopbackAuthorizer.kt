package co.abaye.mailtice.auth

import co.abaye.mailtice.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val SIGN_IN_TIMEOUT_MS = 5 * 60_000L

/**
 * RFC 8252 loopback flow for every provider: a one-shot HTTP server on 127.0.0.1, PKCE, state check.
 * Yahoo only accepts https redirects, so there the redirect is a relay page (website/oauth-relay.html)
 * and the loopback port travels inside `state` ("<port>.<random>") for the relay to forward to.
 */
class LoopbackAuthorizer : Authorizer {
    private val random = SecureRandom()

    override suspend fun authorize(provider: OAuthProvider, loginHint: String?): AuthCode = withContext(Dispatchers.IO) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val redirect = provider.fixedRedirect ?: "http://${provider.loopbackHost}:$port"
            val nonce = token(24)
            val state = if (provider.fixedRedirect != null) "$port.$nonce" else nonce
            val verifier = token(64)
            val challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            Platform.openUrl(provider.authUrl(redirect, challenge, state, loginHint))

            server.soTimeout = 1000
            val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
            while (true) {
                currentCoroutineContext().ensureActive()
                if (System.currentTimeMillis() > deadline) throw AuthCancelledException("Timed out")
                val socket = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    continue
                }
                socket.use { s ->
                    val requestLine = s.getInputStream().bufferedReader().readLine().orEmpty()
                    val query = requestLine.substringAfter(' ').substringBefore(' ').substringAfter('?', "")
                    val params = query.split('&').filter { '=' in it }.associate {
                        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
                    }
                    // Browsers also ask for /favicon.ico; answer and keep waiting.
                    if (params.isEmpty()) {
                        s.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        return@use
                    }
                    val ok = params["state"] == state && params["code"] != null
                    s.getOutputStream().write(page(ok).toByteArray(Charsets.UTF_8))
                    s.getOutputStream().flush()
                    if (params["error"] != null) throw AuthCancelledException(params["error"].orEmpty())
                    if (!ok) throw AuthCancelledException("State mismatch")
                    return@withContext AuthCode(params.getValue("code"), verifier, redirect)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    private fun token(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    private fun page(ok: Boolean): String {
        val (he, en) = if (ok) {
            "ההתחברות הושלמה. אפשר לסגור את החלון ולחזור ל-Mailtice." to "Signed in. You can close this tab and return to Mailtice."
        } else {
            "ההתחברות לא הושלמה. חזור ל-Mailtice ונסה שוב." to "Sign-in did not complete. Return to Mailtice and try again."
        }
        val body = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Mailtice</title></head>" +
            "<body style=\"font-family:system-ui;text-align:center;padding:48px\"><p dir=\"rtl\">$he</p><p>$en</p></body></html>"
        val bytes = body.toByteArray(Charsets.UTF_8).size
        return "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: $bytes\r\nConnection: close\r\n\r\n$body"
    }
}
