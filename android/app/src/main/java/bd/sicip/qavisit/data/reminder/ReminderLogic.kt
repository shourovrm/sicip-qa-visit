// pure day-before/day-of visit reminder rules -- tested without any Android/WorkManager glue.
package bd.sicip.qavisit.data.reminder

import bd.sicip.qavisit.data.db.Visit
import java.time.LocalDate

// one line per qualifying visit, today's visits first then tomorrow's. re-guards status/deleted
// itself even though the caller's query is expected to have already filtered them, so a future
// caller that passes a broader list (e.g. all visits) still gets correct output.
fun reminderLines(visits: List<Visit>, today: LocalDate): List<String> {
    val tomorrow = today.plusDays(1)
    fun eligible(v: Visit) = v.status == "scheduled" && !v.deleted
    val todays = visits.filter { eligible(it) && it.startDate == today.toString() }
    val tomorrows = visits.filter { eligible(it) && it.startDate == tomorrow.toString() }
    return todays.map { "Today: ${it.purpose} — ${it.institute}" } +
        tomorrows.map { "Tomorrow: ${it.purpose} — ${it.institute}" }
}
