package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailMessage

/** The provider's "#rrggbb" label colour, or null when it has none (or it does not parse). */
fun Folder.labelColor(): Color? {
    val hex = color.removePrefix("#").takeIf { it.length == 6 } ?: return null
    return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/**
 * The labels worth a chip on a message: the user's own labels and folders. System folders and
 * Gmail's category tabs would put the same chip on nearly every row, so they are left out.
 */
fun AppState.labelsOf(message: MailMessage): List<Folder> {
    if (message.folderIds.isEmpty()) return emptyList()
    val folders = foldersOf(message.accountId).associateBy { it.id }
    return message.folderIds.mapNotNull { folders[it] }
        .filter { it.role == FolderRole.Other && !it.id.startsWith("CATEGORY_") }
        .sortedBy { it.name }
}

/** A small rounded chip in the label's colour; labels without one get a neutral chip with a dot. */
@Composable
fun LabelChip(folder: Folder, modifier: Modifier = Modifier, small: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val tint = folder.labelColor()
    val background = tint?.copy(alpha = 0.18f) ?: colors.surfaceContainerHighest
    // The label colour itself as text is only readable when it is dark enough on the tinted chip.
    val text = when {
        tint == null -> colors.onSurfaceVariant
        tint.luminance() < 0.45f -> tint
        else -> colors.onSurface
    }
    val height: Dp = if (small) 18.dp else 24.dp
    Row(
        modifier.background(background, RoundedCornerShape(height / 2)).padding(horizontal = if (small) 6.dp else 10.dp)
            .widthIn(max = 140.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(if (small) 6.dp else 8.dp).background(tint ?: colors.outline, CircleShape))
        Text(
            folder.name,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Up to [max] chips in a row, then "+n". */
@Composable
fun LabelChips(labels: List<Folder>, modifier: Modifier = Modifier, max: Int = 3, small: Boolean = true) {
    if (labels.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.take(max).forEach { LabelChip(it, small = small) }
        if (labels.size > max) {
            Text(
                "+${labels.size - max}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
