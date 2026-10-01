// QA Monitoring Visit Report (Annex-3) printable HTML -- QA report spec §7. A separate layout
// from pdf/ReportHtml.kt's surprise-visit report: this one follows the paper Annex-3 form
// heading-by-heading and table-by-table, adding exactly one column (REMARKS) to every criteria
// table. Mirrors web/src/lib/qareporthtml.js (the other agent's own port of this same spec
// section) -- kept in lockstep the same way pdf/ReportHtml.kt and reporthtml.js are: same CSS,
// same structure, hand-kept copies since there is no shared module either platform can import.
//
// pure string building, no android imports -- unit tested by test/.../pdf/QaReportHtmlTest.kt.
// The actual print-to-PDF goes through pdf/BillPrinter.kt's WebView pipeline, same as every other
// report PDF in this app (ui/reports/ReportPdf.kt's shareReportPdf picks this builder over
// pdf/ReportHtml.kt's when template.id == "qa").
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.CriteriaItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.normalize
import bd.sicip.qavisit.domain.report.printedRemarks
import bd.sicip.qavisit.domain.report.shown
import bd.sicip.qavisit.domain.report.shownFor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun esc(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun blank(v: String?): Boolean = v == null || v.trim().isEmpty()

private fun escMultiline(s: String): String = esc(s).replace("\n", "<br>")

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

// "from dd/mm/yyyy to dd/mm/yyyy" out of the visit's own start/end (yyyy-mm-dd, this app's
// storage format) -- Annex-3's exact wording (spec §7's header line example).
private fun ddmmyyyy(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size != 3) return isoDate
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

// print CSS -- Annex-3 table structure in the surprise reports' type scale (spec 2026-10-01 §5):
// Arial, body 10 pt, tables 9 pt, section heading 11 pt, sub-heading 10 pt, title 15 pt.
// page geometry and "<title> · Page X of Y" footer from ReportHtml.kt's pageCss.
private val BODY_CSS = """
  * { box-sizing: border-box; }
  body { margin: 0; font-family: $REPORT_FONT; font-size: 10pt; line-height: 1.3; color: #000; background: #fff; }

  .annex { text-align: right; font-weight: 700; }
  .program { text-align: center; font-size: 9pt; margin-top: 2pt; }
  .title { text-align: center; font-weight: 700; font-size: 15pt; margin: 2pt 0 8pt; padding-bottom: 4pt; border-bottom: 1.6pt solid #000; }

  .kv { margin: 0 0 8pt; }
  .kv div { margin: 1pt 0; }
  .kv b { font-weight: 700; }

  h2.sh { font-weight: 700; text-transform: uppercase; font-size: 11pt; margin: 12pt 0 4pt; break-after: avoid; }
  p.intro { font-style: italic; margin: 0 0 4pt; }
  h3 { font-weight: 700; font-size: 10pt; margin: 6pt 0 3pt; break-after: avoid; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; margin-bottom: 6pt; }
  th, td { border: 0.6pt solid #000; padding: 4pt 6pt; vertical-align: top; font-size: 9pt; text-align: left; }
  th { font-weight: 700; text-align: center; background: #e6e6e6; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }
  td.sl { text-align: center; }
  td.c { text-align: center; }
  td.heading-row { font-weight: 700; }

  ul.remarks { margin: 0; padding-left: 14pt; }
  ul.remarks li { margin: 0 0 2pt; }

  .field-line { margin: 1pt 0; }
  .field-line b { font-weight: 700; }
  .field-box { margin: 3pt 0 6pt; }
  .field-box .lbl { font-weight: 700; }
  .field-box .box { border: 0.6pt solid #000; min-height: 22pt; padding: 4pt 6pt; margin-top: 2pt; }

  ul.points, ol.points { margin: 2pt 0; padding-left: 16pt; }
  ol.points li { margin: 0 0 2pt; }
""".trimIndent()

private fun css(template: ReportTemplate): String = pageCss(footerTitle(template), REPORT_FONT) + "\n" + BODY_CSS

// header block: top-right "Annex-3", centred bold program line, centred bold title, then the
// fixed key/value lines exactly as Annex-3 prints them (spec §7).
private fun headerHtml(template: ReportTemplate, data: ReportData): String {
    // "Annex-3" top right on v1 only; qa-v2 keeps officers as cards
    val annex = template.annex?.let { "<div class=\"annex\">${esc(it)}</div>" } ?: ""
    val officers = if (data.cards("officers").isNotEmpty()) officersLine(data) else data.field("officers")
    val dateFrom = data.field("date_from")
    val dateTo = data.field("date_to")
    val dates = if (!blank(dateFrom) && !blank(dateTo)) {
        "from ${ddmmyyyy(dateFrom)} to ${ddmmyyyy(dateTo)}"
    } else {
        ""
    }
    return """
      $annex
      <div class="program">${esc(template.program)}</div>
      <div class="title">${esc(template.title)}</div>
      <div class="kv">
        <div><b>Name of Training TI/TC Visited :</b> ${esc(data.field("ti_name"))}</div>
        <div><b>Name of the Association/Provider :</b> ${esc(data.field("provider"))}</div>
        <div><b>Date(s) of Visit :</b> ${esc(dates)}</div>
        <div><b>Name(s) &amp; Designation(s) of Visiting Officer(s) :</b> ${esc(officers)}</div>
      </div>
    """.trimIndent()
}

// one criteria table (spec §7): Sl. | QUALITY CRITERIA | EVIDENCE | REMARKS, widths ~7/31/31/31%.
// a heading item (no options) is one row with its Sl + text spanning the other three columns; a
// real item's REMARKS cell is domain/report/Remarks.kt's printedRemarks as a bulleted list, blank
// when there's nothing to print (never a placeholder).
// PrintTable (QaTables.kt) -> heading + grid table; null -> nothing
private fun tableHtml(table: PrintTable?, heading: String? = table?.heading): String {
    if (table == null) return ""
    val head = table.headers.joinToString("") { "<th>${esc(it)}</th>" }
    val body = table.rows.joinToString("") { row ->
        val cells = row.mapIndexed { index, cell ->
            val cellClass = if (index in table.centred) " class=\"c\"" else ""
            "<td$cellClass>${dashIfBlank(escMultiline(cell))}</td>"
        }
        "<tr>${cells.joinToString("")}</tr>"
    }
    return (heading?.let { "<h3>${esc(it)}</h3>" } ?: "") + "<table><thead><tr>$head</tr></thead><tbody>$body</tbody></table>"
}

private fun criteriaBlockHtml(block: ReportBlock.Criteria, data: ReportData, template: ReportTemplate): String {
    val rows = block.items.joinToString("") { item ->
        if (item.heading) {
            "<tr><td class=\"sl\">${esc(item.no)}</td><td class=\"heading-row\" colspan=\"3\">${esc(item.text)}</td></tr>"
        } else {
            // qa-v2: the numbered evidence the officer saw; v1: the form's own evidence text
            val evidence = if (template.evidenceRegister) evidenceLines(data, item.id).joinToString("\n") else item.evidence.orEmpty()
            val evidenceHtml = escMultiline(evidence)
            val remarksHtml = remarksListHtml(item, data)
            "<tr><td class=\"sl\">${esc(item.no)}</td><td>${esc(item.text)}</td><td>${dashIfBlank(evidenceHtml)}</td><td>${dashIfBlank(remarksHtml)}</td></tr>"
        }
    }
    val intro = block.intro?.takeIf { it.isNotBlank() }?.let { "<p class=\"intro\">(${esc(it)})</p>" } ?: ""
    return intro +
        "<table><colgroup><col style=\"width:7%\"><col style=\"width:31%\"><col style=\"width:31%\"><col style=\"width:31%\"></colgroup>" +
        "<thead><tr><th>Sl.</th><th>QUALITY CRITERIA</th><th>EVIDENCE</th><th>REMARKS</th></tr></thead>" +
        "<tbody>$rows</tbody></table>"
}

private fun remarksListHtml(item: CriteriaItem, data: ReportData): String {
    val bullets = printedRemarks(item, data)
    if (bullets.isEmpty()) return ""
    return "<ul class=\"remarks\">${bullets.joinToString("") { "<li>${esc(it)}</li>" }}</ul>"
}

// section 1's fields/cards, and the conclusions sections (11-13's cards/fields, 14/15's
// longtext) -- a generic best-effort renderer (label: value lines, longtext as a bulleted list of
// its own non-blank lines, cards as one small table per block) rather than Annex-3's exact
// hand-tuned sub-tables (1.10-1.62's own column layouts; 11-13 + 16 now print their own tables,
// see QaConclusionsHtml.kt): those
// depend on the real qa-v1.json's exact field/card keys, which don't exist yet at the time this
// was written (see this agent's own build note in DECISIONS.md). Generic rendering still prints
// every value the officer entered, in template order, losing only the paper form's exact spacing
// -- reasonable given the template it must match isn't authored yet.
private fun fieldsBlockHtml(block: ReportBlock.Fields, data: ReportData, template: ReportTemplate): String {
    if (block.pairs.isNotEmpty()) return strengthsWeaknessesHtml(block, data) // s13
    // qa-v2 i) status: BTEB/NSDA as one table, then any other registration
    if (block.fields.any { it.key == "bteb_registered" }) {
        val other = data.field("other_registration")
        return "<div class=\"field-line\"><b>${esc(block.heading.orEmpty())} :</b></div>" + tableHtml(registrationTable(template, data), null) +
            (if (blank(other)) "" else "<div class=\"field-line\">Other registration: ${escMultiline(other)}</div>")
    }
    // qa-v2 1.30: the yes/N/A answer on the heading line (its table prints with the course rows)
    val contractAnswer = block.fields.find { it.key == "other_contract" }
    if (contractAnswer != null) {
        return "<h3>${esc(block.heading.orEmpty())}: ${esc(shownValue(contractAnswer, data.field("other_contract")))}</h3>"
    }
    val heading = block.heading?.let { "<h3>${esc(it)}</h3>" } ?: ""
    return heading + block.fields.filter { field -> field.showIf.shown { data.field(it) } }
        .joinToString("") { field -> fieldHtml(field) { data.field(it) } }
}

private fun fieldHtml(field: Field, value: (String) -> String): String {
    val raw = value(field.key)
    if (field.kind == "longtext") {
        // findings (s14) / recommendations (s15): always a real numbered list
        if (field.draftFrom != null && !blank(raw)) {
            val points = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            return "<div class=\"field-box\"><div class=\"lbl\">${esc(field.label)}</div><ol class=\"points\">${points.joinToString("") { "<li>${esc(it)}</li>" }}</ol></div>"
        }
        if (blank(raw)) return "<div class=\"field-box\"><div class=\"lbl\">${esc(field.label)}</div><div class=\"box\"></div></div>"
        // "one point per line" fields (spec §2's findings/recommendations/dropout-reasons/steps)
        // print as a bulleted list when they have more than one non-blank line, a plain box
        // otherwise -- either way every line the officer wrote prints, nothing collapsed away.
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val body = if (lines.size > 1) "<ul class=\"points\">${lines.joinToString("") { "<li>${esc(it)}</li>" }}</ul>" else escMultiline(raw)
        return "<div class=\"field-box\"><div class=\"lbl\">${esc(field.label)}</div><div class=\"box\">$body</div></div>"
    }
    return "<div class=\"field-line\"><b>${esc(field.label)} :</b> ${esc(raw)}</div>"
}

private fun cardsBlockHtml(block: ReportBlock.Cards, data: ReportData, template: ReportTemplate): String {
    if (block.anonymous) return feedbackTablesHtml(block, data) // s11/s12
    if (block.draftFrom != null) return planTableHtml(block, data) // s16
    // qa-v2 tables (QaTables.kt, same rows as the web PDF/Word)
    when (block.key) {
        "officers" -> return "<div class=\"field-line\"><b>iii) Monitoring Team Members with Designations :</b> ${esc(officersLine(data))}</div>"
        "mous" -> return tableHtml(mouTable(data), block.heading).ifEmpty { "<h3>${esc(block.heading.orEmpty())}</h3>" }
        "contracts" -> return "" // printed with the course rows below
        "contract_courses" -> return tableHtml(contractsTable(template, data), null)
        "selection", "rooms", "damaged" -> return tableHtml(cardsTable(block, data))
    }
    val cards = data.cards(block.key)
    if (cards.isEmpty()) return ""
    val headerCells = block.fields.joinToString("") { "<th>${esc(it.label)}</th>" }
    val rows = cards.joinToString("") { card ->
        val cells = block.fields.joinToString("") { f -> "<td${cellClassAttr(f)}>${dashIfBlank(esc(card.stringOrNull(f.key)))}</td>" }
        "<tr>$cells</tr>"
    }
    return "<table><thead><tr>$headerCells</tr></thead><tbody>$rows</tbody></table>"
}

private fun blockHtml(block: ReportBlock, data: ReportData, template: ReportTemplate): String = when {
    !block.shownFor(data) -> "" // qa-v2 showIf (1.30 contracts while the answer isn't Yes)
    else -> blockBodyHtml(block, data, template)
}

private fun blockBodyHtml(block: ReportBlock, data: ReportData, template: ReportTemplate): String = when (block) {
    is ReportBlock.Criteria -> criteriaBlockHtml(block, data, template)
    is ReportBlock.Fields -> fieldsBlockHtml(block, data, template)
    is ReportBlock.Cards -> cardsBlockHtml(block, data, template)
    // qa-v1.json has neither of these block types (spec §2) -- nothing to print if one ever
    // sneaks in, rather than guessing at a layout for it.
    is ReportBlock.Checklist -> ""
    is ReportBlock.Flags, is ReportBlock.Remarks, is ReportBlock.Findings -> ""
}

private fun sectionHtml(section: ReportSection, data: ReportData, template: ReportTemplate): String {
    val heading = "<h2 class=\"sh\">${esc(section.badge)}. ${esc(section.title.uppercase())}</h2>"
    val blocks = section.blocks.joinToString("") { blockHtml(it, data, template) }
    return heading + blocks
}

// pure fn: template + report data -> full print HTML, Annex-3 layout (spec §7). Only ever called
// for template.id == "qa" (ui/reports/ReportPdf.kt's shareReportPdf branches on that); a surprise
// report never reaches this file.
fun buildQaReportHtml(template: ReportTemplate, data: ReportData): String {
    val normalizedData = normalize(template, data)
    val sections = template.sections.joinToString("") { sectionHtml(it, normalizedData, template) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(template.title)}</title>" +
        "<style>${css(template)}\n$SIGNOFF_CSS</style></head><body>" +
        headerHtml(template, normalizedData) + sections + tableHtml(evidenceIndexTable(template, normalizedData)) +
        signoffHtml(normalizedData) +
        "</body></html>"
}
