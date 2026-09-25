package co.abaye.mailtice.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import java.io.File
import java.util.Properties

private fun databasePath(): String = joinPath(Platform.appDir(), DATABASE_NAME)

internal actual fun createSqlDriver(): SqlDriver {
    val props = Properties().apply {
        // sqlite-jdbc reads these as connection pragmas.
        put("foreign_keys", "true")
        put("journal_mode", "WAL")
    }
    // The schema argument creates the tables on first run and migrates by user_version afterwards.
    return JdbcSqliteDriver("jdbc:sqlite:${databasePath()}", props, MailDatabase.Schema)
}

internal actual fun databaseFiles(): List<String> = databasePath().let { listOf(it, "$it-wal", "$it-shm") }

internal actual fun fileSize(path: String): Long = File(path).takeIf { it.isFile }?.length() ?: 0L
