// HTML builder is pure string assembly -- no Android/WebView needed, so structure bits are
// asserted directly against the generated markup here. the actual print-to-PDF path
// (pdf/BillPrinter.kt) needs a real WebView and is left to the manual/instrumented smoke
// check, not this suite (see BillLayoutTest.kt's old note, same rule applies).
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.domain.BillTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillHtmlTest {

    private fun leg(depDate: String, night: Int = 0, food: Double = 0.0, fare: Double = 100.0) = BillLeg(
        depDate = depDate, depTime = "10:00:00", depPlace = "A",
        arrDate = depDate, arrTime = "11:00:00", arrPlace = "B",
        mode = "Bus", travelClass = "AC", fare = fare, remarks = null,
        nightStay = night, foodDay = food,
    )

    @Test fun `same-day legs share one rowspanned date cell`() {
        val trip = BillTrip(
            purposeLine = "Purpose: X",
            legs = listOf(leg("2026-06-08", night = 1, food = 1.0), leg("2026-06-08"), leg("2026-06-09", night = 0, food = 0.5)),
            nights = 1, foodDays = 1.5,
        )
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(0.0, 0.0, 0.0, 0.0))

        // the two 2026-06-08 legs collapse into one rowspan="2" date cell (shared by dep date,
        // arr date, night stay and food day), not two separate date cells -- that's the whole
        // point of switching off the canvas's "blank the repeated leg" trick. (header's own six
        // rowspan="2" cells -- Night/Food/Mode/Class/Fare/Remarks -- are excluded via <tbody>.)
        assertEquals(4, Regex("rowspan=\"2\"").findAll(html.substringAfter("<tbody>")).count())
        // one leg row per leg, day-grouping only merges cells (rowspan), never drops a <tr>.
        assertEquals(3, Regex("<td class=\"place\">A</td>").findAll(html).count())
    }

    @Test fun `purpose line renders verbatim as the trip's band text`() {
        val trip = BillTrip(purposeLine = "Purpose: Capacity Assessment - AEOSIB (Ref: —, 6 May 2026)", legs = listOf(leg("2026-06-08")), nights = 0, foodDays = 0.0)
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(0.0, 0.0, 0.0, 0.0))

        assertTrue(html.contains("Purpose: Capacity Assessment - AEOSIB (Ref: —, 6 May 2026)"))
    }

    @Test fun `purposeDate prefers ref_date over start_date`() {
        assertEquals("6 May 2026", purposeDate(startDate = "2026-05-10", refDate = "2026-05-06"))
    }

    @Test fun `purposeDate falls back to start_date when ref_date is absent`() {
        assertEquals("10 May 2026", purposeDate(startDate = "2026-05-10", refDate = null))
    }

    @Test fun `fares render with thousands separators`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg("2026-06-08", fare = 12345.0)), nights = 0, foodDays = 0.0)
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(ta = 12345.0, accommodation = 0.0, food = 0.0, net = 12345.0))

        assertTrue(html.contains("12,345"))
    }

    @Test fun `net claim row spells the amount in words`() {
        val html = buildBillHtml("Officer", "2026-06-10", emptyList(), BillTotals(ta = 0.0, accommodation = 0.0, food = 0.0, net = 1680.0))

        assertTrue(html.contains("Net claim bill TK. (In word) One Thousand Six Hundred Eighty Only"))
    }

    @Test fun `zero night and food values render as a dash`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg("2026-06-08", night = 0, food = 0.0)), nights = 0, foodDays = 0.0)
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(0.0, 0.0, 0.0, 0.0))

        assertTrue(html.contains(">-</td>"))
    }
}
