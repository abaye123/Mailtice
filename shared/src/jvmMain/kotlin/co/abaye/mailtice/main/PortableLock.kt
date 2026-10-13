package co.abaye.mailtice.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.data.PortableKeyVault
import co.abaye.mailtice.data.PortableLock
import co.abaye.mailtice.data.PortableSecrets
import co.abaye.mailtice.platform.PortableMode
import co.abaye.mailtice.ui.SettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.app_icon
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.portable_forgot
import mailtice.shared.generated.resources.portable_forgot_body
import mailtice.shared.generated.resources.portable_forgot_confirm
import mailtice.shared.generated.resources.portable_mismatch
import mailtice.shared.generated.resources.portable_no_password
import mailtice.shared.generated.resources.portable_password
import mailtice.shared.generated.resources.portable_password_again
import mailtice.shared.generated.resources.portable_set_password
import mailtice.shared.generated.resources.portable_setup_body
import mailtice.shared.generated.resources.portable_title
import mailtice.shared.generated.resources.portable_too_short
import mailtice.shared.generated.resources.portable_unlock
import mailtice.shared.generated.resources.portable_unlock_body
import mailtice.shared.generated.resources.portable_wrong
import mailtice.shared.generated.resources.settings_portable_password
import mailtice.shared.generated.resources.settings_portable_password_change
import mailtice.shared.generated.resources.settings_portable_password_off
import mailtice.shared.generated.resources.settings_portable_password_on
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MIN_PASSWORD = 4
private val LtrField = TextStyle(textDirection = TextDirection.Ltr)

/**
 * True when a portable copy has to ask about its secrets before the app may start: on its first
 * run (password or none), or when they are behind a password not given yet. The app, and with it
 * every sync, waits for [PortableLockScreen]: a sync with the secrets still locked would read every
 * account as signed out.
 */
fun portableLockNeeded(): Boolean = PortableMode.enabled && PortableSecrets.vault.state != PortableLock.Open

/** The first-run choice of a password, or the password asked at each start; [onReady] once open. */
@Composable
fun PortableLockScreen(onReady: () -> Unit, modifier: Modifier = Modifier) {
    val vault = PortableSecrets.vault
    var lock by remember { mutableStateOf(vault.state) }
    val ready by rememberUpdatedState(onReady)
    LaunchedEffect(lock) { if (lock == PortableLock.Open) ready() }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(Res.drawable.app_icon), null, Modifier.size(48.dp))
            Text(stringResource(Res.string.portable_title), style = MaterialTheme.typography.headlineSmall)
            when (lock) {
                PortableLock.Unset -> {
                    Text(stringResource(Res.string.portable_setup_body), style = MaterialTheme.typography.bodyMedium)
                    PasswordForm(vault, onDone = { lock = vault.state })
                }

                PortableLock.Locked -> UnlockForm(vault, onChange = { lock = vault.state })

                PortableLock.Open -> Unit
            }
        }
    }
}

/**
 * A new password typed twice, or none: sets the vault up on first run, or changes its password
 * later. The key derivation takes a moment, so it runs off the UI thread with a bar showing.
 */
@Composable
internal fun PasswordForm(vault: PortableKeyVault, onDone: () -> Unit, modifier: Modifier = Modifier, change: Boolean = false) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mismatch = stringResource(Res.string.portable_mismatch)
    val tooShort = stringResource(Res.string.portable_too_short)
    val submit = { chosen: CharArray? ->
        busy = true
        scope.launch {
            withContext(Dispatchers.Default) { if (change) vault.changePassword(chosen) else vault.setUp(chosen) }
            chosen?.fill(' ')
            busy = false
            onDone()
        }
    }
    val setPassword = {
        error = when {
            password.length < MIN_PASSWORD -> tooShort
            password != again -> mismatch
            else -> null
        }
        if (error == null) submit(password.toCharArray())
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PasswordField(password, { password = it }, stringResource(Res.string.portable_password), !busy, ImeAction.Next) {}
        PasswordField(again, { again = it }, stringResource(Res.string.portable_password_again), !busy, ImeAction.Done, setPassword)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = setPassword, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.portable_set_password))
        }
        OutlinedButton(onClick = { submit(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.portable_no_password))
        }
    }
}

/** The password asked at each start; a wrong one says so and opens nothing. */
@Composable
private fun UnlockForm(vault: PortableKeyVault, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    val open = {
        busy = true
        wrong = false
        scope.launch {
            val chars = password.toCharArray()
            val ok = withContext(Dispatchers.Default) { vault.unlock(chars) }
            chars.fill(' ')
            busy = false
            if (ok) onChange() else wrong = true
        }
        Unit
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(Res.string.portable_unlock_body), style = MaterialTheme.typography.bodyMedium)
        PasswordField(password, { password = it }, stringResource(Res.string.portable_password), !busy, ImeAction.Done, open)
        if (wrong) {
            Text(
                stringResource(Res.string.portable_wrong),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = open, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.portable_unlock))
        }
        TextButton(onClick = { forgetting = true }, enabled = !busy) { Text(stringResource(Res.string.portable_forgot)) }
        if (forgetting) {
            AlertDialog(
                onDismissRequest = { forgetting = false },
                text = { Text(stringResource(Res.string.portable_forgot_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        forgetting = false
                        vault.forget(PortableSecrets.secretsFile)
                        onChange()
                    }) { Text(stringResource(Res.string.portable_forgot_confirm)) }
                },
                dismissButton = { TextButton(onClick = { forgetting = false }) { Text(stringResource(Res.string.dialog_cancel)) } },
            )
        }
    }
}

@Composable
internal actual fun PortablePasswordSetting() {
    if (!PortableMode.enabled) return
    val vault = PortableSecrets.vault
    var protected by remember { mutableStateOf(vault.hasPassword) }
    var changing by remember { mutableStateOf(false) }
    SettingRow(
        stringResource(Res.string.settings_portable_password),
        subtitle = stringResource(if (protected) Res.string.settings_portable_password_on else Res.string.settings_portable_password_off),
    ) {
        OutlinedButton(onClick = { changing = true }) { Text(stringResource(Res.string.settings_portable_password_change)) }
        if (changing) {
            AlertDialog(
                onDismissRequest = { changing = false },
                title = { Text(stringResource(Res.string.settings_portable_password)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PasswordForm(vault, onDone = {
                            protected = vault.hasPassword
                            changing = false
                        }, change = true)
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { changing = false }) { Text(stringResource(Res.string.dialog_cancel)) } },
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    action: ImeAction,
    onAction: () -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = LtrField,
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = action),
        keyboardActions = KeyboardActions(onDone = { onAction() }),
    )
}
