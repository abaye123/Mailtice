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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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

/** How often a running app looks for a new release: it can sit in the tray for days. */
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

/**
 * The desktop self-update: GitHub Releases checked at launch and then every [CHECK_INTERVAL_MS], or
 * on demand from the settings; a new release downloads in the background. Nothing installs without
 * the user: [installOnExit] runs on quit, which is the quiet path, and the title-bar icon or the
 * settings offer a restart for the impatient.
 *
 * Held by the host rather than by the view model because it is desktop-only and dies with the
 * window; the shared settings reach it through [LocalAppUpdates].
 */
@Stable
class DesktopUpdate internal constructor(
    private val updater: NucleusUpdater,
    // The window's composition scope: a check started from the settings ends with the window.
    @StructuredScope private val scope: CoroutineScope,
) : AppUpdates {
    /** False when the app runs from source or an unpackaged build: there is no installer to replace. */
    private val supported = runCatching { updater.isUpdateSupported() }.getOrDefault(false)
    private val running = Mutex()
    private var file by mutableStateOf<File?>(null)
    private var fileVersion: String? = null

    override val currentVersion: String = runCatching { updater.currentVersion }.getOrDefault("")

    override var status: UpdateStatus by mutableStateOf(if (supported) UpdateStatus.Idle else UpdateStatus.Unsupported)
        private set

    val ready: Boolean get() = file != null

    var showDialog: Boolean by mutableStateOf(false)
        private set

    fun onIconClick() {
        if (ready) showDialog = true
    }

    fun onDismissDialog() {
        showDialog = false
    }

    fun installOnExit() {
        file?.let(updater::installAndQuit)
    }

    override fun restartNow() {
        file?.let(updater::installAndRestart)
    }

    override fun checkNow() {
        scope.launch { check() }
    }

    /** One check (and download, when there is something new); skipped while another is running. */
    internal suspend fun check() {
        if (!supported || !running.tryLock()) return
        try {
            status = UpdateStatus.Checking
            // A failed check is not worth a message of its own: the settings show it, and the next round tries again.
            val result = runCatching { updater.checkForUpdates() }.getOrNull()
            status = when (result) {
                is UpdateResult.Available -> download(result.info)
                UpdateResult.NotAvailable -> UpdateStatus.UpToDate(System.currentTimeMillis())
                else -> fileVersion?.let { UpdateStatus.Ready(it) } ?: UpdateStatus.Failed
            }
        } finally {
            running.unlock()
        }
    }

    private suspend fun download(info: UpdateInfo): UpdateStatus {
        val version = info.version
        // Already on disk from an earlier round: nothing to fetch again.
        if (file != null && fileVersion == version) return UpdateStatus.Ready(version)
        status = UpdateStatus.Downloading(version, 0)
        val done = runCatching {
            updater.downloadUpdate(info).collect { progress ->
                status = UpdateStatus.Downloading(version, progress.percent.toInt().coerceIn(0, 100))
                progress.file?.let {
                    file = it
                    fileVersion = version
                }
            }
        }.isSuccess
        return if (done && fileVersion == version) UpdateStatus.Ready(version) else UpdateStatus.Failed
    }
}

@Composable
fun rememberDesktopUpdate(): DesktopUpdate {
    val scope = rememberCoroutineScope()
    val update = remember {
        DesktopUpdate(
            NucleusUpdater {
                provider = GitHubProvider(owner = UPDATE_OWNER, repo = UPDATE_REPO)
                // The JDK ignores the machine's own certificate store, so on a filtered line every
                // release check would fail the handshake. Same reason as HttpClientFactory.jvm.kt.
                httpClient = NativeHttpClient.create()
            },
            scope,
        )
    }
    LaunchedEffect(update) {
        while (true) {
            update.check()
            delay(CHECK_INTERVAL_MS)
        }
    }
    return update
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
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = update::onIconClick),
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
        onDismissRequest = update::onDismissDialog,
        title = { Text(stringResource(Res.string.update_restart_title)) },
        text = { Text(stringResource(Res.string.update_restart_body)) },
        confirmButton = {
            TextButton(onClick = update::restartNow) {
                Text(stringResource(Res.string.update_restart_now))
            }
        },
        dismissButton = {
            TextButton(onClick = update::onDismissDialog) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
    )
}
