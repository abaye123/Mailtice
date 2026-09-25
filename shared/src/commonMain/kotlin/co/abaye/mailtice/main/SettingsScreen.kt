package co.abaye.mailtice.main

import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import co.abaye.mailtice.app.AppKey
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.formatBytes
import mailtice.shared.generated.resources.settings_storage
import mailtice.shared.generated.resources.settings_storage_compact
import mailtice.shared.generated.resources.settings_storage_desc
import mailtice.shared.generated.resources.settings_storage_total
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.domain.PollIntervals
import co.abaye.mailtice.domain.ThemeMode
import co.abaye.mailtice.domain.UiLanguage
import co.abaye.mailtice.ui.SectionHeader
import co.abaye.mailtice.ui.SettingBlock
import co.abaye.mailtice.ui.SettingRow
import co.abaye.mailtice.ui.Tooltip
import mailtice.shared.generated.resources.accent_amber
import mailtice.shared.generated.resources.accent_flag
import mailtice.shared.generated.resources.accent_indigo
import mailtice.shared.generated.resources.accent_rose
import mailtice.shared.generated.resources.accent_slate
import mailtice.shared.generated.resources.accent_teal
import mailtice.shared.generated.resources.accent_violet
import mailtice.shared.generated.resources.density_comfortable
import mailtice.shared.generated.resources.density_compact
import mailtice.shared.generated.resources.density_spacious
import mailtice.shared.generated.resources.pane_cards
import mailtice.shared.generated.resources.pane_lines
import mailtice.shared.generated.resources.settings_density
import mailtice.shared.generated.resources.settings_density_desc
import mailtice.shared.generated.resources.settings_pane_style
import mailtice.shared.generated.resources.settings_pane_style_desc
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.language_system
import mailtice.shared.generated.resources.poll_minutes
import mailtice.shared.generated.resources.poll_seconds
import mailtice.shared.generated.resources.settings_accent
import mailtice.shared.generated.resources.settings_appearance
import mailtice.shared.generated.resources.settings_close_to_tray
import mailtice.shared.generated.resources.settings_close_to_tray_desc
import mailtice.shared.generated.resources.settings_data
import mailtice.shared.generated.resources.settings_language
import mailtice.shared.generated.resources.settings_launch_at_login
import mailtice.shared.generated.resources.settings_launch_at_login_desc
import mailtice.shared.generated.resources.settings_notifications
import mailtice.shared.generated.resources.settings_notifications_desc
import mailtice.shared.generated.resources.settings_poll
import mailtice.shared.generated.resources.settings_poll_desc
import mailtice.shared.generated.resources.settings_reset
import mailtice.shared.generated.resources.settings_reset_desc
import mailtice.shared.generated.resources.settings_sync
import mailtice.shared.generated.resources.settings_theme
import mailtice.shared.generated.resources.settings_window
import mailtice.shared.generated.resources.theme_dark
import mailtice.shared.generated.resources.theme_light
import mailtice.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { onIntent(AppIntent.RefreshStorage) }
    val settings = state.data.settings
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp)) {
            SectionHeader(stringResource(Res.string.settings_appearance))
            SettingBlock(stringResource(Res.string.settings_theme)) {
                ThemePicker(
                    current = settings.theme,
                    onPick = { onIntent(AppIntent.SetTheme(it)) },
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                )
            }
            SettingRow(stringResource(Res.string.settings_accent)) {
                AccentPicker(settings.accent) { onIntent(AppIntent.SetAccent(it)) }
            }
            SettingBlock(stringResource(Res.string.settings_density), subtitle = stringResource(Res.string.settings_density_desc)) {
                ChoicePicker(ListDensity.entries, settings.density, { it.label() }) { onIntent(AppIntent.SetDensity(it)) }
            }
            SettingBlock(stringResource(Res.string.settings_pane_style), subtitle = stringResource(Res.string.settings_pane_style_desc)) {
                ChoicePicker(PaneStyle.entries, settings.paneStyle, { it.label() }) { onIntent(AppIntent.SetPaneStyle(it)) }
            }
            SettingRow(stringResource(Res.string.settings_language)) {
                LanguagePicker(
                    language = if (settings.uiLanguageAuto) null else settings.uiLanguage,
                    onPick = { onIntent(AppIntent.SetUiLanguage(it)) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader(stringResource(Res.string.settings_sync))
            SettingBlock(stringResource(Res.string.settings_poll), subtitle = stringResource(Res.string.settings_poll_desc)) {
                PollPicker(settings.pollSeconds) { onIntent(AppIntent.SetPollInterval(it)) }
            }
            SettingRow(stringResource(Res.string.settings_notifications), subtitle = stringResource(Res.string.settings_notifications_desc)) {
                Switch(checked = settings.notificationsEnabled, onCheckedChange = { onIntent(AppIntent.SetNotifications(it)) })
            }

            // Tray and login items exist only on desktop; on Android the section is not shown at all.
            if (Platform.isDesktop) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SectionHeader(stringResource(Res.string.settings_window))
                SettingRow(stringResource(Res.string.settings_close_to_tray), subtitle = stringResource(Res.string.settings_close_to_tray_desc)) {
                    Switch(checked = settings.closeToTray, onCheckedChange = { onIntent(AppIntent.SetCloseToTray(it)) })
                }
                SettingRow(stringResource(Res.string.settings_launch_at_login), subtitle = stringResource(Res.string.settings_launch_at_login_desc)) {
                    Switch(checked = settings.launchAtLogin, onCheckedChange = { onIntent(AppIntent.SetLaunchAtLogin(it)) })
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StorageSection(state, onIntent)

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader(stringResource(Res.string.settings_data))
            SettingRow(stringResource(Res.string.settings_reset), subtitle = stringResource(Res.string.settings_reset_desc)) {
                Button(
                    onClick = { onIntent(AppIntent.ResetApp) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(Res.string.settings_reset))
                }
            }
        }
    }
}

/** A row of segmented buttons over [options], the shared shape of the appearance choices. */
@Composable
private fun <T> ChoicePicker(options: List<T>, current: T, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = current == option,
                onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ListDensity.label(): String = when (this) {
    ListDensity.Compact -> stringResource(Res.string.density_compact)
    ListDensity.Comfortable -> stringResource(Res.string.density_comfortable)
    ListDensity.Spacious -> stringResource(Res.string.density_spacious)
}

@Composable
private fun PaneStyle.label(): String = when (this) {
    PaneStyle.Cards -> stringResource(Res.string.pane_cards)
    PaneStyle.Lines -> stringResource(Res.string.pane_lines)
}

@Composable
private fun AccentColor.label(): String = when (this) {
    AccentColor.Flag -> stringResource(Res.string.accent_flag)
    AccentColor.Indigo -> stringResource(Res.string.accent_indigo)
    AccentColor.Teal -> stringResource(Res.string.accent_teal)
    AccentColor.Amber -> stringResource(Res.string.accent_amber)
    AccentColor.Rose -> stringResource(Res.string.accent_rose)
    AccentColor.Violet -> stringResource(Res.string.accent_violet)
    AccentColor.Slate -> stringResource(Res.string.accent_slate)
}

@Composable
private fun PollPicker(current: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        PollIntervals.forEachIndexed { index, seconds ->
            SegmentedButton(
                selected = current == seconds,
                onClick = { onPick(seconds) },
                shape = SegmentedButtonDefaults.itemShape(index, PollIntervals.size),
            ) {
                Text(
                    if (seconds < 60) {
                        stringResource(Res.string.poll_seconds, seconds)
                    } else {
                        stringResource(Res.string.poll_minutes, seconds / 60)
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onPick(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(
                    when (mode) {
                        ThemeMode.System -> stringResource(Res.string.theme_system)
                        ThemeMode.Light -> stringResource(Res.string.theme_light)
                        ThemeMode.Dark -> stringResource(Res.string.theme_dark)
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun AccentPicker(current: AccentColor, onPick: (AccentColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentColor.entries.forEach { accent ->
            val selected = accent == current
            // A swatch has no text of its own; the colour's name shows on hover.
            Tooltip(accent.label()) {
                Box(
                Modifier
                    .size(if (selected) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(accent.seed)
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onPick(accent) },
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(language: UiLanguage?, onPick: (UiLanguage?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(language?.label ?: stringResource(Res.string.language_system))
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.language_system)) },
                onClick = {
                    expanded = false
                    onPick(null)
                },
            )
            UiLanguage.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        onPick(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun StorageSection(state: AppState, onIntent: (AppIntent) -> Unit) {
    val storage = state.storage
    SectionHeader(stringResource(Res.string.settings_storage))
    SettingRow(
        title = stringResource(Res.string.settings_storage_total, formatBytes(storage.totalBytes)),
        subtitle = stringResource(Res.string.settings_storage_desc),
    ) {
        OutlinedButton(onClick = { onIntent(AppIntent.CompactDatabase) }) { Text(stringResource(Res.string.settings_storage_compact)) }
    }
    state.accounts.forEach { account ->
        Row(
            Modifier.fillMaxWidth().clickable { onIntent(AppIntent.Navigate(AppKey.AccountDetail(account.id))) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountDot(account)
            Text(account.displayName, Modifier.weight(1f))
            Text(
                "${formatBytes(storage.perAccount[account.id] ?: 0L)} · ${storage.messagesPerAccount[account.id] ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
