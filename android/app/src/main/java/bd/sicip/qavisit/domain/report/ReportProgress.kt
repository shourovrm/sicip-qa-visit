// progress rules -- ported 1:1 from shared/report-templates/fixtures/reference.py (the source
// of truth; regenerate progress-1.json by running it), implemented identically on android and
// web; the fixture's `expected` proves the two agree.
//
// blank = null or trimmed "". `counts(field)` = field.required || field.kind == "choice", used
// uniformly for BOTH a `fields` block's fields AND a `cards` block's per-card fields (a required
// but non-choice card field, e.g. attendance's present_total/trainers_present, counts just like
// a required top-level field does). Per section:
// - checklist: total += items; answered += non-blank (derived, see ReportLinks.kt's normalize)
//   answer; flagged if that answer == "no" OR (perCourse item, 2+ courses today) any course's
//   own answer == "no", even when the derived overall answer is "partial".
// - fields: each field where counts(field) → total+1, answered if non-blank; flagged if a
//   choice field's chosen option's tone == "no".
// - cards (countsAsFlags block, e.g. section L's "other_flags"): contributes 0 to totals; every
//   card whose titleField is non-blank is a free-text flag (flags the section, and its text is
//   collected into ReportProgress.customFlags) -- see reference.py's `counts()` early-continue.
// - cards (ordinary block): for every card PRESENT in the data, each field where counts(field)
//   → total+1, answered if non-blank, flagged if a choice field's tone is "no"; if block.compare:
//   parse non-blank compare fields as ints, ≥2 present and not all equal → flagged (also see
//   cardCompareMismatch, shared with the UI's per-card compare-warning badge).
// - flags: contributes 0 to totals; flagged if any of its items ticked.
// - done = total > 0 && answered == total.
// Report level: sectionsCounted/sectionsDone only count sections where !section.optional (an
// optional section, e.g. K, is still computed and shown in `sections`, just excluded from the
// hub's "x of N" rollup); answerCounts per template answer id over checklist items only;
// unanswered checklist item ids in template order (unaffected by `optional`); flagsTicked (the
// flags block's own ticked ids); customFlags (countsAsFlags cards' free text, template order).
//
// UI entry point: computeProgress(template, data) is the one entry point -- call it on
// every render (it's a small pure walk, cheap enough not to cache). ReportProgress.sections is
// keyed by section.key in template order (iterate template.sections and look up by key, don't
// iterate the map directly, if you need template order guaranteed).
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
    val customFlags: List<String>,
    val sectionsWithContent: List<String> = emptyList(),
) {
    val unansweredCount: Int get() = unansweredChecklistItemIds.size
    val firstUnanswered: String? get() = unansweredChecklistItemIds.firstOrNull()
}

private fun isBlank(value: String?): Boolean = value == null || value.trim().isEmpty()

// reference.py's counts(field): a field counts toward total/answered if it's required OR a
// choice field -- applies identically to a top-level `fields` block and to a `cards` block's
// per-card fields, so a required-but-plain card field like attendance's present_total/
// trainers_present counts just like a required top-level field does.
private fun counts(field: Field): Boolean = field.required || field.kind == "choice"

// one card's own (answered, total) using the same counts() rule computeProgress uses -- shared
// by ReportProgress's cards branch and by the UI's per-course/per-tab "x of y answered" badges
// (e.g. section I's interview tab strip) so they can't drift apart.
fun cardCountedProgress(block: ReportBlock.Cards, card: JsonObject): Pair<Int, Int> {
    var answered = 0
    var total = 0
    block.fields.forEach { field ->
        if (!counts(field)) return@forEach
        total++
        if (!isBlank(card[field.key]?.jsonPrimitive?.contentOrNull)) answered++
    }
    return answered to total
}

// the compare rule: parse the named fields of one card as ints, ignoring blanks; two or
// more present and not all equal is a mismatch. shared by ReportProgress's flagged calculation
// and by the UI's per-card compare-warning badge (compare.message), so they can't drift apart.
fun cardCompareMismatch(compare: CardsCompare, card: JsonObject): Boolean {
    val presentInts = compare.fields.mapNotNull { key ->
        card[key]?.jsonPrimitive?.contentOrNull?.takeIf { !isBlank(it) }?.toIntOrNull()
    }
    return presentInts.size >= 2 && presentInts.distinct().size > 1
}

