// TMS sends counts as numbers or strings ("24"), sometimes null: parse either, default to 0/"".
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonElement?.lenientText(): String = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: ""

fun JsonElement?.lenientLong(): Long {
    val text = lenientText()
    return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong() ?: 0L
}

fun JsonElement?.lenientInt(): Int = lenientLong().toInt()

fun JsonElement?.objectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
