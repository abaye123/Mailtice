package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import dev.nucleusframework.webview.request.RequestInterceptor
import dev.nucleusframework.webview.request.WebRequest
import dev.nucleusframework.webview.request.WebRequestInterceptResult
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

private const val MAX_INLINE_DOCUMENT = 1_500_000

/**
 * The page's height in CSS pixels (dp). Run by the app, not by the page: page scripts stay off.
 * The body has no height of its own, so it measures the content even while the view is taller.
 */
private const val MEASURE_SCRIPT = "Math.max(document.body.scrollHeight, Math.ceil(document.body.getBoundingClientRect().height))"

/** Writes [document] to the reader's page file and returns a file: URL that changes with it (so it reloads). */
private fun writePage(document: String): String {
    val dir = joinPath(Platform.appDir(), "webview")
    Platform.mkdir(dir)
    val path = joinPath(dir, "message.html")
    Platform.writeText(path, document)
    return fileUrl(path) + "?v=" + document.hashCode()
}

/** A file: URL for a local [path], on Windows as well as elsewhere. */
fun fileUrl(path: String): String {
    val slashed = path.replace('\\', '/').replace(" ", "%20").replace("#", "%23")
    return (if (slashed.startsWith("/")) "file://" else "file:///") + slashed
}

/**
 * A local file in the webview: a PDF in the engine's own viewer, or the player page around an
 * audio or video file. No mail content runs here, but links still leave for the browser.
 */
@Composable
internal fun FileBody(url: String, modifier: Modifier = Modifier) {
    if (!holdsWebViewSlot()) {
        Box(modifier)
        return
    }
    val state = rememberWebViewState(url)
    remember(state) { configure(state) }
    WebView(state, modifier, rememberWebViewNavigator(requestInterceptor = remember { externalLinks {} }))
}

private fun configure(state: WebViewState, javaScript: Boolean = true) {
    state.webSettings.apply {
        isJavaScriptEnabled = javaScript
        backgroundColor = Color.White
        desktopWebSettings.incognito = true
        desktopWebSettings.dataDirectory = joinPath(Platform.appDir(), "webview")
        desktopWebSettings.enableNavigationGestures = false
    }
}

/** Lets the page itself (about:, data:, file:) load, and hands http(s) and mailto links to [open]. */
private fun externalLinks(open: (String) -> Unit) = object : RequestInterceptor {
    override fun onInterceptUrlRequest(request: WebRequest, navigator: WebViewNavigator): WebRequestInterceptResult {
        val url = request.url
        val external = listOf("http://", "https://", "mailto:").any { url.startsWith(it, ignoreCase = true) }
        if (!external) return WebRequestInterceptResult.Allow
        open(url)
        return WebRequestInterceptResult.Reject
    }
}

/**
 * A window can hold one native webview at a time: on Windows each takes the window's single
 * DirectComposition target, and a second one fails to be created while the first is alive. When
 * one page replaces another (the viewer opening over a message, a screen change) the new one is
 * composed before the old one is disposed, so every webview waits here for its turn: it takes the
 * slot once free and gives it back when it leaves.
 */
private object WebViewSlot {
    var owner: Any? by mutableStateOf(null)
}

/** True once this call site holds the window's webview slot; until then it shows nothing. */
@Composable
private fun holdsWebViewSlot(): Boolean {
    val token = remember { Any() }
    SideEffect { if (WebViewSlot.owner == null) WebViewSlot.owner = token }
    // Declared before the webview, so it is let go after the webview is destroyed.
    DisposableEffect(token) { onDispose { if (WebViewSlot.owner === token) WebViewSlot.owner = null } }
    return WebViewSlot.owner === token
}

/** How the reader shows mail; provided at the root from the settings. */
@Immutable
data class ReaderPrefs(val loadRemoteImages: Boolean = true)

val LocalReaderPrefs = staticCompositionLocalOf { ReaderPrefs() }

/**
 * An HTML message in the platform's own browser engine (WebView2 on Windows, WebKit elsewhere),
 * so newsletters, tables and styled mail look the way their sender built them. [document] comes
 * from [emailDocument]. JavaScript is off and the profile is private (nothing is kept between
 * runs); a click on a link is handed to [onOpenUrl] for the real browser instead of navigating.
 */
@Composable
internal fun HtmlBody(
    document: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    onContentHeight: ((Int) -> Unit)? = null,
) {
    if (!holdsWebViewSlot()) {
        Box(modifier)
        return
    }
    // WebView2 takes at most 2 MB as a string; a bigger page (inline photos) is loaded from a file.
    val state = if (document.length > MAX_INLINE_DOCUMENT) {
        val url = remember(document) { writePage(document) }
        rememberWebViewState(url)
    } else {
        rememberWebViewStateWithHTMLData(document)
    }
    remember(state) { configure(state, javaScript = false) }
    val open by rememberUpdatedState(onOpenUrl)
    // The page itself loads as about:blank or data:; anything a click leads to goes to the browser.
    val interceptor = remember { externalLinks { open(it) } }
    val navigator = rememberWebViewNavigator(requestInterceptor = interceptor)
    val report by rememberUpdatedState(onContentHeight)
    if (onContentHeight != null) {
        // Measured once loaded, then again for a while: images and fonts arriving late change it.
        LaunchedEffect(state, navigator) {
            snapshotFlow { state.loadingState }.first { it is LoadingState.Finished }
            var round = 0
            while (true) {
                navigator.evaluateJavaScript(MEASURE_SCRIPT) { result ->
                    result.trim().trim('"').toDoubleOrNull()?.takeIf { it > 0 }?.let { report?.invoke(ceil(it).toInt()) }
                }
                delay(if (round++ < FAST_MEASURES) FAST_MEASURE_MS else SLOW_MEASURE_MS)
            }
        }
    }
    WebView(state, modifier, navigator)
}

private const val FAST_MEASURES = 8
private const val FAST_MEASURE_MS = 250L
private const val SLOW_MEASURE_MS = 1_000L
