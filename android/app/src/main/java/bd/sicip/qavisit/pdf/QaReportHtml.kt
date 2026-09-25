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

// print CSS -- A4 portrait, Times New Roman 11pt, black on white, thin borders (spec §7), page
// numbers via @page counters, footer text fixed per spec ("SICIP Quality Assurance Visit Report").
private val CSS = """
  @page {
    size: A4 portrait;
    margin: 10mm 11mm 12mm;
    @bottom-left { content: "SICIP Quality Assurance Visit Report"; font: 8pt "Times New Roman", Times, serif; color: #333; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 8pt "Times New Roman", Times, serif; color: #333; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "Times New Roman", Times, serif; font-size: 11pt; line-height: 1.3; color: #000; background: #fff; }

  .annex { text-align: right; font-weight: 700; }
  .program { text-align: center; font-weight: 700; margin-top: 2pt; }
  .title { text-align: center; font-weight: 700; font-size: 13pt; margin: 2pt 0 8pt; }

  .kv { margin: 0 0 8pt; }
  .kv div { margin: 1pt 0; }
  .kv b { font-weight: 700; }

  h2.sh { font-weight: 700; text-transform: uppercase; font-size: 11.5pt; margin: 10pt 0 2pt; }
  p.intro { font-style: italic; margin: 0 0 4pt; }
  h3 { font-weight: 700; font-size: 11pt; margin: 6pt 0 2pt; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; margin-bottom: 6pt; }
  th, td { border: 0.75pt solid #000; padding: 3pt 4pt; vertical-align: top; font-size: 10.5pt; }
  th { font-weight: 700; text-align: center; }
  td.sl { text-align: center; }
  td.heading-row { font-weight: 700; }

  ul.remarks { margin: 0; padding-left: 14pt; }
  ul.remarks li { margin: 0 0 2pt; }

  .field-line { margin: 1pt 0; }
  .field-line b { font-weight: 700; }
  .field-box { margin: 2pt 0 6pt; }
  .field-box .lbl { font-weight: 700; }
  .field-box .box { border: 0.75pt solid #000; min-height: 16pt; padding: 2pt 4pt; margin-top: 1pt; }

  ul.points { margin: 2pt 0; padding-left: 16pt; }

  .sign { display: flex; justify-content: space-between; margin-top: 16pt; }
  .sign .officers div { margin: 2pt 0; }
""".trimIndent()

// header block: top-right "Annex-3", centred bold program line, centred bold title, then the
// fixed key/value lines exactly as Annex-3 prints them (spec §7).
private fun headerHtml(template: ReportTemplate, data: ReportData): String {
    val annex = template.annex ?: "Annex-3"
    val dateFrom = data.field("date_from")
    val dateTo = data.field("date_to")
    val dates = if (!blank(dateFrom) && !blank(dateTo)) {
        "from ${ddmmyyyy(dateFrom)} to ${ddmmyyyy(dateTo)}"
    } else {
        ""
    }
    return """
      <div class="annex">${esc(annex)}</div>
      <div class="program">${esc(template.program)}</div>
      <div class="title">${esc(template.title)}</div>
      <div class="kv">
        <div><b>Name of Training TI/TC Visited :</b> ${esc(data.field("ti_name"))}</div>
        <div><b>Name of the Association/Provider :</b> ${esc(data.field("provider"))}</div>
        <div><b>Date(s) of Visit :</b> ${esc(dates)}</div>
        <div><b>Name(s) &amp; Designation(s) of Visiting Officer(s) :</b> ${esc(data.field("officers"))}</div>
      </div>
    """.trimIndent()
}

