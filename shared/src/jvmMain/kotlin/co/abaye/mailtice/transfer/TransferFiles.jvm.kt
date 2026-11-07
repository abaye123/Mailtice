package co.abaye.mailtice.transfer

import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.safeFileName
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.io.File

internal actual object TransferFiles {
    actual val canImport: Boolean = true

    /** Streams straight to the file, so a mailbox far larger than memory exports just the same. */
    actual fun export(root: String, fileName: String, write: (ArchiveSink) -> Unit): String {
        val dir = root.takeIf { it.isNotBlank() }?.let(::File) ?: File(Platform.defaultDownloadRoot())
        dir.mkdirs()
        val name = safeFileName(fileName, "mailtice.${TransferFormat.EXTENSION}")
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var target = File(dir, name)
        var n = 2
        while (target.exists()) target = File(dir, "$stem ($n)$ext").also { n++ }
        // Written under a temporary name: a stick pulled out halfway leaves no file that looks whole.
        val partial = File(dir, target.name + ".part")
        try {
            ZipSink(partial.outputStream()).use(write)
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = false)
                partial.delete()
            }
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
        return target.absolutePath
    }

    actual suspend fun pick(title: String): String? = runCatching {
        val parent = if (Platform.osLabel == "Windows") {
            runCatching {
                val hwnd = com.sun.jna.platform.win32.User32.INSTANCE.GetForegroundWindow()
                FileKitDialogParent.windows(com.sun.jna.Pointer.nativeValue(hwnd.pointer))
            }.getOrNull()
        } else {
            null
        }
        FileKit.openFilePicker(
            type = FileKitType.File(extensions = listOf(TransferFormat.EXTENSION, "zip")),
            mode = FileKitMode.Single,
            dialogSettings = FileKitDialogSettings(title = title, parent = parent),
        )?.file?.absolutePath
    }.getOrNull()

    actual fun open(path: String): ArchiveSource {
        val file = File(path)
        if (!file.isFile) throw TransferException(TransferError.Failed)
        return ZipSource(file.inputStream())
    }
}
