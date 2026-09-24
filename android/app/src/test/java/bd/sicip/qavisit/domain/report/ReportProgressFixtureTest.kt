// contract test (spec: "Both platforms MUST have a unit test that loads it and matches
// `expected` exactly"). Reads shared/report-templates/ directly off disk via a relative path --
// gradle runs unit tests with the :app module dir (android/app) as the working directory, so
// "../../shared/..." lands on the repo-root shared/ folder both platforms read from. No test
// fixture is duplicated into this module: a change to the shared JSON is what this test reacts to.
//
// CHANGE SET 2 added a second contract on top of progress: normalize(before_sync) must equal
// `synced` exactly (deep equal) -- reference.py's fixture_1() proves both rules against the same
// hand-built scenario (a stale linked card whose source is gone gets dropped, an existing linked
// card keeps its own answers, a newly-linkable source gets a fresh target card). CHANGE SET 3
// folded syncPerCourse into that same normalize() step (a perCourse item's per-course answers
// derive its overall answer once section A has 2+ named courses).
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@Serializable
private data class ExpectedSection(val answered: Int, val total: Int, val done: Boolean, val flagged: Boolean)

@Serializable
private data class ExpectedProgress(
    val sections: Map<String, ExpectedSection>,
    val sectionsDone: Int,
    val sectionsCounted: Int,
    val answerCounts: Map<String, Int>,
    val unansweredCount: Int,
    val firstUnanswered: String? = null,
    val flagsTicked: List<String>,
    val customFlags: List<String>,
    val sectionsWithContent: List<String>,
)

@Serializable
private data class FixtureFile(
    val template: String,
    @SerialName("before_sync") val beforeSync: JsonObject,
    val synced: JsonObject,
    val data: JsonObject,
    val expected: ExpectedProgress,
)

private val fixtureJson = Json { ignoreUnknownKeys = true }

private fun loadFixture(): Pair<ReportTemplate, FixtureFile> {
    val templatesDir = File("../../shared/report-templates")
    val fixtureText = File(templatesDir, "fixtures/progress-1.json").readText()
    val fixture = fixtureJson.decodeFromString(FixtureFile.serializer(), fixtureText)
    val template = parseReportTemplate(File(templatesDir, fixture.template).readText())
    return template to fixture
}

class ReportProgressFixtureTest {
    @Test
    fun `normalize(before_sync) matches synced exactly`() {
        val (template, fixture) = loadFixture()

        val actual = normalize(template, ReportData(fixture.beforeSync))

        // deep-equal on the parsed JsonObject, not the raw string -- key order inside an
        // object never matters for this contract, only the structure/values do.
        assertEquals(ReportData(fixture.synced), actual)
    }

    @Test
    fun `progress-1 fixture matches expected exactly`() {
        val (template, fixture) = loadFixture()
        val data = ReportData(fixture.data)

        val progress = computeProgress(template, data)

        // section-by-section first, so a mismatch names the offending section key directly
        // instead of a single opaque map-equality failure.
        fixture.expected.sections.forEach { (key, expectedSection) ->
            val actual = progress.sections[key]
            assertEquals("section '$key' answered", expectedSection.answered, actual?.answered)
            assertEquals("section '$key' total", expectedSection.total, actual?.total)
            assertEquals("section '$key' done", expectedSection.done, actual?.done)
            assertEquals("section '$key' flagged", expectedSection.flagged, actual?.flagged)
        }
        assertEquals(fixture.expected.sections.keys, progress.sections.keys)

        assertEquals("sectionsDone", fixture.expected.sectionsDone, progress.sectionsDone)
        assertEquals("sectionsCounted", fixture.expected.sectionsCounted, progress.sectionsCounted)
        assertEquals("answerCounts", fixture.expected.answerCounts, progress.answerCounts)
        assertEquals("unansweredCount", fixture.expected.unansweredCount, progress.unansweredCount)
        assertEquals("firstUnanswered", fixture.expected.firstUnanswered, progress.firstUnanswered)
        assertEquals("flagsTicked", fixture.expected.flagsTicked, progress.flagsTicked)
        assertEquals("customFlags", fixture.expected.customFlags, progress.customFlags)
        assertEquals("sectionsWithContent", fixture.expected.sectionsWithContent, progress.sectionsWithContent)
    }
}
