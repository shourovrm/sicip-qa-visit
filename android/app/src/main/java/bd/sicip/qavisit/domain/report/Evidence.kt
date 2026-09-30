// QA v2 evidence register: one list per report (data.evidence = [{_id, name, no}]), each criteria
// item points at entries by id (data.criteria[item].evidenceRefs). An entry's number is fixed
// where it is first added ("7.1b.a" = section 7, criterion 1 b), first evidence there) and never
// changes, so documents the officer already labelled at the institute keep matching the report.
// 1:1 port of web/src/lib/evidence.js.
package bd.sicip.qavisit.domain.report

import java.util.UUID

private fun sameName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

// "2.3" for a numbered criterion, "7.1b" for sub-criterion b) under 7's "1."
fun criteriaPath(section: ReportSection, block: ReportBlock.Criteria, item: CriteriaItem): String {
    var main = ""
    for (candidate in block.items) {
        val number = Regex("""^(\d+)\.""").find(candidate.no.orEmpty().trim())
        if (number != null) main = number.groupValues[1]
        if (candidate.id != item.id) continue
        val letter = Regex("""^([a-z])\)""").find(candidate.no.orEmpty().trim())
        return if (letter != null) "${section.number}.$main${letter.groupValues[1]}" else "${section.number}.$main"
    }
    return section.number.orEmpty()
}

// first free suffix under this path: a, b, ... z, then 27, 28, ... (26+ items on one criterion
// never happens in practice; numbers just keep it unique)
private fun nextNumber(list: List<EvidenceEntry>, path: String): String {
    val taken = list.map { it.no }.toSet()
    var index = 0
    while (true) {
        val suffix = if (index < 26) ('a' + index).toString() else (index + 1).toString()
        val candidate = "$path.$suffix"
        if (candidate !in taken) return candidate
        index++
    }
}

// add evidence `name` to an item: an entry with the same name (any case) is reused with its
// number; otherwise a new entry is numbered at this item's path
fun withEvidenceAdded(
    data: ReportData,
    path: String,
    itemId: String,
    name: String,
    newId: () -> String = { UUID.randomUUID().toString() },
): ReportData {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return data
    val list = data.evidenceList()
    var nextData = data
    val entry = list.find { sameName(it.name, trimmed) } ?: EvidenceEntry(newId(), trimmed, nextNumber(list, path)).also {
        nextData = data.withEvidenceList(list + it)
    }
    val refs = nextData.criteriaEvidenceRefs(itemId)
    if (entry.id in refs) return nextData
    return nextData.withCriteriaEvidenceRefs(itemId, refs + entry.id)
}

// removing from one item keeps the register entry (and its number) for any later re-use
fun withEvidenceRemoved(data: ReportData, itemId: String, evidenceId: String): ReportData =
    data.withCriteriaEvidenceRefs(itemId, data.criteriaEvidenceRefs(itemId).filter { it != evidenceId })

// the item's evidence in the order it was added
fun itemEvidence(data: ReportData, itemId: String): List<EvidenceEntry> {
    val byId = data.evidenceList().associateBy { it.id }
    return data.criteriaEvidenceRefs(itemId).mapNotNull { byId[it] }
}

fun evidenceLabel(entry: EvidenceEntry): String = "${entry.no} – ${entry.name}"

// names offered while typing: this report's register (not already on the item), the app's own
// tables (cards blocks with evidenceName), then the template's Word-table names; deduped by name
fun evidenceSuggestions(template: ReportTemplate, data: ReportData, itemId: String, query: String = ""): List<String> {
    val onItem = itemEvidence(data, itemId).map { it.name.trim().lowercase() }.toSet()
    val tableNames = template.sections.flatMap { it.blocks }
        .filterIsInstance<ReportBlock.Cards>()
        .mapNotNull { it.evidenceName }
    val names = data.evidenceList().map { it.name } + tableNames + template.evidenceSuggestions
    val needle = query.trim().lowercase()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (name in names) {
        val key = name.trim().lowercase()
        if (key.isEmpty() || key in onItem || !seen.add(key)) continue
        if (needle.isNotEmpty() && !key.contains(needle)) continue
        out += name
    }
    return out
}

// "7.1b.a" < "7.1b.b" < "8.2.a" < "10.1.a": number runs compared as numbers
private val NUMBER_RUN = Regex("""\d+|\D+""")

private fun naturalCompare(a: String, b: String): Int {
    val left = NUMBER_RUN.findAll(a).map { it.value }.toList()
    val right = NUMBER_RUN.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
        if (result != 0) return result
    }
    return left.size - right.size
}

// every entry some item still points at, in number order -- the report's closing evidence list
fun usedEvidence(data: ReportData): List<EvidenceEntry> {
    val used = data.criteriaItemIds().flatMap { data.criteriaEvidenceRefs(it) }.toSet()
    return data.evidenceList().filter { it.id in used }.sortedWith { a, b -> naturalCompare(a.no, b.no) }
}
