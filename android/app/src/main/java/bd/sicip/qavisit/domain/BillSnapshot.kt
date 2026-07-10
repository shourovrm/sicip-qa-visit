// the frozen record of a submitted TA/DA bill -- once a bill exists, its Previous-bills detail
// view and re-rendered PDF must show exactly these values forever, independent of anything
// that happens later to the trips/visits/legs it was built from. per-leg night-stay/food-day
// display values are NOT stored (they're a rendering detail, not user data): renderableFromSnapshot
// recomputes them the same deterministic way bill prep does (BillMath.legDefaults), so re-printing
// the PDF later reproduces the original byte-for-byte without persisting a derived value.
// pure kotlin, no android deps -- mirrors domain/BillMath.kt's own rule.
package bd.sicip.qavisit.domain

import bd.sicip.qavisit.pdf.BillLeg
import bd.sicip.qavisit.pdf.BillTrip
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class SnapshotLeg(
    val depDate: String,
    val depTime: String,
    val depPlace: String,
    val arrDate: String,
    val arrTime: String,
    val arrPlace: String,
    val mode: String,
    val travelClass: String?,
    val fare: Double,
    val remarks: String?,
)

data class SnapshotTrip(
    val tripId: String,
    val purposeLine: String,
    val nights: Int,
    val foodDays: Double,
    val legs: List<SnapshotLeg>,
)

data class BillSnapshot(
    val billDate: String,
    val officerName: String,
    val trips: List<SnapshotTrip>,
    val totals: BillTotals,
)

fun snapshotBill(
    billDate: String,
    officerName: String,
    trips: List<SnapshotTrip>,
    totals: BillTotals,
): JsonObject = buildJsonObject {
    put("billDate", billDate)
    put("officerName", officerName)
    putJsonArray("trips") {
        trips.forEach { t ->
            addJsonObject {
                put("tripId", t.tripId)
                put("purposeLine", t.purposeLine)
                put("nights", t.nights)
                put("foodDays", t.foodDays)
                putJsonArray("legs") {
                    t.legs.forEach { leg ->
                        addJsonObject {
                            put("dep_date", leg.depDate)
                            put("dep_time", leg.depTime)
                            put("dep_place", leg.depPlace)
                            put("arr_date", leg.arrDate)
                            put("arr_time", leg.arrTime)
                            put("arr_place", leg.arrPlace)
                            put("mode", leg.mode)
                            put("class", leg.travelClass)
                            put("fare", leg.fare)
                            put("remarks", leg.remarks)
                        }
                    }
                }
            }
        }
    }
    putJsonObject("totals") {
        put("ta", totals.ta)
        put("accommodation", totals.accommodation)
        put("food", totals.food)
        put("net", totals.net)
        put("words", "${amountInWords(totals.net.toLong())} Only")
    }
}

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.num(key: String): Double = getValue(key).jsonPrimitive.doubleOrNull ?: 0.0
private fun JsonObject.intNum(key: String): Int = getValue(key).jsonPrimitive.intOrNull ?: 0

fun renderableFromSnapshot(json: JsonObject): BillSnapshot {
    val trips = json.getValue("trips").jsonArray.map { it.jsonObject }.map { t ->
        SnapshotTrip(
            tripId = t.str("tripId"),
            purposeLine = t.str("purposeLine"),
            nights = t.intNum("nights"),
            foodDays = t.num("foodDays"),
            legs = t.getValue("legs").jsonArray.map { it.jsonObject }.map { l ->
                SnapshotLeg(
                    depDate = l.str("dep_date"),
                    depTime = l.str("dep_time"),
                    depPlace = l.str("dep_place"),
                    arrDate = l.str("arr_date"),
                    arrTime = l.str("arr_time"),
                    arrPlace = l.str("arr_place"),
                    mode = l.str("mode"),
                    travelClass = l.strOrNull("class"),
                    fare = l.num("fare"),
                    remarks = l.strOrNull("remarks"),
                )
            },
        )
    }
    val t = json.getValue("totals").jsonObject
    return BillSnapshot(
        billDate = json.str("billDate"),
        officerName = json.str("officerName"),
        trips = trips,
        totals = BillTotals(ta = t.num("ta"), accommodation = t.num("accommodation"), food = t.num("food"), net = t.num("net")),
    )
}

// PDF-ready form: per-leg night-stay/food-day recomputed the same way bill prep computes them
// (day-grouped, last day halved/zeroed, whole trip zeroed if both nights and food are 0) so
// re-rendering later via pdf/BillPdf.kt reproduces the original submitted PDF.
fun BillSnapshot.toBillTrips(): List<BillTrip> = trips.map { t ->
    val mathLegs = t.legs.map { Leg(fare = it.fare, depDate = it.depDate) }
    val endDate = t.legs.maxOfOrNull { it.depDate } ?: billDate
    val defaults = legDefaults(mathLegs, endDate)
    val zeroed = t.nights == 0 && t.foodDays == 0.0
    val billLegs = t.legs.zip(defaults).map { (leg, d) ->
        val (n, f) = if (zeroed) 0 to 0.0 else d
        BillLeg(
            depDate = leg.depDate, depTime = leg.depTime, depPlace = leg.depPlace,
            arrDate = leg.arrDate, arrTime = leg.arrTime, arrPlace = leg.arrPlace,
            mode = leg.mode, travelClass = leg.travelClass, fare = leg.fare, remarks = leg.remarks,
            nightStay = n, foodDay = f,
        )
    }
    BillTrip(purposeLine = t.purposeLine, legs = billLegs, nights = t.nights, foodDays = t.foodDays)
}
