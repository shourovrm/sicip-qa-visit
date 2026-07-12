// snapshot round-trip parity -- same shape/semantics as android BillSnapshotTest.kt
import { describe, it, expect } from 'vitest'
import { snapshotBill, toBillTrips, tourSortKey, purposeLineForTrip, institutesForTrip, buildStoredTrips } from './billsnapshot.js'
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

// -- android T5 tour-grouping rules ported for web (chrono sort, multi-institute, purpose join) --

describe('tourSortKey', () => {
  it('uses the earliest leg departure when legs exist', () => {
    const legs = [
      { dep_date: '2026-07-05', dep_time: '14:00' },
      { dep_date: '2026-07-05', dep_time: '09:00' },
      { dep_date: '2026-07-06', dep_time: '08:00' },
    ]
    expect(tourSortKey(legs, '2026-07-01T00:00:00Z')).toBe('2026-07-05T09:00')
  })

  it('falls back to trip started_at with no legs', () => {
    expect(tourSortKey([], '2026-07-01T00:00:00Z')).toBe('2026-07-01T00:00:00Z')
  })
})

const pv = { trip_id: 't1', is_additional: false, institute: 'SDC Overseas Training and Testing Center, Dhaka', purpose: 'Capacity Assessment', association: 'BACCO', ref_no: 'X', ref_date: '2026-07-01', start_date: '2026-07-05' }
const addl1 = { trip_id: 't1', is_additional: true, institute: 'Lalmatia Mohila College, Dhaka', purpose: 'Monitoring Visit', association: 'BGMEA', ref_no: 'Y', ref_date: '2026-07-02', start_date: '2026-07-05' }
const addl2 = { trip_id: 't1', is_additional: true, institute: 'Kamarpara TTC, Dhaka', purpose: 'Capacity Assessment', association: 'BACCO', ref_no: 'X', ref_date: '2026-07-01', start_date: '2026-07-05' } // identical to pv's purpose line

describe('institutesForTrip', () => {
  it('lists all institutes, primary first', () => {
    expect(institutesForTrip('t1', [addl1, addl2, pv])).toEqual([
      'SDC Overseas Training and Testing Center, Dhaka',
      'Lalmatia Mohila College, Dhaka',
      'Kamarpara TTC, Dhaka',
    ])
  })

  it('single-visit tour is just that institute', () => {
    expect(institutesForTrip('t1', [pv])).toEqual([pv.institute])
  })
})

describe('purposeLineForTrip', () => {
  it('joins distinct purpose+association+ref lines with "; ", primary first', () => {
    const line = purposeLineForTrip('t1', [addl1, pv])
    expect(line).toBe('Capacity Assessment - BACCO (Ref: X, 01 Jul 2026); Monitoring Visit - BGMEA (Ref: Y, 02 Jul 2026)')
  })

  it('collapses an additional visit identical to the primary into one band', () => {
    const line = purposeLineForTrip('t1', [pv, addl2])
    expect(line).toBe('Capacity Assessment - BACCO (Ref: X, 01 Jul 2026)')
  })

  it('single-visit tour prints one band, no join', () => {
    expect(purposeLineForTrip('t1', [pv])).toBe('Capacity Assessment - BACCO (Ref: X, 01 Jul 2026)')
  })
})

describe('buildStoredTrips', () => {
  it('keeps caller-given tripId order and joins multi-visit purpose bands', () => {
    const legs = [
      { trip_id: 't1', dep_date: '2026-07-05', dep_time: '09:00', dep_place: 'A', arr_date: '2026-07-05', arr_time: '10:00', arr_place: 'B', mode: 'Bus', class: 'AC', fare: 100, remarks: null },
    ]
    const [stored] = buildStoredTrips(['t1'], [pv, addl1], legs)
    expect(stored.tripId).toBe('t1')
    expect(stored.purposeLine).toContain('; ')
    expect(stored.legs.length).toBe(1)
  })
})
