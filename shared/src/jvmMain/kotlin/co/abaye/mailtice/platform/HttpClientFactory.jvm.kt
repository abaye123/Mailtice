package co.abaye.mailtice.platform

import dev.nucleusframework.nativehttp.ktor.installNativeSsl
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * The engine is named rather than left to the service loader, because [installNativeSsl] configures
 * whichever engine it recognises and there has to be one on the config to recognise.
 */
internal actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    config()
    installNativeSsl()
}
