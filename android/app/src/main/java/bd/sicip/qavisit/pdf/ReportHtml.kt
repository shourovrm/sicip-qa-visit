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

import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.CardsCompare
import bd.sicip.qavisit.domain.report.ChecklistItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.cardCompareMismatch
import bd.sicip.qavisit.domain.report.normalize
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// what the header/footer needs beyond the template+data: who filed it and where it stands.
// status is the Report entity's own "draft"/"submitted" string.
data class ReportMeta(val officerName: String, val status: String, val submittedAt: String? = null)

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

private data class LayoutColumn(val label: String, val weight: Int)

// checklist table: # | Item | Yes | No | Part | N/A | Remarks. Weights sum to 100, used
// directly as CSS % -- hand-kept copy of web/src/lib/reportlayout.js's CHECKLIST_COLUMNS.
private val CHECKLIST_COLUMNS = listOf(
    LayoutColumn("#", 4),
    LayoutColumn("Item", 44),
    LayoutColumn("Yes", 6),
    LayoutColumn("No", 6),
    LayoutColumn("Part", 6),
    LayoutColumn("N/A", 6),
    LayoutColumn("Remarks", 28),
)

// interview per-course sub-table (section I): Item | Yes | No | Part | N/A -- no #, no Remarks
// column (interview questions carry no per-question remarks, only a shared feedback field).
private val INTERVIEW_TICK_COLUMNS = listOf(
    LayoutColumn("Item", 76),
    LayoutColumn("Yes", 6),
    LayoutColumn("No", 6),
    LayoutColumn("Part", 6),
    LayoutColumn("N/A", 6),
)

// flags: narrow tick column + wide text column.
private val FLAGS_COLUMNS = listOf(LayoutColumn("", 6), LayoutColumn("", 94))

private fun esc(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun blank(v: String?): Boolean = v == null || v.trim().isEmpty()

private fun escMultiline(s: String): String = esc(s).replace("\n", "<br>")

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
private fun fieldValueHtml(field: Field, rawValue: String?): String {
    if (field.kind == "choice") {
        if (blank(rawValue)) return ""
        val opt = field.choiceOptions().find { it.id == rawValue }
        return if (opt != null) toneSpan(opt.tone, opt.label) else esc(rawValue)
    }
    if (blank(rawValue)) return ""
    return escMultiline(rawValue!!)
}

// short-kind fields print "label: value" on one line; longtext fields print the label above a
// bordered box (matches the paper form's ruled boxes for findings/instructions/address/...).
// blank value = an empty line/box, never placeholder text. `value` reads either a top-level
// ReportData.field(key) or a raw card JsonObject's field, whichever the caller has.
private fun fieldsListHtml(fields: List<Field>, value: (String) -> String?): String =
    fields.joinToString("") { f ->
        if (f.kind == "longtext") {
            val raw = value(f.key)
            val body = if (blank(raw)) "" else escMultiline(raw!!)
            "<div class=\"field-box\"><div class=\"label\">${esc(f.label)}</div><div class=\"box\">$body</div></div>"
        } else {
            "<div class=\"line\"><span class=\"label\">${esc(f.label)}</span><span class=\"value\">${fieldValueHtml(f, value(f.key))}</span></div>"
        }
    }

private fun fieldsBlockHtml(block: ReportBlock.Fields, data: ReportData): String =
    "<div class=\"details\">${fieldsListHtml(block.fields) { key -> data.field(key) }}</div>"

// courses with a non-blank name, in section-A order -- same list normalize()'s syncPerCourse
// splits per-course items by, and the source for the linked interview/attendance cards and the
// per-course summary line.
private fun courseList(data: ReportData): List<JsonObject> =
    data.cards("courses").filter { !blank(it.stringOrNull("course")) }

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
        val perCourseTag = if (item.perCourse && courses.size >= 2) " <span class=\"per-course-tag\">Per course</span>" else ""
        val itemHtml = "${esc(item.text)}$perCourseTag${perCourseLineHtml(item, data, courses, answerMap)}"
        val remarks = data.checkRemarks(item.id)
        val remarksHtml = if (blank(remarks)) "" else escMultiline(remarks)
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
        "<tr>${block.fields.joinToString("") { f -> "<td>${fieldValueHtml(f, entry.stringOrNull(f.key))}</td>" }}</tr>"
    }.joinToString("")
    var warning = ""
    if (mismatchedRows.isNotEmpty() && block.compare != null) {
        val which = if (mismatchedRows.size == entries.size) "" else " (row${if (mismatchedRows.size > 1) "s" else ""} ${mismatchedRows.joinToString(", ")})"
        warning = "<p class=\"mismatch-msg\">${esc(block.compare.message)}$which</p>"
    }
    return "$heading$note<table class=\"cards-table\"><thead><tr>$headerRow</tr></thead><tbody>$rows</tbody></table>$warning"
}

