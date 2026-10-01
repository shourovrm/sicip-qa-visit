// filled visit-report printable HTML -- Kotlin port of web/src/lib/reporthtml.js, kept in
// lockstep with it (same CSS, same per-block rules, same column-width table) so the android and
// web PDFs of the same report look the same. Layout traces back to
// ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-report-template.html/.pdf.
//
// CHANGE SET 3 (2026-09-25) "ONE REPORT LAYOUT FOR ALL OUTPUTS": reporthtml.js is the reference
// layout, this file is a port of it -- web/src/lib/reportlayout.js's column-width table has no
// Kotlin equivalent to import (no shared module between the two platforms), so the
// LayoutColumn lists below are a hand-kept copy; keep them numerically identical if reportlayout.js
// changes. Rules baked in throughout: NEVER print "Not answered" (blank = an empty tick row /
// empty line / empty box); an unanswered checklist item is simply four empty ☐; cards render as
// ONE TABLE PER BLOCK with one row per card; the flags list always shows every fixed item
// (ticked or not) plus any custom (countsAsFlags) flags as extra ticked rows.
//
// pure string building, no android imports -- this file's output is a plain JVM unit test
// target (test/.../pdf/ReportHtmlTest.kt); the actual print-to-PDF still goes through
// pdf/BillPrinter.kt's WebView pipeline (renderBillPdf works for any HTML string, this one included).
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.sectionHasContent
import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.CardsCompare
import bd.sicip.qavisit.domain.report.ChecklistItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.cardCompareMismatch
import bd.sicip.qavisit.domain.report.RemarkLine
import bd.sicip.qavisit.domain.report.findingLines
import bd.sicip.qavisit.domain.report.normalize
import bd.sicip.qavisit.domain.report.printedRemarkLines
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// answer/choice colours -- MUST match ui/theme/Color.kt's Light* tone values and
// web/src/lib/reportlayout.js's TONE_COLOR exactly (three independent copies of the same four
// hex values by necessity: this file has no android/compose dependency, reportlayout.js has no
// kotlin dependency -- there is no single shared source they could both import from).
private val TONE_COLOR = mapOf(
    "yes" to "#1c6b38",
    "no" to "#b3261e",
    "partial" to "#8a4600",
    "na" to "#4c4f66",
)

private const val TICK_CHECKED = "☒" // ☒ BALLOT BOX WITH X
private const val TICK_UNCHECKED = "☐" // ☐ BALLOT BOX

private data class LayoutColumn(val label: String, val weight: Double)

// checklist table: # | Item | Yes | No | Part | N/A | Remarks. Weights sum to 100, used
// directly as CSS % -- hand-kept copy of web/src/lib/reportlayout.js's CHECKLIST_COLUMNS.
private val CHECKLIST_COLUMNS = listOf(
    LayoutColumn("#", 4.0),
    LayoutColumn("Item", 38.0),
    LayoutColumn("Yes", 7.5),
    LayoutColumn("No", 7.5),
    LayoutColumn("Part", 7.5),
    LayoutColumn("N/A", 7.5),
    LayoutColumn("Remarks", 28.0),
)

// interview per-course sub-table (section I): Item | Yes | No | Part | N/A -- no #, no Remarks
// column (interview questions carry no per-question remarks, only a shared feedback field).
private val INTERVIEW_TICK_COLUMNS = listOf(
    LayoutColumn("Item", 70.0),
    LayoutColumn("Yes", 7.5),
    LayoutColumn("No", 7.5),
    LayoutColumn("Part", 7.5),
    LayoutColumn("N/A", 7.5),
)

// surprise v2 interviews: questions carry their own remarks box (`noteFor` fields)
private val INTERVIEW_NOTE_COLUMNS = listOf(
    LayoutColumn("Item", 42.0),
    LayoutColumn("Yes", 7.5),
    LayoutColumn("No", 7.5),
    LayoutColumn("Part", 7.5),
    LayoutColumn("N/A", 7.5),
    LayoutColumn("Remarks", 28.0),
)

// flags: narrow tick column + wide text column.
private val FLAGS_COLUMNS = listOf(LayoutColumn("", 6.0), LayoutColumn("", 94.0))

