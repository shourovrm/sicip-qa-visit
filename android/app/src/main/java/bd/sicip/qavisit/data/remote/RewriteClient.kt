// "Improve wording" endpoint client -- one text field goes to our own Cloudflare Worker
// (sicip-qa-visit.shourovrm.workers.dev/api/rewrite), never a whole report. Same plain
// HttpsURLConnection + kotlinx-serialization stack as SupabaseClient.kt (no new dependency for
// one endpoint). JSON building/parsing and the status->message mapping are pulled out as pure
// functions below so they're unit-testable without a real network call (see RewriteClientTest).
package bd.sicip.qavisit.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val REWRITE_URL = "https://sicip-qa-visit.shourovrm.workers.dev/api/rewrite"
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

// pure: builds the fixed {"text","label"} request body the worker expects.
internal fun buildRewriteRequestBody(text: String, label: String): String =
    buildJsonObject {
        put("text", text)
        put("label", label)
    }.toString()

// pure: pulls "text" out of the worker's 200 {"text"} response.
internal fun parseRewriteResponseText(body: String): String =
    Json.parseToJsonElement(body).jsonObject.getValue("text").jsonPrimitive.content

// pure: http status -> officer-facing message, per the fixed API contract's error codes.
fun rewriteErrorMessage(httpStatus: Int): String = when (httpStatus) {
    401 -> "Session expired — sign in again"
    413 -> REWRITE_TOO_LONG_MESSAGE
    429 -> "Daily limit reached — try again tomorrow"
    else -> "Could not improve wording — try again"
}

class RewriteClient {
    suspend fun rewrite(text: String, label: String, accessToken: String): RewriteResult =
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
                    conn.outputStream.use { it.write(buildRewriteRequestBody(text, label).toByteArray()) }
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
}
