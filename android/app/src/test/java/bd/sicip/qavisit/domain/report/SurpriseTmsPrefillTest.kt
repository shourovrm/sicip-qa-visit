package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsRunningBatch
import bd.sicip.qavisit.data.tms.TmsSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SurpriseTmsPrefillTest {
    private val batch = TmsRunningBatch(
        course = "Electrical Installation and Maintenance",
        batchNumber = "3",
        startDate = "2026-08-01",
        endDate = "2026-11-30",
        enrolled = 25,
        female = 7,
        attendanceToday = 20,
        attendance7day = 21.43,
        tmsCourse = "EIM",
        averageFrom = "2026-09-18",
        averageTo = "2026-09-28",
        averageClassDays = 7,
    )
    private val snapshot = TmsSnapshot("t", emptyList(), listOf(batch))

    private fun attendanceCard(course: String, batchNumber: String, vararg extra: Pair<String, String>): JsonObject = buildJsonObject {
        put("_id", "a-$course-$batchNumber")
        put("course", course)
        put("batch", batchNumber)
        extra.forEach { (key, value) -> put(key, value) }
    }

    private fun dataWith(vararg cards: JsonObject) = ReportData.EMPTY.withCardsReplaced("attendance", cards.toList())

    @Test
    fun `matching card gets enrolment, one-decimal TMS mean and a range note`() {
        val data = prefillSurpriseFromTms(snapshot, dataWith(attendanceCard("Electrical Installation & Maintenance (EIM)", "03"))).data
        assertEquals("25", data.cardField("attendance", 0, "enrolled_total"))
        assertEquals("7", data.cardField("attendance", 0, "enrolled_female"))
        assertEquals("21.4", data.cardField("attendance", 0, "tms_avg7"))
        assertEquals("TMS avg 18–28 Sep 2026 (7 class days)", data.cardField("attendance", 0, "remarks"))
    }

    @Test
    fun `filled fields and typed remarks are kept`() {
        val card = attendanceCard("EIM", "3", "enrolled_total" to "24", "remarks" to "Register shows 22")
        val data = prefillSurpriseFromTms(snapshot, dataWith(card)).data
        assertEquals("24", data.cardField("attendance", 0, "enrolled_total"))
        assertEquals("Register shows 22", data.cardField("attendance", 0, "remarks"))
        assertEquals("7", data.cardField("attendance", 0, "enrolled_female"))
    }

    @Test
    fun `differing filled values become Use suggestions, never overwritten, the note never one`() {
        val stale = attendanceCard("EIM", "3", "enrolled_total" to "24", "tms_avg7" to "21.4", "remarks" to "TMS avg 1–9 Sep 2026 (7 class days)")
        val result = prefillSurpriseFromTms(snapshot, dataWith(stale))
        assertEquals("24", result.data.cardField("attendance", 0, "enrolled_total"))
        assertEquals("TMS avg 1–9 Sep 2026 (7 class days)", result.data.cardField("attendance", 0, "remarks"))
        assertEquals(listOf("enrolled_total" to "25"), result.suggestions.map { it.fieldKey to it.tmsValue })
        assertEquals("a-EIM-3", result.suggestions.single().cardId)
        assertEquals(listOf("tms_avg7"), result.matched.map { it.fieldKey })
    }

    @Test
    fun `other batches and unknown courses are untouched`() {
        val data = prefillSurpriseFromTms(snapshot, dataWith(attendanceCard("EIM", "4"), attendanceCard("Welding", "3"))).data
        assertEquals("", data.cardField("attendance", 0, "enrolled_total"))
        assertEquals("", data.cardField("attendance", 1, "tms_avg7"))
    }

    @Test
    fun `range note names fewer class days and spans months`() {
        assertEquals("TMS avg 28 Aug – 3 Sep 2026 (3 class days)", tmsAverageNote("2026-08-28", "2026-09-03", 3))
        assertEquals("TMS avg 28 Dec 2025 – 3 Jan 2026 (2 class days)", tmsAverageNote("2025-12-28", "2026-01-03", 2))
        assertEquals("TMS avg 3 Sep 2026 (1 class day)", tmsAverageNote("2026-09-03", "2026-09-03", 1))
    }

    @Test
    fun `no mean leaves the TMS fields blank`() {
        val noRows = snapshot.copy(runningBatches = listOf(batch.copy(attendance7day = null, averageFrom = null, averageTo = null, averageClassDays = 0)))
        val data = prefillSurpriseFromTms(noRows, dataWith(attendanceCard("EIM", "3"))).data
        assertEquals("", data.cardField("attendance", 0, "tms_avg7"))
        assertEquals("", data.cardField("attendance", 0, "remarks"))
        assertEquals("25", data.cardField("attendance", 0, "enrolled_total"))
    }

    @Test
    fun `address fills only an empty field`() {
        assertEquals("Mirpur, Dhaka", withAddressIfEmpty(ReportData.EMPTY, "Mirpur, Dhaka").field("address"))
        val typed = ReportData.EMPTY.withField("address", "Typed")
        assertEquals("Typed", withAddressIfEmpty(typed, "Mirpur, Dhaka").field("address"))
        assertEquals("", withAddressIfEmpty(ReportData.EMPTY, " ").field("address"))
    }
}
