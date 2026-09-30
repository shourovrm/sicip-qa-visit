// three-way merge of a report's data JSON -- stops a stale copy on one platform wiping what was
// written on the other (2026-09-30: phone pushed its whole old blob over an officer's web edits).
// base = the server copy this client last saw, local = this client's current data, server = the
// row on the server right now. Per value: changed only locally -> local, changed only on the
// server -> server, changed on both -> recurse into objects / cards-style arrays (items matched
// by `_id`), else local wins. base unknown (null, rows synced before this existed) -> local
// wins except a blank local value never hides a filled server one.
// Mirrored 1:1 by web/src/lib/reportmerge.js; both checked against fixtures/merge-1.json.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun isBlankValue(value: JsonElement?): Boolean =
    value == null || value is JsonNull || (value is JsonPrimitive && value.isString && value.content.isBlank())

private fun idOf(element: JsonElement): String? = (element as? JsonObject)?.get("_id")?.jsonPrimitive?.contentOrNull

private fun isCardList(array: JsonArray): Boolean = array.all { idOf(it) != null }

// null result = "absent" (key dropped)
private fun mergeValue(base: JsonElement?, local: JsonElement?, server: JsonElement?, baseKnown: Boolean): JsonElement? {
    if (local == server) return local
    if (baseKnown && local == base) return server
    if (baseKnown && server == base) return local
    if (local is JsonObject && server is JsonObject) {
        val baseObject = base as? JsonObject
        val keys = LinkedHashSet(local.keys + server.keys)
        val merged = LinkedHashMap<String, JsonElement>()
        keys.forEach { key ->
            mergeValue(baseObject?.get(key), local[key], server[key], baseKnown && baseObject != null)?.let { merged[key] = it }
        }
        return JsonObject(merged)
    }
    if (local is JsonArray && server is JsonArray && isCardList(local) && isCardList(server)) {
        return mergeCards(base as? JsonArray, local, server, baseKnown && base is JsonArray)
    }
    if (!baseKnown && isBlankValue(local) && !isBlankValue(server)) return server
    return local
}

// cards: local order, then server-only cards. A card missing on one side is a delete only when
// the other side left it unchanged since base; otherwise it is kept (never lose an edit).
private fun mergeCards(base: JsonArray?, local: JsonArray, server: JsonArray, baseKnown: Boolean): JsonArray {
    val baseById = base?.associateBy { idOf(it) } ?: emptyMap()
    val serverById = server.associateBy { idOf(it) }
    val localIds = local.map { idOf(it) }.toSet()
    val out = mutableListOf<JsonElement>()
    local.forEach { card ->
        val id = idOf(card)
        val serverCard = serverById[id]
        val baseCard = baseById[id]
        when {
            serverCard == null && baseKnown && baseCard != null && baseCard == card -> Unit // deleted on server
            serverCard == null -> out += card
            else -> mergeValue(baseCard, card, serverCard, baseKnown && baseCard != null)?.let { out += it }
        }
    }
    server.forEach { card ->
        val id = idOf(card)
        if (id in localIds) return@forEach
        val baseCard = baseById[id]
        if (baseKnown && baseCard != null && baseCard == card) return@forEach // deleted here
        out += card
    }
    return JsonArray(out)
}

fun mergeReportData(base: JsonObject?, local: JsonObject, server: JsonObject): JsonObject =
    mergeValue(base, local, server, baseKnown = base != null) as? JsonObject ?: local
