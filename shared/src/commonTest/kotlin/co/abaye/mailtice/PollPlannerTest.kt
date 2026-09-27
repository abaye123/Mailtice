package co.abaye.mailtice

import co.abaye.mailtice.sync.ActivityEvent
import co.abaye.mailtice.sync.ActivityProfile
import co.abaye.mailtice.sync.PollPlanner
import co.abaye.mailtice.sync.PollReason
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PollPlannerTest {
    private val zone = TimeZone.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(2026, 9, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    /** Four weeks of an office account: mail on weekdays 9-17, heaviest at 10 and 14, nothing at night. */
    private val office: ActivityProfile by lazy {
        val events = mutableListOf<ActivityEvent>()
        // 1 Sep 2026 is a Tuesday; 28 days back from the 29th.
        for (day in 1..28) {
            val weekday = (day + 1) % 7 !in listOf(0, 6) // Tuesday = 2 -> Mon..Fri
            if (!weekday) continue
            for (hour in 9..17) {
                val count = if (hour == 10 || hour == 14) 6 else 2
                repeat(count) { events += ActivityEvent(at(day, hour, it * 7), sent = false) }
            }
        }
        ActivityProfile.from(events, at(29, 0), zone)
    }

    @Test
    fun busyQuietAndDeadHours() {
        // 29 Sep 2026 is a Tuesday.
        assertEquals(PollReason.Busy, PollPlanner.plan(office, at(29, 10, 30), zone, null, null).reason)
        assertEquals(PollReason.Night, PollPlanner.plan(office, at(29, 3), zone, null, null).reason)
        assertEquals(15 * 60_000L, PollPlanner.plan(office, at(29, 3), zone, null, null).delayMs)
    }

    @Test
    fun aSentMailSpeedsUpEvenAtNight() {
        val now = at(29, 3, 20)
        assertEquals(30_000L, PollPlanner.plan(office, now, zone, lastSentAt = now - 4 * 60_000, lastIncomingAt = null).delayMs)
        val later = PollPlanner.plan(office, now, zone, lastSentAt = now - 40 * 60_000, lastIncomingAt = null)
        assertEquals(PollReason.AwaitingReply, later.reason)
        assertEquals(60_000L, later.delayMs)
        // An hour and more later the night pace is back.
        assertEquals(PollReason.Night, PollPlanner.plan(office, now, zone, lastSentAt = now - 90 * 60_000, lastIncomingAt = null).reason)
    }

    @Test
    fun freshMailMeansAConversation() {
        val now = at(29, 3, 20)
        assertEquals(PollReason.Conversation, PollPlanner.plan(office, now, zone, null, lastIncomingAt = now - 2 * 60_000).reason)
    }

    @Test
    fun anAccountWithoutHistoryIsStillLearning() {
        val thin = ActivityProfile.from(List(5) { ActivityEvent(at(28, 10), false) }, at(29, 0), zone)
        val plan = PollPlanner.plan(thin, at(29, 10), zone, null, null)
        assertEquals(PollReason.Learning, plan.reason)
        assertEquals(120_000L, plan.delayMs)
    }
}
