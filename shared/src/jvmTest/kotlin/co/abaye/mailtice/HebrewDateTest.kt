package co.abaye.mailtice

import co.abaye.mailtice.calendar.HebrewDateStyle
import co.abaye.mailtice.calendar.hebrewDate
import co.abaye.mailtice.domain.SunsetCity
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HebrewDateTest {
    private val original = TimeZone.getDefault()

    @BeforeTest
    fun inJerusalem() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"))

    @AfterTest
    fun restore() = TimeZone.setDefault(original)

    private fun at(hour: Int, day: Int = 22) =
        ZonedDateTime.of(2025, 9, day, hour, 0, 0, 0, ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()

    private val sunset = HebrewDateStyle(atSunset = true, city = SunsetCity.Jerusalem, hebrewLetters = true)

    @Test
    fun rosh_hashana_5786_begins_at_sunset() {
        // Rosh Hashana 5786 fell on 23 September 2025; the evening before already belongs to it.
        assertEquals("כ״ט אלול תשפ״ה", hebrewDate(at(12), sunset, withYear = true))
        assertEquals("א׳ תשרי תשפ״ו", hebrewDate(at(21), sunset, withYear = true))
    }

    @Test
    fun midnight_mode_follows_the_civil_date() {
        assertEquals("כ״ט אלול", hebrewDate(at(21), sunset.copy(atSunset = false), withYear = false))
        assertEquals("1 Tishrei", hebrewDate(at(9, day = 23), sunset.copy(hebrewLetters = false), withYear = false))
    }
}
