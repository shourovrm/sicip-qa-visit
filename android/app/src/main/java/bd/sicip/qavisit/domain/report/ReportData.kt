// wraps a report's `data` column ({fields, checks, cards, flags} -- shape in the spec) as a
// raw JsonObject instead of a typed data class. A report's fields are officer-authored and
// forward-compatible by contract ("unknown keys must be preserved on save") -- a typed model
// would silently drop any key a newer template version or the other platform wrote that this
// build doesn't know about. Every `with*` setter below edits exactly the one key it's asked to
// and copies everything else through untouched, including keys this file has never heard of.
//
// API for agent A2 (UI): ReportData.parse(report.data) to load, data.toJsonString() to get the
// string back out for Room's Report.data column (call this after every edit -- autosave writes
// the whole string via reportDao().upsert(report.copy(data = newData.toJsonString(), dirty =
// true, updatedAt = now))). Readers: field(key), checkAnswer(itemId), checkRemarks(itemId),
// cards(cardsKey) (list of raw per-card JsonObject; read a value with
// card[fieldKey]?.jsonPrimitive?.contentOrNull), flags() (ticked flag id set). Writers, each
// returns a NEW ReportData: withField, withCheck (pass answer and/or remarks; "" clears an
// answer -- the checklist tap-again-to-clear UI just calls withCheck(id, answer = "")),
// withCardField, withCardAdded/withCardRemoved (cards block add/remove row), withFlag(id,
// ticked). See domain/report/ReportProgress.kt for the "blank" definition these all agree on.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

data class ReportData(val root: JsonObject) {
    companion object {
        val EMPTY = ReportData(
            buildJsonObject {
                put("fields", buildJsonObject {})
                put("checks", buildJsonObject {})
                put("cards", buildJsonObject {})
                put("flags", JsonArray(emptyList()))
            },
        )

        // blank/absent text (a brand-new report row, or a column that hasn't round-tripped
        // through the server yet) is treated the same as the empty shape, never a parse error.
        fun parse(text: String?): ReportData =
            if (text.isNullOrBlank()) EMPTY else ReportData(Json.parseToJsonElement(text).jsonObject)
    }

    fun toJsonString(): String = root.toString()

    private val fieldsObj: JsonObject get() = (root["fields"] as? JsonObject) ?: JsonObject(emptyMap())
    private val checksObj: JsonObject get() = (root["checks"] as? JsonObject) ?: JsonObject(emptyMap())
    private val cardsObj: JsonObject get() = (root["cards"] as? JsonObject) ?: JsonObject(emptyMap())
    private val flagsArr: JsonArray get() = (root["flags"] as? JsonArray) ?: JsonArray(emptyList())

    fun field(key: String): String = fieldsObj[key]?.jsonPrimitive?.contentOrNull ?: ""

    fun checkAnswer(itemId: String): String =
        (checksObj[itemId] as? JsonObject)?.get("answer")?.jsonPrimitive?.contentOrNull ?: ""

    fun checkRemarks(itemId: String): String =
        (checksObj[itemId] as? JsonObject)?.get("remarks")?.jsonPrimitive?.contentOrNull ?: ""

