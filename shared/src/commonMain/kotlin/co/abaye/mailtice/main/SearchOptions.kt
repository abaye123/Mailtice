package co.abaye.mailtice.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.search.MailSearch
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.search_any_time
import mailtice.shared.generated.resources.search_apply
import mailtice.shared.generated.resources.search_clear
import mailtice.shared.generated.resources.search_date
import mailtice.shared.generated.resources.search_exclude
import mailtice.shared.generated.resources.search_from
import mailtice.shared.generated.resources.search_has_attachment
import mailtice.shared.generated.resources.search_last_day
import mailtice.shared.generated.resources.search_last_month
import mailtice.shared.generated.resources.search_last_week
import mailtice.shared.generated.resources.search_last_year
import mailtice.shared.generated.resources.search_older_year
import mailtice.shared.generated.resources.search_options
import mailtice.shared.generated.resources.search_subject
import mailtice.shared.generated.resources.search_to
import mailtice.shared.generated.resources.search_unread
import mailtice.shared.generated.resources.search_words
import org.jetbrains.compose.resources.stringResource

/** The date choices of the panel, each an operator Gmail and the parser both understand. */
private enum class DateRange(val operator: String) {
    Any(""),
    Day("newer_than:1d"),
    Week("newer_than:7d"),
    Month("newer_than:1m"),
    Year("newer_than:1y"),
    OlderThanYear("older_than:1y"),
}

/**
 * Gmail's "search options": fill in fields instead of remembering operators. It opens with the
 * current search already split into its fields, and writes the result back to the search box as
 * operators (from:, subject:, -word...), so the box always shows exactly what is being searched.
 */
@Composable
internal fun SearchOptions(current: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val parsed = remember(current) { MailSearch.parse(current, Platform.now()) }
    var from by remember { mutableStateOf(parsed.from) }
    var to by remember { mutableStateOf(parsed.to) }
    var subject by remember { mutableStateOf(parsed.subject) }
    var words by remember { mutableStateOf(parsed.words.joinToString(" ") { if (' ' in it) "\"$it\"" else it }) }
    var exclude by remember { mutableStateOf(parsed.excluded.joinToString(" ")) }
    var range by remember { mutableStateOf(DateRange.entries.firstOrNull { it.operator.isNotEmpty() && it.operator in current } ?: DateRange.Any) }
    var attachment by remember { mutableStateOf(parsed.hasAttachment) }
    var unread by remember { mutableStateOf(parsed.unread == true) }

    fun build(): String = buildList {
        fun q(v: String) = if (v.any { it.isWhitespace() }) "\"${v.trim()}\"" else v.trim()
        if (from.isNotBlank()) add("from:" + q(from))
        if (to.isNotBlank()) add("to:" + q(to))
        if (subject.isNotBlank()) add("subject:" + q(subject))
        if (words.isNotBlank()) add(words.trim())
        exclude.split(' ', ',').filter { it.isNotBlank() }.forEach { add("-" + it.trim()) }
        if (range != DateRange.Any) add(range.operator)
        if (attachment) add("has:attachment")
        if (unread) add("is:unread")
    }.joinToString(" ")

    Column(Modifier.width(440.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.search_options), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Field(stringResource(Res.string.search_from), from) { from = it }
        Field(stringResource(Res.string.search_to), to) { to = it }
        Field(stringResource(Res.string.search_subject), subject) { subject = it }
        Field(stringResource(Res.string.search_words), words) { words = it }
        Field(stringResource(Res.string.search_exclude), exclude) { exclude = it }
        Text(stringResource(Res.string.search_date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DateRange.entries.forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label()) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = attachment, onCheckedChange = { attachment = it })
            Text(stringResource(Res.string.search_has_attachment), Modifier.weight(1f))
            Checkbox(checked = unread, onCheckedChange = { unread = it })
            Text(stringResource(Res.string.search_unread), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = {
                onSearch("")
                onDismiss()
            }) { Text(stringResource(Res.string.search_clear)) }
            Button(onClick = {
                onSearch(build())
                onDismiss()
            }) { Text(stringResource(Res.string.search_apply)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.merge(ContentDirection),
    )
}

@Composable
private fun DateRange.label(): String = when (this) {
    DateRange.Any -> stringResource(Res.string.search_any_time)
    DateRange.Day -> stringResource(Res.string.search_last_day)
    DateRange.Week -> stringResource(Res.string.search_last_week)
    DateRange.Month -> stringResource(Res.string.search_last_month)
    DateRange.Year -> stringResource(Res.string.search_last_year)
    DateRange.OlderThanYear -> stringResource(Res.string.search_older_year)
}
