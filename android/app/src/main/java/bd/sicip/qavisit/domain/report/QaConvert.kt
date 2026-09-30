// QA v1 report data -> v2 shape (the Hub's "Convert to new format"). 1:1 port of
// web/src/lib/qaconvert.js. Nothing is deleted: every v1 key stays in the JSON (v2 just doesn't
// show the ones it dropped, e.g. the improvement plan cards). What moves:
// - officers text -> "officers" cards; registration text -> "Other registration"
// - 1.21-1.25 fields -> first MoU card; 1.31-1.34 text -> contracts + contract course rows
// - criteria answers follow their option ids into the v2 items (a few renamed ones via
//   OPTION_SOURCES); item evidence text -> numbered evidence entries; item notes follow the item
// - 6.2 "CBLM / lesson plan / TDP / job sheet available" answered Seen -> ticked boxes
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

// v2 option id -> the v1 option whose answer it takes over
private val OPTION_SOURCES = mapOf(
    "stock_register" to "ttm_registers",
    "fuel_register" to "ttm_registers",
    "tools_cover" to "assessment_tools",
    "training_record" to "staff_records",
)

// v1 items whose evidence/note now belong to a differently numbered v2 item
private val ITEM_MOVES = mapOf(
    "s8_5" to "s8_7", "s8_6" to "s2_3", "s81_1" to "s8_1e", "s81_2" to "s8_1e", "s81_3" to "s8_2", "s81_4" to "s8_2",
)

// v1 options answered Seen that become ticked boxes on a v2 item
private val TICKS_FROM_OPTIONS = mapOf("s6_2" to listOf("cblm", "lesson_plan", "tdp", "job_sheet"))

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun splitLines(text: String): List<String> = text.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

private fun card(id: String, vararg values: Pair<String, String>): JsonObject = buildJsonObject {
    put("_id", id)
    values.forEach { (key, value) -> put(key, value) }
}

private fun convertProfile(v2: ReportTemplate, v1Data: ReportData, newId: () -> String): ReportData {
    var data = v1Data
    if (data.cards("officers").isEmpty()) {
        val officers = splitOfficerLines(data.field("officers")).map { card(newId(), "name" to it.name, "designation" to it.designation) }
        data = data.withCardsReplaced("officers", officers)
    }
    if (data.field("other_registration").isBlank() && data.field("status").isNotBlank()) {
        data = data.withField("other_registration", data.field("status").trim())
    }

    val partner = data.field("contract_with").trim()
    val mouValues = listOf(
        "signed_date" to data.field("signed_date").trim(), "target" to data.field("mou_target").trim(),
        "duration" to data.field("mou_duration").trim(), "amount" to data.field("mou_amount").trim(),
    )
    if (data.cards("mous").isEmpty() && (partner.isNotEmpty() || mouValues.any { it.second.isNotEmpty() })) {
        val associations = v2.sections.first().blocks.filterIsInstance<ReportBlock.Cards>()
            .first { it.key == "mous" }.fields.first { it.key == "partner" }.selectOptions()
        val known = associations.find { it.equals(partner, ignoreCase = true) }
        val partnerValues = when {
            known != null -> listOf("partner" to known)
            partner.isNotEmpty() -> listOf("partner" to "Others", "partner_other" to partner)
            else -> emptyList()
        }
        data = data.withCardsReplaced("mous", listOf(card(newId(), *(partnerValues + mouValues).toTypedArray())))
    }

    val organisations = splitLines(data.field("other_orgs"))
    if (data.cards("contracts").isEmpty() && organisations.isNotEmpty()) {
        data = data.withCardsReplaced("contracts", organisations.map { card(newId(), "organisation" to it) })
    }
    val courses = splitLines(data.field("other_courses"))
    val overlapping = splitLines(data.field("overlapping_courses"))
    if (data.cards("contract_courses").isEmpty() && (courses.isNotEmpty() || overlapping.isNotEmpty())) {
        // one organisation: every course is under it; several: the officer picks per row
        val contract = if (organisations.size == 1) organisations[0] else ""
        val rows = courses.map { course ->
            val overlap = if (overlapping.any { it.equals(course, ignoreCase = true) }) "yes" else ""
            card(newId(), "contract" to contract, "course" to course, "overlap" to overlap)
        }.toMutableList()
        overlapping.filter { course -> courses.none { it.equals(course, ignoreCase = true) } }.forEach { course ->
            rows += card(newId(), "contract" to contract, "course" to course, "overlap" to "yes")
        }
        data = data.withCardsReplaced("contract_courses", rows)
    }
    val facilities = data.field("same_facilities").trim()
    val comments = data.field("comments").trim()
    if (facilities.isNotEmpty() && !comments.contains(facilities)) {
        data = data.withField("comments", listOf(comments, "Facilities: $facilities").filter { it.isNotEmpty() }.joinToString("\n"))
    }
    return data
}

