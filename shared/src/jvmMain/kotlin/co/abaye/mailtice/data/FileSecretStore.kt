package co.abaye.mailtice.data

import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import java.io.File

/**
 * MVP desktop secret store: `secrets.txt` in the app dir, owner-only permissions where the OS lets
 * us set them. TODO(1.0): Windows Credential Manager / macOS Keychain / libsecret.
 */
internal class FileSecretStore : SecretStore {
    private val file get() = File(joinPath(Platform.appDir(), "secrets.txt"))

    @Synchronized
    private fun read(): Map<String, String> = runCatching {
        file.takeIf { it.isFile }?.readLines().orEmpty()
            .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
            .toMap()
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun write(map: Map<String, String>) {
        val f = file
        f.writeText(map.entries.joinToString("\n") { "${it.key}=${it.value}" })
        runCatching {
            f.setReadable(false, false)
            f.setReadable(true, true)
            f.setWritable(false, false)
            f.setWritable(true, true)
        }
    }

    override fun get(key: String): String? = read()[key]

    override fun put(key: String, value: String) = write(read() + (key to value))

    override fun remove(key: String) = write(read() - key)

    override fun clear() {
        runCatching { file.delete() }
    }
}

internal actual fun createSecretStore(): SecretStore = FileSecretStore()
