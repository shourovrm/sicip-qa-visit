// to12h/to24h round trips, digit formatting, and invalid-time rejection.
package bd.sicip.qavisit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFieldTest {

    @Test fun round_trips_midnight() {
        val (display, isPM) = to12h("00:00")
        assertEquals("12:00" to false, display to isPM)
        assertEquals("00:00:00", to24h(display, isPM))
    }

    @Test fun round_trips_noon() {
        val (display, isPM) = to12h("12:00")
        assertEquals("12:00" to true, display to isPM)
        assertEquals("12:00:00", to24h(display, isPM))
    }

    @Test fun round_trips_end_of_day() {
        val (display, isPM) = to12h("23:59")
        assertEquals("11:59" to true, display to isPM)
        assertEquals("23:59:00", to24h(display, isPM))
    }

    @Test fun round_trips_morning() {
        val (display, isPM) = to12h("09:05")
        assertEquals("9:05" to false, display to isPM)
        assertEquals("09:05:00", to24h(display, isPM))
    }

    @Test fun round_trips_with_seconds_in_source() {
        val (display, isPM) = to12h("09:05:00")
        assertEquals("9:05" to false, display to isPM)
    }

    @Test fun formats_digits_with_colon_before_minutes() {
        assertEquals("9:35", formatDigits("935"))
        assertEquals("11:30", formatDigits("1130"))
    }

    @Test fun formats_digits_still_typing_hour_has_no_colon() {
        assertEquals("9", formatDigits("9"))
        assertEquals("11", formatDigits("11"))
    }

    @Test fun formats_digits_caps_at_four() {
        assertEquals("11:30", formatDigits("113000"))
    }

    @Test fun formats_digits_strips_non_digits() {
        assertEquals("9:35", formatDigits("9:35"))
    }

    @Test fun rejects_hour_out_of_range() {
        assertNull(parseTime("13:00"))
        assertNull(parseTime("0:30"))
    }

    @Test fun rejects_minute_out_of_range() {
        assertNull(parseTime("1:61"))
    }

    @Test fun accepts_valid_time() {
        assertEquals(9 to 5, parseTime("9:05"))
        assertEquals(12 to 0, parseTime("12:00"))
    }
}
