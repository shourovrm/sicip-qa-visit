// lookup over a prefill result for the editor: which card field has a "Use" suggestion, which
// one already equals TMS (the "TMS" marker). computed from data, never stored in the report.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsSnapshot

class TmsHints(result: TmsPrefillResult) {
    val suggestions: List<TmsSuggestion> = result.suggestions
    private val suggestionByField = result.suggestions.associateBy { fieldId(it) }
    private val matchedFields = result.matched.map { fieldId(it) }.toSet()

    fun suggestionFor(cardsKey: String, cardIndex: Int, fieldKey: String): TmsSuggestion? =
        suggestionByField["$cardsKey|$cardIndex|$fieldKey"]

    fun matchesTms(cardsKey: String, cardIndex: Int, fieldKey: String): Boolean =
        "$cardsKey|$cardIndex|$fieldKey" in matchedFields

    companion object {
        val NONE = TmsHints(TmsPrefillResult(ReportData.EMPTY, emptyList()))

        // no snapshot -> no hints. the prefilled data of the result is dropped on purpose.
        fun of(snapshot: TmsSnapshot?, data: ReportData): TmsHints =
            if (snapshot == null) NONE else TmsHints(prefillFromTms(snapshot, data))
    }
}

private fun fieldId(entry: TmsSuggestion) = "${entry.cardsKey}|${entry.cardIndex}|${entry.fieldKey}"