    // a perCourse checklist item's per-course answers (spec CHANGE SET 3), keyed by course
    // card _id -- empty for a non-perCourse item or one nobody has answered per-course yet.
    // the item's overall `answer` (checkAnswer above) is DERIVED from these by
    // ReportLinks.kt's syncPerCourse, never written here directly.
    fun checkCourses(itemId: String): Map<String, String> {
        val coursesObj = (checksObj[itemId] as? JsonObject)?.get("courses") as? JsonObject ?: return emptyMap()
        return coursesObj.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: "" }
    }

    // raw per-card objects for a cards block -- read a field with card[key]?.jsonPrimitive?.contentOrNull.
    fun cards(cardsKey: String): List<JsonObject> =
        (cardsObj[cardsKey] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    fun cardField(cardsKey: String, index: Int, fieldKey: String): String =
        cards(cardsKey).getOrNull(index)?.get(fieldKey)?.jsonPrimitive?.contentOrNull ?: ""

    // every card (seeded, officer-added, or synced-from-a-link) carries a stable "_id" (spec:
    // "Every card has a stable _id string") -- linked cards' is derived deterministically
    // (see ReportLinks.kt's syncLinks), everything else gets a random one on creation below.
    fun cardLinkSource(cardsKey: String, index: Int): String? =
        cards(cardsKey).getOrNull(index)?.get("_link")?.jsonPrimitive?.contentOrNull

    fun flags(): Set<String> = flagsArr.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()

    // same values as flags() but as a List in the order stored in `data.flags` (a JSON array,
    // not a set) -- reference.py's report_progress passes `data.flags` straight through as
    // flagsTicked verbatim, order and all, so this is what ReportProgress.kt reads for that.
    fun flagsList(): List<String> = flagsArr.mapNotNull { it.jsonPrimitive.contentOrNull }

    fun withField(key: String, value: String): ReportData {
        val newFields = JsonObject(fieldsObj.toMutableMap().apply { put(key, JsonPrimitive(value)) })
        return withRoot("fields", newFields)
    }

    // pass only the parameter that changed -- the other one is read from the existing entry
    // (or defaults to "" for a brand-new checklist item) so a remarks-only edit never clobbers
    // an already-chosen answer and vice versa.
    fun withCheck(itemId: String, answer: String? = null, remarks: String? = null): ReportData {
        val existing = (checksObj[itemId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (!existing.containsKey("answer")) existing["answer"] = JsonPrimitive("")
        if (!existing.containsKey("remarks")) existing["remarks"] = JsonPrimitive("")
        if (answer != null) existing["answer"] = JsonPrimitive(answer)
        if (remarks != null) existing["remarks"] = JsonPrimitive(remarks)
        val newChecks = JsonObject(checksObj.toMutableMap().apply { put(itemId, JsonObject(existing)) })
        return withRoot("checks", newChecks)
    }

    // the officer's actual edit action on one course's row of a perCourse item -- this only
    // writes that one course's answer, it does NOT touch the item's overall `answer`;
    // normalize() (ReportLinks.kt's syncPerCourse) derives that on the next pass, same as every
    // other autosaved edit.
    fun withCheckCourse(itemId: String, courseId: String, answer: String): ReportData {
        val existing = (checksObj[itemId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (!existing.containsKey("answer")) existing["answer"] = JsonPrimitive("")
        if (!existing.containsKey("remarks")) existing["remarks"] = JsonPrimitive("")
        val coursesObj = ((existing["courses"] as? JsonObject)?.toMutableMap()) ?: mutableMapOf()
        coursesObj[courseId] = JsonPrimitive(answer)
        existing["courses"] = JsonObject(coursesObj)
        val newChecks = JsonObject(checksObj.toMutableMap().apply { put(itemId, JsonObject(existing)) })
        return withRoot("checks", newChecks)
    }

    // ReportLinks.kt's syncPerCourse writes the derived overall answer + the filtered
    // per-course map back in one shot; remarks (and any other unknown key) pass through untouched.
    fun withCheckCoursesReplaced(itemId: String, courses: Map<String, String>, derivedAnswer: String): ReportData {
        val existing = (checksObj[itemId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (!existing.containsKey("remarks")) existing["remarks"] = JsonPrimitive("")
        existing["answer"] = JsonPrimitive(derivedAnswer)
        existing["courses"] = JsonObject(courses.mapValues { (_, v) -> JsonPrimitive(v) })
        val newChecks = JsonObject(checksObj.toMutableMap().apply { put(itemId, JsonObject(existing)) })
        return withRoot("checks", newChecks)
    }

    fun withCardField(cardsKey: String, index: Int, fieldKey: String, value: String): ReportData {
        val list = cards(cardsKey).toMutableList()
        if (index !in list.indices) return this // caller's bug (stale index) -- no-op, not a crash
        list[index] = JsonObject(list[index].toMutableMap().apply { put(fieldKey, JsonPrimitive(value)) })
        return withCardsReplaced(cardsKey, list)
    }

    // appends one row; template's `start` count is seeded by domain/report/NewReport.kt, this
    // is what the section screen's "Add <itemLabel>" button calls afterwards. "Random id on
    // create (uuid)" (spec) -- a caller only passes an explicit `card` for a linked target,
    // whose own _id/_link ReportLinks.kt's syncLinks computes itself.
    fun withCardAdded(cardsKey: String, card: JsonObject = buildJsonObject { put("_id", UUID.randomUUID().toString()) }): ReportData =
        withCardsReplaced(cardsKey, cards(cardsKey) + card)

    fun withCardRemoved(cardsKey: String, index: Int): ReportData {
        val list = cards(cardsKey).toMutableList()
        if (index !in list.indices) return this
        list.removeAt(index)
        return withCardsReplaced(cardsKey, list)
    }

    fun withFlag(flagId: String, ticked: Boolean): ReportData {
        val current = flags()
        val newSet = if (ticked) current + flagId else current - flagId
        return withRoot("flags", JsonArray(newSet.map { JsonPrimitive(it) }))
    }

    // replaces a whole cards[key] array wholesale -- ReportLinks.kt's syncLinks uses this
    // directly (it computes the entire synced list itself, not a single row edit).
    fun withCardsReplaced(cardsKey: String, list: List<JsonObject>): ReportData {
        val newCardsObj = JsonObject(cardsObj.toMutableMap().apply { put(cardsKey, JsonArray(list)) })
        return withRoot("cards", newCardsObj)
    }

    private fun withRoot(key: String, value: JsonElement): ReportData =
        ReportData(JsonObject(root.toMutableMap().apply { put(key, value) }))
}
