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

    override fun browserProfiles(): List<BrowserProfile> = BrowserProfiles.list()

    override suspend fun authorize(provider: OAuthProvider, loginHint: String?, profile: BrowserProfile?): AuthCode =
        withContext(Dispatchers.IO) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val port = server.localPort
                val redirect = provider.fixedRedirect ?: "http://${provider.loopbackHost}:$port"
                val nonce = token(24)
                val state = if (provider.fixedRedirect != null) "$port.$nonce" else nonce
                val verifier = token(64)
                val challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
                val url = provider.authUrl(redirect, challenge, state, loginHint)
                // The chosen profile when there is one (and its browser starts); the default browser otherwise.
                if (profile == null || !BrowserProfiles.open(profile, url)) Platform.openUrl(url)

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

    /**
     * The page the browser lands on. On success it counts down two seconds and tries to close the
     * tab; browsers only let a page close a tab a script opened, so when that is refused the page
     * says the tab can be closed. Mailtice comes to the front on its own either way.
     */
    private fun page(ok: Boolean): String {
        val (he, en) = if (ok) {
            "ההתחברות הושלמה. חוזרים ל-Mailtice…" to "Signed in. Returning to Mailtice…"
        } else {
            "ההתחברות לא הושלמה. חזור ל-Mailtice ונסה שוב." to "Sign-in did not complete. Return to Mailtice and try again."
        }
        val color = if (ok) "#2D53D0" else "#891F01"
        val mark = if (ok) "&#10003;" else "&#10005;"
        val script = if (!ok) {
            ""
        } else {
            "<script>var n=2,c=document.getElementById('c');var t=setInterval(function(){n--;if(n>0){c.textContent=n;return}" +
                "clearInterval(t);window.close();setTimeout(function(){document.getElementById('w').style.display='none';" +
                "document.getElementById('d').style.display='block'},300)},1000)</script>"
        }
        val countdown = if (!ok) {
            ""
        } else {
            "<p id=\"w\" class=\"m\"><span dir=\"rtl\">הכרטיסייה תיסגר בעוד <b id=\"c\">2</b> שניות</span>" +
                "<br>This tab closes in a moment</p>" +
                "<p id=\"d\" class=\"m\" style=\"display:none\"><span dir=\"rtl\">אפשר לסגור את הכרטיסייה</span><br>You can close this tab</p>"
        }
        val body = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Mailtice</title><style>" +
            "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#F3F2FD;" +
            "font-family:'Segoe UI',system-ui,sans-serif;color:#1A1B23}.card{background:#fff;border-radius:24px;padding:40px 48px;" +
            "text-align:center;box-shadow:0 8px 30px rgba(0,20,82,.12)}.i{width:64px;height:64px;border-radius:50%;margin:0 auto 16px;" +
            "background:$color;color:#fff;font-size:34px;line-height:64px}.m{color:#444654;font-size:14px}" +
            "@media(prefers-color-scheme:dark){body{background:#12131A;color:#E2E1EC}" +
            ".card{background:#1E1F27}.m{color:#C4C5D6}}</style></head>" +
            "<body><div class=\"card\"><div class=\"i\">$mark</div><p dir=\"rtl\"><b>$he</b></p><p>$en</p>$countdown</div>$script</body></html>"
        val bytes = body.toByteArray(Charsets.UTF_8).size
        return "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: $bytes\r\nConnection: close\r\n\r\n$body"
    }
}
