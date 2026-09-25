package co.abaye.mailtice.data

/**
 * Refresh tokens and IMAP passwords. Never in the database, never in the settings snapshot.
 * Android: AES-GCM with a key in the Android Keystore. Desktop MVP: a separate file readable only by
 * the OS user - TODO before 1.0: Windows Credential Manager / macOS Keychain / Secret Service.
 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

internal expect fun createSecretStore(): SecretStore
