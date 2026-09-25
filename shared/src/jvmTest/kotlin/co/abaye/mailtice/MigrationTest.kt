package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.db.MailDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** A database written by version 1 of the schema must open, keep its rows, and gain the new column. */
class MigrationTest {
    @Test
    fun version1FolderTableUpgrades() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // The folder table exactly as schema version 1 created it.
        driver.execute(
            null,
            """
            CREATE TABLE folder (
              accountId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL,
              sync INTEGER NOT NULL DEFAULT 0, notify INTEGER NOT NULL DEFAULT 0,
              uidValidity INTEGER NOT NULL DEFAULT 0, uidNext INTEGER NOT NULL DEFAULT 0,
              highestModSeq INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (accountId, id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO folder(accountId, id, name, role, sync, notify) VALUES ('a', 'INBOX', 'Inbox', 'Inbox', 1, 1)", 0)
        driver.execute(null, "INSERT INTO folder(accountId, id, name, role, sync, notify) VALUES ('a', 'SPAM', 'Spam', 'Spam', 0, 0)", 0)

        MailDatabase.Schema.migrate(driver, 1, MailDatabase.Schema.version)

        val rows = driver.executeQuery(
            null,
            "SELECT id, sync, notify, color FROM folder ORDER BY id",
            { cursor ->
                val out = mutableListOf<String>()
                while (cursor.next().value) out += "${cursor.getString(0)}:${cursor.getLong(1)}:${cursor.getLong(2)}:${cursor.getString(3)}"
                app.cash.sqldelight.db.QueryResult.Value(out)
            },
            0,
        ).value
        // Everything syncs now; notifications untouched; no colour until the next folder refresh.
        assertEquals(listOf("INBOX:1:1:", "SPAM:1:0:"), rows)
        assertEquals(2L, MailDatabase.Schema.version)
    }
}
