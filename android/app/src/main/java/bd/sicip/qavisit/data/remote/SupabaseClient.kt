// lean http client for supabase auth (gotrue) + rest (postgrest). plain
// HttpsURLConnection + kotlinx-serialization, no ktor/supabase-kt dep — a handful of
// endpoints doesn't earn a whole sdk. no retries: caller (sync layer) decides that.
package bd.sicip.qavisit.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

private const val TIMEOUT_MS = 15_000

// non-2xx response from supabase. code = http status, body = raw response text (often json
// with an error message, left raw here — caller decides how much of it to show/log).
class SupabaseException(val code: Int, val body: String) : Exception("supabase http $code: $body")

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // epoch seconds
    val userId: String,
)

class SupabaseClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        parseAuth(request("POST", "$baseUrl/auth/v1/token?grant_type=password", body.toString()))
    }

    suspend fun refresh(refreshToken: String): AuthResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        parseAuth(request("POST", "$baseUrl/auth/v1/token?grant_type=refresh_token", body.toString()))
    }

    suspend fun changePassword(accessToken: String, newPassword: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("password", newPassword) }
        request("PUT", "$baseUrl/auth/v1/user", body.toString(), authToken = accessToken)
        Unit
    }

    // triggers gotrue's reset email; anon key auth (no session yet). body ignored on 2xx.
    suspend fun recover(email: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("email", email) }
        request("POST", "$baseUrl/auth/v1/recover", body.toString())
        Unit
    }

    suspend fun select(table: String, params: Map<String, String>, accessToken: String): JsonArray =
        withContext(Dispatchers.IO) {
            val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val full = if (query.isEmpty()) "$baseUrl/rest/v1/$table" else "$baseUrl/rest/v1/$table?$query"
            json.parseToJsonElement(request("GET", full, body = null, authToken = accessToken)).jsonArray
        }

    suspend fun upsert(table: String, rows: JsonArray, accessToken: String) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "$baseUrl/rest/v1/$table",
            rows.toString(),
            authToken = accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        Unit
    }

    // blocking http call, always run from withContext(Dispatchers.IO) above.
    // returns body text on 2xx; throws SupabaseException otherwise, IOException on network failure.
    private fun request(
        method: String,
        urlStr: String,
        body: String?,
        authToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val conn = URL(urlStr).openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Authorization", "Bearer ${authToken ?: apiKey}")
            conn.setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw SupabaseException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parseAuth(res: String): AuthResult {
        val obj = json.parseToJsonElement(res).jsonObject
        val expiresAt = obj["expires_at"]?.jsonPrimitive?.longOrNull
            ?: (System.currentTimeMillis() / 1000 + (obj["expires_in"]?.jsonPrimitive?.long ?: 3600L))
        return AuthResult(
            accessToken = obj.getValue("access_token").jsonPrimitive.content,
            refreshToken = obj.getValue("refresh_token").jsonPrimitive.content,
            expiresAt = expiresAt,
            userId = obj.getValue("user").jsonObject.getValue("id").jsonPrimitive.content,
        )
    }
}
