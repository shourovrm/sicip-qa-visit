// parity tests -- same numbers as android BillMathTest.kt (real sample bill: net 24349 etc)
import { describe, it, expect } from 'vitest'
import { leg, ta, makeTrip, billTotals, legDefaults, amountInWords } from './billmath.js'

// real sample: decoded official bill xlsx batches two finished trips --
// trip1: 11 May same-day Dhaka (legs 441+482), claimed 0 nights/0 food ("-" on the sheet)
// trip2: 8-14 Jun Bhola (legs 176+1500), 6 nights, 6.5 food days (A** category span)
it('real sample bill with claimed overrides', () => {
  const trip1 = makeTrip([leg(441), leg(482)], '2026-05-11', '2026-05-11', 0, 0)
  const trip2 = makeTrip([leg(176), leg(1500)], '2026-06-08', '2026-06-14', 6, 6.5)
  const totals = billTotals([trip1, trip2])
  expect(totals.ta).toBeCloseTo(2599, 3)
  expect(totals.accommodation).toBeCloseTo(12000, 3)
  expect(totals.food).toBeCloseTo(9750, 3)
  expect(totals.net).toBeCloseTo(24349, 3)
  expect(amountInWords(Math.round(totals.net))).toBe('Twenty Four Thousand Three Hundred Forty Nine')
})

it('unresolved trip defaults to zero', () => {
  const trip1 = makeTrip([leg(441)], '2026-05-11', '2026-05-11')
  expect(trip1.resolvedNights).toBe(0)
  expect(trip1.resolvedFood).toBeCloseTo(0, 4)
})

it('ta sums leg fares', () => {
  expect(ta([leg(176), leg(1500), leg(441)])).toBeCloseTo(2117, 3)
})

it('billTotals sums resolved nights/food across trips', () => {
  const totals = billTotals([makeTrip([leg(500)], '2026-06-01', '2026-06-01', 0, 0.5)])
  expect(totals.accommodation).toBeCloseTo(0, 3)
  expect(totals.food).toBeCloseTo(750, 3)
  expect(totals.ta).toBeCloseTo(500, 3)
})

it('legDefaults day-grouped, last day halved', () => {
  const legs = [
    leg(100, '2026-06-08'), // day 1, first leg -> night
    leg(50, '2026-06-08'), // day 1, second leg -> no double count
    leg(100, '2026-06-14'), // last day -> half food, no night
  ]
  expect(legDefaults(legs, '2026-06-14')).toEqual([[1, 1], [0, 0], [0, 0.5]])
})

it('legDefaults single day trip', () => {
  expect(legDefaults([leg(100, '2026-06-01')], '2026-06-01')).toEqual([[0, 0.5]])
})

describe('amountInWords', () => {
  it('zero', () => expect(amountInWords(0)).toBe('Zero'))
  it('one lakh', () => expect(amountInWords(100000)).toBe('One Lakh'))
  it('one lakh five', () => expect(amountInWords(100005)).toBe('One Lakh Five'))
  it('crore', () => expect(amountInWords(12345678)).toBe(
    'One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight'))
})
