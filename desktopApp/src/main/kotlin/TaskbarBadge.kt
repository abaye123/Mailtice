import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.launcher.linux.LinuxLauncherEntry
import dev.nucleusframework.launcher.windows.WindowsBadgeManager
import java.awt.Taskbar

/**
 * The unread count on the app's taskbar / dock / launcher icon. Every OS call is best effort: a
 * desktop without badge support (or a dev run the OS does not know as an installed app) simply
 * shows no number.
 *
 * - Windows: a badge notification on the taskbar button (needs the installed app's AUMID, so it
 *   stays empty in a plain `run` from source).
 * - macOS: the Dock tile badge.
 * - Linux: the Unity launcher count (KDE, Dash to Dock and other docks that speak it).
 */
object TaskbarBadge {
    private const val LINUX_DESKTOP_FILE = "mailtice.desktop"
    private var windowsReady: Boolean? = null

    fun show(count: Int) {
        runCatching {
            when (Platform.Current) {
                Platform.Windows -> windows(count)
                Platform.MacOS -> mac(count)
                Platform.Linux -> linux(count)
                else -> Unit
            }
        }.onFailure { println("Taskbar badge unavailable: ${it::class.simpleName}") }
    }

    private fun windows(count: Int) {
        val ready = windowsReady ?: (WindowsBadgeManager.isAvailable && WindowsBadgeManager.initialize()).also { windowsReady = it }
        if (!ready) return
        if (count > 0) WindowsBadgeManager.setCount(count) else WindowsBadgeManager.clear()
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
