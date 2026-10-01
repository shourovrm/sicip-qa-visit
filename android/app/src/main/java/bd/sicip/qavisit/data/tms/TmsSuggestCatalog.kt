// TMS data behind the report's suggestion dropdowns: the linked institute's courses (full names)
// with ALL their batches, one batch's trainees as name + mobile only, and the institute's trainers
// as name + designation only. held in memory by the
// editor, never stored in the report, never logged.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.JsonArray
import java.time.LocalDate

data class TmsBatchRef(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val number: String,
    val start: LocalDate?,
    val end: LocalDate?,
    // every spelling TMS uses for the course (full, short, alias); a report may use any of them
    val names: List<String> = emptyList(),
)

data class TmsCourseCatalog(val courseNames: List<String>, val batches: List<TmsBatchRef>)

data class TmsTraineeHint(val name: String, val mobile: String)

// an institute trainer: name, TMS designation ("" when TMS has none), courses mapped to them
data class TmsTrainerHint(val name: String, val designation: String, val courseIds: Set<Long>)

// course names = institute targets (longer of course_name and its alias); a batch whose course
// is not a target still gets its own course_info name.
fun buildTmsCourseCatalog(targets: JsonArray, batches: JsonArray, aliases: JsonArray): TmsCourseCatalog {
    val aliasNames = aliases.map { it.objectOrEmpty() }.associate { it["id"].lenientLong() to it["name"].lenientText() }
    val courseNameById = targets.map { it.objectOrEmpty() }.associate { row ->
        row["id"].lenientLong() to fullCourseName(row["course_name"].lenientText(), aliasNames[row["x_course_name_id"].lenientLong()])
    }
    val spellingsById = targets.map { it.objectOrEmpty() }.associate { row ->
        row["id"].lenientLong() to listOf(row["course_name"].lenientText(), aliasNames[row["x_course_name_id"].lenientLong()].orEmpty())
    }
    val batchRefs = batches.map { it.objectOrEmpty() }.map { row ->
        val courseId = row["course_info_id"].lenientLong()
        val courseInfo = row["course_info"].objectOrEmpty()
        val fallbackName = fullCourseName(courseInfo["course_name"].lenientText(), aliasNames[courseInfo["x_course_name_id"].lenientLong()])
        val courseName = courseNameById[courseId] ?: fallbackName
        val spellings = listOf(courseName) + spellingsById[courseId].orEmpty() +
            listOf(courseInfo["course_name"].lenientText(), aliasNames[courseInfo["x_course_name_id"].lenientLong()].orEmpty())
        TmsBatchRef(
            id = row["id"].lenientLong(),
            courseId = courseId,
            courseName = courseName,
            names = spellings.filter { it.isNotBlank() }.distinct(),
            number = row["batch_number"].lenientText(),
            start = parseDate(row["start_date"].lenientText()),
            end = parseDate(row["end_date"].lenientText()),
        )
    }
    val names = courseNameById.values.filter { it.isNotEmpty() }.distinct()
    return TmsCourseCatalog(names, batchRefs)
}

// trainee/attendanceReport rows -> name + mobile; every other trainee field is dropped here.
fun traineeHintsOf(rows: JsonArray): List<TmsTraineeHint> = rows.mapNotNull { row ->
    val trainee = row.objectOrEmpty()["trainee"].objectOrEmpty()
    val name = trainee["trainee_name"].lenientText()
    if (name.isEmpty()) null else TmsTraineeHint(name, trainee["mobile"].lenientText())
}

// entity/trainer/list rows -> name + designation + mapped course ids; every other trainer field
// (phone, NID, addresses, certificates) is dropped here.
fun trainerHintsOf(rows: JsonArray): List<TmsTrainerHint> = rows.mapNotNull { row ->
    val trainer = row.objectOrEmpty()
    val employee = trainer["employee_info"].objectOrEmpty()
    val name = employee["name"].lenientText().trim()
    if (name.isEmpty()) return@mapNotNull null
    val mappings = trainer["map_entity_institute_course_trainer"] as? JsonArray ?: JsonArray(emptyList())
    val courseIds = mappings.map { it.objectOrEmpty()["course_info_id"].lenientLong() }.filter { it != 0L }.toSet()
    TmsTrainerHint(name, employee["designation"].lenientText().trim(), courseIds)
}

// the linked institute's active trainers (TMS SPA "trainer list" page uses the same call)
suspend fun fetchTmsTrainers(api: TmsApi, link: TmsLink): List<TmsTrainerHint> =
    trainerHintsOf(
        api.getList(
            "entity/trainer/list?tranche_id=${link.trancheId}&entity_info_id=${link.entityId}" +
                "&institute_info_id=${link.instituteId}&active_status=1",
        ),
    )

suspend fun fetchTmsCourseCatalog(api: TmsApi, link: TmsLink): TmsCourseCatalog {
    val targets = api.getList(
        "institutetarget/all-list?tranche_id=${link.trancheId}&entity_id=${link.entityId}&course_info_id=" +
            "&training_institute_id=${link.instituteId}&active_status=1",
    )
    val batches = api.getList("batch/list?entity_id=${link.entityId}&course_info_id=&institute_info_id=${link.instituteId}")
    return buildTmsCourseCatalog(targets, batches, fetchAliasList(api))
}

// whole-batch attendance report is the one TMS list that names a batch's trainees (ended
// batches included); the rows are reduced to name + mobile before they leave this call.
suspend fun fetchTmsTrainees(api: TmsApi, link: TmsLink, batch: TmsBatchRef): List<TmsTraineeHint> =
    traineeHintsOf(
        api.getList(
            "trainee/attendanceReport?entity=${link.entityId}&tranche=${link.trancheId}&institute=${link.instituteId}" +
                "&course=${batch.courseId}&batch=${batch.id}",
        ),
    )
