package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TmsSnapshotBuilderTest {
    private val snapshot = TmsFixture.snapshot()
    private val courseA = snapshot.courses.first { it.name == "Course A" }
    private val courseB = snapshot.courses.first { it.name == "Course B" }

    @Test
    fun `course facts come from targets and latest batch`() {
        assertEquals(2, snapshot.courses.size)
        assertEquals(10, courseA.targetBatches)
        assertEquals(20, courseA.batchSize)
        assertEquals("60 days / 300 h", courseA.duration)
        assertEquals("48 days / 240 h", courseB.duration)
    }

    @Test
    fun `cumulative counts parse numbers and strings`() {
        assertEquals(22, courseA.enrolledTotal)
        assertEquals(9, courseA.enrolledFemale)
        assertEquals(8, courseA.certifiedTotal)
        assertEquals(2, courseA.placedFemale)
        assertEquals(10, courseB.enrolledTotal) // "10" string in the fixture
    }

    @Test
    fun `dropout counts only ended batches with assessment data`() {
        // ended batch 9001: 10 enrolled, 8 assessed, female 4 vs 3; running batch 9002 is ignored
        assertEquals(2, courseA.dropoutTotal)
        assertEquals(1, courseA.dropoutFemale)
        // course B: its only ended batch has assessed = 0, so no dropout figure at all
        assertNull(courseB.dropoutTotal)
        assertNull(courseB.dropoutFemale)
    }

    @Test
    fun `only batches running on the visit date are listed`() {
        assertEquals(1, snapshot.runningBatches.size)
        val batch = snapshot.runningBatches.single()
        assertEquals("Course A", batch.course)
        assertEquals("2", batch.batchNumber)
        assertEquals("2026-04-12", batch.startDate)
        assertEquals(12, batch.enrolled)
        assertEquals(5, batch.female)
    }

    @Test
    fun `attendance today is the present count on the visit date`() {
        assertEquals(4, snapshot.runningBatches.single().attendanceToday)
    }

    @Test
    fun `seven day mean skips days without rows and stops at seven class days`() {
        // class days 13,12,11,10,7,6,5 May -> 4+5+3+4+5+4+4 = 29 / 7; 9 and 8 May have no rows; 4 May is the 8th day
        assertEquals(4.1, snapshot.runningBatches.single().attendance7day!!, 0.0001)
    }

    @Test
    fun `no attendance rows anywhere gives null`() = runBlocking {
        val result = buildTmsSnapshot(TmsFixture.input(), TMS_SAMPLE_VISIT_DATE, "t") { _, _, _ -> null }
        assertNull(result.runningBatches.single().attendanceToday)
        assertNull(result.runningBatches.single().attendance7day)
    }

    @Test
    fun `presentCountOf is null for empty and counts is_present`() {
        assertNull(presentCountOf(JsonArray(emptyList())))
        assertEquals(4, presentCountOf(TmsFixture.data("trainee/date_wise_attendanceReport")))
    }

    @Test
    fun `batch_summary that is not a list is a shape error`() {
        val broken = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"course_info":{"id":101},"batch_summary":"oops"}]""",
        ) as JsonArray
        val error = assertThrows(TmsApiChangeException::class.java) {
            runBlocking {
                buildTmsSnapshot(TmsSnapshotInput(TmsFixture.data("institutetarget/all-list"), JsonArray(emptyList()), broken), TMS_SAMPLE_VISIT_DATE, "t") { _, _, _ -> null }
            }
        }
        assertEquals("shape", error.kind)
        assertEquals("enrollment/batch_summary", error.endpoint)
    }
}
