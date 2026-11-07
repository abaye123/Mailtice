package co.abaye.mailtice.auth

import co.abaye.mailtice.data.SecretStore
import kotlin.concurrent.Volatile

/**
 * OAuth clients the user entered in Settings (desktop), for a build made without the OAuth secrets:
 * each one takes the place of the built-in value when it is set. Kept in the [SecretStore], never
 * in the settings snapshot.
 */
internal object OAuthOverrides {
    @Volatile
    var keys: OAuthKeys = OAuthKeys()
        private set

    fun load(secrets: SecretStore) {
        keys = OAuthKeys(
            googleClientId = secrets.get(GOOGLE_ID).orEmpty(),
            googleClientSecret = secrets.get(GOOGLE_SECRET).orEmpty(),
            microsoftClientId = secrets.get(MICROSOFT_ID).orEmpty(),
        )
    }

    fun save(secrets: SecretStore, value: OAuthKeys) {
        val clean = OAuthKeys(value.googleClientId.trim(), value.googleClientSecret.trim(), value.microsoftClientId.trim())
        listOf(GOOGLE_ID to clean.googleClientId, GOOGLE_SECRET to clean.googleClientSecret, MICROSOFT_ID to clean.microsoftClientId)
            .forEach { (key, v) -> if (v.isEmpty()) secrets.remove(key) else secrets.put(key, v) }
        keys = clean
    }

    private const val GOOGLE_ID = "oauth:google-client-id"
    private const val GOOGLE_SECRET = "oauth:google-client-secret"
    private const val MICROSOFT_ID = "oauth:microsoft-client-id"
}

/** The OAuth clients a user may enter by hand; "" = use the one the build carries. */
data class OAuthKeys(val googleClientId: String = "", val googleClientSecret: String = "", val microsoftClientId: String = "")