private fun esc(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun blank(v: String?): Boolean = v == null || v.trim().isEmpty()

private fun escMultiline(s: String): String = esc(s).replace("\n", "<br>")

// a table cell with nothing in it prints "-" (spec 2026-10-02 §14); tick cells keep their glyphs
internal fun dashIfBlank(cellHtml: String): String = if (cellHtml.isBlank()) "-" else cellHtml

private fun answerMapOf(template: ReportTemplate): Map<String, AnswerOption> = template.answers.associateBy { it.id }

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

// a field counts as a "standard yes/no/partial/na choice" (renders with the same 4-tick-column
// style as a checklist row, e.g. section I's q1-q7) when its option ids are exactly the
// template's answer ids -- checked by id set, not by key, so this stays template-driven. Hand-
// kept copy of web/src/lib/reportlayout.js's isStandardAnswerChoice.
private fun isStandardAnswerChoice(field: Field, template: ReportTemplate): Boolean {
    if (field.kind != "choice") return false
    val answerIds = template.answers.map { it.id }.toSet()
    val optionIds = field.choiceOptions().map { it.id }.toSet()
    return optionIds == answerIds
}

private fun toneSpan(tone: String, label: String): String {
    val color = TONE_COLOR[tone] ?: "#111"
    return "<span class=\"ans\" style=\"color:$color\">${esc(label)}</span>"
}

// text for one field's value: choice fields render as a coloured tone label (blank = nothing,
// never "Not answered" -- CHANGE SET 3); every other kind renders as escaped plain/multiline text.
// a label that is already a question ("Could the trainees answer it?") gets no trailing colon
private fun labelClass(label: String): String = if (label.trim().endsWith("?")) "label question" else "label"

// choice/select fields print every option inline with a tick box, the chosen one filled (in its
// tone colour for a choice field) -- port of web reporthtml.js inlineChoiceHtml.
private fun inlineChoiceHtml(field: Field, rawValue: String?): String {
    val options = if (field.kind == "select") {
        field.selectOptions().map { AnswerOption(id = it, label = it, tone = "") }
    } else {
        field.choiceOptions()
    }
    val parts = options.joinToString(" ") { opt ->
        val checked = rawValue == opt.id
        val color = if (checked) TONE_COLOR[opt.tone] else null
        val style = if (color != null) " style=\"color:$color\"" else ""
        val tick = if (checked) TICK_CHECKED else TICK_UNCHECKED
        "<span class=\"choice-opt${if (checked) " checked" else ""}\"$style>$tick ${esc(opt.label)}</span>"
    }
    return "<div class=\"line choice-line\"><span class=\"${labelClass(field.label)}\">${esc(field.label)}</span><span class=\"choices\">$parts</span></div>"
}

// a cell's printed words (choice -> its label), for column widths
internal fun printedCellText(field: Field, rawValue: String?): String {
    if (rawValue == null) return ""
    if (field.kind == "choice") return field.choiceOptions().find { it.id == rawValue }?.label ?: rawValue
    return rawValue
}

private fun fieldValueHtml(field: Field, rawValue: String?): String {
    if (field.kind == "choice") {
        if (blank(rawValue)) return ""
        val opt = field.choiceOptions().find { it.id == rawValue }
        return if (opt != null) toneSpan(opt.tone, opt.label) else esc(rawValue)
    }
    if (blank(rawValue)) return ""
    if (field.kind == "time") return esc(displayTime(rawValue!!))
    return escMultiline(rawValue!!)
}

// "13:29" or "13:29:00" -> "1:29 PM" for print; mirrors web reportlayout.js displayTime
internal fun displayTime(value: String): String {
    val match = Regex("^(\\d{1,2}):(\\d{2})").find(value) ?: return value
    val hour24 = match.groupValues[1].toInt()
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12:${match.groupValues[2]} $suffix"
}

// short-kind fields print "label: value" on one line; longtext fields print the label above a
// bordered box (matches the paper form's ruled boxes for findings/instructions/address/...).
// blank value = an empty line/box, never placeholder text. `value` reads either a top-level
// ReportData.field(key) or a raw card JsonObject's field, whichever the caller has.
private fun fieldsListHtml(fields: List<Field>, value: (String) -> String?): String =
    fields.joinToString("") { f ->
        if (f.draftFrom == "findings") {
            numberedListHtml(f.label, value(f.key).orEmpty().lines())
        } else if (f.kind == "longtext") {
            val raw = value(f.key)
            val body = if (blank(raw)) "" else escMultiline(raw!!)
            "<div class=\"field-box\"><div class=\"label\">${esc(f.label)}</div><div class=\"box\">$body</div></div>"
        } else if (f.kind == "choice" || f.kind == "select") {
            inlineChoiceHtml(f, value(f.key))
        } else {
            "<div class=\"line\"><span class=\"${labelClass(f.label)}\">${esc(f.label)}</span><span class=\"value\">${fieldValueHtml(f, value(f.key))}</span></div>"
        }
    }

// surprise v2 "Major findings" / "Recommendations": heading + numbered points
internal fun numberedListHtml(heading: String, lines: List<String>): String {
    val items = lines.map { it.trim() }.filter { it.isNotEmpty() }
    val body = if (items.isEmpty()) "<p class=\"empty\">None.</p>" else "<ol class=\"findings\">${items.joinToString("") { "<li>${esc(it)}</li>" }}</ol>"
    return "<h3>${esc(heading)}</h3>$body"
}

// surprise v2 section remarks: bullets, issues marked (same list as the narrative report)
internal fun remarkBulletsHtml(lines: List<RemarkLine>): String =
    "<ul class=\"bul\">${lines.joinToString("") { "<li${if (it.neg) " class=\"neg\"" else ""}>${esc(it.text)}</li>" }}</ul>"

// no "Remarks" label: the bullets follow the table/heading directly; nothing typed = nothing printed
private fun remarksBoxHtml(block: ReportBlock.Remarks, section: ReportSection, data: ReportData, template: ReportTemplate): String {
    val lines = printedRemarkLines(template, section, block, data)
    if (lines.isEmpty()) return ""
    return remarkBulletsHtml(lines)
}

private fun fieldsBlockHtml(block: ReportBlock.Fields, data: ReportData): String =
    "<div class=\"details\">${fieldsListHtml(block.fields) { key -> data.field(key) }}</div>"

// courses with a non-blank name, in section-A order -- same list normalize()'s syncPerCourse
// splits per-course items by, and the source for the linked interview/attendance cards and the
// per-course summary line.
private fun courseList(data: ReportData): List<JsonObject> =
    data.cards("courses").filter { !blank(it.stringOrNull("course")) }

// numbers, batch no., counts, quantities, dates/times and short choice answers print centred;
// long text stays left. decided by the field, so a whole column lines up the same way.
private val CENTRED_KINDS = setOf("number", "choice", "select", "date", "time", "phone")

internal fun isCentredField(field: Field): Boolean =
    field.kind in CENTRED_KINDS || (field.key == "batch" && field.kind == "text")

internal fun cellClassAttr(field: Field): String = if (isCentredField(field)) " class=\"c\"" else ""

private fun tickCellHtml(checked: Boolean, tone: String?): String {
    val color = if (checked) tone?.let { TONE_COLOR[it] } else null
    val style = if (color != null) " style=\"color:$color\"" else ""
    return "<td class=\"tick-cell\"><span class=\"tick\"$style>${if (checked) TICK_CHECKED else TICK_UNCHECKED}</span></td>"
}

private fun colgroupHtml(columns: List<LayoutColumn>): String =
    "<colgroup>${columns.joinToString("") { "<col style=\"width:${it.weight}%\">" }}</colgroup>"

