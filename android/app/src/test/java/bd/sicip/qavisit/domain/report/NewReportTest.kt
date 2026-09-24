package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.db.Visit
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
        assertEquals("", data.field("received_by"))
    }

    @Test
    fun `seeds each cards block with exactly start empty rows`() {
        val template = loadSurpriseTemplate()
        val visit = sampleVisit()

        val data = newReport(template, visit, officerName = "Jane Doe")

        // start values from surprise-v1.json: attendance=2, identity=5, graduate=5, followup=1
        assertEquals(2, data.cards("attendance").size)
        assertEquals(5, data.cards("identity").size)
        assertEquals(5, data.cards("graduate").size)
        assertEquals(1, data.cards("followup").size)
        assertTrue("seeded cards start empty", data.cards("attendance").all { it.isEmpty() })
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
