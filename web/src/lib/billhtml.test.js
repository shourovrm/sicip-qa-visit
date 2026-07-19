// buildBillHtml -- ported from android BillHtmlTest.kt (date/time formatting, day-grouping
// rowspans, dash-for-zero). string-shape checks only -- the actual print/PDF layer is browser
// window.print(), not JVM-testable here.
import { describe, it, expect } from 'vitest'
import { buildBillHtml, buildLocalBillHtml, localBillTrips } from './billhtml.js'
import { TICKET_REMARK } from './seeds.js'

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

it('day-group shares rowspan=2 date cells; night/food merge over the whole trip', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip()], totals)
  const tbody = html.slice(html.indexOf('<tbody>'))
  // one 2-leg day-group -> exactly 2 rowspan=2 cells (dep date, arr date); night/food span
  // the whole 3-leg trip and show trip-level values, not per-leg counts.
  const spanCells = (tbody.match(/rowspan="2"/g) || []).length
  expect(spanCells).toBe(2)
  expect(tbody).toContain('rowspan="3">6</td>')
  expect(tbody).toContain('rowspan="3">6.50</td>')
})

it('zero night/food cells render as dash', () => {
  const html = buildBillHtml('X', '2026-07-01', [trip({ nights: 0, foodDays: 0, legs: trip().legs.map((l) => ({ ...l, nightStay: 0, foodDay: 0 })) })], totals)
  expect(html).toMatch(/rowspan="3">-<\/td>/)
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

it('zero fare renders as dash in leg and totals cells', () => {
  const zeroTrip = trip({ legs: trip().legs.map((l) => ({ ...l, fare: 0 })) })
  const html = buildBillHtml('X', '2026-07-01', [zeroTrip], { ta: 0, accommodation: 0, food: 0, net: 0 })
  expect(html).toMatch(/class="money">-<\/td>/)
  expect(html).toMatch(/class="money"><b>-<\/b><\/td>/)
})

function localLeg(overrides = {}) {
  return {
    depDate: '2026-06-08', depTime: '09:00', depPlace: 'Dhaka', arrDate: '2026-06-08', arrTime: '10:00', arrPlace: 'Bhola',
    mode: 'CNG', fare: 50, remarks: '',
    ...overrides,
  }
}

describe('localBillTrips', () => {
  it('drops zero-fare and ticket-remark legs; keeps the rest', () => {
    const trips = [{
      purposeLine: 'P1',
      legs: [
        localLeg({ fare: 0 }),
        localLeg({ remarks: TICKET_REMARK }),
        localLeg({ fare: 100 }),
      ],
    }]
    const out = localBillTrips(trips)
    expect(out.length).toBe(1)
    expect(out[0].legs.length).toBe(1)
    expect(out[0].legs[0].fare).toBe(100)
  })

  it('N/A-mode leg with zero fare is still dropped by the fare rule', () => {
    const trips = [{ purposeLine: 'P1', legs: [localLeg({ mode: 'N/A', fare: 0 })] }]
    expect(localBillTrips(trips)).toEqual([])
  })

  it('N/A-mode leg with fare and no ticket remark is kept and prints dash mode', () => {
    const trips = [{ purposeLine: 'P1', legs: [localLeg({ mode: 'N/A', fare: 150 })] }]
    const out = localBillTrips(trips)
    expect(out[0].legs.length).toBe(1)

    const html = buildLocalBillHtml('X', '2026-07-01', trips)
    expect(html).toContain('<td>-</td>')
  })

  it('drops a trip whose every leg is filtered out', () => {
    const trips = [{ purposeLine: 'AllGone', legs: [localLeg({ fare: 0 }), localLeg({ fare: 0, mode: 'N/A' })] }]
    expect(localBillTrips(trips)).toEqual([])
  })
})

describe('buildLocalBillHtml', () => {
  const localTrips = [{
    purposeLine: 'Local errands',
    legs: [
      localLeg({ fare: 100 }),
      localLeg({ fare: 0 }), // filtered out
      localLeg({ fare: 50, depDate: '2026-06-09', arrDate: '2026-06-09' }),
    ],
  }]

  it('renders the local band and omits Recommended By', () => {
    const html = buildLocalBillHtml('X', '2026-07-01', localTrips)
    expect(html).toContain('Detailed Local Travel Itinerary:')
    expect(html).not.toContain('Recommended By')
  })

  it('total row sums only surviving legs\' fare', () => {
    const html = buildLocalBillHtml('X', '2026-07-01', localTrips)
    expect(html).toContain('<b>150</b>') // 100 + 50, zero-fare leg dropped
  })
})