// one section's {answered,total,done,flagged} plus any free-text customFlags it contributed
// (reference.py's section_progress returns the same pair).
private fun sectionProgress(
    section: ReportSection,
    data: ReportData,
    todaysCourseIds: List<String>,
    answerCounts: MutableMap<String, Int>,
    unanswered: MutableList<String>,
): Pair<SectionProgress, List<String>> {
    var total = 0
    var answered = 0
    var flagged = false
    val customFlags = mutableListOf<String>()

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
                    // reference.py: a perCourse item (once 2+ courses are in play) flags the
                    // section on ANY course answering "no", even when the overall DERIVED
                    // answer is "partial" rather than "no" itself (e.g. one course "yes", one
                    // "no" -> derived "partial", but still a real non-compliance to flag).
                    val perCourseValues = if (item.perCourse && todaysCourseIds.size >= 2) {
                        data.checkCourses(item.id).values
                    } else {
                        emptyList()
                    }
                    if (answer == "no" || perCourseValues.any { it == "no" }) flagged = true
                }
            }

            is ReportBlock.Fields -> {
                block.fields.forEach { field ->
                    if (!counts(field)) return@forEach
                    total++
                    val value = data.field(field.key)
                    if (!isBlank(value)) answered++
                    if (field.kind == "choice" && !isBlank(value) && field.toneFor(value) == "no") {
                        flagged = true
                    }
                }
            }

            is ReportBlock.Cards -> {
                val entries = data.cards(block.key)
                if (block.countsAsFlags) {
                    entries.forEach { card ->
                        val text = card[block.titleField]?.jsonPrimitive?.contentOrNull
                        if (!isBlank(text)) {
                            customFlags.add(text!!.trim())
                            flagged = true
                        }
                    }
                    return@forEach
                }
                entries.forEach { card ->
                    block.fields.forEach { field ->
                        if (!counts(field)) return@forEach
                        total++
                        val value = card[field.key]?.jsonPrimitive?.contentOrNull
                        if (!isBlank(value)) {
                            answered++
                            if (field.kind == "choice" && field.toneFor(value!!) == "no") flagged = true
                        }
                    }
                    val compare = block.compare
                    if (compare != null && cardCompareMismatch(compare, card)) flagged = true
                }
            }

            is ReportBlock.Flags -> {
                val tickedFlagIds = data.flags()
                if (block.items.any { it.id in tickedFlagIds }) flagged = true
            }
        }
    }

    return SectionProgress(answered = answered, total = total, done = total > 0 && answered == total, flagged = flagged) to customFlags
}

fun computeProgress(template: ReportTemplate, data: ReportData): ReportProgress {
    val sections = LinkedHashMap<String, SectionProgress>()
    // reference.py zero-fills every template answer id up front (not just the ones actually
    // used), so an answer id with zero occurrences still shows up as 0, not absent.
    val answerCounts = template.answers.associate { it.id to 0 }.toMutableMap()
    val unanswered = mutableListOf<String>()
    val customFlags = mutableListOf<String>()
    val todaysCourseIds = courseIds(data)

    template.sections.forEach { section ->
        val (progress, found) = sectionProgress(section, data, todaysCourseIds, answerCounts, unanswered)
        sections[section.key] = progress
        customFlags += found
    }

    val counted = template.sections.filter { !it.optional && (sections[it.key]?.total ?: 0) > 0 }

    return ReportProgress(
        sections = sections,
        sectionsCounted = counted.size,
        sectionsDone = counted.count { sections.getValue(it.key).done },
        answerCounts = answerCounts,
        unansweredChecklistItemIds = unanswered,
        // reference.py: flagsTicked is data.flags verbatim (order and all), not re-derived
        // from the template's flag items.
        flagsTicked = data.flagsList(),
        customFlags = customFlags,
        sectionsWithContent = template.sections.filter { sectionHasContent(it, data) }.map { it.key },
    )
}

// optional sections print only when the officer filled something in them
fun sectionHasContent(section: ReportSection, data: ReportData): Boolean = section.blocks.any { block ->
    when (block) {
        is ReportBlock.Fields -> block.fields.any { !isBlank(data.field(it.key)) }
        is ReportBlock.Checklist -> block.items.any {
            !isBlank(data.checkAnswer(it.id)) || !isBlank(data.checkRemarks(it.id)) || data.checkCourses(it.id).isNotEmpty()
        }
        is ReportBlock.Cards -> data.cards(block.key).any { card ->
            card.any { (key, value) -> !key.startsWith("_") && !isBlank(value.toString().trim('"')) }
        }
        is ReportBlock.Flags -> block.items.any { it.id in data.flags() }
    }
}
