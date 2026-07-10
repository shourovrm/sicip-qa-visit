// TA/DA bill math: night/food span totals, multi-trip aggregation, amount-in-words
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillMathTest {

    // real sample: decoded official bill xlsx batches two finished trips —
    // trip1: 11 May same-day Dhaka (legs 441+482), claimed 0 nights/0 food ("-" on the sheet)
    // trip2: 8-14 Jun Bhola (legs 176+1500), 6 nights, 6.5 food days
    private val trip2 = Trip(
        legs = listOf(Leg(fare = 176.0), Leg(fare = 1500.0)),
        startDate = "2026-06-08",
        endDate = "2026-06-14",
    )

    @Test fun real_sample_bill_with_claimed_overrides() {
        val trip1 = Trip(
            legs = listOf(Leg(fare = 441.0), Leg(fare = 482.0)),
            startDate = "2026-05-11",
            endDate = "2026-05-11",
            nights = 0,
            food = 0.0,
        )
        val totals = billTotals(listOf(trip1, trip2))
        assertEquals(2599.0, totals.ta, 0.001)
        assertEquals(12000.0, totals.accommodation, 0.001)
        assertEquals(9750.0, totals.food, 0.001)
        assertEquals(24349.0, totals.net, 0.001)
        assertEquals("Twenty Four Thousand Three Hundred Forty Nine", amountInWords(totals.net.toLong()))
    }

    // same two trips, but trip1 left at its span-rule defaults (single day -> 0 nights, 0.5 food)
    // instead of the claimed override: proves the override path above is what matches the
    // official doc, not the raw span rule.
    @Test fun rule_default_bill_without_override_differs_from_real_sample() {
        val trip1 = Trip(
            legs = listOf(Leg(fare = 441.0), Leg(fare = 482.0)),
            startDate = "2026-05-11",
            endDate = "2026-05-11",
        )
        val totals = billTotals(listOf(trip1, trip2))
        assertEquals(2599.0, totals.ta, 0.001)
        assertEquals(12000.0, totals.accommodation, 0.001)
        assertEquals(10500.0, totals.food, 0.001)
        assertEquals(25099.0, totals.net, 0.001)
    }

    @Test fun trip_span_totals_match_sample_trip2() {
        assertEquals(6, tripNights("2026-06-08", "2026-06-14"))
        assertEquals(6.5, tripFoodDays("2026-06-08", "2026-06-14"), 0.0001)
    }

    // TA = sum of fares, sample values lifted from the real template
    @Test fun ta_sums_leg_fares() {
        val legs = listOf(Leg(fare = 176.0), Leg(fare = 1500.0), Leg(fare = 441.0))
        assertEquals(2117.0, ta(legs), 0.001)
    }

    @Test fun single_day_trip_no_night_half_food() {
        assertEquals(0, tripNights("2026-06-01", "2026-06-01"))
        assertEquals(0.5, tripFoodDays("2026-06-01", "2026-06-01"), 0.0001)
        val totals = billTotals(listOf(Trip(listOf(Leg(fare = 500.0)), "2026-06-01", "2026-06-01")))
        assertEquals(0.0, totals.accommodation, 0.001)
        assertEquals(750.0, totals.food, 0.001)
        assertEquals(500.0, totals.ta, 0.001)
    }

    @Test fun endDate_before_startDate_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            tripNights("2026-06-14", "2026-06-08")
        }
    }

    // per-leg defaults: day-grouped, first leg of a day carries the value, last day halved
    @Test fun legDefaults_day_grouped_last_day_halved() {
        val legs = listOf(
            Leg(fare = 100.0, depDate = "2026-06-08"), // day 1, first leg -> night
            Leg(fare = 50.0, depDate = "2026-06-08"),  // day 1, second leg -> no double count
            Leg(fare = 100.0, depDate = "2026-06-14"), // last day -> half food, no night
        )
        val defaults = legDefaults(legs, "2026-06-14")
        assertEquals(listOf(1 to 1.0, 0 to 0.0, 0 to 0.5), defaults)
    }

    @Test fun legDefaults_single_day_trip() {
        val legs = listOf(Leg(fare = 100.0, depDate = "2026-06-01"))
        assertEquals(listOf(0 to 0.5), legDefaults(legs, "2026-06-01"))
    }

    // ---- amountInWords ----
    @Test fun amountInWords_zero() {
        assertEquals("Zero", amountInWords(0))
    }

    @Test fun amountInWords_one_lakh() {
        assertEquals("One Lakh", amountInWords(100000))
    }

    @Test fun amountInWords_one_lakh_five() {
        assertEquals("One Lakh Five", amountInWords(100005))
    }

    @Test fun amountInWords_crore() {
        assertEquals(
            "One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight",
            amountInWords(12345678)
        )
    }
}
