package co.abaye.mailtice.main

import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/**
 * How the main window was when the app last closed: maximized or not, and its size and place when
 * not (kept while maximized, so un-maximizing comes back to them). Sizes and positions in dp.
 */
data class SavedWindow(val maximized: Boolean, val width: Float, val height: Float, val x: Float?, val y: Float?) {
    /** A place on a screen that is still attached; null (centre it) after a monitor went away. */
    val visibleX: Float? get() = if (x != null && y != null && onScreen(x, y)) x else null
    val visibleY: Float? get() = if (x != null && y != null && onScreen(x, y)) y else null

    private fun onScreen(x: Float, y: Float): Boolean = runCatching {
        // The title bar has to be reachable: its top-left corner, a little way in, on some screen.
        val probe = Rectangle(x.toInt() + GRAB_MARGIN, y.toInt() + GRAB_MARGIN, 1, 1)
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { it.defaultConfiguration.bounds.intersects(probe) }
    }.getOrDefault(true)

    private companion object {
        const val GRAB_MARGIN = 40
    }
}

/** Reads and writes [SavedWindow] as a small file next to the app's other data (desktop only). */
object WindowMemory {
    private const val FILE = "window.properties"
    private const val MIN_SIZE = 320f

    private fun file() = File(joinPath(Platform.appDir(), FILE))

    fun load(): SavedWindow? = runCatching {
        val f = file()
        if (!f.isFile) return null
        val p = Properties().apply { f.reader().use { load(it) } }
        SavedWindow(
            maximized = p.getProperty("maximized").toBoolean(),
            width = p.getProperty("width")?.toFloatOrNull()?.takeIf { it >= MIN_SIZE } ?: return null,
            height = p.getProperty("height")?.toFloatOrNull()?.takeIf { it >= MIN_SIZE } ?: return null,
            x = p.getProperty("x")?.toFloatOrNull(),
            y = p.getProperty("y")?.toFloatOrNull(),
        )
    }.getOrNull()

    fun save(window: SavedWindow) {
        runCatching {
            val p = Properties()
            p.setProperty("maximized", window.maximized.toString())
            p.setProperty("width", window.width.toString())
            p.setProperty("height", window.height.toString())
            window.x?.let { p.setProperty("x", it.toString()) }
            window.y?.let { p.setProperty("y", it.toString()) }
            val target = file()
            // Written aside and moved in, so a crash mid-write never leaves half a file.
            val temp = File(target.parentFile, "$FILE.tmp")
            temp.writer().use { p.store(it, null) }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }.onFailure { println("Window state not saved: ${it::class.simpleName}") }
    }
}
