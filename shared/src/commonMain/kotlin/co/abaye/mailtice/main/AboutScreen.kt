package co.abaye.mailtice.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
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
import mailtice.shared.generated.resources.about_by
import mailtice.shared.generated.resources.about_developed_by
import mailtice.shared.generated.resources.about_lib_compose
import mailtice.shared.generated.resources.about_lib_nucleus
import mailtice.shared.generated.resources.about_libraries
import mailtice.shared.generated.resources.about_licenses
import mailtice.shared.generated.resources.about_open_source
import mailtice.shared.generated.resources.about_report_bug
import mailtice.shared.generated.resources.about_version
import mailtice.shared.generated.resources.app_icon
import mailtice.shared.generated.resources.app_name
import mailtice.shared.generated.resources.app_tagline
import mailtice.shared.generated.resources.developer_avatar
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val DEVELOPER = "abaye"
private const val DEVELOPER_SITE_LABEL = "abaye.co"
private const val DEVELOPER_SITE = "https://abaye.co"
private const val REPO_LABEL = "abaye123/Mailtice"
private const val REPO_URL = "https://github.com/abaye123/Mailtice"
private const val ISSUES_URL = "https://github.com/abaye123/Mailtice/issues/new"

// TEMPORARY: the app icon is Icons8's "Gmail Logo" until Mailtice has its own. Icons8's free licence
// requires this credit; delete it (and ICON_CREDIT_* below) together with the icon files.
private const val ICON_CREDIT_TEXT = "Gmail Logo icon by Icons8"
private val ICON_CREDIT_LINKS = listOf(
    "Gmail Logo" to "https://icons8.com/icon/td499GRWwrWC/gmail-logo",
    "Icons8" to "https://icons8.com",
)

/** A library the app is built on, named with what it does here. Names, authors and licences stay as published. */
private class KeyLibrary(val name: String, val purpose: StringResource, val author: String, val license: String, val url: String)

private val KeyLibraries = listOf(
    KeyLibrary(
        "Compose Multiplatform",
        Res.string.about_lib_compose,
        "JetBrains",
        "Apache-2.0",
        "https://github.com/JetBrains/compose-multiplatform",
    ),
    KeyLibrary("Nucleus", Res.string.about_lib_nucleus, "NucleusFramework", "MIT", "https://github.com/NucleusFramework/Nucleus"),
)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    Box(modifier.fillMaxSize()) {
        // At the start of the page (the right in Hebrew), like the other settings pages.
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize().align(Alignment.TopStart),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            header = {
                item { AboutHeader() }
            },
        )
    }
}

@Composable
private fun AboutHeader() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(Res.drawable.app_icon), null, Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                // Only a packaged build reports a version; a run from source shows none.
                val version = Platform.appVersion
                if (version.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.about_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(stringResource(Res.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(Res.drawable.developer_avatar), null, Modifier.size(76.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.about_developed_by, DEVELOPER),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.about_open_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                LinkedText(DEVELOPER_SITE_LABEL, listOf(DEVELOPER_SITE_LABEL to DEVELOPER_SITE), style = MaterialTheme.typography.bodyLarge)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GitHubLink(REPO_LABEL, REPO_URL)
            GitHubLink(stringResource(Res.string.about_report_bug), ISSUES_URL)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(Res.string.about_libraries))
            KeyLibraries.forEach { library ->
                Column {
                    Text(
                        "${stringResource(library.purpose)}: ${library.name}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(Res.string.about_by, library.author, library.license),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyLibraries.forEach { GitHubLink(it.name, it.url) }
            }
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

/** A GitHub mark and a link, as the about page's link rows show them. */
@Composable
private fun GitHubLink(label: String, url: String) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { uri.openUri(url) }.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(GitHubMark, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
