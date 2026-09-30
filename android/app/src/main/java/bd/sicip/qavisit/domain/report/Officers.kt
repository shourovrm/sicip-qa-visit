// visiting officers of a report: qa-v2 / surprise v2 keep them as "officers" cards, surprise v1
// and QA v1 as free text ("Name, Designation" or "Name (Designation)", one per line). Used by the
// "Submitted by-" sign-off (pdf/SignOff.kt) and the QA v1 -> v2 convert (QaConvert.kt).
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class VisitingOfficer(val name: String, val designation: String)

// the comma wins because designations like "Program Officer (QA)" carry their own brackets
fun splitOfficerLine(line: String): VisitingOfficer {
    val comma = line.indexOf(',')
    if (comma > 0) return VisitingOfficer(line.substring(0, comma).trim(), line.substring(comma + 1).trim())
    val bracketed = Regex("""^(.*?)\s*\((.+)\)\s*$""").find(line)
    if (bracketed != null) return VisitingOfficer(bracketed.groupValues[1], bracketed.groupValues[2])
    return VisitingOfficer(line, "")
}

fun splitOfficerLines(text: String): List<VisitingOfficer> =
    text.split(Regex("[\n;]")).map { it.trim() }.filter { it.isNotEmpty() }.map(::splitOfficerLine)

// officer cards first, else the free-text field; nobody named -> one blank slot to sign
fun visitingOfficers(data: ReportData): List<VisitingOfficer> {
    val cards = data.cards("officers")
        .map { card ->
            VisitingOfficer(
                card["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                card["designation"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            )
        }
        .filter { it.name.isNotEmpty() }
    if (cards.isNotEmpty()) return cards
    val lines = splitOfficerLines(data.field("officers"))
    if (lines.isNotEmpty()) return lines
    return listOf(VisitingOfficer("", ""))
}
