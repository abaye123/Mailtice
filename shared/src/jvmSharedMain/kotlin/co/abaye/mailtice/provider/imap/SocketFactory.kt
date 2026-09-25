package co.abaye.mailtice.provider.imap

import javax.net.ssl.SSLSocketFactory

/**
 * The TLS socket factory IMAP connections use. Desktop: Nucleus native trust (the OS certificate
 * store, so NetFree and corporate roots work). Android: null - the platform default already follows
 * the app's network security config.
 */
internal expect fun platformSocketFactory(): SSLSocketFactory?
