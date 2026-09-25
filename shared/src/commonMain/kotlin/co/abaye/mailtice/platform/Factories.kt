package co.abaye.mailtice.platform

import co.abaye.mailtice.auth.Authorizer
import co.abaye.mailtice.notify.Notifier

/** Desktop: loopback server. Android: Play services (Google) and Custom Tabs (Microsoft). */
internal expect fun createAuthorizer(): Authorizer

internal expect fun createNotifier(): Notifier
