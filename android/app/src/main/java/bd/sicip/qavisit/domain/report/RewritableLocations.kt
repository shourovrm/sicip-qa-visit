// locates every place in an OPEN report's data that the "Improve wording" button (ui/reports/
// ImproveWording.kt) can apply to -- a Field.rewrite=true field, whether it lives in a plain
// `fields` block or inside a `cards` block (including linked ones and section I's `interviews`
// tabs), plus every checklist item's remarks slot (that one is unconditional, not gated by any
// template flag -- see ReportBlocks.kt's ChecklistRemarksField comment). Pure, template+data in,
// no Android/Compose dependency, so ui/reports/ReportReview.kt's per-text list is unit-testable
// via RewritableLocationsTest without a real report screen.
//
// UI entry point: collectRewritableLocations(template, data) once per render (cheap walk, same
// pattern as ReportProgress.computeProgress), then per location: currentText(data) to display
// the text, ImproveWordingButton(text = currentText, label = location.label, onApply = { v ->
// editor.editNow(location.withText(data, v)) }). A card location's index is only ever used
// within the SAME render's data -- this list is never cached across edits, so a stale index
// after a card add/remove can't happen.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed class RewritableLocation {
    abstract val sectionLetter: String
    abstract val label: String

    data class TopField(override val sectionLetter: String, override val label: String, val fieldKey: String) : RewritableLocation()

    data class CardField(
        override val sectionLetter: String,
        override val label: String,
        val cardsKey: String,
        val cardIndex: Int,
        val fieldKey: String,
    ) : RewritableLocation()

    data class ChecklistRemarks(override val sectionLetter: String, override val label: String, val itemId: String) : RewritableLocation()
}

fun RewritableLocation.currentText(data: ReportData): String = when (this) {
    is RewritableLocation.TopField -> data.field(fieldKey)
    is RewritableLocation.CardField -> data.cardField(cardsKey, cardIndex, fieldKey)
    is RewritableLocation.ChecklistRemarks -> data.checkRemarks(itemId)
}

fun RewritableLocation.withText(data: ReportData, newText: String): ReportData = when (this) {
    is RewritableLocation.TopField -> data.withField(fieldKey, newText)
    is RewritableLocation.CardField -> data.withCardField(cardsKey, cardIndex, fieldKey, newText)
    is RewritableLocation.ChecklistRemarks -> data.withCheck(itemId, remarks = newText)
}

// a card's own display name for the label prefix (e.g. "Welding (SMAW) · Batch 07" for a linked
// course card, or a plain card's titleField value) -- "" when there is nothing better than the
// generic "<itemLabel> <n>" fallback, or when the only candidate name IS the field being labeled
// (e.g. section K's "issue" field is itself unresolved's titleField -- using its own text as a
// name prefix would just repeat it back).
private fun cardDisplayName(block: ReportBlock.Cards, card: JsonObject, excludeFieldKey: String): String {
    val link = block.linkFrom
    if (link != null && excludeFieldKey != "course") {
        val course = card["course"]?.jsonPrimitive?.contentOrNull
        val batch = card["batch"]?.jsonPrimitive?.contentOrNull
        if (!course.isNullOrBlank()) return if (batch.isNullOrBlank()) course else "$course · Batch $batch"
    }
    if (block.titleField != excludeFieldKey) {
        val title = card[block.titleField]?.jsonPrimitive?.contentOrNull
        if (!title.isNullOrBlank()) return title
    }
    return ""
}

fun collectRewritableLocations(template: ReportTemplate, data: ReportData): List<RewritableLocation> {
    val out = mutableListOf<RewritableLocation>()
    template.sections.forEach { section ->
        section.blocks.forEach { block ->
            when (block) {
                is ReportBlock.Fields -> {
                    block.fields.forEach { field ->
                        if (field.rewrite) out += RewritableLocation.TopField(section.letter, field.label, field.key)
                    }
                }

                is ReportBlock.Cards -> {
                    data.cards(block.key).forEachIndexed { index, card ->
                        block.fields.forEach { field ->
                            if (!field.rewrite) return@forEach
                            val name = cardDisplayName(block, card, field.key)
                            val label = if (name.isNotBlank()) "$name · ${field.label}" else "${block.itemLabel} ${index + 1} · ${field.label}"
                            out += RewritableLocation.CardField(section.letter, label, block.key, index, field.key)
                        }
                    }
                }

                is ReportBlock.Checklist -> {
                    // unconditional (spec): every checklist item's remarks box gets the button,
                    // no per-item opt-out -- numbered "Q<n>" by its 1-based position in this
                    // block, matching the number ChecklistBlockView shows next to the question.
                    block.items.forEachIndexed { i, item ->
                        out += RewritableLocation.ChecklistRemarks(section.letter, "Q${i + 1} remarks", item.id)
                    }
                }

                is ReportBlock.Flags -> Unit

                // criteria items get their own dedicated "AI remarks" flow (spec §6,
                // ui/reports/CriteriaBlocks.kt), never the generic per-field ImproveWordingButton
                // this list drives -- nothing to collect here.
                is ReportBlock.Criteria -> Unit
            }
        }
    }
    return out
}
