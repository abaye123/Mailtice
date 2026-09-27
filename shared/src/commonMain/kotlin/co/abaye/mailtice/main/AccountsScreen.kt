package co.abaye.mailtice.main

import mailtice.shared.generated.resources.accounts_move_down
import mailtice.shared.generated.resources.accounts_move_up
import co.abaye.mailtice.ui.TooltipIconButton
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppKey
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.ui.EmptyIllustration
import co.abaye.mailtice.ui.Illustration
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_add
import mailtice.shared.generated.resources.accounts_empty
import mailtice.shared.generated.resources.accounts_reconnect
import mailtice.shared.generated.resources.accounts_subtitle
import mailtice.shared.generated.resources.accounts_title
import mailtice.shared.generated.resources.empty_welcome_body
import mailtice.shared.generated.resources.provider_gmail
import mailtice.shared.generated.resources.provider_imap
import mailtice.shared.generated.resources.provider_microsoft
import mailtice.shared.generated.resources.provider_yahoo
import mailtice.shared.generated.resources.status_idle
import mailtice.shared.generated.resources.status_needs_reauth
import mailtice.shared.generated.resources.status_offline
import mailtice.shared.generated.resources.status_ok
import mailtice.shared.generated.resources.status_syncing
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountsScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.accounts_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.accounts_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onIntent(AppIntent.StartAddAccount) }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(Res.string.accounts_add), Modifier.padding(start = 8.dp))
            }
            if (state.accounts.isEmpty()) NoAccountsCard(onIntent)
            state.accounts.forEachIndexed { index, account ->
                AccountRow(
                    account,
                    state.status(account.id),
                    unread = state.unread[account.id] ?: 0L,
                    bytes = state.storage.perAccount[account.id] ?: 0L,
                    // The order here is the order everywhere: sidebar, home, "From", settings.
                    canMoveUp = index > 0,
                    canMoveDown = index < state.accounts.lastIndex,
                    onIntent = onIntent,
                )
            }
        }
    }
    AddAccountDialog(state, onIntent)
}

/** Takes the place of the account list until there is one, with the same way in as the button above. */
@Composable
private fun NoAccountsCard(onIntent: (AppIntent) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable { onIntent(AppIntent.StartAddAccount) },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmptyIllustration(Illustration.Welcome, size = 150.dp)
            Text(
                stringResource(Res.string.accounts_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.empty_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    status: AccountStatus,
    unread: Long,
    bytes: Long,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onIntent: (AppIntent) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().clickable { onIntent(AppIntent.Navigate(AppKey.AccountDetail(account.id))) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountDot(account, Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(account.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${account.kind.label()} · ${status.label()} · $unread · ${formatBytes(bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == AccountStatus.NeedsReauth) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == AccountStatus.NeedsReauth) {
                Button(onClick = { onIntent(AppIntent.Reconnect(account.id)) }) { Text(stringResource(Res.string.accounts_reconnect)) }
            }
            if (canMoveUp || canMoveDown) {
                TooltipIconButton(
                    Icons.Outlined.ArrowUpward,
                    stringResource(Res.string.accounts_move_up),
                    { onIntent(AppIntent.MoveAccount(account.id, up = true)) },
                    enabled = canMoveUp,
                )
                TooltipIconButton(
                    Icons.Outlined.ArrowDownward,
                    stringResource(Res.string.accounts_move_down),
                    { onIntent(AppIntent.MoveAccount(account.id, up = false)) },
                    enabled = canMoveDown,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AccountStatus.label(): String = when (this) {
    AccountStatus.Idle -> stringResource(Res.string.status_idle)
    AccountStatus.Syncing -> stringResource(Res.string.status_syncing)
    AccountStatus.Ok -> stringResource(Res.string.status_ok)
    AccountStatus.Offline -> stringResource(Res.string.status_offline)
    AccountStatus.NeedsReauth -> stringResource(Res.string.status_needs_reauth)
}

@Composable
internal fun ProviderKind.label(): String = when (this) {
    ProviderKind.Gmail -> stringResource(Res.string.provider_gmail)
    ProviderKind.Microsoft -> stringResource(Res.string.provider_microsoft)
    ProviderKind.Yahoo -> stringResource(Res.string.provider_yahoo)
    ProviderKind.Imap -> stringResource(Res.string.provider_imap)
}
