// unit test for domain/report/RewritableLocations.kt -- proves ui/reports/ReportReview.kt's
// per-text "Improve wording" list locates the right slot (plain field / linked card field /
// checklist remarks) and writes a suggestion back to that exact slot without disturbing anything
// else, against the real surprise-v1.json template (same fixture-loading convention as
// NewReportTest.kt).
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun loadSurpriseTemplate(): ReportTemplate =
    parseReportTemplate(File("../../shared/report-templates/surprise-v1.json").readText())

// one named course in section A -- enough for normalize() to derive a linked attendance card
// (section C) and a linked interview card (section I) to exercise the CardField branch against.
private fun dataWithOneCourse(template: ReportTemplate): ReportData {
    val course = buildJsonObject {
        put("_id", "course-1")
        put("course", "Welding (SMAW)")
        put("batch", "07")
    }
    val data = ReportData.EMPTY.withCardAdded("courses", course)
    return normalize(template, data)
}

class RewritableLocationsTest {
    @Test
    fun `finds a plain field, still lists it when blank`() {
        val template = loadSurpriseTemplate()
        val data = ReportData.EMPTY.withField("instructions_given", "told coordinator to update the register")

        val locations = collectRewritableLocations(template, data)

        val instructions = locations.filterIsInstance<RewritableLocation.TopField>().single { it.fieldKey == "instructions_given" }
        assertEquals("M", instructions.sectionLetter)
        assertEquals("Instructions given to the TI on the spot", instructions.label)
        assertEquals("told coordinator to update the register", instructions.currentText(data))

        // key_findings is also rewrite:true but left blank -- the pure walk still returns it
        // (ReportReview.kt filters blanks itself before rendering, not this function).
        val keyFindings = locations.filterIsInstance<RewritableLocation.TopField>().single { it.fieldKey == "key_findings" }
        assertEquals("", keyFindings.currentText(data))
    }

    @Test
    fun `finds a linked card field, labelled by the card's own course name`() {
        val template = loadSurpriseTemplate()
        var data = dataWithOneCourse(template)
        data = data.withCardField("attendance", 0, "remarks", "3/15 trainee no ID card")

        val locations = collectRewritableLocations(template, data)

        val remarks = locations.filterIsInstance<RewritableLocation.CardField>()
            .single { it.cardsKey == "attendance" && it.fieldKey == "remarks" }
        assertEquals("C", remarks.sectionLetter)
        assertEquals("Welding (SMAW) · Batch 07 · Remarks", remarks.label)
        assertEquals("3/15 trainee no ID card", remarks.currentText(data))
    }

    @Test
    fun `finds every checklist item's remarks, numbered by position in its own block`() {
        val template = loadSurpriseTemplate()
        val data = ReportData.EMPTY.withCheck("arrival_1", remarks = "trainer takes class w/o lesson plan")

        val locations = collectRewritableLocations(template, data)

        val arrival1 = locations.filterIsInstance<RewritableLocation.ChecklistRemarks>().single { it.itemId == "arrival_1" }
        assertEquals("B", arrival1.sectionLetter)
        assertEquals("Q1 remarks", arrival1.label)
        assertEquals("trainer takes class w/o lesson plan", arrival1.currentText(data))
    }

    @Test
    fun `locations come back in report order`() {
        val template = loadSurpriseTemplate()
        var data = dataWithOneCourse(template)
        data = data
            .withCheck("arrival_1", remarks = "x") // section B
            .withCardField("attendance", 0, "remarks", "y") // section C
            .withField("key_findings", "z") // section M

        val locations = collectRewritableLocations(template, data)
        val letters = locations.map { it.sectionLetter }
        val distinctInOrder = letters.distinct()

        // B (arrival) before C (attendance) before I (interview) before M (rating) -- matches
        // template.sections' own order, never re-sorted.
        assertTrue(distinctInOrder.indexOf("B") < distinctInOrder.indexOf("C"))
        assertTrue(distinctInOrder.indexOf("C") < distinctInOrder.indexOf("M"))
    }

    @Test
    fun `withText rewrites exactly one slot, everything else stays untouched`() {
        val template = loadSurpriseTemplate()
        var data = dataWithOneCourse(template)
        data = data
            .withCardField("attendance", 0, "remarks", "original attendance remarks")
            .withCheck("arrival_1", remarks = "original checklist remarks")
            .withField("key_findings", "original key findings")

        val locations = collectRewritableLocations(template, data)
        val attendanceRemarks = locations.filterIsInstance<RewritableLocation.CardField>().single { it.cardsKey == "attendance" && it.fieldKey == "remarks" }

        val updated = attendanceRemarks.withText(data, "rewritten attendance remarks")

        assertEquals("rewritten attendance remarks", updated.cardField("attendance", 0, "remarks"))
        // untouched siblings
        assertEquals("original checklist remarks", updated.checkRemarks("arrival_1"))
        assertEquals("original key findings", updated.field("key_findings"))
        // original (pre-apply) copy is unaffected -- ReportData is immutable
        assertEquals("original attendance remarks", data.cardField("attendance", 0, "remarks"))
    }
}
