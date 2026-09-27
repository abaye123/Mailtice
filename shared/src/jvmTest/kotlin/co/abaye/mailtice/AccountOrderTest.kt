package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.ProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountOrderTest {
    @Test
    fun theChosenOrderIsTheListedOrder() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        listOf("a", "b", "c").forEach { repo.addAccount(Account(it, ProviderKind.Imap, "$it@x.com")) }
        assertEquals(listOf("a", "b", "c"), repo.accounts.first().map { it.id })
        repo.reorderAccounts(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), repo.accounts.first().map { it.id })
    }
}
