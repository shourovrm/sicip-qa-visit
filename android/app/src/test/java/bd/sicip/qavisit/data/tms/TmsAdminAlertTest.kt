package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmsAdminAlertTest {
    @Test
    fun `no rows means no banner`() = assertNull(tmsAlertText(emptyList()))

    @Test
    fun `counts distinct endpoint and kind, first day and latest row`() {
        val rows = listOf(
            TmsErrorRow("institute/filterList", "shape", "data: expected array", "2026-09-30"),
            TmsErrorRow("institute/filterList", "shape", "data: expected array", "2026-09-29"),
            TmsErrorRow("batch/list", "http_404", "HTTP 404", "2026-09-28"),
        )
        assertEquals(
            "TMS API changed: 2 error kinds since 2026-09-28, latest institute/filterList (data: expected array)",
            tmsAlertText(rows),
        )
    }

    @Test
    fun `rows parse from postgrest json`() {
        val array = Json.parseToJsonElement("""[{"endpoint":"a","kind":"shape","detail":"d","day":"2026-09-30"},7]""").jsonArray
        assertEquals(listOf(TmsErrorRow("a", "shape", "d", "2026-09-30")), parseTmsErrorRows(array))
    }
}
