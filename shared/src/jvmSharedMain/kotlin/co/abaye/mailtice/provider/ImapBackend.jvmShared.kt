package co.abaye.mailtice.provider

import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.provider.imap.ImapProvider

actual fun createImapBackend(auth: AuthManager): ImapBackend = ImapProvider(auth)
