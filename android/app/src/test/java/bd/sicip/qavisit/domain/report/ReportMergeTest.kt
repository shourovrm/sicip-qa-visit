// three-way report merge must match the shared fixture web's reportmerge.test.js also checks
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReportMergeTest {
    @Test
    fun `merge matches the shared fixture`() {
        val cases = Json.parseToJsonElement(File("../../shared/report-templates/fixtures/merge-1.json").readText()).jsonArray
        cases.forEach { element ->
            val case = element.jsonObject
            val base = case["base"] as? JsonObject
            val merged = mergeReportData(base, case.getValue("local").jsonObject, case.getValue("server").jsonObject)
            assertEquals(case.getValue("name").jsonPrimitive.content, case.getValue("expected"), merged)
        }
    }
}
