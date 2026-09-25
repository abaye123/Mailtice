package co.abaye.mailtice.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spot illustrations of the empty screens. */
enum class Illustration {
    /** No account yet: an envelope with a letter and a "+" badge. */
    Welcome,

    /** Nothing in the inbox or folder: an empty tray with a check. */
    InboxZero,

    /** The unread filter shows nothing: an opened envelope with a ticked letter. */
    AllRead,

    /** A search that matched nothing: a magnifier over a page. */
    NoResults,

    /** Desktop reader pane with nothing selected: a letter on a stack. */
    SelectMessage,

    /** The first sync is running: an envelope inside turning arrows (animated). */
    Syncing,

    /** Every relevant account is offline: a cloud with a broken link. */
    Offline,

    /** Sign-in continues in the browser: a browser window with a padlock. */
    Browser,

    /** An account was connected: an envelope with a check badge and confetti. */
    Connected,

    /** Sign-in did not complete: a padlock with an "x" badge. */
    SignInFailed,
}

/**
 * Drawn in code rather than shipped as images, so each illustration follows the colour scheme
 * (the "Flag" palette or any accent) in light and dark alike and stays sharp at any size. Drawing
 * happens on a 200x200 grid that is scaled to the requested [size]. The shapes are symmetric
 * enough to read the same in RTL and LTR, so nothing is mirrored.
 */
@Composable
fun EmptyIllustration(illustration: Illustration, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) { IllustrationPalette.of(scheme) }
    val turn = if (illustration == Illustration.Syncing) {
        val transition = rememberInfiniteTransition(label = "sync")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing), RepeatMode.Restart),
            label = "sync-angle",
        )
        angle
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / GRID
        scale(unit, pivot = Offset.Zero) {
            val p = palette
            backdrop(p)
            when (illustration) {
                Illustration.Welcome -> welcome(p)
                Illustration.InboxZero -> inboxZero(p)
                Illustration.AllRead -> allRead(p)
                Illustration.NoResults -> noResults(p)
                Illustration.SelectMessage -> selectMessage(p)
                Illustration.Syncing -> syncing(p, turn)
                Illustration.Offline -> offline(p)
                Illustration.Browser -> browser(p)
                Illustration.Connected -> connected(p)
                Illustration.SignInFailed -> signInFailed(p)
            }
        }
    }
}

private const val GRID = 200f

private class IllustrationPalette(
    val backdrop: Color,
    val paper: Color,
    val ink: Color,
    val onInk: Color,
    val line: Color,
    val soft: Color,
    val accent: Color,
) {
    companion object {
        fun of(c: ColorScheme): IllustrationPalette {
            val dark = c.surface.luminance() < 0.5f
            return IllustrationPalette(
                backdrop = c.primaryContainer.copy(alpha = if (dark) 0.35f else 0.6f),
                paper = if (dark) c.surfaceContainerHighest else c.surfaceContainerLowest,
                ink = c.primary,
                onInk = c.onPrimary,
                line = c.outlineVariant,
                soft = c.secondaryContainer,
                // A warm counterpoint to the blue. Light schemes whose tertiary container is vivid
                // (the Flag palette) use it; pale containers would vanish on white, so tertiary.
                accent = when {
                    dark -> c.tertiary
                    c.tertiaryContainer.luminance() < 0.3f -> c.tertiaryContainer
                    else -> c.tertiary
                },
            )
        }
    }
}

// ---- building blocks (all coordinates on the 200x200 grid) --------------------------------------

private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.backdrop(p: IllustrationPalette) {
    drawCircle(p.backdrop, radius = 84f, center = Offset(100f, 104f))
}

private fun DrawScope.card(
    p: IllustrationPalette,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    border: Color,
    fill: Color = p.paper,
) {
    val topLeft = Offset(left, top)
    val size = Size(right - left, bottom - top)
    drawRoundRect(fill, topLeft, size, CornerRadius(10f))
    drawRoundRect(border, topLeft, size, CornerRadius(10f), style = stroke(4f))
}

private fun DrawScope.textLine(color: Color, x1: Float, x2: Float, y: Float, width: Float = 5f) {
    drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.polyline(color: Color, width: Float, vararg points: Float, fill: Color? = null) {
    val path = Path().apply {
        moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            lineTo(points[i], points[i + 1])
            i += 2
        }
        if (fill != null) close()
    }
    if (fill != null) drawPath(path, fill)
    drawPath(path, color, style = stroke(width))
}

