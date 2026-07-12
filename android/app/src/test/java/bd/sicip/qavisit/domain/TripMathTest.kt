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

    // ---- tourStartKey / tourEndKey ----
    @Test fun tourStartKey_picks_earliest_leg_departure() {
        val legs = listOf("2026-07-06" to "09:00:00", "2026-07-05" to "14:00:00")
        assertEquals("2026-07-05T14:00:00Z", tourStartKey(legs, "2026-07-01T00:00:00Z"))
    }

    @Test fun tourStartKey_falls_back_to_startedAt_when_no_legs() {
        assertEquals("2026-07-01T00:00:00Z", tourStartKey(emptyList(), "2026-07-01T00:00:00Z"))
    }

    @Test fun tourEndKey_picks_latest_leg_arrival() {
        val legs = listOf("2026-07-05" to "09:00:00", "2026-07-06" to "18:00:00")
        assertEquals("2026-07-06T18:00:00Z", tourEndKey(legs, "2026-07-01T00:00:00Z"))
    }

    @Test fun tourEndKey_falls_back_to_finishedAt_when_no_legs() {
        assertEquals("2026-07-01T00:00:00Z", tourEndKey(emptyList(), "2026-07-01T00:00:00Z"))
    }

    @Test fun tourEndKey_falls_back_to_null_finishedAt() {
        assertEquals(null, tourEndKey(emptyList(), null))
    }

    // ---- instituteTitle ----
    private data class VisitRow(val institute: String, val additional: Boolean)

    @Test fun instituteTitle_lists_all_primary_first() {
        val visits = listOf(
            VisitRow("Kamarpara TTC", true),
            VisitRow("SDC Overseas Training Center", false),
            VisitRow("Lalmatia Mohila College", true),
        )
        assertEquals(
            "SDC Overseas Training Center, Kamarpara TTC, Lalmatia Mohila College",
            instituteTitle(visits, { it.additional }, { it.institute }),
        )
    }

    @Test fun instituteTitle_single_visit() {
        val visits = listOf(VisitRow("XYZ Institute", false))
        assertEquals("XYZ Institute", instituteTitle(visits, { it.additional }, { it.institute }))
    }

    @Test fun instituteTitle_empty_is_blank() {
        assertEquals("", instituteTitle(emptyList<VisitRow>(), { it.additional }, { it.institute }))
    }

    // ---- tourSubtitle ----
    @Test fun tourSubtitle_plural_counts() {
        assertEquals(
            "2026-07-05 – 2026-07-05 · 3 visits · 3 travels · Σ ৳1,422",
            tourSubtitle("2026-07-05", "2026-07-05", 3, 3, 1422.0),
        )
    }

    @Test fun tourSubtitle_singular_counts() {
        assertEquals(
            "2026-07-05 – 2026-07-06 · 1 visit · 1 travel · Σ ৳500",
            tourSubtitle("2026-07-05", "2026-07-06", 1, 1, 500.0),
        )
    }

    // ---- purposeBand ----
    @Test fun purposeBand_joins_distinct_clauses() {
        assertEquals(
            "Purpose: QA visit - Assoc A (Ref: 1, 5 Jul 2026); Audit - Assoc B (Ref: 2, 6 Jul 2026)",
            purposeBand(
                listOf(
                    "QA visit - Assoc A (Ref: 1, 5 Jul 2026)",
                    "Audit - Assoc B (Ref: 2, 6 Jul 2026)",
                ),
            ),
        )
    }

    @Test fun purposeBand_collapses_identical_duplicates() {
        val clause = "QA visit - Assoc A (Ref: 1, 5 Jul 2026)"
        assertEquals("Purpose: $clause", purposeBand(listOf(clause, clause)))
    }
}
