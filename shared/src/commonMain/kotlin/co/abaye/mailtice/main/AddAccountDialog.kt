package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.app.AddAccountStep
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ImapForm
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.provider.ImapLoginException
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.accounts_add
import mailtice.shared.generated.resources.add_choose_provider
import mailtice.shared.generated.resources.add_imap_detect
import mailtice.shared.generated.resources.add_imap_email
import mailtice.shared.generated.resources.add_imap_error_credentials
import mailtice.shared.generated.resources.add_imap_error_host
import mailtice.shared.generated.resources.add_imap_error_other
import mailtice.shared.generated.resources.add_imap_error_tls
import mailtice.shared.generated.resources.add_imap_hint
import mailtice.shared.generated.resources.add_imap_host
import mailtice.shared.generated.resources.add_imap_password
import mailtice.shared.generated.resources.add_imap_port
import mailtice.shared.generated.resources.add_imap_reconnect_title
import mailtice.shared.generated.resources.add_imap_save
import mailtice.shared.generated.resources.add_imap_username
import mailtice.shared.generated.resources.add_provider_imap_desc
import mailtice.shared.generated.resources.provider_imap
import mailtice.shared.generated.resources.dialog_cancel
import org.jetbrains.compose.resources.stringResource

/** Addresses, hosts, ports and passwords read left-to-right even in a Hebrew interface. */
private val LtrField = TextStyle(textDirection = TextDirection.Ltr)

@Composable
fun AddAccountDialog(state: AppState, onIntent: (AppIntent) -> Unit) {
    val step = state.addAccount ?: return
    val close = { onIntent(AppIntent.CloseAddAccount) }
    when (step) {
        AddAccountStep.ChooseProvider -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(Res.string.accounts_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.add_choose_provider), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Only providers this build and platform can actually sign in to.
                    state.availableProviders.forEach { kind ->
                        OutlinedButton(onClick = { onIntent(AppIntent.ChooseProvider(kind)) }, Modifier.fillMaxWidth()) {
                            Text(kind.label())
                        }
                    }
                    Text(stringResource(Res.string.add_provider_imap_desc), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = close) { Text(stringResource(Res.string.dialog_cancel)) } },
        )

        is AddAccountStep.Imap -> ImapFormDialog(step.form, onIntent, close)
    }
}

@Composable
private fun ImapFormDialog(form: ImapForm, onIntent: (AppIntent) -> Unit, close: () -> Unit) {
    val update = { f: ImapForm -> onIntent(AppIntent.UpdateImapForm(f)) }
    val fixed = form.reconnectId != null
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(if (fixed) Res.string.add_imap_reconnect_title else Res.string.provider_imap)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!fixed) Text(stringResource(Res.string.add_imap_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    form.email, { update(form.copy(email = it.trim())) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.add_imap_email)) }, textStyle = LtrField, singleLine = true, enabled = !fixed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    form.password, { update(form.copy(password = it)) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.add_imap_password)) }, textStyle = LtrField, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (!fixed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            form.host, { update(form.copy(host = it.trim())) }, Modifier.weight(1f),
                            label = { Text(stringResource(Res.string.add_imap_host)) }, textStyle = LtrField, singleLine = true,
                        )
                        OutlinedTextField(
                            form.port, { update(form.copy(port = it.filter(Char::isDigit).take(5))) }, Modifier.width(110.dp),
                            label = { Text(stringResource(Res.string.add_imap_port)) }, textStyle = LtrField, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ImapSecurity.entries.forEachIndexed { i, sec ->
                            SegmentedButton(
                                selected = form.security == sec,
                                onClick = { update(form.copy(security = sec, port = if (sec == ImapSecurity.Tls) "993" else "143")) },
                                shape = SegmentedButtonDefaults.itemShape(i, ImapSecurity.entries.size),
                            ) { Text(if (sec == ImapSecurity.Tls) "SSL/TLS" else "STARTTLS") }
                        }
                    }
                    OutlinedTextField(
                        form.username, { update(form.copy(username = it.trim())) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.add_imap_username)) }, textStyle = LtrField, singleLine = true,
                        placeholder = { Text(form.email) },
                    )
                    TextButton(onClick = { onIntent(AppIntent.DetectImapServer) }, enabled = '@' in form.email && !form.busy) {
                        Text(stringResource(Res.string.add_imap_detect))
                    }
                }
                form.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(AppIntent.SubmitImapForm) }, enabled = !form.busy && '@' in form.email && form.password.isNotBlank()) {
                if (form.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.add_imap_save))
            }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun ImapLoginException.Reason.text(): String = when (this) {
    ImapLoginException.Reason.HostNotFound -> stringResource(Res.string.add_imap_error_host)
    ImapLoginException.Reason.Tls -> stringResource(Res.string.add_imap_error_tls)
    ImapLoginException.Reason.Credentials -> stringResource(Res.string.add_imap_error_credentials)
    ImapLoginException.Reason.Other -> stringResource(Res.string.add_imap_error_other)
}
