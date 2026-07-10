// leaderboard: points sum, competition-ranking ties, deleted visits excluded.
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RankTest {

    private val officers = listOf(
        RankOfficer("a", "Alice"),
        RankOfficer("b", "Bob"),
        RankOfficer("c", "Carol"),
        RankOfficer("d", "Dave"),
    )

    @Test fun sorts_desc_by_points() {
        val visits = listOf(
            VisitScore("a", "A**"), // 100
            VisitScore("b", "B"),   // 36
            VisitScore("c", "D"),   // 4
        )
        val rows = rank(officers.take(3), visits)
        assertEquals(listOf("a", "b", "c"), rows.map { it.officerId })
        assertEquals(listOf(1, 2, 3), rows.map { it.position })
    }

    // two-way tie for 1st (100 each) -> next distinct score is 3rd, not dense-ranked 2nd.
    @Test fun tie_uses_competition_ranking_not_dense() {
        val visits = listOf(
            VisitScore("a", "A**"), // 100
            VisitScore("c", "A**"), // 100
            VisitScore("b", "B"),   // 36
        )
        val rows = rank(officers.take(3), visits)
        val byId = rows.associateBy { it.officerId }
        assertEquals(1, byId.getValue("a").position)
        assertEquals(1, byId.getValue("c").position)
        assertEquals(3, byId.getValue("b").position) // skips 2, standard 1,2,2,4-style jump
    }

    @Test fun tie_breaks_by_name_asc() {
        val visits = listOf(VisitScore("c", "A"), VisitScore("a", "A")) // both 52
        val rows = rank(officers.take(3), visits)
        assertEquals(listOf("a", "c"), rows.filter { it.points == 52 }.map { it.officerId })
    }

    @Test fun deleted_visits_excluded() {
        val visits = listOf(
            VisitScore("a", "A**", deleted = true), // skipped
            VisitScore("a", "D"),                    // 4
        )
        val rows = rank(officers.take(1), visits)
        assertEquals(4, rows.single().points)
    }

    @Test fun officer_with_no_visits_gets_zero_and_last_place() {
        val visits = listOf(VisitScore("a", "A**")) // 100
        val rows = rank(officers.take(2), visits) // b has no visits
        val byId = rows.associateBy { it.officerId }
        assertEquals(0, byId.getValue("b").points)
        assertEquals(2, byId.getValue("b").position)
    }

    // ---- lastDayOfPreviousMonth: cumulative "Last month" rank snapshot cutoff ----

    @Test fun mid_year_month_lands_on_previous_months_last_day() {
        assertEquals(LocalDate.parse("2026-06-30"), lastDayOfPreviousMonth(LocalDate.parse("2026-07-10")))
    }

    @Test fun january_rolls_back_to_dec_31_of_prior_year() {
        assertEquals(LocalDate.parse("2025-12-31"), lastDayOfPreviousMonth(LocalDate.parse("2026-01-15")))
    }

    @Test fun march_lands_on_last_day_of_february_non_leap_year() {
        assertEquals(LocalDate.parse("2026-02-28"), lastDayOfPreviousMonth(LocalDate.parse("2026-03-01")))
    }

    @Test fun march_lands_on_last_day_of_february_leap_year() {
        assertEquals(LocalDate.parse("2024-02-29"), lastDayOfPreviousMonth(LocalDate.parse("2024-03-15")))
    }

    @Test fun respects_short_and_long_month_lengths() {
        assertEquals(LocalDate.parse("2026-04-30"), lastDayOfPreviousMonth(LocalDate.parse("2026-05-01"))) // Apr = 30
        assertEquals(LocalDate.parse("2026-05-31"), lastDayOfPreviousMonth(LocalDate.parse("2026-06-01"))) // May = 31
    }

    @Test fun cutoff_is_stable_regardless_of_day_of_month() {
        assertEquals(LocalDate.parse("2026-06-30"), lastDayOfPreviousMonth(LocalDate.parse("2026-07-01")))
        assertEquals(LocalDate.parse("2026-06-30"), lastDayOfPreviousMonth(LocalDate.parse("2026-07-31")))
    }
}
