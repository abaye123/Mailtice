package co.abaye.mailtice.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlin.math.ceil

private const val TURN_MS = 900
private const val SETTLE_MS = 300

/**
 * An icon's rotation while [active] (a sync running): full turns one after another, and when it
 * stops, the turn under way finishes instead of snapping back. Read it in a graphics layer
 * (`Modifier.graphicsLayer { rotationZ = spin.value }`), so only the layer redraws.
 */
@Composable
fun rememberSpin(active: Boolean): State<Float> {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(TURN_MS, easing = LinearEasing))
        } else if (rotation.value % 360f != 0f) {
            rotation.animateTo(ceil(rotation.value / 360f) * 360f, tween(SETTLE_MS))
        }
    }
    return rotation.asState()
}