private fun convertCriteria(v2: ReportTemplate, v1Data: ReportData, v1Criteria: JsonObject, newId: () -> String): ReportData {
    val v1Options = mutableMapOf<String, JsonElement>()
    v1Criteria.values.forEach { entry -> ((entry as? JsonObject)?.get("opts") as? JsonObject)?.let { v1Options.putAll(it) } }

    val criteria = linkedMapOf<String, MutableMap<String, JsonElement>>()
    for (section in v2.sections) {
        for (block in section.blocks.filterIsInstance<ReportBlock.Criteria>()) {
            for (item in block.items.filterNot { it.heading }) {
                val opts = buildJsonObject {
                    item.options.forEach { option -> v1Options[OPTION_SOURCES[option.id] ?: option.id]?.let { put(option.id, it) } }
                }
                val entry = mutableMapOf<String, JsonElement>("opts" to opts)
                val ticks = TICKS_FROM_OPTIONS[item.id].orEmpty().filter { id ->
                    ((v1Options[id] as? JsonObject)?.get("v") as? JsonPrimitive)?.contentOrNull == "seen"
                }
                if (ticks.isNotEmpty()) entry["ticks"] = JsonArray(ticks.map { JsonPrimitive(it) })
                criteria[item.id] = entry
            }
        }
    }

    // item notes + evidence text: from the v1 item of the same id, or the one that moved here
    fun v2ItemFor(v1ItemId: String): String? = ITEM_MOVES[v1ItemId] ?: v1ItemId.takeIf { it in criteria }
    v1Criteria.forEach { (v1ItemId, entry) ->
        val target = v2ItemFor(v1ItemId) ?: return@forEach
        val note = (entry as? JsonObject)?.text("note").orEmpty()
        if (note.isEmpty()) return@forEach
        val existing = (criteria.getValue(target)["note"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        criteria.getValue(target)["note"] = JsonPrimitive(listOf(existing, note).filter { it.isNotEmpty() }.joinToString("\n"))
    }
    var data = v1Data.withTopLevel("criteria", JsonObject(criteria.mapValues { JsonObject(it.value) }))
    v1Criteria.forEach { (v1ItemId, entry) ->
        val target = v2ItemFor(v1ItemId) ?: return@forEach
        splitLines((entry as? JsonObject)?.text("evidence").orEmpty()).forEach { name ->
            data = withEvidenceAdded(data, target, name, newId)
        }
    }
    return data
}

fun convertQaV1ToV2(v2: ReportTemplate, v1Data: ReportData, newId: () -> String = { UUID.randomUUID().toString() }): ReportData {
    // the raw v1 answers stay under their own key (v2 rebuilds data.criteria)
    val v1Criteria = v1Data.criteriaRaw()
    var data = v1Data.withTopLevel("criteriaV1", v1Criteria)
    data = convertProfile(v2, data, newId)
    data = convertCriteria(v2, data, v1Criteria, newId)

    // 4.2's old "number of applicants" box -> the first course row of the sample-check table
    val applicants = ((v1Criteria["s4_2"] as? JsonObject)?.get("opts") as? JsonObject)
        ?.let { (it["applicants_list"] as? JsonObject)?.text("detail") }.orEmpty()
    if (applicants.isNotEmpty()) {
        val selection = data.cards("selection")
        data = when {
            selection.isEmpty() -> data.withCardsReplaced("selection", listOf(card(newId(), "applicants" to applicants)))
            selection[0].text("applicants").isEmpty() -> data.withCardField("selection", 0, "applicants", applicants)
            else -> data
        }
    }
    return normalize(v2, data)
}
