package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.auth.OAuthKeys
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.SectionHeader
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.oauth_keys_desc
import mailtice.shared.generated.resources.oauth_keys_google_id
import mailtice.shared.generated.resources.oauth_keys_google_secret
import mailtice.shared.generated.resources.oauth_keys_help
import mailtice.shared.generated.resources.oauth_keys_microsoft_id
import mailtice.shared.generated.resources.oauth_keys_save
import mailtice.shared.generated.resources.oauth_keys_title
import org.jetbrains.compose.resources.stringResource

private const val OAUTH_HELP_URL = "https://github.com/abaye123/Mailtice#first-setup-oauth"

/**
 * Settings (desktop): the Google and Microsoft OAuth clients, for a copy built without them. Left
 * empty, the build's own clients are used.
 */
@Composable
internal fun OAuthKeysSection(saved: OAuthKeys, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    var googleId by remember(saved) { mutableStateOf(saved.googleClientId) }
    var googleSecret by remember(saved) { mutableStateOf(saved.googleClientSecret) }
    var microsoftId by remember(saved) { mutableStateOf(saved.microsoftClientId) }
    val ltr = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.oauth_keys_title))
        Text(
            stringResource(Res.string.oauth_keys_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            googleId,
            { googleId = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.oauth_keys_google_id)) },
            singleLine = true,
            textStyle = ltr,
        )
        OutlinedTextField(
            googleSecret,
            { googleSecret = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.oauth_keys_google_secret)) },
            singleLine = true,
            textStyle = ltr,
        )
        OutlinedTextField(
            microsoftId,
            { microsoftId = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.oauth_keys_microsoft_id)) },
            singleLine = true,
            textStyle = ltr,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val changed = OAuthKeys(googleId, googleSecret, microsoftId) != saved
            OutlinedButton(onClick = {
                onIntent(AppIntent.SaveOAuthKeys(OAuthKeys(googleId, googleSecret, microsoftId)))
            }, enabled = changed) {
                Text(stringResource(Res.string.oauth_keys_save))
            }
            TextButton(onClick = { Platform.openUrl(OAUTH_HELP_URL) }) { Text(stringResource(Res.string.oauth_keys_help)) }
        }
    }
}
