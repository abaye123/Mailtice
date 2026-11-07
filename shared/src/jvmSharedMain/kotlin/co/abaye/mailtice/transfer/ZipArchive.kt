package co.abaye.mailtice.transfer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** No account or message entry comes near this: the exporter starts a new chunk well before it. */
private const val MAX_TEXT_BYTES = 256L shl 20

/** An attachment larger than this is left out, whatever the file claims. */
private const val MAX_FILE_BYTES = 4L shl 30
private const val BUFFER_BYTES = 1 shl 16

/** The archive written to [out], encrypted when there is a [password]. */
internal fun zipSink(out: OutputStream, password: String?): ZipSink =
    ZipSink(if (password != null) TransferCrypto.encrypt(out, password) else out)

/** The archive read from [input], decrypted when there is a [password]. */
internal fun zipSource(input: InputStream, password: String?): ZipSource =
    ZipSource(if (password != null) TransferCrypto.decrypt(input, password) else input)

/** A transfer file being written; ZIP64 takes over by itself past 4 GB or 65,535 entries. */
internal class ZipSink(out: OutputStream) :
    ArchiveSink,
    AutoCloseable {
    private val zip = ZipOutputStream(out.buffered(BUFFER_BYTES)).apply { setLevel(Deflater.BEST_SPEED) }

    override fun put(name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun putFile(name: String, path: String) {
        zip.putNextEntry(ZipEntry(name))
        File(path).inputStream().use { it.copyTo(zip, BUFFER_BYTES) }
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }
}

/** A transfer file read front to back, one entry at a time and never whole. */
internal class ZipSource(input: InputStream) : ArchiveSource {
    private val zip = ZipInputStream(input.buffered(BUFFER_BYTES))

    override fun next(): ArchiveEntry? {
        while (true) {
            val entry = try {
                zip.nextEntry
            } catch (e: ZipException) {
                throw TransferException(TransferError.NotATransferFile, e)
            } ?: return null
            if (!entry.isDirectory) return Current(entry.name)
        }
    }

    /** Good until the next call to [next], which moves the stream on. */
    private inner class Current(override val name: String) : ArchiveEntry {
        override fun readBytes(): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            return if (copyLimited(out, MAX_TEXT_BYTES)) out.toByteArray() else null
        }

        override fun saveTo(path: String): Boolean {
            val target = File(path)
            target.parentFile?.mkdirs()
            // Under another name until it is whole: a full disk leaves no attachment that looks done.
            val partial = File(target.path + ".part")
            try {
                if (!partial.outputStream().use { copyLimited(it, MAX_FILE_BYTES) }) return false
                target.delete()
                return partial.renameTo(target)
            } finally {
                partial.delete()
            }
        }
    }

    /** False when the entry is longer than [limit]. */
    private fun copyLimited(out: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) return true
            total += read
            if (total > limit) return false
            out.write(buffer, 0, read)
        }
    }

    override fun close() {
        zip.close()
    }
}
