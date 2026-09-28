package co.abaye.mailtice.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.abaye.mailtice.app.AddAccountStep
import co.abaye.mailtice.app.AppIntent
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.ImapForm
import co.abaye.mailtice.app.SignInPhase
import co.abaye.mailtice.auth.BrowserProfile
import co.abaye.mailtice.domain.ImapSecurity
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.ImapLoginException
import co.abaye.mailtice.ui.EmptyIllustration
import co.abaye.mailtice.ui.Illustration
import co.abaye.mailtice.ui.TooltipIconButton
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
import mailtice.shared.generated.resources.browser_body
import mailtice.shared.generated.resources.browser_default
import mailtice.shared.generated.resources.browser_title
import mailtice.shared.generated.resources.dialog_cancel
import mailtice.shared.generated.resources.provider_imap
import mailtice.shared.generated.resources.signin_back
import mailtice.shared.generated.resources.signin_browser_body
import mailtice.shared.generated.resources.signin_browser_title
import mailtice.shared.generated.resources.signin_close
import mailtice.shared.generated.resources.signin_connecting_body
import mailtice.shared.generated.resources.signin_connecting_title
import mailtice.shared.generated.resources.signin_done_body
import mailtice.shared.generated.resources.signin_done_title
import mailtice.shared.generated.resources.signin_failed_body
import mailtice.shared.generated.resources.signin_failed_title
import mailtice.shared.generated.resources.signin_open_mail
import mailtice.shared.generated.resources.signin_reconnected_title
import mailtice.shared.generated.resources.signin_retry
import mailtice.shared.generated.resources.web_profile_body
import mailtice.shared.generated.resources.web_profile_title
import org.jetbrains.compose.resources.stringResource

/** Addresses, hosts, ports and passwords read left-to-right even in a Hebrew interface. */
private val LtrField = TextStyle(textDirection = TextDirection.Ltr)

/**
 * Adding (or reconnecting) an account happens entirely in this dialog: pick a provider, then one
 * screen per sign-in phase - waiting for the browser, connecting, connected or failed - each with
 * its own illustration, so the user always sees where things stand without looking behind it.
 */
@Composable
fun AddAccountDialog(state: AppState, onIntent: (AppIntent) -> Unit) {
    val step = state.addAccount ?: return
    val close = { onIntent(AppIntent.CloseAddAccount) }
    when (step) {
        AddAccountStep.ChooseProvider -> FlowDialog(onDismiss = close) {
            ProviderChooser(state, onIntent, close)
        }

        is AddAccountStep.Imap -> ImapFormDialog(step.form, onIntent, close)

        is AddAccountStep.ChooseBrowser -> FlowDialog(onDismiss = close) {
            BrowserChooser(step, state, onIntent, close)
        }

        is AddAccountStep.SignIn -> FlowDialog(
            // A running sign-in is cancelled from its own button, not by a stray click outside.
            onDismiss = { if (step.phase == SignInPhase.Done || step.phase == SignInPhase.Failed) close() },
        ) {
            SignInScreen(step, onIntent, close)
        }
    }
}

/** The shared frame: a rounded sheet, the same width at every step so it never jumps around. */
@Composable
private fun FlowDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            content = content,
        )
    }
}

@Composable
private fun ProviderChooser(state: AppState, onIntent: (AppIntent) -> Unit, close: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.accounts_add), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.dialog_cancel), close)
        }
        Text(stringResource(Res.string.add_choose_provider), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Only providers this build and platform can actually sign in to.
        state.availableProviders.forEach { kind ->
            ProviderRow(kind) { onIntent(AppIntent.ChooseProvider(kind)) }
        }
        Text(
            stringResource(Res.string.add_provider_imap_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderRow(kind: ProviderKind, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LetterAvatar(kind.badgeLetter(), kind.badgeColor(), size = 36.dp)
        Text(kind.label(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
    }
}

/** A letter and colour per provider - not their logos, just enough to find the right row fast. */
private fun ProviderKind.badgeLetter(): String = when (this) {
    ProviderKind.Gmail -> "G"
    ProviderKind.Microsoft -> "M"
    ProviderKind.Yahoo -> "Y"
    ProviderKind.Imap -> "@"
}

private fun ProviderKind.badgeColor(): Color = when (this) {
    ProviderKind.Gmail -> Color(0xFFD93025)
    ProviderKind.Microsoft -> Color(0xFF0F6CBD)
    ProviderKind.Yahoo -> Color(0xFF6001D2)
    ProviderKind.Imap -> Color(0xFF5A5D72)
}

/**
 * Several browser profiles on this computer: the sign-in page should open in the one already signed
 * in to the right Google (or Microsoft) account. The last pick is marked and listed first.
 */
@Composable
private fun BrowserChooser(step: AddAccountStep.ChooseBrowser, state: AppState, onIntent: (AppIntent) -> Unit, close: () -> Unit) {
    val last = state.data.settings.browserProfile
    val profiles = state.browserProfiles.sortedBy { if (it.key == last) 0 else 1 }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.browser_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.dialog_cancel), close)
        }
        Text(
            stringResource(Res.string.browser_body, step.kind.label()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { profile ->
                BrowserRow(
                    title = profile.name,
                    subtitle = listOf(profile.browser, profile.email).filter { it.isNotBlank() }.joinToString(" · "),
                    letter = profile.name,
                    color = profile.browserColor(),
                    marked = profile.key == last,
                ) { onIntent(AppIntent.ChooseBrowser(profile.key)) }
            }
            BrowserRow(
                title = stringResource(Res.string.browser_default),
                subtitle = null,
                letter = "",
                color = MaterialTheme.colorScheme.outline,
                marked = last.isEmpty(),
            ) { onIntent(AppIntent.ChooseBrowser(null)) }
        }
    }
}

/**
 * Which browser profile an account's web mail opens in, asked the first time when no profile is
 * signed in with the account's address. The answer is remembered.
 */
@Composable
fun WebProfileDialog(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val request = state.webProfileRequest ?: return
    val account = state.account(request.accountId) ?: return
    val close = { onIntent(AppIntent.DismissWebProfile) }
    FlowDialog(onDismiss = close) {
        Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.web_profile_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TooltipIconButton(Icons.Outlined.Close, stringResource(Res.string.dialog_cancel), close)
            }
            Text(
                stringResource(Res.string.web_profile_body, account.email),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.browserProfiles.forEach { profile ->
                    BrowserRow(
                        title = profile.name,
                        subtitle = listOf(profile.browser, profile.email).filter { it.isNotBlank() }.joinToString(" · "),
                        letter = profile.name,
                        color = profile.browserColor(),
                        marked = false,
                    ) { onIntent(AppIntent.ChooseWebProfile(profile.key)) }
                }
                BrowserRow(
                    title = stringResource(Res.string.browser_default),
                    subtitle = null,
                    letter = "",
                    color = MaterialTheme.colorScheme.outline,
                    marked = false,
                ) { onIntent(AppIntent.ChooseWebProfile(null)) }
            }
        }
    }
}

