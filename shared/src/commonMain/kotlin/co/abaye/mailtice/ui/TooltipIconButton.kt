package co.abaye.mailtice.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every icon-only control goes through here: the [label] is both the hover tooltip (desktop, long
 * press on touch) and the accessibility description, so an icon never appears without a name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    Tooltip(label, modifier) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, modifier = iconModifier, tint = tint)
        }
    }
}

/** A plain tooltip above any [content]; for icon buttons use [TooltipIconButton]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tooltip(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