// linked cards block with display:"tabs" (section I "interviews"): tabs are an editor-only
// concept -- exports print one small table per course instead. Fields whose choice options are
// exactly the template's answer ids (e.g. q1-q7) render as a checklist-style tick sub-table;
// every other field (trainees_interviewed, tech_topic, tech_result, feedback) renders through
// the normal fields renderer. Linked fields (course/batch) are shown in the caption, not twice.
private fun tabsCardsBlockHtml(block: ReportBlock.Cards, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String {
    val entries = data.cards(block.key)
    if (entries.isEmpty()) return "<p class=\"empty\">Add courses in section A.</p>"
    val linkedKeys = block.linkFrom?.fields?.toSet() ?: emptySet()
    val tickFields = block.fields.filter { it.key !in linkedKeys && isStandardAnswerChoice(it, template) }
    val tickFieldKeys = tickFields.map { it.key }.toSet()
    val otherFields = block.fields.filter { it.key !in linkedKeys && it.key !in tickFieldKeys }
    val answerIds = template.answers.map { it.id }
    return entries.joinToString("") { entry ->
        val linkedFieldKeys = block.linkFrom?.fields ?: emptyList()
        val extra = linkedFieldKeys.filter { it != block.titleField }
            .mapNotNull { entry.stringOrNull(it) }.filter { !blank(it) }.joinToString(" ")
        val titleValue = entry.stringOrNull(block.titleField) ?: ""
        val caption = "${esc(titleValue)}${if (extra.isNotBlank()) " &middot; Batch ${esc(extra)}" else ""}"
        val tickRows = tickFields.joinToString("") { f ->
            val value = entry.stringOrNull(f.key)
            "<tr><td class=\"question\">${esc(f.label)}</td>${answerIds.joinToString("") { id -> tickCellHtml(value == id, answerMap[id]?.tone) }}</tr>"
        }
        val tickTable = if (tickFields.isNotEmpty()) {
            "<table class=\"checklist interview-ticks\">${colgroupHtml(INTERVIEW_TICK_COLUMNS)}${tableHeadHtml(INTERVIEW_TICK_COLUMNS)}<tbody>$tickRows</tbody></table>"
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

private fun blockHtml(block: ReportBlock, data: ReportData, template: ReportTemplate, answerMap: Map<String, AnswerOption>): String = when (block) {
    is ReportBlock.Fields -> fieldsBlockHtml(block, data)
    is ReportBlock.Checklist -> checklistBlockHtml(block, data, template, answerMap)
    is ReportBlock.Cards -> if (block.display == "tabs") tabsCardsBlockHtml(block, data, template, answerMap) else cardsBlockHtml(block, data)
    is ReportBlock.Flags -> "" // handled by sectionHtml's combinedFlagsHtml, alongside any countsAsFlags cards block in the same section
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
            blockHtml(b, data, template, answerMap)
        }
    }
    return "<section class=\"keep\"><h2><span class=\"letter\">${esc(section.letter)}</span>${esc(section.title)}$optionalTag$note</h2>$blocks</section>"
}

private fun statusLabel(status: String): String = if (status == "submitted") "Submitted" else "Draft"

private fun metaHtml(meta: ReportMeta): String {
    val status = statusLabel(meta.status)
    val submitted = if (meta.status == "submitted" && !blank(meta.submittedAt)) {
        " &middot; submitted ${esc(meta.submittedAt)}"
    } else {
        ""
    }
    return "<div class=\"meta\">Officer: ${esc(meta.officerName)} &middot; $status$submitted</div>"
}

private fun headerHtml(template: ReportTemplate, meta: ReportMeta): String = """
    <header>
      <div>
        <div class="program">${esc(template.program)}</div>
        <h1>${esc(template.title)}</h1>
        ${metaHtml(meta)}
      </div>
      <div class="form-code">${esc(template.subtitle)}</div>
    </header>
""".trimIndent()

// print CSS -- A4 portrait, margins top 10mm/sides 11mm/bottom 12mm (paper form geometry), page
// numbers via @page counters. Kept in lockstep with web/src/lib/reporthtml.js's CSS const.
private val CSS = """
  @page {
    size: A4 portrait;
    margin: 10mm 11mm 12mm;
    @bottom-left { content: "SICIP Surprise Visit Report"; font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "Noto Sans", "Segoe UI", Arial, sans-serif; font-size: 8.4pt; line-height: 1.25; color: #111; }

  header { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 1.6pt solid #111; padding-bottom: 4pt; margin-bottom: 6pt; }
  header .program { font-size: 8pt; }
  header h1 { margin: 1pt 0 0; font-size: 13pt; font-weight: 700; letter-spacing: 0.01em; }
  header .form-code { font-size: 7.5pt; text-align: right; color: #333; }
  .meta { margin-top: 2pt; font-size: 7.5pt; color: #333; }

  h2 { display: flex; align-items: baseline; gap: 5pt; margin: 8pt 0 3pt; font-size: 9.2pt; font-weight: 700; break-after: avoid; }
  h2 .letter { display: inline-block; min-width: 13pt; padding: 0.5pt 0; text-align: center; background: #111; color: #fff; font-size: 8pt; }
  h2 .note { margin-left: auto; font-weight: 400; font-size: 7.4pt; font-style: italic; color: #333; }
  h2 .optional-tag { font-weight: 700; font-size: 6.6pt; text-transform: uppercase; letter-spacing: 0.03em; color: #8a4600; border: 0.6pt solid #8a4600; border-radius: 3pt; padding: 0.5pt 3pt; }
  h3 { margin: 4pt 0 2pt; font-size: 8.2pt; font-weight: 700; }

  .details { margin: 0 0 2pt; }
  .details .line { display: flex; align-items: baseline; gap: 4pt; min-height: 12pt; padding: 0.5pt 0; }
  .details .label { white-space: nowrap; font-weight: 700; }
  .details .label::after { content: ':'; }
  .details .value { flex: 1; border-bottom: 0.6pt solid #ccc; }
  .field-box { margin: 2pt 0 5pt; }
  .field-box .label { font-weight: 700; display: block; margin-bottom: 1pt; }
  .field-box .box { border: 0.6pt solid #666; min-height: 20pt; padding: 2pt 3pt; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  th, td { border: 0.6pt solid #666; padding: 2pt 3pt; vertical-align: middle; }
  th { background: #e6e6e6; font-weight: 700; font-size: 7.4pt; text-align: center; line-height: 1.15; }
  tr { break-inside: avoid; }
  td.num, th.num { text-align: center; }

  .checklist td { height: 14pt; }
  .checklist td.num { color: #333; text-align: center; }
  .checklist td.question { text-align: left; }
  .per-course-line { font-size: 6.8pt; font-weight: 400; color: #444; margin-top: 1pt; }
  .per-course-tag { font-weight: 700; font-size: 6.2pt; text-transform: uppercase; letter-spacing: 0.02em; color: #4c4f66; border: 0.6pt solid #4c4f66; border-radius: 3pt; padding: 0 2pt; margin-left: 3pt; }

  .tick-cell { text-align: center; }
  .tick { font-size: 9pt; font-weight: 700; }

  .cards-table th { font-size: 7pt; }
  .cards-table td { font-size: 7.6pt; }
  .mismatch-msg { color: #b3261e; font-weight: 700; font-size: 7.6pt; margin: -2pt 0 5pt; }
  p.empty, .block-note { color: #666; font-style: italic; margin: 2pt 0 6pt; font-size: 7.4pt; }

  .flags-table td.flag-text { text-align: left; }
  .flags-table td.flag-text.checked { font-weight: 700; color: #b3261e; }

  .interview-card { margin-bottom: 6pt; break-inside: avoid; }
  .interview-ticks td.question { text-align: left; }

  .ans { font-weight: 700; }

  .keep { break-inside: avoid; }
  footer.legend { margin-top: 6pt; font-size: 7pt; color: #333; }
""".trimIndent()

private const val LEGEND = "T = total, F = female, TMS = Training Management System, TDP = training delivery plan, " +
    "CS = competency standard, CBLM = competency-based learning material, PPE = personal protective equipment, " +
    "OHS = occupational health and safety."

// pure fn: template + report data + {officerName, status, submittedAt?} -> full print HTML.
// mirrors web/src/lib/reporthtml.js's exported reportHtml(). caller renders this through
// pdf/BillPrinter.kt's renderBillPdf (same WebView print pipeline, any HTML string works).
// normalize()s the data first (spec: exports must show the derived/synced state, e.g. a
// perCourse item's derived overall answer and a stale linked card dropped), never the raw
// stored row, and never mutates the caller's ReportData (ReportData itself is immutable).
fun buildReportHtml(template: ReportTemplate, data: ReportData, meta: ReportMeta): String {
    val normalizedData = normalize(template, data)
    val answerMap = answerMapOf(template)
    val sections = template.sections.joinToString("") { sectionHtml(it, normalizedData, template, answerMap) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(template.title)}</title>" +
        "<style>$CSS</style></head><body>" +
        headerHtml(template, meta) + sections +
        "<footer class=\"legend\">$LEGEND</footer>" +
        "</body></html>"
}
