// filled visit-report printable HTML -- Kotlin port of web/src/lib/reporthtml.js, kept in
// lockstep with it (same CSS, same per-block rules) so the android and web PDFs of the same
// report look the same. Layout traces back to
// ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-report-template.html. unlike
// the blank paper form, this prints only GIVEN answers as coloured labels; blank checklist
// items print "Not answered"; empty remarks and unticked flags are left out. driven purely by
// the template's sections/blocks -- never hardcode a question here.
//
// pure string building, no android imports -- this file's output is a plain JVM unit test
// target (test/.../pdf/ReportHtmlTest.kt); the actual print-to-PDF still goes through
// pdf/BillPrinter.kt's WebView pipeline (renderBillPdf works for any HTML string, this one included).
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.CardsCompare
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.cardCompareMismatch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// what the header/footer needs beyond the template+data: who filed it and where it stands.
// status is the Report entity's own "draft"/"submitted" string.
data class ReportMeta(val officerName: String, val status: String, val submittedAt: String? = null)

// answer/choice colours -- MUST match ui/theme/Color.kt's Light* tone values and
// web/src/lib/reporthtml.js's TONE_COLOR exactly (three independent copies of the same four
// hex values by necessity: this file has no android/compose dependency, reporthtml.js has no
// kotlin dependency -- there is no single shared source they could both import from).
private val TONE_COLOR = mapOf(
    "yes" to "#1c6b38",
    "no" to "#b3261e",
    "partial" to "#8a4600",
    "na" to "#4c4f66",
)

