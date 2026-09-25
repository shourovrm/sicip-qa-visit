// "Improve wording" endpoint client -- one text field goes to our own Cloudflare Worker
// (sicip-qa-visit.shourovrm.workers.dev/api/rewrite), never a whole report. Same plain
// HttpsURLConnection + kotlinx-serialization stack as SupabaseClient.kt (no new dependency for
// one endpoint). JSON building/parsing and the status->message mapping are pulled out as pure
// functions below so they're unit-testable without a real network call (see RewriteClientTest).
package bd.sicip.qavisit.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val REWRITE_URL = "https://sicip-qa-visit.shourovrm.workers.dev/api/rewrite"
private const val MODELS_URL = "https://sicip-qa-visit.shourovrm.workers.dev/api/models"
private const val TIMEOUT_MS = 20_000

// server's own limit (413 above this) -- checked client-side too so an obviously-too-long
// remark never makes a round trip just to be told no.
const val REWRITE_MAX_CHARS = 1500

const val REWRITE_TOO_LONG_MESSAGE = "Too long to improve (max 1500 characters)"
const val REWRITE_OFFLINE_MESSAGE = "You are offline"

sealed class RewriteResult {
    data class Ok(val text: String) : RewriteResult()
    data class Err(val message: String) : RewriteResult()
}

// one GET /api/models {key,id,label,note} entry -- key is what the officer's pick + the rewrite
// request's "model" field carry, id/label/note are display-only (worker's own model id + officer
// facing name + one muted line, e.g. quirks or speed).
@Serializable
data class RewriteModel(val key: String, val id: String, val label: String, val note: String? = null)

// full GET /api/models body -- default is a key present in models, the server's own pick when
// the officer hasn't saved one (see settings/RewriteModelPref.kt).
@Serializable
data class RewriteModelsResponse(val default: String, val models: List<RewriteModel>)

// pure: builds the fixed {"text","label"[,"model"]} request body the worker expects. modelKey
// null means "let the server use its own default" -- the key is simply omitted, never sent blank.
internal fun buildRewriteRequestBody(text: String, label: String, modelKey: String? = null): String =
    buildJsonObject {
        put("text", text)
        put("label", label)
        if (modelKey != null) put("model", modelKey)
    }.toString()

// pure: pulls "text" out of the worker's 200 {"text"} response.
internal fun parseRewriteResponseText(body: String): String =
    Json.parseToJsonElement(body).jsonObject.getValue("text").jsonPrimitive.content

// pure: decodes GET /api/models's 200 body. Public (not internal) -- ui/profile/ProfileScreen.kt
// also calls this to re-parse the cached copy of this same body when offline.
// ignoreUnknownKeys: a field the worker adds later must not break the list on old APKs.
private val modelsJson = Json { ignoreUnknownKeys = true }
fun parseModelsResponse(body: String): RewriteModelsResponse = modelsJson.decodeFromString(body)

// pure: http status -> officer-facing message, per the fixed API contract's error codes.
fun rewriteErrorMessage(httpStatus: Int): String = when (httpStatus) {
    401 -> "Session expired — sign in again"
    413 -> REWRITE_TOO_LONG_MESSAGE
    429 -> "Daily limit reached — try again tomorrow"
    else -> "Could not improve wording — try again"
}

class RewriteClient {
    // modelKey = officer's saved settings/RewriteModelPref.kt pick, or null to let the worker
    // use its own default (unknown/stale key from an old cache is also the server's problem to
    // fall back on, per the API contract -- android never validates it against the model list).
    suspend fun rewrite(text: String, label: String, accessToken: String, modelKey: String? = null): RewriteResult =
        withContext(Dispatchers.IO) {
            if (text.length > REWRITE_MAX_CHARS) return@withContext RewriteResult.Err(REWRITE_TOO_LONG_MESSAGE)
            try {
                val conn = URL(REWRITE_URL).openConnection() as HttpsURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.use { it.write(buildRewriteRequestBody(text, label, modelKey).toByteArray()) }
                    val code = conn.responseCode
                    val responseBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code in 200..299) {
                        RewriteResult.Ok(parseRewriteResponseText(responseBody))
                    } else {
                        RewriteResult.Err(rewriteErrorMessage(code))
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                RewriteResult.Err(REWRITE_OFFLINE_MESSAGE)
            } catch (e: Exception) {
                // malformed 200 body (bad json / no "text") -- never crash the report screen over it
                RewriteResult.Err(rewriteErrorMessage(0))
            }
        }

    // GET /api/models -- no auth (see worker contract). Returns null on any failure (offline,
    // non-200, malformed body); the caller (ui/profile/ProfileScreen.kt) falls back to its own
    // cached copy of the last successful response, and to an "unavailable offline" message if
    // there isn't one yet.
    suspend fun fetchModels(): RewriteModelsResponse? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(MODELS_URL).openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                val code = conn.responseCode
                if (code !in 200..299) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseModelsResponse(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
