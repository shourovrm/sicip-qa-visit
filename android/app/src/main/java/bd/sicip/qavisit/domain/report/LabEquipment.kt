// lab-standard equipment (asset lab-equipment.json = shared/lab-standards/, built by
// tools/lab_equipment.py from the SICIP-standards repo) for a report's association + course.
// pure; mirrors web lib/labequipment.js, fixture shared/report-templates/fixtures/lab-equipment-1.json.
package bd.sicip.qavisit.domain.report

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

const val LAB_EQUIPMENT_ASSET = "lab-equipment.json"

@Serializable
data class LabCourse(val org: String, val course: String, val equipment: List<String>)

// visit association -> standards organisation where the two lists spell it differently
private val ORG_ALIASES = mapOf("flaxa" to "lfmeab")

// words that say nothing about which course it is
private val FILLER_WORDS = setOf("and", "of", "in", "on", "the", "for", "course", "certificate")

// courseRef value separator, as built by ui/reports/ReportBlocks.kt courseRefOptions
private const val COURSE_BATCH_SEPARATOR = " · "

fun orgKey(association: String): String {
    val key = association.lowercase().replace(Regex("[^a-z0-9]+"), "")
    return ORG_ALIASES[key] ?: key
}

// "Tiles & Marble Works (TMW)" -> [tile, marble, work]
fun courseWords(name: String): List<String> =
    courseKey(name).split(" ")
        .filter { it.isNotEmpty() && it !in FILLER_WORDS }
        .map { word -> if (word.length > 3 && word.endsWith("s")) word.dropLast(1) else word }

// the association's standard courses a typed course name means: the same words; else one name's
// words inside the other ("Welding" ~ "Welding (1G, 2G & 3G)"); else its initials ("RAC")
fun matchingCourses(courses: List<LabCourse>, association: String, courseText: String): List<LabCourse> {
    val org = orgKey(association)
    val ofOrg = courses.filter { orgKey(it.org) == org }
    val typed = courseWords(courseText)
    if (typed.isEmpty()) return emptyList()
    val same = ofOrg.filter { courseWords(it.course) == typed }
    if (same.isNotEmpty()) return same
    val within = ofOrg.filter { course ->
        val words = courseWords(course.course)
        words.containsAll(typed) || typed.containsAll(words)
    }
    if (within.isNotEmpty() || typed.size > 1) return within
    return ofOrg.filter { course -> courseWords(course.course).joinToString("") { it.take(1) } == typed.first() }
}

private fun uniqueNames(courses: List<LabCourse>): List<String> =
    courses.flatMap { it.equipment }.distinctBy { it.lowercase() }

// equipment names to offer: the matched course's list; every course of the association when the
// card names no course or none matches; nothing for an association without standards
fun standardEquipment(courses: List<LabCourse>, association: String, courseText: String): List<String> {
    val matched = matchingCourses(courses, association, courseText)
    if (matched.isNotEmpty()) return uniqueNames(matched)
    val org = orgKey(association)
    return uniqueNames(courses.filter { orgKey(it.org) == org })
}

// the course a card is about: the card field the template names in `suggestCourse`, which holds
// either a plain course or a courseRef "Course · N"
fun cardCourse(field: Field, card: JsonObject): String {
    val key = field.suggestCourse ?: return ""
    val value = card[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val split = value.lastIndexOf(COURSE_BATCH_SEPARATOR)
    return if (split < 0) value else value.substring(0, split).trim()
}

private val labEquipmentJson = Json { ignoreUnknownKeys = true }

fun parseLabCourses(text: String): List<LabCourse> = labEquipmentJson.decodeFromString(text)

// read once per process: the list never changes at runtime
private var labCoursesMemo: List<LabCourse>? = null

fun loadLabCourses(context: Context): List<LabCourse> = synchronized(labEquipmentJson) {
    labCoursesMemo ?: parseLabCourses(context.assets.open(LAB_EQUIPMENT_ASSET).bufferedReader().use { it.readText() })
        .also { labCoursesMemo = it }
}
