package co.abaye.mailtice.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Every Google call goes through this client. On the desktop the JDK trusts only its bundled
 * `cacerts` and ignores the OS store, so behind a TLS-filtering line (NetFree, corporate proxies)
 * every request would fail the handshake. The JVM actual installs Nucleus native SSL to fix that.
 */
internal expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
