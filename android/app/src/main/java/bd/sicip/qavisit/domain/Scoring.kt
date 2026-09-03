// visit scoring: category -> points, rank aggregation. category is picked manually at
// End tour (no more auto-derivation from dates). pure kotlin, no android deps.
package bd.sicip.qavisit.domain

// fixed points scale per category.
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

// minimal shape scoring needs from a visit; keeps this file free of Room/android deps.
// done = visit.status == "done" -- only finished visits score, scheduled ones are worth 0.
data class VisitScore(val officerId: String, val category: String, val deleted: Boolean = false, val done: Boolean = true)

fun totalPoints(visits: List<VisitScore>): Int =
    visits.filter { !it.deleted && it.done }.sumOf { points(it.category) }

// officers ranked by summed points, lowest first (fewer points = better rank, see DECISIONS.md)
fun rank(visits: List<VisitScore>): List<Pair<String, Int>> =
    visits.filter { !it.deleted && it.done }
        .groupBy { it.officerId }
        .mapValues { (_, v) -> v.sumOf { points(it.category) } }
        .entries.sortedBy { it.value }
        .map { it.key to it.value }

// visits within a calendar month, for the home dashboard's "this month: n visits · m pts" line.
data class MonthVisit(val startDate: String, val category: String, val deleted: Boolean = false, val done: Boolean = true)

// yearMonth as "yyyy-MM" (java.time.YearMonth.toString() shape); startDate compared by its
// first 7 chars so this stays a plain string op, no date parsing needed.
fun monthSummary(visits: List<MonthVisit>, yearMonth: String): Pair<Int, Int> {
    val inMonth = visits.filter { !it.deleted && it.done }.filter { it.startDate.take(7) == yearMonth }
    return inMonth.size to inMonth.sumOf { points(it.category) }
}
