package co.abaye.mailtice.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.RetentionOptions
import co.abaye.mailtice.ui.SectionHeader
import co.abaye.mailtice.ui.SettingBlock
import co.abaye.mailtice.ui.SettingRow
import co.abaye.mailtice.ui.TooltipIconButton
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_color
import mailtice.shared.generated.resources.accounts_label
import mailtice.shared.generated.resources.accounts_notify
import mailtice.shared.generated.resources.accounts_reconnect
import mailtice.shared.generated.resources.accounts_remove
import mailtice.shared.generated.resources.detail_clear_cache
import mailtice.shared.generated.resources.detail_clear_cache_desc
import mailtice.shared.generated.resources.detail_folder_notify
import mailtice.shared.generated.resources.detail_folder_sync
import mailtice.shared.generated.resources.detail_folders
import mailtice.shared.generated.resources.detail_folders_desc
import mailtice.shared.generated.resources.detail_folders_empty
import mailtice.shared.generated.resources.detail_general
import mailtice.shared.generated.resources.detail_labels
import mailtice.shared.generated.resources.detail_refresh_folders
import mailtice.shared.generated.resources.detail_retention
import mailtice.shared.generated.resources.detail_retention_all
import mailtice.shared.generated.resources.detail_retention_all_warning
import mailtice.shared.generated.resources.detail_retention_days
import mailtice.shared.generated.resources.detail_storage
import mailtice.shared.generated.resources.detail_storage_value
import mailtice.shared.generated.resources.reader_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountDetailScreen(accountId: String, state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val account = state.account(accountId) ?: return
    val status = state.status(account.id)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.reader_back), {
                    onIntent(AppIntent.Back)
                })
                AccountDot(account, Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${account.kind.label()} · ${account.email} · ${status.label()}", style = MaterialTheme.typography.bodySmall)
                }
                if (status == AccountStatus.NeedsReauth || !account.kind.oauth) {
                    TextButton(onClick = {
                        onIntent(AppIntent.Reconnect(account.id))
                    }) { Text(stringResource(Res.string.accounts_reconnect)) }
                }
            }

            SectionHeader(stringResource(Res.string.detail_general))
            General(account, onIntent)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Retention(account, onIntent)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Folders(account, state.foldersOf(account.id), onIntent)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(Res.string.detail_storage))
            val bytes = state.storage.perAccount[account.id] ?: 0L
            val count = state.storage.messagesPerAccount[account.id] ?: 0
            SettingRow(
                title = stringResource(Res.string.detail_storage_value, formatBytes(bytes), count.toString()),
                subtitle = stringResource(Res.string.detail_clear_cache_desc),
            ) {
                OutlinedButton(onClick = {
                    onIntent(AppIntent.ClearAccountCache(account.id))
                }) { Text(stringResource(Res.string.detail_clear_cache)) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { onIntent(AppIntent.RemoveAccount(account.id)) }) {
                Text(stringResource(Res.string.accounts_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun General(account: Account, onIntent: (AppIntent) -> Unit) {
    // Local text state: the database round trip must not move the cursor while typing.
    var label by remember(account.id) { mutableStateOf(account.label) }
    OutlinedTextField(
        value = label,
        onValueChange = {
            label = it
            onIntent(AppIntent.SetAccountLabel(account.id, it))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(Res.string.accounts_label)) },
    )
    SettingRow(stringResource(Res.string.accounts_notify)) {
        Switch(account.notify, { onIntent(AppIntent.SetAccountNotify(account.id, it)) })
    }
    SettingRow(stringResource(Res.string.accounts_color)) {
        AccountDot(account, Modifier.size(22.dp).clickable { onIntent(AppIntent.CycleAccountColor(account.id)) })
    }
}

@Composable
private fun Retention(account: Account, onIntent: (AppIntent) -> Unit) {
    SettingBlock(
        title = stringResource(Res.string.detail_retention),
        subtitle = if (account.retentionDays == 0) stringResource(Res.string.detail_retention_all_warning) else null,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RetentionOptions.forEachIndexed { i, days ->
                SegmentedButton(
                    selected = account.retentionDays == days,
                    onClick = { onIntent(AppIntent.SetRetention(account.id, days)) },
                    shape = SegmentedButtonDefaults.itemShape(i, RetentionOptions.size),
                ) {
                    Text(
                        if (days ==
                            0
                        ) {
                            stringResource(Res.string.detail_retention_all)
                        } else {
                            stringResource(Res.string.detail_retention_days, days)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Folders(account: Account, folders: List<Folder>, onIntent: (AppIntent) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                stringResource(if (account.capabilities.labels) Res.string.detail_labels else Res.string.detail_folders),
                Modifier.weight(1f),
            )
            TooltipIconButton(Icons.Outlined.Refresh, stringResource(Res.string.detail_refresh_folders), {
                onIntent(AppIntent.RefreshFolders(account.id))
            })
        }
        Text(
            stringResource(Res.string.detail_folders_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (folders.isEmpty()) {
            Text(stringResource(Res.string.detail_folders_empty), Modifier.padding(vertical = 8.dp))
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("", Modifier.weight(1f))
            Text(stringResource(Res.string.detail_folder_sync), Modifier.widthIn(min = 72.dp), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(Res.string.detail_folder_notify),
                Modifier.widthIn(min = 72.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        folders.forEach { folder ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(ContentDirection))
                Checkbox(
                    checked = folder.sync,
                    onCheckedChange = {
                        onIntent(AppIntent.SetFolderPrefs(account.id, folder.id, sync = it, notify = folder.notify && it))
                    },
                    modifier = Modifier.widthIn(min = 72.dp),
                )
                Checkbox(
                    checked = folder.notify,
                    enabled = folder.sync,
                    onCheckedChange = { onIntent(AppIntent.SetFolderPrefs(account.id, folder.id, sync = folder.sync, notify = it)) },
                    modifier = Modifier.widthIn(min = 72.dp),
                )
            }
        }
    }
}
