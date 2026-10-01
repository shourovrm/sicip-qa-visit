// qa-v2 print tables as plain headers + rows (strings only), rendered by QaReportHtml.kt.
// 1:1 port of web/src/lib/qatables.js (which the web PDF and Word file share). A table with no
// filled row is null (nothing printed).
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.attachmentName
import bd.sicip.qavisit.domain.report.criteriaPath
import bd.sicip.qavisit.domain.report.evidenceLabel
import bd.sicip.qavisit.domain.report.itemEvidence
import bd.sicip.qavisit.domain.report.usedEvidence
import bd.sicip.qavisit.domain.report.visitingOfficers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// one top header cell over `span` leaf columns; span 1 = that column's own header (two rows tall)
data class HeaderGroup(val label: String, val span: Int = 1)

// centred: column indexes printed centred (S.N., numbers, dates, short choice answers)
// groups: two-level header (1.50); widths: fixed column % so long names wrap; note: line under it
data class PrintTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val heading: String? = null,
    val centred: Set<Int> = emptySet(),
    val groups: List<HeaderGroup> = emptyList(),
    val widths: List<Int> = emptyList(),
    val note: String? = null,
)

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun filled(card: JsonObject): Boolean = card.any { (key, _) -> !key.startsWith("_") && card.text(key).isNotEmpty() }

// a choice value -> its option label; anything else as typed
fun shownValue(field: Field?, value: String): String =
    if (field?.kind == "choice") field.choiceOptions().find { it.id == value }?.label ?: value.trim() else value.trim()

private fun numbered(rows: List<List<String>>): List<List<String>> = rows.mapIndexed { index, row -> listOf("${index + 1}.") + row }

// "2026-01-10" -> "10/01/2026" like the rest of the printed form; anything else as typed
private fun ddmmyyyy(value: String): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(value.trim()) ?: return value.trim()
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}

private fun cardsBlock(template: ReportTemplate, key: String): ReportBlock.Cards? =
    template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().find { it.key == key }

// every yyyy-mm-dd inside a text ("2026-08-01 – 2026-10-30") as dd/mm/yyyy
private fun ddmmyyyyInside(value: String): String =
    Regex("""(\d{4})-(\d{2})-(\d{2})""").replace(value.trim()) { match ->
        val (year, month, day) = match.destructured
        "$day/$month/$year"
    }

// 1.40: S.N. | Training Course | Target | Duration | No. of Batches | Batch Size
fun mouCoursesTable(data: ReportData): PrintTable? {
    val rows = data.cards("mou_courses").filter(::filled).map { card ->
        listOf(card.text("course"), card.text("target"), card.text("duration"), card.text("batches"), card.text("batch_size"))
    }
    if (rows.isEmpty()) return null
    return PrintTable(
        listOf("S.N.", "Training Course", "Target", "Duration", "No. of Batches", "Batch Size"),
        numbered(rows),
        centred = setOf(0, 2, 3, 4, 5),
        widths = listOf(7, 37, 12, 18, 13, 13),
    )
}

// 1.50: compact two-level header like the paper template, T/F pairs under each count
fun cumulativeTable(data: ReportData): PrintTable? {
    val pairKeys = listOf("enrolled", "certified", "placed", "dropout")
    val rows = data.cards("cumulative").filter(::filled).map { card ->
        listOf(card.text("course"), card.text("target")) + pairKeys.flatMap { listOf(card.text("${it}_t"), card.text("${it}_f")) }
    }
    if (rows.isEmpty()) return null
    val pairLabels = listOf("Enrolled", "Certified", "Job Placed with Percentage", "No. of dropouts with Percentage")
    return PrintTable(
        headers = listOf("S.N.", "Course Name", "Target") + pairLabels.flatMap { listOf("T", "F") },
        rows = numbered(rows),
        centred = setOf(0) + (2..10),
        groups = listOf(HeaderGroup("S.N."), HeaderGroup("Course Name"), HeaderGroup("Target")) + pairLabels.map { HeaderGroup(it, 2) },
        widths = listOf(5, 24, 7) + List(8) { 8 },
        note = "T= Total and F = Female",
    )
}

// 1.60: running batches; mismatch and dropouts are the officer's own counts
fun currentBatchesTable(data: ReportData): PrintTable? {
    val keys = listOf("course", "batch", "start_end", "enrolled", "female", "attendance_today", "attendance_7day", "tms_mismatch", "dropouts")
    val rows = data.cards("batches").filter(::filled).map { card ->
        keys.map { key -> if (key == "start_end") ddmmyyyyInside(card.text(key)) else card.text(key) }
    }
    if (rows.isEmpty()) return null
    return PrintTable(
        listOf(
            "S.N.", "Course Name", "Batch No.", "Start and End Date", "Total Number of Enrolled Trainees", "Number of Female Trainees",
            "Attendance on Visit Date", "Attendance (07-day average)", "No. of Attendance Data-Mismatch with TMS", "No. of Dropouts",
        ),
        numbered(rows),
        centred = setOf(0) + (2..9),
        widths = listOf(5, 17, 7, 13, 9, 9, 9, 9, 12, 10),
    )
}

// "R. M. Shourov, Program Officer (QA); S. Akter, Program Officer"
fun officersLine(data: ReportData): String =
    visitingOfficers(data).filter { it.name.isNotEmpty() }
        .joinToString("; ") { listOf(it.name, it.designation).filter { part -> part.isNotEmpty() }.joinToString(", ") }

