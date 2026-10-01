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
        TmsTrainerHint("Abul Kashem", "", setOf(11L), masterBatchIds = setOf(3L)),
        TmsTrainerHint("Bina Akter", "Senior Instructor", setOf(10L)),
        TmsTrainerHint("Chandan Roy", "", setOf(10L), associateBatchIds = setOf(7L)),
        TmsTrainerHint("Dipa Rani", "", setOf(10L), masterBatchIds = setOf(7L)),
    )
    private val sources = SuggestionSources(catalog = catalog, trainers = trainers)

    @Test
    fun `trainer list keeps name, designation, course ids and batch roles only`() {
        val rows = Json.parseToJsonElement(
            """[{"id":1,"employee_info":{"name":"Abul Kashem","designation":null,"mobile":"01700000000","nid":"123"},
                 "map_entity_institute_course_trainer":[{"course_info_id":11,"mapping_type":"2"},{"course_info_id":null,"mapping_type":"1"}],
                 "batch_info_details_master_trainer":[{"batch_info_id":3,"master_trainer_id":1}],
                 "batch_info_details_associate_trainer":[{"batch_info_id":9,"associate_trainer_id":1}]},
                {"id":2,"employee_info":{"name":" ","designation":"x"}}]""",
        ).jsonArray
        assertEquals(listOf(TmsTrainerHint("Abul Kashem", "", setOf(11L), setOf(3L), setOf(9L))), trainerHintsOf(rows))
    }

    @Test
    fun `batch master first, then its associates, its course, then the rest`() {
        val welding = card("batch" to "Welding (SMAW) · 7")
        assertEquals(listOf("Dipa Rani", "Chandan Roy", "Bina Akter", "Abul Kashem"), suggestionsFor(nameField, welding, sources))
        assertEquals(listOf("Abul Kashem", "Bina Akter", "Chandan Roy", "Dipa Rani"), suggestionsFor(nameField, card("batch" to ""), sources))
    }

    @Test
    fun `designation is TMS's own, else the role in the card's batch, only into an empty field`() {
        val welding = card("batch" to "Welding (SMAW) · 7")
        assertEquals("Senior Instructor", trainerDesignationFor(block, welding, "Bina Akter", sources))
        assertEquals("Master Trainer", trainerDesignationFor(block, welding, "Dipa Rani", sources))
        assertEquals("Associate Trainer", trainerDesignationFor(block, welding, "Chandan Roy", sources))
        assertNull(trainerDesignationFor(block, welding, "Abul Kashem", sources)) // master of another batch
        assertNull(trainerDesignationFor(block, card("batch" to "Welding (SMAW) · 7", "designation" to "Lead"), "Dipa Rani", sources))
    }
}
