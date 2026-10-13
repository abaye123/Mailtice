package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.diag.AppLog
import co.abaye.mailtice.diag.DiagnosticsReport
import co.abaye.mailtice.diag.redact
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsTest {
    @Test
    fun theLogKeepsNoAddressLinkOrHost() {
        assertEquals(
            "Sync: round failed: login failed for <address> at <host> (<ip>), see <link>",
            redact(
                "Sync: round failed: login failed for dana.cohen@example.co.il at imap.private-domain.org (10.0.0.12), see https://x.example/a?b=c",
            ),
        )
        assertEquals("Sync: round failed, attempt 1: SQLiteException", redact("Sync: round failed, attempt 1: SQLiteException"))
    }

    @Test
    fun theReportHoldsCountsAndNothingPersonal() {
        val email = "dana.cohen@example.co.il"
        val secrets =
            listOf(
                email,
                "Dana Work",
                "Medical letters",
                "Lab results for Dana",
                "from.someone@clinic.example",
                "Private snippet",
                "acc-77",
            )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        val account =
            Account("acc-77", ProviderKind.Gmail, email, label = "Dana Work", capabilities = Capabilities.Gmail, syncCursor = "123")
        repo.addAccount(account)
        repo.mergeFolders(
            "acc-77",
            listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("Label_9", "Medical letters", FolderRole.Other)),
        )
        val message = RemoteMessage(
            "m1", "t1", 0, "Clinic", "from.someone@clinic.example", email, "Lab results for Dana", "Private snippet",
            System.currentTimeMillis(), true, false, false, 0, setOf("INBOX", "Label_9"), null,
        )
        repo.applyBatch("acc-77", SyncBatch(newMessages = listOf(message)))
        AppLog.log("Sync: round failed, attempt 1: AuthenticationFailedException: bad login for $email")
        val state = AppState(accounts = listOf(account), syncErrors = mapOf("acc-77" to "Refused for $email by imap.clinic.example"))

        val report = DiagnosticsReport.build(state, repo.diagnostics(), System.currentTimeMillis())

        secrets.forEach { assertFalse(report.contains(it), "the report gives away \"$it\"") }
        assertFalse(report.contains("clinic.example"))
        assertTrue("Account 1 (Gmail)" in report)
        assertTrue("Inbox: 1, unread 1" in report)
        assertTrue("labels and other folders: 1" in report)
        assertTrue("history cursor yes" in report)
    }
}
