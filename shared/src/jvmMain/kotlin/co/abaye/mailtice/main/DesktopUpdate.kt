package co.abaye.mailtice.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.update_available
import mailtice.shared.generated.resources.update_restart_body
import mailtice.shared.generated.resources.update_restart_now
import mailtice.shared.generated.resources.update_restart_title
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val UPDATE_OWNER = "abaye123"
private const val UPDATE_REPO = "Mailtice"

/**
 * What the window chrome needs to know about a pending update. Held by the host rather than by
 * [AppViewModel] because it is desktop-only and dies with the window: Android updates through the
 * Play Store and the browser has nothing to install.
 */
class DesktopUpdate internal constructor(
    val ready: Boolean,
    val showDialog: Boolean,
    val onIconClick: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onRestartNow: () -> Unit,
    val installOnExit: () -> Unit,
)

/**
 * Checks GitHub Releases once per launch and downloads the installer in the background. Nothing is
 * installed without the user: [DesktopUpdate.installOnExit] runs on quit, which is the quiet path,
 * and the icon offers the impatient one.
 *
 * `isUpdateSupported()` is false when the app runs from source or from an unpackaged build, which
 * is the whole of `./gradlew :desktopApp:run` - there is no installer on disk to replace.
 */
@Composable
fun rememberDesktopUpdate(): DesktopUpdate {
    val updater = remember {
        NucleusUpdater {
            provider = GitHubProvider(owner = UPDATE_OWNER, repo = UPDATE_REPO)
            // The JDK ignores the machine's own certificate store, so on a filtered line every
            // release check would fail the handshake. Same reason as HttpClientFactory.jvm.kt.
            httpClient = NativeHttpClient.create()
        }
    }
    var file by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updater) {
        if (!updater.isUpdateSupported()) return@LaunchedEffect
        // A failed check is not worth a message: the app works, and the next launch tries again.
        val result = runCatching { updater.checkForUpdates() }.getOrNull()
        if (result !is UpdateResult.Available) return@LaunchedEffect
        runCatching {
            updater.downloadUpdate(result.info).collect { progress ->
                progress.file?.let { file = it }
            }
        }
    }

    val ready = file != null
    return DesktopUpdate(
        ready = ready,
        showDialog = showDialog,
        onIconClick = { if (ready) showDialog = true },
        onDismissDialog = { showDialog = false },
        onRestartNow = { file?.let(updater::installAndRestart) },
        installOnExit = { file?.let(updater::installAndQuit) },
    )
}

/**
 * Appears in the title bar only once an installer is on disk, so it never advertises work the user
 * would then have to wait for.
 */
@Composable
fun UpdateButton(update: DesktopUpdate, modifier: Modifier = Modifier) {
    if (!update.ready) return
    val label = stringResource(Res.string.update_available)
    val tooltip = rememberTooltipState(isPersistent = true)
    // Shown once, unprompted: an icon that quietly appears in the chrome is an icon nobody notices.
    LaunchedEffect(Unit) { tooltip.show() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        modifier = modifier,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = update.onIconClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SystemUpdate, label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Matches [co.abaye.mailtice.ui.AppDialogHost] rather than the template's hand-drawn sheet. */
@Composable
fun UpdateRestartDialog(update: DesktopUpdate) {
    if (!update.showDialog) return
    AlertDialog(
        onDismissRequest = update.onDismissDialog,
        title = { Text(stringResource(Res.string.update_restart_title)) },
        text = { Text(stringResource(Res.string.update_restart_body)) },
        confirmButton = {
            TextButton(onClick = update.onRestartNow) {
                Text(stringResource(Res.string.update_restart_now))
            }
        },
        dismissButton = {
            TextButton(onClick = update.onDismissDialog) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
    )
}
