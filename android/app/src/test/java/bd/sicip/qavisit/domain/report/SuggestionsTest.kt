// suggestion resolution for template `suggest` fields, the running-batch filter used at report
// creation, and the TMS catalog/trainee parsing. all names are made up.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsBatchRef
import bd.sicip.qavisit.data.tms.TmsCourseCatalog
import bd.sicip.qavisit.data.tms.TmsTraineeHint
import bd.sicip.qavisit.data.tms.buildTmsCourseCatalog
import bd.sicip.qavisit.data.tms.traineeHintsOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

private fun json(text: String): JsonArray = Json.parseToJsonElement(text).jsonArray

private fun card(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

private fun day(text: String) = LocalDate.parse(text)

class SuggestionsTest {
    private val template = parseReportTemplate(File("../../shared/report-templates/surprise-v2.json").readText())
    private val cardsBlocks = template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>()
    private fun block(key: String) = cardsBlocks.first { it.key == key }
    private fun field(blockKey: String, fieldKey: String) = block(blockKey).fields.first { it.key == fieldKey }

    private val welding5 = TmsBatchRef(5, 10, "Welding (SMAW)", "5", day("2025-01-01"), day("2025-04-30"))
    private val welding7 = TmsBatchRef(7, 10, "Welding (SMAW)", "7", day("2026-07-01"), day("2026-10-31"))
    private val electrical3 = TmsBatchRef(3, 11, "Electrical Installation", "3", day("2026-07-15"), day("2026-11-30"))
    private val future = TmsBatchRef(9, 11, "Electrical Installation", "4", day("2026-12-01"), day("2027-03-31"))
    private val catalog = TmsCourseCatalog(
        courseNames = listOf("Welding (SMAW)", "Electrical Installation"),
        batches = listOf(welding5, welding7, electrical3, future),
    )
    private val sources = SuggestionSources(
        catalog = catalog,
        equipment = listOf("Grinder", "Welding machine"),
        traineesByBatch = mapOf(7L to listOf(TmsTraineeHint("Rahim Uddin", "01700000001"), TmsTraineeHint("Karima Begum", ""))),
    )

    @Test
    fun `catalog uses the longer of course name and alias, batches carry the course name`() {
        val targets = json("""[{"id":10,"course_name":"Welding","x_course_name_id":1},{"id":11,"course_name":"Electrical Installation","x_course_name_id":2}]""")
        val aliases = json("""[{"id":1,"name":"Welding (SMAW)"},{"id":2,"name":"Electrical"}]""")
        val batches = json(
            """[{"id":7,"course_info_id":10,"batch_number":7,"start_date":"2026-07-01","end_date":"2026-10-31"},
               {"id":8,"course_info_id":12,"batch_number":"2","start_date":"2026-07-01","end_date":"2026-10-31","course_info":{"course_name":"Plumbing"}}]""",
        )
        val built = buildTmsCourseCatalog(targets, batches, aliases)
        assertEquals(listOf("Welding (SMAW)", "Electrical Installation"), built.courseNames)
        assertEquals(listOf("Welding (SMAW)" to "7", "Plumbing" to "2"), built.batches.map { it.courseName to it.number })
    }

    @Test
    fun `trainee hints keep only name and mobile`() {
        val rows = json("""[{"present":3,"trainee":{"trainee_name":" Rahim Uddin ","mobile":"01700000001","nid":"x"}},{"trainee":{"trainee_name":""}},{"present":1}]""")
        assertEquals(listOf(TmsTraineeHint("Rahim Uddin", "01700000001")), traineeHintsOf(rows))
    }

    @Test
    fun `running batches are those with start on or before and end on or after the visit date`() {
        assertEquals(listOf(welding7, electrical3), runningBatches(catalog, day("2026-07-15")))
        assertEquals(listOf(welding7), runningBatches(catalog, day("2026-07-01")))
        assertEquals(listOf(electrical3), runningBatches(catalog, day("2026-11-30")))
    }

    @Test
    fun `course and batch suggestions, batches newest first`() {
        assertEquals(catalog.courseNames, suggestionsFor(field("courses", "course"), card(), sources))
        assertEquals(listOf("7", "5"), suggestionsFor(field("courses", "batch"), card("course" to " welding (smaw) "), sources))
        assertEquals(emptyList<String>(), suggestionsFor(field("courses", "batch"), card(), sources))
    }

    @Test
    fun `trainee suggestions resolve D's course-batch value and L's course plus batch`() {
        val names = listOf("Rahim Uddin", "Karima Begum")
        assertEquals(names, suggestionsFor(field("identity", "name"), card("batch" to "Welding (SMAW) · 7"), sources))
        assertEquals(names, suggestionsFor(field("graduate", "name"), card("course" to "Welding (SMAW)", "batch" to "07"), sources))
        assertEquals(emptyList<String>(), suggestionsFor(field("graduate", "name"), card("course" to "Welding (SMAW)", "batch" to "5"), sources))
        assertEquals(welding7, traineeBatchOf(card("batch" to "Welding (SMAW) · 7"), catalog))
        assertNull(traineeBatchOf(card("batch" to "Welding (SMAW)"), catalog))
    }

    @Test
    fun `equipment suggestions come from the shared list, unknown kinds give nothing`() {
        assertEquals(listOf("Grinder", "Welding machine"), suggestionsFor(field("equipment", "name"), card(), sources))
        assertEquals(emptyList<String>(), suggestionsFor(field("graduate", "remarks"), card(), sources))
        assertEquals(emptyList<String>(), suggestionsFor(field("courses", "course"), card(), SuggestionSources()))
    }

    @Test
    fun `picking a trainee fills an empty phone only`() {
        val graduate = block("graduate")
        val picked = card("course" to "Welding (SMAW)", "batch" to "7")
        assertEquals("01700000001", traineePhoneFor(graduate, picked, "Rahim Uddin", sources))
        assertNull(traineePhoneFor(graduate, card("course" to "Welding (SMAW)", "batch" to "7", "phone" to "0188"), "Rahim Uddin", sources))
        assertNull(traineePhoneFor(graduate, picked, "Karima Begum", sources)) // no mobile on record
        assertNull(traineePhoneFor(block("identity"), card("batch" to "Welding (SMAW) · 7"), "Rahim Uddin", sources)) // D has no phone field
    }

    @Test
    fun `running batches become section A course cards, blank seeds dropped`() {
        val seeded = ReportData.EMPTY.withCardAdded("courses")
        val withCourses = withRunningCourses(seeded, listOf(welding7, electrical3))
        val cards = withCourses.cards("courses")
        assertEquals(listOf("Welding (SMAW)" to "7", "Electrical Installation" to "3"), cards.map { it.text("course") to it.text("batch") })
        assertEquals(2, cards.map { it.text("_id") }.distinct().size)
        // a course the officer already typed stays, and is not added twice
        val typed = ReportData.EMPTY.withCardAdded("courses", card("_id" to "a", "course" to "Welding (SMAW)", "batch" to "7"))
        assertEquals(2, withRunningCourses(typed, listOf(welding7, electrical3)).cards("courses").size)
    }

    // the report may say "&" for "and", drop the "(EIM)" code, or use TMS's short name or the alias
    @Test
    fun `course typed with ampersand, no code or short name still finds its batches and trainees`() {
        val targets = json("""[{"id":20,"course_name":"Plumbing and Pipe Fitting (PPF)","x_course_name_id":3}]""")
        val aliases = json("""[{"id":3,"name":"PPF"}]""")
        val batches = json("""[{"id":30,"course_info_id":20,"batch_number":4,"start_date":"2026-07-01","end_date":"2026-10-31"}]""")
        val built = buildTmsCourseCatalog(targets, batches, aliases)
        val hints = SuggestionSources(built, traineesByBatch = mapOf(30L to listOf(TmsTraineeHint("Mina Akter", "01700000002"))))
        val traineeField = field("identity", "name")

        assertEquals(listOf("4"), batchesOfCourse("Plumbing & Pipe Fitting", built).map { it.number })
        assertEquals(listOf("4"), batchesOfCourse("PPF", built).map { it.number })
        assertEquals(listOf("Mina Akter"), suggestionsFor(traineeField, card("batch" to "Plumbing & Pipe Fitting · 04"), hints))
    }

    @Test
    fun `existing report courses are kept when linking, filled only when empty`() {
        val typed = ReportData.EMPTY.withCardAdded("courses", card("_id" to "a", "course" to "Own course", "batch" to "1"))
        assertEquals(listOf("Own course"), withRunningCoursesIfEmpty(typed, listOf(welding7)).cards("courses").map { it.text("course") })
        val empty = ReportData.EMPTY.withCardAdded("courses")
        assertEquals(listOf("Welding (SMAW)"), withRunningCoursesIfEmpty(empty, listOf(welding7)).cards("courses").map { it.text("course") })
    }

    @Test
    fun `equipment names to share are the G names, first spelling per key`() {
        val data = ReportData.EMPTY
            .withCardAdded("equipment", card("_id" to "1", "name" to " Grinder "))
            .withCardAdded("equipment", card("_id" to "2", "name" to "grinder"))
            .withCardAdded("equipment", card("_id" to "3", "name" to ""))
            .withCardAdded("equipment", card("_id" to "4", "name" to "Arc welder"))
        assertEquals(listOf("Grinder", "Arc welder"), sharedSuggestionValues(template, data, "equipment"))
        assertEquals("grinder", suggestionKey(" Grinder "))
    }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
