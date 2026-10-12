package co.abaye.mailtice.data

/**
 * Refresh tokens and IMAP passwords. Never in the database, never in the settings snapshot.
 * Android: AES-GCM with a key in the Android Keystore. Desktop: an AES-GCM sealed file whose key the
 * OS keeps (DPAPI on Windows, the Keychain on macOS, the Secret Service on Linux).
 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

internal expect fun createSecretStore(): SecretStore
