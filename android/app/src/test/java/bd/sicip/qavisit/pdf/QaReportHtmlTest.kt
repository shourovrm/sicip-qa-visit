// pdf/QaReportHtml.kt is pure string assembly (spec §7) -- asserted directly against the
// generated markup, same approach as ReportHtmlTest.kt. Loads the REAL shared/report-templates/
// qa-v1.json off disk (same relative-path trick ReportProgressFixtureTest.kt uses: gradle runs
// unit tests with :app as the working directory) rather than a hand-built template, so these
// assertions double as a "does the real template still parse and render" smoke test.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.parseReportTemplate
import org.junit.Assert.assertFalse
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

    @Test
    fun `feedback prints anonymous question tables, 4 respondents max, comments below, never names`() {
        val template = loadQaTemplate()
        var data = ReportData.EMPTY
        repeat(5) { data = data.withCardAdded("trainee_feedback") }
        data = data.withCardField("trainee_feedback", 0, "name", "Secret Name")
        data = data.withCardField("trainee_feedback", 0, "tq1", "no")
        data = data.withCardField("trainee_feedback", 4, "feedback", "Need more practice")

        val html = buildQaReportHtml(template, data)

        assertFalse(html.contains("Secret Name"))
        assertTrue(html.contains("<th>Trainee 4</th></tr>"))
        assertTrue(html.contains("<tr><th>Question</th><th>Trainee 5</th></tr>"))
        assertTrue(html.contains("<td>Trainer explains well and answers questions</td><td class=\"c\">No</td>"))
        assertTrue(html.contains("<li>Trainee 5: Need more practice</li>"))
        // trainer block has no cards: still 2 blank respondent columns
        assertTrue(html.contains("<tr><th>Question</th><th>Trainer 1</th><th>Trainer 2</th></tr>"))
    }

    @Test
    fun `s13 prints component names from pairs and s16 a plan table of at least 3 rows`() {
        val template = loadQaTemplate()
        val data = ReportData.EMPTY.withField("weak_7", "No PPE list")

        val html = buildQaReportHtml(template, data)

        assertTrue(html.contains("<td>Physical Resources</td><td></td><td>No PPE list</td>"))
        assertTrue(html.contains("<th>Improvement action</th>"))
        assertTrue(html.contains("<td class=\"sl\">3.</td><td></td>"))
    }
}
