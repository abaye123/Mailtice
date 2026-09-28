package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.decodeSnapshot
import co.abaye.mailtice.data.encodeSnapshot
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.provider.RemoteFolder
import co.abaye.mailtice.provider.RemoteMessage
import co.abaye.mailtice.provider.SyncBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BadgeCountTest {
    private fun remote(id: String, at: Long, unread: Boolean, folder: String) =
        RemoteMessage(id, "", 0, "", "x@y.com", "", "s $id", "", at, unread, false, false, 0, setOf(folder), null)

    @Test
    fun onlyRecentUnreadInboxMailCounts() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(Account("a", ProviderKind.Imap, "a@y.com"))
        repo.mergeFolders("a", listOf(RemoteFolder("INBOX", "Inbox", FolderRole.Inbox), RemoteFolder("Spam", "Spam", FolderRole.Spam)))
        repo.applyBatch(
            "a",
            SyncBatch(
                listOf(
                    remote("INBOX/1", 1_000, unread = true, folder = "INBOX"),
                    remote("INBOX/2", 5_000, unread = true, folder = "INBOX"),
                    remote("INBOX/3", 6_000, unread = false, folder = "INBOX"),
                    remote("Spam/4", 7_000, unread = true, folder = "Spam"),
                ),
            ),
        )
        assertEquals(2L, repo.unreadSince(0).first())
        // Old unread mail, read mail and spam stay out of a recent window.
        assertEquals(1L, repo.unreadSince(2_000).first())
    }

    @Test
    fun theBadgeWindowIsRemembered() {
        assertEquals(7, decodeSnapshot(encodeSnapshot(AppData(UserSettings(badgeMaxAgeDays = 7)))).settings.badgeMaxAgeDays)
        // A value no longer offered falls back to counting everything.
        assertEquals(0, decodeSnapshot("badgeMaxAgeDays=5").settings.badgeMaxAgeDays)
    }
}
