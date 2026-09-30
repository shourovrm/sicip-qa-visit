// tells the admin (public.tms_errors) that the TMS API changed. best-effort: never throws,
// never sends response bodies. the table may not exist yet server-side; that failure is silent.
package bd.sicip.qavisit.data.tms

import bd.sicip.qavisit.BuildConfig
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_DETAIL_CHARS = 300
private const val DEDUPE_COLUMNS = "endpoint,kind,app_version,day" // server unique key; one row per problem per day

// officer_id and day are filled by column defaults server-side.
fun tmsErrorRow(error: TmsApiChangeException, appVersion: String): JsonObject = buildJsonObject {
    put("app_version", appVersion)
    put("endpoint", endpointOf(error.endpoint))
    put("kind", error.kind)
    put("detail", error.detail.take(MAX_DETAIL_CHARS))
}

class TmsErrorReporter(
    private val client: SupabaseClient,
    private val sessions: SessionStore,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) {
    suspend fun report(error: TmsApiChangeException) {
        try {
            val session = sessions.ensureFresh(client) ?: return
            client.insertIgnoreDuplicates(
                "tms_errors",
                DEDUPE_COLUMNS,
                JsonArray(listOf(tmsErrorRow(error, appVersion))),
                session.accessToken,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // table missing, offline, RLS: nothing useful to do
        }
    }
}
