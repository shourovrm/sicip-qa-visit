// filled fields whose value differs from TMS (web tmsprefill.js "differences"): listed in the TMS
// status row with "Use" per field and "Use all". nothing is overwritten until the officer asks.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val ADDRESS = "address"

// the report's typed address vs the linked TMS institute's; null when one is blank or they agree
fun addressDifference(data: ReportData): TmsSuggestion? {
    val tmsAddress = data.tmsLink()?.address?.trim().orEmpty()
    val current = data.field(ADDRESS).trim()
    if (tmsAddress.isEmpty() || current.isEmpty() || current.equals(tmsAddress, ignoreCase = true)) return null
    return TmsSuggestion(cardsKey = "", cardIndex = -1, cardId = "", fieldKey = ADDRESS, tmsValue = tmsAddress)
}

// "Use": the TMS value into its field; a card is found by _id, so a reorder never hits another card
fun withTmsValue(data: ReportData, difference: TmsSuggestion): ReportData {
    if (difference.cardsKey.isEmpty()) return data.withField(difference.fieldKey, difference.tmsValue)
    val cards = data.cards(difference.cardsKey).map { card ->
        if ((card["_id"] as? JsonPrimitive)?.content != difference.cardId) return@map card
        JsonObject(card + (difference.fieldKey to JsonPrimitive(difference.tmsValue)))
    }
    return data.withCardsReplaced(difference.cardsKey, cards)
}

fun withAllTmsValues(data: ReportData, differences: List<TmsSuggestion>): ReportData =
    differences.fold(data) { current, difference -> withTmsValue(current, difference) }

// the field's label from the template ("Enrolled, total"), the key when the template lacks it
fun tmsDifferenceLabel(template: ReportTemplate, difference: TmsSuggestion): String {
    val fields = template.sections.flatMap { it.blocks }.flatMap { block ->
        when (block) {
            is ReportBlock.Cards -> if (block.key == difference.cardsKey) block.fields else emptyList()
            is ReportBlock.Fields -> if (difference.cardsKey.isEmpty()) block.fields else emptyList()
            else -> emptyList()
        }
    }
    return fields.firstOrNull { it.key == difference.fieldKey }?.label ?: difference.fieldKey
}

// which card it is: "Plumbing · 4"; "" for a top-level field
fun tmsDifferenceContext(data: ReportData, difference: TmsSuggestion): String {
    if (difference.cardsKey.isEmpty()) return ""
    val card = data.cards(difference.cardsKey).firstOrNull { (it["_id"] as? JsonPrimitive)?.content == difference.cardId } ?: return ""
    return listOf("course", "batch").map { (card[it] as? JsonPrimitive)?.content?.trim().orEmpty() }.filter { it.isNotEmpty() }.joinToString(" · ")
}