@Composable
private fun BrowserRow(title: String, subtitle: String?, letter: String, color: Color, marked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (marked) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (letter.isEmpty()) {
            Icon(Icons.Outlined.Public, null, Modifier.size(36.dp).padding(4.dp), tint = colors.onSurfaceVariant)
        } else {
            LetterAvatar(letter, color, size = 36.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.merge(LtrField),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
    }
}

/** Each browser's own colour, so the same profile name in two browsers is still told apart. */
private fun BrowserProfile.browserColor(): Color = when (browser) {
    "Chrome" -> Color(0xFF1A73E8)
    "Edge" -> Color(0xFF0C8484)
    "Brave" -> Color(0xFFE3511C)
    else -> Color(0xFF5A5D72)
}

@Composable
private fun SignInScreen(step: AddAccountStep.SignIn, onIntent: (AppIntent) -> Unit, close: () -> Unit) {
    val provider = step.kind.label()
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val illustration = when (step.phase) {
            SignInPhase.Browser -> Illustration.Browser
            SignInPhase.Connecting -> Illustration.Syncing
            SignInPhase.Done -> Illustration.Connected
            SignInPhase.Failed -> Illustration.SignInFailed
        }
        Crossfade(illustration, label = "signin-illustration") { EmptyIllustration(it, size = 150.dp) }
        Text(
            when (step.phase) {
                SignInPhase.Browser -> stringResource(Res.string.signin_browser_title)

                SignInPhase.Connecting -> stringResource(Res.string.signin_connecting_title)

                SignInPhase.Done ->
                    stringResource(if (step.reconnectId != null) Res.string.signin_reconnected_title else Res.string.signin_done_title)

                SignInPhase.Failed -> stringResource(Res.string.signin_failed_title)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            when (step.phase) {
                SignInPhase.Browser -> stringResource(Res.string.signin_browser_body, provider)
                SignInPhase.Connecting -> stringResource(Res.string.signin_connecting_body, provider)
                SignInPhase.Done -> stringResource(Res.string.signin_done_body)
                SignInPhase.Failed -> stringResource(Res.string.signin_failed_body, provider)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (step.phase == SignInPhase.Done && step.email.isNotBlank()) {
            Text(step.email, style = MaterialTheme.typography.bodyLarge.merge(LtrField), fontWeight = FontWeight.Medium)
        }
        if (step.phase == SignInPhase.Browser || step.phase == SignInPhase.Connecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth(0.6f).padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step.phase) {
                SignInPhase.Browser, SignInPhase.Connecting -> TextButton(onClick = { onIntent(AppIntent.CancelSignIn) }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }

                SignInPhase.Done -> {
                    TextButton(onClick = close) { Text(stringResource(Res.string.signin_close)) }
                    if (step.accountId.isNotEmpty()) {
                        Button(onClick = { onIntent(AppIntent.OpenAccount(step.accountId)) }) {
                            Text(stringResource(Res.string.signin_open_mail))
                        }
                    }
                }

                SignInPhase.Failed -> {
                    TextButton(onClick = { onIntent(AppIntent.CancelSignIn) }) { Text(stringResource(Res.string.signin_back)) }
                    Button(onClick = { onIntent(AppIntent.RetrySignIn) }) { Text(stringResource(Res.string.signin_retry)) }
                }
            }
        }
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
                    form.email,
                    { update(form.copy(email = it.trim())) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.add_imap_email)) },
                    textStyle = LtrField,
                    singleLine = true,
                    enabled = !fixed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    form.password,
                    { update(form.copy(password = it)) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.add_imap_password)) },
                    textStyle = LtrField,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (!fixed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            form.host,
                            { update(form.copy(host = it.trim())) },
                            Modifier.weight(1f),
                            label = { Text(stringResource(Res.string.add_imap_host)) },
                            textStyle = LtrField,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            form.port,
                            { update(form.copy(port = it.filter(Char::isDigit).take(5))) },
                            Modifier.width(110.dp),
                            label = { Text(stringResource(Res.string.add_imap_port)) },
                            textStyle = LtrField,
                            singleLine = true,
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
                        form.username,
                        { update(form.copy(username = it.trim())) },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.add_imap_username)) },
                        textStyle = LtrField,
                        singleLine = true,
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
            TextButton(
                onClick = { onIntent(AppIntent.SubmitImapForm) },
                enabled =
                !form.busy && '@' in form.email && form.password.isNotBlank(),
            ) {
                if (form.busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.add_imap_save))
                }
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
