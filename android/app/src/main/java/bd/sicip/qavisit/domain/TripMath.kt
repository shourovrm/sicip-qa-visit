// pure trip helpers: day-in-trip counter, primary-visit pick, fare-sum formatting.
// no android/room deps, same rule as Scoring.kt/BillMath.kt.
package bd.sicip.qavisit.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

// "DAY n" on the active-trip hero: day of startedAt itself is day 1.
fun dayNumber(startedAt: String, now: Instant = Instant.now()): Int {
    val startDay = Instant.parse(startedAt).atZone(ZoneOffset.UTC).toLocalDate()
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    return (ChronoUnit.DAYS.between(startDay, today) + 1).toInt().coerceAtLeast(1)
}

// primary visit on a trip = first non-additional one; if every visit is (somehow) marked
// additional, fall back to the first one so a trip is never left without a "primary".
// generic over the caller's own row type so this stays free of the Room Visit entity.
fun <T> primaryVisit(visits: List<T>, isAdditional: (T) -> Boolean): T? =
    visits.firstOrNull { !isAdditional(it) } ?: visits.firstOrNull()

// leg-fare sum, Bengali taka symbol + thousands separator. drops a bare ".00".
fun formatFare(total: Double): String {
    val formatted = String.format(Locale.US, "%,.2f", total)
    val trimmed = if (formatted.endsWith(".00")) formatted.dropLast(3) else formatted
    return "৳$trimmed"
}

// combine a leg's plain date + HH:mm:ss time into the same "yyyy-MM-ddTHH:mm:ssZ" shape
// trip.started_at/finished_at already use (see StartTrip/FinishTrip) -- lexicographic string
// compare then sorts chronologically against both leg keys and trip timestamps.
private fun legDateTime(date: String, time: String): String = "${date}T${time}Z"

// T5 #1/#3: tour chronological key for bill ordering AND the imposed trip.started_at value --
// same formula, earliest leg departure if any legs exist, else the trip's own started_at.
// (date, time) pairs, ascending or not, doesn't matter -- min is taken here.
fun tourStartKey(legDeps: List<Pair<String, String>>, startedAt: String): String =
    legDeps.minOfOrNull { (d, t) -> legDateTime(d, t) } ?: startedAt

// T5 #3: imposed trip.finished_at -- latest leg arrival if legs exist, else the trip's own
// finished_at (nullable: an unfinished trip never reaches bill prep, but the type stays honest).
// no cross-midnight leg modeling today -- arr_date is already a real per-leg field, so an
// overnight arrival just needs whoever enters the leg to set arr_date to the next day; nothing
// here infers it. keep simple per spec.
fun tourEndKey(legArrs: List<Pair<String, String>>, finishedAt: String?): String? =
    legArrs.maxOfOrNull { (d, t) -> legDateTime(d, t) } ?: finishedAt

// T5 #2: tour-select card title = every visit's institute, primary's first, comma-separated,
// no dedup (spec: "ALL institute names"). generic like primaryVisit above, no Room dependency.
fun <T> instituteTitle(visits: List<T>, isAdditional: (T) -> Boolean, institute: (T) -> String): String {
    if (visits.isEmpty()) return ""
    val primaryIdx = visits.indexOfFirst { !isAdditional(it) }.let { if (it >= 0) it else 0 }
    val ordered = listOf(visits[primaryIdx]) + visits.filterIndexed { i, _ -> i != primaryIdx }
    return ordered.joinToString(", ") { institute(it) }
}

// T5 #2: tour-select card subtitle -- "start – end · N visit(s) · N travel(s) · Σ fare",
// singular noun at n=1 (mockup: "3 visits · 3 travels", "1 visit"/"1 travel" elsewhere).
fun tourSubtitle(startDate: String, endDate: String, visitCount: Int, travelCount: Int, fareSum: Double): String =
    "$startDate – $endDate · ${plural(visitCount, "visit")} · ${plural(travelCount, "travel")} · Σ ${formatFare(fareSum)}"

private fun plural(n: Int, noun: String): String = "$n $noun${if (n == 1) "" else "s"}"

// T5 #4: bill purpose band for a tour with possibly several visits -- one clause per visit
// (caller builds "purpose - association (Ref: n, date)" per visit), identical clauses collapse
// to one (distinct() is order-preserving), joined "; " under a single "Purpose: " prefix so a
// multi-institute tour still prints exactly one band, not one per visit.
fun purposeBand(clauses: List<String>): String = "Purpose: ${clauses.distinct().joinToString("; ")}"
