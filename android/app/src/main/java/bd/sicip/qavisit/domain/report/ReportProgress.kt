// progress rules from the spec's "Progress rules" section -- implemented identically on
// android and web; shared/report-templates/fixtures/progress-1.json proves the two agree.
//
// blank = null or trimmed "". Per section: checklist items always count toward total/answered
// (answered = non-blank answer, flagged if any item's answer == "no"); a `fields` block counts
// only fields marked required==true or kind=="choice" (a choice field flags the section if its
// chosen option's tone == "no"); a `cards` block counts each kind=="choice" field once per card
// PRESENT in the data (a template with zero cards filled in contributes 0, not
// fields.size * 0 -- see the graduates/followup sections in the fixture); a `flags` block never
// contributes to total/answered, only to flagged (if any of its items is ticked). A section is
// done when total > 0 && answered == total (a section with total == 0, e.g. an all-optional
// fields block, is never "done" -- it's simply not counted).
//
// API for agent A2 (UI): computeProgress(template, data) is the one entry point -- call it on
// every render (it's a small pure walk, cheap enough not to cache). ReportProgress.sections is
// keyed by section.key in template order (iterate template.sections and look up by key, don't
// iterate the map directly, if you need template order guaranteed). sectionsCounted/sectionsDone
// drive the report hub's "x of N sections" line. answerCounts is keyed by template answer id
// (yes/no/partial/na for surprise-v1) and only counts checklist items, not card choice fields --
// it's meant for a review-screen breakdown, not a raw total. unansweredChecklistItemIds is in
// template section/block order; firstUnanswered (its head, or null if none) is what a "resume
// where you left off" jump would target. flagsTicked is the ticked subset of every flags
// block's items, same template order. cardCompareMismatch is exposed separately so a card's UI
// badge and this file's section-level `flagged` never compute the compare rule two different
// ways.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class SectionProgress(val answered: Int, val total: Int, val done: Boolean, val flagged: Boolean)

data class ReportProgress(
    val sections: Map<String, SectionProgress>,
    val sectionsCounted: Int,
    val sectionsDone: Int,
    val answerCounts: Map<String, Int>,
    val unansweredChecklistItemIds: List<String>,
    val flagsTicked: List<String>,
) {
    val unansweredCount: Int get() = unansweredChecklistItemIds.size
    val firstUnanswered: String? get() = unansweredChecklistItemIds.firstOrNull()
}

private fun isBlank(value: String?): Boolean = value == null || value.trim().isEmpty()

// the spec's compare rule: parse the named fields of one card as ints, ignoring blanks; two or
// more present and not all equal is a mismatch. shared by ReportProgress's flagged calculation
// and by the UI's per-card compare-warning badge (compare.message), so they can't drift apart.
fun cardCompareMismatch(compare: CardsCompare, card: JsonObject): Boolean {
    val presentInts = compare.fields.mapNotNull { key ->
        card[key]?.jsonPrimitive?.contentOrNull?.takeIf { !isBlank(it) }?.toIntOrNull()
    }
    return presentInts.size >= 2 && presentInts.distinct().size > 1
}

fun computeProgress(template: ReportTemplate, data: ReportData): ReportProgress {
    val sections = LinkedHashMap<String, SectionProgress>()
    val answerCounts = mutableMapOf<String, Int>()
    val unanswered = mutableListOf<String>()
    val flagsTicked = mutableListOf<String>()
    val tickedFlagIds = data.flags()

    template.sections.forEach { section ->
        var total = 0
        var answered = 0
        var flagged = false

        section.blocks.forEach { block ->
            when (block) {
                is ReportBlock.Checklist -> {
                    block.items.forEach { item ->
                        total++
                        val answer = data.checkAnswer(item.id)
                        if (isBlank(answer)) {
                            unanswered.add(item.id)
                        } else {
                            answered++
                            answerCounts[answer] = (answerCounts[answer] ?: 0) + 1
                        }
                        if (answer == "no") flagged = true
                    }
                }

                is ReportBlock.Fields -> {
                    block.fields.forEach { field ->
                        if (field.required || field.kind == "choice") {
                            total++
                            val value = data.field(field.key)
                            if (!isBlank(value)) answered++
                            if (field.kind == "choice" && !isBlank(value) && field.toneFor(value) == "no") {
                                flagged = true
                            }
                        }
                    }
                }

                is ReportBlock.Cards -> {
                    val choiceFields = block.fields.filter { it.kind == "choice" }
                    data.cards(block.key).forEach { card ->
                        choiceFields.forEach { field ->
                            total++
                            val value = card[field.key]?.jsonPrimitive?.contentOrNull
                            if (!isBlank(value)) {
                                answered++
                                if (field.toneFor(value!!) == "no") flagged = true
                            }
                        }
                        val compare = block.compare
                        if (compare != null && cardCompareMismatch(compare, card)) flagged = true
                    }
                }

                is ReportBlock.Flags -> {
                    if (block.items.any { it.id in tickedFlagIds }) flagged = true
                    block.items.forEach { item -> if (item.id in tickedFlagIds) flagsTicked.add(item.id) }
                }
            }
        }

        sections[section.key] = SectionProgress(answered = answered, total = total, done = total > 0 && answered == total, flagged = flagged)
    }

    return ReportProgress(
        sections = sections,
        sectionsCounted = sections.values.count { it.total > 0 },
        sectionsDone = sections.values.count { it.total > 0 && it.done },
        answerCounts = answerCounts,
        unansweredChecklistItemIds = unanswered,
        flagsTicked = flagsTicked,
    )
}
