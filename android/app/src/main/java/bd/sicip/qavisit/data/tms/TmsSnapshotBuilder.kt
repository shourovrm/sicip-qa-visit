// pure snapshot math: TMS response arrays in, TmsSnapshot out. no network; attendance comes
// through a callback so tests can feed it from a fixture. rules live in the tms-link spec.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

private const val SUMMARY = "enrollment/batch_summary"
private const val CLASS_DAYS_FOR_MEAN = 7
private const val MAX_DAYS_LOOKBACK = 14 // stop walking back over holidays

class TmsSnapshotInput(val targets: JsonArray, val batches: JsonArray, val summary: JsonArray)

// present trainees in one day's report; null = no rows (no class that day).
fun presentCountOf(rows: JsonArray): Int? {
    if (rows.isEmpty()) return null
    return rows.count { row ->
        val attendance = row.objectOrEmpty()["attendance"].objectOrEmpty()
        attendance["is_present"].lenientInt() == 1
    }
}

private class BatchRow(
    val id: Long,
    val courseId: Long,
    val number: String,
    val start: LocalDate?,
    val end: LocalDate?,
    val days: Int,
    val hours: Int,
)

private fun JsonObject.toBatchRow() = BatchRow(
    id = this["id"].lenientLong(),
    courseId = this["course_info_id"].lenientLong(),
    number = this["batch_number"].lenientText(),
    start = parseDate(this["start_date"].lenientText()),
    end = parseDate(this["end_date"].lenientText()),
    days = this["total_training_days"].lenientInt(),
    hours = this["total_training_hours"].lenientInt(),
)

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.take(10)) }.getOrNull()

// (course id, batch id, date) -> present count, null when the day has no rows.
typealias TmsPresentOn = suspend (Long, Long, LocalDate) -> Int?

suspend fun buildTmsSnapshot(
    input: TmsSnapshotInput,
    visitDate: LocalDate,
    fetchedAt: String,
    presentOn: TmsPresentOn,
): TmsSnapshot {
    val summaryRows = input.summary.map { it.objectOrEmpty() }
    val summaryByCourse = summaryRows.associateBy { it["course_info"].objectOrEmpty()["id"].lenientLong() }
    val batchCounts = summaryRows.flatMap { row ->
        row["batch_summary"].requireArray(SUMMARY, "batch_summary").map { it.objectOrEmpty() }
    }.associateBy { it["batch_info"].objectOrEmpty()["id"].lenientLong() }

    val targetRows = input.targets.map { it.objectOrEmpty() }
    val extraRows = summaryRows.map { it["course_info"].objectOrEmpty() }
        .filter { extra -> targetRows.none { it["id"].lenientLong() == extra["id"].lenientLong() } }
    val courseRows = targetRows + extraRows
    val allBatches = input.batches.map { it.objectOrEmpty().toBatchRow() }

    val courses = courseRows.map { courseRow ->
        val courseId = courseRow["id"].lenientLong()
        val totals = summaryByCourse[courseId] ?: JsonObject(emptyMap())
        val ownBatches = allBatches.filter { it.courseId == courseId }
        val dropouts = endedDropouts(ownBatches, batchCounts, visitDate)
        TmsCourse(
            name = courseRow["course_name"].lenientText(),
            code = courseRow["code"].lenientText(),
            targetBatches = courseRow["total_target_batches"].lenientInt(),
            batchSize = courseRow["trainee_per_batch"].lenientInt(),
            duration = durationOf(ownBatches),
            enrolledTotal = totals["enroll_trainee"].lenientInt(),
            enrolledFemale = totals["enroll_female_trainee"].lenientInt(),
            certifiedTotal = totals["certification_trainee"].lenientInt(),
            certifiedFemale = totals["certification_female_trainee"].lenientInt(),
            placedTotal = totals["employment_trainee"].lenientInt(),
            placedFemale = totals["employment_female_trainee"].lenientInt(),
            dropoutTotal = dropouts?.first,
            dropoutFemale = dropouts?.second,
        )
    }

    val courseNames = courseRows.associate { it["id"].lenientLong() to it["course_name"].lenientText() }
    val running = allBatches
        .filter { it.start != null && it.end != null && it.start <= visitDate && visitDate <= it.end }
        .sortedWith(compareBy({ it.courseId }, { it.number.toIntOrNull() ?: Int.MAX_VALUE }))
        .map { batch ->
            val counts = batchCounts[batch.id] ?: JsonObject(emptyMap())
            val attendance = attendanceOf(batch, visitDate, presentOn)
            TmsRunningBatch(
                course = courseNames[batch.courseId] ?: "",
                batchNumber = batch.number,
                startDate = batch.start.toString(),
                endDate = batch.end.toString(),
                enrolled = counts["enroll_trainee"].lenientInt(),
                female = counts["enroll_female_trainee"].lenientInt(),
                attendanceToday = attendance.first,
                attendance7day = attendance.second,
            )
        }
    return TmsSnapshot(fetchedAt, courses, running)
}

// dropout = enrolled who never sat the assessment, counted only for ended batches with
// assessment data (a running batch has assessed = 0, which would read as 100% dropout).
// returns (total, female) or null when no batch qualifies.
private fun endedDropouts(batches: List<BatchRow>, counts: Map<Long, JsonObject>, visitDate: LocalDate): Pair<Int, Int>? {
    var total = 0
    var female = 0
    var qualifying = 0
    for (batch in batches) {
        val ended = batch.end != null && batch.end < visitDate
        val row = counts[batch.id] ?: continue
        if (!ended || row["assessed_trainee"].lenientInt() <= 0) continue
        total += (row["enroll_trainee"].lenientInt() - row["assessed_trainee"].lenientInt()).coerceAtLeast(0)
        female += (row["enroll_female_trainee"].lenientInt() - row["assessed_female_trainee"].lenientInt()).coerceAtLeast(0)
        qualifying++
    }
    return if (qualifying == 0) null else total to female
}

private fun durationOf(batches: List<BatchRow>): String {
    val latest = batches.filter { it.start != null }.maxByOrNull { it.start!! } ?: batches.firstOrNull() ?: return ""
    return "${latest.days} days / ${latest.hours} h"
}

// (present on visit date, mean present over the last 7 class days up to the visit date).
private suspend fun attendanceOf(batch: BatchRow, visitDate: LocalDate, presentOn: TmsPresentOn): Pair<Int?, Double?> {
    val classDayCounts = mutableListOf<Int>()
    var today: Int? = null
    var day = visitDate
    var walked = 0
    while (classDayCounts.size < CLASS_DAYS_FOR_MEAN && walked <= MAX_DAYS_LOOKBACK && !day.isBefore(batch.start ?: visitDate)) {
        val present = presentOn(batch.courseId, batch.id, day)
        if (day == visitDate) today = present
        if (present != null) classDayCounts += present
        day = day.minusDays(1)
        walked++
    }
    if (classDayCounts.isEmpty()) return today to null
    val mean = classDayCounts.average()
    return today to Math.round(mean * 10) / 10.0
}
