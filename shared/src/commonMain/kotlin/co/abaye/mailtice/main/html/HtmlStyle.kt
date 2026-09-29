package co.abaye.mailtice.main.html

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/** An element's inline `style`, property names lowercased, later declarations winning. */
fun styleOf(element: HtmlElement): Map<String, String> {
    val raw = element.attr("style") ?: return emptyMap()
    return raw.split(';').mapNotNull { decl ->
        val i = decl.indexOf(':')
        if (i <= 0) null else decl.substring(0, i).trim().lowercase() to decl.substring(i + 1).replace("!important", "").trim()
    }.toMap()
}

/** A length in CSS pixels (dp): px, pt, em (of [emBase]) or a bare number; null for %, auto and the rest. */
fun cssLength(value: String?, emBase: Float = 15f): Float? {
    val v = value?.trim()?.lowercase() ?: return null
    return when {
        v.endsWith("px") -> v.removeSuffix("px").trim().toFloatOrNull()
        v.endsWith("pt") -> v.removeSuffix("pt").trim().toFloatOrNull()?.times(4f / 3f)
        v.endsWith("rem") -> v.removeSuffix("rem").trim().toFloatOrNull()?.times(15f)
        v.endsWith("em") -> v.removeSuffix("em").trim().toFloatOrNull()?.times(emBase)
        else -> v.toFloatOrNull()
    }
}

/** A percentage ("50%") as a fraction of 100; null when not one. */
fun cssPercent(value: String?): Float? = value?.trim()?.takeIf { it.endsWith("%") }?.removeSuffix("%")?.trim()?.toFloatOrNull()

/** The four sides of a CSS box shorthand (padding, margin), in dp; missing sides as CSS fills them. */
data class Sides(val top: Float, val end: Float, val bottom: Float, val start: Float) {
    val isZero: Boolean get() = top == 0f && end == 0f && bottom == 0f && start == 0f

    companion object {
        val Zero = Sides(0f, 0f, 0f, 0f)

        fun parse(value: String?, emBase: Float = 15f): Sides? {
            val parts = value?.trim()?.split(Regex("\\s+"))?.map { cssLength(it, emBase) ?: 0f } ?: return null
            return when (parts.size) {
                1 -> Sides(parts[0], parts[0], parts[0], parts[0])
                2 -> Sides(parts[0], parts[1], parts[0], parts[1])
                3 -> Sides(parts[0], parts[1], parts[2], parts[1])
                4 -> Sides(parts[0], parts[1], parts[2], parts[3])
                else -> null
            }
        }
    }
}

/** A box shorthand with its per-side properties over it ("padding" then "padding-top"...). */
fun boxSides(css: Map<String, String>, name: String, emBase: Float = 15f): Sides? {
    val base = Sides.parse(css[name], emBase)
    val top = cssLength(css["$name-top"], emBase)
    val right = cssLength(css["$name-right"], emBase)
    val bottom = cssLength(css["$name-bottom"], emBase)
    val left = cssLength(css["$name-left"], emBase)
    if (base == null && top == null && right == null && bottom == null && left == null) return null
    val b = base ?: Sides.Zero
    return Sides(top ?: b.top, right ?: b.end, bottom ?: b.bottom, left ?: b.start)
}

private val Named = mapOf(
    "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
    "gray" to 0xFF808080, "grey" to 0xFF808080, "silver" to 0xFFC0C0C0, "maroon" to 0xFF800000, "navy" to 0xFF000080,
    "orange" to 0xFFFFA500, "purple" to 0xFF800080, "teal" to 0xFF008080, "yellow" to 0xFFFFFF00, "lightgray" to 0xFFD3D3D3,
    "lightgrey" to 0xFFD3D3D3, "darkgray" to 0xFFA9A9A9, "darkgrey" to 0xFFA9A9A9, "whitesmoke" to 0xFFF5F5F5,
)
private val RgbFunction = Regex("rgba?\\(([^)]*)\\)", RegexOption.IGNORE_CASE)

/** A CSS colour (#rgb, #rrggbb, #rrggbbaa, rgb(), rgba(), basic names); null for transparent and the unknown. */
fun cssColor(value: String?): Color? {
    val v = value?.trim()?.lowercase()?.substringBefore(' ') ?: return null
    if (v.startsWith("#")) {
        val hex = v.drop(1)
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("") + "ff"
            6 -> hex + "ff"
            8 -> hex
            else -> return null
        }
        val argb = full.toLongOrNull(16) ?: return null
        val rgb = argb shr 8
        return Color(((argb and 0xFF) shl 24) or rgb)
    }
    RgbFunction.find(value.trim())?.let { m ->
        val parts = m.groupValues[1].split(',', ' ', '/').filter { it.isNotBlank() }
        val channels = parts.take(3).map { p -> cssPercent(p)?.let { it * 2.55f } ?: p.trim().toFloatOrNull() ?: return null }
        if (channels.size < 3) return null
        val alpha = parts.getOrNull(3)?.let { a -> cssPercent(a)?.div(100f) ?: a.trim().toFloatOrNull() } ?: 1f
        if (alpha <= 0f) return null
        return Color(channels[0] / 255f, channels[1] / 255f, channels[2] / 255f, alpha.coerceIn(0f, 1f))
    }
    return Named[v]?.let { Color(it) }
}

/** `text-align` or the `align` attribute as a Compose alignment; null when not set. */
fun cssAlign(value: String?): TextAlign? = when (value?.trim()?.lowercase()) {
    "center", "middle" -> TextAlign.Center
    "right" -> TextAlign.Right
    "left" -> TextAlign.Left
    "justify" -> TextAlign.Justify
    else -> null
}

/**
 * Hidden the ways mail hides things: display none, invisible, zero size with the overflow cut
 * (the preheader line newsletters put before the visible mail), or Outlook's own switch.
 */
fun isHidden(element: HtmlElement, css: Map<String, String>): Boolean {
    if (element.attrs.containsKey("hidden")) return true
    if (css["display"]?.lowercase() == "none" || css["visibility"]?.lowercase() == "hidden") return true
    if (css["mso-hide"]?.lowercase() == "all") return true
    if (css["opacity"]?.trim()?.toFloatOrNull() == 0f) return true
    val cut = css["overflow"]?.lowercase() == "hidden"
    val zero = listOf("max-height", "height", "max-width", "width", "font-size").any { cssLength(css[it]) == 0f }
    return (cut && zero) || cssLength(css["font-size"]) == 0f
}
