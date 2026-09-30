package bd.sicip.qavisit.ui.visits

import bd.sicip.qavisit.data.tms.TmsApiChangeException
import bd.sicip.qavisit.data.tms.TmsMessageException
import bd.sicip.qavisit.data.tms.TmsTransientException
import org.junit.Assert.assertEquals
import org.junit.Test

class TmsInstituteChoicesTest {
    @Test
    fun `visit form list problems are worded per cause`() {
        assertEquals(
            "TMS list unavailable (TMS has changed; the admin is informed) — type the institute name",
            tmsListUnavailableText(TmsApiChangeException("http_404", "HTTP 404")),
        )
        assertEquals("No connection to TMS — type the institute name", tmsListUnavailableText(TmsTransientException("x", true)))
        assertEquals("TMS list unavailable — type the institute name", tmsListUnavailableText(TmsMessageException("nope")))
    }
}
