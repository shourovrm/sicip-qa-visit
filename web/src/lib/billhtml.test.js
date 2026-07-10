// buildBillHtml -- ported from android BillHtmlTest.kt (date/time formatting, day-grouping
// rowspans, dash-for-zero). string-shape checks only -- the actual print/PDF layer is browser
// window.print(), not JVM-testable here.
import { describe, it, expect } from 'vitest'
import { buildBillHtml } from './billhtml.js'

const totals = { ta: 623, accommodation: 12000, food: 9750, net: 22373 }

function trip(overrides = {}) {
  return {
    purposeLine: 'Monitoring Visit - BGMEA (Ref: 123, 01 Jun 2026)',
    nights: 6, foodDays: 6.5,
    legs: [
      { depDate: '2026-06-08', depTime: '09:00', depPlace: 'Dhaka', arrDate: '2026-06-08', arrTime: '13:00', arrPlace: 'Bhola', mode: 'Bus', travelClass: 'AC', fare: 441, remarks: '', nightStay: 1, foodDay: 1 },
      { depDate: '2026-06-08', depTime: '14:00', depPlace: 'Bhola town', arrDate: '2026-06-08', arrTime: '14:30', arrPlace: 'Institute', mode: 'CNG', travelClass: 'Rented', fare: 50, remarks: '', nightStay: 0, foodDay: 0 },
      { depDate: '2026-06-14', depTime: '17:00', depPlace: 'Bhola', arrDate: '2026-06-14', arrTime: '21:00', arrPlace: 'Dhaka', mode: 'Bus', travelClass: 'AC', fare: 482, remarks: '', nightStay: 0, foodDay: 0.5 },
    ],
    ...overrides,
  }
}

it('header shows dd MMM yyyy date and officer name/designation', () => {
  const html = buildBillHtml('Mahfuzul Islam', '2026-07-01', [trip()], totals)
  expect(html).toContain('<b>Date:</b> 01 Jul 2026')
  expect(html).toContain('Mahfuzul Islam, Program Officer (QA)')
})

it('purpose band prefixes Purpose: and escapes html', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip({ purposeLine: 'A & B <script>' })], totals)
  expect(html).toContain('Purpose: A &amp; B &lt;script&gt;')
})

it('itinerary dates render dd-MMM-yy and times render h:mm a', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip()], totals)
  expect(html).toContain('08-Jun-26')
  expect(html).toContain('9:00 AM')
  expect(html).toContain('2:00 PM')
})

it('day-group shares rowspan=2 date/night/food cells, continuation leg repeats none', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip()], totals)
  const tbody = html.slice(html.indexOf('<tbody>'))
  // one 2-leg day-group -> exactly 4 rowspan=2 cells (dep date, arr date, night, food);
  // the continuation leg (2nd leg, same dep_date) contributes zero of its own.
  const spanCells = (tbody.match(/rowspan="2"/g) || []).length
  expect(spanCells).toBe(4)
})

it('zero night/food cells render as dash', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip({ nights: 0, foodDays: 0, legs: trip().legs.map((l) => ({ ...l, nightStay: 0, foodDay: 0 })) })], totals)
  expect(html).toMatch(/rowspan="2">-<\/td>/)
})

it('totals row sums nights/food across trips and shows net in words', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip(), trip()], { ta: 623, accommodation: 24000, food: 19500, net: 44123 })
  expect(html).toContain('<b>12</b>') // nights summed (6+6)
  expect(html).toContain('Forty Four Thousand One Hundred Twenty Three')
})

describe('logos and print', () => {
  it('references /logos/*.jpg (public/, not android_asset)', () => {
    const html = buildBillHtml('X', '2026-07-01', [trip()], totals)
    expect(html).toContain('/logos/bd-govt-seal.jpg')
    expect(html).toContain('/logos/sicip-logo.jpg')
  })
})