/** A four-pointed sparkle. */
private fun DrawScope.sparkle(color: Color, cx: Float, cy: Float, r: Float) {
    val w = r * 0.3f
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx + w * 0.4f, cy - w * 0.4f, cx + r, cy)
        quadraticTo(cx + w * 0.4f, cy + w * 0.4f, cx, cy + r)
        quadraticTo(cx - w * 0.4f, cy + w * 0.4f, cx - r, cy)
        quadraticTo(cx - w * 0.4f, cy - w * 0.4f, cx, cy - r)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.badge(p: IllustrationPalette, cx: Float, cy: Float, r: Float = 20f) {
    drawCircle(p.paper, radius = r + 4f, center = Offset(cx, cy))
    drawCircle(p.ink, radius = r, center = Offset(cx, cy))
}

private fun DrawScope.check(color: Color, cx: Float, cy: Float, s: Float, width: Float) {
    polyline(color, width, cx - s, cy, cx - s * 0.3f, cy + s * 0.7f, cx + s, cy - s * 0.7f)
}

// ---- the illustrations --------------------------------------------------------------------------

private fun DrawScope.welcome(p: IllustrationPalette) {
    // Letter peeking out of the envelope.
    card(p, 58f, 42f, 142f, 124f, border = p.line)
    textLine(p.ink, 72f, 110f, 60f, width = 6f)
    textLine(p.line, 72f, 128f, 76f)
    textLine(p.line, 72f, 120f, 90f)
    // Envelope front, drawn over the letter.
    polyline(p.ink, 4f, 40f, 90f, 40f, 152f, 160f, 152f, 160f, 90f, fill = p.paper)
    polyline(p.ink, 4f, 40f, 90f, 100f, 128f, 160f, 90f)
    // "+" badge.
    badge(p, 154f, 148f)
    textLine(p.onInk, 146f, 162f, 148f, width = 4.5f)
    drawLine(p.onInk, Offset(154f, 140f), Offset(154f, 156f), strokeWidth = 4.5f, cap = StrokeCap.Round)
    sparkle(p.accent, 36f, 52f, 10f)
    sparkle(p.ink.copy(alpha = 0.5f), 168f, 62f, 6f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(28f, 124f))
}

private fun DrawScope.inboxZero(p: IllustrationPalette) {
    // Tray.
    polyline(p.ink, 4f, 44f, 118f, 60f, 98f, 140f, 98f, 156f, 118f, 156f, 150f, 44f, 150f, fill = p.paper)
    polyline(p.ink, 4f, 44f, 118f, 78f, 118f, 86f, 130f, 114f, 130f, 122f, 118f, 156f, 118f)
    // Check badge floating above it.
    badge(p, 100f, 66f, r = 22f)
    check(p.onInk, 100f, 67f, 10f, 5f)
    sparkle(p.accent, 56f, 60f, 9f)
    sparkle(p.ink.copy(alpha = 0.5f), 146f, 50f, 6f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(154f, 82f))
    drawCircle(p.line, radius = 3f, center = Offset(40f, 90f))
}

private fun DrawScope.allRead(p: IllustrationPalette) {
    // Open flap behind the letter.
    polyline(p.ink, 4f, 44f, 100f, 100f, 58f, 156f, 100f, fill = p.soft)
    card(p, 62f, 62f, 138f, 128f, border = p.line)
    check(p.ink, 100f, 92f, 16f, 6f)
    // Envelope front pocket.
    polyline(p.ink, 4f, 44f, 100f, 100f, 136f, 156f, 100f, 156f, 156f, 44f, 156f, fill = p.paper)
    polyline(p.line, 3f, 44f, 156f, 90f, 126f)
    polyline(p.line, 3f, 156f, 156f, 110f, 126f)
    sparkle(p.accent, 40f, 64f, 9f)
    sparkle(p.accent, 164f, 70f, 6f)
    drawCircle(p.ink.copy(alpha = 0.5f), radius = 3.5f, center = Offset(158f, 44f))
}

