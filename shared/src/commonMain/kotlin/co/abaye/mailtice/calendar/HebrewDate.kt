package co.abaye.mailtice.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import co.abaye.mailtice.domain.SunsetCity
import co.abaye.mailtice.main.formatTime
import co.abaye.mailtice.platform.Platform
import io.github.kdroidfilter.kosherkotlin.AstronomicalCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** How the Hebrew date is shown next to the civil one; `null` in [LocalHebrewDate] hides it. */
@Immutable
data class HebrewDateStyle(
    /** The Hebrew day starts at sunset in [city]; otherwise it turns at midnight with the civil date. */
    val atSunset: Boolean,
    val city: SunsetCity,
    /** Hebrew letters ("כ״ד אלול"); otherwise transliterated ("24 Elul"). */
    val hebrewLetters: Boolean,
)

/** Provided at the root from the user's settings. */
val LocalHebrewDate = staticCompositionLocalOf<HebrewDateStyle?> { null }

/** The Hebrew date of a civil day, with or without the year. */
private fun hebrewDateText(date: LocalDate, hebrewLetters: Boolean, withYear: Boolean): String {
    val jewish = JewishCalendar(date)
    val formatter = HebrewDateFormatter().apply { isHebrewFormat = hebrewLetters }
    return if (hebrewLetters) {
        val dayMonth = formatter.formatHebrewNumber(jewish.jewishDayOfMonth) + " " + formatter.formatMonth(jewish)
        // Without the thousands, as it is written ("תשפ״ו", not "ה׳תשפ״ו"): KosherKotlin 2.7.0 ignores
        // isUseLongHebrewYears and always prints them.
        if (withYear) dayMonth + " " + formatter.formatHebrewNumber(jewish.jewishYear % 1000) else dayMonth
    } else {
        val dayMonth = "${jewish.jewishDayOfMonth} ${formatter.formatMonth(jewish)}"
        if (withYear) "$dayMonth ${jewish.jewishYear}" else dayMonth
    }
}

/** Sunset of a civil day in [city], epoch millis; `null` where the sun does not set (polar days). */
@OptIn(ExperimentalTime::class)
private fun sunsetMillis(date: LocalDate, city: SunsetCity): Long? {
    val zone = TimeZone.of(city.zoneId)
    val calendar = AstronomicalCalendar(GeoLocation(city.name, city.latitude, city.longitude, zone))
    calendar.localDateTime = LocalDateTime(date, LocalTime(12, 0))
    return calendar.sunset?.toEpochMilliseconds()
}

// Rows of a list share few days, so both lookups are cached. Touched from composition only.
private val sunsets = HashMap<Pair<LocalDate, SunsetCity>, Long?>()
private val texts = HashMap<Triple<LocalDate, Boolean, Boolean>, String>()

/** The Hebrew date of the moment [epochMillis], turning at sunset or midnight as [style] says. */
@OptIn(ExperimentalTime::class)
fun hebrewDate(epochMillis: Long, style: HebrewDateStyle, withYear: Boolean): String {
    val civil = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = if (style.atSunset) {
        val sunset = sunsets.getOrPut(civil to style.city) { sunsetMillis(civil, style.city) }
        if (sunset != null && epochMillis >= sunset) civil.plus(1, DateTimeUnit.DAY) else civil
    } else {
        civil
    }
    return texts.getOrPut(Triple(day, style.hebrewLetters, withYear)) {
        hebrewDateText(day, style.hebrewLetters, withYear)
    }
}

/**
 * [formatTime] with the Hebrew date beside it when the user wants one: "23/09 · כ״ד אלול" in the
 * list, the full date with the year in the reader. A time alone (a message from today) stays alone.
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun dateLabel(epochMillis: Long, withDate: Boolean = false): String {
    val civil = formatTime(epochMillis, withDate)
    val style = LocalHebrewDate.current ?: return civil
    if (!withDate) {
        val zone = TimeZone.currentSystemDefault()
        val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
        val today = Instant.fromEpochMilliseconds(Platform.now()).toLocalDateTime(zone).date
        if (then == today) return civil
    }
    return "$civil · ${hebrewDate(epochMillis, style, withYear = withDate)}"
}
