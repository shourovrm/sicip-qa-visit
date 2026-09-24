// normalize(): the one step every client runs after each edit and on open (spec CHANGE SET 3).
// = syncLinks (linked cards blocks follow their source block) + syncPerCourse (perCourse
// checklist items derive their overall answer from a per-course breakdown). Ported 1:1 from
// shared/report-templates/fixtures/reference.py's normalize/sync_links/sync_per_course -- see
// that file for the reference behaviour shared/report-templates/fixtures/progress-1.json proves
// this matches (normalize(before_sync) == synced).
//
// syncLinks rule: for every source card (in the linked block, in that block's own order) that
// has ANY of the linked fields filled in, keep exactly one target card. A target's `_id` is
// derived from its source's `_id` (`"<targetBlockKey>:<sourceId>"`) so every platform computes
// the same id independently. Re-running it preserves a target's OTHER answers (matched by which
// source it's linked to, via `_link`) and drops any target whose source card is gone or went
// blank.
//
// syncPerCourse rule: with 2+ courses in section A carrying a non-blank name, every checklist
// item marked `perCourse` in the template keeps only the current courses' entries in
// checks[item].courses and derives checks[item].answer from them (blank until every course is
// answered; all-same -> that value; a mix of yes/no/partial -> "partial"; all "na" -> "na"; "na"
// entries are otherwise ignored when checking for unanimity). With 0-1 courses a perCourse item
// is untouched here -- ReportBlocks.kt just renders it as a normal single-answer row.
//
// Both steps are idempotent: calling normalize twice in a row is a no-op the second time.
//
// API for agent A2 (UI): call normalize(template, data) after every edit and once when a report
// is opened (ui/reports/ReportEditing.kt's ReportEditor does both) -- never call syncLinks or
// syncPerCourse directly from UI code, they're normalize's internal steps. Linked cards render
// read-only for their linked fields and have no Add/Remove button of their own -- see
// ReportBlocks.kt's CardsBlockView, which checks ReportBlock.Cards.linkFrom != null.
// courseIds(data) (today's non-blank courses from section A, in that block's order) is also
// what the UI uses to decide whether a perCourse item shows its per-course rows (2+) or the
// plain single-answer row (0-1) -- same threshold syncPerCourse itself uses.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun blank(value: String?): Boolean = value == null || value.trim().isEmpty()

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun normalize(template: ReportTemplate, data: ReportData): ReportData =
    syncPerCourse(template, syncLinks(template, data))

private fun syncLinks(template: ReportTemplate, data: ReportData): ReportData {
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

// today's course ids (section A's "courses" cards block, non-blank course name only), in that
// block's own order -- both syncPerCourse and ReportProgress.kt's flagged rule key off this.
fun courseIds(data: ReportData): List<String> =
    data.cards("courses").mapNotNull { card ->
        val id = card.stringOrNull("_id") ?: return@mapNotNull null
        if (blank(card.stringOrNull("course"))) null else id
    }

// reference.py's derive_answer: blank until every listed course has an answer; "na" entries are
// ignored when checking for unanimity (an all-"na" set of REAL answers still counts as
// unanimous); a genuine mix of differing non-na answers is "partial".
private fun deriveAnswer(values: List<String>): String {
    if (values.any { blank(it) }) return ""
    val real = values.filter { it != "na" }
    if (real.isEmpty()) return "na"
    return if (real.all { it == real.first() }) real.first() else "partial"
}

private fun syncPerCourse(template: ReportTemplate, data: ReportData): ReportData {
    val ids = courseIds(data)
    if (ids.size < 2) return data
    var result = data
    template.sections.forEach { section ->
        section.blocks.forEach { block ->
            if (block !is ReportBlock.Checklist) return@forEach
            block.items.forEach { item ->
                if (!item.perCourse) return@forEach
                var existingCourses = result.checkCourses(item.id)
                // answered before a 2nd course existed: that answer belonged to the first
                // course, so it moves there instead of vanishing (new courses start blank)
                val single = result.checkAnswer(item.id)
                if (existingCourses.isEmpty() && single.isNotBlank()) existingCourses = mapOf(ids.first() to single)
                // keep only today's course ids, dropping any course that's no longer present
                // (removed in section A, or never had a per-course answer to begin with).
                val keptCourses = ids.filter { it in existingCourses }.associateWith { existingCourses.getValue(it) }
                val derived = deriveAnswer(ids.map { keptCourses[it] ?: "" })
                result = result.withCheckCoursesReplaced(item.id, keptCourses, derived)
            }
        }
    }
    return result
}
