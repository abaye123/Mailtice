package co.abaye.mailtice.data

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.platform.androidContext
import java.io.File

internal actual fun createSqlDriver(): SqlDriver = AndroidSqliteDriver(
    schema = MailDatabase.Schema,
    context = androidContext(),
    name = DATABASE_NAME,
    callback = object : AndroidSqliteDriver.Callback(MailDatabase.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    },
)

internal actual fun databaseFiles(): List<String> =
    androidContext().getDatabasePath(DATABASE_NAME).absolutePath.let { listOf(it, "$it-wal", "$it-shm", "$it-journal") }

internal actual fun fileSize(path: String): Long = File(path).takeIf { it.isFile }?.length() ?: 0L
