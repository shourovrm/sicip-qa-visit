// round-trip snapshotBill()/renderableFromSnapshot() -- everything the JSON schema actually
// carries must survive the trip through JsonObject and back untouched. per-leg night/food
// display values are deliberately NOT part of that contract (see file header comment on
// domain/BillSnapshot.kt), so they're covered separately below.
package bd.sicip.qavisit.domain

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class BillSnapshotTest {
    private val leg1 = SnapshotLeg(
        depDate = "2026-06-08", depTime = "08:00:00", depPlace = "Dhaka",
        arrDate = "2026-06-08", arrTime = "14:00:00", arrPlace = "Bhola",
        mode = "Launch", travelClass = "1st", fare = 176.0, remarks = "remark 1",
    )
    private val leg2 = SnapshotLeg(
        depDate = "2026-06-14", depTime = "09:00:00", depPlace = "Bhola",
        arrDate = "2026-06-14", arrTime = "15:00:00", arrPlace = "Dhaka",
        mode = "Launch", travelClass = null, fare = 1500.0, remarks = null,
    )
    private val trip = SnapshotTrip(
        tripId = "trip-1",
        purposeLine = "Purpose: QA visit - BASIS (Ref: REF-1, 08 Jun 2026)",
        nights = 6,
        foodDays = 6.5,
        legs = listOf(leg1, leg2),
    )
    private val totals = BillTotals(ta = 1676.0, accommodation = 12000.0, food = 9750.0, net = 23426.0)

    @Test fun `snapshot round trips through json`() {
        val json = snapshotBill(billDate = "2026-06-15", officerName = "Jane Officer", trips = listOf(trip), totals = totals)
        val restored = renderableFromSnapshot(json)

        assertEquals(
            BillSnapshot(billDate = "2026-06-15", officerName = "Jane Officer", trips = listOf(trip), totals = totals),
            restored,
        )
    }

    @Test fun `words field derives from net amount`() {
        val json = snapshotBill(billDate = "2026-06-15", officerName = "Jane Officer", trips = listOf(trip), totals = totals)
        val words = json.getValue("totals").jsonObject.getValue("words").jsonPrimitive.content
        assertEquals("${amountInWords(totals.net.toLong())} Only", words)
    }

    @Test fun `toBillTrips recomputes per-leg night and food defaults`() {
        val snapshot = BillSnapshot(billDate = "2026-06-15", officerName = "Jane Officer", trips = listOf(trip), totals = totals)
        val billTrips = snapshot.toBillTrips()

        assertEquals(1, billTrips.size)
        val legs = billTrips[0].legs
        // first day of a 6-night, 6.5-food trip: full night/food; last day (trip end): halved food, no night.
        assertEquals(1, legs[0].nightStay)
        assertEquals(1.0, legs[0].foodDay, 0.001)
        assertEquals(0, legs[1].nightStay)
        assertEquals(0.5, legs[1].foodDay, 0.001)
    }

    @Test fun `toBillTrips zeroes every leg when the trip was claimed fully zeroed`() {
        val zeroedTrip = trip.copy(nights = 0, foodDays = 0.0)
        val snapshot = BillSnapshot(billDate = "2026-05-11", officerName = "Jane Officer", trips = listOf(zeroedTrip), totals = totals)
        val legs = snapshot.toBillTrips()[0].legs
        legs.forEach {
            assertEquals(0, it.nightStay)
            assertEquals(0.0, it.foodDay, 0.001)
        }
    }
}
