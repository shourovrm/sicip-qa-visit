// surprise v2: remark lines + finding candidates must match the shared fixture the web
// (sectionremarks.test.js) is also checked against; plus the v1 -> v2 converter and the two PDFs.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.pdf.buildNarrativeReportHtml
import bd.sicip.qavisit.pdf.buildReportHtml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val templatesDir = File("../../shared/report-templates")

private fun template(name: String) = parseReportTemplate(File(templatesDir, name).readText())

private fun lines(element: kotlinx.serialization.json.JsonElement): List<RemarkLine> =
    element.jsonArray.map { RemarkLine(it.jsonObject["text"]!!.jsonPrimitive.content, it.jsonObject["neg"]!!.jsonPrimitive.boolean) }

class SurpriseV2Test {
    private val v2 = template("surprise-v2.json")
    private val fixture = Json.parseToJsonElement(File(templatesDir, "fixtures/remarks-surprise-2.json").readText()).jsonObject
    private val data = ReportData(fixture["data"]!!.jsonObject)

    @Test
    fun `remark lines match the shared fixture`() {
        val built = fixture["built"]!!.jsonObject
        v2.sections.forEach { section ->
            section.blocks.filterIsInstance<ReportBlock.Remarks>().forEach { block ->
                assertEquals(block.key, lines(built[block.key]!!), buildRemarkLines(v2, section, block, data))
            }
        }
    }

    @Test
    fun `finding candidates match the shared fixture`() {
        assertEquals(lines(fixture["candidates"]!!), findingCandidates(v2, data))
    }

    @Test
    fun `manual remarks build nothing and print the typed lines`() {
        val section = v2.sections.first { it.key == "interview" }
        val block = section.blocks.filterIsInstance<ReportBlock.Remarks>().first { it.key == "interview_remarks" }
        assertTrue(block.manual)
        assertEquals(emptyList<RemarkLine>(), buildRemarkLines(v2, section, block, data))
        val typed = data.withRemarks("interview_remarks", "", " First point \n\nSecond point")
        assertEquals(listOf(RemarkLine("First point", false), RemarkLine("Second point", false)), printedRemarkLines(v2, section, block, typed))
        assertEquals(emptyList<RemarkLine>(), printedRemarkLines(v2, section, block, data.withRemarksCleared("interview_remarks")))
    }

    @Test
    fun `sections run A to N with registers after materials`() {
        assertEquals("ABCDEFGHIJKLMN", v2.sections.joinToString("") { it.letter })
        assertEquals(listOf("materials", "registers", "delivery"), v2.sections.map { it.key }.subList(4, 7))
    }

    @Test
    fun `optional segments and blank keys`() {
        val lookup = mapOf("a" to "A", "b" to "")
        assertEquals("A x.", fillSays("{a}[ y {b}] x", { lookup[it].orEmpty() }))
        assertEquals(null, fillSays("{b} x", { lookup[it].orEmpty() }))
        assertEquals("A (A).", fillSays("{a}[ ({a}[ {b}])]", { lookup[it].orEmpty() }))
    }

    @Test
    fun `major answer parse keeps range and order`() {
        assertEquals(listOf(2, 0), parseMajorAnswer("3, 1, 9, 3", 4))
        assertEquals(null, parseMajorAnswer("none", 4))
        assertEquals(null, parseMajorAnswer("1. No 3 phase line.\n2. Earthing missing.", 4))
    }

    @Test
    fun `gap compare warns only on a large shortfall`() {
        val block = v2.sections.first { it.key == "attendance" }.blocks.filterIsInstance<ReportBlock.Cards>().first()
        val compare = block.compare!!
        fun card(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to kotlinx.serialization.json.JsonPrimitive(it.second) })
        assertFalse(cardCompareMismatch(compare, card("present_total" to "22", "register_avg7" to "23.4")))
        assertTrue(cardCompareMismatch(compare, card("present_total" to "14", "tms_avg7" to "24")))
        assertFalse(compare.print)
    }

    @Test
    fun `v1 report converts to v2 keeping answers`() {
        val v1Data = ReportData.parse(
            """{"fields":{"officers":"Rafiq (QA Specialist)\nNusrat","key_findings":"Low attendance\nNo CBLM","instructions_given":"Fix register","follow_up":"Full QA visit"},
               "checks":{"registers_3":{"answer":"no","remarks":"none kept"}},
               "cards":{"courses":[{"_id":"c1","course":"Welding","batch":"07"}],
                        "attendance":[{"_id":"attendance:c1","_link":"c1","course":"Welding","batch":"07","trainers_present":"1"}],
                        "graduate":[{"_id":"g1","batch":"Welding 05","confirmed":"same"}]},"flags":["flag_1"]}""",
        )
        var next = 0
        val out = convertSurpriseV1ToV2(v2, v1Data) { "id${next++}" }
        assertEquals(listOf("Rafiq", "Nusrat"), out.cards("officers").map { it["name"]!!.jsonPrimitive.content })
        assertEquals("QA Specialist", out.cards("officers")[0]["designation"]!!.jsonPrimitive.content)
        assertEquals("no", out.checkAnswer("followup_1"))
        assertEquals("1", out.cards("trainers").single()["present"]!!.jsonPrimitive.content)
        assertEquals("Welding 05", out.cards("graduate").single()["course"]!!.jsonPrimitive.content)
        assertEquals("employed", out.cards("graduate").single()["confirmed"]!!.jsonPrimitive.content)
        assertEquals(listOf("Low attendance", "No CBLM"), out.findings().map { it.text })
        assertEquals("Fix register\nRecommended follow-up: Full QA visit", out.field("recommendations"))
        assertEquals(setOf("flag_1"), out.flags()) // v1 data is kept, just not shown

        // revert copies v2 answers back to their v1 places
        val edited = out.withFindings(listOf(Finding("", "Edited finding")))
            .withCardsReplaced("trainers", out.cards("trainers").map { JsonObject(it + ("present" to kotlinx.serialization.json.JsonPrimitive("2"))) })
        val back = revertSurpriseV2ToV1(template("surprise-v1.json"), edited)
        assertEquals("Rafiq (QA Specialist)\nNusrat", back.field("officers"))
        assertEquals("Edited finding", back.field("key_findings"))
        assertEquals("no", back.checkAnswer("registers_3"))
        assertEquals("2", back.cards("attendance").single()["trainers_present"]!!.jsonPrimitive.content)
        assertEquals("Welding 05", back.cards("graduate").single()["batch"]!!.jsonPrimitive.content)
        assertEquals("same", back.cards("graduate").single()["confirmed"]!!.jsonPrimitive.content)
    }

    @Test
    fun `both PDFs print remarks as bullets and findings numbered, no legend`() {
        val withFindings = data.withFindings(listOf(Finding("", "Low attendance in EIM 03"))).withField("recommendations", "The institute should fix it.")
        listOf(buildReportHtml(v2, withFindings), buildNarrativeReportHtml(v2, withFindings)).forEach { html ->
            assertTrue(html.contains("<li class=\"neg\">The training calendar was not displayed"))
            assertTrue(html.contains("<ol class=\"findings\"><li>Low attendance in EIM 03</li></ol>"))
            assertTrue(html.contains("<li>The institute should fix it.</li>"))
            assertFalse(html.contains("Training Management System"))
            assertFalse(html.contains("Headcount is far below"))
        }
    }
}
