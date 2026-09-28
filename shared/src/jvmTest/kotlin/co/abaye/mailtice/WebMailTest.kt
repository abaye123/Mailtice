package co.abaye.mailtice

import co.abaye.mailtice.data.decodeSnapshot
import co.abaye.mailtice.data.encodeSnapshot
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.domain.gmailUrl
import co.abaye.mailtice.domain.webMailUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebMailTest {
    @Test
    fun eachProviderOpensItsOwnSite() {
        assertEquals("https://mail.google.com/mail/?authuser=a@x.com#inbox", webMailUrl(Account("1", ProviderKind.Gmail, "a@x.com")))
        // A plus address keeps its "+", which a query would otherwise read as a space.
        assertEquals("https://mail.google.com/mail/?authuser=a%2Bnews@x.com#all/m1", gmailUrl("a+news@x.com", "all/m1"))
        assertEquals("https://outlook.live.com/mail/0/", webMailUrl(Account("2", ProviderKind.Microsoft, "a@hotmail.co.il")))
        assertEquals("https://outlook.office.com/mail/", webMailUrl(Account("3", ProviderKind.Microsoft, "a@company.com")))
        assertEquals("https://mail.aol.com/", webMailUrl(Account("4", ProviderKind.Yahoo, "a@aol.com")))
        assertNull(webMailUrl(Account("5", ProviderKind.Imap, "a@server.org")))
    }

    @Test
    fun rememberedBrowserProfilesSurviveARestart() {
        val browsers = mapOf("acc-1" to "Chrome|Profile 1", "acc-2" to "")
        val data = AppData(UserSettings(accountBrowsers = browsers, closeReaderOnSwitch = false))
        val back = decodeSnapshot(encodeSnapshot(data)).settings
        assertEquals(browsers, back.accountBrowsers)
        assertEquals(false, back.closeReaderOnSwitch)
        assertTrue(decodeSnapshot("").settings.closeReaderOnSwitch)
    }
}
