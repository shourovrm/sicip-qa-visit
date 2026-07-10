// scoring rules: category boundaries, points table, rank aggregation
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    // ---- autoCategory: Dhaka special-cases ----
    @Test fun dhaka_metro_is_E() {
        assertEquals("E", autoCategory("2026-06-01", "2026-06-01", "Dhaka", true))
    }

    @Test fun dhaka_non_metro_is_D() {
        assertEquals("D", autoCategory("2026-06-01", "2026-06-01", "Dhaka", false))
    }

    @Test fun dhaka_non_metro_multiday_still_D() {
        // Dhaka sub-option wins over span table
        assertEquals("D", autoCategory("2026-06-01", "2026-06-05", "Dhaka", false))
    }

    @Test fun dhaka_metro_unset_is_D() {
        // metro flag unset (null) for a Dhaka visit is treated as non-metro
        assertEquals("D", autoCategory("2026-06-01", "2026-06-01", "Dhaka", null))
    }

    // ---- autoCategory: 1-day outside Dhaka ----
    @Test fun one_day_outside_dhaka_is_D() {
        assertEquals("D", autoCategory("2026-06-01", "2026-06-01", "Sylhet", null))
    }

    // ---- autoCategory: span table boundaries (2..7+ days) ----
    @Test fun two_days_is_C() {
        assertEquals("C", autoCategory("2026-06-01", "2026-06-02", "Sylhet", null))
    }

    @Test fun three_days_is_B() {
        assertEquals("B", autoCategory("2026-06-01", "2026-06-03", "Sylhet", null))
    }

    @Test fun four_days_is_A() {
        assertEquals("A", autoCategory("2026-06-01", "2026-06-04", "Sylhet", null))
    }

    @Test fun five_days_is_Aplus() {
        assertEquals("A+", autoCategory("2026-06-01", "2026-06-05", "Sylhet", null))
    }

    @Test fun six_days_is_Aplusplus() {
        assertEquals("A++", autoCategory("2026-06-01", "2026-06-06", "Sylhet", null))
    }

    @Test fun seven_days_is_Astarstar() {
        assertEquals("A**", autoCategory("2026-06-01", "2026-06-07", "Sylhet", null))
    }

    @Test fun more_than_seven_days_still_Astarstar() {
        assertEquals("A**", autoCategory("2026-06-01", "2026-06-14", "Sylhet", null))
    }

    @Test fun span_crossing_month_boundary_is_Aplus() {
        // 28 Jun - 2 Jul = 5 days, must count correctly across the month boundary
        assertEquals("A+", autoCategory("2026-06-28", "2026-07-02", "Sylhet", null))
    }

    // ---- points table ----
    @Test fun points_table_matches_fixed_scale() {
        assertEquals(100, points("A**"))
        assertEquals(85, points("A++"))
        assertEquals(69, points("A+"))
        assertEquals(53, points("A"))
        assertEquals(36, points("B"))
        assertEquals(20, points("C"))
        assertEquals(4, points("D"))
        assertEquals(1, points("E"))
        assertEquals(0, points("N/A"))
    }

    @Test fun points_unknown_category_is_zero() {
        assertEquals(0, points("bogus"))
    }

    @Test fun category_labels_cover_exactly_the_points_keys() {
        assertEquals(POINTS.keys, CATEGORY_LABELS.keys)
    }

    // ---- totalPoints: skip deleted ----
    @Test fun totalPoints_sums_skipping_deleted() {
        val visits = listOf(
            VisitScore("o1", "A**", deleted = false), // 100
            VisitScore("o1", "N/A", deleted = false),  // 0
            VisitScore("o1", "A++", deleted = true),   // skipped
        )
        assertEquals(100, totalPoints(visits))
    }

    // ---- rank: officers sorted desc ----
    @Test fun rank_sorts_officers_by_points_desc() {
        val visits = listOf(
            VisitScore("low", "D"),       // 4
            VisitScore("high", "A**"),    // 100
            VisitScore("high", "E"),      // +1 = 101
            VisitScore("mid", "B"),       // 36
            VisitScore("high", "A++", deleted = true), // skipped
        )
        val ranked = rank(visits)
        assertEquals(listOf("high" to 101, "mid" to 36, "low" to 4), ranked)
    }

    // ---- monthSummary: home dashboard "this month" line ----
    @Test fun monthSummary_counts_only_matching_month() {
        val visits = listOf(
            MonthVisit("2026-07-01", "A"),   // 53, in July
            MonthVisit("2026-07-15", "B"),   // 36, in July
            MonthVisit("2026-06-30", "A**"), // in June, excluded
        )
        assertEquals(2 to 89, monthSummary(visits, "2026-07"))
    }

    @Test fun monthSummary_skips_deleted() {
        val visits = listOf(
            MonthVisit("2026-07-01", "A**", deleted = true),
            MonthVisit("2026-07-02", "D"), // 4
        )
        assertEquals(1 to 4, monthSummary(visits, "2026-07"))
    }

    @Test fun monthSummary_empty_when_nothing_matches() {
        assertEquals(0 to 0, monthSummary(listOf(MonthVisit("2026-05-01", "A")), "2026-07"))
    }
}
