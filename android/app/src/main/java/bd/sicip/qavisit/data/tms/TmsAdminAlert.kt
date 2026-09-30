// TMS API-change alerts fetched from Supabase. silent on any failure (offline, table missing,
// RLS): the banner just does not show.
//  - admin: unresolved tms_errors rows -> one banner line, same wording as the web banner
//  - everyone: tms_api_broken() rpc -> true while any unresolved error exists
package bd.sicip.qavisit.data.tms

import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class TmsErrorRow(val endpoint: String, val kind: String, val detail: String, val day: String)

// rows may come in any order; day is an ISO date so string order is date order.
fun tmsAlertText(rows: List<TmsErrorRow>): String? {
    if (rows.isEmpty()) return null
    val kinds = rows.map { it.endpoint to it.kind }.distinct().size
    val firstDay = rows.minOf { it.day }
    val latest = rows.maxByOrNull { it.day } ?: return null
    return "TMS API changed: $kinds error kinds since $firstDay, latest ${latest.endpoint} (${latest.detail})"
}

fun parseTmsErrorRows(array: JsonArray): List<TmsErrorRow> = array.mapNotNull { element ->
    val row = element as? JsonObject ?: return@mapNotNull null
    fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull ?: ""
    TmsErrorRow(text("endpoint"), text("kind"), text("detail"), text("day"))
}

suspend fun fetchTmsAlertText(client: SupabaseClient, sessions: SessionStore): String? = try {
    val session = sessions.ensureFresh(client)
    if (session == null) {
        null
    } else {
        val params = mapOf(
            "resolved_at" to "is.null",
            "select" to "endpoint,kind,detail,day",
            "order" to "day.desc",
        )
        tmsAlertText(parseTmsErrorRows(client.select("tms_errors", params, session.accessToken)))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

// public.tms_api_broken() answers a bare true/false; null = could not ask.
suspend fun fetchTmsApiBroken(client: SupabaseClient, sessions: SessionStore): Boolean? = try {
    val session = sessions.ensureFresh(client)
    if (session == null) null else client.rpc("tms_api_broken", "{}", session.accessToken).trim().toBooleanStrictOrNull()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
