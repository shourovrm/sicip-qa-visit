// surprise v2 "narrative" PDF: same page/header as ReportHtml.kt, but each section prints as
// its remark sentences (bullets, issues marked) with tables only where rows compare better
// (cards blocks). Checklists print nothing themselves -- their answers are the bullets.
// Kotlin port of web/src/lib/narrativehtml.js; keep the two in lockstep.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.courseBatchLabel
import bd.sicip.qavisit.domain.report.findingLines
import bd.sicip.qavisit.domain.report.normalize
import bd.sicip.qavisit.domain.report.printedRemarkLines
import bd.sicip.qavisit.domain.report.sectionHasContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val NARRATIVE_CSS = """
  .kv { display: grid; grid-template-columns: 34mm 1fr 30mm 1fr; gap: 1pt 6pt; margin: 0 0 4pt; }
  .kv b::after { content: ':'; }
  td.l, th.l { text-align: left; }
  td small { display: block; text-align: left; color: #444; font-size: 8pt; }
""".trimIndent()

private fun JsonObject.value(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun isBlank(value: String?): Boolean = value == null || value.trim().isEmpty()

private fun kvHtml(fields: List<Field>, data: ReportData): String {
    val cells = fields.filter { it.draftFrom == null && !isBlank(data.field(it.key)) }.joinToString("") { f ->
        "<b>${reportEsc(f.label.removeSuffix("?"))}</b><span>${reportFieldValueHtml(f, data.field(f.key))}</span>"
    }
    return if (cells.isEmpty()) "" else "<div class=\"kv\">$cells</div>"
}

private fun fieldsHtml(block: ReportBlock.Fields, data: ReportData): String {
    val lists = block.fields.filter { it.draftFrom == "findings" }.joinToString("") { f ->
        numberedListHtml(f.label, data.field(f.key).lines())
    }
    return kvHtml(block.fields, data) + lists
}

// one table per cards block; columns nobody filled are left out
private fun cardsTableHtml(block: ReportBlock.Cards, data: ReportData): String {
    val cards = data.cards(block.key)
    if (cards.isEmpty() || block.narrative == "bullets") return ""
    val columns = block.fields.filter { f -> f.noteFor == null && cards.any { !isBlank(it.value(f.key)) } }
    if (columns.isEmpty()) return ""
    val head = columns.joinToString("") { "<th>${reportEsc(it.label)}</th>" }
    val rows = cards.joinToString("") { card ->
        "<tr>${columns.joinToString("") { f -> "<td${cellClassAttr(f)}>${dashIfBlank(reportFieldValueHtml(f, card.value(f.key)))}</td>" }}</tr>"
    }
    val heading = block.heading?.let { "<h3>${reportEsc(it)}</h3>" } ?: ""
    return "$heading<table class=\"cards-table\"><thead><tr>$head</tr></thead><tbody>$rows</tbody></table>"
}

// interviews: questions down, courses across; a question's remarks sit under its answer
private fun interviewTableHtml(block: ReportBlock.Cards, data: ReportData): String {
    val linked = block.linkFrom?.fields?.toSet() ?: emptySet()
    // a course nobody was interviewed in prints no blank column
    val cards = data.cards(block.key).filter { card -> block.fields.any { it.key !in linked && !isBlank(card.value(it.key)) } }
    if (cards.isEmpty()) return ""
    // a question (and its note) prints only when at least one course answered it
    val answered = answeredRowKeys(block, cards)
    val rows = block.fields.filter { it.key in answered }
    val head = "<th class=\"l\">Question</th>" + cards.joinToString("") { card ->
        "<th>${reportEsc(courseBatchLabel(card.value("course"), card.value("batch")))}</th>"
    }
    val body = rows.joinToString("") { f ->
        val note = block.fields.find { it.noteFor == f.key }
        val cells = cards.joinToString("") { card ->
            val noteText = note?.let { card.value(it.key) }.orEmpty()
            val small = if (noteText.isEmpty()) "" else "<small>${reportEsc(noteText)}</small>"
            "<td${cellClassAttr(f)}>${dashIfBlank(reportFieldValueHtml(f, card.value(f.key)) + small)}</td>"
        }
        "<tr><td class=\"l\">${reportEsc(f.label)}</td>$cells</tr>"
    }
    return "<table class=\"cards-table\"><thead><tr>$head</tr></thead><tbody>$body</tbody></table>"
}

private fun blockHtml(block: ReportBlock, section: ReportSection, data: ReportData, template: ReportTemplate): String = when (block) {
    is ReportBlock.Fields -> fieldsHtml(block, data)
    is ReportBlock.Cards -> if (block.display == "tabs") interviewTableHtml(block, data) else cardsTableHtml(block, data)
    is ReportBlock.Remarks -> printedRemarkLines(template, section, block, data).let { if (it.isEmpty()) "" else remarkBulletsHtml(it) }
    is ReportBlock.Findings -> numberedListHtml(block.heading ?: "Major findings", findingLines(block, data))
    // answers print as the remarks bullets; v2 has no flags or criteria
    is ReportBlock.Checklist, is ReportBlock.Flags, is ReportBlock.Criteria -> ""
}

private fun sectionHtml(section: ReportSection, data: ReportData, template: ReportTemplate): String {
    val blocks = section.blocks.joinToString("") { blockHtml(it, section, data, template) }
    return "<h2><span class=\"letter\">${reportEsc(section.letter)}</span>${reportEsc(section.title)}</h2>$blocks"
}

fun buildNarrativeReportHtml(template: ReportTemplate, data: ReportData): String {
    val normalized = normalize(template, data)
    val sections = template.sections
        .filter { !it.optional || sectionHasContent(it, normalized) }
        .joinToString("") { sectionHtml(it, normalized, template) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${reportEsc(template.title)}</title>" +
        "<style>${reportCss(template)}\n$NARRATIVE_CSS\n$SIGNOFF_CSS</style></head><body>" +
        reportHeaderHtml(template) + sections + signoffHtml(normalized) +
        "</body></html>"
}
