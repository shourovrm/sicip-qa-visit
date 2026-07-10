// snapshot round-trip parity -- same shape/semantics as android BillSnapshotTest.kt
import { it, expect } from 'vitest'
import { snapshotBill, toBillTrips } from './billsnapshot.js'
import { billTotals, makeTrip, leg } from './billmath.js'

// stored-shape trips (snake_case legs) as Bills.svelte buildSnapshotTrips produces them
const trips = [
  { tripId: 't1', purposeLine: 'Capacity Assessment - BACCO (Ref: X, 06 May 2026)', nights: 0, foodDays: 0,
    legs: [
      { dep_date: '2026-05-11', dep_time: '10:30', dep_place: 'A', arr_date: '2026-05-11', arr_time: '11:00', arr_place: 'B', mode: 'Uber Car', class: 'Rented', fare: 441, remarks: null },
      { dep_date: '2026-05-11', dep_time: '16:11', dep_place: 'B', arr_date: '2026-05-11', arr_time: '17:12', arr_place: 'A', mode: 'Bus', class: 'AC', fare: 482, remarks: null },
    ] },
  { tripId: 't2', purposeLine: 'ToT - JUTTI (Ref: Y, 04 Mar 2026)', nights: 6, foodDays: 6.5,
    legs: [
      { dep_date: '2026-06-08', dep_time: '14:54', dep_place: 'A', arr_date: '2026-06-08', arr_time: '15:31', arr_place: 'B', mode: 'Uber Bike', class: 'Rented', fare: 176, remarks: null },
      { dep_date: '2026-06-14', dep_time: '15:30', dep_place: 'B', arr_date: '2026-06-14', arr_time: '23:30', arr_place: 'A', mode: 'Launch', class: 'Cabin', fare: 1500, remarks: null },
    ] },
]

const totals = billTotals(trips.map((t) => makeTrip(t.legs.map((l) => leg(l.fare, l.dep_date)), '', '', t.nights, t.foodDays)))

it('real-sample totals survive the snapshot', () => {
  const snap = snapshotBill('2026-06-15', 'Test Officer', trips, totals)
  expect(snap.totals.net).toBeCloseTo(24349, 3)
  expect(snap.totals.words).toBe('Twenty Four Thousand Three Hundred Forty Nine Only')
  expect(snap.trips[0].legs[0].dep_date).toBe('2026-05-11') // stored shape kept intact
})

it('toBillTrips recomputes per-leg night/food deterministically', () => {
  const snap = snapshotBill('2026-06-15', 'Test Officer', trips, totals)
  const rendered = toBillTrips(snap)
  // trip1 claimed 0/0 -> whole trip zeroed
  expect(rendered[0].legs.map((l) => [l.nightStay, l.foodDay])).toEqual([[0, 0], [0, 0]])
  // trip2: first day full, last travel day half food / no night
  expect(rendered[1].legs.map((l) => [l.nightStay, l.foodDay])).toEqual([[1, 1], [0, 0.5]])
  expect(rendered[1].purposeLine).toBe('ToT - JUTTI (Ref: Y, 04 Mar 2026)')
})
