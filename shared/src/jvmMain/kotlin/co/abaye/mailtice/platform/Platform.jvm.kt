package co.abaye.mailtice.platform

import co.abaye.mailtice.dev.DemoMode
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.nucleusframework.core.runtime.NucleusApp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Locale

private const val APP_DIR_NAME = "Mailtice"
internal const val DEMO_DIR_NAME = "Mailtice-Demo"

internal actual object Platform {
    actual val osLabel: String
        get() {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.contains("mac") -> "macOS"
                os.contains("win") -> "Windows"
                else -> "Linux"
            }
        }

    actual val appVersion: String
        get() = NucleusApp.version.orEmpty()

    /** Per-OS user data location: %APPDATA% on Windows, ~/Library on macOS, XDG on Linux. */
    actual fun appDir(): String {
        val home = System.getProperty("user.home").orEmpty()
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: joinPath(home, "AppData\\Roaming")
            os.contains("mac") -> joinPath(joinPath(home, "Library"), "Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: joinPath(joinPath(home, ".local"), "share")
        }
        // Demo mode never sees (or damages) the real accounts, settings and secrets.
        val dir = joinPath(base, if (DemoMode.enabled) DEMO_DIR_NAME else APP_DIR_NAME)
        mkdir(dir)
        return dir
    }

    actual fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.isFile }?.readText()
    }.getOrNull()

    actual fun writeText(path: String, content: String) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    actual fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    actual fun mkdir(path: String) {
        runCatching { File(path).mkdirs() }
    }

    actual fun now(): Long = System.currentTimeMillis()

    actual fun applyLocale(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
    }

    // Read once at object init, which happens before the first applyLocale() call.
    private val bootLanguage: String = Locale.getDefault().language

    actual fun systemLanguage(): String = bootLanguage

    actual fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                return
            }
            val command = when {
                osLabel == "Windows" -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                osLabel == "macOS" -> listOf("open", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(command).start()
        }
    }

    actual val isDesktop: Boolean = true

    /**
     * Writes the body to a temp file and opens it in the default browser. The CSP blocks every
     * remote load, so tracking pixels and remote images stay dead; inline styles still apply.
     */
    actual fun openHtml(html: String) {
        runCatching {
            val file = java.io.File.createTempFile("mailtice-", ".html").apply { deleteOnExit() }
            val csp = "default-src 'none'; img-src data: cid:; style-src 'unsafe-inline'"
            file.writeText(
                "<!doctype html><html><head><meta charset=\"utf-8\">" +
                    "<meta http-equiv=\"Content-Security-Policy\" content=\"$csp\"></head><body>$html</body></html>",
                Charsets.UTF_8,
            )
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(file.toURI())
            }
        }
    }

    actual fun defaultDownloadRoot(): String = File(File(System.getProperty("user.home").orEmpty(), "Downloads"), APP_DIR_NAME).absolutePath

    actual fun saveDownload(folder: String, fileName: String, bytes: ByteArray, root: String): String? = runCatching {
        val base = root.takeIf { it.isNotBlank() }?.let(::File) ?: File(defaultDownloadRoot())
        val dir = File(base, safeFileName(folder, ""))
        dir.mkdirs()
        val name = safeFileName(fileName, "attachment")
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var target = File(dir, name)
        var n = 2
        while (target.exists()) target = File(dir, "$stem ($n)$ext").also { n++ }
        target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()

    actual fun revealDownload(location: String) {
        runCatching {
            val file = File(location)
            val dir = if (file.isDirectory) file else file.parentFile ?: return
            when (osLabel) {
                // Opens the folder with the file selected.
                "Windows" -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()

                "macOS" -> ProcessBuilder("open", "-R", file.absolutePath).start()

                else -> if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(
                        dir,
                    )
                } else {
                    ProcessBuilder("xdg-open", dir.absolutePath).start()
                }
            }
        }
    }

    actual val canPickFiles: Boolean = true

    actual val canPickFolder: Boolean = true

    actual val canOpenFiles: Boolean = true

    actual fun readBytes(path: String): ByteArray? = runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    actual fun openFile(path: String): Boolean = runCatching {
        val file = File(path)
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            when (osLabel) {
                "Windows" -> ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
                "macOS" -> ProcessBuilder("open", file.absolutePath).start()
                else -> ProcessBuilder("xdg-open", file.absolutePath).start()
            }
        }
        true
    }.getOrDefault(false)

    /** Same native dialogs as [pickFiles], owned by the app window so it opens in front. */
    actual suspend fun pickFolder(title: String): String? = runCatching {
        val parent = if (osLabel == "Windows") {
            runCatching {
                val hwnd = com.sun.jna.platform.win32.User32.INSTANCE.GetForegroundWindow()
                FileKitDialogParent.windows(com.sun.jna.Pointer.nativeValue(hwnd.pointer))
            }.getOrNull()
        } else {
            null
        }
        FileKit.openDirectoryPicker(dialogSettings = FileKitDialogSettings(title = title, parent = parent))?.file?.absolutePath
    }.getOrNull()

    /**
     * The native dialog (FileKit): IFileOpenDialog on Windows - the current Windows 11 one - NSOpenPanel
     * on macOS, the XDG portal on Linux. On Windows it is owned by the app's window (the foreground
     * one: the user just clicked "attach" in it), so it opens in front of the app, never behind it.
     */
    actual suspend fun pickFiles(title: String): List<PickedFile> = runCatching {
        val parent = if (osLabel == "Windows") {
            runCatching {
                val hwnd = com.sun.jna.platform.win32.User32.INSTANCE.GetForegroundWindow()
                FileKitDialogParent.windows(com.sun.jna.Pointer.nativeValue(hwnd.pointer))
            }.getOrNull()
        } else {
            null
        }
        val picked = FileKit.openFilePicker(
            type = FileKitType.File(),
            mode = FileKitMode.Multiple(),
            dialogSettings = FileKitDialogSettings(title = title, parent = parent),
        )
        picked.orEmpty().mapNotNull { readPicked(it.file) }
    }.getOrDefault(emptyList())

    /** A file from the dialog or a drop, with its type guessed from the name / content. */
    fun readPicked(f: File): PickedFile? {
        if (!f.isFile) return null
        val mime = runCatching { java.nio.file.Files.probeContentType(f.toPath()) }.getOrNull()
            ?: java.net.URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream"
        return PickedFile(f.name, mime, f.readBytes())
    }

    actual fun setLaunchAtLogin(enabled: Boolean): Boolean = runCatching {
        val result = if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
        result == AutoLaunchResult.OK || result == AutoLaunchResult.UNCHANGED
    }.getOrDefault(false)
}
