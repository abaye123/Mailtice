package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.TransferStatus
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.transfer.TransferError
import co.abaye.mailtice.transfer.TransferFiles
import co.abaye.mailtice.ui.SectionHeader
import co.abaye.mailtice.ui.SettingRow
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.transfer_dismiss
import mailtice.shared.generated.resources.transfer_export
import mailtice.shared.generated.resources.transfer_export_button
import mailtice.shared.generated.resources.transfer_export_desc
import mailtice.shared.generated.resources.transfer_export_failed
import mailtice.shared.generated.resources.transfer_exported
import mailtice.shared.generated.resources.transfer_exporting
import mailtice.shared.generated.resources.transfer_import
import mailtice.shared.generated.resources.transfer_import_button
import mailtice.shared.generated.resources.transfer_import_desc
import mailtice.shared.generated.resources.transfer_import_failed
import mailtice.shared.generated.resources.transfer_import_password
import mailtice.shared.generated.resources.transfer_import_password_title
import mailtice.shared.generated.resources.transfer_import_title
import mailtice.shared.generated.resources.transfer_imported
import mailtice.shared.generated.resources.transfer_importing
import mailtice.shared.generated.resources.transfer_not_a_file
import mailtice.shared.generated.resources.transfer_password
import mailtice.shared.generated.resources.transfer_password_mismatch
import mailtice.shared.generated.resources.transfer_password_repeat
import mailtice.shared.generated.resources.transfer_password_title
import mailtice.shared.generated.resources.transfer_password_warning
import mailtice.shared.generated.resources.transfer_show
import mailtice.shared.generated.resources.transfer_skipped
import mailtice.shared.generated.resources.transfer_title
import mailtice.shared.generated.resources.transfer_too_new
import mailtice.shared.generated.resources.transfer_wrong_password
import org.jetbrains.compose.resources.stringResource

private val PasswordField = TextStyle(textDirection = TextDirection.Ltr)

/**
 * Settings: carry mail to a computer without internet. Export writes everything stored here to a
 * file, with a password or without; import reads one written by the app elsewhere.
 */
@Composable
internal fun TransferSection(status: TransferStatus, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    var exporting by remember { mutableStateOf(false) }
    Column(modifier) {
        SectionHeader(stringResource(Res.string.transfer_title))
        val running = status is TransferStatus.Running
        SettingRow(stringResource(Res.string.transfer_export), subtitle = stringResource(Res.string.transfer_export_desc)) {
            OutlinedButton(onClick = { exporting = true }, enabled = !running) {
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
        when (status) {
            TransferStatus.Idle -> Unit
            is TransferStatus.NeedsPassword -> ImportPasswordDialog(status.wrong, onIntent)
            else -> TransferStatusCard(status, onIntent)
        }
    }
    if (exporting) {
        ExportPasswordDialog(onDismiss = { exporting = false }) { password ->
            exporting = false
            onIntent(AppIntent.ExportMail(password))
        }
    }
}

@Composable
private fun PasswordInput(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = PasswordField,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

/** An empty password exports a plain file; the warning stays on screen until there is one. */
@Composable
private fun ExportPasswordDialog(onDismiss: () -> Unit, onExport: (String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = password != repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.transfer_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.transfer_password_warning), style = MaterialTheme.typography.bodySmall)
                PasswordInput(password, stringResource(Res.string.transfer_password)) { password = it }
                PasswordInput(repeat, stringResource(Res.string.transfer_password_repeat)) { repeat = it }
                if (mismatch && repeat.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.transfer_password_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(password.ifEmpty { null }) }, enabled = !mismatch) {
                Text(stringResource(Res.string.transfer_export_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun ImportPasswordDialog(wrong: Boolean, onIntent: (AppIntent) -> Unit) {
    var password by remember(wrong) { mutableStateOf("") }
    val close = { onIntent(AppIntent.DismissTransfer) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(Res.string.transfer_import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordInput(password, stringResource(Res.string.transfer_import_password)) { password = it }
                if (wrong) {
                    Text(
                        stringResource(Res.string.transfer_wrong_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(AppIntent.ImportWithPassword(password)) }, enabled = password.isNotEmpty()) {
                Text(stringResource(Res.string.transfer_import_button))
            }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun transferText(status: TransferStatus): String = when (status) {
    TransferStatus.Idle, is TransferStatus.NeedsPassword -> ""

    is TransferStatus.Running -> stringResource(
        if (status.importing) Res.string.transfer_importing else Res.string.transfer_exporting,
        status.messages,
    )

    is TransferStatus.Done -> {
        val done = stringResource(
            if (status.importing) Res.string.transfer_imported else Res.string.transfer_exported,
            status.summary.messages,
            status.summary.accounts,
        )
        val skipped = status.summary.skippedAccounts
        if (skipped > 0) done + "\n" + stringResource(Res.string.transfer_skipped, skipped) else done
    }

    is TransferStatus.Failed -> when {
        status.error == TransferError.NotATransferFile -> stringResource(Res.string.transfer_not_a_file)
        status.error == TransferError.TooNew -> stringResource(Res.string.transfer_too_new)
        status.importing -> stringResource(Res.string.transfer_import_failed)
        else -> stringResource(Res.string.transfer_export_failed)
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
            Text(transferText(status), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
