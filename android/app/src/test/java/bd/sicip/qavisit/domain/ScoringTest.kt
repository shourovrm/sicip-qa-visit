// scoring rules: category boundaries, points table, rank aggregation
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    // ---- autoCategory(days, nights, ...): Dhaka special-cases ----
    @Test fun dhaka_metro_is_E() {
        assertEquals("E", autoCategory(1, 0, "Dhaka", true))
    }

    @Test fun dhaka_non_metro_is_D() {
        assertEquals("D", autoCategory(1, 0, "Dhaka", false))
    }

    @Test fun dhaka_non_metro_multiday_still_D() {
        // Dhaka sub-option wins over the ladder
        assertEquals("D", autoCategory(5, 4, "Dhaka", false))
    }

    @Test fun dhaka_metro_unset_is_D() {
        // metro flag unset (null) for a Dhaka visit is treated as non-metro
        assertEquals("D", autoCategory(1, 0, "Dhaka", null))
    }

    // ---- autoCategory: classic ladder (nights == days-1) ----
    @Test fun one_day_outside_dhaka_is_D() {
        assertEquals("D", autoCategory(1, 0, "Sylhet", null))
    }

    @Test fun two_days_one_night_is_C() {
        assertEquals("C", autoCategory(2, 1, "Sylhet", null))
    }

    @Test fun three_days_two_nights_is_B() {
        assertEquals("B", autoCategory(3, 2, "Sylhet", null))
    }

    @Test fun four_days_three_nights_is_A() {
        assertEquals("A", autoCategory(4, 3, "Sylhet", null))
    }

    @Test fun five_days_four_nights_is_Aplus() {
        assertEquals("A+", autoCategory(5, 4, "Sylhet", null))
    }

    @Test fun six_days_five_nights_is_Aplusplus() {
        assertEquals("A++", autoCategory(6, 5, "Sylhet", null))
    }

    @Test fun seven_days_six_nights_is_Astarstar() {
        assertEquals("A**", autoCategory(7, 6, "Sylhet", null))
    }

    @Test fun more_than_seven_days_still_Astarstar() {
        assertEquals("A**", autoCategory(14, 13, "Sylhet", null))
    }

    // ---- autoCategory: plus ladder (nights >= days) ----
    @Test fun one_day_one_night_is_Dplus() {
        assertEquals("D+", autoCategory(1, 1, "Sylhet", null))
    }

    @Test fun two_days_two_nights_is_Cplus() {
        assertEquals("C+", autoCategory(2, 2, "Sylhet", null))
    }

    @Test fun three_days_three_nights_is_Bplus() {
        assertEquals("B+", autoCategory(3, 3, "Sylhet", null))
    }

    @Test fun four_days_four_nights_is_Astar() {
        assertEquals("A*", autoCategory(4, 4, "Sylhet", null))
    }

    @Test fun five_days_five_nights_is_Aplusstar() {
        assertEquals("A+*", autoCategory(5, 5, "Sylhet", null))
    }

    @Test fun six_days_six_nights_is_Aplusplusstar() {
        assertEquals("A++*", autoCategory(6, 6, "Sylhet", null))
    }

    @Test fun seven_plus_days_plus_nights_caps_at_Astarstar() {
        assertEquals("A**", autoCategory(8, 8, "Sylhet", null))
    }

    // ---- autoCategory: gaps (nights < days-1) fall back to the classic ladder, conservative ----
    @Test fun nights_short_of_classic_still_uses_classic_ladder() {
        assertEquals("B", autoCategory(3, 1, "Sylhet", null))
    }

    // ---- autoCategory(startDate, endDate, ...): date-only overload for scheduling preview ----
    @Test fun date_overload_assumes_classic_shape() {
        assertEquals("A+", autoCategory("2026-06-01", "2026-06-05", "Sylhet", null))
    }

    @Test fun date_overload_span_crossing_month_boundary_is_Aplus() {
        // 28 Jun - 2 Jul = 5 days, must count correctly across the month boundary
        assertEquals("A+", autoCategory("2026-06-28", "2026-07-02", "Sylhet", null))
    }

    @Test fun date_overload_dhaka_metro_is_E() {
        assertEquals("E", autoCategory("2026-06-01", "2026-06-01", "Dhaka", true))
    }

    // ---- daysAndNights: FinishTrip's started_at -> chosen end datetime ----
    @Test fun same_day_is_1_0() {
        assertEquals(1 to 0, daysAndNights("2026-06-01T09:00:00Z", "2026-06-01T17:00:00Z"))
    }

    @Test fun evening_return_next_day_is_2_1() {
        // leave 9pm day 1, home 5pm day 2 -- worked both days, classic 2D1N
        assertEquals(2 to 1, daysAndNights("2026-06-01T21:00:00Z", "2026-06-02T17:00:00Z"))
    }

    @Test fun early_morning_return_before_cutoff_is_1_1() {
        // leave 9pm day 1, home 7am day 2 -- never worked day 2, so it's 1D1N (D+), not 2D1N
        assertEquals(1 to 1, daysAndNights("2026-06-01T21:00:00Z", "2026-06-02T07:00:00Z"))
        assertEquals("D+", autoCategory(1, 1, "Sylhet", null))
    }

    @Test fun three_day_tour_returning_before_cutoff_on_day_4_is_3_3() {
        // worked days 1-3, home 7:30am day 4 -- day 4 doesn't count -> 3D3N (B+)
        val (days, nights) = daysAndNights("2026-06-01T09:00:00Z", "2026-06-04T07:30:00Z")
        assertEquals(3 to 3, days to nights)
        assertEquals("B+", autoCategory(days, nights, "Sylhet", null))
    }

    @Test fun classic_four_day_three_night_trip() {
        val (days, nights) = daysAndNights("2026-06-01T09:00:00Z", "2026-06-04T17:00:00Z")
        assertEquals(4 to 3, days to nights)
        assertEquals("A", autoCategory(days, nights, "Sylhet", null))
    }

    @Test fun eight_day_seven_night_trip_caps_at_Astarstar() {
        val (days, nights) = daysAndNights("2026-06-01T09:00:00Z", "2026-06-08T17:00:00Z")
        assertEquals(8 to 7, days to nights)
        assertEquals("A**", autoCategory(days, nights, "Sylhet", null))
    }

    // ---- points table ----
    @Test fun points_table_matches_fixed_scale() {
        assertEquals(100, points("A**"))
        assertEquals(96, points("A++*"))
        assertEquals(84, points("A++"))
        assertEquals(80, points("A+*"))
        assertEquals(68, points("A+"))
        assertEquals(64, points("A*"))
        assertEquals(52, points("A"))
        assertEquals(48, points("B+"))
        assertEquals(36, points("B"))
        assertEquals(32, points("C+"))
        assertEquals(20, points("C"))
        assertEquals(16, points("D+"))
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
            MonthVisit("2026-07-01", "A"),   // 52, in July
            MonthVisit("2026-07-15", "B"),   // 36, in July
            MonthVisit("2026-06-30", "A**"), // in June, excluded
        )
        assertEquals(2 to 88, monthSummary(visits, "2026-07"))
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
