// surprise v1 report data -> v2 shape (the Hub's "Convert to new format"). Mirrors
// web/src/lib/reportconvert.js. Nothing is deleted: every v1 key stays in the JSON (v2 just
// doesn't show the ones it dropped), so converting loses no data. What moves:
// - officers text -> "officers" cards ("Name (Designation)" split when written that way)
// - H3 monitoring logbook answer -> K's followup_1
// - C attendance trainers_present -> the new C "trainers" card of the same course
// - J graduate "Course / batch" text -> course; Employment ids renamed to v2's
// - M key findings -> major findings; instructions + follow-up -> recommendations
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

private val NAME_WITH_DESIGNATION = Regex("""^(.*?)\s*\((.+)\)\s*$""")

private val EMPLOYMENT_V1_TO_V2 = mapOf("same" to "employed", "other" to "other_job", "none" to "not_employed")

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun splitLines(text: String): List<String> = text.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

fun convertSurpriseV1ToV2(v2: ReportTemplate, v1Data: ReportData, newId: () -> String = { UUID.randomUUID().toString() }): ReportData {
    var data = v1Data

    // A: visiting officers
    if (data.cards("officers").isEmpty()) {
        val officers = splitLines(data.field("officers")).map { line ->
            val match = NAME_WITH_DESIGNATION.find(line)
            buildJsonObject {
                put("_id", newId())
                put("name", match?.groupValues?.get(1) ?: line)
                put("designation", match?.groupValues?.get(2) ?: "")
            }
        }
        data = data.withCardsReplaced("officers", officers)
    }

    // H3 -> K followup_1
    val logbookAnswer = data.checkAnswer("registers_3")
    val logbookRemarks = data.checkRemarks("registers_3")
    if ((logbookAnswer.isNotBlank() || logbookRemarks.isNotBlank()) && data.checkAnswer("followup_1").isBlank()) {
        data = data.withCheck("followup_1", answer = logbookAnswer, remarks = logbookRemarks)
    }

    // J graduates: course/batch text -> course, employment ids
    val graduates = data.cards("graduate").map { card ->
        val merged = card.toMutableMap()
        if (card.text("course").isEmpty() && card.text("batch").isNotEmpty()) {
            merged["course"] = JsonPrimitive(card.text("batch"))
            merged["batch"] = JsonPrimitive("")
        }
        EMPLOYMENT_V1_TO_V2[card.text("confirmed")]?.let { merged["confirmed"] = JsonPrimitive(it) }
        JsonObject(merged)
    }
    data = data.withCardsReplaced("graduate", graduates)

    // M -> L findings + recommendations
    if (data.findings().isEmpty()) {
        data = data.withFindings(splitLines(data.field("key_findings")).map { Finding("", it) })
    }
    if (data.field("recommendations").isBlank()) {
        val followUp = data.field("follow_up").takeIf { it.isNotBlank() && it != "No further action" }
        val lines = splitLines(data.field("instructions_given")) + listOfNotNull(followUp?.let { "Recommended follow-up: $it" })
        data = data.withField("recommendations", lines.joinToString("\n"))
    }

    // C: trainers present per course -- linked cards are created by normalize, then filled
    val trainersPresent = data.cards("attendance").associate { it.text("_link") to it.text("trainers_present") }
    data = normalize(v2, data)
    val trainers = data.cards("trainers").map { card ->
        val present = trainersPresent[card.text("_link")].orEmpty()
        if (present.isEmpty() || card.text("present").isNotEmpty()) card else JsonObject(card + ("present" to JsonPrimitive(present)))
    }
    return data.withCardsReplaced("trainers", trainers)
}

private val EMPLOYMENT_V2_TO_V1 = mapOf(
    "employed" to "same", "other_job" to "other", "self_employed" to "other", "others" to "other", "not_employed" to "none",
)

// the Hub's "Revert to old format": v2 data -> v1 shape. Every key stays; answers given in v2
// are copied back to where v1 shows them (officers, H3 logbook, trainers present, J course/
// batch + employment, key findings, instructions), so reverting loses nothing either.
fun revertSurpriseV2ToV1(v1: ReportTemplate, v2Data: ReportData): ReportData {
    var data = v2Data

    val officers = data.cards("officers").mapNotNull { card ->
        val name = card.text("name")
        val designation = card.text("designation")
        when {
            name.isEmpty() -> null
            designation.isEmpty() -> name
            else -> "$name ($designation)"
        }
    }
    if (officers.isNotEmpty()) data = data.withField("officers", officers.joinToString("\n"))

    val logbookAnswer = data.checkAnswer("followup_1")
    val logbookRemarks = data.checkRemarks("followup_1")
    if (logbookAnswer.isNotBlank() || logbookRemarks.isNotBlank()) {
        data = data.withCheck("registers_3", answer = logbookAnswer, remarks = logbookRemarks)
    }

    val trainersPresent = data.cards("trainers").associate { it.text("_link") to it.text("present") }
    data = data.withCardsReplaced("attendance", data.cards("attendance").map { card ->
        val present = trainersPresent[card.text("_link")].orEmpty()
        if (present.isEmpty()) card else JsonObject(card + ("trainers_present" to JsonPrimitive(present)))
    })

    data = data.withCardsReplaced("graduate", data.cards("graduate").map { card ->
        val merged = card.toMutableMap()
        val courseBatch = listOf(card.text("course"), card.text("batch")).filter { it.isNotEmpty() }.joinToString(" ")
        if (courseBatch.isNotEmpty()) {
            merged["batch"] = JsonPrimitive(courseBatch)
            merged["course"] = JsonPrimitive("")
        }
        EMPLOYMENT_V2_TO_V1[card.text("confirmed")]?.let { merged["confirmed"] = JsonPrimitive(it) }
        JsonObject(merged)
    })

    val findings = data.findings().map { it.text.trim() }.filter { it.isNotEmpty() }
    if (findings.isNotEmpty()) data = data.withField("key_findings", findings.joinToString("\n"))
    if (data.field("recommendations").isNotBlank()) data = data.withField("instructions_given", data.field("recommendations"))

    return normalize(v1, data)
}
