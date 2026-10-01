// surprise v2 templated remarks -- mirrored 1:1 by web/src/lib/sectionremarks.js, both checked
// against shared/report-templates/fixtures/remarks-surprise-2.json.
// A `remarks` block's lines come from the blocks above it in the same section (back to the
// previous remarks block): checklist items via item.says[answer] (per course when the courses
// answered differently), cards via block.says (one sentence per card) + choice fields' own
// says (prefixed by block.sayPrefix, followed by their `noteFor` box), fields blocks via a
// choice field's says. Every line carries neg = "an issue" (answer no/partial, a no/partial
// tone, always:"neg" or a negWhen rule) -- the narrative PDF marks those, findings list them first.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class RemarkLine(val text: String, val neg: Boolean)

private val NEG_TONES = setOf("no", "partial")
private val NEG_ANSWERS = setOf("no", "partial")
private val PLACEHOLDER = Regex("""\{(\w+)\}""")
private val INNER_SEGMENT = Regex("""\[([^\[\]]*)\]""")

private fun blank(value: String?): Boolean = value == null || value.trim().isEmpty()

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

// fill "{a} [x {b}]" from lookup: innermost [..] first, kept only when all its keys are filled;
// a blank key outside any [..] -> null (no sentence). Values go in as private-use markers so a
// "[" or "{" typed by the officer can't be read as template syntax.
fun fillSays(template: String, lookup: (String) -> String): String? {
    val values = mutableListOf<String>()
    fun substitute(part: String): String? {
        var missing = false
        val out = PLACEHOLDER.replace(part) { match ->
            val value = lookup(match.groupValues[1]).trim()
            if (value.isEmpty()) {
                missing = true
                ""
            } else {
                values += value
                "${values.size - 1}"
            }
        }
        return if (missing) null else out
    }
    var text = template
    while (true) {
        val match = INNER_SEGMENT.find(text) ?: break
        val kept = substitute(match.groupValues[1]) ?: ""
        text = text.replaceRange(match.range, kept)
    }
    val filled = substitute(text) ?: return null
    val restored = Regex("(\\d+)").replace(filled) { values[it.groupValues[1].toInt()] }
    return ensureStop(restored.replace(Regex("\\s+"), " ").trim()).ifEmpty { null }
}

private fun number(value: String): Double? = value.trim().replace(",", "").toDoubleOrNull()

private fun ruleMatches(rule: NegRule, card: JsonObject): Boolean {
    val value = number(card.text(rule.field)) ?: return false
    return when (rule.op) {
        "lt" -> number(card.text(rule.than ?: return false))?.let { value < it } ?: false
        "gap" -> rule.others.any { other ->
            val base = number(card.text(other)) ?: return@any false
            base > 0 && value < base * (1 - rule.pct / 100.0)
        }
        else -> false
    }
}

// "{key}" -> the card's value; a choice value -> its option's say (or label)
private fun cardLookup(block: ReportBlock.Cards, card: JsonObject): (String) -> String = { key ->
    val raw = card.text(key)
    val field = block.fields.find { it.key == key }
    if (field?.kind == "choice" && raw.isNotEmpty()) {
        field.choiceOptions().find { it.id == raw }?.let { it.say.ifBlank { it.label } } ?: raw
    } else {
        raw
    }
}

private fun cardLines(block: ReportBlock.Cards, data: ReportData): List<RemarkLine> {
    val lines = mutableListOf<RemarkLine>()
    data.cards(block.key).forEach { card ->
        val lookup = cardLookup(block, card)
        block.says?.let { says ->
            val text = fillSays(says.text, lookup)
            if (text != null) {
                val usedKeys = PLACEHOLDER.findAll(says.text).map { it.groupValues[1] }.toSet()
                val negTone = block.fields.any { field ->
                    field.kind == "choice" && field.key in usedKeys && field.toneFor(card.text(field.key)) in NEG_TONES
                }
                val neg = says.always == "neg" || negTone || says.negWhen.any { ruleMatches(it, card) }
                lines += RemarkLine(text, neg)
            }
        }
        block.fields.forEach { field ->
            val value = card.text(field.key)
            val sentence = field.says[value]
            if (field.kind != "choice" || sentence.isNullOrBlank()) return@forEach
            val prefix = block.sayPrefix?.let { fillSays(it, lookup)?.removeSuffix(".")?.let { filled -> "$filled " } } ?: ""
            val note = block.fields.find { it.noteFor == field.key }?.let { card.text(it.key) }.orEmpty()
            val text = listOf(ensureStop(prefix + sentence), ensureStop(note)).filter { it.isNotEmpty() }.joinToString(" ")
            lines += RemarkLine(text, field.toneFor(value) in NEG_TONES)
        }
    }
    return lines
}

// "Plumbing and pipe fitting batch-4" -- a bare number after the course reads like part of its name
fun courseBatchLabel(course: String, batch: String): String {
    val courseText = course.trim()
    val batchText = batch.trim()
    if (batchText.isEmpty()) return courseText
    return listOf(courseText, "batch-$batchText").filter { it.isNotEmpty() }.joinToString(" ")
}

private fun courseLabel(card: JsonObject): String = courseBatchLabel(card.text("course"), card.text("batch"))

private fun itemSentence(item: ChecklistItem, answer: String, answers: List<AnswerOption>): String =
    item.says[answer]?.takeIf { it.isNotBlank() }
        ?: ensureStop("${item.text}: ${answers.find { it.id == answer }?.label ?: answer}")

