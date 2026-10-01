// the card an "Add <item>" tap appends: a fresh _id, plus the block's newFrom seed.
// newFrom "firstCourse" (surprise L graduates): course + batch of the first section-A course card,
// still editable. mirrors web lib newCard.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val FIRST_COURSE_KEYS = listOf("course", "batch")

fun newCard(block: ReportBlock.Cards, data: ReportData): JsonObject {
    val values = linkedMapOf<String, JsonPrimitive>("_id" to JsonPrimitive(UUID.randomUUID().toString()))
    if (block.newFrom == "firstCourse") {
        val firstCourse = data.cards("courses").firstOrNull()
        val blockKeys = block.fields.map { it.key }.toSet()
        FIRST_COURSE_KEYS.filter { it in blockKeys }.forEach { key ->
            val value = firstCourse?.get(key)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (value.isNotEmpty()) values[key] = JsonPrimitive(value)
        }
    }
    return JsonObject(values)
}
