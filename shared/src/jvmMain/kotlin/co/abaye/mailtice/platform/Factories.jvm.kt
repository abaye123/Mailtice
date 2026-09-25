package co.abaye.mailtice.platform

import co.abaye.mailtice.auth.Authorizer
import co.abaye.mailtice.auth.LoopbackAuthorizer
import co.abaye.mailtice.notify.Notifier
import co.abaye.mailtice.notify.NucleusNotifier

internal actual fun createAuthorizer(): Authorizer = LoopbackAuthorizer()

internal actual fun createNotifier(): Notifier = NucleusNotifier()
