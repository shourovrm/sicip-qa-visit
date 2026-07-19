// fills the real TA/DA bill template (public/tada-template.xlsx) with exceljs. the template file
// on disk is itself a real filled sample bill (used as the style source) -- we capture its row
// styles up front, wipe the sample itinerary rows (13-32), then rewrite them for the actual trips
// passed in, growing/shrinking the block as needed and shifting the Total/footer rows to match.
import { DESIGNATION } from './seeds.js'
import { amountInWords } from './billmath.js'

const ITIN_START = 13 // first itinerary row in the template
const ITIN_TEMPLATE_ROWS = 20 // template ships with rows 13..32 (20 rows) pre-filled as style source
const COLS = 12 // A..L
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// local-tada-template.xlsx: same block-splice approach, smaller 9-col sheet, no night/food/class.
const LOCAL_ITIN_START = 13
const LOCAL_ITIN_TEMPLATE_ROWS = 11 // rows 13..23 pre-filled as style source
const LOCAL_COLS = 9 // A..I

function fmtDate(iso) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  return `${String(d).padStart(2, '0')} ${MONTHS[m - 1]} ${y}`
}

function cloneStyle(cell) {
  return JSON.parse(JSON.stringify(cell.style ?? {}))
}

// capture per-column styles (A..L, or fewer for the local sheet) from a template row
function captureRowStyle(ws, rowNum, cols = COLS) {
  const row = ws.getRow(rowNum)
  const styles = []
  for (let c = 1; c <= cols; c++) styles.push(cloneStyle(row.getCell(c)))
  return styles
}

function applyRowStyle(ws, rowNum, styles, cols = COLS) {
  const row = ws.getRow(rowNum)
  for (let c = 1; c <= cols; c++) row.getCell(c).style = styles[c - 1]
}

// unmerge every merge range fully inside [fromRow, toRow] -- required before splice/clear so
// exceljs doesn't leave dangling merge refs (spliceRows warns it's not merge-safe otherwise).
// reads the private _merges map -- exceljs has no public merge-listing API.
function listMerges(ws) {
  return Object.keys(ws._merges ?? {}).map((key) => {
    const m = ws._merges[key]
    const model = m?.model ?? m ?? {}
    return { key, top: model.top, left: model.left, bottom: model.bottom, right: model.right }
  })
}

function unmergeAllFrom(ws, fromRow) {
  for (const m of listMerges(ws)) {
    if (m.top >= fromRow) {
      try { ws.unMergeCells(m.key) } catch { /* already gone */ }
    }
  }
}