private fun checklistLines(block: ReportBlock.Checklist, data: ReportData, answers: List<AnswerOption>): List<RemarkLine> {
    val courseCards = data.cards("courses").associateBy { it.text("_id") }
    val ids = courseIds(data)
    val lines = mutableListOf<RemarkLine>()
    block.items.forEach { item ->
        val remarks = ensureStop(data.checkRemarks(item.id).trim())
        val itemLines = mutableListOf<RemarkLine>()
        val perCourse = if (item.perCourse && ids.size >= 2) data.checkCourses(item.id) else emptyMap()
        val answered = ids.mapNotNull { id -> perCourse[id]?.takeIf { !blank(it) }?.let { id to it } }
        if (answered.map { it.second }.distinct().size > 1) {
            // courses differ: one line per course, "Welding (SMAW) 07: <sentence>"
            answered.forEach { (id, answer) ->
                if (answer == "na") return@forEach
                val label = courseCards[id]?.let { courseLabel(it) }.orEmpty()
                itemLines += RemarkLine("$label: ${itemSentence(item, answer, answers)}", answer in NEG_ANSWERS)
            }
        } else {
            val answer = data.checkAnswer(item.id)
            if (!blank(answer) && answer != "na") itemLines += RemarkLine(itemSentence(item, answer, answers), answer in NEG_ANSWERS)
        }
        if (remarks.isNotEmpty()) {
            if (itemLines.isEmpty()) {
                itemLines += RemarkLine("${item.text}: $remarks", false)
            } else {
                val last = itemLines.removeAt(itemLines.size - 1)
                itemLines += last.copy(text = "${last.text} $remarks")
            }
        }
        lines += itemLines
    }
    return lines
}

private fun fieldsLines(block: ReportBlock.Fields, data: ReportData): List<RemarkLine> =
    block.fields.mapNotNull { field ->
        val value = data.field(field.key)
        val sentence = field.says[value]?.takeIf { field.kind == "choice" && it.isNotBlank() } ?: return@mapNotNull null
        RemarkLine(sentence, field.toneFor(value) in NEG_TONES)
    }

// the blocks a remarks block summarises: those above it back to the previous remarks block
private fun coveredBlocks(section: ReportSection, remarks: ReportBlock.Remarks): List<ReportBlock> {
    val index = section.blocks.indexOf(remarks)
    val before = section.blocks.subList(0, maxOf(index, 0))
    val lastRemarks = before.indexOfLast { it is ReportBlock.Remarks }
    return before.drop(lastRemarks + 1)
}

// a manual remarks block (K, L) builds nothing: the officer writes it by hand
fun buildRemarkLines(template: ReportTemplate, section: ReportSection, remarks: ReportBlock.Remarks, data: ReportData): List<RemarkLine> =
    if (remarks.manual) emptyList() else coveredBlocks(section, remarks).flatMap { block ->
        when (block) {
            is ReportBlock.Checklist -> checklistLines(block, data, template.answers)
            is ReportBlock.Cards -> cardLines(block, data)
            is ReportBlock.Fields -> fieldsLines(block, data)
            else -> emptyList()
        }
    }

// what prints: the officer's edit while it is still fresh (source == built lines), else the
// built lines. An edited line keeps neg only when it is word-for-word a built issue line.
// A manual block prints its typed text, one line per non-blank line, never an issue.
fun printedRemarkLines(template: ReportTemplate, section: ReportSection, remarks: ReportBlock.Remarks, data: ReportData): List<RemarkLine> {
    if (remarks.manual) return textLines(data.remarksText(remarks.key)).map { RemarkLine(it, false) }
    val built = buildRemarkLines(template, section, remarks, data)
    val edited = data.remarksText(remarks.key)
    if (edited.isBlank() || data.remarksSource(remarks.key) != built.joinToString("\n") { it.text }) return built
    val negTexts = built.filter { it.neg }.map { it.text }.toSet()
    return textLines(edited).map { RemarkLine(it, it in negTexts) }
}

private fun textLines(value: String): List<String> = value.lines().map { it.trim() }.filter { it.isNotEmpty() }

// every remarks line of the report, issues first (template order within each group), no repeats
fun findingCandidates(template: ReportTemplate, data: ReportData): List<RemarkLine> {
    val all = template.sections.flatMap { section ->
        section.blocks.filterIsInstance<ReportBlock.Remarks>().flatMap { printedRemarkLines(template, section, it, data) }
    }.distinctBy { it.text }
    return all.filter { it.neg } + all.filterNot { it.neg }
}

// AI pre-select fallback: every issue line
fun fallbackMajorFindings(candidates: List<RemarkLine>): List<Finding> =
    candidates.filter { it.neg }.map { Finding(it.text, it.text) }

// "3, 1, 7" -> candidate indexes 2, 0, 6 (1-based, in range, first occurrence order); an
// answer with words in it (an old worker rewriting the list instead of choosing) -> null
fun parseMajorAnswer(answer: String, count: Int): List<Int>? {
    if (Regex("[A-Za-z]{3,}").containsMatchIn(answer)) return null
    val picked = Regex("""\d+""").findAll(answer).map { it.value.toInt() }.filter { it in 1..count }.distinct().map { it - 1 }.toList()
    return picked.ifEmpty { null }
}

// recommendations fallback: one generic line per finding
fun fallbackRecommendations(findings: List<String>): List<String> =
    findings.map { "The institute should take necessary measures to address this: ${it.trim().removeSuffix(".")}." }
