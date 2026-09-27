package co.abaye.mailtice.app

import mailtice.shared.generated.resources.labels_manage
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.nav_about
import mailtice.shared.generated.resources.nav_accounts
import mailtice.shared.generated.resources.nav_inbox
import mailtice.shared.generated.resources.nav_settings
import org.jetbrains.compose.resources.stringResource

@Immutable
sealed interface AppKey : NavKey {
    data object Inbox : AppKey
    data object Accounts : AppKey
    data object Settings : AppKey
    data object About : AppKey

    /** Per-account settings: folders, retention, storage. */
    data class AccountDetail(val accountId: String) : AppKey

    /** One account's labels or folders: pin, hide, rename, colour, create and delete. */
    data class Labels(val accountId: String) : AppKey

    /** The reader as its own page, on narrow (phone) layouts only. */
    data object Reader : AppKey
}

val MainDestinations: List<AppKey> = listOf(
    AppKey.Inbox,
    AppKey.Accounts,
    AppKey.Settings,
    AppKey.About,
)

fun AppKey.isMain(): Boolean = this in MainDestinations

@Composable
fun AppKey.label(): String = when (this) {
    AppKey.Inbox -> stringResource(Res.string.nav_inbox)
    AppKey.Accounts -> stringResource(Res.string.nav_accounts)
    AppKey.Settings -> stringResource(Res.string.nav_settings)
    AppKey.About -> stringResource(Res.string.nav_about)
    is AppKey.AccountDetail -> stringResource(Res.string.nav_accounts)
    is AppKey.Labels -> stringResource(Res.string.labels_manage)
    AppKey.Reader -> stringResource(Res.string.nav_inbox)
}

fun AppKey.icon(): ImageVector = when (this) {
    AppKey.Inbox -> Icons.Outlined.Inbox
    AppKey.Accounts -> Icons.Outlined.ManageAccounts
    AppKey.Settings -> Icons.Outlined.Settings
    AppKey.About -> Icons.Outlined.Info
    is AppKey.AccountDetail -> Icons.Outlined.ManageAccounts
    is AppKey.Labels -> Icons.AutoMirrored.Outlined.Label
    AppKey.Reader -> Icons.Outlined.Inbox
}
