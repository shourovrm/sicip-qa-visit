// column widths that never break a header word (web reportlayout.test.js has the same idea)
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.parseReportTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ColumnLayoutTest {
    @Test
    fun `every column is at least as wide as its longest header word`() {
        val headers = listOf("S.N.", "Course", "Enrolled", "Entrepreneurship")
        val layout = tableColumnLayout(headers, listOf(listOf("1.", "Welding", "25", "Yes")), listOf(7.0, 20.0, 1.0, 1.0))
        assertFalse(layout.dense)
        assertEquals(100.0, layout.percents.sum(), 0.01)
        // "Entrepreneurship" = 16 bold chars at 9 pt + 12 pt padding + 1 > 13% of 487.3 pt
        assertTrue(layout.percents[3] / 100 * 487.3 >= 16 * 0.6 * 9 + 13 - 0.01)
        assertTrue(layout.percents[2] / 100 * 487.3 >= 8 * 0.6 * 9 + 13 - 0.01)
    }

    @Test
    fun `a table too crowded for the 9 pt header turns dense`() {
        val headers = List(12) { "Attendance" }
        val layout = tableColumnLayout(headers, emptyList(), List(12) { 1.0 })
        assertTrue(layout.dense)
    }

    @Test
    fun `cards table prints colgroup widths, headers wrap only between words, dense css present`() {
        val fields = listOf("Course", "Enrolled, total", "Enrolled, female", "Headcount, total", "Headcount, female", "Remarks")
            .mapIndexed { i, label -> Field(key = "f$i", label = label, kind = if (i == 0 || i == 5) "text" else "number") }
        val section = ReportSection(letter = "C", key = "c", short = "C", title = "Attendance", blocks = listOf(ReportBlock.Cards(key = "att", itemLabel = "Course", start = 1, titleField = "f0", fields = fields)))
        val template = ReportTemplate(id = "surprise", version = 1, title = "Surprise Visit Report", program = "SICIP", subtitle = "", answers = emptyList(), sections = listOf(section))
        val html = buildReportHtml(template, ReportData.EMPTY.withCardAdded("att").withCardField("att", 0, "f0", "Welding"))
        assertTrue(Regex("""<table class="cards-table( dense)?"><colgroup>(<col style="width:[0-9.]+%">){6}</colgroup>""").containsMatchIn(html))
        assertTrue(html.contains("table.dense th { font-size: 8pt; }"))
        assertTrue(html.contains("th { overflow-wrap: normal; word-break: normal; hyphens: none; }"))
        assertFalse(html.contains("break-all"))
    }

    @Test
    fun `qa tables never allow a mid-word break in headers`() {
        val qa = parseReportTemplate(File("../../shared/report-templates/qa-v2.json").readText())
        val html = buildQaReportHtml(qa, ReportData.EMPTY)
        assertFalse(html.contains("overflow-wrap: anywhere"))
        assertFalse(html.contains("break-all"))
        assertTrue(html.contains("th { overflow-wrap: normal; word-break: normal; hyphens: none; }"))
        assertTrue(html.contains("table.dense th { font-size: 8pt; }"))
    }
}
