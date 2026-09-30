// raw TMS transport: HttpURLConnection like SupabaseClient. no parsing, no auth policy.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

const val TMS_BASE_URL = "https://bee.sicip.gov.bd/api/"
private const val TIMEOUT_MS = 20_000

data class TmsRawResponse(val code: Int, val body: String)

// seam so auth/api policy can be unit-tested with a fake.
interface TmsTransport {
    // network failures surface as TmsTransientException; http status is never an exception here.
    suspend fun get(path: String, bearerToken: String): TmsRawResponse
    suspend fun postForm(path: String, fields: Map<String, String>): TmsRawResponse
}

class TmsHttp : TmsTransport {
    override suspend fun get(path: String, bearerToken: String) =
        call("GET", path, form = null, bearerToken = bearerToken)

    override suspend fun postForm(path: String, fields: Map<String, String>) =
        call("POST", path, form = fields, bearerToken = null)

    private suspend fun call(method: String, path: String, form: Map<String, String>?, bearerToken: String?) =
        withContext(Dispatchers.IO) {
            val conn = URL(TMS_BASE_URL + path).openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/json")
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (form != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    val encoded = form.entries.joinToString("&") { (k, v) ->
                        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                    }
                    conn.outputStream.use { it.write(encoded.toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                TmsRawResponse(code, text)
            } catch (e: IOException) {
                val offline = e is UnknownHostException || e is ConnectException || e is NoRouteToHostException
                throw TmsTransientException(e.message ?: "network error", offline)
            } finally {
                conn.disconnect()
            }
        }
}
