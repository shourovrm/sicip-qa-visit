// qa-v2 1.40 / 1.50 / 1.60 print tables (spec 2026-10-02 items 8-11). course names made up.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.parseReportTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QaCourseTablesTest {
    private val v2 = parseReportTemplate(File("../../shared/report-templates/qa-v2.json").readText())
    private val data = ReportData(
        Json.parseToJsonElement(
            """
            {"fields": {"other_contract": "na"},
             "cards": {
               "mou_courses": [{"_id": "m1", "course": "Plumbing and Pipe Fitting", "duration": "90 days / 360 h", "batch_size": "25"}],
               "cumulative": [{"_id": "c1", "course": "Plumbing and Pipe Fitting", "enrolled_t": "75", "enrolled_f": "10", "certified_t": "50", "certified_f": "7", "placed_t": "30", "placed_f": "4"}],
               "batches": [{"_id": "b1", "course": "Plumbing and Pipe Fitting", "batch": "4", "start_end": "2026-08-01 – 2026-10-30", "enrolled": "25", "female": "3", "attendance_today": "21", "attendance_7day": "22.4"}]},
             "checks": {}, "flags": []}
            """.trimIndent(),
        ).jsonObject,
    )
    private val html = buildQaReportHtml(v2, data)

    @Test
    fun `1_40 prints its own heading and course rows, not under 1_30`() {
        val table = mouCoursesTable(data)!!
        assertEquals(listOf("S.N.", "Training Course", "Target", "Duration", "No. of Batches", "Batch Size"), table.headers)
        assertEquals(listOf("1.", "Plumbing and Pipe Fitting", "", "90 days / 360 h", "", "25"), table.rows.single())
        assertTrue(html.contains("<h3>1.40 Name(s) of course(s) included in the MoU with duration(s)</h3>"))
        val section130 = html.substringAfter("1.30").substringBefore("1.40")
        assertFalse(section130.contains("Plumbing"))
    }

    @Test
    fun `1_50 has a two-level T F header, the T F note and wrapping fixed columns`() {
        assertTrue(html.contains("<h3>1.50 Cumulative Training Implementation Information (up to the date of visit)</h3>"))
        assertTrue(html.contains("<th rowspan=\"2\">Course Name</th>"))
        assertTrue(html.contains("<th colspan=\"2\">Enrolled</th><th colspan=\"2\">Certified</th><th colspan=\"2\">Job Placed with Percentage</th><th colspan=\"2\">No. of dropouts with Percentage</th>"))
        assertTrue(html.contains("<tr><th>T</th><th>F</th><th>T</th><th>F</th><th>T</th><th>F</th><th>T</th><th>F</th></tr></thead>"))
        assertTrue(html.contains("T= Total and F = Female"))
        assertTrue(html.contains("<td>Plumbing and Pipe Fitting</td><td class=\"c\">-</td><td class=\"c\">75</td>"))
        // placed % of certified (30/50, 4/7), no dropouts typed -> "-"
        assertTrue(html.contains("<td class=\"c\">30 (60%)</td><td class=\"c\">4 (57%)</td><td class=\"c\">-</td><td class=\"c\">-</td>"))
        assertTrue(html.contains("td { overflow-wrap: break-word; }"))
        assertTrue(html.contains("<table class=\"grid dense\"><colgroup><col style=\"width:5.00%\">"))
    }

    @Test
    fun `percentage is a whole percent of a usable base, else the count alone`() {
        assertEquals("12 (48%)", countWithPercent("12", "25"))
        assertEquals("1 (13%)", countWithPercent("1", "8")) // 12.5 rounds up
        assertEquals("12", countWithPercent("12", "0"))
        assertEquals("12", countWithPercent("12", ""))
        assertEquals("", countWithPercent("", "25"))
    }

    @Test
    fun `1_60 prints the template columns with dd mm yyyy dates`() {
        val table = currentBatchesTable(data)!!
        assertEquals(
            listOf(
                "S.N.", "Course Name", "Batch No.", "Start and End Date", "Total Number of Enrolled Trainees", "Number of Female Trainees",
                "Attendance on Visit Date", "Attendance (07-day average)", "No. of Attendance Data-Mismatch with TMS", "No. of Dropouts",
            ),
            table.headers,
        )
        assertEquals("01/08/2026 – 30/10/2026", table.rows.single()[3])
        assertTrue(html.contains("<h3>1.60 Enrolment and Attendance Information of Current Batches</h3>"))
    }

    @Test
    fun `empty course tables still print their numbered heading`() {
        val blank = buildQaReportHtml(v2, ReportData.EMPTY)
        assertTrue(blank.contains("<h3>1.40 Name(s) of course(s) included in the MoU with duration(s)</h3>"))
        assertTrue(blank.contains("<h3>1.60 Enrolment and Attendance Information of Current Batches</h3>"))
    }
}
