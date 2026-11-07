package co.abaye.mailtice.transfer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val MAGIC = "MAILTICE-ENC-1\n".encodeToByteArray()
private const val SALT_BYTES = 16
private const val KEY_BITS = 256
private const val TAG_BITS = 128
private const val TAG_BYTES = TAG_BITS / 8
private const val NONCE_BYTES = 12

/** OWASP's figure for PBKDF2-HMAC-SHA256, the same as the portable copy's password. */
private const val ROUNDS = 600_000

/** Refuses a file that would keep the key derivation busy for minutes. */
private const val MAX_ROUNDS = 10_000_000
private const val FRAME_BYTES = 1 shl 20

/**
 * The password-protected transfer file: a header (magic, salt, rounds), then the zip archive as
 * AES-256-GCM frames. A frame's nonce is its number, which is safe because the random salt gives
 * every file its own key, and the last frame is marked inside the tag, so a file cut short or with
 * frames swapped is refused rather than read as a smaller one.
 */
internal object TransferCrypto {
    fun isEncrypted(input: InputStream): Boolean = try {
        ByteArray(MAGIC.size).also(DataInputStream(input)::readFully).contentEquals(MAGIC)
    } catch (_: EOFException) {
        false
    }

    fun encrypt(out: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.write(salt)
        data.writeInt(ROUNDS)
        return EncryptingStream(data, deriveKey(password, salt, ROUNDS))
    }

    fun decrypt(input: InputStream, password: String): InputStream {
        val data = DataInputStream(input)
        try {
            if (!ByteArray(MAGIC.size).also(data::readFully).contentEquals(MAGIC)) throw TransferException(TransferError.NotATransferFile)
            val salt = ByteArray(SALT_BYTES).also(data::readFully)
            val rounds = data.readInt()
            if (rounds !in 1..MAX_ROUNDS) throw TransferException(TransferError.NotATransferFile)
            return DecryptingStream(data, deriveKey(password, salt, rounds))
        } catch (e: EOFException) {
            throw TransferException(TransferError.NotATransferFile, e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, rounds: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

private fun frameCipher(mode: Int, key: SecretKey, frame: Long, last: Boolean): Cipher {
    val nonce = ByteBuffer.allocate(NONCE_BYTES).putLong(NONCE_BYTES - Long.SIZE_BYTES, frame).array()
    return Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        updateAAD(byteArrayOf(if (last) 1 else 0))
    }
}

private class EncryptingStream(private val out: DataOutputStream, private val key: SecretKey) : OutputStream() {
    private val buffer = ByteArray(FRAME_BYTES)
    private var used = 0
    private var frame = 0L
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        var from = off
        var left = len
        while (left > 0) {
            // Emptied only when more follows: the last frame must be the one close() marks.
            if (used == buffer.size) writeFrame(last = false)
            val n = minOf(left, buffer.size - used)
            b.copyInto(buffer, used, from, from + n)
            used += n
            from += n
            left -= n
        }
    }

    private fun writeFrame(last: Boolean) {
        val sealed = frameCipher(Cipher.ENCRYPT_MODE, key, frame++, last).doFinal(buffer, 0, used)
        out.writeBoolean(last)
        out.writeInt(sealed.size)
        out.write(sealed)
        used = 0
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        out.use { writeFrame(last = true) }
    }
}

private class DecryptingStream(private val input: DataInputStream, private val key: SecretKey) : InputStream() {
    private var plain = ByteArray(0)
    private var position = 0
    private var frame = 0L
    private var ended = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position == plain.size) {
            if (ended) return -1
            readFrame()
        }
        val n = minOf(len, plain.size - position)
        plain.copyInto(b, off, position, position + n)
        position += n
        return n
    }

    private fun readFrame() {
        try {
            val last = input.readBoolean()
            val size = input.readInt()
            if (size !in TAG_BYTES..FRAME_BYTES + TAG_BYTES) throw TransferException(TransferError.Failed)
            val sealed = ByteArray(size).also(input::readFully)
            plain = frameCipher(Cipher.DECRYPT_MODE, key, frame, last).doFinal(sealed)
            position = 0
            ended = last
            frame++
        } catch (e: AEADBadTagException) {
            // The first frame failing is what a wrong password looks like; a later one is damage.
            throw TransferException(if (frame == 0L) TransferError.WrongPassword else TransferError.Failed, e)
        } catch (e: EOFException) {
            throw TransferException(TransferError.Failed, e)
        }
    }

    override fun close() = input.close()
}
