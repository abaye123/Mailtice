package co.abaye.mailtice

import co.abaye.mailtice.data.DpapiKeyVault
import co.abaye.mailtice.data.EncryptedSecretStore
import co.abaye.mailtice.data.KeyVault
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretStoreTest {
    private val dir: File = Files.createTempDirectory("mailtice-secrets").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Holds the key in memory; [broken] makes it fail as a locked keyring does. */
    private class MemoryVault(var key: ByteArray? = null, var broken: Boolean = false) : KeyVault {
        override fun load(): ByteArray? {
            check(!broken) { "locked" }
            return key
        }

        override fun save(key: ByteArray) {
            check(!broken) { "locked" }
            this.key = key
        }
    }

    private val token = "1//0refresh-token-value"

    @Test
    fun secretsAreSealedAndReadBackByTheNextRun() {
        val vault = MemoryVault()
        EncryptedSecretStore(dir, vault).put("refresh.acc", token)
        val sealed = File(dir, "secrets.enc")
        assertTrue(sealed.isFile)
        assertFalse(String(sealed.readBytes(), Charsets.ISO_8859_1).contains(token), "the token is in the file as plain text")
        assertEquals(token, EncryptedSecretStore(dir, vault).get("refresh.acc"))
    }

    @Test
    fun thePlainFileOfEarlierVersionsIsTakenInAndDeleted() {
        File(dir, "secrets.txt").writeText("refresh.acc=$token\npassword.imap=p=ss")
        val store = EncryptedSecretStore(dir, MemoryVault())
        assertEquals(token, store.get("refresh.acc"))
        assertEquals("p=ss", store.get("password.imap"))
        assertFalse(File(dir, "secrets.txt").exists())
        assertTrue(File(dir, "secrets.enc").isFile)
    }

    @Test
    fun aFileWhoseKeyIsGoneIsSetAsideAndTheStoreStartsOver() {
        EncryptedSecretStore(dir, MemoryVault()).put("refresh.acc", token)
        // Another vault: the key that sealed the file is not in it.
        val store = EncryptedSecretStore(dir, MemoryVault())
        assertNull(store.get("refresh.acc"))
        assertTrue(File(dir, "secrets.enc.unreadable").isFile)
        store.put("refresh.acc", "new")
        assertEquals("new", store.get("refresh.acc"))
    }

    @Test
    fun aLockedVaultWritesNothingAndKeepsThePlainFileWorking() {
        val vault = MemoryVault()
        EncryptedSecretStore(dir, vault).put("refresh.acc", token)
        val before = File(dir, "secrets.enc").readBytes()
        File(dir, "secrets.txt").writeText("password.imap=pw")
        vault.broken = true
        val store = EncryptedSecretStore(dir, vault)
        assertEquals("pw", store.get("password.imap"))
        store.put("refresh.other", "x")
        assertContentEquals(before, File(dir, "secrets.enc").readBytes())
        assertTrue(File(dir, "secrets.txt").isFile)
        // Unlocked on the next run: everything is there, the plain file is taken in.
        vault.broken = false
        val next = EncryptedSecretStore(dir, vault)
        assertEquals(token, next.get("refresh.acc"))
        assertEquals("pw", next.get("password.imap"))
        assertFalse(File(dir, "secrets.txt").exists())
    }

    @Test
    fun dpapiSealsTheKeyForThisUser() {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return
        val vault = DpapiKeyVault(File(dir, "secrets.key"))
        assertNull(vault.load())
        val key = ByteArray(32) { it.toByte() }
        vault.save(key)
        assertFalse(File(dir, "secrets.key").readBytes().contentEquals(key))
        assertContentEquals(key, vault.load())
    }
}
