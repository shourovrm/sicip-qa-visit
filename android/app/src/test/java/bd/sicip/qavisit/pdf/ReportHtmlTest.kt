// HTML builder is pure string assembly -- no Android/WebView needed, so structure bits are
// asserted directly against the generated markup here, same approach as BillHtmlTest.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.ChecklistItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.FlagItem
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val ANSWERS = listOf(
    AnswerOption("yes", "Yes", "yes"),
    AnswerOption("no", "No", "no"),
    AnswerOption("partial", "Partial", "partial"),
    AnswerOption("na", "N/A", "na"),
)

private fun template(sections: List<ReportSection>) = ReportTemplate(
    id = "surprise",
    version = 1,
    title = "Surprise Visit Report",
    program = "SICIP",
    subtitle = "Unannounced visit",
    answers = ANSWERS,
    sections = sections,
)

private val META = ReportMeta(officerName = "Jane Doe", status = "draft")

class ReportHtmlTest {
    @Test
    fun `field values are html-escaped`() {
        val section = ReportSection(
            letter = "A", key = "visit", short = "Visit", title = "Visit details",
            blocks = listOf(ReportBlock.Fields(fields = listOf(Field(key = "notes", label = "Notes", kind = "text")))),
        )
        val data = ReportData.EMPTY.withField("notes", "<script>alert(\"x\")</script> & 'quote'")

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertFalse("raw script tag must not appear unescaped", html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&quot;x&quot;"))
    }

    @Test
    fun `blank field prints Not answered`() {
        val section = ReportSection(
            letter = "A", key = "visit", short = "Visit", title = "Visit details",
            blocks = listOf(
                ReportBlock.Fields(
                    fields = listOf(
                        Field(
                            key = "rating", label = "Rating", kind = "choice",
                            options = null,
                        ),
                    ),
                ),
            ),
        )
        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("Not answered"))
    }

    @Test
    fun `blank checklist item prints Not answered and empty remarks cell`() {
        val section = ReportSection(
            letter = "B", key = "arrival", short = "Arrival", title = "Status on arrival",
            blocks = listOf(
                ReportBlock.Checklist(
                    key = "arrival",
                    items = listOf(ChecklistItem(id = "arrival_1", text = "Centre open on time")),
                ),
            ),
        )
        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("Not answered"))
        // remarks cell renders empty, not the word "Not answered" a second time for the same row.
        assertTrue(html.contains("<td class=\"remarks\"></td>"))
    }

    @Test
    fun `answered checklist item renders the toned answer label and escaped remarks`() {
        val section = ReportSection(
            letter = "B", key = "arrival", short = "Arrival", title = "Status on arrival",
            blocks = listOf(
                ReportBlock.Checklist(
                    key = "arrival",
                    items = listOf(ChecklistItem(id = "arrival_1", text = "Centre open on time")),
                ),
            ),
        )
        val data = ReportData.EMPTY.withCheck("arrival_1", answer = "no", remarks = "Closed <early>")

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertTrue(html.contains("style=\"color:#b3261e\""))
        assertTrue(html.contains(">No<"))
        assertTrue(html.contains("Closed &lt;early&gt;"))
    }

    @Test
    fun `unticked flags are omitted, only ticked flags print`() {
        val section = ReportSection(
            letter = "L", key = "flags", short = "Flags", title = "Critical non-compliance flags",
            blocks = listOf(
                ReportBlock.Flags(
                    items = listOf(
                        FlagItem(id = "flag_1", text = "Centre closed"),
                        FlagItem(id = "flag_2", text = "Trainer absent"),
                    ),
                ),
            ),
        )
        val data = ReportData.EMPTY.withFlag("flag_1", true)

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertTrue(html.contains("Centre closed"))
        assertFalse("untouched flag must not print", html.contains("Trainer absent"))
    }

    @Test
    fun `flags block with nothing ticked prints None ticked`() {
        val section = ReportSection(
            letter = "L", key = "flags", short = "Flags", title = "Critical non-compliance flags",
            blocks = listOf(ReportBlock.Flags(items = listOf(FlagItem(id = "flag_1", text = "Centre closed")))),
        )

        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("None ticked."))
        assertFalse(html.contains("Centre closed"))
    }

    @Test
    fun `submitted meta shows the submitted timestamp, draft meta does not`() {
        val section = ReportSection(letter = "A", key = "visit", short = "Visit", title = "Visit details", blocks = emptyList())
        val draftHtml = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)
        val submittedHtml = buildReportHtml(
            template(listOf(section)),
            ReportData.EMPTY,
            ReportMeta(officerName = "Jane Doe", status = "submitted", submittedAt = "2026-09-24T10:00:00Z"),
        )

        assertTrue(draftHtml.contains(">Draft<") || draftHtml.contains("Draft</div>") || draftHtml.contains("Officer: Jane Doe &middot; Draft"))
        assertFalse(draftHtml.contains("submitted 2026"))
        assertTrue(submittedHtml.contains("Officer: Jane Doe &middot; Submitted"))
        assertTrue(submittedHtml.contains("submitted 2026-09-24T10:00:00Z"))
    }
}
