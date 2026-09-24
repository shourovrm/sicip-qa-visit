package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDataTest {
    @Test
    fun `empty report data has blank shape`() {
        val data = ReportData.EMPTY
        assertEquals("", data.field("anything"))
        assertEquals("", data.checkAnswer("anything"))
        assertTrue(data.cards("anything").isEmpty())
        assertTrue(data.flags().isEmpty())
    }

    @Test
    fun `withField sets one field without disturbing others`() {
        val data = ReportData.EMPTY.withField("a", "1").withField("b", "2")
        assertEquals("1", data.field("a"))
        assertEquals("2", data.field("b"))
    }

    @Test
    fun `withField is immutable -- original untouched`() {
        val original = ReportData.EMPTY
        val edited = original.withField("a", "1")
        assertEquals("", original.field("a"))
        assertEquals("1", edited.field("a"))
    }

    @Test
    fun `withCheck sets answer and remarks independently`() {
        val data = ReportData.EMPTY
            .withCheck("item1", answer = "yes")
            .withCheck("item1", remarks = "looks fine")
        assertEquals("yes", data.checkAnswer("item1"))
        assertEquals("looks fine", data.checkRemarks("item1"))
    }

    @Test
    fun `withCheck answer of blank string clears a previous answer (tap-again-to-clear)`() {
        val data = ReportData.EMPTY.withCheck("item1", answer = "yes").withCheck("item1", answer = "")
        assertEquals("", data.checkAnswer("item1"))
    }

    @Test
    fun `withCardAdded then withCardField edits the right row`() {
        val data = ReportData.EMPTY
            .withCardAdded("attendance")
            .withCardAdded("attendance")
            .withCardField("attendance", 1, "course", "Welding")
        assertEquals("", data.cardField("attendance", 0, "course"))
        assertEquals("Welding", data.cardField("attendance", 1, "course"))
        assertEquals(2, data.cards("attendance").size)
    }

    @Test
    fun `withCardRemoved drops exactly that row`() {
        val data = ReportData.EMPTY
            .withCardAdded("identity")
            .withCardField("identity", 0, "name", "Rakib")
            .withCardAdded("identity")
            .withCardField("identity", 1, "name", "Karim")
            .withCardRemoved("identity", 0)
        assertEquals(1, data.cards("identity").size)
        assertEquals("Karim", data.cardField("identity", 0, "name"))
    }

    @Test
    fun `withFlag ticks and unticks`() {
        val ticked = ReportData.EMPTY.withFlag("flag_1", true)
        assertTrue(ticked.flags().contains("flag_1"))
        val unticked = ticked.withFlag("flag_1", false)
        assertTrue(unticked.flags().isEmpty())
    }

    @Test
    fun `unknown top-level keys survive every setter untouched (forward compat)`() {
        val raw = buildJsonObject {
            put("fields", buildJsonObject {})
            put("checks", buildJsonObject {})
            put("cards", buildJsonObject {})
            put("flags", Json.parseToJsonElement("[]"))
            put("futureKey", "from a newer template version")
        }
        val data = ReportData(raw)
            .withField("a", "1")
            .withCheck("item1", answer = "yes")
            .withFlag("flag_1", true)

        assertEquals("from a newer template version", data.root["futureKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `unknown keys inside a checklist item entry survive an unrelated edit`() {
        val raw = buildJsonObject {
            put("fields", buildJsonObject {})
            put(
                "checks",
                buildJsonObject {
                    put(
                        "item1",
                        buildJsonObject {
                            put("answer", "yes")
                            put("remarks", "")
                            put("futureField", "unmodeled by this build")
                        },
                    )
                },
            )
            put("cards", buildJsonObject {})
            put("flags", Json.parseToJsonElement("[]"))
        }
        // edit item1's remarks only -- its answer and its unknown sibling key must both survive.
        val data = ReportData(raw).withCheck("item1", remarks = "updated")

        val item1 = data.root["checks"]!!.jsonObject["item1"]!!.jsonObject
        assertEquals("yes", item1["answer"]?.jsonPrimitive?.content)
        assertEquals("updated", item1["remarks"]?.jsonPrimitive?.content)
        assertEquals("unmodeled by this build", item1["futureField"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parse round trips toJsonString back to an equal ReportData`() {
        val data = ReportData.EMPTY.withField("a", "1").withCheck("item1", answer = "no", remarks = "issue")
        val roundTripped = ReportData.parse(data.toJsonString())
        assertEquals(data, roundTripped)
    }

    @Test
    fun `parse treats null or blank text as the empty shape`() {
        assertEquals(ReportData.EMPTY, ReportData.parse(null))
        assertEquals(ReportData.EMPTY, ReportData.parse(""))
        assertEquals(ReportData.EMPTY, ReportData.parse("   "))
    }
}
