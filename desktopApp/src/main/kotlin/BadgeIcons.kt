import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The unread count drawn as a badge: a red disc with the number in white, the way mail apps mark
 * their icons. Drawn with Skia, so it looks the same on every desktop.
 */
object BadgeIcons {
    private const val BADGE_RED = 0xFFD93025.toInt()
    private const val TRAY_SIZE = 64

    private val typeface by lazy { FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD) }

    /** What the badge says: the count, or "99+" past that. */
    fun label(count: Int): String = if (count > 99) "99+" else count.toString()

    /** The tray icon: [base] with the badge over its bottom corner; [base] itself when nothing is unread. */
    fun trayIcon(base: ImageBitmap, count: Int): ImageBitmap {
        if (count <= 0) return base
        val surface = Surface.makeRasterN32Premul(TRAY_SIZE, TRAY_SIZE)
        val canvas = surface.canvas
        canvas.drawImageRect(Image.makeFromBitmap(base.asSkiaBitmap()), Rect.makeWH(TRAY_SIZE.toFloat(), TRAY_SIZE.toFloat()))
        // Large enough to read in a 16 px tray slot: over half the icon, anchored to its corner.
        val diameter = TRAY_SIZE * 0.62f
        drawBadge(canvas, count, left = TRAY_SIZE - diameter, top = TRAY_SIZE - diameter, diameter = diameter, ring = true)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    /**
     * A Windows .ico holding the badge alone at 16 and 32 px (PNG entries, which Windows reads since
     * Vista), for the taskbar button's overlay. Written to [dir], one file per label.
     */
    fun overlayIco(dir: File, count: Int): File {
        val file = File(dir, "badge-${label(count).replace("+", "plus")}.ico")
        if (file.isFile) return file
        dir.mkdirs()
        val images = listOf(16, 32).map { size -> size to badgePng(count, size) }
        val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = header.capacity()
        images.forEach { (size, png) ->
            header.put(size.toByte()).put(size.toByte()).put(0).put(0)
            header.putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        val out = ByteArrayOutputStream()
        out.write(header.array())
        images.forEach { out.write(it.second) }
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun badgePng(count: Int, size: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(size, size)
        drawBadge(surface.canvas, count, left = 0f, top = 0f, diameter = size.toFloat(), ring = false)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun drawBadge(canvas: org.jetbrains.skia.Canvas, count: Int, left: Float, top: Float, diameter: Float, ring: Boolean) {
        val radius = diameter / 2
        val cx = left + radius
        val cy = top + radius
        canvas.drawCircle(
            cx,
            cy,
            radius,
            Paint().apply {
                color = BADGE_RED
                isAntiAlias = true
            },
        )
        if (ring) {
            // A thin white edge keeps the disc apart from the icon under it.
            canvas.drawCircle(
                cx,
                cy,
                radius - diameter * 0.03f,
                Paint().apply {
                    color = Color.WHITE
                    mode = PaintMode.STROKE
                    strokeWidth = diameter * 0.06f
                    isAntiAlias = true
                },
            )
        }
        val text = label(count)
        // Two digits fill the disc; three characters ("99+") need a smaller size to fit.
        val size = diameter * when (text.length) {
            1 -> 0.66f
            2 -> 0.56f
            else -> 0.42f
        }
        val line = TextLine.make(text, Font(typeface, size))
        val white = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawTextLine(line, cx - line.width / 2, cy - (line.ascent + line.descent) / 2, white)
    }

    private fun Image.toComposeImageBitmap(): ImageBitmap = org.jetbrains.skia.Bitmap.makeFromImage(this).asComposeImageBitmap()
}
