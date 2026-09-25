package co.abaye.mailtice.provider.imap

import javax.net.ssl.SSLSocketFactory

/** The platform default already applies res/xml/network_security_config.xml (NetFree roots). */
internal actual fun platformSocketFactory(): SSLSocketFactory? = null
