package co.abaye.mailtice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppMessage
import co.abaye.mailtice.app.text
import kotlinx.coroutines.delay
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.dialog_dismiss
import org.jetbrains.compose.resources.stringResource

private const val AUTO_DISMISS_MS = 5_000L

@Composable
fun MessageBar(message: AppMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The effect restarts on the message, not on the callback, so calling the parameter directly
    // would run whichever one this composable was born with - stale the moment RootScreen hands
    // down a new lambda while a message is on screen. rememberUpdatedState keeps the countdown
    // running and still dismisses through the current one.
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            delay(AUTO_DISMISS_MS)
            dismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val shown = message
        // As wide as its text needs, between a minimum that keeps short notices from looking
        // cramped and a maximum past which it wraps - never the whole width of the window.
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(min = 280.dp, max = 560.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Weighted without filling: a long sentence wraps on word boundaries and leaves the
                // button its room, a short one keeps the toast as narrow as the text.
                Text(
                    shown?.text().orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.dialog_dismiss),
                        color = MaterialTheme.colorScheme.inversePrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
