package co.abaye.mailtice.transfer

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Larger entries are skipped rather than read: no transfer file entry comes near it. */
private const val MAX_ENTRY_BYTES = 1L shl 30

/** A transfer file being written; ZIP64 takes over by itself past 4 GB or 65,535 entries. */
internal class ZipSink(out: OutputStream) :
    ArchiveSink,
    AutoCloseable {
    private val zip = ZipOutputStream(out.buffered(1 shl 16))

    override fun put(name: String, bytes: ByteArray, compress: Boolean) {
        val entry = ZipEntry(name)
        if (!compress) {
            // A stored entry carries its size and checksum up front, which streaming readers need.
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }
}

/** A transfer file read front to back, one entry at a time. */
internal class ZipSource(input: InputStream) : ArchiveSource {
    private val zip = ZipInputStream(input.buffered(1 shl 16))

    override fun next(): ArchiveEntry? {
        while (true) {
            val next: ZipEntry? = try {
                zip.nextEntry
            } catch (e: ZipException) {
                throw TransferException(TransferError.NotATransferFile, e)
            }
            val entry = next ?: return null
            if (entry.isDirectory) continue
            if (entry.size > MAX_ENTRY_BYTES) continue
            val bytes = readLimited(zip) ?: continue
            return ArchiveEntry(entry.name, bytes)
        }
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    override fun close() {
        zip.close()
    }
}
