// frozen record of a submitted TA/DA bill -- once a bill exists, Previous-bills view and
// regenerated xlsx must show exactly these values forever, independent of later edits to the
// trips/visits/legs it was built from. per-leg night-stay/food-day are NOT stored (rendering
// detail): toBillTrips recomputes them the same deterministic way bill prep does (legDefaults).
// ported 1:1 from android/app/src/main/java/bd/sicip/qavisit/domain/BillSnapshot.kt
import { legDefaults, amountInWords } from './billmath.js'
import { suggestedNights, suggestedFood } from './scoring.js'
import { fmtDate } from './xlsx.js'

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

// -- tour grouping for the New-bill / bill-preview UI (Bills.svelte) --------------------------
// android T5 rules ported here (not Bills.svelte) so they're plain testable functions; Bills.svelte
// calls them with visits/legs passed explicitly so svelte's $:/{@const} dependency tracking sees
// the reads (a helper closing over module-level state hides the dependency -- see DECISIONS.md).

// tour sort key for bill UI ordering: earliest travel-leg departure, else trip start.
// `legs` must already be that one tour's own legs.
export function tourSortKey(legs, startedAt) {
  if (!legs.length) return startedAt
  return legs.reduce((min, l) => {
    const key = `${l.dep_date}T${l.dep_time}`
    return key < min ? key : min
  }, `${legs[0].dep_date}T${legs[0].dep_time}`)
}

// one visit's purpose-band text: "<purpose> - <association> (Ref: <ref_no>, <ref_date>)"
export function purposeLineForVisit(v) {
  return `${v.purpose} - ${v.association} (Ref: ${v.ref_no || '—'}, ${fmtDate(v.ref_date || v.start_date)})`
}

// all of a tour's visits, primary first -- shared ordering for both the purpose band and the
// tour-select card's institute list.
function orderedTripVisits(tripId, visits) {
  const tVisits = visits.filter((v) => v.trip_id === tripId)
  const pv = tVisits.find((v) => !v.is_additional)
  return pv ? [pv, ...tVisits.filter((v) => v !== pv)] : tVisits
}

// a tour's purpose band: primary visit's line first, then any additional visits with a distinct
// purpose+association+ref+date -- duplicates collapsed, multiple bands joined "; ".
export function purposeLineForTrip(tripId, visits) {
  return [...new Set(orderedTripVisits(tripId, visits).map(purposeLineForVisit))].join('; ')
}

// all institute names for a tour's visits, primary first (full names -- caller decides wrapping).
export function institutesForTrip(tripId, visits) {
  return orderedTripVisits(tripId, visits).map((v) => v.institute)
}

// build snapshot-shaped trips (the bills.data.trips shape) from selected live trips. `tripIds`
// should already be chrono-ordered (tourSortKey) -- this only shapes, it doesn't sort.
export function buildStoredTrips(tripIds, visits, legs) {
  return tripIds.map((tripId) => {
    const pv = visits.find((v) => v.trip_id === tripId && !v.is_additional)
    const tLegs = legs.filter((l) => l.trip_id === tripId).sort((a, b) => (a.dep_date + a.dep_time).localeCompare(b.dep_date + b.dep_time))
    return {
      tripId,
      purposeLine: purposeLineForTrip(tripId, visits),
      nights: suggestedNights(pv.category),
      foodDays: suggestedFood(pv.category),
      legs: tLegs.map((l) => ({
        dep_date: l.dep_date, dep_time: l.dep_time, dep_place: l.dep_place,
        arr_date: l.arr_date, arr_time: l.arr_time, arr_place: l.arr_place,
        mode: l.mode, class: l.class, fare: Number(l.fare), remarks: l.remarks,
      })),
    }
  })
}
