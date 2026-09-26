package co.abaye.mailtice.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.fileDropTarget(onHover: (Boolean) -> Unit, onFiles: (List<PickedFile>) -> Unit): Modifier = this
