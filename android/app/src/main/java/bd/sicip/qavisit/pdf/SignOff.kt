// "Submitted by-" block closing every surprise and QA report: signature space, then each
// visiting officer's name, designation and organisation, side by side.
// Kotlin port of web/src/lib/signoff.js; keep the two in lockstep.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportData
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val SIGNOFF_ORGANISATION = "SICIP"

data class VisitingOfficer(val name: String, val designation: String)

// "Name, Designation" or "Name (Designation)" -- the comma wins because designations like
// "Program Officer (QA)" carry their own brackets
private fun splitOfficerLine(line: String): VisitingOfficer {
    val comma = line.indexOf(',')
    if (comma > 0) return VisitingOfficer(line.substring(0, comma).trim(), line.substring(comma + 1).trim())
    val bracketed = Regex("""^(.*?)\s*\((.+)\)\s*$""").find(line)
    if (bracketed != null) return VisitingOfficer(bracketed.groupValues[1], bracketed.groupValues[2])
    return VisitingOfficer(line, "")
}

// surprise v2 keeps officers as cards; surprise v1 and QA as free text, one per line.
// Nobody named yet -> one blank slot so the page still has a place to sign.
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
    val lines = data.field("officers").split(Regex("[\n;]")).map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isNotEmpty()) return lines.map(::splitOfficerLine)
    return listOf(VisitingOfficer("", ""))
}

internal val SIGNOFF_CSS = """
  .signoff { margin-top: 18pt; break-inside: avoid; }
  .signoff-people { display: flex; flex-wrap: wrap; gap: 6pt 28pt; }
  .signoff-person { min-width: 48mm; }
  .signoff-space { height: 34pt; }
  .signoff-name { font-weight: 700; }
""".trimIndent()

internal fun signoffHtml(data: ReportData): String {
    val people = visitingOfficers(data).joinToString("") { officer ->
        "<div class=\"signoff-person\"><div class=\"signoff-space\"></div>" +
            "<div class=\"signoff-name\">${reportEsc(officer.name)}</div>" +
            "<div>${reportEsc(officer.designation)}</div>" +
            "<div>${reportEsc(SIGNOFF_ORGANISATION)}</div></div>"
    }
    return "<div class=\"signoff\"><div>Submitted by-</div><div class=\"signoff-people\">$people</div></div>"
}
