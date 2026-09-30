package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsFixture
import bd.sicip.qavisit.data.tms.TmsLink
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmsPrefillTest {
    private val snapshot = TmsFixture.snapshot()

    private fun card(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        put("_id", "card-${pairs.first().second}")
        pairs.forEach { (k, v) -> put(k, v) }
    }

    private fun dataWith(key: String, vararg cards: JsonObject) = ReportData.EMPTY.withCardsReplaced(key, cards.toList())

    @Test
    fun `blank report gets all courses and running batches`() {
        val result = prefillFromTms(snapshot, dataWith("mou_courses", buildJsonObject { put("_id", "seed") }))
        val data = result.data
        assertEquals(listOf("Course A", "Course B"), data.cards("mou_courses").map { it["course"]!!.let { v -> (v as JsonPrimitive).content } })
        assertEquals("seed", (data.cards("mou_courses")[0]["_id"] as JsonPrimitive).content) // blank seed row reused
        assertEquals("60 days / 300 h", data.cardField("mou_courses", 0, "duration"))
        assertEquals("10", data.cardField("mou_courses", 0, "batches"))
        assertEquals("20", data.cardField("mou_courses", 0, "batch_size"))
        assertEquals("22", data.cardField("cumulative", 0, "enrolled_t"))
        assertEquals("2", data.cardField("cumulative", 0, "dropout_t"))
        assertEquals("", data.cardField("cumulative", 1, "dropout_t")) // no qualifying batch
        assertEquals(1, data.cards("batches").size)
        assertEquals("2", data.cardField("batches", 0, "batch"))
        assertEquals("2026-04-12 – 2026-07-09", data.cardField("batches", 0, "start_end"))
        assertEquals("4", data.cardField("batches", 0, "attendance_today"))
        assertEquals("4.1", data.cardField("batches", 0, "attendance_7day"))
        assertTrue(result.suggestions.isEmpty())
        data.cards("mou_courses").forEach { assertNotNull(it["_id"]) }
    }

    @Test
    fun `target and tms_mismatch and dropouts of running batches stay blank`() {
        val data = prefillFromTms(snapshot, ReportData.EMPTY).data
        listOf("mou_courses" to "target", "cumulative" to "target", "batches" to "tms_mismatch", "batches" to "dropouts").forEach { (block, field) ->
            data.cards(block).forEach { assertNull("$block.$field", it[field]) }
        }
    }

    @Test
    fun `existing values are kept and differing ones become suggestions`() {
        val existing = dataWith(
            "cumulative",
            card("course" to "Course A", "enrolled_t" to "25", "enrolled_f" to "9.0", "certified_t" to ""),
        )
        val result = prefillFromTms(snapshot, existing)
        assertEquals("25", result.data.cardField("cumulative", 0, "enrolled_t"))
        assertEquals("8", result.data.cardField("cumulative", 0, "certified_t")) // blank -> filled
        val suggestion = result.suggestions.single { it.cardsKey == "cumulative" && it.fieldKey == "enrolled_t" }
        assertEquals("22", suggestion.tmsValue)
        assertEquals(0, suggestion.cardIndex)
        assertEquals("card-Course A", suggestion.cardId)
        assertTrue(result.suggestions.none { it.fieldKey == "enrolled_f" }) // 9.0 == 9
    }

    @Test
    fun `cards are matched by name case-insensitively and by batch number`() {
        val existing = dataWith("batches", card("course" to "course a", "batch" to "2", "enrolled" to "11"))
        val result = prefillFromTms(snapshot, existing)
        assertEquals(1, result.data.cards("batches").size)
        assertEquals("12", result.suggestions.single { it.fieldKey == "enrolled" }.tmsValue)

        val otherBatch = dataWith("batches", card("course" to "Course A", "batch" to "9"))
        assertEquals(2, prefillFromTms(snapshot, otherBatch).data.cards("batches").size)
    }

    @Test
    fun `cards are never removed`() {
        val existing = dataWith("mou_courses", card("course" to "Manual Course"), card("course" to "Course A"))
        val result = prefillFromTms(snapshot, existing).data.cards("mou_courses")
        assertEquals(listOf("Manual Course", "Course A", "Course B"), result.map { (it["course"] as JsonPrimitive).content })
    }

    @Test
    fun `tms present count is exposed for the mismatch reference`() {
        assertEquals(4, tmsPresentToday(snapshot, "Course A", "2"))
        assertNull(tmsPresentToday(snapshot, "Course A", "7"))
    }

    @Test
    fun `link and snapshot round trip through report data`() {
        val link = TmsLink(1, 7, 1, "0000001", "Test Institute")
        val data = ReportData.EMPTY.withTmsLink(link).withTmsSnapshot(snapshot)
        val reread = ReportData.parse(data.toJsonString())
        assertEquals(link, reread.tmsLink())
        assertEquals(snapshot, reread.tmsSnapshot())
        assertEquals(snapshot.fetchedAt, reread.tmsFetchedAt())
        val relinked = reread.withTmsLink(link.copy(instituteId = 2))
        assertNull(relinked.tmsSnapshot()) // other institute: stale snapshot dropped
    }
}
