// scoring rules: points table, rank aggregation, done/deleted filters
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    // ---- points table ----
    @Test fun points_table_matches_fixed_scale() {
        assertEquals(116, points("A***"))
        assertEquals(112, points("A**+"))
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

    // ---- rank: officers sorted asc (fewest points = #1) ----
    @Test fun rank_sorts_officers_by_points_asc() {
        val visits = listOf(
            VisitScore("low", "D"),       // 4
            VisitScore("high", "A**"),    // 100
            VisitScore("high", "E"),      // +1 = 101
            VisitScore("mid", "B"),       // 36
            VisitScore("high", "A++", deleted = true), // skipped
        )
        val ranked = rank(visits)
        assertEquals(listOf("low" to 4, "mid" to 36, "high" to 101), ranked)
    }

    // ---- scheduled visits do not score ----
    @Test fun scheduled_visits_do_not_score() {
        val visits = listOf(
            VisitScore("o1", "A**", done = false), // scheduled, not counted
            VisitScore("o1", "D"),                  // done (default), 4
        )
        assertEquals(4, totalPoints(visits))
        assertEquals(listOf("o1" to 4), rank(visits))

        val monthVisits = listOf(
            MonthVisit("2026-07-01", "A**", done = false), // scheduled, skipped
            MonthVisit("2026-07-02", "D"),                   // done (default), 4
        )
        assertEquals(1 to 4, monthSummary(monthVisits, "2026-07"))
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

    // ---- CATEGORY_SPANS / suggestedNights / suggestedFood: full 17-row table, v1.5 policy --
    // category is the single source for bill allowances (BillMath no longer computes its own
    // span defaults). food = nights + 0.5*(days-nights); table below is CATEGORIES.md's table.
    private data class Row(val category: String, val days: Int, val nights: Int, val food: Double)

    private val spanTable = listOf(
        Row("A***", 8, 7, 7.5),
        Row("A**+", 7, 7, 7.0),
        Row("A**", 7, 6, 6.5),
        Row("A++*", 6, 6, 6.0),
        Row("A++", 6, 5, 5.5),
        Row("A+*", 5, 5, 5.0),
        Row("A+", 5, 4, 4.5),
        Row("A*", 4, 4, 4.0),
        Row("A", 4, 3, 3.5),
        Row("B+", 3, 3, 3.0),
        Row("B", 3, 2, 2.5),
        Row("C+", 2, 2, 2.0),
        Row("C", 2, 1, 1.5),
        Row("D+", 1, 1, 1.0),
        Row("D", 1, 0, 0.5),
        Row("E", 0, 0, 0.0),
        Row("N/A", 0, 0, 0.0),
    )

    @Test fun category_spans_cover_exactly_the_points_keys() {
        assertEquals(POINTS.keys, CATEGORY_SPANS.keys)
    }

    @Test fun category_spans_full_table() {
        spanTable.forEach { row ->
            assertEquals("${row.category} span", row.days to row.nights, CATEGORY_SPANS[row.category])
        }
    }

    @Test fun suggestedNights_full_table() {
        spanTable.forEach { row ->
            assertEquals("${row.category} nights", row.nights, suggestedNights(row.category))
        }
    }

    @Test fun suggestedFood_full_table() {
        spanTable.forEach { row ->
            assertEquals("${row.category} food", row.food, suggestedFood(row.category), 0.0001)
        }
    }

    @Test fun suggestedNights_and_suggestedFood_unknown_category_are_zero() {
        assertEquals(0, suggestedNights("bogus"))
        assertEquals(0.0, suggestedFood("bogus"), 0.0001)
    }
}
