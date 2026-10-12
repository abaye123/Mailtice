package co.abaye.mailtice.data

import co.abaye.mailtice.platform.Platform
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val SECRETS_FILE = "secrets.enc"
private const val LEGACY_FILE = "secrets.txt"
private const val UNREADABLE_SUFFIX = ".unreadable"
private val MAGIC = "MTS1".toByteArray()
private const val IV_BYTES = 12
private const val KEY_BYTES = 32
private const val TAG_BITS = 128

/**
 * Desktop secrets: one AES-256-GCM sealed file, `secrets.enc`, whose key the OS keeps ([KeyVault]).
 * Read once and held in memory; every change rewrites the file whole.
 *
 * The plain `secrets.txt` of earlier versions is taken in on first use, then overwritten and
 * deleted. A file that cannot be opened any more (its key gone, another user's copy) is set aside,
 * not deleted, and the accounts ask to sign in again. When the vault cannot be reached right now (a
 * locked keyring), nothing is written this run, so the next run can still read what is there.
 */
internal class EncryptedSecretStore(private val dir: File, private val vault: KeyVault) : SecretStore {
    private val file get() = File(dir, SECRETS_FILE)
    private var entries: MutableMap<String, String>? = null
    private var key: ByteArray? = null

    /** False when the vault failed: changes then live in memory only, for this run. */
    private var persist = true

    @Synchronized
    override fun get(key: String): String? = entries()[key]

    @Synchronized
    override fun put(key: String, value: String) {
        entries()[key] = value
        save()
    }

    @Synchronized
    override fun remove(key: String) {
        if (entries().remove(key) != null) save()
    }

    @Synchronized
    override fun clear() {
        entries = mutableMapOf()
        runCatching { file.delete() }
    }

    private fun entries(): MutableMap<String, String> = entries ?: load().also { entries = it }

    private fun load(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val sealed = file
        val legacy = File(dir, LEGACY_FILE)
        val stored = try {
            vault.load()
        } catch (e: Exception) {
            println("Secrets: the key store cannot be reached (${e::class.simpleName}); nothing is saved this run")
            persist = false
            // A plain file not taken in yet still works; it stays until a run can seal it.
            if (legacy.isFile) map += readLegacy(legacy)
            return map
        }
        if (sealed.isFile) {
            val opened = stored?.let { k -> runCatching { open(sealed.readBytes(), k) }.getOrNull() }
            if (opened != null) {
                map += opened
                key = stored
            } else {
                // Its key is gone: the accounts sign in again. Kept aside rather than deleted.
                println("Secrets: the stored secrets cannot be opened; set aside, accounts will ask to sign in again")
                runCatching { sealed.renameTo(File(dir, SECRETS_FILE + UNREADABLE_SUFFIX)) }
            }
        } else if (stored != null) {
            key = stored
        }
        if (legacy.isFile) {
            // Written by an earlier version, or by one run after this one took the file in: the newer wins.
            map += readLegacy(legacy)
            entries = map
            save()
            if (persist) wipe(legacy)
        }
        return map
    }

    private fun save() {
        if (!persist) return
        val map = entries ?: return
        writeOwnerOnly(file, seal(Json.encodeToString(map.toMap()).toByteArray(), keyForWriting()))
    }

    private fun keyForWriting(): ByteArray = key ?: ByteArray(KEY_BYTES).also {
        SecureRandom().nextBytes(it)
        vault.save(it)
        key = it
    }

    private fun seal(plain: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + iv + cipher.doFinal(plain)
    }

    private fun open(bytes: ByteArray, key: ByteArray): Map<String, String> {
        require(bytes.size > MAGIC.size + IV_BYTES && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a secrets file" }
        val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val plain = cipher.doFinal(bytes, MAGIC.size + IV_BYTES, bytes.size - MAGIC.size - IV_BYTES)
        return Json.decodeFromString<Map<String, String>>(plain.decodeToString())
    }

    /** `key=value` lines; the value may hold '=' itself. */
    private fun readLegacy(file: File): Map<String, String> = runCatching {
        file.readLines()
            .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
            .toMap()
    }.getOrDefault(emptyMap())

    /** Overwritten before it goes, so the plain text does not linger in the file's old blocks. */
    private fun wipe(file: File) {
        runCatching { file.writeBytes(ByteArray(file.length().toInt())) }
        runCatching { file.delete() }
    }
}

internal actual fun createSecretStore(): SecretStore {
    val dir = File(Platform.appDir())
    return EncryptedSecretStore(dir, defaultKeyVault(dir))
}
