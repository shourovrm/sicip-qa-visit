// QA conclusions drafts (s13 strengths/weaknesses, s14 findings, s16 plan, s15 recs) -- ported
// 1:1 from shared/report-templates/fixtures/reference.py's "QA conclusions drafts" section,
// fixture-tested against fixtures/drafts-qa-1.json (DraftsFixtureTest). Pure, no android deps.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// one component's raw material: seen -> strengths, gaps + feedback No's -> weaknesses
data class ComponentNotes(
    val seen: List<String>,
    val gaps: List<String>,
    val notes: List<String>,
    val feedback: List<String>,
)

data class StrengthsDraft(val strengths: List<String>, val weaknesses: List<String>)

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

// feedback cards (s11/s12) -> "N of M trainees said No: <question>." for questions tied to this
// component. M = cards that answered the question at all.
fun feedbackLines(template: ReportTemplate, data: ReportData, sourceKey: String): List<String> {
    val lines = mutableListOf<String>()
    template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().forEach { block ->
        val cards = data.cards(block.key)
        val noun = block.itemLabel.lowercase()
        block.fields.filter { it.component == sourceKey }.forEach { field ->
            val answers = cards.map { it.text(field.key) }.filter { it.isNotBlank() }
            val noCount = answers.count { it == "no" }
            if (noCount > 0) {
                val plural = if (answers.size == 1) "" else "s"
                lines += "$noCount of ${answers.size} $noun$plural said No: ${ensureStop(field.label)}"
            }
        }
    }
    return lines
}

// one component (criteria section) sorted into seen / not seen / other notes / feedback
fun componentNotes(template: ReportTemplate, data: ReportData, sourceKey: String): ComponentNotes {
    val seen = mutableListOf<String>()
    val gaps = mutableListOf<String>()
    val other = mutableListOf<String>()
    val section = template.sections.first { it.key == sourceKey }
    section.blocks.filterIsInstance<ReportBlock.Criteria>().forEach { block ->
        block.items.filterNot { it.heading }.forEach { item ->
            item.options.forEach { option ->
                val value = data.criteriaOptValue(item.id, option.id)
                if (value.isBlank()) {
                    // unmarked option: only its typed remark counts, as "name: remark"
                    val remark = ensureStop(data.criteriaOptRemark(item.id, option.id).trim())
                    if (remark.isNotEmpty()) other += "${option.short ?: option.label}: $remark"
                    return@forEach
                }
                val text = optionText(item.id, option, data)
                if (text.isEmpty()) return@forEach
                when (value) {
                    "seen" -> seen += text
                    "not" -> gaps += text
                    else -> other += text
                }
            }
            val evidence = data.criteriaEvidence(item.id).trim()
            if (evidence.isNotEmpty()) other += "Evidence seen: " + ensureStop(evidence)
            val note = data.criteriaNote(item.id).trim()
            if (note.isNotEmpty()) other += ensureStop(note)
        }
    }
    return ComponentNotes(seen, gaps, other, feedbackLines(template, data, sourceKey))
}

fun hasNotes(notes: ComponentNotes): Boolean =
    notes.seen.isNotEmpty() || notes.gaps.isNotEmpty() || notes.notes.isNotEmpty() || notes.feedback.isNotEmpty()

// offline / AI-failed draft: seen -> strengths, gaps + feedback No's -> weaknesses
fun fallbackDraft(notes: ComponentNotes): StrengthsDraft = StrengthsDraft(notes.seen, notes.gaps + notes.feedback)

// the user message sent to the Worker's mode "strengths"
fun strengthsPromptText(notes: ComponentNotes): String {
    val groups = listOf(
        "Positive observations" to notes.seen,
        "Gaps observed" to notes.gaps,
        "Feedback from trainees and trainers" to notes.feedback,
        "Other officer notes" to notes.notes,
    )
    return groups.filter { it.second.isNotEmpty() }
        .joinToString("\n\n") { (heading, lines) -> heading + ":\n" + lines.joinToString("\n") { "- $it" } }
}

private val LIST_PREFIX = Regex("^\\s*(?:[-*\\u2022]+|\\d+[.)])\\s*")
private val NONE_LINE = Regex("^none\\b", RegexOption.IGNORE_CASE)
private val HEADING_LINE = Regex("^[#*\\s]*(strengths?|weakness(?:es)?)[\\s*:]*$", RegexOption.IGNORE_CASE)

// model text -> clean points: bullet/number prefix and ** stripped, blanks and "None..." dropped
fun cleanLines(text: String?): List<String> =
    (text ?: "").split("\n")
        .map { LIST_PREFIX.replaceFirst(it, "").replace("**", "").trim() }
        .filter { it.isNotEmpty() && !NONE_LINE.containsMatchIn(it) }

// "STRENGTHS:\n- a\nWEAKNESSES:\n- b" -> draft; null when no heading found
fun parseStrengthsAnswer(text: String?): StrengthsDraft? {
    val strengths = mutableListOf<String>()
    val weaknesses = mutableListOf<String>()
    var current: MutableList<String>? = null
    (text ?: "").split("\n").forEach { raw ->
        val match = HEADING_LINE.find(raw)
        if (match != null) {
            current = if (match.groupValues[1].lowercase().startsWith("strength")) strengths else weaknesses
            return@forEach
        }
        current?.addAll(cleanLines(raw))
    }
    if (current == null) return null
    return StrengthsDraft(strengths, weaknesses)
}

// every weakness line across the s13 pairs, component order
fun allWeaknesses(template: ReportTemplate, data: ReportData): List<String> =
    template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Fields>()
        .flatMap { it.pairs }
        .flatMap { cleanLines(data.field(it.weakness)) }

fun numberedText(lines: List<String>): String =
    lines.mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n")

private val NUMBERED_LINE = Regex("^\\s*\\**(\\d+)[.)]\\**\\s*(.+)$")

// "1. a\n2. b" -> [a, b]; null unless every number 1..count has a non-blank answer
fun parseNumbered(text: String?, count: Int): List<String>? {
    val found = mutableMapOf<Int, String>()
    (text ?: "").split("\n").forEach { raw ->
        val match = NUMBERED_LINE.find(raw) ?: return@forEach
        val number = match.groupValues[1].toIntOrNull() ?: return@forEach
        found.getOrPut(number) { match.groupValues[2].replace("**", "").trim() }
    }
    val answers = (1..count).map { found[it] ?: "" }
    return if (answers.all { it.isNotEmpty() }) answers else null
}

// one plan card per weakness; a card with the same weakness keeps its responsible/timeline
// (and its action when the AI gave none). actions=null -> AI failed.
fun planCards(
    weaknesses: List<String>,
    actions: List<String>?,
    existing: List<JsonObject>,
    newId: (Int) -> String,
): List<JsonObject> = weaknesses.mapIndexed { i, weakness ->
    val prev = existing.firstOrNull { it.text("weakness").trim() == weakness } ?: JsonObject(emptyMap())
    val action = if (!actions.isNullOrEmpty()) actions[i] else prev.text("action")
    buildJsonObject {
        put("_id", JsonPrimitive(prev.text("_id").ifEmpty { newId(i) }))
        put("weakness", JsonPrimitive(weakness))
        put("action", JsonPrimitive(action))
        put("responsible", JsonPrimitive(prev.text("responsible")))
        put("timeline", JsonPrimitive(prev.text("timeline")))
    }
}

// s15 prefill: one line per plan action
fun recommendationsFromPlan(cards: List<JsonObject>): String =
    cards.map { it.text("action").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
