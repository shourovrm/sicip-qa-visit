// shared suggestion lists in Supabase (migration 014, only "equipment" today): read on editor
// open, added to after a report save via the add_suggestions rpc. both silent on any failure
// (offline, signed out): the officer just types without the hints.
package bd.sicip.qavisit.data.suggest

import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private const val MAX_SUGGESTIONS = "1000"

class SharedSuggestions(private val client: SupabaseClient, private val sessions: SessionStore) {
    // most used first
    suspend fun fetch(list: String): List<String> = try {
        val session = sessions.ensureFresh(client)
        if (session == null) {
            emptyList()
        } else {
            val params = mapOf("list" to "eq.$list", "select" to "value", "order" to "uses.desc", "limit" to MAX_SUGGESTIONS)
            client.select("shared_suggestions", params, session.accessToken).mapNotNull { row ->
                ((row as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // true when the server took the values
    suspend fun add(list: String, values: List<String>): Boolean = try {
        val session = sessions.ensureFresh(client)
        if (session == null || values.isEmpty()) {
            false
        } else {
            val body = buildJsonObject {
                put("p_list", list)
                put("p_values", JsonArray(values.map { JsonPrimitive(it) }))
            }
            client.rpc("add_suggestions", body.toString(), session.accessToken)
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
