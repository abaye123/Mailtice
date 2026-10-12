package co.abaye.mailtice.data

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Where the key that encrypts the secrets file is kept: whatever the OS offers to hold a secret for
 * the signed-in user, so the file alone (a backup, a copied profile, another user) reads as noise.
 */
internal interface KeyVault {
    /** The stored key; null when none was stored yet. Throws when one may exist but cannot be read now. */
    fun load(): ByteArray?

    fun save(key: ByteArray)
}

private const val SERVICE = "co.abaye.mailtice"
private const val ACCOUNT = "secrets-key"
private const val KEY_FILE = "secrets.key"
private const val TOOL_TIMEOUT_S = 60L

/** The vault for this OS, keeping its key next to the secrets in [dir] where it needs a file. */
internal fun defaultKeyVault(dir: File): KeyVault {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val keyFile = File(dir, KEY_FILE)
    return when {
        os.contains("win") -> DpapiKeyVault(keyFile)

        os.contains("mac") -> KeychainKeyVault()

        // A key already kept in a file stays there: switching would lose it.
        keyFile.isFile || !SecretToolKeyVault.available() -> FileKeyVault(keyFile)

        else -> SecretToolKeyVault()
    }
}

/** Windows: the key sealed with DPAPI for the current user; only that user on this machine opens it. */
internal class DpapiKeyVault(private val file: File) : KeyVault {
    private val entropy = SERVICE.toByteArray()

    override fun load(): ByteArray? {
        if (!file.isFile) return null
        return Crypt32Util.cryptUnprotectData(file.readBytes(), entropy, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
    }

    override fun save(key: ByteArray) {
        writeOwnerOnly(file, Crypt32Util.cryptProtectData(key, entropy, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "Mailtice", null))
    }
}

/**
 * macOS: a generic password in the login Keychain. Written and read by the `security` tool, so the
 * item's access list names that tool and reading it back never prompts.
 */
internal class KeychainKeyVault : KeyVault {
    override fun load(): ByteArray? {
        val result = runTool("/usr/bin/security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w")
        // 44: errSecItemNotFound.
        if (result.exit == 44) return null
        check(result.exit == 0) { "security find-generic-password exited ${result.exit}" }
        return Base64.getDecoder().decode(result.out.trim())
    }

    // The key passes on the command line here, once, when it is made: `security` reads -w from
    // nowhere else without a terminal. Another process of the same user could see it then, and
    // such a process can ask the Keychain for it anyway.
    override fun save(key: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(key)
        val result =
            runTool("/usr/bin/security", "add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT, "-l", "Mailtice", "-w", encoded)
        check(result.exit == 0) { "security add-generic-password exited ${result.exit}" }
    }
}

/** Linux: the desktop's Secret Service (GNOME Keyring, KWallet) through `secret-tool`. */
internal class SecretToolKeyVault : KeyVault {
    override fun load(): ByteArray? {
        val result = runTool("secret-tool", "lookup", "service", SERVICE, "key", ACCOUNT)
        // Not found is exit 1 and nothing said; a locked or missing keyring says why.
        if (result.exit == 1 && result.err.isBlank()) return null
        check(result.exit == 0) { "secret-tool lookup exited ${result.exit}" }
        return Base64.getDecoder().decode(result.out.trim())
    }

    override fun save(key: ByteArray) {
        val result = runTool(
            "secret-tool",
            "store",
            "--label=Mailtice",
            "service",
            SERVICE,
            "key",
            ACCOUNT,
            input = Base64.getEncoder().encodeToString(key),
        )
        check(result.exit == 0) { "secret-tool store exited ${result.exit}" }
    }

    companion object {
        fun available(): Boolean = runCatching { runTool("secret-tool", "--version").exit == 0 }.getOrDefault(false)
    }
}

/** Last resort (Linux without a Secret Service): the key in a file only its owner may read. */
internal class FileKeyVault(private val file: File) : KeyVault {
    override fun load(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun save(key: ByteArray) = writeOwnerOnly(file, key)
}

private class ToolResult(val exit: Int, val out: String, val err: String)

private fun runTool(vararg command: String, input: String? = null): ToolResult {
    val process = ProcessBuilder(*command).start()
    process.outputStream.use { stdin -> input?.let { stdin.write(it.toByteArray()) } }
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()
    if (!process.waitFor(TOOL_TIMEOUT_S, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw IOException("${command.first()} timed out")
    }
    return ToolResult(process.exitValue(), out, err)
}

/** Written whole or not at all, then narrowed to its owner where the file system lets us. */
internal fun writeOwnerOnly(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeBytes(bytes)
    runCatching {
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        tmp.setWritable(false, false)
        tmp.setWritable(true, true)
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
