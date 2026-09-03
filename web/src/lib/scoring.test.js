// parity tests -- same cases/numbers as android ScoringTest.kt
import { describe, it, expect } from 'vitest'
import {
  POINTS, CATEGORY_SPANS, CATEGORY_LABELS, points, suggestedNights, suggestedFood,
  totalPoints, rank, monthSummary,
  lastDayOfPreviousMonth,
} from './scoring.js'

it('points table matches fixed scale', () => {
  expect(points('A***')).toBe(116)
  expect(points('A**+')).toBe(112)
  expect(points('A**')).toBe(100)
  expect(points('A++*')).toBe(96)
  expect(points('A++')).toBe(84)
  expect(points('A+*')).toBe(80)
  expect(points('A+')).toBe(68)
  expect(points('A*')).toBe(64)
  expect(points('A')).toBe(52)
  expect(points('B+')).toBe(48)
  expect(points('B')).toBe(36)
  expect(points('C+')).toBe(32)
  expect(points('C')).toBe(20)
  expect(points('D+')).toBe(16)
  expect(points('D')).toBe(4)
  expect(points('E')).toBe(1)
  expect(points('N/A')).toBe(0)
})

it('points unknown category is zero', () => expect(points('bogus')).toBe(0))
it('category labels cover exactly the points keys', () => {
  expect(Object.keys(CATEGORY_LABELS).sort()).toEqual(Object.keys(POINTS).sort())
})

it('totalPoints sums skipping deleted', () => {
  const visits = [
    { officerId: 'o1', category: 'A**', deleted: false }, // 100
    { officerId: 'o1', category: 'N/A', deleted: false }, // 0
    { officerId: 'o1', category: 'A++', deleted: true }, // skipped
  ]
  expect(totalPoints(visits)).toBe(100)
})

it('rank sorts officers by points asc (fewest points = #1)', () => {
  const visits = [
    { officerId: 'low', category: 'D' }, // 4
    { officerId: 'high', category: 'A**' }, // 100
    { officerId: 'high', category: 'E' }, // +1 = 101
    { officerId: 'mid', category: 'B' }, // 36
    { officerId: 'high', category: 'A++', deleted: true }, // skipped
  ]
  expect(rank(visits)).toEqual([['low', 4], ['mid', 36], ['high', 101]])
})

it('scheduled (not done) visits do not score', () => {
  const visits = [
    { officerId: 'o1', category: 'A**', done: false }, // scheduled, skipped
    { officerId: 'o1', category: 'D', done: true }, // 4
    { officerId: 'o2', category: 'A***', done: false }, // scheduled, skipped -> no entry at all
  ]
  expect(totalPoints(visits)).toBe(4)
  expect(rank(visits)).toEqual([['o1', 4]])
  expect(monthSummary(visits.map((v) => ({ ...v, startDate: '2026-07-01' })), '2026-07')).toEqual([1, 4])
})

describe('monthSummary', () => {
  it('counts only matching month', () => {
    const visits = [
      { startDate: '2026-07-01', category: 'A' }, // 52
      { startDate: '2026-07-15', category: 'B' }, // 36
      { startDate: '2026-06-30', category: 'A**' }, // excluded
    ]
    expect(monthSummary(visits, '2026-07')).toEqual([2, 88])
  })
  it('skips deleted', () => {
    const visits = [
      { startDate: '2026-07-01', category: 'A**', deleted: true },
      { startDate: '2026-07-02', category: 'D' }, // 4
    ]
    expect(monthSummary(visits, '2026-07')).toEqual([1, 4])
  })
  it('empty when nothing matches', () => {
    expect(monthSummary([{ startDate: '2026-05-01', category: 'A' }], '2026-07')).toEqual([0, 0])
  })
})

// full 17-row span table from CATEGORIES.md
const spanTable = [
  ['A***', 8, 7, 7.5], ['A**+', 7, 7, 7.0], ['A**', 7, 6, 6.5], ['A++*', 6, 6, 6.0],
  ['A++', 6, 5, 5.5], ['A+*', 5, 5, 5.0], ['A+', 5, 4, 4.5], ['A*', 4, 4, 4.0],
  ['A', 4, 3, 3.5], ['B+', 3, 3, 3.0], ['B', 3, 2, 2.5], ['C+', 2, 2, 2.0],
  ['C', 2, 1, 1.5], ['D+', 1, 1, 1.0], ['D', 1, 0, 0.5], ['E', 0, 0, 0.0], ['N/A', 0, 0, 0.0],
]

it('category spans cover exactly the points keys', () => {
  expect(Object.keys(CATEGORY_SPANS).sort()).toEqual(Object.keys(POINTS).sort())
})

it('category spans full table', () => {
  for (const [cat, d, n] of spanTable) expect(CATEGORY_SPANS[cat]).toEqual([d, n])
})

it('suggestedNights full table', () => {
  for (const [cat, , n] of spanTable) expect(suggestedNights(cat)).toBe(n)
})

it('suggestedFood full table', () => {
  for (const [cat, , , f] of spanTable) expect(suggestedFood(cat)).toBeCloseTo(f, 4)
})

it('suggestedNights/suggestedFood unknown category are zero', () => {
  expect(suggestedNights('bogus')).toBe(0)
  expect(suggestedFood('bogus')).toBeCloseTo(0, 4)
})

// same cases as android RankTest.kt's lastDayOfPreviousMonth suite
describe('lastDayOfPreviousMonth', () => {
  it('mid-year month lands on previous month\'s last day', () => expect(lastDayOfPreviousMonth('2026-07-10')).toBe('2026-06-30'))
  it('january rolls back to dec 31 of prior year', () => expect(lastDayOfPreviousMonth('2026-01-15')).toBe('2025-12-31'))
  it('march lands on last day of february, non-leap year', () => expect(lastDayOfPreviousMonth('2026-03-01')).toBe('2026-02-28'))
  it('march lands on last day of february, leap year', () => expect(lastDayOfPreviousMonth('2024-03-15')).toBe('2024-02-29'))
  it('respects short and long month lengths', () => {
    expect(lastDayOfPreviousMonth('2026-05-01')).toBe('2026-04-30')
    expect(lastDayOfPreviousMonth('2026-06-01')).toBe('2026-05-31')
  })
  it('cutoff is stable regardless of day of month', () => {
    expect(lastDayOfPreviousMonth('2026-07-01')).toBe('2026-06-30')
    expect(lastDayOfPreviousMonth('2026-07-31')).toBe('2026-06-30')
  })
})
