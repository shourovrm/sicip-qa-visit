// qa-v2: tick remarks + showIf progress against the shared fixture qa-2.json (web's
// remarksqa2.test.js / qav2.test.js check the same file), evidence numbering, v1 -> v2 convert,
// and the v2 print.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.pdf.buildQaReportHtml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val templatesDir = File("../../shared/report-templates")

private fun strings(element: kotlinx.serialization.json.JsonElement): List<String> = element.jsonArray.map { it.jsonPrimitive.content }

class QaV2Test {
    private val v2 = parseReportTemplate(File(templatesDir, "qa-v2.json").readText())
    private val fixture = Json.parseToJsonElement(File(templatesDir, "fixtures/qa-2.json").readText()).jsonObject

    private fun item(itemId: String): CriteriaItem =
        v2.sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Criteria>().flatMap { it.items }.first { it.id == itemId }

    private fun place(sectionKey: String, itemId: String): String {
        val section = v2.sections.first { it.key == sectionKey }
        val block = section.blocks.filterIsInstance<ReportBlock.Criteria>().first()
        return criteriaPath(section, block, block.items.first { it.id == itemId })
    }

    @Test
    fun `tick remarks match the shared fixture`() {
        fixture["cases"]!!.jsonArray.forEach { case ->
            val obj = case.jsonObject
            val found = item(obj["item"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            val data = ReportData(buildJsonObject { put("criteria", buildJsonObject { put(found.id, obj["entry"]!!) }) })
            val name = obj["case"]!!.jsonPrimitive.content
            assertEquals(name, strings(obj["bullets"]!!), buildRemarks(found, data))
            assertEquals(name, strings(obj["printed"]!!), printedRemarks(found, data))
        }
    }

    @Test
    fun `component notes use the ticks sentence`() {
        val expected = fixture["component_s6"]!!.jsonObject
        val notes = componentNotes(v2, ReportData(fixture["data"]!!.jsonObject), "s6")
        assertEquals(strings(expected["seen"]!!), notes.seen)
        assertEquals(strings(expected["gaps"]!!), notes.gaps)
    }

    @Test
    fun `hidden fields and blocks do not count`() {
        val expected = fixture["progress_s1"]!!.jsonObject
        val progress = computeProgress(v2, ReportData(fixture["profile"]!!.jsonObject)).sections.getValue("s1")
        assertEquals(expected["answered"]!!.jsonPrimitive.int, progress.answered)
        assertEquals(expected["total"]!!.jsonPrimitive.int, progress.total)
        assertEquals(expected["done"]!!.jsonPrimitive.boolean, progress.done)
    }

    @Test
    fun `evidence keeps its first number`() {
        var n = 0
        val newId = { "id-${++n}" }
        assertEquals("7.1b", place("s7", "s7_1b"))
        assertEquals("2.3", place("s2", "s2_3"))
        var data = ReportData.EMPTY
        data = withEvidenceAdded(data, "7.1b", "s7_1b", "Trainers list", newId)
        data = withEvidenceAdded(data, "7.1b", "s7_1b", "Appointment letters", newId)
        data = withEvidenceAdded(data, "8.2", "s8_2", " trainers LIST ", newId)
        assertEquals(listOf("7.1b.a", "7.1b.b"), itemEvidence(data, "s7_1b").map { it.no })
        assertEquals(listOf(EvidenceEntry("id-1", "Trainers list", "7.1b.a")), itemEvidence(data, "s8_2"))
        data = withEvidenceRemoved(data, "s7_1b", "id-1")
        assertEquals(listOf("7.1b.a", "7.1b.b"), usedEvidence(data).map { it.no })
        data = withEvidenceAdded(data, "7.1b", "s7_1b", "Pay slips", newId)
        assertEquals(listOf("7.1b.b", "7.1b.c"), itemEvidence(data, "s7_1b").map { it.no })
        val suggestions = evidenceSuggestions(v2, data, "s7_1b")
        assertTrue(suggestions.contains("Table 1.20: Contract/MoU information"))
        assertFalse(suggestions.contains("Pay slips"))
    }

    @Test
    fun `convert moves profile, answers, ticks, notes and evidence`() {
        val v1Data = ReportData(Json.parseToJsonElement("""
            {"fields": {"officers": "R. M. Shourov, Program Officer (QA)\nS. Akter (Program Officer)", "status": "BTEB registered",
              "contract_with": "bgmea", "signed_date": "2026-01-10", "mou_target": "120", "other_contract": "yes",
              "other_orgs": "ILO", "other_courses": "Welding\nTailoring", "overlapping_courses": "Welding\nIT support",
              "same_facilities": "Same workshop"},
             "cards": {"persons": [], "plan": [{"_id": "p1", "weakness": "x"}]}, "checks": {}, "flags": [],
             "criteria": {
               "s2_1": {"opts": {"visit_log": {"v": "seen"}, "self_appraisal": {"v": "not"}}, "evidence": "Visit register; Logbook"},
               "s3_1": {"opts": {"ttm_registers": {"v": "not", "remark": "no fuel register"}}},
               "s4_2": {"opts": {"applicants_list": {"v": "seen", "detail": "85"}}},
               "s6_2": {"opts": {"cblm": {"v": "seen"}, "tdp": {"v": "seen"}, "job_sheet": {"v": "not"}, "learning_materials": {"v": "seen"}}},
               "s8_6": {"opts": {"monitoring_logbook": {"v": "seen"}}, "note": "Kept by principal"}}}
        """.trimIndent()).jsonObject)
        var n = 0
        val data = convertQaV1ToV2(v2, v1Data) { "id-${++n}" }
        fun text(card: JsonObject, key: String) = card[key]?.jsonPrimitive?.contentOrNull.orEmpty()

        assertEquals(listOf("R. M. Shourov" to "Program Officer (QA)", "S. Akter" to "Program Officer"),
            data.cards("officers").map { text(it, "name") to text(it, "designation") })
        assertEquals("BTEB registered", data.field("other_registration"))
        assertEquals("BGMEA", text(data.cards("mous")[0], "partner"))
        assertEquals(listOf(Triple("ILO", "Welding", "yes"), Triple("ILO", "Tailoring", ""), Triple("ILO", "IT support", "yes")),
            data.cards("contract_courses").map { Triple(text(it, "contract"), text(it, "course"), text(it, "overlap")) })
        assertEquals("Facilities: Same workshop", data.field("comments"))
        assertEquals("85", text(data.cards("selection")[0], "applicants"))

        assertEquals("seen", data.criteriaOptValue("s2_3", "visit_log"))
        assertEquals("seen", data.criteriaOptValue("s2_3", "monitoring_logbook"))
        assertEquals("Kept by principal", data.criteriaNote("s2_3"))
        assertEquals("not", data.criteriaOptValue("s8_5", "stock_register"))
        assertEquals("no fuel register", data.criteriaOptRemark("s8_6", "fuel_register"))
        assertEquals(listOf("cblm", "tdp"), data.criteriaTicks("s6_2"))
        assertEquals(listOf("2.1.a Visit register", "2.1.b Logbook"), itemEvidence(data, "s2_1").map { "${it.no} ${it.name}" })
        assertEquals(1, data.cards("plan").size)
    }

    @Test
    fun `v2 print has the new tables and no Annex-3`() {
        var n = 0
        var data = ReportData(Json.parseToJsonElement("""
            {"fields": {"bteb_registered": "yes", "bteb_reg_no": "B-77", "bteb_courses": "3", "bteb_uptodate": "no", "other_contract": "yes"},
             "cards": {"officers": [{"_id": "o1", "name": "R. M. Shourov", "designation": "Program Officer (QA)"}],
               "mous": [{"_id": "m1", "partner": "Others", "partner_other": "Local chamber", "signed_date": "2026-01-10"}],
               "contracts": [{"_id": "c1", "organisation": "GIZ"}],
               "contract_courses": [{"_id": "k1", "contract": "ILO", "course": "Welding", "overlap": "yes", "facilities": "same"}],
               "rooms": [{"_id": "r1", "course": "Welding", "layout": "separate", "classroom_sft": "300", "workshop_sft": "800", "trainees": "25"}],
               "damaged": [{"_id": "d1", "course": "Welding", "equipment": "Grinder", "count": "2"}]},
             "checks": {}, "flags": [],
             "criteria": {"s6_2": {"opts": {"learning_materials": {"v": "seen"}}, "ticks": ["cblm", "lesson_plan"]}}}
        """.trimIndent()).jsonObject)
        data = withEvidenceAdded(data, "7.1a", "s7_1a", "Trainers list") { "e${++n}" }
        data = withEvidenceAdded(data, "8.2", "s8_2", "Trainers list") { "e${++n}" }
        val html = buildQaReportHtml(v2, data)
        assertFalse(html.contains("Annex-3"))
        assertTrue(html.contains("<td>BTEB</td><td>Yes</td><td>B-77</td><td>3</td><td>No</td>"))
        assertTrue(html.contains("<td>Local chamber</td><td>10/01/2026</td>"))
        assertTrue(html.contains("<td>ILO</td><td>Welding</td><td>Yes</td><td>Same as SICIP</td>"))
        assertTrue(html.contains("<td>GIZ</td><td></td>"))
        assertTrue(html.contains("Classroom - 300 sft and workshop/lab - 800 sft"))
        assertTrue(html.contains("<td>Grinder</td><td>2</td>"))
        assertTrue(html.contains("Available CBLM and lesson plan indicate they cover every unit of competency."))
        assertEquals(2, html.split("7.1a.a – Trainers list").size - 1)
        assertTrue(html.contains("<td>7.1a.a</td><td>Trainers list</td><td>7.1a, 8.2</td>"))
    }
}
