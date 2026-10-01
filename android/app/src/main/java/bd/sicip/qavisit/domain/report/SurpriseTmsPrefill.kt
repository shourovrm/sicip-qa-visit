// surprise report: C attendance cards from the TMS snapshot (batch_summary enrolment, 7-class-day
// mean + a short range note in remarks) and A's address from the linked TMS institute. empty
// fields only; refresh = also overwrite the TMS numbers and an earlier TMS note, never typed text.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsRunningBatch
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

private const val ATTENDANCE = "attendance"
private const val NOTE_PREFIX = "TMS avg"

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun isBatch(card: JsonObject, batch: TmsRunningBatch): Boolean {
    val wanted = courseKey(card.text("course"))
    if (wanted.isEmpty() || card.text("batch").isEmpty()) return false
    val courseMatches = listOf(batch.course, batch.tmsCourse).any { it.isNotBlank() && courseKey(it) == wanted }
    return courseMatches && sameBatchNumber(card.text("batch"), batch.batchNumber)
}

fun prefillSurpriseFromTms(snapshot: TmsSnapshot, data: ReportData, refresh: Boolean = false): ReportData {
    val cards = data.cards(ATTENDANCE).map { card ->
        val batch = snapshot.runningBatches.firstOrNull { isBatch(card, it) } ?: return@map card
        filledCard(card, batch, refresh)
    }
    return data.withCardsReplaced(ATTENDANCE, cards)
}

private fun filledCard(card: JsonObject, batch: TmsRunningBatch, refresh: Boolean): JsonObject {
    val values = card.toMutableMap()
    fun fill(key: String, value: String) {
        if (value.isEmpty()) return
        if (refresh || card.text(key).isEmpty()) values[key] = JsonPrimitive(value)
    }
    fill("enrolled_total", batch.enrolled.toString())
    fill("enrolled_female", batch.female.toString())
    val mean = batch.attendance7day
    if (mean != null) fill("tms_avg7", formatMean(Math.round(mean * 10) / 10.0))
    val from = batch.averageFrom
    val to = batch.averageTo
    if (from != null && to != null) {
        val remarks = card.text("remarks")
        val replaceable = remarks.isEmpty() || (refresh && remarks.startsWith(NOTE_PREFIX))
        if (replaceable) values["remarks"] = JsonPrimitive(tmsAverageNote(from, to, batch.averageClassDays))
    }
    return JsonObject(values)
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
    return "$NOTE_PREFIX $range ($days)"
}

// A / section 1 "Address and contact" from the linked TMS institute, only while empty
fun withAddressIfEmpty(data: ReportData, address: String): ReportData {
    if (address.isBlank() || data.field("address").isNotBlank()) return data
    return data.withField("address", address.trim())
}
