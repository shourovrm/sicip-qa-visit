// TMS differences listed for "Use" / "Use all" (status row), surprise + address. names made up.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsRunningBatch
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TmsDifferencesTest {
    private val surprise = parseReportTemplate(File("../../shared/report-templates/surprise-v2.json").readText())
    private val batch = TmsRunningBatch("Plumbing", "4", "2026-08-01", "2026-11-30", enrolled = 25, female = 3, attendance7day = 20.0)
    private val snapshot = TmsSnapshot("t", emptyList(), listOf(batch))
    private val link = TmsLink(1, 5, 9, "0009", "Test Institute", address = "Road 1, Test Town")
    private val data = ReportData.EMPTY
        .withTmsLink(link)
        .withField("address", "Typed address")
        .withCardsReplaced(
            "attendance",
            listOf(
                buildJsonObject {
                    put("_id", "c1"); put("course", "Plumbing"); put("batch", "4"); put("enrolled_total", "24"); put("enrolled_female", "2")
                },
            ),
        )

    @Test
    fun `surprise hints list card and address differences with label and context`() {
        val hints = TmsHints.of(surprise, snapshot, data)
        assertEquals(
            listOf("address" to "Road 1, Test Town", "enrolled_total" to "25", "enrolled_female" to "3"),
            hints.suggestions.map { it.fieldKey to it.tmsValue },
        )
        val row = hints.suggestions[1]
        assertEquals("Enrolled, total", tmsDifferenceLabel(surprise, row))
        assertEquals("Plumbing · 4", tmsDifferenceContext(data, row))
        assertEquals("25", hints.suggestionFor("attendance", 0, "enrolled_total")?.tmsValue)
    }

    @Test
    fun `use one and use all write the TMS values`() {
        val hints = TmsHints.of(surprise, snapshot, data)
        val one = withTmsValue(data, hints.suggestions[1])
        assertEquals("25", one.cardField("attendance", 0, "enrolled_total"))
        assertEquals("2", one.cardField("attendance", 0, "enrolled_female"))
        val all = withAllTmsValues(data, hints.suggestions)
        assertEquals("Road 1, Test Town", all.field("address"))
        assertEquals("3", all.cardField("attendance", 0, "enrolled_female"))
        assertTrue(TmsHints.of(surprise, snapshot, all).suggestions.isEmpty())
    }
}
