package co.abaye.mailtice.data

import co.abaye.mailtice.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val VAULT_FILE = "secrets.vault"
private const val VAULT_VERSION = 1
private const val MODE_PASSWORD = "password"
private const val MODE_NONE = "none"
private val VAULT_AAD = "MTV1".toByteArray()
private const val KEY_BYTES = 32
private const val SALT_BYTES = 16
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/** OWASP's figure for PBKDF2-HMAC-SHA256; about half a second on an ordinary laptop. */
internal const val PBKDF2_ROUNDS = 600_000

/** Where a portable copy stands with its secrets. */
internal enum class PortableLock {
    /** First run: the user picks a password or none. */
    Unset,

    /** Protected by a password not given yet in this run. */
    Locked,

    /** Ready: no password, or the right one was given. */
    Open,
}

/** `secrets.vault`: the key of the secrets file, wrapped by a password, or as is ([MODE_NONE]). */
@Serializable
private data class VaultFile(
    val version: Int = VAULT_VERSION,
    val mode: String,
    val key: String,
    val salt: String = "",
    val rounds: Int = 0,
)

/**
 * The portable copy's [KeyVault]. DPAPI cannot serve here: it ties the key to one user on one
 * computer, and a portable copy moves between computers. So the key lives in the data folder,
 * wrapped with a key derived from the user's password (PBKDF2), or unwrapped when the user chose
 * none - a USB stick is something its owner already keeps safe.
 *
 * Until the right password is given [load] throws, which the store reads as "cannot be reached
 * now": nothing is written and nothing is set aside. A wrong password is refused by [unlock]
 * itself (the wrapped key fails its GCM tag), so it never reaches the store looking like a lost key.
 */
internal class PortableKeyVault(private val file: File) : KeyVault {
    private var unlocked: ByteArray? = null

    val state: PortableLock
        @Synchronized get() {
            val vault = read() ?: return PortableLock.Unset
            return if (vault.mode == MODE_NONE || unlocked != null) PortableLock.Open else PortableLock.Locked
        }

    /** Whether the secrets are behind a password. */
    val hasPassword: Boolean get() = read()?.mode == MODE_PASSWORD

    @Synchronized
    override fun load(): ByteArray? {
        val vault = read() ?: return null
        return when (vault.mode) {
            MODE_NONE -> decode(vault.key)
            else -> unlocked?.copyOf() ?: throw IllegalStateException("portable secrets are locked")
        }
    }

    /** Reached only when no vault exists yet; the first run sets one up before anything is stored. */
    @Synchronized
    override fun save(key: ByteArray) {
        if (read() == null) write(VaultFile(mode = MODE_NONE, key = encode(key)))
    }

    /** First run: a new key, behind [password] or behind nothing when it is null. */
    @Synchronized
    fun setUp(password: CharArray?) {
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        write(seal(key, password))
        unlocked = key
    }

    /** Opens the vault with [password]; false when it is the wrong one. */
    @Synchronized
    fun unlock(password: CharArray): Boolean {
        val vault = read() ?: return false
        if (vault.mode != MODE_PASSWORD) return true
        val wrapped = decode(vault.key)
        val kek = derive(password, decode(vault.salt), vault.rounds)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            cipher.updateAAD(VAULT_AAD)
            unlocked = cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
            true
        } catch (_: AEADBadTagException) {
            false
        } finally {
            kek.fill(0)
        }
    }

    /** Puts the same key behind a new password, or behind none. Needs the vault open. */
    @Synchronized
    fun changePassword(password: CharArray?) {
        val key = load() ?: return
        write(seal(key, password))
        unlocked = key
    }

    /**
     * The password is lost: the vault goes, and the secrets file it opened is set aside the way a
     * lost key always is, so every account signs in again.
     */
    @Synchronized
    fun forget(secrets: File) {
        unlocked = null
        file.delete()
        if (secrets.isFile) secrets.renameTo(File(secrets.parentFile, secrets.name + ".unreadable"))
    }

    private fun seal(key: ByteArray, password: CharArray?): VaultFile {
        if (password == null) return VaultFile(mode = MODE_NONE, key = encode(key))
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val kek = derive(password, salt, PBKDF2_ROUNDS)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(VAULT_AAD)
            return VaultFile(mode = MODE_PASSWORD, key = encode(iv + cipher.doFinal(key)), salt = encode(salt), rounds = PBKDF2_ROUNDS)
        } finally {
            kek.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, rounds, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun read(): VaultFile? = file.takeIf {
        it.isFile
    }?.let { runCatching { Json.decodeFromString<VaultFile>(it.readText()) }.getOrNull() }

    private fun write(vault: VaultFile) = writeOwnerOnly(file, Json.encodeToString(vault).toByteArray())

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)
}

/** The one vault of a portable copy, shared by the lock screen and the secret store. */
internal object PortableSecrets {
    val vault: PortableKeyVault by lazy { PortableKeyVault(File(Platform.appDir(), VAULT_FILE)) }

    /** The store's own file, set aside when the password is forgotten. */
    val secretsFile: File get() = File(Platform.appDir(), "secrets.enc")
}
