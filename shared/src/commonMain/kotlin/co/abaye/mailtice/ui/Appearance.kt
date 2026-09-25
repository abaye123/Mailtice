package co.abaye.mailtice.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.PaneStyle

/** The measurements one [ListDensity] stands for, so screens never branch on the enum themselves. */
@Immutable
data class DensitySpec(
    /** Vertical padding inside a message row. */
    val rowPadding: Dp,
    /** Sender avatar in the list, which doubles as the row's checkbox. */
    val avatar: Dp,
    /** Lines of preview text under the subject; 0 hides the preview. */
    val snippetLines: Int,
    /** Height of a sidebar entry. */
    val navItem: Dp,
    /** Gap between message rows in the card style. */
    val rowGap: Dp,
)

fun ListDensity.spec(): DensitySpec = when (this) {
    ListDensity.Compact -> DensitySpec(rowPadding = 6.dp, avatar = 28.dp, snippetLines = 0, navItem = 40.dp, rowGap = 0.dp)
    ListDensity.Comfortable -> DensitySpec(rowPadding = 10.dp, avatar = 36.dp, snippetLines = 1, navItem = 48.dp, rowGap = 2.dp)
    ListDensity.Spacious -> DensitySpec(rowPadding = 14.dp, avatar = 40.dp, snippetLines = 2, navItem = 56.dp, rowGap = 4.dp)
}

/** Current list density; provided at the root from the user's settings. */
val LocalDensitySpec = staticCompositionLocalOf { ListDensity.Comfortable.spec() }

/** Current pane style; provided at the root from the user's settings. */
val LocalPaneStyle = staticCompositionLocalOf { PaneStyle.Cards }

/**
 * One pane of the wide layout (sidebar excluded). [rounded] (the card style on a wide window) makes
 * it a rounded surface on the tinted window background; otherwise it is flat and the caller draws
 * the dividers.
 */
@Composable
fun Pane(rounded: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = if (rounded) RoundedCornerShape(24.dp) else RectangleShape
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surface, content = content)
}
