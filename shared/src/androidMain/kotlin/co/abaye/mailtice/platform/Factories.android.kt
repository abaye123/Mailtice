package co.abaye.mailtice.platform

import co.abaye.mailtice.auth.AndroidAuthorizer
import co.abaye.mailtice.auth.Authorizer
import co.abaye.mailtice.notify.Notifier
import co.abaye.mailtice.notify.createAndroidNotifier

internal actual fun createAuthorizer(): Authorizer = AndroidAuthorizer()

internal actual fun createNotifier(): Notifier = createAndroidNotifier()