private fun tableHeadHtml(columns: List<LayoutColumn>): String =
    "<thead><tr>${columns.joinToString("") { "<th>${esc(it.label)}</th>" }}</tr></thead>"

// small line under a per-course item's text, e.g. "Welding (SMAW) 07: Yes · Electrical
// Installation 03: No" -- only courses with a non-blank per-course answer are listed; omitted
// entirely (not shown blank) when nothing has been answered yet for any course.
private fun perCourseLineHtml(item: ChecklistItem, data: ReportData, courses: List<JsonObject>, answerMap: Map<String, AnswerOption>): String {
    if (!item.perCourse || courses.size < 2) return ""
    val per = data.checkCourses(item.id)
    val parts = courses.mapNotNull { c ->
        val id = c.stringOrNull("_id") ?: return@mapNotNull null
        val answer = per[id]
        if (blank(answer)) return@mapNotNull null
        val label = answerMap[answer]?.label ?: answer
        val name = listOfNotNull(c.stringOrNull("course"), c.stringOrNull("batch")).filter { !blank(it) }.joinToString(" ")
        "${esc(name)}: ${esc(label)}"
    }
    return if (parts.isNotEmpty()) "<div class=\"per-course-line\">${parts.joinToString(" &middot; ")}</div>" else ""
}