// build the row plan: one purpose row + one row per leg, per trip; each leg's day-group
// (same dep_date) shares merged date cells; night/food merge across the whole trip.
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
  // lazy import: exceljs is ~900KB minified, only bill generation needs it
  const ExcelJS = (await import('exceljs')).default
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

  // splice/insert are not merge-safe: remember the footer merges (Total/net/signature bands,
  // below the itinerary block) so they can be re-merged shifted by delta afterwards, then
  // unmerge everything from the itinerary start down before touching row structure.
  const footerMerges = listMerges(ws).filter((m) => m.top >= ITIN_START + ITIN_TEMPLATE_ROWS)
  unmergeAllFrom(ws, ITIN_START)

  // wipe the sample itinerary block's values, keep the row slots
  for (let r = ITIN_START; r < ITIN_START + ITIN_TEMPLATE_ROWS; r++) {
    const row = ws.getRow(r)
    for (let c = 1; c <= COLS; c++) row.getCell(c).value = null
  }

  // grow or shrink the block to fit `needed` rows, so Total/footer rows land right after
  if (delta > 0) {
    const blanks = Array.from({ length: delta }, () => [])
    ws.insertRows(ITIN_START + ITIN_TEMPLATE_ROWS, blanks, 'n')
  } else if (delta < 0) {
    ws.spliceRows(ITIN_START + needed, -delta)
  }

  // restore footer merges at their shifted positions
  for (const m of footerMerges) {
    try { ws.mergeCells(m.top + delta, m.left, m.bottom + delta, m.right) } catch { /* overlap */ }
  }

  // write each planned row. day-group merges span A/D (dates); night/food G/H merge over the
  // WHOLE trip and carry the trip-level values (per-leg counts never printed).
  let r = ITIN_START
  let groupStart = null
  let tripStart = null
  const mergeCol = (col, startRow, endRow) => {
    if (startRow !== null && endRow > startRow) ws.mergeCells(startRow, col, endRow, col)
  }
  const closeSpans = (endRow, { trip = false } = {}) => {
    mergeCol(1, groupStart, endRow); mergeCol(4, groupStart, endRow)
    groupStart = null
    if (trip) { mergeCol(7, tripStart, endRow); mergeCol(8, tripStart, endRow); tripStart = null }
  }

  for (let i = 0; i < plan.length; i++) {
    const item = plan[i]
    const row = ws.getRow(r)
    if (item.kind === 'purpose') {
      closeSpans(r - 1, { trip: true })
      applyRowStyle(ws, r, purposeStyle)
      row.getCell(1).value = `Purpose: ${item.trip.purposeLine}`
      ws.mergeCells(r, 1, r, COLS)
    } else {
      const l = item.leg
      applyRowStyle(ws, r, item.kind === 'legFirst' ? legFirstStyle : legContStyle)
      if (item.kind === 'legFirst') {
        closeSpans(r - 1)
        groupStart = r
        row.getCell(1).value = new Date(l.depDate)
        row.getCell(4).value = new Date(l.arrDate)
      }
      if (tripStart === null) {
        tripStart = r
        row.getCell(7).value = item.trip.nights || '-'
        row.getCell(8).value = item.trip.foodDays || '-'
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
  // close the final trip's merges
  closeSpans(r - 1, { trip: true })

  // Total/footer rows shift by `delta` from their original template positions
  const totalRow = 33 + delta
  const accomRow = 34 + delta
  const foodRow = 35 + delta
  const netRow = 36 + delta
  const nameRow = 43 + delta
  const desgRow = 44 + delta

  const totalNights = trips.reduce((s, t) => s + t.nights, 0)
  const totalFoodDays = trips.reduce((s, t) => s + t.foodDays, 0)
  ws.getCell(`G${totalRow}`).value = totalNights || '-'
  ws.getCell(`H${totalRow}`).value = totalFoodDays || '-'
  ws.getCell(`K${totalRow}`).value = totals.ta || '-'
  ws.getCell(`K${accomRow}`).value = totals.accommodation || '-'
  ws.getCell(`K${foodRow}`).value = totals.food || '-'
  ws.getCell(`A${netRow}`).value = `Net claim bill TK. (In word) ${amountInWords(Math.round(totals.net))} only`
  ws.getCell(`K${netRow}`).value = totals.net || '-'
  ws.getCell(`A${nameRow}`).value = officerName
  ws.getCell(`A${desgRow}`).value = `${DESIGNATION} SICIP`

  return wb.xlsx.writeBuffer()
}

// fills local-tada-template.xlsx (public/local-tada-template.xlsx) -- trips arrive already
// filtered (localBillTrips shape: purposeLine + legs with fare/mode/remarks), same block-splice
// approach as fillBillTemplate but a smaller 9-col sheet (A dep date .. I remarks, no
// night/food/class) and a single fare-total row instead of TA/accommodation/food/net.
export async function fillLocalBillTemplate(templateBuffer, officerName, billDate, trips) {
  const ExcelJS = (await import('exceljs')).default
  const wb = new ExcelJS.Workbook()
  await wb.xlsx.load(templateBuffer)
  const ws = wb.worksheets[0]

  ws.getCell('C7').value = officerName
  ws.getCell('H7').value = new Date(billDate)

  const purposeStyle = captureRowStyle(ws, 13, LOCAL_COLS)
  const legFirstStyle = captureRowStyle(ws, 14, LOCAL_COLS)
  const legContStyle = captureRowStyle(ws, 15, LOCAL_COLS)

  const plan = planRows(trips)
  const needed = plan.length
  const delta = needed - LOCAL_ITIN_TEMPLATE_ROWS

  const footerMerges = listMerges(ws).filter((m) => m.top >= LOCAL_ITIN_START + LOCAL_ITIN_TEMPLATE_ROWS)
  unmergeAllFrom(ws, LOCAL_ITIN_START)

  for (let r = LOCAL_ITIN_START; r < LOCAL_ITIN_START + LOCAL_ITIN_TEMPLATE_ROWS; r++) {
    const row = ws.getRow(r)
    for (let c = 1; c <= LOCAL_COLS; c++) row.getCell(c).value = null
  }

  if (delta > 0) {
    const blanks = Array.from({ length: delta }, () => [])
    ws.insertRows(LOCAL_ITIN_START + LOCAL_ITIN_TEMPLATE_ROWS, blanks, 'n')
  } else if (delta < 0) {
    ws.spliceRows(LOCAL_ITIN_START + needed, -delta)
  }

  for (const m of footerMerges) {
    try { ws.mergeCells(m.top + delta, m.left, m.bottom + delta, m.right) } catch { /* overlap */ }
  }

  // write each planned row + merges for day-group spans (A dep date, D arr date only -- no
  // night/food columns to span in the local layout)
  let r = LOCAL_ITIN_START
  let groupStart = null
  const flushGroupMerge = (col, endRow) => {
    if (groupStart !== null && endRow > groupStart) ws.mergeCells(groupStart, col, endRow, col)
  }

  for (let i = 0; i < plan.length; i++) {
    const item = plan[i]
    const row = ws.getRow(r)
    if (item.kind === 'purpose') {
      applyRowStyle(ws, r, purposeStyle, LOCAL_COLS)
      row.getCell(1).value = `Purpose: ${item.trip.purposeLine}`
      ws.mergeCells(r, 1, r, LOCAL_COLS)
      groupStart = null
    } else {
      const l = item.leg
      applyRowStyle(ws, r, item.kind === 'legFirst' ? legFirstStyle : legContStyle, LOCAL_COLS)
      if (item.kind === 'legFirst') {
        if (groupStart !== null) { flushGroupMerge(1, r - 1); flushGroupMerge(4, r - 1) }
        groupStart = r
        row.getCell(1).value = new Date(l.depDate)
        row.getCell(4).value = new Date(l.arrDate)
      }
      row.getCell(2).value = l.depTime
      row.getCell(3).value = l.depPlace
      row.getCell(5).value = l.arrTime
      row.getCell(6).value = l.arrPlace
      row.getCell(7).value = l.mode || '-'
      row.getCell(8).value = l.fare ? l.fare : '-'
      row.getCell(9).value = l.remarks || ''
    }
    r++
  }
  if (groupStart !== null) { flushGroupMerge(1, r - 1); flushGroupMerge(4, r - 1) }

  // Total/signature rows shift by `delta`; template's A cell already reads "Total" -- footer
  // merges (re-merged above) carry the Total band, we just fill the fare sum into H
  const totalRow = 24 + delta
  const nameRow = 31 + delta
  const desgRow = 32 + delta

  const fareSum = trips.reduce((s, t) => s + t.legs.reduce((ss, l) => ss + Number(l.fare || 0), 0), 0)
  ws.getCell(`H${totalRow}`).value = fareSum || '-'
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