// i) status: one row per registration body the officer answered
fun registrationTable(template: ReportTemplate, data: ReportData): PrintTable? {
    val block = template.sections.first().blocks.filterIsInstance<ReportBlock.Fields>()
        .find { fields -> fields.fields.any { it.key == "bteb_registered" } }
    fun field(key: String) = block?.fields?.find { it.key == key }
    val rows = listOf("bteb" to "BTEB", "nsda" to "NSDA").mapNotNull { (body, name) ->
        val registered = data.field("${body}_registered").trim()
        if (registered.isEmpty()) return@mapNotNull null
        val yes = registered == "yes"
        listOf(
            name,
            shownValue(field("${body}_registered"), registered),
            if (yes) data.field("${body}_reg_no").trim() else "",
            if (yes) data.field("${body}_courses").trim() else "",
            if (yes) shownValue(field("${body}_uptodate"), data.field("${body}_uptodate")) else "",
            if (yes) data.field("${body}_remarks").trim() else "",
        )
    }
    if (rows.isEmpty()) return null
    return PrintTable(listOf("Body", "Registered", "Registration no.", "Accredited courses", "Up to date", "Remarks"), rows, centred = setOf(1, 2, 4))
}

// 1.20: one row per MoU, "Others" printed as the typed organisation name
fun mouTable(data: ReportData): PrintTable? {
    val rows = data.cards("mous").filter(::filled).map { card ->
        val partner = if (card.text("partner") == "Others") card.text("partner_other").ifEmpty { "Others" } else card.text("partner")
        listOf(partner, ddmmyyyy(card.text("signed_date")), card.text("target"), card.text("duration"), card.text("amount"))
    }
    if (rows.isEmpty()) return null
    return PrintTable(
        listOf("S.N.", "Contract/MoU signed with", "Date of signing", "Total target", "Duration", "Total amount"),
        numbered(rows),
        centred = setOf(0, 2, 3, 4, 5),
    )
}

// 1.31-1.34: one row per course; a contract with no course yet still gets its own row
fun contractsTable(template: ReportTemplate, data: ReportData): PrintTable? {
    val block = cardsBlock(template, "contract_courses")
    fun field(key: String) = block?.fields?.find { it.key == key }
    val courses = data.cards("contract_courses").filter(::filled)
    val rows = courses.map { card ->
        listOf(
            card.text("contract"), card.text("course"),
            shownValue(field("overlap"), card.text("overlap")), shownValue(field("facilities"), card.text("facilities")),
        )
    }.toMutableList()
    data.cards("contracts").forEach { contract ->
        val organisation = contract.text("organisation")
        if (organisation.isNotEmpty() && courses.none { it.text("contract") == organisation }) rows += listOf(organisation, "", "", "")
    }
    if (rows.isEmpty()) return null
    return PrintTable(
        listOf("S.N.", "Organization / project", "Training course", "Overlaps a SICIP course", "Facilities"),
        numbered(rows),
        centred = setOf(0, 3, 4),
    )
}

// "Classroom - 300 sft and workshop/lab - 800 sft" | "Classroom cum workshop/lab - 1000 sft"
fun roomSize(card: JsonObject): String {
    if (card.text("layout") == "same") {
        return card.text("combined_sft").let { if (it.isEmpty()) "" else "Classroom cum workshop/lab - $it sft" }
    }
    val parts = mutableListOf<String>()
    if (card.text("classroom_sft").isNotEmpty()) parts += "Classroom - ${card.text("classroom_sft")} sft"
    if (card.text("workshop_sft").isNotEmpty()) parts += "workshop/lab - ${card.text("workshop_sft")} sft"
    return parts.joinToString(" and ")
}

// cards blocks inside criteria sections (4.2 sample check, 8 rooms, 8 damaged equipment) and the
// plain section 1 tables that print one column per field
fun cardsTable(block: ReportBlock.Cards, data: ReportData): PrintTable? {
    val cards = data.cards(block.key).filter(::filled)
    if (cards.isEmpty()) return null
    if (block.key == "rooms") {
        return PrintTable(
            listOf("S.N.", "Course", "Classroom and workshop size", "Trainees per batch"),
            numbered(cards.map { listOf(it.text("course"), roomSize(it), it.text("trainees")) }),
            block.heading,
            centred = setOf(0, 3),
        )
    }
    return PrintTable(
        listOf("S.N.") + block.fields.map { it.label },
        numbered(cards.map { card -> block.fields.map { shownValue(it, card.text(it.key)) } }),
        block.heading,
        centred = setOf(0) + block.fields.indices.filter { isCentredField(block.fields[it]) }.map { it + 1 },
    )
}

// a criterion's EVIDENCE cell: its numbered evidence, one per line
fun evidenceLines(data: ReportData, itemId: String): List<String> = itemEvidence(data, itemId).map(::evidenceLabel)

// closing list: every attachment in number order with the criteria that cite it
fun evidenceIndexTable(template: ReportTemplate, data: ReportData): PrintTable? {
    val used = usedEvidence(data)
    if (used.isEmpty()) return null
    val citedBy = used.associate { it.id to mutableListOf<String>() }
    for (section in template.sections) {
        for (block in section.blocks.filterIsInstance<ReportBlock.Criteria>()) {
            for (item in block.items) {
                itemEvidence(data, item.id).forEach { citedBy[it.id]?.add(criteriaPath(section, block, item)) }
            }
        }
    }
    return PrintTable(
        listOf("Attachment", "Evidence", "Criteria"),
        used.map { listOf(attachmentName(it), it.name, citedBy.getValue(it.id).joinToString(", ")) },
        "List of attachments",
        centred = setOf(0),
    )
}
