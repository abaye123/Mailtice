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
        // The account table as version 1 created it (6 -> 7 adds a column to it).
        driver.execute(
            null,
            """
            CREATE TABLE account (
              id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, email TEXT NOT NULL, label TEXT NOT NULL DEFAULT '',
              color TEXT NOT NULL, notify INTEGER NOT NULL DEFAULT 1, retentionDays INTEGER NOT NULL DEFAULT 30,
              capabilities TEXT NOT NULL DEFAULT '', imapHost TEXT, imapPort INTEGER, imapSecurity TEXT, username TEXT,
              syncCursor TEXT NOT NULL DEFAULT '', sortOrder INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO account(id, kind, email, color, createdAt) VALUES ('a', 'Gmail', 'a@example.com', 'Blue', 1)", 0)
        // The body table as version 1 created it, with a stored body.
        driver.execute(
            null,
            """
            CREATE TABLE message_body (
              accountId TEXT NOT NULL, messageId TEXT NOT NULL, textBody TEXT NOT NULL DEFAULT '',
              htmlBody TEXT NOT NULL DEFAULT '', attachments TEXT NOT NULL DEFAULT '', PRIMARY KEY (accountId, messageId)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO message_body(accountId, messageId, textBody) VALUES ('a', 'm1', 'hello')", 0)
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
        assertEquals(7L, MailDatabase.Schema.version)
        // 2 -> 3 added the scheduled-send queue.
        val tables = driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scheduled_mail'",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(cursor.next().value) },
            0,
        ).value
        assertEquals(true, tables)
        // 5 -> 6: stored bodies stay, marked static.
        val body = driver.executeQuery(
            null,
            "SELECT textBody, amp FROM message_body",
            { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) "${cursor.getString(0)}:${cursor.getLong(1)}" else "")
            },
            0,
        ).value
        assertEquals("hello:0", body)
        // 6 -> 7: an account already here is a connected one, not a copy from a transfer file.
        val fileOnly = driver.executeQuery(
            null,
            "SELECT fileOnly FROM account WHERE id = 'a'",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else -1L) },
            0,
        ).value
        assertEquals(0L, fileOnly)
    }
}
