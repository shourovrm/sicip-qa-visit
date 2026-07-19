// template-fill smoke: real template, shrink (few rows) + grow (many rows) paths.
// reads the local gitignored template -- skipped when absent (e.g. CI without assets/).
import { it, expect } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import ExcelJS from 'exceljs'
import { fillBillTemplate, fillLocalBillTemplate } from './xlsx.js'

const TEMPLATE = new URL('../../../assets/tada-template.xlsx', import.meta.url).pathname
const LOCAL_TEMPLATE = new URL('../../../assets/local-tada-template.xlsx', import.meta.url).pathname

function legRow(depDate, fare, night, food) {
  return { depDate, depTime: '09:00', depPlace: 'A', arrDate: depDate, arrTime: '10:00', arrPlace: 'B',
    mode: 'Bus', travelClass: 'AC', fare, remarks: null, nightStay: night, foodDay: food }
}

function localLegRow(depDate, fare, remarks = null) {
  return { depDate, depTime: '09:00', depPlace: 'A', arrDate: depDate, arrTime: '10:00', arrPlace: 'B', mode: 'CNG', fare, remarks }
}

async function loadOut(buf, sheetName = 'TA') {
  const wb = new ExcelJS.Workbook()
  await wb.xlsx.load(buf)
  return wb.getWorksheet(sheetName) ?? wb.worksheets[0]
}

it.skipIf(!existsSync(TEMPLATE))('shrink path: 1 trip, 2 legs', async () => {
  const trips = [{ purposeLine: 'P1', nights: 1, foodDays: 1.5, legs: [legRow('2026-06-08', 100, 1, 1), legRow('2026-06-09', 50, 0, 0.5)] }]
  const totals = { ta: 150, accommodation: 2000, food: 2250, net: 4400 }
  const out = await fillBillTemplate(readFileSync(TEMPLATE), 'Test Officer', '2026-06-15', trips, totals)
  const ws = await loadOut(out)
  expect(ws.getCell('C7').value).toBe('Test Officer, Program Officer (QA)')
  expect(ws.getCell('A13').value).toBe('Purpose: P1')
  expect(ws.getCell('K14').value).toBe(100)
  // night/food: trip-level values on the trip's first leg row, merged down the whole trip
  expect(ws.getCell('G14').value).toBe(1)
  expect(ws.getCell('H14').value).toBe(1.5)
  expect(ws.getCell('G15').master.address).toBe('G14')
  expect(ws.getCell('H15').master.address).toBe('H14')
  // 3 plan rows -> delta -17 -> total row 33-17=16
  expect(ws.getCell('K16').value).toBe(150)
  expect(ws.getCell('K19').value).toBe(4400)
  expect(String(ws.getCell('A19').value)).toContain('Four Thousand Four Hundred')
})

it.skipIf(!existsSync(TEMPLATE))('grow path: 24 plan rows shifts totals down', async () => {
  const legs = Array.from({ length: 22 }, (_, i) => legRow(`2026-06-${String(1 + i % 28).padStart(2, '0')}`, 10, 1, 1))
  const trips = [
    { purposeLine: 'Big tour', nights: 6, foodDays: 6.5, legs },
    { purposeLine: 'Second', nights: 0, foodDays: 0, legs: [legRow('2026-07-01', 5, 0, 0)] },
  ]
  const totals = { ta: 225, accommodation: 12000, food: 9750, net: 21975 }
  const out = await fillBillTemplate(readFileSync(TEMPLATE), 'Test Officer', '2026-07-10', trips, totals)
  const ws = await loadOut(out)
  // 25 plan rows -> delta +5 -> total row 38, net row 41, name row 48
  expect(ws.getCell('K38').value).toBe(225)
  expect(ws.getCell('K41').value).toBe(21975)
  expect(ws.getCell('A48').value).toBe('Test Officer')
})

it.skipIf(!existsSync(LOCAL_TEMPLATE))('local bill: shrink path, 2 trips / 3 legs', async () => {
  const trips = [
    { purposeLine: 'P1', legs: [localLegRow('2026-06-08', 100), localLegRow('2026-06-08', 50)] },
    { purposeLine: 'P2', legs: [localLegRow('2026-06-09', 75)] },
  ]
  const out = await fillLocalBillTemplate(readFileSync(LOCAL_TEMPLATE), 'Test Officer', '2026-06-15', trips)
  const ws = await loadOut(out, 'Local')
  expect(ws.getCell('C7').value).toBe('Test Officer')
  expect(ws.getCell('A13').value).toBe('Purpose: P1')
  expect(ws.getCell('H14').value).toBe(100) // day-group leg 1
  expect(ws.getCell('H15').value).toBe(50) // day-group leg 2 (continuation, same dep date)
  expect(ws.getCell('A16').value).toBe('Purpose: P2')
  expect(ws.getCell('H17').value).toBe(75)
  // 5 plan rows (2 purposes + 3 legs) -> delta -6 -> total row 24-6=18, name row 25, designation row 26
  expect(ws.getCell('H18').value).toBe(225)
  expect(ws.getCell('A25').value).toBe('Test Officer')
  expect(ws.getCell('A26').value).toBe('Program Officer (QA) SICIP')
})
