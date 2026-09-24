package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.db.Visit
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun loadSurpriseTemplate(): ReportTemplate =
    parseReportTemplate(File("../../shared/report-templates/surprise-v1.json").readText())

private fun sampleVisit(): Visit = Visit(
    id = "v1",
    officerId = "o1",
    tripId = null,
    institute = "Bangladesh-Korea TTC, Mirpur",
    association = "FLAXA",
    district = "Dhaka",
    dhakaMetro = true,
    purpose = "Surprise visit",
    startDate = "2026-09-24",
    endDate = "2026-09-24",
    createdAt = "2026-09-20T00:00:00Z",
    updatedAt = "2026-09-20T00:00:00Z",
)

class NewReportTest {
    @Test
    fun `prefills every field the template marks with a known prefill source`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe", officerDesignation = "QA Officer")

        assertEquals(visit.institute, data.field("ti_name")) // prefill: institute
        assertEquals(visit.association, data.field("provider")) // prefill: association
        assertEquals(visit.startDate, data.field("visit_date")) // prefill: visit_date
        assertEquals("Jane Doe (QA Officer)", data.field("officers")) // prefill: officers
    }

    @Test
    fun `officers prefill falls back to name alone when designation is unknown`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe", officerDesignation = null)

        assertEquals("Jane Doe", data.field("officers"))
    }

    @Test
    fun `fields with no prefill source stay blank`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe")

        assertEquals("", data.field("address")) // no prefill on this field
        assertEquals("", data.field("departure_time"))
    }

    @Test
    fun `seeds each cards block with exactly start rows, each carrying a random _id`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe")

        // start values from surprise-v1.json: persons=1, courses=1, identity=5, graduate=5
        assertEquals(1, data.cards("persons").size)
        assertEquals(1, data.cards("courses").size)
        assertEquals(5, data.cards("identity").size)
        assertEquals(5, data.cards("graduate").size)
        // every seeded card carries a random _id (spec: "Random id on create (uuid)"), and no
        // two seeded cards collide.
        val identityIds = data.cards("identity").map { it["_id"]?.jsonPrimitive?.contentOrNull }
        assertTrue("every seeded card has a non-blank _id", identityIds.all { !it.isNullOrBlank() })
        assertEquals("seeded ids are unique", identityIds.size, identityIds.toSet().size)
    }

    @Test
    fun `linked cards blocks (attendance) are never seeded directly -- they start empty`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe")

        // attendance links from courses; a freshly seeded course card is blank (course/batch
        // both ""), so nothing has a linked field filled in yet -- syncLinks produces no rows.
        assertTrue(data.cards("attendance").isEmpty())
        // optional section K's cards block (unresolved) also starts at 0 in the template.
        assertTrue(data.cards("unresolved").isEmpty())
        assertTrue(data.cards("other_flags").isEmpty())
    }

    @Test
    fun `filling a seeded course immediately links an attendance card on the next syncLinks pass`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()
        val data = newReport(template, visit, officerName = "Jane Doe")

        val courseId = data.cards("courses").first()["_id"]?.jsonPrimitive?.content
        val filled = data.withCardField("courses", 0, "course", "Welding (SMAW)")
            .withCardField("courses", 0, "batch", "07")
        val synced = syncLinks(template, filled)

        val attendance = synced.cards("attendance")
        assertEquals(1, attendance.size)
        assertEquals("attendance:$courseId", attendance.first()["_id"]?.jsonPrimitive?.content)
        assertEquals("Welding (SMAW)", synced.cardField("attendance", 0, "course"))
        assertEquals("07", synced.cardField("attendance", 0, "batch"))
    }

    @Test
    fun `checklist and flags blocks seed nothing`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe")

        assertEquals("", data.checkAnswer("arrival_1"))
        assertTrue(data.flags().isEmpty())
    }
}
