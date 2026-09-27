package co.abaye.mailtice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.ScheduledCodec
import co.abaye.mailtice.db.MailDatabase
import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.domain.SenderIdentity
import co.abaye.mailtice.provider.MimeBuilder
import co.abaye.mailtice.provider.OutgoingMail
import co.abaye.mailtice.provider.gmail.SendAsList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SenderIdentityTest {
    @Test
    fun aliasGoesInFromAndReplyTo() {
        val mail = OutgoingMail(to = listOf("dana@x.com"), subject = "Hi", text = "Hi", from = "Noa Levi <noa@alias.com>", replyTo = "desk@alias.com")
        val raw = MimeBuilder.build("noa@gmail.com", mail, epochMillis = 0)
        assertTrue("From: \"Noa Levi\" <noa@alias.com>" in raw)
        assertTrue("Reply-To: desk@alias.com" in raw)
        // Without an alias the account's own address stays.
        assertTrue("From: noa@gmail.com" in MimeBuilder.build("noa@gmail.com", mail.copy(from = null, replyTo = null), 0))
    }

    @Test
    fun queuedMailKeepsItsSender() {
        val mail = OutgoingMail(to = listOf("a@x.com"), subject = "S", text = "T", from = "Noa <noa@alias.com>", replyTo = "r@alias.com")
        val back = ScheduledCodec.decode(ScheduledCodec.encode(mail))
        assertEquals(mail.from, back.from)
        assertEquals(mail.replyTo, back.replyTo)
    }

    @Test
    fun gmailSendAsParses() {
        val json = """{"sendAs":[
            {"sendAsEmail":"noa@gmail.com","displayName":"Noa Levi","isPrimary":true,"isDefault":true,"signature":"<b>Noa</b>"},
            {"sendAsEmail":"noa@alias.com","displayName":"Noa (work)","replyToAddress":"desk@alias.com","verificationStatus":"accepted"},
            {"sendAsEmail":"pending@alias.com","verificationStatus":"pending"}]}"""
        val list = Json { ignoreUnknownKeys = true }.decodeFromString(SendAsList.serializer(), json).sendAs
        assertEquals(3, list.size)
        assertEquals("desk@alias.com", list[1].replyToAddress)
        assertEquals("pending", list[2].verificationStatus)
    }

    @Test
    fun identitiesAreStoredPerAccount() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        val repo = MailRepository(driver, Dispatchers.Unconfined)
        repo.addAccount(Account("a", ProviderKind.Gmail, "noa@gmail.com"))
        repo.replaceIdentities("a", listOf(SenderIdentity("a", "noa@gmail.com", "Noa", isDefault = true), SenderIdentity("a", "noa@alias.com", "Noa (work)")))
        assertEquals(listOf("noa@gmail.com", "noa@alias.com"), repo.identities.first()["a"]?.map { it.email })
        repo.replaceIdentities("a", listOf(SenderIdentity("a", "noa@gmail.com")))
        assertEquals(1, repo.identities.first()["a"]?.size)
    }
}
