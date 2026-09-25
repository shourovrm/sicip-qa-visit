// pdf/QaReportHtml.kt is pure string assembly (spec §7) -- asserted directly against the
// generated markup, same approach as ReportHtmlTest.kt. Loads the REAL shared/report-templates/
// qa-v1.json off disk (same relative-path trick ReportProgressFixtureTest.kt uses: gradle runs
// unit tests with :app as the working directory) rather than a hand-built template, so these
// assertions double as a "does the real template still parse and render" smoke test.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.parseReportTemplate
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun loadQaTemplate(): ReportTemplate =
    parseReportTemplate(File("../../shared/report-templates/qa-v1.json").readText())

class QaReportHtmlTest {
    @Test
    fun `header prints Annex-3, program, title and key-value lines`() {
        val template = loadQaTemplate()
        var data = ReportData.EMPTY
        data = data.withField("ti_name", "Dhaka Skills Institute")
        data = data.withField("provider", "Sample Association")
        data = data.withField("date_from", "2026-09-25")
        data = data.withField("date_to", "2026-09-26")
        data = data.withField("officers", "R. M. Shourov, Program Officer (QA)")

        val html = buildQaReportHtml(template, data)

        assertTrue(html.contains("<div class=\"annex\">Annex-3</div>"))
        assertTrue(html.contains(template.program))
        assertTrue(html.contains(template.title))
        assertTrue(html.contains("Dhaka Skills Institute"))
        assertTrue(html.contains("Sample Association"))
        assertTrue(html.contains("from 25/09/2026 to 26/09/2026"))
        assertTrue(html.contains("R. M. Shourov, Program Officer (QA)"))
    }

    @Test
    fun `criteria section prints its uppercase heading and the REMARKS column`() {
        val template = loadQaTemplate()
        val html = buildQaReportHtml(template, ReportData.EMPTY)

        assertTrue(html.contains("QUALITY MANAGEMENT SYSTEM"))
        assertTrue(html.contains("<th>REMARKS</th>"))
        assertTrue(html.contains("<th>QUALITY CRITERIA</th>"))
        assertTrue(html.contains("<th>EVIDENCE</th>"))
    }

    @Test
    fun `a marked option prints its fixed sentence as a Remarks bullet`() {
        val template = loadQaTemplate()
        // s2_1's "qms_process" option (shared/report-templates/qa-v1.json) -- marking it Seen
        // must print its exact fixed sentence inside the REMARKS cell's bullet list.
        var data = ReportData.EMPTY
        data = data.withCriteriaOpt("s2_1", "qms_process", v = "seen")

        val html = buildQaReportHtml(template, data)

        assertTrue(html.contains("A written quality management system procedure or work-flow chart is kept."))
        assertTrue(html.contains("<ul class=\"remarks\">"))
    }

    @Test
    fun `section 10 intro and section 13 heading use the fixed spellings`() {
        val template = loadQaTemplate()
        val html = buildQaReportHtml(template, ReportData.EMPTY)

        // spec §2's Annex-3 defect fixes: section 10's replaced intro sentence, and "WEAKNESSES"
        // (not "WEAKNESS") in section 13's heading.
        assertTrue(html.contains("assesses and certifies trainees according to the requirements of the training programmes offered"))
        assertTrue(html.contains("COMPONENT-WISE STRENGTHS &amp; WEAKNESSES"))
    }

    @Test
    fun `an unmarked option with no remark prints nothing in Remarks`() {
        val template = loadQaTemplate()
        val html = buildQaReportHtml(template, ReportData.EMPTY)

        // every criteria table row for an untouched item has an empty REMARKS cell, never a
        // "Not answered" placeholder (same "blank never prints a placeholder" rule as the
        // surprise report's pdf/ReportHtml.kt).
        assertTrue(!html.contains("Not answered"))
    }

    @Test
    fun `signature block lists every officer named in the header line`() {
        val template = loadQaTemplate()
        var data = ReportData.EMPTY
        data = data.withField("officers", "R. M. Shourov, Program Officer (QA)\nS. Akter, Program Officer")

        val html = buildQaReportHtml(template, data)

        assertTrue(html.contains("1) R. M. Shourov, Program Officer (QA)"))
        assertTrue(html.contains("2) S. Akter, Program Officer"))
        assertTrue(html.contains("Signatures"))
    }
}
