// template `suggest` fields: which values to offer for one card field. suggestions never restrict
// the input (free text stays valid). pure: the TMS catalog, trainee lists and the shared equipment
// list come in as SuggestionSources, loaded and held in memory by the editor.
//   tmsCourse        -> full course names of the linked institute
//   tmsBatch         -> batch numbers of the card's `course`, all batches, newest first
//   tmsTrainee       -> trainees of the card's course + batch (D: "Course · N"; L: course + batch)
//   shared:equipment -> the shared equipment-name list
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsBatchRef
import bd.sicip.qavisit.data.tms.TmsCourseCatalog
import bd.sicip.qavisit.data.tms.TmsTraineeHint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID

const val SUGGEST_TMS_COURSE = "tmsCourse"
const val SUGGEST_TMS_BATCH = "tmsBatch"
const val SUGGEST_TMS_TRAINEE = "tmsTrainee"
const val SUGGEST_EQUIPMENT = "shared:equipment"

// courseRef value separator, as built by ui/reports/ReportBlocks.kt courseRefOptions
private const val COURSE_BATCH_SEPARATOR = " · "

data class SuggestionSources(
    val catalog: TmsCourseCatalog? = null,
    val equipment: List<String> = emptyList(),
    val traineesByBatch: Map<Long, List<TmsTraineeHint>> = emptyMap(),
)

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun sameCourse(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

// a course typed in the report may be the full name, TMS's short name or the alias, with "and" for
// "&" and without the "(EIM)" code: compare on a folded key (mirrors web lib/tmscatalog.js courseKey)
fun courseKey(name: String): String =
    name.trim().lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun isCourse(batch: TmsBatchRef, course: String): Boolean {
    val wanted = courseKey(course)
    if (wanted.isEmpty()) return false
    return (listOf(batch.courseName) + batch.names).any { courseKey(it) == wanted }
}

// "07" and "7" are the same batch
private fun sameBatchNumber(a: String, b: String): Boolean {
    val left = a.trim()
    val right = b.trim()
    val leftNumber = left.toIntOrNull()
    val rightNumber = right.toIntOrNull()
    return if (leftNumber != null && rightNumber != null) leftNumber == rightNumber else left.equals(right, ignoreCase = true)
}

fun batchesOfCourse(course: String, catalog: TmsCourseCatalog): List<TmsBatchRef> =
    catalog.batches
        .filter { isCourse(it, course) }
        .sortedWith(compareByDescending<TmsBatchRef> { it.start ?: LocalDate.MIN }.thenByDescending { it.number.toIntOrNull() ?: 0 })

// batches running on the visit date (start <= date <= end), in the institute's course order
// (non-target courses last), then batch number
fun runningBatches(catalog: TmsCourseCatalog, visitDate: LocalDate): List<TmsBatchRef> {
    fun courseRank(batch: TmsBatchRef) = catalog.courseNames.indexOf(batch.courseName).let { if (it < 0) Int.MAX_VALUE else it }
    return catalog.batches
        .filter { batch -> batch.start != null && batch.end != null && batch.start <= visitDate && visitDate <= batch.end }
        .sortedWith(compareBy<TmsBatchRef>(::courseRank).thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE })
}

// the TMS batch a trainee card points at: course + batch fields (L), else a courseRef
// "Course · N" value in `batch` (D). null when either part is missing or unknown to TMS.
fun traineeBatchOf(card: JsonObject, catalog: TmsCourseCatalog): TmsBatchRef? {
    var course = card.text("course")
    var batchNumber = card.text("batch")
    if (course.isEmpty()) {
        val split = batchNumber.lastIndexOf(COURSE_BATCH_SEPARATOR)
        if (split < 0) return null
        course = batchNumber.substring(0, split)
        batchNumber = batchNumber.substring(split + COURSE_BATCH_SEPARATOR.length)
    }
    if (course.isBlank() || batchNumber.isBlank()) return null
    return catalog.batches.firstOrNull { isCourse(it, course) && sameBatchNumber(it.number, batchNumber) }
}

