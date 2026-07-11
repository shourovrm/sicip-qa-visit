// to12h/to24h round trips, digit formatting, and hour/minute validation.
package bd.sicip.qavisit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFieldTest {

    @Test fun round_trips_midnight() {
        val (h, m, isPM) = to12h("00:00")
        assertEquals(Triple(12, 0, false), Triple(h, m, isPM))
        assertEquals("00:00:00", to24h(h, m, isPM))
    }

    @Test fun round_trips_noon() {
        val (h, m, isPM) = to12h("12:00")
        assertEquals(Triple(12, 0, true), Triple(h, m, isPM))
        assertEquals("12:00:00", to24h(h, m, isPM))
    }

    @Test fun round_trips_end_of_day() {
        val (h, m, isPM) = to12h("23:59")
        assertEquals(Triple(11, 59, true), Triple(h, m, isPM))
        assertEquals("23:59:00", to24h(h, m, isPM))
    }

    @Test fun round_trips_morning() {
        val (h, m, isPM) = to12h("09:05")
        assertEquals(Triple(9, 5, false), Triple(h, m, isPM))
        assertEquals("09:05:00", to24h(h, m, isPM))
    }

    @Test fun round_trips_with_seconds_in_source() {
        val (h, m, isPM) = to12h("09:05:00")
        assertEquals(Triple(9, 5, false), Triple(h, m, isPM))
    }

    @Test fun formats_digits_strips_non_digits_and_caps_at_two() {
        assertEquals("93", formatDigits("935"))
        assertEquals("11", formatDigits("1130"))
        assertEquals("9", formatDigits("9"))
        assertEquals("93", formatDigits("9:3"))
    }

    @Test fun rejects_hour_out_of_range() {
        assertNull(parseHour("13"))
        assertNull(parseHour("0"))
        assertNull(parseHour(""))
    }

    @Test fun accepts_valid_hour() {
        assertEquals(9, parseHour("9"))
        assertEquals(12, parseHour("12"))
    }

    @Test fun rejects_minute_out_of_range() {
        assertNull(parseMinute("61"))
        assertNull(parseMinute(""))
    }

    @Test fun accepts_valid_minute() {
        assertEquals(5, parseMinute("05"))
        assertEquals(0, parseMinute("0"))
        assertEquals(59, parseMinute("59"))
    }
}
