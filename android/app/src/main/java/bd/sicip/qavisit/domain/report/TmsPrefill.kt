// pure: TMS snapshot + report data -> data with empty QA v2 card fields filled (and missing
// course/batch cards added), plus "Use" suggestions where a filled-in value differs from TMS.
// rules: only empty fields are written, cards are never removed, target stays blank,
// tms_mismatch is never filled (officer counts heads; TMS present count is exposed as reference).
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsCourse
import bd.sicip.qavisit.data.tms.TmsRunningBatch
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID

const val MOU_COURSES = "mou_courses"
const val CUMULATIVE = "cumulative"
const val BATCHES = "batches"

data class TmsSuggestion(
    val cardsKey: String,
    val cardIndex: Int,
    val cardId: String,
    val fieldKey: String,
    val tmsValue: String,
)

// matched = filled fields whose current value already equals TMS (the editor's "TMS" marker).
data class TmsPrefillResult(
    val data: ReportData,
    val suggestions: List<TmsSuggestion>,
    val matched: List<TmsSuggestion> = emptyList(),
)

// one card the snapshot wants to see; batchNumber == null for course-level blocks.
private class WantedCard(val course: String, val batchNumber: String?, val values: Map<String, String>)

fun prefillFromTms(snapshot: TmsSnapshot, data: ReportData): TmsPrefillResult {
    val suggestions = mutableListOf<TmsSuggestion>()
    val matched = mutableListOf<TmsSuggestion>()
    var result = data
    val blocks = listOf(
        MOU_COURSES to snapshot.courses.map(::mouCourseCard),
        CUMULATIVE to snapshot.courses.map(::cumulativeCard),
        BATCHES to snapshot.runningBatches.map(::batchCard),
    )
    for ((cardsKey, wanted) in blocks) {
        val block = applyBlock(result.cards(cardsKey), cardsKey, wanted)
        result = result.withCardsReplaced(cardsKey, block.cards)
        suggestions += block.suggestions
        matched += block.matched
    }
    return TmsPrefillResult(result, suggestions, matched)
}

// TMS present count on the visit date, shown next to tms_mismatch as a reference.
fun tmsPresentToday(snapshot: TmsSnapshot, course: String, batchNumber: String): Int? =
    snapshot.runningBatches.firstOrNull { sameText(it.course, course) && sameText(it.batchNumber, batchNumber) }
        ?.attendanceToday

private fun mouCourseCard(course: TmsCourse) = WantedCard(
    course.name, null,
    mapOf(
        "course" to course.name,
        "duration" to course.duration,
        "batches" to course.targetBatches.toString(),
        "batch_size" to course.batchSize.toString(),
    ),
)

private fun cumulativeCard(course: TmsCourse) = WantedCard(
    course.name, null,
    mapOf(
        "course" to course.name,
        "enrolled_t" to course.enrolledTotal.toString(),
        "enrolled_f" to course.enrolledFemale.toString(),
        "certified_t" to course.certifiedTotal.toString(),
        "certified_f" to course.certifiedFemale.toString(),
        "placed_t" to course.placedTotal.toString(),
        "placed_f" to course.placedFemale.toString(),
        "dropout_t" to (course.dropoutTotal?.toString() ?: ""),
        "dropout_f" to (course.dropoutFemale?.toString() ?: ""),
    ),
)

private fun batchCard(batch: TmsRunningBatch) = WantedCard(
    batch.course, batch.batchNumber,
    mapOf(
        "course" to batch.course,
        "batch" to batch.batchNumber,
        "start_end" to "${batch.startDate} – ${batch.endDate}",
        "enrolled" to batch.enrolled.toString(),
        "female" to batch.female.toString(),
        "attendance_today" to (batch.attendanceToday?.toString() ?: ""),
        "attendance_7day" to (batch.attendance7day?.let(::formatMean) ?: ""),
    ),
)

private fun formatMean(mean: Double): String =
    if (mean == Math.floor(mean)) mean.toLong().toString() else mean.toString()

private fun sameText(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun sameValue(a: String, b: String): Boolean {
    val numberA = a.trim().toDoubleOrNull()
    val numberB = b.trim().toDoubleOrNull()
    return if (numberA != null && numberB != null) numberA == numberB else sameText(a, b)
}

private class BlockResult(
    val cards: List<JsonObject>,
    val suggestions: List<TmsSuggestion>,
    val matched: List<TmsSuggestion>,
)

private fun applyBlock(
    existing: List<JsonObject>,
    cardsKey: String,
    wantedCards: List<WantedCard>,
): BlockResult {
    val cards = existing.toMutableList()
    val claimedBlankCards = mutableSetOf<Int>()
    val suggestions = mutableListOf<TmsSuggestion>()
    val matched = mutableListOf<TmsSuggestion>()

    for (wanted in wantedCards) {
        var index = cards.indexOfFirst { matches(it, wanted) }
        if (index < 0) {
            // reuse an untouched blank card (the template's seeded first row) before adding one
            index = cards.indices.firstOrNull { it !in claimedBlankCards && isBlankIdentity(cards[it], wanted) } ?: -1
            if (index >= 0) claimedBlankCards += index
        }
        if (index < 0) {
            cards += buildJsonObject { put("_id", UUID.randomUUID().toString()) }
            index = cards.lastIndex
        }
        val card = cards[index].toMutableMap()
        for ((fieldKey, tmsValue) in wanted.values) {
            if (tmsValue.isEmpty()) continue
            val current = cards[index].text(fieldKey)
            if (current.isBlank()) {
                card[fieldKey] = JsonPrimitive(tmsValue)
            } else {
                val entry = TmsSuggestion(cardsKey, index, cards[index].text("_id"), fieldKey, tmsValue)
                if (sameValue(current, tmsValue)) matched += entry else suggestions += entry
            }
        }
        cards[index] = JsonObject(card)
    }
    return BlockResult(cards, suggestions, matched)
}

private fun matches(card: JsonObject, wanted: WantedCard): Boolean {
    if (card.text("course").isBlank() || !sameText(card.text("course"), wanted.course)) return false
    return wanted.batchNumber == null || sameText(card.text("batch"), wanted.batchNumber)
}

private fun isBlankIdentity(card: JsonObject, wanted: WantedCard): Boolean =
    card.text("course").isBlank() && (wanted.batchNumber == null || card.text("batch").isBlank())
