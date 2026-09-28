package co.abaye.mailtice

import co.abaye.mailtice.data.decodeSnapshot
import co.abaye.mailtice.data.encodeSnapshot
import co.abaye.mailtice.domain.AccentColor
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.AppFont
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.ListDensity
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.domain.ReadingPane
import co.abaye.mailtice.domain.ThemeMode
import co.abaye.mailtice.domain.UiLanguage
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.provider.HtmlText
import co.abaye.mailtice.provider.ImapAutoConfig
import co.abaye.mailtice.provider.gmail.splitAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotCodecTest {
    @Test
    fun settingsRoundTrip() {
        val data = AppData(
            UserSettings(
                theme = ThemeMode.Dark, accent = AccentColor.Teal, density = ListDensity.Spacious, font = AppFont.Heebo,
                paneStyle = PaneStyle.Lines,
                uiLanguage = UiLanguage.English, pollSeconds = 120,
                readingPane = ReadingPane.Off, hiddenFolders = setOf("acc-1/Label_7"),
                pinnedLabels = setOf("acc-1/Label_3", "acc-2/Receipts"), collapsedAccounts = setOf("acc-2"),
            ),
        )
        assertEquals(data, decodeSnapshot(encodeSnapshot(data)))
    }

    @Test
    fun standardFoldersCanNoLongerBeHidden() {
        val data = decodeSnapshot("hiddenFolders=view:Spamacc-1/Label_7")
        assertEquals(setOf("acc-1/Label_7"), data.settings.hiddenFolders)
    }

    @Test
    fun unknownAndBrokenKeysFallBack() {
        val data = decodeSnapshot("theme=Neon\npollSeconds=7\nnonsense\n")
        assertEquals(UserSettings(), data.settings)
    }

    @Test
    fun capabilitiesRoundTrip() {
        val caps =
            Capabilities(
                markRead = true,
                archive = false,
                labels = true,
                openInWeb = false,
                incremental = true,
                idle = true,
                labelColors = true,
            )
        assertEquals(caps, Capabilities.decode(caps.encode()))
        assertEquals(Capabilities(), Capabilities.decode(""))
    }

    @Test
    fun htmlBecomesReadableText() {
        val text = HtmlText.toText("<style>p{}</style><p>שלום&nbsp;<b>עולם</b></p><a href=\"https://x.co\">קישור</a>")
        assertEquals("שלום עולם\nקישור [https://x.co]", text)
    }

    @Test
    fun addressSplits() {
        assertEquals("Dana Cohen" to "dana@x.com", splitAddress("\"Dana Cohen\" <dana@x.com>"))
        assertEquals("" to "a@b.c", splitAddress("a@b.c"))
    }

    @Test
    fun autoconfigParses() {
        val xml = """<clientConfig><emailProvider><incomingServer type="imap"><hostname>imap.x.com</hostname>
            <port>143</port><socketType>STARTTLS</socketType></incomingServer></emailProvider></clientConfig>"""
        val server = ImapAutoConfig.parseAutoconfig(xml)!!
        assertEquals("imap.x.com", server.host)
        assertEquals(143, server.port)
    }
}
