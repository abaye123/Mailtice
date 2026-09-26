package co.abaye.mailtice.platform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.fileDropTarget(onHover: (Boolean) -> Unit, onFiles: (List<PickedFile>) -> Unit): Modifier {
    val hover = rememberUpdatedState(onHover)
    val files = rememberUpdatedState(onFiles)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hover.value(true)

            override fun onExited(event: DragAndDropEvent) = hover.value(false)

            override fun onEnded(event: DragAndDropEvent) = hover.value(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover.value(false)
                val dropped = droppedFiles(event)
                if (dropped.isEmpty()) return false
                files.value(dropped.mapNotNull { Platform.readPicked(it) })
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { droppedFiles(it).isNotEmpty() || isFileDrag(it) }, target = target)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun isFileDrag(event: DragAndDropEvent): Boolean =
    runCatching { event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }.getOrDefault(false)

@OptIn(ExperimentalComposeUiApi::class)
private fun droppedFiles(event: DragAndDropEvent): List<File> = runCatching {
    val t = event.awtTransferable
    if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    @Suppress("UNCHECKED_CAST")
    (t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>).filter { it.isFile }
}.getOrDefault(emptyList())
