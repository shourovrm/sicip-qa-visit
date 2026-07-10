// frozen record of a submitted TA/DA bill -- once a bill exists, Previous-bills view and
// regenerated xlsx must show exactly these values forever, independent of later edits to the
// trips/visits/legs it was built from. per-leg night-stay/food-day are NOT stored (rendering
// detail): toBillTrips recomputes them the same deterministic way bill prep does (legDefaults).
// ported 1:1 from android/app/src/main/java/bd/sicip/qavisit/domain/BillSnapshot.kt
import { legDefaults, amountInWords } from './billmath.js'

// build the frozen JSON blob stored in bills.data. trips arrive already in the stored shape
// (snake_case leg keys, as built by Bills.svelte buildSnapshotTrips) and are frozen as-is.
export function snapshotBill(billDate, officerName, trips, totals) {
  return {
    billDate,
    officerName,
    trips: trips.map((t) => ({
      tripId: t.tripId,
      purposeLine: t.purposeLine,
      nights: t.nights,
      foodDays: t.foodDays,
      legs: t.legs.map((l) => ({
        dep_date: l.dep_date, dep_time: l.dep_time, dep_place: l.dep_place,
        arr_date: l.arr_date, arr_time: l.arr_time, arr_place: l.arr_place,
        mode: l.mode, class: l.class ?? null, fare: l.fare, remarks: l.remarks ?? null,
      })),
    })),
    totals: {
      ta: totals.ta, accommodation: totals.accommodation, food: totals.food, net: totals.net,
      words: `${amountInWords(Math.round(totals.net))} Only`,
    },
  }
}

// PDF/xlsx-ready form: per-leg night-stay/food-day recomputed the same way bill prep computes
// them (day-grouped, last day halved/zeroed, whole trip zeroed if both nights and food are 0) so
// re-rendering later reproduces the original submitted output.
export function toBillTrips(snapshot) {
  return snapshot.trips.map((t) => {
    const mathLegs = t.legs.map((l) => ({ fare: l.fare, depDate: l.dep_date }))
    // max dep_date; billDate only as the empty-legs fallback (kotlin: maxOfOrNull ?: billDate)
    const endDate = t.legs.length ? t.legs.reduce((max, l) => (l.dep_date > max ? l.dep_date : max), t.legs[0].dep_date) : snapshot.billDate
    const defaults = legDefaults(mathLegs, endDate)
    const zeroed = t.nights === 0 && t.foodDays === 0
    const legs = t.legs.map((l, i) => {
      const [n, f] = zeroed ? [0, 0] : defaults[i]
      return {
        depDate: l.dep_date, depTime: l.dep_time, depPlace: l.dep_place,
        arrDate: l.arr_date, arrTime: l.arr_time, arrPlace: l.arr_place,
        mode: l.mode, travelClass: l.class, fare: l.fare, remarks: l.remarks,
        nightStay: n, foodDay: f,
      }
    })
    return { purposeLine: t.purposeLine, legs, nights: t.nights, foodDays: t.foodDays }
  })
}
