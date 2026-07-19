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

        // the two 2026-06-08 legs collapse into one rowspan="2" date cell (dep + arr date);
        // night/food merge over the WHOLE 3-leg trip (rowspan="3") and show the trip-level
        // values, not per-leg counts. (header's own rowspan="2" cells excluded via <tbody>.)
        val tbody = html.substringAfter("<tbody>")
        assertEquals(2, Regex("rowspan=\"2\"").findAll(tbody).count())
        assertTrue(tbody.contains("rowspan=\"3\">1</td>"))
        assertTrue(tbody.contains("rowspan=\"3\">1.50</td>"))
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

    @Test fun `zero fare leg renders as a dash`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg("2026-06-08", fare = 0.0)), nights = 0, foodDays = 0.0)
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(0.0, 0.0, 0.0, 0.0))

        assertTrue(html.contains("<td class=\"money\">-</td>"))
    }

    @Test fun `N slash A mode leg prints a dash in the mode cell`() {
        // real N/A legs carry a null class too (see ui.bill.BillScreen.toBillTrip) -- both the
        // mode and class cells should print bare "-" (no rowspan/class attrs on either).
        val na = BillLeg(
            depDate = "2026-06-08", depTime = "10:00:00", depPlace = "A",
            arrDate = "2026-06-08", arrTime = "11:00:00", arrPlace = "B",
            mode = "N/A", travelClass = null, fare = 100.0, remarks = "note",
            nightStay = 1, foodDay = 1.0,
        )
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(na), nights = 1, foodDays = 1.0)
        val html = buildBillHtml("Officer", "2026-06-10", listOf(trip), BillTotals(0.0, 0.0, 0.0, 0.0))

        assertEquals(2, Regex("<td>-</td>").findAll(html.substringAfter("<tbody>")).count())
    }
}
