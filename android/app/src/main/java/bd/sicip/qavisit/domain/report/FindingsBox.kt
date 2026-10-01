// surprise v2 N "Major findings" box (spec 2026-10-02 item 6), port of web/src/lib/findingsbox.js:
// the officer ticks written lines of the report, adds them to ONE editable box (data[boxKey], one
// finding per line) which prints as the numbered Major findings list. ticks = data[tickedKey]
// (line texts). reports from before the box keep their data.findings list untouched.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun textLines(value: String): List<String> = value.lines().map { it.trim() }.filter { it.isNotEmpty() }

// the box text, null while the report has no box yet
fun ReportData.findingsBox(block: ReportBlock.Findings): String? =
    (root[block.boxKey] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun ReportData.withFindingsBox(block: ReportBlock.Findings, text: String): ReportData =
    withTopLevel(block.boxKey, JsonPrimitive(text))

// what prints: the box once it exists (even empty), else the old picked list
fun findingLines(block: ReportBlock.Findings, data: ReportData): List<String> {
    val box = data.findingsBox(block)
    if (box != null) return textLines(box)
    return data.findings().map { it.text.trim() }.filter { it.isNotEmpty() }
}

// on open: an old picked list becomes the box text (old data kept); unchanged otherwise
fun withFindingsBoxOpened(block: ReportBlock.Findings, data: ReportData): ReportData {
    if (data.findingsBox(block) != null) return data
    val old = findingLines(block, data)
    if (old.isEmpty()) return data
    return data.withFindingsBox(block, old.joinToString("\n"))
}

fun withTicked(block: ReportBlock.Findings, data: ReportData, lines: List<String>): ReportData =
    data.withTopLevel(block.tickedKey, JsonArray(lines.map { JsonPrimitive(it) }))

// ticked lines still written in the report, in report (candidate) order
fun tickedLines(block: ReportBlock.Findings, data: ReportData, candidates: List<RemarkLine>): List<String> {
    val stored = data.root[block.tickedKey] as? JsonArray ?: JsonArray(emptyList())
    val ticked = stored.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    return candidates.map { it.text }.filter { it in ticked }
}

fun selectedFindingsText(block: ReportBlock.Findings, data: ReportData, candidates: List<RemarkLine>): String =
    tickedLines(block, data, candidates).joinToString("\n")

// the template's findings block (surprise v2 N), null for templates without one
fun ReportTemplate.findingsBlock(): ReportBlock.Findings? =
    sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Findings>().firstOrNull()
