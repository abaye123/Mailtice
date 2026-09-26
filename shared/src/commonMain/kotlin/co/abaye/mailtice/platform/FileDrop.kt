package co.abaye.mailtice.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Makes the element accept files dragged from the OS (Explorer, Finder, the desktop). [onHover]
 * reports a drag entering and leaving, for a highlight; [onFiles] gets what was dropped.
 * A no-op where there is nothing to drag from (Android).
 */
@Composable
internal expect fun Modifier.fileDropTarget(onHover: (Boolean) -> Unit, onFiles: (List<PickedFile>) -> Unit): Modifier
