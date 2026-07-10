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
