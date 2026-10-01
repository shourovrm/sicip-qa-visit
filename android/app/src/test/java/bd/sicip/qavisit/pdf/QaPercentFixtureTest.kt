// QA 1.50 "with Percentage": every case of the shared fixture (web lib/percent.test.js runs the same)
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.parseReportTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class QaPercentFixtureTest {
    private val fixture = Json.parseToJsonElement(File("../../shared/report-templates/fixtures/qa-percent-1.json").readText()).jsonObject
    private val v2 = parseReportTemplate(File("../../shared/report-templates/qa-v2.json").readText())
    private val v1 = parseReportTemplate(File("../../shared/report-templates/qa-v1.json").readText())

    private fun cumulative(template: bd.sicip.qavisit.domain.report.ReportTemplate) =
        template.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Cards>().firstOrNull { it.key == "cumulative" }

    @Test
    fun `every count and base case prints like the fixture`() {
        val cases = fixture["cases"]!!.jsonArray
        assertEquals(16, cases.size)
        cases.forEach { case ->
            val obj = case.jsonObject
            val count = obj["count"]?.jsonPrimitive?.contentOrNull
            val base = obj["base"]?.jsonPrimitive?.contentOrNull
            assertEquals("$count / $base", obj["printed"]!!.jsonPrimitive.content, countWithPercent(count, base))
        }
    }

    @Test
    fun `the whole card prints like the fixture, percentOf from qa-v2 and the v1 fallback`() {
        val card = fixture["card"]!!.jsonObject
        val fields = card["fields"]!!.jsonObject
        val printed = card["printed"]!!.jsonObject
        listOf(cumulative(v2), cumulative(v1)).forEach { block ->
            printed.forEach { (key, expected) ->
                assertEquals(key, expected.jsonPrimitive.content, printedCount(block, key, JsonObject(fields)))
            }
        }
        assertEquals("certified_t", cumulative(v2)!!.fields.first { it.key == "placed_t" }.percentOf)
        assertEquals("12", printedCount(block = null, key = "enrolled_t", card = JsonObject(mapOf("enrolled_t" to JsonPrimitive("12")))))
    }
}