private fun esc(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun blank(v: String?): Boolean = v == null || v.trim().isEmpty()

private fun escMultiline(s: String): String = esc(s).replace("\n", "<br>")

private fun answerMapOf(template: ReportTemplate): Map<String, AnswerOption> = template.answers.associateBy { it.id }

private fun toneSpan(tone: String, label: String): String {
    val color = TONE_COLOR[tone] ?: "#111"
    return "<span class=\"ans\" style=\"color:$color\">${esc(label)}</span>"
}

private const val NOT_ANSWERED = "<span class=\"not-answered\">Not answered</span>"

// text for one field's value: choice fields render as a coloured tone label (or "Not
// answered" when blank); every other kind renders as escaped plain/multiline text.
private fun fieldValueHtml(field: Field, rawValue: String?): String {
    if (field.kind == "choice") {
        if (blank(rawValue)) return NOT_ANSWERED
        val opt = field.choiceOptions().find { it.id == rawValue }
        return if (opt != null) toneSpan(opt.tone, opt.label) else esc(rawValue)
    }
    if (blank(rawValue)) return ""
    return escMultiline(rawValue!!)
}

private fun fieldsBlockHtml(block: ReportBlock.Fields, data: ReportData): String {
    val lines = block.fields.joinToString("") { f ->
        "<div class=\"line full\"><span class=\"label\">${esc(f.label)}</span>" +
            "<span class=\"value\">${fieldValueHtml(f, data.field(f.key))}</span></div>"
    }
    return "<div class=\"details\">$lines</div>"
}

private fun checklistBlockHtml(block: ReportBlock.Checklist, data: ReportData, answerMap: Map<String, AnswerOption>): String {
    val heading = block.heading?.let { "<h3>${esc(it)}</h3>" } ?: ""
    val rows = block.items.mapIndexed { i, item ->
        val answer = data.checkAnswer(item.id)
        val remarks = data.checkRemarks(item.id)
        val answerHtml = if (blank(answer)) {
            NOT_ANSWERED
        } else {
            answerMap[answer]?.let { toneSpan(it.tone, it.label) } ?: esc(answer)
        }
        val remarksHtml = if (blank(remarks)) "" else escMultiline(remarks)
        "<tr><td class=\"num\">${i + 1}</td><td class=\"question\">${esc(item.text)}</td>" +
            "<td class=\"ans-cell\">$answerHtml</td><td class=\"remarks\">$remarksHtml</td></tr>"
    }.joinToString("")
    return "$heading<table class=\"checklist\"><colgroup><col class=\"c-num\"><col class=\"c-question\">" +
        "<col class=\"c-ans\"><col class=\"c-remarks\"></colgroup>" +
        "<thead><tr><th>#</th><th>Item</th><th>Answer</th><th>Remarks</th></tr></thead><tbody>$rows</tbody></table>"
}

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun cardsBlockHtml(block: ReportBlock.Cards, data: ReportData): String {
    val entries = data.cards(block.key)
    if (entries.isEmpty()) return "<p class=\"empty\">No entries.</p>"
    return entries.mapIndexed { i, entry ->
        val titleValue = entry.stringOrNull(block.titleField)
        val caption = if (blank(titleValue)) {
            "${esc(block.itemLabel)} ${i + 1}"
        } else {
            "${esc(block.itemLabel)} ${i + 1}: ${esc(titleValue)}"
        }
        val compare = block.compare
        val mismatch = compare != null && cardCompareMismatch(compare, entry)
        val warning = if (compare != null && mismatch) "<div class=\"mismatch-msg\">${esc(compare.message)}</div>" else ""
        val rows = block.fields.joinToString("") { f ->
            "<tr><th>${esc(f.label)}</th><td>${fieldValueHtml(f, entry.stringOrNull(f.key))}</td></tr>"
        }
        "<table class=\"card${if (mismatch) " mismatch" else ""}\"><caption>$caption</caption><tbody>$rows</tbody></table>$warning"
    }.joinToString("")
}

private fun flagsBlockHtml(block: ReportBlock.Flags, data: ReportData): String {
    val ticked = data.flags()
    val items = block.items.filter { it.id in ticked }
    if (items.isEmpty()) return "<p class=\"none-ticked\">None ticked.</p>"
    return "<ul class=\"flags-list\">${items.joinToString("") { "<li>${esc(it.text)}</li>" }}</ul>"
}

private fun blockHtml(block: ReportBlock, data: ReportData, answerMap: Map<String, AnswerOption>): String = when (block) {
    is ReportBlock.Fields -> fieldsBlockHtml(block, data)
    is ReportBlock.Checklist -> checklistBlockHtml(block, data, answerMap)
    is ReportBlock.Cards -> cardsBlockHtml(block, data)
    is ReportBlock.Flags -> flagsBlockHtml(block, data)
}

private fun sectionHtml(section: ReportSection, data: ReportData, answerMap: Map<String, AnswerOption>): String {
    val note = section.note?.let { "<span class=\"note\">${esc(it)}</span>" } ?: ""
    val blocks = section.blocks.joinToString("") { blockHtml(it, data, answerMap) }
    return "<section class=\"keep\"><h2><span class=\"letter\">${esc(section.letter)}</span>${esc(section.title)}$note</h2>$blocks</section>"
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

// print CSS -- kept byte-identical in spirit to web/src/lib/reporthtml.js's CSS const (see
// that file's own comment for the page-geometry rationale); only given answers print, so the
// tick-box columns collapse into one coloured "Answer" column.
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
  h3 { margin: 4pt 0 2pt; font-size: 8.2pt; font-weight: 700; }

  .details { display: grid; grid-template-columns: 1fr 1fr; column-gap: 12pt; row-gap: 3pt; }
  .details .line { display: flex; align-items: baseline; gap: 4pt; min-height: 12pt; }
  .details .line.full { grid-column: 1 / -1; }
  .details .label { white-space: nowrap; font-weight: 700; }
  .details .label::after { content: ':'; }
  .details .value { flex: 1; border-bottom: 0.6pt solid #ccc; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  th, td { border: 0.6pt solid #666; padding: 2pt 3pt; vertical-align: middle; }
  th { background: #e6e6e6; font-weight: 700; font-size: 7.4pt; text-align: center; line-height: 1.15; }
  tr { break-inside: avoid; }
  td.num, th.num { width: 16pt; text-align: center; }

  .checklist col.c-num { width: 16pt; }
  .checklist col.c-question { width: 46%; }
  .checklist col.c-ans { width: 16%; }
  .checklist td { height: 14pt; }
  .checklist td.ans-cell { text-align: center; font-weight: 700; }
  .checklist td.num { color: #333; }
  .not-answered { color: #888; font-style: italic; font-weight: 400; }

  .card { margin: 2pt 0 4pt; }
  .card caption { text-align: left; font-weight: 700; font-size: 8pt; padding: 2pt 0; caption-side: top; }
  .card th { width: 32%; text-align: left; background: #f2f2f2; font-size: 7.6pt; }
  .card.mismatch { outline: 1pt solid #b3261e; }
  .mismatch-msg { color: #b3261e; font-weight: 700; font-size: 7.6pt; margin: -2pt 0 5pt; }
  p.empty, p.none-ticked { color: #666; font-style: italic; margin: 2pt 0 6pt; }

  .flags-list { columns: 2; column-gap: 14pt; margin: 0; padding: 0 0 4pt; list-style: none; }
  .flags-list li { padding: 1.8pt 0; break-inside: avoid; }
  .flags-list li::before { content: '\2713  '; font-weight: 700; color: #b3261e; }

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
fun buildReportHtml(template: ReportTemplate, data: ReportData, meta: ReportMeta): String {
    val answerMap = answerMapOf(template)
    val sections = template.sections.joinToString("") { sectionHtml(it, data, answerMap) }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(template.title)}</title>" +
        "<style>$CSS</style></head><body>" +
        headerHtml(template, meta) + sections +
        "<footer class=\"legend\">$LEGEND</footer>" +
        "</body></html>"
}
