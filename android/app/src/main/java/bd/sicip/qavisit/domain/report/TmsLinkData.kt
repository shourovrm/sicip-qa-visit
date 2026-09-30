// reads/writes the TMS link + cached snapshot on a report's data JSON:
// data.tms = {tranche_id, entity_id, institute_id, institute_no, name, fetched_at, snapshot}.
// other keys under `tms` survive; the rest of the report is untouched.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val TMS_KEY = "tms"
private val json = Json { ignoreUnknownKeys = true }

private fun ReportData.tmsObject(): JsonObject? = root[TMS_KEY] as? JsonObject

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

fun ReportData.tmsLink(): TmsLink? {
    val tms = tmsObject() ?: return null
    val instituteId = (tms["institute_id"] as? JsonPrimitive)?.longOrNull ?: return null
    return TmsLink(
        trancheId = (tms["tranche_id"] as? JsonPrimitive)?.longOrNull ?: return null,
        entityId = (tms["entity_id"] as? JsonPrimitive)?.longOrNull ?: return null,
        instituteId = instituteId,
        instituteNo = tms.text("institute_no"),
        name = tms.text("name"),
    )
}

fun ReportData.tmsSnapshot(): TmsSnapshot? {
    val snapshot = tmsObject()?.get("snapshot") as? JsonObject ?: return null
    return runCatching { json.decodeFromJsonElement(TmsSnapshot.serializer(), snapshot) }.getOrNull()
}

// keeps an existing snapshot only if the link still points at the same institute.
fun ReportData.withTmsLink(link: TmsLink): ReportData {
    val old = tmsObject() ?: JsonObject(emptyMap())
    val sameInstitute = tmsLink()?.instituteId == link.instituteId
    val merged = old.toMutableMap().apply {
        put("tranche_id", JsonPrimitive(link.trancheId))
        put("entity_id", JsonPrimitive(link.entityId))
        put("institute_id", JsonPrimitive(link.instituteId))
        put("institute_no", JsonPrimitive(link.instituteNo))
        put("name", JsonPrimitive(link.name))
        if (!sameInstitute) {
            put("fetched_at", JsonNull)
            remove("snapshot")
        }
    }
    return withTopLevel(TMS_KEY, JsonObject(merged))
}

fun ReportData.withTmsSnapshot(snapshot: TmsSnapshot): ReportData {
    val old = tmsObject() ?: JsonObject(emptyMap())
    val merged = old.toMutableMap().apply {
        put("fetched_at", JsonPrimitive(snapshot.fetchedAt))
        put("snapshot", json.encodeToJsonElement(TmsSnapshot.serializer(), snapshot))
    }
    return withTopLevel(TMS_KEY, JsonObject(merged))
}

fun ReportData.tmsFetchedAt(): String? = tmsObject()?.text("fetched_at")?.takeIf { it.isNotEmpty() }

fun ReportData.withoutTmsLink(): ReportData = ReportData(JsonObject(root.filterKeys { it != TMS_KEY }))