private fun checklistBlockHtml(block: ReportBlock.Checklist, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String {
    val courses = courseList(data)
    val answerIds = template.answers.map { it.id }
    val heading = block.heading?.let { "<h3>${esc(it)}</h3>" } ?: ""
    val rows = block.items.mapIndexed { i, item ->
        val answer = data.checkAnswer(item.id)
        val tickCells = answerIds.joinToString("") { id -> tickCellHtml(answer == id, answerMap[id]?.tone) }
        // no "Per course" badge on paper: the course line under the item already says it
        val itemHtml = "${esc(item.text)}${perCourseLineHtml(item, data, courses, answerMap)}"
        val remarks = data.checkRemarks(item.id)
        val remarksHtml = if (blank(remarks)) "-" else escMultiline(remarks)
        "<tr><td class=\"num\">${i + 1}</td><td class=\"question\">$itemHtml</td>$tickCells<td class=\"remarks\">$remarksHtml</td></tr>"
    }.joinToString("")
    return "$heading<table class=\"checklist\">${colgroupHtml(CHECKLIST_COLUMNS)}${tableHeadHtml(CHECKLIST_COLUMNS)}<tbody>$rows</tbody></table>"
}

// one table per cards block, one row per card, columns = the block's fields in template order.
private fun cardsBlockHtml(block: ReportBlock.Cards, data: ReportData): String {
    val entries = data.cards(block.key)
    val heading = block.heading?.let { "<h3>${esc(it)}</h3>" } ?: ""
    val note = block.note?.let { "<p class=\"block-note\">${esc(it)}</p>" } ?: ""
    if (entries.isEmpty()) return "$heading$note<p class=\"empty\">No entries.</p>"
    val headerRow = block.fields.joinToString("") { f -> "<th>${esc(f.label)}</th>" }
    val mismatchedRows = mutableListOf<Int>()
    val rows = entries.mapIndexed { i, entry ->
        if (block.compare != null && cardCompareMismatch(block.compare, entry)) mismatchedRows.add(i + 1)
        "<tr>${block.fields.joinToString("") { f -> "<td${cellClassAttr(f)}>${dashIfBlank(fieldValueHtml(f, entry.stringOrNull(f.key)))}</td>" }}</tr>"
    }.joinToString("")
    var warning = ""
    if (mismatchedRows.isNotEmpty() && block.compare != null && block.compare.print) {
        val which = if (mismatchedRows.size == entries.size) "" else " (row${if (mismatchedRows.size > 1) "s" else ""} ${mismatchedRows.joinToString(", ")})"
        warning = "<p class=\"mismatch-msg\">${esc(block.compare.message)}$which</p>"
    }
    val layout = cardsColumnLayout(block.fields) { f -> entries.map { printedCellText(f, it.stringOrNull(f.key)) } }
    return "$heading$note${tableOpenHtml("cards-table", layout)}<thead><tr>$headerRow</tr></thead><tbody>$rows</tbody></table>$warning"
}

// interview rows (non-linked, non-note fields) that at least one course card answered; a
// question's note alone also counts, so typed text is never dropped. shared with the narrative.
internal fun answeredRowKeys(block: ReportBlock.Cards, cards: List<JsonObject>): Set<String> {
    val linkedKeys = block.linkFrom?.fields?.toSet() ?: emptySet()
    fun filled(card: JsonObject, key: String) = !blank(card.stringOrNull(key))
    return block.fields
        .filter { it.key !in linkedKeys && it.noteFor == null }
        .filter { row ->
            val note = block.fields.find { it.noteFor == row.key }
            cards.any { card -> filled(card, row.key) || (note != null && filled(card, note.key)) }
        }
        .map { it.key }
        .toSet()
}

// linked cards block with display:"tabs" (section I "interviews"): tabs are an editor-only
// concept -- exports print one small table per course instead. Fields whose choice options are
// exactly the template's answer ids (e.g. q1-q7) render as a checklist-style tick sub-table;
// every other field (tech_topic, tech_result, feedback) renders through
// the normal fields renderer. Linked fields (course/batch) are shown in the caption, not twice.
private fun tabsCardsBlockHtml(block: ReportBlock.Cards, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String {
    val entries = data.cards(block.key)
    if (entries.isEmpty()) return "<p class=\"empty\">Add courses in section A.</p>"
    val linkedKeys = block.linkFrom?.fields?.toSet() ?: emptySet()
    // a question (and its note) prints only when at least one course answered it
    val answeredKeys = answeredRowKeys(block, entries)
    val tickFields = block.fields.filter { it.key !in linkedKeys && it.key in answeredKeys && isStandardAnswerChoice(it, template) }
    val tickFieldKeys = tickFields.map { it.key }.toSet()
    val noteFields = block.fields.filter { it.noteFor != null }
    val otherFields = block.fields.filter { it.key !in linkedKeys && it.key in answeredKeys && it.key !in tickFieldKeys && it.noteFor == null }
    val answerIds = template.answers.map { it.id }
    val columns = if (noteFields.isEmpty()) INTERVIEW_TICK_COLUMNS else INTERVIEW_NOTE_COLUMNS
    return entries.joinToString("") { entry ->
        val linkedFieldKeys = block.linkFrom?.fields ?: emptyList()
        val extra = linkedFieldKeys.filter { it != block.titleField }
            .mapNotNull { entry.stringOrNull(it) }.filter { !blank(it) }.joinToString(" ")
        val titleValue = entry.stringOrNull(block.titleField) ?: ""
        val caption = "${esc(titleValue)}${if (extra.isNotBlank()) " &middot; Batch ${esc(extra)}" else ""}"
        val tickRows = tickFields.joinToString("") { f ->
            val value = entry.stringOrNull(f.key)
            val noteCell = if (noteFields.isEmpty()) "" else "<td>${dashIfBlank(escMultiline(noteFields.find { it.noteFor == f.key }?.let { entry.stringOrNull(it.key) }.orEmpty()))}</td>"
            "<tr><td class=\"question\">${esc(f.label)}</td>${answerIds.joinToString("") { id -> tickCellHtml(value == id, answerMap[id]?.tone) }}$noteCell</tr>"
        }
        val tickTable = if (tickFields.isNotEmpty()) {
            "<table class=\"checklist interview-ticks\">${colgroupHtml(columns)}${tableHeadHtml(columns)}<tbody>$tickRows</tbody></table>"
        } else {
            ""
        }
        val otherHtml = "<div class=\"details\">${fieldsListHtml(otherFields) { key -> entry.stringOrNull(key) }}</div>"
        "<div class=\"interview-card\"><h3>$caption</h3>$tickTable$otherHtml</div>"
    }
}

// the fixed flags list AND any countsAsFlags cards blocks (free-text flags, e.g. L
// "other_flags") in the same section render together as one 2-column tick/text table: every
// fixed item always prints (ticked or not), a countsAsFlags card with a non-blank titleField is
// inherently "ticked" (its presence is the flag) and appears as an extra checked row.
private fun combinedFlagsHtml(section: ReportSection, data: ReportData): String {
    val flagsBlock = section.blocks.filterIsInstance<ReportBlock.Flags>().firstOrNull()
    val ticked = data.flags()
    val rows = mutableListOf<String>()
    flagsBlock?.items?.forEach { item ->
        val checked = item.id in ticked
        rows.add("<tr>${tickCellHtml(checked, "no")}<td class=\"flag-text${if (checked) " checked" else ""}\">${esc(item.text)}</td></tr>")
    }
    section.blocks.filterIsInstance<ReportBlock.Cards>().filter { it.countsAsFlags }.forEach { block ->
        data.cards(block.key).forEach { entry ->
            val text = entry.stringOrNull(block.titleField)
            if (!blank(text)) {
                rows.add("<tr>${tickCellHtml(true, "no")}<td class=\"flag-text checked\">${esc(text!!.trim())}</td></tr>")
            }
        }
    }
    if (rows.isEmpty()) return "<p class=\"empty\">None ticked.</p>"
    return "<table class=\"flags-table\">${colgroupHtml(FLAGS_COLUMNS)}<tbody>${rows.joinToString("")}</tbody></table>"
}

private fun blockHtml(block: ReportBlock, section: ReportSection, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String = when (block) {
    is ReportBlock.Fields -> fieldsBlockHtml(block, data)
    is ReportBlock.Checklist -> checklistBlockHtml(block, data, template, answerMap)
    is ReportBlock.Cards -> if (block.display == "tabs") tabsCardsBlockHtml(block, data, template, answerMap) else cardsBlockHtml(block, data)
    is ReportBlock.Flags -> "" // handled by sectionHtml's combinedFlagsHtml, alongside any countsAsFlags cards block in the same section
    // this surprise-report layout never receives one -- qa-v1.json's criteria blocks render
    // through the separate pdf/QaReportHtml.kt (spec §7), never this file.
    is ReportBlock.Criteria -> ""
    is ReportBlock.Remarks -> remarksBoxHtml(block, section, data, template)
    is ReportBlock.Findings -> numberedListHtml(block.heading ?: "Major findings", findingLines(block, data))
}

private fun sectionHtml(section: ReportSection, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String {
    val note = section.note?.let { "<span class=\"note\">${esc(it)}</span>" } ?: ""
    val optionalTag = if (section.optional) " <span class=\"optional-tag\">Optional</span>" else ""
    fun isFlagsRelevant(b: ReportBlock) = b is ReportBlock.Flags || (b is ReportBlock.Cards && b.countsAsFlags)
    var flagsRendered = false
    val blocks = section.blocks.joinToString("") { b ->
        if (isFlagsRelevant(b)) {
            if (flagsRendered) {
                ""
            } else {
                flagsRendered = true
                combinedFlagsHtml(section, data)
            }
        } else {
            blockHtml(b, section, data, template, answerMap)
        }
    }
    return "<section class=\"keep\"><h2><span class=\"letter\">${esc(section.letter)}</span>${esc(section.title)}$optionalTag$note</h2>$blocks</section>"
}

// program line + title + rule; no subtitle, no officer/status line (spec 2026-10-01 §5)
private fun headerHtml(template: ReportTemplate): String =
    "<header><div class=\"program\">${esc(template.program)}</div><h1>${esc(template.title)}</h1></header>"

// footer title: the report's name without its ": Quality Assurance" tail ("Surprise Visit Report")
internal fun footerTitle(template: ReportTemplate): String = template.title.substringBefore(':').trim()

// @page rule shared by every report PDF: A4, 1 in top/bottom, 0.75 in sides; footer = title
// bottom-left, "Page X of Y" bottom-right (same as the web print and Word)
internal fun pageCss(footer: String, fontFamily: String): String {
    val quoted = footer.replace("\\", "").replace("\"", "")
    return """
  @page {
    size: A4 portrait;
    margin: 25.4mm 19.05mm;
    @bottom-left { content: "$quoted"; font: 8pt $fontFamily; color: #333; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 8pt $fontFamily; color: #333; }
  }""".trimIndent()
}

// one font for every report PDF (surprise form, narrative, QA)
internal const val REPORT_FONT = "Arial, \"Liberation Sans\", \"Noto Sans\", sans-serif"

// print CSS (spec 2026-10-01 §5): Arial, body 10 pt, tables 9 pt, section heading 11 pt with
// letter badge, sub-heading 10 pt, title 15 pt, program 9 pt; cell padding 4 pt / 6 pt.
// page geometry + footer come from pageCss. kept in lockstep with web/src/lib/reporthtml.js.
private val BODY_CSS = """
  * { box-sizing: border-box; }
  body { margin: 0; font-family: $REPORT_FONT; font-size: 10pt; line-height: 1.3; color: #111; }

  header { border-bottom: 1.6pt solid #111; padding-bottom: 4pt; margin-bottom: 8pt; }
  header .program { font-size: 9pt; }
  header h1 { margin: 2pt 0 0; font-size: 15pt; font-weight: 700; }

  h2 { display: flex; align-items: baseline; gap: 6pt; margin: 12pt 0 4pt; font-size: 11pt; font-weight: 700; break-after: avoid; }
  h2 .letter { display: inline-block; min-width: 15pt; padding: 1pt 0; text-align: center; background: #111; color: #fff; font-size: 10pt; }
  h2 .note { margin-left: auto; font-weight: 400; font-size: 9pt; font-style: italic; color: #333; }
  h2 .optional-tag { font-weight: 700; font-size: 8pt; text-transform: uppercase; letter-spacing: 0.03em; color: #8a4600; border: 0.6pt solid #8a4600; border-radius: 3pt; padding: 0.5pt 3pt; }
  h3 { margin: 6pt 0 3pt; font-size: 10pt; font-weight: 700; break-after: avoid; }

  .details { margin: 0 0 4pt; }
  .details .line { display: flex; align-items: baseline; gap: 4pt; min-height: 14pt; padding: 1pt 0; }
  .details .label { white-space: nowrap; font-weight: 700; }
  .details .label::after { content: ':'; }
  .details .label.question::after { content: ''; }
  .choice-line .choices { display: flex; flex-wrap: wrap; gap: 2pt 8pt; }
  .choice-opt { white-space: nowrap; }
  .choice-opt.checked { font-weight: 700; }
  .details .value { flex: 1; border-bottom: 0.6pt solid #ccc; }
  .field-box { margin: 3pt 0 6pt; }
  .field-box .label { font-weight: 700; display: block; margin-bottom: 2pt; }
  .field-box .box { border: 0.6pt solid #666; min-height: 22pt; padding: 4pt 6pt; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; margin: 0 0 4pt; }
  th, td { border: 0.6pt solid #666; padding: 4pt 6pt; vertical-align: middle; font-size: 9pt; text-align: left; }
  th { background: #e6e6e6; font-weight: 700; text-align: center; line-height: 1.15; }
  td { overflow-wrap: break-word; }
  th { overflow-wrap: normal; word-break: normal; hyphens: none; }
  td.c, td.num, td.tick-cell { text-align: center; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }

  .checklist td.num { color: #333; }
  .per-course-line { font-size: 8pt; font-weight: 400; color: #444; margin-top: 1pt; }

  .tick { font-size: 10pt; font-weight: 700; }

  .mismatch-msg { color: #b3261e; font-weight: 700; font-size: 9pt; margin: 0 0 6pt; }
  p.empty, .block-note { color: #666; font-style: italic; margin: 2pt 0 6pt; font-size: 9pt; }

  .flags-table td.flag-text.checked { font-weight: 700; color: #b3261e; }

  .interview-card { margin-bottom: 6pt; break-inside: avoid; }

  .ans { font-weight: 700; }

  .keep { break-inside: avoid; }

  ol.findings, ul.bul { margin: 3pt 0 8pt; padding-left: 16pt; }
  ol.findings li, ul.bul li { margin: 0 0 2pt; }
  ul.bul li.neg::marker { color: #b3261e; }
""".trimIndent()

private fun css(template: ReportTemplate): String = pageCss(footerTitle(template), REPORT_FONT) + "\n" + BODY_CSS + "\n" + DENSE_TABLE_CSS

// shared with NarrativeHtml.kt: same page, header and helpers, different body
internal fun reportCss(template: ReportTemplate): String = css(template)
internal fun reportHeaderHtml(template: ReportTemplate): String = headerHtml(template)
internal fun reportEsc(s: String?): String = esc(s)
internal fun reportFieldValueHtml(field: Field, rawValue: String?): String = fieldValueHtml(field, rawValue)

// pure fn: template + report data + {officerName, status, submittedAt?} -> full print HTML.
// mirrors web/src/lib/reporthtml.js's exported reportHtml(). caller renders this through
// pdf/BillPrinter.kt's renderBillPdf (same WebView print pipeline, any HTML string works).
// normalize()s the data first (spec: exports must show the derived/synced state, e.g. a
// perCourse item's derived overall answer and a stale linked card dropped), never the raw
// stored row, and never mutates the caller's ReportData (ReportData itself is immutable).
fun buildReportHtml(template: ReportTemplate, data: ReportData): String {
    val normalizedData = normalize(template, data)
    val answerMap = answerMapOf(template)
    // an optional section (K) nobody touched is left out of the report entirely
    val sections = template.sections
        .filter { !it.optional || sectionHasContent(it, normalizedData) }
        .joinToString("") { sectionHtml(it, normalizedData, template, answerMap) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(template.title)}</title>" +
        "<style>${css(template)}\n$SIGNOFF_CSS</style></head><body>" +
        headerHtml(template) + sections + signoffHtml(normalizedData) +
        "</body></html>"
}
