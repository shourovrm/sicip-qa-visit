// local-travel bill: row filter (localBillTrips) + the html shape that differs from the full
// bill (band text, one totals row, no "Recommended By"). see pdf/BillHtml.kt doc comments.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.data.seed.TICKET_REMARK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBillHtmlTest {

    private fun leg(fare: Double = 100.0, mode: String = "Bus", remarks: String? = null) = BillLeg(
        depDate = "2026-06-08", depTime = "10:00:00", depPlace = "A",
        arrDate = "2026-06-08", arrTime = "11:00:00", arrPlace = "B",
        mode = mode, travelClass = "AC", fare = fare, remarks = remarks,
        nightStay = 0, foodDay = 0.0,
    )

    @Test fun `leg carrying the ticket remark is dropped`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg(remarks = "bus fare; $TICKET_REMARK")), nights = 0, foodDays = 0.0)

        assertTrue(localBillTrips(listOf(trip)).isEmpty())
    }

    @Test fun `zero-fare leg is dropped`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg(fare = 0.0)), nights = 0, foodDays = 0.0)

        assertTrue(localBillTrips(listOf(trip)).isEmpty())
    }

    @Test fun `N slash A mode leg is dropped`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg(mode = "N/A")), nights = 0, foodDays = 0.0)

        assertTrue(localBillTrips(listOf(trip)).isEmpty())
    }

    @Test fun `trip losing every leg drops its whole purpose band`() {
        val dropped = BillTrip(purposeLine = "Purpose: Dropped", legs = listOf(leg(fare = 0.0)), nights = 0, foodDays = 0.0)
        val kept = BillTrip(purposeLine = "Purpose: Kept", legs = listOf(leg(fare = 500.0)), nights = 0, foodDays = 0.0)

        val result = localBillTrips(listOf(dropped, kept))

        assertEquals(1, result.size)
        assertEquals("Purpose: Kept", result.single().purposeLine)
    }

    @Test fun `total sums only the surviving fares`() {
        val trip = BillTrip(
            purposeLine = "Purpose: X",
            legs = listOf(leg(fare = 300.0), leg(fare = 0.0), leg(mode = "N/A"), leg(remarks = TICKET_REMARK), leg(fare = 200.0)),
            nights = 0, foodDays = 0.0,
        )
        val html = buildLocalBillHtml("Officer", "2026-06-10", listOf(trip))

        assertTrue(html.contains("<td class=\"money\"><b>500</b></td>"))
    }

    @Test fun `local bill band and footer differ from the full bill`() {
        val trip = BillTrip(purposeLine = "Purpose: X", legs = listOf(leg()), nights = 0, foodDays = 0.0)
        val html = buildLocalBillHtml("Officer", "2026-06-10", listOf(trip))

        assertTrue(html.contains("Detailed Local Travel Itinerary:"))
        assertFalse(html.contains("Recommended By"))
    }
}
