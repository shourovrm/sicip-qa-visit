// syncLinks: keeps a `cards` block's rows derived from another cards block (spec's `linkFrom`,
// e.g. section C's attendance cards follow section A's courses) instead of the officer adding/
// removing them by hand. Ported 1:1 from shared/report-templates/fixtures/reference.py's
// sync_links -- see that file for the reference behaviour the fixture proves this matches.
//
// Rule: for every source card (in the linked block, in that block's own order) that has ANY of
// the linked fields filled in, keep exactly one target card. A target's `_id` is derived from
// its source's `_id` (`"<targetBlockKey>:<sourceId>"`) so every platform computes the same id
// independently. Re-running syncLinks preserves a target's OTHER answers (matched by which
// source it's linked to, via `_link`) and drops any target whose source card is gone or went
// blank. Idempotent: calling it twice in a row is a no-op the second time.
//
// API for agent A2 (UI): call syncLinks(template, data) after every edit and once when a report
// is opened (ui/reports/ReportEditing.kt's ReportEditor does both). Linked cards render read-
// only for their linked fields and have no Add/Remove button of their own -- see
// ReportBlocks.kt's CardsBlockView, which checks Field.Cards.linkFrom != null.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun blank(value: String?): Boolean = value == null || value.trim().isEmpty()

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun syncLinks(template: ReportTemplate, data: ReportData): ReportData {
    var result = data
    template.sections.forEach { section ->
        section.blocks.forEach { block ->
            if (block !is ReportBlock.Cards) return@forEach
            val link = block.linkFrom ?: return@forEach

            val sources = result.cards(link.cards).filter { source ->
                link.fields.any { key -> !blank(source.stringOrNull(key)) }
            }
            // keyed by the OLD target's _link (which source it used to follow) so a target
            // that's still linked to the same source keeps every other answer it already has.
            val existingByLink = result.cards(block.key).associateBy { it.stringOrNull("_link") }

            val synced = sources.map { source ->
                val sourceId = source.stringOrNull("_id") ?: ""
                val existing = existingByLink[sourceId] ?: JsonObject(emptyMap())
                val merged = existing.toMutableMap()
                merged["_id"] = JsonPrimitive("${block.key}:$sourceId")
                merged["_link"] = JsonPrimitive(sourceId)
                link.fields.forEach { key -> merged[key] = JsonPrimitive(source.stringOrNull(key) ?: "") }
                JsonObject(merged)
            }
            result = result.withCardsReplaced(block.key, synced)
        }
    }
    return result
}
