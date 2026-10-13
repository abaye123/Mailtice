package co.abaye.mailtice

import co.abaye.mailtice.data.EncryptedSecretStore
import co.abaye.mailtice.data.PortableKeyVault
import co.abaye.mailtice.data.PortableLock
import co.abaye.mailtice.platform.PORTABLE_DATA_DIR
import co.abaye.mailtice.platform.PORTABLE_MARKER
import co.abaye.mailtice.platform.portableDataDir
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortableTest {
    private val dir: File = Files.createTempDirectory("mailtice-portable").toFile()
    private val exe = File(dir, "Mailtice.exe").apply { writeText("") }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun onlyTheMarkerFileMakesACopyPortable() {
        // A stray "data" folder beside the executable is not enough.
        File(dir, PORTABLE_DATA_DIR).mkdirs()
        assertNull(portableDataDir(exe.path))
        File(dir, PORTABLE_MARKER).writeText("")
        assertEquals(File(dir, PORTABLE_DATA_DIR), portableDataDir(exe.path))
        // A run from source goes through the JDK's java.exe, whatever sits beside it.
        assertNull(portableDataDir(File(dir, "java.exe").path))
        assertNull(portableDataDir(null))
    }

    private val token = "1//0refresh-token-value"

    @Test
    fun aPasswordGuardsTheSecretsOnTheNextComputer() {
        val vaultFile = File(dir, "secrets.vault")
        val first = PortableKeyVault(vaultFile)
        assertEquals(PortableLock.Unset, first.state)
        first.setUp("correct horse".toCharArray())
        EncryptedSecretStore(dir, first).put("refresh.acc", token)
        assertFalse(vaultFile.readText().contains(token))

        // Another run, as on another computer: locked until the password is given.
        val next = PortableKeyVault(vaultFile)
        assertEquals(PortableLock.Locked, next.state)
        assertFalse(next.unlock("wrong".toCharArray()))
        assertEquals(PortableLock.Locked, next.state)
        assertTrue(next.unlock("correct horse".toCharArray()))
        assertEquals(PortableLock.Open, next.state)
        assertEquals(token, EncryptedSecretStore(dir, next).get("refresh.acc"))
    }

    @Test
    fun aLockedVaultNeverSetsTheSecretsAside() {
        val vaultFile = File(dir, "secrets.vault")
        PortableKeyVault(vaultFile).apply { setUp("pw12".toCharArray()) }.also { EncryptedSecretStore(dir, it).put("refresh.acc", token) }
        val locked = PortableKeyVault(vaultFile)
        assertNull(EncryptedSecretStore(dir, locked).get("refresh.acc"))
        assertTrue(File(dir, "secrets.enc").isFile)
        assertFalse(File(dir, "secrets.enc.unreadable").exists())
    }

    @Test
    fun withoutAPasswordItOpensOnItsOwn() {
        val vaultFile = File(dir, "secrets.vault")
        PortableKeyVault(vaultFile).apply { setUp(null) }.also { EncryptedSecretStore(dir, it).put("refresh.acc", token) }
        val next = PortableKeyVault(vaultFile)
        assertEquals(PortableLock.Open, next.state)
        assertFalse(next.hasPassword)
        assertEquals(token, EncryptedSecretStore(dir, next).get("refresh.acc"))
    }

    @Test
    fun aNewPasswordKeepsTheSecrets() {
        val vaultFile = File(dir, "secrets.vault")
        val vault = PortableKeyVault(vaultFile).apply { setUp(null) }
        EncryptedSecretStore(dir, vault).put("refresh.acc", token)
        vault.changePassword("new pass".toCharArray())
        val next = PortableKeyVault(vaultFile)
        assertTrue(next.hasPassword)
        assertTrue(next.unlock("new pass".toCharArray()))
        assertEquals(token, EncryptedSecretStore(dir, next).get("refresh.acc"))
    }

    @Test
    fun aForgottenPasswordStartsOver() {
        val vaultFile = File(dir, "secrets.vault")
        PortableKeyVault(vaultFile).apply { setUp("pw12".toCharArray()) }.also { EncryptedSecretStore(dir, it).put("refresh.acc", token) }
        val next = PortableKeyVault(vaultFile)
        next.forget(File(dir, "secrets.enc"))
        assertEquals(PortableLock.Unset, next.state)
        assertTrue(File(dir, "secrets.enc.unreadable").isFile)
        next.setUp(null)
        assertNull(EncryptedSecretStore(dir, next).get("refresh.acc"))
    }
}
