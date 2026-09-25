package co.abaye.mailtice.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Everything an empty screen shows. Actions are optional; the primary one is a filled button. */
@Immutable
data class EmptyContent(
    val illustration: Illustration,
    val title: String,
    val body: String? = null,
    val action: String? = null,
    val secondaryAction: String? = null,
)

/**
 * The one empty-screen layout: illustration, title, a line of explanation and at most two actions,
 * centred and width-limited so it reads well in a narrow list pane and a full window alike.
 * Switching between two empty states cross-fades instead of jumping.
 */
@Composable
fun EmptyState(
    content: EmptyContent,
    modifier: Modifier = Modifier,
    illustrationSize: Dp = 176.dp,
    onAction: () -> Unit = {},
    onSecondaryAction: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            contentAlignment = Alignment.Center,
            label = "empty-state",
        ) { c ->
            Column(
                Modifier.widthIn(max = 380.dp).padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyIllustration(c.illustration, size = illustrationSize)
                Spacer(Modifier.height(20.dp))
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (c.body != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (c.action != null) {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAction) { Text(c.action) }
                }
                if (c.secondaryAction != null) {
                    Spacer(Modifier.height(if (c.action != null) 4.dp else 16.dp))
                    TextButton(onClick = onSecondaryAction) { Text(c.secondaryAction) }
                }
            }
        }
    }
}
