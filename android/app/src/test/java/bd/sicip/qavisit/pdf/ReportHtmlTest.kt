// HTML builder is pure string assembly -- no Android/WebView needed, so structure bits are
// asserted directly against the generated markup here, same approach as BillHtmlTest.
//
// CHANGE SET 3 "ONE REPORT LAYOUT FOR ALL OUTPUTS" rewrote the output shape: checklist rows are
// now tick columns (Yes/No/Part/N/A, ☒ chosen / ☐ the rest) instead of one coloured "Answer"
// column, blank NEVER prints "Not answered" (an empty value/box/tick, full stop), cards render
// as one table per block (one row per card) instead of one table per card, and the fixed flags
// list always prints every item (ticked or not) alongside any custom (countsAsFlags) flags.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.CardsLink
import bd.sicip.qavisit.domain.report.ChecklistItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.FlagItem
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.normalize
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TICK_CHECKED_PATTERN = "☒"
private const val TICK_UNCHECKED_PATTERN = "☐"

private val ANSWERS = listOf(
    AnswerOption("yes", "Yes", "yes"),
    AnswerOption("no", "No", "no"),
    AnswerOption("partial", "Partial", "partial"),
    AnswerOption("na", "N/A", "na"),
)

// builds a `choice` Field whose options JSON matches what Field.choiceOptions() decodes.
private fun choiceField(key: String, label: String, options: List<AnswerOption>, required: Boolean = false): Field =
    Field(key = key, label = label, kind = "choice", required = required, options = Json.encodeToJsonElement(ListSerializer(AnswerOption.serializer()), options))

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
    fun `field and checklist text is html-escaped`() {
        val section = ReportSection(
            letter = "A", key = "visit", short = "Visit", title = "Visit details",
            blocks = listOf(
                ReportBlock.Fields(fields = listOf(Field(key = "notes", label = "Notes", kind = "text"))),
                ReportBlock.Checklist(key = "arrival", items = listOf(ChecklistItem(id = "a1", text = "Open <on> time"))),
            ),
        )
        val data = ReportData.EMPTY
            .withField("notes", "<script>alert(\"x\")</script> & 'quote'")
            .withCheck("a1", remarks = "Closed <early>")

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertFalse("raw script tag must not appear unescaped", html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&quot;x&quot;"))
        assertTrue(html.contains("Open &lt;on&gt; time"))
        assertTrue(html.contains("Closed &lt;early&gt;"))
    }

    @Test
    fun `blank field never prints Not answered -- just an empty value`() {
        val section = ReportSection(
            letter = "A", key = "visit", short = "Visit", title = "Visit details",
            blocks = listOf(
                ReportBlock.Fields(
                    fields = listOf(
                        choiceField("rating", "Rating", ANSWERS.take(3)),
                        Field(key = "notes", label = "Notes", kind = "text"),
                    ),
                ),
            ),
        )
        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertFalse(html.contains("Not answered"))
        assertTrue(html.contains("<span class=\"label\">Rating</span><span class=\"value\"></span>"))
        assertTrue(html.contains("<span class=\"label\">Notes</span><span class=\"value\"></span>"))
    }

    @Test
    fun `blank checklist item renders four unchecked ticks and an empty remarks cell, never Not answered`() {
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

        assertFalse(html.contains("Not answered"))
        // 4 tick cells, all unchecked (no per-answer style attribute -- only a chosen tick gets one).
        val row = html.substringAfter("<td class=\"question\">Centre open on time</td>").substringBefore("</tr>")
        assertEquals(4, Regex(TICK_UNCHECKED_PATTERN).findAll(row).count())
        assertFalse(row.contains("style=\"color:"))
        assertTrue(html.contains("<td class=\"remarks\"></td>"))
    }

    @Test
    fun `answered checklist item ticks only its own answer column, in the tone colour`() {
        val section = ReportSection(
            letter = "B", key = "arrival", short = "Arrival", title = "Status on arrival",
            blocks = listOf(
                ReportBlock.Checklist(
                    key = "arrival",
                    items = listOf(ChecklistItem(id = "arrival_1", text = "Centre open on time")),
                ),
            ),
        )
        val data = ReportData.EMPTY.withCheck("arrival_1", answer = "no", remarks = "Shut early")

        val html = buildReportHtml(template(listOf(section)), data, META)

        val row = html.substringAfter("<td class=\"question\">Centre open on time</td>").substringBefore("</tr>")
        assertEquals(1, Regex(TICK_CHECKED_PATTERN).findAll(row).count())
        assertEquals(3, Regex(TICK_UNCHECKED_PATTERN).findAll(row).count())
        assertTrue(row.contains("style=\"color:#b3261e\"")) // "no" tone
        assertTrue(html.contains("Shut early"))
    }

    @Test
    fun `perCourse item with 2+ answered courses shows the per-course summary line`() {
        val section = ReportSection(
            letter = "B", key = "arrival", short = "Arrival", title = "Status on arrival",
            blocks = listOf(
                ReportBlock.Checklist(
                    key = "arrival",
                    items = listOf(ChecklistItem(id = "arrival_1", text = "Centre open on time", perCourse = true)),
                ),
            ),
        )
        val data = ReportData.EMPTY
            .withCardAdded("courses").withCardField("courses", 0, "_id", "c1").withCardField("courses", 0, "course", "Welding").withCardField("courses", 0, "batch", "07")
            .withCardAdded("courses").withCardField("courses", 1, "_id", "c2").withCardField("courses", 1, "course", "Electrical").withCardField("courses", 1, "batch", "03")
            .withCheckCourse("arrival_1", "c1", "yes")
            .withCheckCourse("arrival_1", "c2", "no")

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertTrue(html.contains("<span class=\"per-course-tag\">Per course</span>"))
        assertTrue(html.contains("class=\"per-course-line\">Welding 07: Yes &middot; Electrical 03: No</div>"))
    }

    @Test
    fun `cards block renders one table with one row per card, not one table per card`() {
        val block = ReportBlock.Cards(
            key = "persons", itemLabel = "Person", start = 0, titleField = "name",
            fields = listOf(Field(key = "name", label = "Name", kind = "text")),
        )
        val section = ReportSection(letter = "A", key = "visit", short = "Visit", title = "Visit details", blocks = listOf(block))
        val data = ReportData.EMPTY
            .withCardAdded("persons").withCardField("persons", 0, "name", "Karim")
            .withCardAdded("persons").withCardField("persons", 1, "name", "Rahim")

        val html = buildReportHtml(template(listOf(section)), data, META)

        assertEquals(1, Regex("<table class=\"cards-table\"").findAll(html).count())
        assertTrue(html.contains("Karim"))
        assertTrue(html.contains("Rahim"))
    }

    @Test
    fun `flags list always prints every fixed item, ticked or not, plus custom flags as extra rows`() {
        val flagsBlock = ReportBlock.Flags(items = listOf(FlagItem(id = "flag_1", text = "Centre closed"), FlagItem(id = "flag_2", text = "Trainer absent")))
        val customBlock = ReportBlock.Cards(
            key = "other_flags", itemLabel = "Other flag", start = 0, titleField = "flag", countsAsFlags = true,
            fields = listOf(Field(key = "flag", label = "Flag", kind = "longtext")),
        )
        val section = ReportSection(letter = "L", key = "flags", short = "Flags", title = "Critical non-compliance flags", blocks = listOf(flagsBlock, customBlock))
        val data = ReportData.EMPTY.withFlag("flag_1", true).withCardAdded("other_flags").withCardField("other_flags", 0, "flag", "Loose wiring")

        val html = buildReportHtml(template(listOf(section)), data, META)

        // both fixed items print (ticked flag_1 bold/checked, untouched flag_2 not) plus the custom flag row.
        assertTrue(html.contains("flag-text checked\">Centre closed"))
        assertTrue(html.contains("flag-text\">Trainer absent"))
        assertTrue(html.contains("flag-text checked\">Loose wiring"))
        assertFalse(html.contains("None ticked"))
    }

    @Test
    fun `section with no flags ticked and no custom flags prints None ticked`() {
        val section = ReportSection(
            letter = "L", key = "flags", short = "Flags", title = "Critical non-compliance flags",
            blocks = listOf(ReportBlock.Flags(items = emptyList())),
        )
        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("None ticked."))
    }

    @Test
    fun `tabs cards block renders one small table per linked course with a tick sub-table for standard choice fields`() {
        val block = ReportBlock.Cards(
            key = "interviews", itemLabel = "Course", start = 0, titleField = "course", display = "tabs",
            linkFrom = CardsLink(cards = "courses", fields = listOf("course", "batch")),
            fields = listOf(
                Field(key = "course", label = "Course", kind = "text"),
                Field(key = "batch", label = "Batch no.", kind = "text"),
                choiceField("q1", "Classes run on schedule", ANSWERS, required = true),
                Field(key = "feedback", label = "Feedback", kind = "longtext"),
            ),
        )
        val section = ReportSection(letter = "I", key = "interview", short = "Trainees", title = "Trainee interviews", blocks = listOf(block))
        val tmpl = template(listOf(section))
        // the interview card is DERIVED by normalize() from a section-A course, not hand-built --
        // seed the course first, let normalize create the linked interview card, then answer q1
        // on it (mirrors how ReportEditor really produces this shape).
        val withCourse = ReportData.EMPTY
            .withCardAdded("courses").withCardField("courses", 0, "course", "Welding (SMAW)").withCardField("courses", 0, "batch", "07")
        val synced = normalize(tmpl, withCourse)
        val data = synced.withCardField("interviews", 0, "q1", "yes")

        val html = buildReportHtml(tmpl, data, META)

        assertTrue(html.contains("<h3>Welding (SMAW) &middot; Batch 07</h3>"))
        assertTrue(html.contains("interview-ticks"))
        assertTrue(html.contains("Classes run on schedule"))
        // course/batch (linked fields) render only once, in the caption -- not again as a details line.
        assertFalse(html.contains("<span class=\"label\">Course</span>"))
    }

    @Test
    fun `empty tabs cards block prompts adding courses in section A`() {
        val block = ReportBlock.Cards(
            key = "interviews", itemLabel = "Course", start = 0, titleField = "course", display = "tabs",
            linkFrom = CardsLink(cards = "courses", fields = listOf("course", "batch")),
            fields = listOf(Field(key = "course", label = "Course", kind = "text")),
        )
        val section = ReportSection(letter = "I", key = "interview", short = "Trainees", title = "Trainee interviews", blocks = listOf(block))

        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("Add courses in section A."))
    }

    @Test
    fun `optional section shows the Optional tag next to its heading`() {
        val section = ReportSection(letter = "K", key = "followup", short = "Follow-up", title = "Previous visit follow-up", optional = true, blocks = emptyList())

        val html = buildReportHtml(template(listOf(section)), ReportData.EMPTY, META)

        assertTrue(html.contains("<span class=\"optional-tag\">Optional</span>"))
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

        assertTrue(draftHtml.contains("Officer: Jane Doe &middot; Draft"))
        assertFalse(draftHtml.contains("submitted 2026"))
        assertTrue(submittedHtml.contains("Officer: Jane Doe &middot; Submitted"))
        assertTrue(submittedHtml.contains("submitted 2026-09-24T10:00:00Z"))
    }
}
