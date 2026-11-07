package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.TransferStatus
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.transfer.TransferError
import co.abaye.mailtice.transfer.TransferFiles
import co.abaye.mailtice.ui.SectionHeader
import co.abaye.mailtice.ui.SettingRow
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.transfer_dismiss
import mailtice.shared.generated.resources.transfer_export
import mailtice.shared.generated.resources.transfer_export_button
import mailtice.shared.generated.resources.transfer_export_desc
import mailtice.shared.generated.resources.transfer_export_failed
import mailtice.shared.generated.resources.transfer_exported
import mailtice.shared.generated.resources.transfer_exporting
import mailtice.shared.generated.resources.transfer_extension
import mailtice.shared.generated.resources.transfer_extension_button
import mailtice.shared.generated.resources.transfer_import
import mailtice.shared.generated.resources.transfer_import_button
import mailtice.shared.generated.resources.transfer_import_desc
import mailtice.shared.generated.resources.transfer_import_failed
import mailtice.shared.generated.resources.transfer_import_title
import mailtice.shared.generated.resources.transfer_imported
import mailtice.shared.generated.resources.transfer_importing
import mailtice.shared.generated.resources.transfer_not_a_file
import mailtice.shared.generated.resources.transfer_show
import mailtice.shared.generated.resources.transfer_title
import mailtice.shared.generated.resources.transfer_too_new
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: carry mail to a computer without internet. Export writes everything stored here to a
 * file; import reads one written by the app elsewhere or by the browser extension.
 */
@Composable
internal fun TransferSection(status: TransferStatus, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHeader(stringResource(Res.string.transfer_title))
        val running = status is TransferStatus.Running
        SettingRow(stringResource(Res.string.transfer_export), subtitle = stringResource(Res.string.transfer_export_desc)) {
            OutlinedButton(onClick = { onIntent(AppIntent.ExportMail) }, enabled = !running) {
                Text(stringResource(Res.string.transfer_export_button))
            }
        }
        if (TransferFiles.canImport) {
            val title = stringResource(Res.string.transfer_import_title)
            SettingRow(stringResource(Res.string.transfer_import), subtitle = stringResource(Res.string.transfer_import_desc)) {
                OutlinedButton(onClick = { onIntent(AppIntent.ImportMail(title)) }, enabled = !running) {
                    Text(stringResource(Res.string.transfer_import_button))
                }
            }
        }
        SettingRow(stringResource(Res.string.transfer_extension)) {
            TextButton(onClick = { Platform.openUrl(RELEASES_URL) }) { Text(stringResource(Res.string.transfer_extension_button)) }
        }
        if (status != TransferStatus.Idle) TransferStatusCard(status, onIntent)
    }
}

@Composable
private fun TransferStatusCard(status: TransferStatus, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val failed = status is TransferStatus.Failed
    Surface(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status is TransferStatus.Running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            val text = when (status) {
                TransferStatus.Idle -> ""

                is TransferStatus.Running -> stringResource(
                    if (status.importing) Res.string.transfer_importing else Res.string.transfer_exporting,
                    status.messages,
                )

                is TransferStatus.Done -> stringResource(
                    if (status.importing) Res.string.transfer_imported else Res.string.transfer_exported,
                    status.summary.messages,
                    status.summary.accounts,
                )

                is TransferStatus.Failed -> when {
                    status.error == TransferError.NotATransferFile -> stringResource(Res.string.transfer_not_a_file)
                    status.error == TransferError.TooNew -> stringResource(Res.string.transfer_too_new)
                    status.importing -> stringResource(Res.string.transfer_import_failed)
                    else -> stringResource(Res.string.transfer_export_failed)
                }
            }
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            val location = (status as? TransferStatus.Done)?.location
            if (location != null && Platform.isDesktop) {
                TextButton(onClick = { Platform.revealDownload(location) }) { Text(stringResource(Res.string.transfer_show)) }
            }
            if (status !is TransferStatus.Running) {
                TextButton(onClick = { onIntent(AppIntent.DismissTransfer) }) { Text(stringResource(Res.string.transfer_dismiss)) }
            }
        }
    }
}
