package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.dev.DemoMailProvider
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountStatus
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.provider.MailProvider
import co.abaye.mailtice.provider.ProviderException
import co.abaye.mailtice.provider.SyncBatch
import co.abaye.mailtice.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** A first sync that hits a rate limit is still a first sync, not a lost connection. */
class FirstSyncStatusTest {
    private val account = Account("acc", ProviderKind.Gmail, "a@example.com")

    private class Flaky(var failures: Int) : MailProvider by DemoMailProvider() {
        override suspend fun sync(account: Account, folders: List<Folder>, sinceMillis: Long?, knownIds: Set<String>): SyncBatch {
            if (failures > 0) {
                failures--
                throw ProviderException.Transient("HTTP 429: rate limited")
            }
            return SyncBatch(more = true)
        }
    }

    private fun engine(provider: MailProvider): SyncEngine {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(account)
        return SyncEngine(repo) { provider }
    }

    private fun SyncEngine.round() = runBlocking { runCatching { syncOnce(account) } }

    @Test
    fun a_failing_first_round_keeps_saying_syncing() {
        val engine = engine(Flaky(failures = 2))
        engine.round()
        assertEquals(AccountStatus.Syncing, engine.statuses.value["acc"])
        engine.round()
        assertEquals(AccountStatus.Syncing, engine.statuses.value["acc"])
        engine.round()
        assertEquals(AccountStatus.Syncing, engine.statuses.value["acc"])
        assertEquals(null, engine.errors.value["acc"])
    }

    @Test
    fun only_repeated_failures_mean_offline() {
        val engine = engine(Flaky(failures = 10))
        repeat(4) { engine.round() }
        assertEquals(AccountStatus.Syncing, engine.statuses.value["acc"])
        engine.round()
        assertEquals(AccountStatus.Offline, engine.statuses.value["acc"])
    }

    @Test
    fun google_rate_limit_sent_as_403_is_transient() {
        val e = ProviderException.of(403, """{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""")
        assertEquals(ProviderException.Transient::class, e::class)
        assertEquals(ProviderException.Client::class, ProviderException.of(403, "forbidden")::class)
    }
}
