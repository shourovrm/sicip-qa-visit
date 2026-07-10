// visit scoring: category -> points, auto-category rules, rank aggregation.
// pure kotlin, no android deps.
package bd.sicip.qavisit.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// fixed points scale per category
val POINTS: Map<String, Int> = mapOf(
    "A**" to 100,
    "A++" to 85,
    "A+" to 69,
    "A" to 53,
    "B" to 36,
    "C" to 20,
    "D" to 4,
    "E" to 1,
    "N/A" to 0,
)

fun points(category: String): Int = POINTS[category] ?: 0

// minimal shape scoring needs from a visit; keeps this file free of Room/android deps
data class VisitScore(val officerId: String, val category: String, val deleted: Boolean = false)

fun totalPoints(visits: List<VisitScore>): Int =
    visits.filterNot { it.deleted }.sumOf { points(it.category) }

// officers ranked by summed points, highest first
fun rank(visits: List<VisitScore>): List<Pair<String, Int>> =
    visits.filterNot { it.deleted }
        .groupBy { it.officerId }
        .mapValues { (_, v) -> v.sumOf { points(it.category) } }
        .entries.sortedByDescending { it.value }
        .map { it.key to it.value }

// visits within a calendar month, for the home dashboard's "this month: n visits · m pts" line.
data class MonthVisit(val startDate: String, val category: String, val deleted: Boolean = false)

// yearMonth as "yyyy-MM" (java.time.YearMonth.toString() shape); startDate compared by its
// first 7 chars so this stays a plain string op, no date parsing needed.
fun monthSummary(visits: List<MonthVisit>, yearMonth: String): Pair<Int, Int> {
    val inMonth = visits.filterNot { it.deleted }.filter { it.startDate.take(7) == yearMonth }
    return inMonth.size to inMonth.sumOf { points(it.category) }
}

// district=Dhaka -> metro sub-option decides; else category from date span.
fun autoCategory(startDate: String, endDate: String, district: String, dhakaMetro: Boolean?): String {
    if (district == "Dhaka") {
        // metro flag unset (null) for a Dhaka visit: treat as outside metro -> "D"
        return if (dhakaMetro == true) "E" else "D"
    }
    val days = ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1
    return when {
        days <= 1 -> "D"     // 1-day trip outside Dhaka
        days == 2L -> "C"
        days == 3L -> "B"
        days == 4L -> "A"
        days == 5L -> "A+"
        days == 6L -> "A++"
        else -> "A**"        // 7+ days
    }
}
