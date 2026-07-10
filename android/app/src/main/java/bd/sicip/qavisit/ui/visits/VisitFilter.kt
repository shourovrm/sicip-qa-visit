// pure filter for the Visits list: period (start_date range) + district/category/purpose equality.
// officer filtering (Team tab) stays separate -- it already existed before this filter row and
// isn't part of the shared filter chips.
package bd.sicip.qavisit.ui.visits

import bd.sicip.qavisit.data.db.Visit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

const val FILTER_ALL = "All"

sealed class Period {
    object AllTime : Period()
    object ThisMonth : Period()
    object Last3Months : Period()
    object ThisYear : Period()
    data class Custom(val start: String, val end: String) : Period()
}

data class VisitFilter(
    val period: Period = Period.AllTime,
    val district: String = FILTER_ALL,
    val category: String = FILTER_ALL,
    val purpose: String = FILTER_ALL,
)

fun filterVisits(visits: List<Visit>, filter: VisitFilter, today: LocalDate = LocalDate.now()): List<Visit> {
    val range = periodRange(filter.period, today)
    return visits.filter { v ->
        val start = LocalDate.parse(v.startDate)
        (range == null || (!start.isBefore(range.first) && !start.isAfter(range.second))) &&
            (filter.district == FILTER_ALL || v.district == filter.district) &&
            (filter.category == FILTER_ALL || v.category == filter.category) &&
            (filter.purpose == FILTER_ALL || v.purpose == filter.purpose)
    }
}

// null = no bound (All time). every other range is inclusive on both ends.
private fun periodRange(period: Period, today: LocalDate): Pair<LocalDate, LocalDate>? = when (period) {
    Period.AllTime -> null
    Period.ThisMonth -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
    Period.Last3Months -> today.minusMonths(3) to today
    Period.ThisYear -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
    is Period.Custom -> LocalDate.parse(period.start) to LocalDate.parse(period.end)
}

// chip text for an active period; null means "All time" (default) -- caller shows the plain
// "Period" label instead.
fun periodLabel(period: Period): String? = when (period) {
    Period.AllTime -> null
    Period.ThisMonth -> "This month"
    Period.Last3Months -> "Last 3 months"
    Period.ThisYear -> "This year"
    is Period.Custom -> {
        val start = LocalDate.parse(period.start)
        val end = LocalDate.parse(period.end)
        val sMon = start.month.getDisplayName(TextStyle.SHORT, Locale.US)
        val eMon = end.month.getDisplayName(TextStyle.SHORT, Locale.US)
        if (start.year == end.year) "$sMon–$eMon ${end.year}" else "$sMon ${start.year}–$eMon ${end.year}"
    }
}
