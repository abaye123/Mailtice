package co.abaye.mailtice.data

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_NAME = "mailtice.db"

/** Desktop: JDBC SQLite in the app dir. Android: the platform SQLite. Foreign keys on in both. */
internal expect fun createSqlDriver(): SqlDriver

/** Every file that makes up the database on disk (main file, -wal, -shm), for the storage screen. */
internal expect fun databaseFiles(): List<String>

/** Size in bytes of a file, 0 when it does not exist. */
internal expect fun fileSize(path: String): Long
