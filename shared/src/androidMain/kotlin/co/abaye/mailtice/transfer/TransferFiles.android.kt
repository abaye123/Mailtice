package co.abaye.mailtice.transfer

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import co.abaye.mailtice.platform.androidContext
import co.abaye.mailtice.platform.safeFileName
import java.io.File

internal actual object TransferFiles {
    // No file dialog is wired on Android yet; the offline computer is a desktop one.
    actual val canImport: Boolean = false

    /** Written to the cache first, then copied into Downloads/Mailtice as a stream. */
    actual fun export(root: String, fileName: String, write: (ArchiveSink) -> Unit): String {
        val ctx = androidContext()
        val name = safeFileName(fileName, "mailtice.${TransferFormat.EXTENSION}")
        val temp = File(ctx.cacheDir, "$name.part")
        try {
            ZipSink(temp.outputStream()).use(write)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Mailtice")
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw TransferException(TransferError.Failed)
                val out = resolver.openOutputStream(uri) ?: throw TransferException(TransferError.Failed)
                out.use { o -> temp.inputStream().use { it.copyTo(o) } }
                return uri.toString()
            }
            val dir = (ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir).apply { mkdirs() }
            val target = File(dir, name)
            temp.copyTo(target, overwrite = true)
            return target.absolutePath
        } finally {
            temp.delete()
        }
    }

    actual suspend fun pick(title: String): String? = null

    actual fun open(path: String): ArchiveSource = ZipSource(File(path).inputStream())
}