private fun DrawScope.noResults(p: IllustrationPalette) {
    // Page.
    card(p, 46f, 44f, 126f, 150f, border = p.line)
    textLine(p.ink, 60f, 98f, 62f, width = 6f)
    textLine(p.line, 60f, 112f, 78f)
    textLine(p.line, 60f, 104f, 92f)
    textLine(p.line, 60f, 110f, 106f)
    textLine(p.line, 60f, 92f, 120f)
    // Magnifier with an "x" in the lens.
    val lens = Offset(126f, 110f)
    drawCircle(p.paper.copy(alpha = 0.92f), radius = 30f, center = lens)
    drawCircle(p.ink, radius = 30f, center = lens, style = stroke(7f))
    drawLine(p.ink, Offset(148f, 132f), Offset(166f, 150f), strokeWidth = 11f, cap = StrokeCap.Round)
    drawLine(p.accent, Offset(116f, 100f), Offset(136f, 120f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(p.accent, Offset(136f, 100f), Offset(116f, 120f), strokeWidth = 5f, cap = StrokeCap.Round)
    sparkle(p.ink.copy(alpha = 0.5f), 158f, 56f, 7f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(34f, 128f))
}

private fun DrawScope.selectMessage(p: IllustrationPalette) {
    // A second message behind, then the open letter.
    card(p, 70f, 44f, 154f, 118f, border = p.soft, fill = p.soft)
    card(p, 46f, 62f, 134f, 150f, border = p.ink)
    drawCircle(p.ink, radius = 9f, center = Offset(66f, 84f))
    textLine(p.ink, 82f, 118f, 80f, width = 5f)
    textLine(p.line, 82f, 106f, 91f, width = 4f)
    textLine(p.line, 60f, 120f, 110f)
    textLine(p.line, 60f, 114f, 123f)
    textLine(p.line, 60f, 100f, 136f)
    sparkle(p.accent, 156f, 138f, 10f)
    sparkle(p.ink.copy(alpha = 0.5f), 38f, 48f, 6f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(166f, 100f))
}

private fun DrawScope.syncing(p: IllustrationPalette, turn: Float) {
    // Small envelope in the middle.
    polyline(p.ink, 4f, 66f, 84f, 134f, 84f, 134f, 128f, 66f, 128f, fill = p.paper)
    polyline(p.ink, 4f, 66f, 86f, 100f, 110f, 134f, 86f)
    // Two chasing arrows around it.
    rotate(turn, pivot = Offset(100f, 106f)) {
        val arc = Size(116f, 116f)
        val topLeft = Offset(42f, 48f)
        drawArc(p.ink, startAngle = 200f, sweepAngle = 115f, useCenter = false, topLeft = topLeft, size = arc, style = stroke(7f))
        drawArc(p.accent, startAngle = 20f, sweepAngle = 115f, useCenter = false, topLeft = topLeft, size = arc, style = stroke(7f))
        // Arrow heads at the end of each arc (315 and 135 degrees on a circle of radius 58).
        polyline(p.ink, 6f, 141.6f, 52.8f, 143f, 67f, 128.8f, 65.6f)
        polyline(p.accent, 6f, 58.4f, 159.2f, 57f, 145f, 71.2f, 146.4f)
    }
    drawCircle(p.line, radius = 3f, center = Offset(34f, 60f))
    sparkle(p.ink.copy(alpha = 0.5f), 170f, 150f, 6f)
}

private fun DrawScope.offline(p: IllustrationPalette) {
    // Cloud.
    val cloud = Path().apply {
        moveTo(62f, 128f)
        cubicTo(42f, 128f, 38f, 100f, 58f, 96f)
        cubicTo(58f, 74f, 86f, 64f, 100f, 80f)
        cubicTo(110f, 62f, 142f, 66f, 142f, 90f)
        cubicTo(164f, 90f, 166f, 128f, 142f, 128f)
        close()
    }
    drawPath(cloud, p.paper)
    drawPath(cloud, p.ink, style = stroke(4f))
    // Broken link hanging below.
    drawLine(p.ink, Offset(100f, 132f), Offset(100f, 142f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(p.ink, Offset(100f, 156f), Offset(100f, 166f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(p.accent, Offset(88f, 146f), Offset(78f, 142f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(p.accent, Offset(112f, 152f), Offset(122f, 156f), strokeWidth = 4f, cap = StrokeCap.Round)
    // "Z z" for a server that is asleep.
    polyline(p.line, 4f, 90f, 98f, 104f, 98f, 90f, 112f, 104f, 112f)
    polyline(p.line, 3.5f, 112f, 90f, 120f, 90f, 112f, 98f, 120f, 98f)
    sparkle(p.accent, 162f, 60f, 8f)
    drawCircle(p.ink.copy(alpha = 0.5f), radius = 3.5f, center = Offset(36f, 70f))
}

/** A padlock centred on ([cx], [cy]), body [w] wide. */
private fun DrawScope.padlock(p: IllustrationPalette, cx: Float, cy: Float, w: Float) {
    val h = w * 0.78f
    drawArc(
        p.ink, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(cx - w * 0.3f, cy - h * 0.5f - w * 0.34f), size = Size(w * 0.6f, w * 0.68f), style = stroke(w * 0.11f),
    )
    drawRoundRect(p.ink, Offset(cx - w / 2, cy - h * 0.5f), Size(w, h), CornerRadius(w * 0.16f))
    drawCircle(p.onInk, radius = w * 0.1f, center = Offset(cx, cy - h * 0.06f))
    drawLine(p.onInk, Offset(cx, cy - h * 0.06f), Offset(cx, cy + h * 0.2f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
}

private fun DrawScope.browser(p: IllustrationPalette) {
    // Window with its title bar and three dots.
    drawRoundRect(p.paper, Offset(38f, 56f), Size(124f, 94f), CornerRadius(12f))
    drawRoundRect(p.soft, Offset(38f, 56f), Size(124f, 20f), CornerRadius(12f))
    drawRoundRect(p.ink, Offset(38f, 56f), Size(124f, 94f), CornerRadius(12f), style = stroke(4f))
    drawLine(p.ink, Offset(38f, 76f), Offset(162f, 76f), strokeWidth = 3f)
    listOf(52f, 62f, 72f).forEach { drawCircle(p.ink, radius = 3f, center = Offset(it, 66f)) }
    // The address bar and the sign-in form lines.
    drawRoundRect(p.line, Offset(84f, 61f), Size(64f, 10f), CornerRadius(5f))
    padlock(p, 100f, 112f, 34f)
    textLine(p.line, 70f, 130f, 140f, width = 4f)
    sparkle(p.accent, 34f, 48f, 9f)
    sparkle(p.ink.copy(alpha = 0.5f), 168f, 150f, 6f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(170f, 60f))
}

private fun DrawScope.connected(p: IllustrationPalette) {
    polyline(p.ink, 4f, 46f, 80f, 46f, 148f, 154f, 148f, 154f, 80f, fill = p.paper)
    polyline(p.ink, 4f, 46f, 82f, 100f, 120f, 154f, 82f)
    badge(p, 150f, 76f, r = 22f)
    check(p.onInk, 150f, 77f, 10f, 5f)
    // Confetti.
    sparkle(p.accent, 40f, 56f, 10f)
    sparkle(p.ink.copy(alpha = 0.55f), 92f, 50f, 7f)
    drawCircle(p.accent, radius = 4f, center = Offset(118f, 44f))
    drawCircle(p.ink.copy(alpha = 0.5f), radius = 3f, center = Offset(34f, 120f))
    drawLine(p.accent, Offset(168f, 118f), Offset(176f, 126f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(p.ink.copy(alpha = 0.5f), Offset(60f, 164f), Offset(70f, 160f), strokeWidth = 4f, cap = StrokeCap.Round)
}

private fun DrawScope.signInFailed(p: IllustrationPalette) {
    drawCircle(p.paper, radius = 46f, center = Offset(96f, 108f))
    drawCircle(p.line, radius = 46f, center = Offset(96f, 108f), style = stroke(3f))
    padlock(p, 96f, 116f, 44f)
    drawCircle(p.paper, radius = 22f, center = Offset(142f, 70f))
    drawCircle(p.accent, radius = 18f, center = Offset(142f, 70f))
    drawLine(p.onInk, Offset(135f, 63f), Offset(149f, 77f), strokeWidth = 4.5f, cap = StrokeCap.Round)
    drawLine(p.onInk, Offset(149f, 63f), Offset(135f, 77f), strokeWidth = 4.5f, cap = StrokeCap.Round)
    sparkle(p.ink.copy(alpha = 0.5f), 40f, 60f, 7f)
    drawCircle(p.accent, radius = 3.5f, center = Offset(40f, 150f))
}
