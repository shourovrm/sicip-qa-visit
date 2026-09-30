// typed TMS failures + the pure response classifier. one job: turn (http code, body) into
// the unwrapped `data` element or exactly one of these exceptions.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed class TmsException(message: String) : Exception(message)

// network down / timeout / 5xx: TMS outage, not an API change. offline = no route at all.
class TmsTransientException(message: String, val offline: Boolean = false) : TmsException(message)

// 401: token rejected, caller re-logins once.
class TmsUnauthorizedException : TmsException("TMS rejected the token")

// TMS answered status:"error" (wrong password, reset notice, ...). message is TMS's own text.
class TmsMessageException(message: String) : TmsException(message)

// no stored credentials / credentials known-bad: nothing to send.
class TmsLoggedOutException(message: String = "Not signed in to TMS") : TmsException(message)

// API changed under us: kind = http_404 | http_405 | not_json | shape. goes to tms_errors.
class TmsApiChangeException(
    val kind: String,
    val detail: String,
    var endpoint: String = "",
) : TmsException("TMS API change ($kind): $detail") {
    var reported = false
}

fun shapeError(endpoint: String, detail: String) = TmsApiChangeException("shape", detail, endpoint)

// expects a json array at `what` (e.g. "batch_summary"), else a shape error.
fun JsonElement?.requireArray(endpoint: String, what: String): JsonArray =
    this as? JsonArray ?: throw shapeError(endpoint, "$what: expected array")

// returns the envelope's `data`; endpoint is the path without query, stamped on API-change errors.
fun unwrapTmsResponse(code: Int, body: String, endpoint: String): JsonElement {
    if (code >= 500) throw TmsTransientException("TMS server error $code")
    if (code == 404 || code == 405) throw TmsApiChangeException("http_$code", "HTTP $code", endpoint)
    val envelope = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
    if (code == 401) throw TmsUnauthorizedException()
    envelope ?: throw TmsApiChangeException("not_json", "response is not a JSON object", endpoint)
    val status = (envelope["status"] as? JsonPrimitive)?.contentOrNull
        ?: throw shapeError(endpoint, "status: missing")
    if (status == "error") {
        val message = (envelope["message"] as? JsonPrimitive)?.contentOrNull
        throw TmsMessageException(message?.takeIf { it.isNotBlank() } ?: "TMS returned an error")
    }
    if (status != "success") throw shapeError(endpoint, "status: unexpected value")
    return envelope["data"] ?: throw shapeError(endpoint, "data: missing")
}

fun endpointOf(pathWithQuery: String): String = pathWithQuery.substringBefore('?')
