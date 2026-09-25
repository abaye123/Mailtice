package co.abaye.mailtice

import co.abaye.mailtice.dev.DemoAccounts
import co.abaye.mailtice.dev.DemoMailProvider
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.provider.OlderQuery
import co.abaye.mailtice.search.MailSearch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Paging older mail the way the list does: each page strictly older than the last, then nothing. */
class OlderMailTest {
    private val now = 1_800_000_000_000L
    private val provider = DemoMailProvider(clock = { now })
    private val account = DemoAccounts.initial().first { it.id == DemoAccounts.WORK_ID }
    private val inbox = listOf(Folder(account.id, "INBOX", "Inbox", FolderRole.Inbox, sync = true, notify = true))

    @Test
    fun pagesAreOlderEachTimeAndEnd() = runTest {
        val seen = mutableSetOf<String>()
        var before: Long? = now - 30L * 24 * 60 * 60_000
        var pages = 0
        while (true) {
            val page = provider.olderMessages(account, inbox, OlderQuery(before = before, limit = 50))
            if (page.isEmpty()) break
            assertTrue(page.all { it.receivedAt < before!! }, "every row older than the cursor")
            assertTrue(page.none { it.id in seen }, "no row twice")
            seen += page.map { it.id }
            before = page.minOf { it.receivedAt }
            pages++
            assertTrue(pages < 50, "the archive must end")
        }
        assertTrue(pages > 1)
    }

    @Test
    fun textSearchFilters() = runTest {
        val hits = provider.olderMessages(account, inbox, OlderQuery(before = null, search = MailSearch(words = listOf("roadmap")), limit = 20))
        assertTrue(hits.isNotEmpty())
        assertEquals(hits.size, hits.count { it.subject.contains("roadmap", ignoreCase = true) || it.fromName.contains("roadmap", true) })
    }
}
