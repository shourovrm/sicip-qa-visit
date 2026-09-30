package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsApiChangeException
import bd.sicip.qavisit.data.tms.TmsFixture
import bd.sicip.qavisit.data.tms.TmsLoggedOutException
import bd.sicip.qavisit.data.tms.TmsMessageException
import bd.sicip.qavisit.data.tms.TmsTransientException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TmsReportInfoTest {
    @Test
    fun `time label uses day month and 24 hour time`() {
        assertEquals("29 Sep, 14:10", tmsTimeLabel("2026-09-29T14:10:00Z", ZoneOffset.UTC))
        assertEquals("not a time", tmsTimeLabel("not a time"))
    }

    @Test
    fun `failure wording per exception`() {
        assertEquals(
            "TMS data unavailable: TMS has changed; the admin is informed",
            tmsFillFailure(TmsApiChangeException("shape", "x"), true).message,
        )
        val offline = tmsFillFailure(TmsTransientException("down", offline = true), hasCachedSnapshot = true)
        assertEquals("No connection to TMS. Try again when online.", offline.message)
        assertTrue(offline.canUseCached)
        assertFalse(tmsFillFailure(TmsTransientException("down"), hasCachedSnapshot = false).canUseCached)
        val message = tmsFillFailure(TmsMessageException("Password reset needed"), false)
        assertEquals("Password reset needed", message.message)
        assertTrue(message.openSettings)
        assertTrue(tmsFillFailure(TmsLoggedOutException(), false).openSettings)
    }

    @Test
    fun `hints separate suggestions from matches`() {
        val snapshot = TmsFixture.snapshot()
        val differing = ReportData.EMPTY.withCardsReplaced(
            "cumulative",
            listOf(
                buildJsonObject {
                    put("_id", "a")
                    put("course", "Course A")
                    put("enrolled_t", "99") // differs from TMS
                },
            ),
        )
        val filled = prefillFromTms(snapshot, differing).data
        val hints = TmsHints.of(snapshot, filled)
        assertEquals("22", hints.suggestionFor("cumulative", 0, "enrolled_t")?.tmsValue)
        assertTrue(hints.matchesTms("cumulative", 0, "enrolled_f")) // filled from TMS, now equals it
        assertFalse(hints.matchesTms("cumulative", 0, "enrolled_t"))
        assertFalse(TmsHints.of(null, filled).matchesTms("cumulative", 0, "enrolled_f"))
    }

    @Test
    fun `visit date and prefilled values come from the template's prefill fields`() {
        val template = parseReportTemplate(
            """{"id":"qa","version":2,"title":"t","program":"p","sections":[{"key":"s1","short":"s","title":"t","blocks":[
              {"type":"fields","fields":[
                {"key":"ti_name","label":"i","kind":"text","prefill":"institute"},
                {"key":"date_from","label":"d","kind":"date","prefill":"visit_date"}]}]}]}""",
        )
        val data = ReportData.EMPTY.withField("ti_name", "Dhaka TI").withField("date_from", "2026-09-15")
        assertEquals("Dhaka TI", template.prefilledValue(data, "institute"))
        assertEquals(java.time.LocalDate.of(2026, 9, 15), template.visitDate(data))
        assertEquals(java.time.LocalDate.of(2026, 1, 2), template.visitDate(ReportData.EMPTY, java.time.LocalDate.of(2026, 1, 2)))
    }
}
