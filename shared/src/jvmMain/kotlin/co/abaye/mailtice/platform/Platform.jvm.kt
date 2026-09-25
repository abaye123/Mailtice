package co.abaye.mailtice.platform

import co.abaye.mailtice.dev.DemoMode
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.nucleusframework.core.runtime.NucleusApp
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

    actual fun saveDownload(folder: String, fileName: String, bytes: ByteArray): String? = runCatching {
        val downloads = File(System.getProperty("user.home").orEmpty(), "Downloads")
        val dir = File(File(downloads, APP_DIR_NAME), safeFileName(folder, ""))
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
                else -> if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir) else ProcessBuilder("xdg-open", dir.absolutePath).start()
            }
        }
    }

    actual val canPickFiles: Boolean = true

    actual fun pickFiles(title: String): List<PickedFile> = runCatching {
        // A hidden owner frame: the window itself is not AWT (Tao), and FileDialog wants a Frame.
        val owner = java.awt.Frame()
        try {
            val dialog = java.awt.FileDialog(owner, title, java.awt.FileDialog.LOAD).apply { isMultipleMode = true }
            dialog.isVisible = true
            dialog.files.orEmpty().filter { it.isFile }.map { f ->
                val mime = runCatching { java.nio.file.Files.probeContentType(f.toPath()) }.getOrNull()
                    ?: java.net.URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream"
                PickedFile(f.name, mime, f.readBytes())
            }
        } finally {
            owner.dispose()
        }
    }.getOrDefault(emptyList())

    actual fun setLaunchAtLogin(enabled: Boolean): Boolean = runCatching {
        val result = if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
        result == AutoLaunchResult.OK || result == AutoLaunchResult.UNCHANGED
    }.getOrDefault(false)
}
