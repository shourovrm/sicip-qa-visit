// surprise report: C attendance cards from the TMS snapshot (batch_summary enrolment, 7-class-day
// mean + a short range note in remarks) and A's address from the linked TMS institute. empty
// fields only; a filled number that differs from TMS becomes a suggestion the officer may "Use"
// (web tmsprefill.js); the remarks note is only a starting text, never a suggestion.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsRunningBatch
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

private const val ATTENDANCE = "attendance"

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun isBatch(card: JsonObject, batch: TmsRunningBatch): Boolean {
    val wanted = courseKey(card.text("course"))
    if (wanted.isEmpty() || card.text("batch").isEmpty()) return false
    val courseMatches = listOf(batch.course, batch.tmsCourse).any { it.isNotBlank() && courseKey(it) == wanted }
    return courseMatches && sameBatchNumber(card.text("batch"), batch.batchNumber)
}

fun prefillSurpriseFromTms(snapshot: TmsSnapshot, data: ReportData): TmsPrefillResult {
    val suggestions = mutableListOf<TmsSuggestion>()
    val matched = mutableListOf<TmsSuggestion>()
    val cards = data.cards(ATTENDANCE).mapIndexed { index, card ->
        val batch = snapshot.runningBatches.firstOrNull { isBatch(card, it) } ?: return@mapIndexed card
        val values = card.toMutableMap()
        for ((key, tmsValue) in tmsNumbers(batch)) {
            if (tmsValue.isEmpty()) continue
            val current = card.text(key)
            val entry = TmsSuggestion(ATTENDANCE, index, card.text("_id"), key, tmsValue)
            when {
                current.isEmpty() -> values[key] = JsonPrimitive(tmsValue)
                sameNumber(current, tmsValue) -> matched += entry
                else -> suggestions += entry
            }
        }
        val from = batch.averageFrom
        val to = batch.averageTo
        if (from != null && to != null && card.text("remarks").isEmpty()) {
            values["remarks"] = JsonPrimitive(tmsAverageNote(from, to, batch.averageClassDays))
        }
        JsonObject(values)
    }
    return TmsPrefillResult(data.withCardsReplaced(ATTENDANCE, cards), suggestions, matched)
}

private fun tmsNumbers(batch: TmsRunningBatch): List<Pair<String, String>> = listOf(
    "enrolled_total" to batch.enrolled.toString(),
    "enrolled_female" to batch.female.toString(),
    "tms_avg7" to (batch.attendance7day?.let { formatMean(Math.round(it * 10) / 10.0) } ?: ""),
)

private fun sameNumber(a: String, b: String): Boolean {
    val left = a.toDoubleOrNull()
    val right = b.toDoubleOrNull()
    return if (left != null && right != null) left == right else a.equals(b, ignoreCase = true)
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun dayMonth(date: LocalDate) = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

// "TMS avg 18–28 Sep 2026 (7 class days)"; month and year printed only where they change
fun tmsAverageNote(fromIso: String, toIso: String, classDays: Int): String {
    val from = LocalDate.parse(fromIso)
    val to = LocalDate.parse(toIso)
    val range = when {
        from == to -> "${dayMonth(to)} ${to.year}"
        from.year != to.year -> "${dayMonth(from)} ${from.year} – ${dayMonth(to)} ${to.year}"
        from.monthValue != to.monthValue -> "${dayMonth(from)} – ${dayMonth(to)} ${to.year}"
        else -> "${from.dayOfMonth}–${dayMonth(to)} ${to.year}"
    }
    val days = if (classDays == 1) "1 class day" else "$classDays class days"
    return "TMS avg $range ($days)"
}

// A / section 1 "Address and contact" from the linked TMS institute, only while empty
fun withAddressIfEmpty(data: ReportData, address: String): ReportData {
    if (address.isBlank() || data.field("address").isNotBlank()) return data
    return data.withField("address", address.trim())
}
