// reads `exp` (epoch seconds) from a JWT payload without verifying it; TMS is the verifier.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

fun jwtExpiry(token: String): Long? = runCatching {
    val payload = token.split('.')[1]
    val json = String(Base64.getUrlDecoder().decode(payload))
    Json.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.longOrNull
}.getOrNull()
