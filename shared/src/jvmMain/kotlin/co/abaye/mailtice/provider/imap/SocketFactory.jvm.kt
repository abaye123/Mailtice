package co.abaye.mailtice.provider.imap

import dev.nucleusframework.nativessl.NativeTrustManager
import javax.net.ssl.SSLSocketFactory

/** The JDK ignores the OS certificate store; Nucleus native SSL trusts what the machine trusts. */
internal actual fun platformSocketFactory(): SSLSocketFactory? = runCatching { NativeTrustManager.sslSocketFactory }.getOrNull()
