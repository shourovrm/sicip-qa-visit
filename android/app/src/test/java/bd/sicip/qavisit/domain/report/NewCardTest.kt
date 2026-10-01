// L graduate card newFrom "firstCourse": a new card starts with the first A course + batch.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class NewCardTest {
    private val template = parseReportTemplate(File("../../shared/report-templates/surprise-v2.json").readText())
    private val graduate = template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().first { it.key == "graduate" }
    private val equipment = template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().first { it.key == "equipment" }

    private fun course(name: String, batch: String) = buildJsonObject {
        put("_id", JsonPrimitive(name))
        put("course", JsonPrimitive(name))
        put("batch", JsonPrimitive(batch))
    }

    private fun kotlinx.serialization.json.JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `graduate card copies the first course and batch`() {
        val data = ReportData.EMPTY.withCardAdded("courses", course("Welding (SMAW)", "7")).withCardAdded("courses", course("Electrical", "3"))
        val card = newCard(graduate, data)
        assertEquals("Welding (SMAW)", card.text("course"))
        assertEquals("7", card.text("batch"))
        assertNotNull(card.text("_id"))
    }

    @Test
    fun `no courses yet gives an empty card`() {
        val card = newCard(graduate, ReportData.EMPTY)
        assertNull(card.text("course"))
        assertNull(card.text("batch"))
    }

    @Test
    fun `blocks without newFrom start empty`() {
        val data = ReportData.EMPTY.withCardAdded("courses", course("Welding (SMAW)", "7"))
        assertEquals(setOf("_id"), newCard(equipment, data).keys)
    }
}
