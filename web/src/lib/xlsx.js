// fills the real TA/DA bill template (public/tada-template.xlsx) with exceljs. the template file
// on disk is itself a real filled sample bill (used as the style source) -- we capture its row
// styles up front, wipe the sample itinerary rows (13-32), then rewrite them for the actual trips
// passed in, growing/shrinking the block as needed and shifting the Total/footer rows to match.
import ExcelJS from 'exceljs'
import { DESIGNATION } from './seeds.js'
import { amountInWords } from './billmath.js'

const ITIN_START = 13 // first itinerary row in the template
const ITIN_TEMPLATE_ROWS = 20 // template ships with rows 13..32 (20 rows) pre-filled as style source
const COLS = 12 // A..L
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function fmtDate(iso) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  return `${String(d).padStart(2, '0')} ${MONTHS[m - 1]} ${y}`
}

function cloneStyle(cell) {
  return JSON.parse(JSON.stringify(cell.style))
}

// capture per-column styles (A..L) from a template row, before we overwrite anything
function captureRowStyle(ws, rowNum) {
  const row = ws.getRow(rowNum)
  const styles = []
  for (let c = 1; c <= COLS; c++) styles.push(cloneStyle(row.getCell(c).style))
  return styles
}

function applyRowStyle(ws, rowNum, styles) {
  const row = ws.getRow(rowNum)
  for (let c = 1; c <= COLS; c++) row.getCell(c).style = styles[c - 1]
}

// unmerge every merge range fully inside [fromRow, toRow] -- required before splice/clear so
// exceljs doesn't leave dangling merge refs (spliceRows warns it's not merge-safe otherwise).
function unmergeRange(ws, fromRow, toRow) {
  const ranges = [...ws._merges ? Object.keys(ws._merges) : []]
  for (const key of ranges) {
    const m = ws._merges[key]
    if (!m) continue
    const top = m.top ?? m.model?.top
    const bottom = m.bottom ?? m.model?.bottom
    if (top >= fromRow && bottom <= toRow) {
      try { ws.unMergeCells(key) } catch { /* already gone */ }
    }
  }
}

// build the row plan: one purpose row + one row per leg, per trip; each leg's day-group
// (same dep_date) shares merged date/night/food cells like the official bill.
function planRows(trips) {
  const plan = [] // { kind: 'purpose'|'legFirst'|'legCont', trip, leg? }
  for (const t of trips) {
    plan.push({ kind: 'purpose', trip: t })
    const seenDays = new Set()
    for (const leg of t.legs) {
      const first = !seenDays.has(leg.depDate)
      seenDays.add(leg.depDate)
      plan.push({ kind: first ? 'legFirst' : 'legCont', trip: t, leg })
    }
  }
  return plan
}

