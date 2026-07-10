// trip helpers: day-in-trip counter, primary-visit pick, fare formatting
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TripMathTest {

    // ---- dayNumber ----
    @Test fun dayNumber_same_day_is_one() {
        val startedAt = "2026-07-10T03:00:00Z"
        val now = Instant.parse("2026-07-10T14:00:00Z")
        assertEquals(1, dayNumber(startedAt, now))
    }

    @Test fun dayNumber_next_day_is_two() {
        val startedAt = "2026-07-10T22:00:00Z"
        val now = Instant.parse("2026-07-11T01:00:00Z")
        assertEquals(2, dayNumber(startedAt, now))
    }

    @Test fun dayNumber_five_days_later_is_six() {
        val startedAt = "2026-07-01T09:00:00Z"
        val now = Instant.parse("2026-07-06T09:00:00Z")
        assertEquals(6, dayNumber(startedAt, now))
    }

    // ---- primaryVisit ----
    private data class Row(val id: String, val additional: Boolean)

    @Test fun primaryVisit_picks_first_non_additional() {
        val rows = listOf(Row("a", true), Row("b", false), Row("c", false))
        assertEquals(Row("b", false), primaryVisit(rows) { it.additional })
    }

    @Test fun primaryVisit_falls_back_to_first_when_all_additional() {
        val rows = listOf(Row("a", true), Row("b", true))
        assertEquals(Row("a", true), primaryVisit(rows) { it.additional })
    }

    @Test fun primaryVisit_empty_list_is_null() {
        assertEquals(null, primaryVisit(emptyList<Row>()) { it.additional })
    }

    // ---- formatFare ----
    @Test fun formatFare_whole_number_drops_decimals() {
        assertEquals("৳1,234", formatFare(1234.0))
    }

    @Test fun formatFare_keeps_paisa() {
        assertEquals("৳1,234.50", formatFare(1234.5))
    }

    @Test fun formatFare_zero() {
        assertEquals("৳0", formatFare(0.0))
    }

    @Test fun formatFare_large_thousands_grouping() {
        assertEquals("৳12,345,678", formatFare(12345678.0))
    }
}
