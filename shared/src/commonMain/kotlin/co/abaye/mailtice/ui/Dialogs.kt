package co.abaye.mailtice.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import co.abaye.mailtice.app.AppDialog
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_remove
import mailtice.shared.generated.resources.accounts_remove_confirm
import mailtice.shared.generated.resources.detail_clear_cache
import mailtice.shared.generated.resources.detail_clear_cache_confirm
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.dialog_confirm
import mailtice.shared.generated.resources.settings_reset
import mailtice.shared.generated.resources.settings_reset_confirm
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppDialogHost(state: AppState, onIntent: (AppIntent) -> Unit) {
    val (title, text) = when (val dialog = state.dialog) {
        AppDialog.Hidden -> return

        AppDialog.ConfirmReset -> stringResource(Res.string.settings_reset) to stringResource(Res.string.settings_reset_confirm)

        is AppDialog.ConfirmRemove -> {
            val name = state.account(dialog.accountId)?.displayName.orEmpty()
            stringResource(Res.string.accounts_remove) to stringResource(Res.string.accounts_remove_confirm, name)
        }

        is AppDialog.ConfirmClearCache -> {
            val name = state.account(dialog.accountId)?.displayName.orEmpty()
            stringResource(Res.string.detail_clear_cache) to stringResource(Res.string.detail_clear_cache_confirm, name)
        }
    }
    AlertDialog(
        onDismissRequest = { onIntent(AppIntent.DismissDialog) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onIntent(AppIntent.ConfirmDialog) }) {
                Text(stringResource(Res.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(AppIntent.DismissDialog) }) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
    )
}
