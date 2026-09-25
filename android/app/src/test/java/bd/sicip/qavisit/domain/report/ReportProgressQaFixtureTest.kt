// contract test (QA report spec §5) for the `criteria` block progress rule -- mirrors
// ReportProgressFixtureTest.kt exactly, just against qa-v1.json + fixtures/progress-qa-1.json
// instead of surprise-v1.json + progress-1.json. No before_sync/synced pair here: qa-v1.json has
// no linked cards or perCourse checklist items for normalize() to reconcile, so the fixture is
// just {template, data, expected}.
package bd.sicip.qavisit.domain.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@Serializable
private data class QaExpectedSection(val answered: Int, val total: Int, val done: Boolean, val flagged: Boolean, val notSeen: Int = 0)

@Serializable
private data class QaExpectedProgress(
    val sections: Map<String, QaExpectedSection>,
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
private data class QaFixtureFile(
    val template: String,
    val data: JsonObject,
    val expected: QaExpectedProgress,
)

private val fixtureJson = Json { ignoreUnknownKeys = true }

private fun loadFixture(): Pair<ReportTemplate, QaFixtureFile> {
    val templatesDir = File("../../shared/report-templates")
    val fixtureText = File(templatesDir, "fixtures/progress-qa-1.json").readText()
    val fixture = fixtureJson.decodeFromString(QaFixtureFile.serializer(), fixtureText)
    val template = parseReportTemplate(File(templatesDir, fixture.template).readText())
    return template to fixture
}

class ReportProgressQaFixtureTest {
    @Test
    fun `progress-qa-1 fixture matches expected exactly`() {
        val (template, fixture) = loadFixture()
        val data = ReportData(fixture.data)

        val progress = computeProgress(template, data)

        fixture.expected.sections.forEach { (key, expectedSection) ->
            val actual = progress.sections[key]
            assertEquals("section '$key' answered", expectedSection.answered, actual?.answered)
            assertEquals("section '$key' total", expectedSection.total, actual?.total)
            assertEquals("section '$key' done", expectedSection.done, actual?.done)
            assertEquals("section '$key' flagged", expectedSection.flagged, actual?.flagged)
            assertEquals("section '$key' notSeen", expectedSection.notSeen, actual?.notSeenCount)
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
