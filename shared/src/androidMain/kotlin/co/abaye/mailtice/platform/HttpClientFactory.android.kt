package co.abaye.mailtice.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** `res/xml/network_security_config.xml` in the app module already names the roots to trust. */
internal actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(config)
