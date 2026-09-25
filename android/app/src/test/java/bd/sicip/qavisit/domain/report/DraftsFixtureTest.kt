// contract test: shared/report-templates/fixtures/drafts-qa-1.json (reference.py
// fixture_drafts_qa_1) -- every draft helper in Drafts.kt checked key by key. Read off disk via
// the same relative path the other fixture tests use (gradle runs with android/app as cwd).
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

private val templatesDir = File("../../shared/report-templates")
private val fixture: JsonObject =
    Json.parseToJsonElement(File(templatesDir, "fixtures/drafts-qa-1.json").readText()).jsonObject
private val template = parseReportTemplate(File(templatesDir, fixture.getValue("template").jsonPrimitive.content).readText())
private val data = ReportData(fixture.getValue("data").jsonObject)

private fun strings(element: JsonElement): List<String> = element.jsonArray.map { it.jsonPrimitive.content }

private fun draftOf(element: JsonElement): StrengthsDraft? {
    if (element is JsonNull) return null
    val obj = element.jsonObject
    return StrengthsDraft(strings(obj.getValue("strengths")), strings(obj.getValue("weaknesses")))
}

class DraftsFixtureTest {
    @Test
    fun `component_s8 notes`() {
        val expected = fixture.getValue("component_s8").jsonObject
        val notes = componentNotes(template, data, "s8")
        assertEquals(strings(expected.getValue("seen")), notes.seen)
        assertEquals(strings(expected.getValue("gaps")), notes.gaps)
        assertEquals(strings(expected.getValue("notes")), notes.notes)
        assertEquals(strings(expected.getValue("feedback")), notes.feedback)
    }

    @Test
    fun `fallback and prompt for s8`() {
        val notes = componentNotes(template, data, "s8")
        assertEquals(draftOf(fixture.getValue("fallback_s8")), fallbackDraft(notes))
        assertEquals(fixture.getValue("prompt_s8").jsonPrimitive.content, strengthsPromptText(notes))
    }

    @Test
    fun `has_notes for untouched s3`() {
        assertEquals(fixture.getValue("has_notes_s3").jsonPrimitive.boolean, hasNotes(componentNotes(template, data, "s3")))
    }

    @Test
    fun `parse_strengths cases`() {
        fixture.getValue("parse_strengths").jsonArray.forEach { case ->
            val text = case.jsonObject.getValue("text").jsonPrimitive.content
            assertEquals(text, draftOf(case.jsonObject.getValue("expected")), parseStrengthsAnswer(text))
        }
    }

    @Test
    fun `weaknesses and plan prompt`() {
        val weaknesses = allWeaknesses(template, data)
        assertEquals(strings(fixture.getValue("weaknesses")), weaknesses)
        assertEquals(fixture.getValue("plan_prompt").jsonPrimitive.content, numberedText(weaknesses))
    }

    @Test
    fun `parse_numbered cases`() {
        fixture.getValue("parse_numbered").jsonArray.forEach { case ->
            val obj = case.jsonObject
            val text = obj.getValue("text").jsonPrimitive.content
            val expected = obj.getValue("expected")
            val actual = parseNumbered(text, obj.getValue("count").jsonPrimitive.int)
            if (expected is JsonNull) assertNull(text, actual) else assertEquals(text, strings(expected), actual)
        }
    }

    @Test
    fun `plan cards with and without AI, and recommendations`() {
        val weaknesses = allWeaknesses(template, data)
        val actions = strings(fixture.getValue("actions"))
        val existing = data.cards("plan")
        val planAi = planCards(weaknesses, actions, existing) { "new-$it" }
        val planFailed = planCards(weaknesses, null, existing) { "new-$it" }
        assertEquals(fixture.getValue("plan_ai") as JsonArray, JsonArray(planAi))
        assertEquals(fixture.getValue("plan_failed") as JsonArray, JsonArray(planFailed))
        assertEquals(fixture.getValue("recommendations").jsonPrimitive.content, recommendationsFromPlan(planAi))
    }

    @Test
    fun `qa template decodes the new optional keys`() {
        val s13 = template.sections.first { it.key == "s13" }.blocks.filterIsInstance<ReportBlock.Fields>().single()
        assertEquals(9, s13.pairs.size)
        val feedback = template.sections.first { it.key == "s11" }.blocks.filterIsInstance<ReportBlock.Cards>().single()
        assertEquals(true, feedback.anonymous)
        val plan = template.sections.first { it.key == "s16" }.blocks.filterIsInstance<ReportBlock.Cards>().single()
        assertEquals("weaknesses", plan.draftFrom)
    }
}
