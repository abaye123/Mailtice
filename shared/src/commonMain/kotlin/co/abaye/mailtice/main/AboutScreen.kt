package co.abaye.mailtice.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.ui.GitHubMark
import co.abaye.mailtice.ui.LinkedText
import co.abaye.mailtice.ui.SectionHeader
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import mailtice.shared.generated.resources.Res
import mailtice.shared.generated.resources.about_licenses
import mailtice.shared.generated.resources.about_repo
import mailtice.shared.generated.resources.about_version
import mailtice.shared.generated.resources.app_icon
import mailtice.shared.generated.resources.app_name
import mailtice.shared.generated.resources.app_tagline
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val REPO_LABEL = "github.com/abaye123/Mailtice"
private const val REPO_URL = "https://github.com/abaye123/Mailtice"

// TEMPORARY: the app icon is Icons8's "Gmail Logo" until Mailtice has its own. Icons8's free licence
// requires this credit; delete it (and ICON_CREDIT_* below) together with the icon files.
private const val ICON_CREDIT_TEXT = "Gmail Logo icon by Icons8"
private val ICON_CREDIT_LINKS = listOf(
    "Gmail Logo" to "https://icons8.com/icon/td499GRWwrWC/gmail-logo",
    "Icons8" to "https://icons8.com",
)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    Box(modifier.fillMaxSize()) {
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            header = {
                item { AboutHeader() }
            },
        )
    }
}

@Composable
private fun AboutHeader() {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(Res.drawable.app_icon), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Column {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(Res.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Only a packaged build reports a version; a run from source shows nothing here.
        val version = Platform.appVersion
        if (version.isNotEmpty()) {
            Text(
                stringResource(Res.string.about_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(GitHubMark, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkedText(
                text = stringResource(Res.string.about_repo, REPO_LABEL),
                links = listOf(REPO_LABEL to REPO_URL),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Attribution wording is fixed by the licence, so it is not translated.
        LinkedText(
            text = ICON_CREDIT_TEXT,
            links = ICON_CREDIT_LINKS,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        )
        SectionHeader(stringResource(Res.string.about_licenses))
    }
}
