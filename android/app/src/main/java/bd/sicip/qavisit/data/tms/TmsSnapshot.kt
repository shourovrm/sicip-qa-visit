// what we keep of TMS for one institute on one visit date: counts and names only, never trainee
// records. stored as data.tms.snapshot in the report JSON.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class TmsCourse(
    val name: String,
    val code: String,
    val targetBatches: Int,
    val batchSize: Int,
    val duration: String,
    val enrolledTotal: Int,
    val enrolledFemale: Int,
    val certifiedTotal: Int,
    val certifiedFemale: Int,
    val placedTotal: Int,
    val placedFemale: Int,
    val dropoutTotal: Int? = null, // null = no ended batch with assessment data
    val dropoutFemale: Int? = null,
)

@Serializable
data class TmsRunningBatch(
    val course: String,
    val batchNumber: String,
    val startDate: String,
    val endDate: String,
    val enrolled: Int,
    val female: Int,
    val attendanceToday: Int? = null, // null = no attendance rows that day
    val attendance7day: Double? = null,
)

@Serializable
data class TmsSnapshot(
    val fetchedAt: String,
    val courses: List<TmsCourse>,
    val runningBatches: List<TmsRunningBatch>,
)

// where a report points in TMS.
data class TmsLink(
    val trancheId: Long,
    val entityId: Long,
    val instituteId: Long,
    val instituteNo: String,
    val name: String,
)

// runs the spec's calls sequentially; API-change errors are reported before they propagate.
suspend fun fetchTmsSnapshot(api: TmsApi, link: TmsLink, visitDate: LocalDate, fetchedAt: String): TmsSnapshot {
    val entity = link.entityId
    val tranche = link.trancheId
    val institute = link.instituteId
    try {
        val input = TmsSnapshotInput(
            targets = api.getList(
                "institutetarget/all-list?tranche_id=$tranche&entity_id=$entity&course_info_id=" +
                    "&training_institute_id=$institute&active_status=1",
            ),
            batches = api.getList("batch/list?entity_id=$entity&course_info_id=&institute_info_id=$institute"),
            summary = api.getList(
                "enrollment/batch_summary?entity_id=$entity&tranche_id=$tranche&institute_info_id=$institute",
            ),
        )
        return buildTmsSnapshot(input, visitDate, fetchedAt) { courseId, batchId, date ->
            val rows = api.getList(
                "trainee/date_wise_attendanceReport?entity=$entity&tranche=$tranche&institute=$institute" +
                    "&course=$courseId&batch=$batchId&date=$date",
            )
            presentCountOf(rows) // rows dropped here: trainee records never leave this call
        }
    } catch (e: TmsApiChangeException) {
        api.report(e)
        throw e
    }
}