export async function fillBillTemplate(templateBuffer, officerName, billDate, trips, totals) {
  const wb = new ExcelJS.Workbook()
  await wb.xlsx.load(templateBuffer)
  const ws = wb.getWorksheet('TA') ?? wb.worksheets[0]

  // header
  ws.getCell('C7').value = `${officerName}, ${DESIGNATION}`
  ws.getCell('K7').value = new Date(billDate)

  // capture styles from the template's sample rows before we touch anything
  const purposeStyle = captureRowStyle(ws, 16) // clean merged purpose band
  const legFirstStyle = captureRowStyle(ws, 17) // first leg of a day (has date/night/food)
  const legContStyle = captureRowStyle(ws, 18) // continuation leg (same day)

  const plan = planRows(trips)
  const needed = plan.length
  const delta = needed - ITIN_TEMPLATE_ROWS

  // wipe the sample itinerary block's merges + values, keep the row slots
  unmergeRange(ws, ITIN_START, ITIN_START + ITIN_TEMPLATE_ROWS - 1)
  for (let r = ITIN_START; r < ITIN_START + ITIN_TEMPLATE_ROWS; r++) {
    const row = ws.getRow(r)
    for (let c = 1; c <= COLS; c++) row.getCell(c).value = null
  }

  // grow or shrink the block to fit `needed` rows, so Total/footer rows land right after
  if (delta > 0) {
    const blanks = Array.from({ length: delta }, () => [])
    ws.insertRows(ITIN_START + ITIN_TEMPLATE_ROWS, blanks, 'n')
  } else if (delta < 0) {
    unmergeRange(ws, ITIN_START + needed, ITIN_START + ITIN_TEMPLATE_ROWS - 1)
    ws.spliceRows(ITIN_START + needed, -delta)
  }

  // write each planned row + merges for day-group spans (A/D/G/H)
  let r = ITIN_START
  let groupStart = null
  const flushGroupMerge = (col, endRow) => {
    if (groupStart !== null && endRow > groupStart) ws.mergeCells(groupStart, col, endRow, col)
  }

  for (let i = 0; i < plan.length; i++) {
    const item = plan[i]
    const row = ws.getRow(r)
    if (item.kind === 'purpose') {
      applyRowStyle(ws, r, purposeStyle)
      row.getCell(1).value = `Purpose: ${item.trip.purposeLine}`
      ws.mergeCells(r, 1, r, COLS)
      groupStart = null
    } else {
      const l = item.leg
      applyRowStyle(ws, r, item.kind === 'legFirst' ? legFirstStyle : legContStyle)
      if (item.kind === 'legFirst') {
        // close the previous day-group's merges before starting a new one
        if (groupStart !== null) {
          flushGroupMerge(1, r - 1); flushGroupMerge(4, r - 1)
          flushGroupMerge(7, r - 1); flushGroupMerge(8, r - 1)
        }
        groupStart = r
        row.getCell(1).value = new Date(l.depDate)
        row.getCell(4).value = new Date(l.arrDate)
        row.getCell(7).value = l.nightStay === 0 && l.foodDay === 0 ? '-' : l.nightStay
        row.getCell(8).value = l.nightStay === 0 && l.foodDay === 0 ? '-' : l.foodDay
      }
      row.getCell(2).value = l.depTime
      row.getCell(3).value = l.depPlace
      row.getCell(5).value = l.arrTime
      row.getCell(6).value = l.arrPlace
      row.getCell(9).value = l.mode || '-'
      row.getCell(10).value = l.travelClass || '-'
      row.getCell(11).value = l.fare ? l.fare : '-'
      row.getCell(12).value = l.remarks || ''
    }
    r++
  }
  // close the final day-group's merges
  if (groupStart !== null) {
    flushGroupMerge(1, r - 1); flushGroupMerge(4, r - 1)
    flushGroupMerge(7, r - 1); flushGroupMerge(8, r - 1)
  }

  // Total/footer rows shift by `delta` from their original template positions
  const totalRow = 33 + delta
  const accomRow = 34 + delta
  const foodRow = 35 + delta
  const netRow = 36 + delta
  const nameRow = 43 + delta
  const desgRow = 44 + delta

  const totalNights = trips.reduce((s, t) => s + t.nights, 0)
  const totalFoodDays = trips.reduce((s, t) => s + t.foodDays, 0)
  ws.getCell(`G${totalRow}`).value = totalNights
  ws.getCell(`H${totalRow}`).value = totalFoodDays
  ws.getCell(`K${totalRow}`).value = totals.ta
  ws.getCell(`K${accomRow}`).value = totals.accommodation
  ws.getCell(`K${foodRow}`).value = totals.food
  ws.getCell(`A${netRow}`).value = `Net claim bill TK. (In word) ${amountInWords(Math.round(totals.net))} only`
  ws.getCell(`K${netRow}`).value = totals.net
  ws.getCell(`A${nameRow}`).value = officerName
  ws.getCell(`A${desgRow}`).value = `${DESIGNATION} SICIP`

  return wb.xlsx.writeBuffer()
}

export function downloadBuffer(buffer, filename) {
  const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export { fmtDate }
