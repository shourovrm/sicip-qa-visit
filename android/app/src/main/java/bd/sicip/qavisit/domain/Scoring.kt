// visit scoring: category -> points, auto-category rules, rank aggregation.
// pure kotlin, no android deps.
package bd.sicip.qavisit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// fixed points scale per category. classic ladder (nights = days-1) vs plus ladder
// (nights >= days, i.e. an extra night beyond the classic span) -- see autoCategory.
val POINTS: Map<String, Int> = mapOf(
    "A***" to 116,
    "A**+" to 112,
    "A**" to 100,
    "A++*" to 96,
    "A++" to 84,
    "A+*" to 80,
    "A+" to 68,
    "A*" to 64,
    "A" to 52,
    "B+" to 48,
    "B" to 36,
    "C+" to 32,
    "C" to 20,
    "D+" to 16,
    "D" to 4,
    "E" to 1,
    "N/A" to 0,
)

fun points(category: String): Int = POINTS[category] ?: 0

// category -> (days, nights) span it represents. single source for bill allowances: nights-away
// and food-days are both derived from this, not stored/edited separately -- see suggestedNights/
// suggestedFood below and CATEGORIES.md at repo root for the full table + formula.
val CATEGORY_SPANS: Map<String, Pair<Int, Int>> = mapOf(
    "A***" to (8 to 7),
    "A**+" to (7 to 7),
    "A**" to (7 to 6),
    "A++*" to (6 to 6),
    "A++" to (6 to 5),
    "A+*" to (5 to 5),
    "A+" to (5 to 4),
    "A*" to (4 to 4),
    "A" to (4 to 3),
    "B+" to (3 to 3),
    "B" to (3 to 2),
    "C+" to (2 to 2),
    "C" to (2 to 1),
    "D+" to (1 to 1),
    "D" to (1 to 0),
    "E" to (0 to 0),
    "N/A" to (0 to 0),
)

// accommodation nights = the category's night count, flat.
fun suggestedNights(category: String): Int = CATEGORY_SPANS[category]?.second ?: 0

// food-days = every night full + a half-day for each day beyond the last night (the day-1
// morning-out and the final travel-home day), same shape as BillMath's old span-default rule,
// now keyed off the category instead of raw dates.
fun suggestedFood(category: String): Double {
    val (d, n) = CATEGORY_SPANS[category] ?: (0 to 0)
    return n + 0.5 * (d - n)
}

// full explanation shown wherever the user picks/reads a category (dropdowns); stored value stays
// the bare code (POINTS key) -- this map is display-only.
val CATEGORY_LABELS: Map<String, String> = mapOf(
    "A***" to "A*** — 8D7N (116 pts)",
    "A**+" to "A**+ — 7D7N (112 pts)",
    "A**" to "A** — 7D6N (100 pts)",
    "A++*" to "A++* — 6D6N (96 pts)",
    "A++" to "A++ — 6D5N (84 pts)",
    "A+*" to "A+* — 5D5N (80 pts)",
    "A+" to "A+ — 5D4N (68 pts)",
    "A*" to "A* — 4D4N (64 pts)",
    "A" to "A — 4D3N (52 pts)",
    "B+" to "B+ — 3D3N (48 pts)",
    "B" to "B — 3D2N (36 pts)",
    "C+" to "C+ — 2D2N (32 pts)",
    "C" to "C — 2D1N (20 pts)",
    "D+" to "D+ — 1D1N (16 pts)",
    "D" to "D — 1 day / Dhaka non-metro (4 pts)",
    "E" to "E — Dhaka metro (1 pt)",
    "N/A" to "N/A — Additional (0 pts)",
)

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

// classic ladder: days worked == nights + 1 (leave morning, work every day, home same evening).
private fun classicLadder(days: Int): String = when {
    days <= 1 -> "D"
    days == 2 -> "C"
    days == 3 -> "B"
    days == 4 -> "A"
    days == 5 -> "A+"
    days == 6 -> "A++"
    days == 7 -> "A**"
    else -> "A***" // 8+ days
}

// plus ladder: an extra night tacked onto the span (nights >= days) -- one rung above classic.
private fun plusLadder(days: Int): String = when {
    days <= 1 -> "D+"
    days == 2 -> "C+"
    days == 3 -> "B+"
    days == 4 -> "A*"
    days == 5 -> "A+*"
    days == 6 -> "A++*"
    days == 7 -> "A**+"
    else -> "A***" // 8+ days, same cap as classic
}

// district=Dhaka -> metro sub-option decides; else category from days/nights via the ladders
// above. computed from category at display time (not stored) so ranks auto-recalculate if the
// formula ever changes again.
fun autoCategory(days: Int, nights: Int, district: String, dhakaMetro: Boolean?): String {
    if (district == "Dhaka") {
        // metro flag unset (null) for a Dhaka visit: treat as outside metro -> "D"
        return if (dhakaMetro == true) "E" else "D"
    }
    val d = maxOf(days, 1)
    return if (nights >= d) plusLadder(d) else classicLadder(d)
}

// date-only overload for the scheduling preview, where there's no time-of-return yet to derive
// nights from the 08:00 cutoff -- assumes the classic (nights = days-1) shape.
fun autoCategory(startDate: String, endDate: String, district: String, dhakaMetro: Boolean?): String {
    val days = (ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1).toInt()
    return autoCategory(days, maxOf(days - 1, 0), district, dhakaMetro)
}

// a return before this local time doesn't count as a working day (e.g. leave 9pm, home 7am the
// next morning never actually worked that day) -- office-tunable, change here if the cutoff moves.
private val RETURN_CUTOFF: LocalTime = LocalTime.of(8, 0)

// days/nights for FinishTrip: nights = midnights crossed between the two instants; days = dates
// touched, minus one (min 1) if the return lands before RETURN_CUTOFF -- that's what turns a
// leave-9pm/return-7am trip into 1D1N instead of 2D1N.
fun daysAndNights(startIso: String, endIso: String): Pair<Int, Int> {
    val startDate = Instant.parse(startIso).atZone(ZoneOffset.UTC).toLocalDate()
    val end = Instant.parse(endIso).atZone(ZoneOffset.UTC)
    val nights = ChronoUnit.DAYS.between(startDate, end.toLocalDate()).toInt()
    var days = nights + 1
    if (end.toLocalTime() < RETURN_CUTOFF) days -= 1
    return maxOf(days, 1) to nights
}