// one criteria table (spec §7): Sl. | QUALITY CRITERIA | EVIDENCE | REMARKS, widths ~7/31/31/31%.
// a heading item (no options) is one row with its Sl + text spanning the other three columns; a
// real item's REMARKS cell is domain/report/Remarks.kt's printedRemarks as a bulleted list, blank
// when there's nothing to print (never a placeholder).
private fun criteriaBlockHtml(block: ReportBlock.Criteria, data: ReportData): String {
    val rows = block.items.joinToString("") { item ->
        if (item.heading) {
            "<tr><td class=\"sl\">${esc(item.no)}</td><td class=\"heading-row\" colspan=\"3\">${esc(item.text)}</td></tr>"
        } else {
            val evidenceHtml = escMultiline(item.evidence ?: "")
            val remarksHtml = remarksListHtml(item, data)
            "<tr><td class=\"sl\">${esc(item.no)}</td><td>${esc(item.text)}</td><td>$evidenceHtml</td><td>$remarksHtml</td></tr>"
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
// hand-tuned sub-tables (1.10-1.62's own column layouts, 13's 9 fixed component rows): those
// depend on the real qa-v1.json's exact field/card keys, which don't exist yet at the time this
// was written (see this agent's own build note in DECISIONS.md). Generic rendering still prints
// every value the officer entered, in template order, losing only the paper form's exact spacing
// -- reasonable given the template it must match isn't authored yet.
private fun fieldsBlockHtml(block: ReportBlock.Fields, data: ReportData): String {
    val heading = block.heading?.let { "<h3>${esc(it)}</h3>" } ?: ""
    return heading + block.fields.joinToString("") { field -> fieldHtml(field) { data.field(it) } }
}

private fun fieldHtml(field: Field, value: (String) -> String): String {
    val raw = value(field.key)
    if (field.kind == "longtext") {
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

private fun cardsBlockHtml(block: ReportBlock.Cards, data: ReportData): String {
    val cards = data.cards(block.key)
    if (cards.isEmpty()) return ""
    val headerCells = block.fields.joinToString("") { "<th>${esc(it.label)}</th>" }
    val rows = cards.joinToString("") { card ->
        val cells = block.fields.joinToString("") { f -> "<td>${esc(card.stringOrNull(f.key))}</td>" }
        "<tr>$cells</tr>"
    }
    return "<table><thead><tr>$headerCells</tr></thead><tbody>$rows</tbody></table>"
}

private fun blockHtml(block: ReportBlock, data: ReportData): String = when (block) {
    is ReportBlock.Criteria -> criteriaBlockHtml(block, data)
    is ReportBlock.Fields -> fieldsBlockHtml(block, data)
    is ReportBlock.Cards -> cardsBlockHtml(block, data)
    // qa-v1.json has neither of these block types (spec §2) -- nothing to print if one ever
    // sneaks in, rather than guessing at a layout for it.
    is ReportBlock.Checklist -> ""
    is ReportBlock.Flags -> ""
}

private fun sectionHtml(section: ReportSection, data: ReportData): String {
    val heading = "<h2 class=\"sh\">${esc(section.badge)}. ${esc(section.title.uppercase())}</h2>"
    val blocks = section.blocks.joinToString("") { blockHtml(it, data) }
    return heading + blocks
}

// spec §7's signature block: one row per officer named in the header's "officers" field
// (newline- or semicolon-separated, same free text the header line already prints), left column
// names+designations, right column a dotted signature line each.
private fun signatureHtml(data: ReportData): String {
    val officers = data.field("officers").split(Regex("[\n;]")).map { it.trim() }.filter { it.isNotBlank() }
    if (officers.isEmpty()) return ""
    val names = officers.mapIndexed { i, name -> "<div>${i + 1}) ${esc(name)}</div>" }.joinToString("")
    val lines = officers.joinToString("") { "<div>&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;&hellip;</div>" }
    return """
      <div class="sign">
        <div class="officers"><b>Name (s) of the Visiting Officer(s) with designation</b>$names</div>
        <div style="text-align:right"><b>Signatures</b>$lines</div>
      </div>
    """.trimIndent()
}

// pure fn: template + report data -> full print HTML, Annex-3 layout (spec §7). Only ever called
// for template.id == "qa" (ui/reports/ReportPdf.kt's shareReportPdf branches on that); a surprise
// report never reaches this file.
fun buildQaReportHtml(template: ReportTemplate, data: ReportData): String {
    val normalizedData = normalize(template, data)
    val sections = template.sections.joinToString("") { sectionHtml(it, normalizedData) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(template.title)}</title>" +
        "<style>$CSS</style></head><body>" +
        headerHtml(template, normalizedData) + sections + signatureHtml(normalizedData) +
        "</body></html>"
}
