import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.launcher.linux.LinuxLauncherEntry
import dev.nucleusframework.launcher.windows.TaskbarIconSource
import dev.nucleusframework.launcher.windows.WindowsOverlayIcon
import java.awt.Taskbar
import java.io.File

/**
 * The unread count on the app's taskbar / dock / launcher icon. Every OS call is best effort: a
 * desktop without badge support (or a dev run the OS does not know as an installed app) simply
 * shows no number.
 *
 * - Windows: an overlay icon on the window's taskbar button ([BadgeIcons.overlayIco]). Unlike a
 *   badge notification it needs no installed app identity, so it also shows in a run from source.
 * - macOS: the Dock tile badge.
 * - Linux: the Unity launcher count (KDE, Dash to Dock and other docks that speak it).
 */
object TaskbarBadge {
    private const val LINUX_DESKTOP_FILE = "mailtice.desktop"
    private val overlayDir = File(System.getProperty("java.io.tmpdir"), "mailtice-badge")

    /** The main window's native handle (HWND on Windows), once the window exists; the overlay needs it. */
    @Volatile
    var windowHandle: Long = 0

    /** [description] is what a screen reader says for the Windows overlay. */
    fun show(count: Int, description: String = "") {
        runCatching {
            when (Platform.Current) {
                Platform.Windows -> windows(count, description)
                Platform.MacOS -> mac(count)
                Platform.Linux -> linux(count)
                else -> Unit
            }
        }.onFailure { println("Taskbar badge unavailable: ${it::class.simpleName}") }
    }

    private fun windows(count: Int, description: String) {
        val hwnd = windowHandle
        if (hwnd == 0L || !WindowsOverlayIcon.isAvailable) return
        if (count > 0) {
            val icon = BadgeIcons.overlayIco(overlayDir, count)
            val shown = WindowsOverlayIcon.setIcon(hwnd, TaskbarIconSource.FromFile(icon.absolutePath), description)
            if (!shown) println("Taskbar overlay failed: ${WindowsOverlayIcon.lastError}")
        } else {
            WindowsOverlayIcon.clearIcon(hwnd)
        }
    }

    private fun mac(count: Int) {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)) return
        taskbar.setIconBadge(if (count > 0) count.toString() else null)
    }

    private fun linux(count: Int) {
        if (!LinuxLauncherEntry.isAvailable) return
        val uri = LinuxLauncherEntry.appUri(LINUX_DESKTOP_FILE)
        if (count > 0) LinuxLauncherEntry.setCount(uri, count.toLong(), true) else LinuxLauncherEntry.clearCount(uri)
    }
}
