package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.ui.Tooltip
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.code_copy
import mailtice.shared.generated.resources.code_label
import org.jetbrains.compose.resources.stringResource

/** A list row's one-time code: a click copies it, without opening the message. */
@Composable
internal fun CodeChip(code: String, onCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Tooltip(stringResource(Res.string.code_copy)) {
        Surface(
            onClick = onCopy,
            shape = RoundedCornerShape(8.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                Text(code, style = CodeStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize))
            }
        }
    }
}

/** At the top of a message with a one-time code: the code, large, and a button that copies it. */
@Composable
internal fun CodeBar(code: String, onCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Password, null, Modifier.size(20.dp), tint = colors.onPrimaryContainer)
        Text(stringResource(Res.string.code_label), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
        Text(
            code,
            Modifier.weight(1f).padding(vertical = 10.dp),
            style = CodeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = colors.onPrimaryContainer,
        )
        TextButton(onClick = onCopy) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.code_copy), Modifier.padding(start = 6.dp))
        }
    }
}

/** Codes read left to right in either language, in even-width digits. */
private val CodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    textDirection = TextDirection.Ltr,
)
