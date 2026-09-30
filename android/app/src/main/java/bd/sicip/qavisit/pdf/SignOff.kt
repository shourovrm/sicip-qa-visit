// "Submitted by-" block closing every surprise and QA report: signature space, then each
// visiting officer's name, designation and organisation, side by side.
// Kotlin port of web/src/lib/signoff.js; keep the two in lockstep.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.visitingOfficers

private const val SIGNOFF_ORGANISATION = "SICIP"

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
