// loads shared/report-templates/fixtures/tms-sample.json (visit date 2026-05-13) and builds a snapshot from it.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.LocalDate

val TMS_SAMPLE_VISIT_DATE: LocalDate = LocalDate.parse("2026-05-13")

object TmsFixture {
    val root: JsonObject =
        Json.parseToJsonElement(File("../../shared/report-templates/fixtures/tms-sample.json").readText()).jsonObject

    fun data(endpoint: String): JsonArray = root.getValue(endpoint).jsonObject.getValue("data").jsonArray

    fun input() = TmsSnapshotInput(
        targets = data("institutetarget/all-list"),
        batches = data("batch/list"),
        summary = data("enrollment/batch_summary"),
    )

    // same input plus the alias master list (Course A -> longer alias, Course B -> shorter alias)
    fun inputWithAliases() = TmsSnapshotInput(
        targets = data("institutetarget/all-list"),
        batches = data("batch/list"),
        summary = data("enrollment/batch_summary"),
        aliases = data("configurations/alias_name/list"),
    )

    fun snapshot(withAliases: Boolean = false): TmsSnapshot = runBlocking {
        buildTmsSnapshot(if (withAliases) inputWithAliases() else input(), TMS_SAMPLE_VISIT_DATE, "2026-05-13T10:00:00Z") { _, batchId, date ->
            val day = root.getValue("_date_wise_by_batch").jsonObject[batchId.toString()]?.jsonObject?.get(date.toString())
            day?.let { presentCountOf(it.jsonObject.getValue("data").jsonArray) }
        }
    }
}
