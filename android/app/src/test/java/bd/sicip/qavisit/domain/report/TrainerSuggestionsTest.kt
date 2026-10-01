// C "Trainers present" name suggestions from TMS entity/trainer/list. all names are made up.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsBatchRef
import bd.sicip.qavisit.data.tms.TmsCourseCatalog
import bd.sicip.qavisit.data.tms.TmsTrainerHint
import bd.sicip.qavisit.data.tms.trainerHintsOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

private fun card(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

class TrainerSuggestionsTest {
    private val template = parseReportTemplate(File("../../shared/report-templates/surprise-v2.json").readText())
    private val block = template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().first { it.key == "trainer_names" }
    private val nameField = block.fields.first { it.key == "name" }

    private val catalog = TmsCourseCatalog(
        courseNames = listOf("Welding (SMAW)", "Electrical Installation"),
        batches = listOf(
            TmsBatchRef(7, 10, "Welding (SMAW)", "7", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-10-31")),
            TmsBatchRef(3, 11, "Electrical Installation", "3", LocalDate.parse("2026-07-15"), LocalDate.parse("2026-11-30")),
        ),
    )
    private val trainers = listOf(
        TmsTrainerHint("Abul Kashem", "", setOf(11L)),
        TmsTrainerHint("Bina Akter", "Senior Instructor", setOf(10L)),
        TmsTrainerHint("Chandan Roy", "Instructor", setOf(10L, 11L)),
    )
    private val sources = SuggestionSources(catalog = catalog, trainers = trainers)

    @Test
    fun `trainer list keeps name, designation and mapped course ids only`() {
        val rows = Json.parseToJsonElement(
            """[{"id":1,"employee_info":{"name":"Abul Kashem","designation":null,"mobile":"01700000000","nid":"123"},
                 "map_entity_institute_course_trainer":[{"course_info_id":11,"mapping_type":"2"},{"course_info_id":null,"mapping_type":"1"}]},
                {"id":2,"employee_info":{"name":" ","designation":"x"}}]""",
        ).jsonArray
        assertEquals(listOf(TmsTrainerHint("Abul Kashem", "", setOf(11L))), trainerHintsOf(rows))
    }

    @Test
    fun `the card's course trainers come first, then the rest of the institute`() {
        val welding = card("batch" to "Welding (SMAW) · 7")
        assertEquals(listOf("Bina Akter", "Chandan Roy", "Abul Kashem"), suggestionsFor(nameField, welding, sources))
        assertEquals(listOf("Abul Kashem", "Bina Akter", "Chandan Roy"), suggestionsFor(nameField, card("batch" to ""), sources))
    }

    @Test
    fun `picking a trainer fills an empty designation when TMS has one`() {
        assertEquals("Senior Instructor", trainerDesignationFor(block, card("name" to ""), "Bina Akter", sources))
        assertNull(trainerDesignationFor(block, card("designation" to "Lead"), "Bina Akter", sources))
        assertNull(trainerDesignationFor(block, card(), "Abul Kashem", sources)) // TMS has none
    }
}
