// QA print, conclusions sections (spec 2026-09-26 §5), mirrors web/src/lib/qareporthtml.js +
// feedbackgrid.js: s11/s12 anonymous feedback tables, s13 strengths/weaknesses by block.pairs,
// s16 improvement plan. Pure string building; called from QaReportHtml.kt.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// max respondents per table (A4 width), min columns so a blank form still has room
private const val PER_TABLE = 4
private const val MIN_COLUMNS = 2
private const val MIN_PLAN_ROWS = 3

private fun escText(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    .replace("\n", "<br>")

// table cells: blank prints "-"
private fun escCell(s: String?): String = dashIfBlank(escText(s))

private fun JsonObject?.value(key: String): String = this?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

// choice id -> its label ("no" -> "No"); blank stays blank
private fun answerLabel(field: Field, value: String): String {
    if (value.isBlank() || field.kind != "choice") return value.trim()
    return field.choiceOptions().firstOrNull { it.id == value }?.label ?: value
}

// questions down, "Trainee 1..N" across -- never a name (old cards may still carry one)
fun feedbackTablesHtml(block: ReportBlock.Cards, data: ReportData): String {
    val cards = data.cards(block.key)
    val respondentCount = maxOf(cards.size, MIN_COLUMNS)
    val names = (1..respondentCount).map { "${block.itemLabel} $it" }
    val rowFields = block.fields.filter { it.kind != "longtext" }
    val commentFields = block.fields.filter { it.kind == "longtext" }

    val tables = (0 until respondentCount step PER_TABLE).joinToString("") { start ->
        val group = (start until minOf(start + PER_TABLE, respondentCount)).toList()
        val header = "<tr><th>Question</th>" + group.joinToString("") { "<th>${escText(names[it])}</th>" } + "</tr>"
        val rows = rowFields.joinToString("") { field ->
            val cells = group.joinToString("") { i -> "<td class=\"c\">${escCell(answerLabel(field, cards.getOrNull(i).value(field.key)))}</td>" }
            "<tr><td>${escCell(field.label)}</td>$cells</tr>"
        }
        // question column wide, respondents share the rest; no header word breaks (web feedback grid)
        val headers = listOf("Question") + group.map { names[it] }
        val bodyRows = rowFields.map { field -> listOf(field.label) + group.map { i -> answerLabel(field, cards.getOrNull(i).value(field.key)) } }
        val layout = tableColumnLayout(headers, bodyRows, listOf(61.0) + group.map { 13.0 })
        "${tableOpenHtml("grid", layout)}<thead>$header</thead><tbody>$rows</tbody></table>"
    }

    val comments = cards.flatMapIndexed { i, card ->
        commentFields.mapNotNull { field ->
            card.value(field.key).trim().takeIf { it.isNotEmpty() }?.let { "${names[i]}: $it" }
        }
    }
    val commentsHtml = if (comments.isEmpty()) "" else
        "<h3>Comments</h3><ul class=\"points\">" + comments.joinToString("") { "<li>${escText(it)}</li>" } + "</ul>"
    return tables + commentsHtml
}

// s13: one row per template pair, component name from the pair (not the field label)
fun strengthsWeaknessesHtml(block: ReportBlock.Fields, data: ReportData): String {
    val rows = block.pairs.mapIndexed { i, pair ->
        "<tr><td class=\"sl\">${i + 1}.</td><td>${escCell(pair.component)}</td>" +
            "<td>${escCell(data.field(pair.strength))}</td><td>${escCell(data.field(pair.weakness))}</td></tr>"
    }.joinToString("")
    val bodyRows = block.pairs.mapIndexed { i, pair -> listOf("${i + 1}.", pair.component, data.field(pair.strength), data.field(pair.weakness)) }
    val layout = tableColumnLayout(listOf("S.N.", "Component", "Strengths", "Weakness"), bodyRows, listOf(6.0, 24.0, 35.0, 35.0))
    return tableOpenHtml("grid", layout) +
        "<thead><tr><th>S.N.</th><th>Component</th><th>Strengths</th><th>Weakness</th></tr></thead><tbody>$rows</tbody></table>"
}

// s16: S.N. | Weakness | Improvement action | Responsible | Timeline, blank rows up to 3
fun planTableHtml(block: ReportBlock.Cards, data: ReportData): String {
    val cards = data.cards(block.key)
    val rowCount = maxOf(cards.size, MIN_PLAN_ROWS)
    val header = "<tr><th style=\"width:7%\">S.N.</th>" + block.fields.joinToString("") { "<th>${escText(it.label)}</th>" } + "</tr>"
    val rows = (0 until rowCount).joinToString("") { i ->
        val card = cards.getOrNull(i)
        "<tr><td class=\"sl\">${i + 1}.</td>" + block.fields.joinToString("") { "<td>${escCell(card.value(it.key))}</td>" } + "</tr>"
    }
    return "<table><thead>$header</thead><tbody>$rows</tbody></table>"
}