private fun traineesOf(card: JsonObject, sources: SuggestionSources): List<TmsTraineeHint> {
    val catalog = sources.catalog ?: return emptyList()
    val batch = traineeBatchOf(card, catalog) ?: return emptyList()
    return sources.traineesByBatch[batch.id].orEmpty()
}

fun suggestionsFor(field: Field, card: JsonObject, sources: SuggestionSources): List<String> = when (field.suggest) {
    SUGGEST_TMS_COURSE -> sources.catalog?.courseNames.orEmpty()
    SUGGEST_TMS_BATCH -> sources.catalog?.let { catalog -> batchesOfCourse(card.text("course"), catalog).map { it.number }.distinct() }.orEmpty()
    SUGGEST_TMS_TRAINEE -> traineesOf(card, sources).map { it.name }.distinct()
    SUGGEST_EQUIPMENT -> sources.equipment
    else -> emptyList()
}

// after a trainee is picked: the mobile to put in the card's empty phone field, else null
fun traineePhoneFor(block: ReportBlock.Cards, card: JsonObject, pickedName: String, sources: SuggestionSources): String? {
    val phoneField = block.fields.firstOrNull { it.key == "phone" } ?: return null
    if (card.text(phoneField.key).isNotEmpty()) return null
    val trainee = traineesOf(card, sources).firstOrNull { it.name == pickedName } ?: return null
    return trainee.mobile.ifEmpty { null }
}

// report creation with a TMS link: one section-A course card per running batch. blank seeded
// cards go; a course + batch already typed is kept and not added twice.
fun withRunningCourses(data: ReportData, running: List<TmsBatchRef>): ReportData {
    val kept = data.cards("courses").filter { it.text("course").isNotEmpty() || it.text("batch").isNotEmpty() }
    val added = running
        .filter { batch -> kept.none { sameCourse(it.text("course"), batch.courseName) && sameBatchNumber(it.text("batch"), batch.number) } }
        .map { batch ->
            JsonObject(
                mapOf(
                    "_id" to JsonPrimitive(UUID.randomUUID().toString()),
                    "course" to JsonPrimitive(batch.courseName),
                    "batch" to JsonPrimitive(batch.number),
                ),
            )
        }
    return data.withCardsReplaced("courses", kept + added)
}

// linking an existing report: section A is only filled while no course card has a course typed,
// so the officer's own courses are never touched (same rule as the web's refresh)
fun withRunningCoursesIfEmpty(data: ReportData, running: List<TmsBatchRef>): ReportData {
    val hasCourse = data.cards("courses").any { it.text("course").isNotEmpty() }
    return if (hasCourse) data else withRunningCourses(data, running)
}

// same folding as the server's value_key: "Grinder " and "grinder" are one entry
fun suggestionKey(value: String): String = value.trim().lowercase()

// every non-blank value the report holds in fields suggesting from shared list `list`
// (e.g. "equipment" <- G equipment names), first spelling per key, report order
fun sharedSuggestionValues(template: ReportTemplate, data: ReportData, list: String): List<String> {
    val suggestKey = "shared:$list"
    val values = template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().flatMap { block ->
        val keys = block.fields.filter { it.suggest == suggestKey }.map { it.key }
        data.cards(block.key).flatMap { card -> keys.map { card.text(it) } }
    }
    return values.filter { it.isNotEmpty() }.distinctBy(::suggestionKey)
}

// any field offering suggestions: only then does the editor load the lists
fun ReportTemplate.hasSuggestFields(): Boolean =
    sections.flatMap { it.blocks }.any { block ->
        when (block) {
            is ReportBlock.Cards -> block.fields.any { it.suggest != null }
            is ReportBlock.Fields -> block.fields.any { it.suggest != null }
            else -> false
        }
    }
