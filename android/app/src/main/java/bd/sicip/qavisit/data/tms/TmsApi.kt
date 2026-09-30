// GET against the TMS API with bearer auth. unwraps `data`, one re-login+retry on 401,
// reports API-change errors to the admin. no endpoint logic here.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

class TmsApi(
    private val auth: TmsAuth,
    private val transport: TmsTransport = TmsHttp(),
    private val reporter: TmsErrorReporter? = null,
) {
    suspend fun get(path: String): JsonElement {
        val endpoint = endpointOf(path)
        try {
            var response = transport.get(path, auth.bearer())
            if (response.code == 401) {
                response = transport.get(path, auth.reloginAfterUnauthorized())
            }
            return unwrapTmsResponse(response.code, response.body, endpoint)
        } catch (e: TmsApiChangeException) {
            report(e)
            throw e
        }
    }

    // `data` must be an array; anything else is a shape change.
    suspend fun getList(path: String): JsonArray {
        val data = get(path)
        try {
            return data.requireArray(endpointOf(path), "data")
        } catch (e: TmsApiChangeException) {
            report(e)
            throw e
        }
    }

    // once per error object: get() and the callers' nested shape checks may both see it.
    suspend fun report(error: TmsApiChangeException) {
        if (error.reported) return
        error.reported = true
        reporter?.report(error)
    }
}
