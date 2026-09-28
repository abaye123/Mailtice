package co.abaye.mailtice.sync

import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val HOURS_IN_WEEK = 7 * 24
private const val MINUTE = 60_000L

/** Fewer messages than this in the window and the profile is not trusted yet. */
private const val MIN_SAMPLES = 40

/** An old week counts half as much as this one after this many days. */
private const val HALF_LIFE_DAYS = 21.0

/**
 * When an account usually gets mail (and its owner writes), as weights over the 168 hours of the
 * week, learned from what is stored: every message's time, the owner's sent mail counting half.
 * Recent weeks weigh more, and each hour borrows a little from its neighbours and from the same
 * hour on other days, so a quiet Tuesday 10:00 in a busy 10:00 pattern is not taken as dead.
 */
class ActivityProfile private constructor(private val weights: DoubleArray, val samples: Int) {

    /** Where [hourOfWeek] ranks among the week's hours, 0 = the quietest, 1 = the busiest. */
    fun rank(hourOfWeek: Int): Double {
        val w = weights[hourOfWeek]
        return weights.count { it < w } / (HOURS_IN_WEEK - 1).toDouble()
    }

    /** Whether [hourOfWeek] has seen next to nothing: under a twentieth of an average hour. */
    fun isDead(hourOfWeek: Int): Boolean = weights[hourOfWeek] < weights.average() / 20

    val trusted: Boolean get() = samples >= MIN_SAMPLES

    companion object {
        @OptIn(ExperimentalTime::class)
        fun from(events: List<ActivityEvent>, now: Long, zone: TimeZone): ActivityProfile {
            val raw = DoubleArray(HOURS_IN_WEEK)
            val decay = ln(2.0) / (HALF_LIFE_DAYS * 24 * 60 * MINUTE)
            for (e in events) {
                if (e.at > now) continue
                raw[hourOfWeek(e.at, zone)] += (if (e.sent) 0.5 else 1.0) * exp(-decay * (now - e.at))
            }
            val smooth = DoubleArray(HOURS_IN_WEEK) { h ->
                val sameHourOtherDays = (1..6).sumOf { d -> raw[(h + d * 24) % HOURS_IN_WEEK] } / 6
                raw[h] * 0.6 + (raw[(h + 1) % HOURS_IN_WEEK] + raw[(h + HOURS_IN_WEEK - 1) % HOURS_IN_WEEK]) * 0.15 +
                    sameHourOtherDays * 0.1
            }
            return ActivityProfile(smooth, events.size)
        }
    }
}

/** A stored message's time, and whether the owner sent it. */
data class ActivityEvent(val at: Long, val sent: Boolean)

/** Monday 00:00 is 0, Sunday 23:00 is 167, in [zone]. */
@OptIn(ExperimentalTime::class)
fun hourOfWeek(at: Long, zone: TimeZone): Int {
    val t = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone)
    return (t.dayOfWeek.isoDayNumber - 1) * 24 + t.hour
}

/** Why the next check comes when it does; shown in the settings. */
enum class PollReason { AwaitingReply, Conversation, Busy, Usual, Quiet, Night, Learning }

data class PollPlan(val delayMs: Long, val reason: PollReason)

/**
 * How long to wait before the next check of one account.
 *
 * - Right after the owner sent mail (from here, or seen arriving in Sent from another device) a
 *   reply may be on its way, whatever the hour: every 30 s for 10 minutes, every minute up to an
 *   hour.
 * - Mail that just came in usually means a live conversation: every minute for 10 minutes.
 * - Otherwise the account's own week decides: its busiest quarter of hours every minute, the next
 *   quarter every 2 minutes, then 5, and hours that almost never see mail (the night, typically)
 *   every 15 minutes.
 * - Until there is enough history to go by, every 2 minutes.
 */
object PollPlanner {
    fun plan(profile: ActivityProfile?, now: Long, zone: TimeZone, lastSentAt: Long?, lastIncomingAt: Long?): PollPlan {
        val sinceSent = lastSentAt?.let { now - it }
        val sinceIncoming = lastIncomingAt?.let { now - it }
        if (sinceSent != null && sinceSent in 0 until 10 * MINUTE) return PollPlan(30_000, PollReason.AwaitingReply)
        val usual = usualPlan(profile, now, zone)
        if (sinceSent != null && sinceSent in 0 until 60 * MINUTE) return PollPlan(minOf(usual.delayMs, MINUTE), PollReason.AwaitingReply)
        if (sinceIncoming != null &&
            sinceIncoming in 0 until 10 * MINUTE
        ) {
            return PollPlan(minOf(usual.delayMs, MINUTE), PollReason.Conversation)
        }
        return usual
    }

    private fun usualPlan(profile: ActivityProfile?, now: Long, zone: TimeZone): PollPlan {
        if (profile == null || !profile.trusted) return PollPlan(2 * MINUTE, PollReason.Learning)
        val hour = hourOfWeek(now, zone)
        if (profile.isDead(hour)) return PollPlan(15 * MINUTE, PollReason.Night)
        val rank = profile.rank(hour)
        return when {
            rank >= 0.75 -> PollPlan(MINUTE, PollReason.Busy)
            rank >= 0.5 -> PollPlan(2 * MINUTE, PollReason.Usual)
            else -> PollPlan(5 * MINUTE, PollReason.Quiet)
        }
    }
}
