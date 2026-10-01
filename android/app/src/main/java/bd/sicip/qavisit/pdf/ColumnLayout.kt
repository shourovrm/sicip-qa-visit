// table column widths that never break a word (port of web/src/lib/reportlayout.js columnLayout,
// 2026-10-02): a column gets at least the width of its longest header word and its longest short
// body word; a table that cannot fit that with the 9 pt header turns `dense` (8 pt header, 3 pt
// side padding, DENSE_TABLE_CSS). Widths are estimated from average Arial glyph widths.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.Field

// A4 595.3 pt minus 2 x 54 pt (19.05 mm) side margins, see pageCss
private const val CONTENT_WIDTH_PT = 487.3
private const val BOLD_EM = 0.6
private const val REGULAR_EM = 0.56
private const val BODY_PT = 9.0
private const val MAX_TOKEN_CHARS = 18 // "Entrepreneurship" fits; anything longer may still wrap

data class TableColumn(val label: String, val texts: List<String>, val weight: Double)

// percents = column widths in % of the content width (sum 100)
data class ColumnLayout(val percents: List<Double>, val dense: Boolean)

// 8 pt header + narrow padding for crowded tables (same as web DENSE_TABLE_CSS)
internal val DENSE_TABLE_CSS = """
  table.dense th { font-size: 8pt; }
  table.dense th, table.dense td { padding-left: 3pt; padding-right: 3pt; }
""".trimIndent()

private fun longestWord(text: String): Int = text.split(Regex("\\s+")).maxOfOrNull { it.length } ?: 0

// the narrowest a column may be, in pt
private fun minTextPt(column: TableColumn, dense: Boolean): Double {
    val headerPt = if (dense) 8.0 else 9.0
    val padding = if (dense) 3.0 else 6.0
    val header = longestWord(column.label) * BOLD_EM * headerPt
    val bodyChars = minOf(MAX_TOKEN_CHARS, maxOf(1, column.texts.maxOfOrNull(::longestWord) ?: 0))
    return maxOf(header, bodyChars * REGULAR_EM * BODY_PT) + 2 * padding + 1
}

// weights -> pt widths with no column under its minimum; null when the minimums don't fit
private fun fitWidths(minimums: List<Double>, weights: List<Double>, total: Double): List<Double>? {
    if (minimums.sum() > total) return null
    val pinned = mutableSetOf<Int>()
    while (true) {
        val free = total - pinned.sumOf { minimums[it] }
        val weightSum = weights.indices.filter { it !in pinned }.sumOf { weights[it] }
        val widths = weights.indices.map { if (it in pinned) minimums[it] else weights[it] / weightSum * free }
        val short = widths.indices.filter { it !in pinned && widths[it] < minimums[it] }
        if (short.isEmpty()) return widths
        pinned += short
    }
}

fun columnLayout(columns: List<TableColumn>): ColumnLayout {
    val weights = columns.map { it.weight }
    for (dense in listOf(false, true)) {
        val widths = fitWidths(columns.map { minTextPt(it, dense) }, weights, CONTENT_WIDTH_PT) ?: continue
        return ColumnLayout(widths.map { it / CONTENT_WIDTH_PT * 100 }, dense)
    }
    val total = weights.sum()
    return ColumnLayout(weights.map { it / total * 100 }, dense = true)
}

// starting weight per field kind (web reportlayout.js columnWeight)
internal fun columnWeight(field: Field): Double = when {
    field.kind == "choice" -> 1.4
    field.kind == "phone" -> 1.5
    isCentredField(field) -> 1.0
    else -> 2.0
}

// a cards table: one column per field, texts = what each card prints
fun cardsColumnLayout(fields: List<Field>, texts: (Field) -> List<String>): ColumnLayout =
    columnLayout(fields.map { TableColumn(it.label, texts(it), columnWeight(it)) })

// a plain headers + rows table (QA prints)
fun tableColumnLayout(headers: List<String>, rows: List<List<String>>, weights: List<Double>): ColumnLayout =
    columnLayout(headers.mapIndexed { i, label -> TableColumn(label, rows.map { it.getOrElse(i) { "" } }, weights[i]) })

// <table class="cards-table [dense]"><colgroup>...</colgroup>
internal fun tableOpenHtml(cssClass: String, layout: ColumnLayout): String {
    val cols = layout.percents.joinToString("") { "<col style=\"width:${"%.2f".format(java.util.Locale.US, it)}%\">" }
    val classes = if (layout.dense) "$cssClass dense" else cssClass
    return "<table class=\"$classes\"><colgroup>$cols</colgroup>"
}
