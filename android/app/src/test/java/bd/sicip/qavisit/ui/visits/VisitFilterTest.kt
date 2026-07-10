// VisitFilter: period boundaries (incl. custom range), district/category/purpose, combined.
package bd.sicip.qavisit.ui.visits

import bd.sicip.qavisit.data.db.Visit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-07-10")

private fun visit(
    id: String,
    startDate: String,
    district: String = "Dhaka",
    category: String = "N/A",
    purpose: String = "Monitoring Visit",
) = Visit(
    id = id,
    officerId = "o1",
    institute = "Inst",
    association = "Assoc",
    district = district,
    purpose = purpose,
    startDate = startDate,
    endDate = startDate,
    category = category,
    createdAt = "2026-01-01T00:00:00.000Z",
    updatedAt = "2026-01-01T00:00:00.000Z",
)

class VisitFilterTest {

    @Test fun all_time_keeps_everything() {
        val visits = listOf(visit("v1", "2020-01-01"), visit("v2", "2026-07-10"))
        assertEquals(visits, filterVisits(visits, VisitFilter(), TODAY))
    }

    // ---- this month: [1st, last day] of today's month, inclusive ----
    @Test fun this_month_includes_first_and_last_day() {
        val visits = listOf(visit("v1", "2026-07-01"), visit("v2", "2026-07-31"))
        assertEquals(visits, filterVisits(visits, VisitFilter(period = Period.ThisMonth), TODAY))
    }

    @Test fun this_month_excludes_adjacent_months() {
        val visits = listOf(visit("v1", "2026-06-30"), visit("v2", "2026-08-01"))
        assertEquals(emptyList<Visit>(), filterVisits(visits, VisitFilter(period = Period.ThisMonth), TODAY))
    }

    // ---- last 3 months: [today - 3 months, today], inclusive ----
    @Test fun last_3_months_boundary_inclusive() {
        val visits = listOf(visit("v1", "2026-04-10"), visit("v2", "2026-07-10"))
        assertEquals(visits, filterVisits(visits, VisitFilter(period = Period.Last3Months), TODAY))
    }

    @Test fun last_3_months_excludes_just_outside() {
        val visits = listOf(visit("v1", "2026-04-09"), visit("v2", "2026-07-11"))
        assertEquals(emptyList<Visit>(), filterVisits(visits, VisitFilter(period = Period.Last3Months), TODAY))
    }

    // ---- this year: [Jan 1, Dec 31], inclusive ----
    @Test fun this_year_includes_boundaries() {
        val visits = listOf(visit("v1", "2026-01-01"), visit("v2", "2026-12-31"))
        assertEquals(visits, filterVisits(visits, VisitFilter(period = Period.ThisYear), TODAY))
    }

    @Test fun this_year_excludes_other_years() {
        val visits = listOf(visit("v1", "2025-12-31"), visit("v2", "2027-01-01"))
        assertEquals(emptyList<Visit>(), filterVisits(visits, VisitFilter(period = Period.ThisYear), TODAY))
    }

    // ---- custom range: inclusive both ends ----
    @Test fun custom_range_includes_both_boundaries() {
        val visits = listOf(visit("v1", "2026-06-15"), visit("v2", "2026-07-05"))
        val period = Period.Custom(start = "2026-06-15", end = "2026-07-05")
        assertEquals(visits, filterVisits(visits, VisitFilter(period = period), TODAY))
    }

    @Test fun custom_range_excludes_just_outside_boundaries() {
        val visits = listOf(visit("v1", "2026-06-14"), visit("v2", "2026-07-06"))
        val period = Period.Custom(start = "2026-06-15", end = "2026-07-05")
        assertEquals(emptyList<Visit>(), filterVisits(visits, VisitFilter(period = period), TODAY))
    }

    // ---- district / category / purpose ----
    @Test fun district_filter() {
        val visits = listOf(visit("v1", "2026-07-01", district = "Dhaka"), visit("v2", "2026-07-01", district = "Sylhet"))
        assertEquals(listOf(visits[0]), filterVisits(visits, VisitFilter(district = "Dhaka"), TODAY))
    }

    @Test fun category_filter() {
        val visits = listOf(visit("v1", "2026-07-01", category = "E"), visit("v2", "2026-07-01", category = "A"))
        assertEquals(listOf(visits[0]), filterVisits(visits, VisitFilter(category = "E"), TODAY))
    }

    @Test fun purpose_filter() {
        val visits = listOf(
            visit("v1", "2026-07-01", purpose = "Recruitment"),
            visit("v2", "2026-07-01", purpose = "Others"),
        )
        assertEquals(listOf(visits[0]), filterVisits(visits, VisitFilter(purpose = "Recruitment"), TODAY))
    }

    // ---- combined ----
    @Test fun combined_period_district_category_purpose() {
        val match = visit("v1", "2026-07-05", district = "Dhaka", category = "E", purpose = "Recruitment")
        val wrongDistrict = visit("v2", "2026-07-05", district = "Sylhet", category = "E", purpose = "Recruitment")
        val wrongCategory = visit("v3", "2026-07-05", district = "Dhaka", category = "A", purpose = "Recruitment")
        val wrongPurpose = visit("v4", "2026-07-05", district = "Dhaka", category = "E", purpose = "Others")
        val wrongPeriod = visit("v5", "2026-01-05", district = "Dhaka", category = "E", purpose = "Recruitment")
        val filter = VisitFilter(period = Period.ThisMonth, district = "Dhaka", category = "E", purpose = "Recruitment")
        val result = filterVisits(listOf(match, wrongDistrict, wrongCategory, wrongPurpose, wrongPeriod), filter, TODAY)
        assertEquals(listOf(match), result)
    }

    @Test fun period_label_formats_custom_range() {
        assertEquals("Jun–Jul 2026", periodLabel(Period.Custom("2026-06-15", "2026-07-05")))
        assertEquals(null, periodLabel(Period.AllTime))
        assertEquals("This month", periodLabel(Period.ThisMonth))
    }
}
