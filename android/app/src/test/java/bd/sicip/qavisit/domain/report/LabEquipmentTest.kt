// contract test: shared/report-templates/fixtures/lab-equipment-1.json holds the course-matching
// cases the web (lib/labequipment.test.js) runs too, against the real bundled equipment list.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Serializable
private data class LabCase(
    val association: String,
    val course: String,
    val expect: List<String>,
    val equipmentHas: List<String> = emptyList(),
    val equipmentLacks: List<String> = emptyList(),
    val equipmentCount: Int? = null,
)

@Serializable
private data class LabFixture(val cases: List<LabCase>)

class LabEquipmentTest {
    private val courses = parseLabCourses(File("../../shared/lab-standards/lab-equipment.json").readText())
    private val fixture = Json { ignoreUnknownKeys = true }
        .decodeFromString<LabFixture>(File("../../shared/report-templates/fixtures/lab-equipment-1.json").readText())

    @Test
    fun `fixture cases match the same standard courses and equipment as the web`() {
        for (case in fixture.cases) {
            val label = "${case.association} / \"${case.course}\""
            assertEquals(label, case.expect, matchingCourses(courses, case.association, case.course).map { it.course })
            val equipment = standardEquipment(courses, case.association, case.course)
            case.equipmentHas.forEach { assertTrue("$label has $it", it in equipment) }
            case.equipmentLacks.forEach { assertFalse("$label lacks $it", it in equipment) }
            case.equipmentCount?.let { assertEquals(label, it, equipment.size) }
        }
    }

    @Test
    fun `visit associations map to standards organisations`() {
        assertEquals(orgKey("ISC-T&H"), orgKey("ISC-TH"))
        assertEquals(orgKey("LFMEAB"), orgKey("FLAXA"))
    }

    @Test
    fun `card course comes from a plain value or a course-batch reference`() {
        val nameField = Field(key = "name", label = "Equipment name", kind = "text", suggestCourse = "batch")
        assertEquals("Welding", cardCourse(nameField, JsonObject(mapOf("batch" to JsonPrimitive("Welding · 06")))))
        assertEquals("Welding", cardCourse(nameField.copy(suggestCourse = "course"), JsonObject(mapOf("course" to JsonPrimitive("Welding")))))
        assertEquals("", cardCourse(nameField.copy(suggestCourse = null), JsonObject(mapOf("course" to JsonPrimitive("Welding")))))
    }
}
