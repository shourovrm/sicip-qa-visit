// TA/DA bill printable HTML -- ported 1:1 from android/app/src/main/java/bd/sicip/qavisit/pdf/BillHtml.kt.
// pure string building; caller opens it in a new window + window.print() (browser's native
// save-as-PDF) instead of android's WebView-print pipeline. logos served from /logos/*.jpg
// (copied from assets/logos/ at build time, see web/public/logos/).
import { amountInWords } from './billmath.js'
import { DESIGNATION } from './seeds.js'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function parseIsoDate(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

// dd-MMM-yy, e.g. "01-Jul-26" (itinerary date cells)
function displayDate(iso) {
  if (!iso) return iso
  const d = parseIsoDate(iso)
  return `${String(d.getUTCDate()).padStart(2, '0')}-${MONTHS[d.getUTCMonth()]}-${String(d.getUTCFullYear()).slice(2)}`
}

// dd MMM yyyy, e.g. "01 Jul 2026" (header date)
function headerDate(iso) {
  if (!iso) return iso
  const d = parseIsoDate(iso)
  return `${String(d.getUTCDate()).padStart(2, '0')} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`
}

// h:mm a, e.g. "9:00 AM" -- input "HH:mm[:ss]" 24h
function displayTime(hhmm) {
  if (!hhmm) return hhmm
  const [h, m] = hhmm.split(':').map(Number)
  const period = h < 12 ? 'AM' : 'PM'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return `${h12}:${String(m).padStart(2, '0')} ${period}`
}

// xlsx <cols> widths (character units) for A..L, same weights as the itinerary table.
const COL_WEIGHTS = [8.63, 8.37, 25.82, 9.09, 8.54, 24.54, 5.91, 8.18, 10.54, 9.82, 12.18, 11.54]

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

// drops a bare ".00", thousands-separated, no currency glyph (header already says "Fare (Tk.)")
function plainAmount(v) {
  const s = Number(v ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return s.endsWith('.00') ? s.slice(0, -3) : s
}

function dashIfZero(v) {
  return !v ? '-' : plainAmount(v)
}

function td(text, { rowspan = 1, cls = null } = {}) {
  const attrs = (rowspan > 1 ? ` rowspan="${rowspan}"` : '') + (cls ? ` class="${cls}"` : '')
  return `<td${attrs}>${text}</td>`
}

function colGroupWidths() {
  const sum = COL_WEIGHTS.reduce((a, b) => a + b, 0)
  return COL_WEIGHTS.map((w) => `<col style="width:${((w / sum) * 100).toFixed(4)}%">`).join('')
}

function tableHeadHtml() {
  return `<colgroup>${colGroupWidths()}</colgroup><thead>
    <tr><th colspan="3">Departure</th><th colspan="3">Arrival</th>
      <th rowspan="2">No. of Night Stay</th><th rowspan="2">No. of Day for Food Allowance</th>
      <th rowspan="2">Mode of Transport</th><th rowspan="2">Class</th>
      <th rowspan="2">Fare (Tk.)</th><th rowspan="2">Remarks</th></tr>
    <tr><th>Date</th><th>Time</th><th>Place</th><th>Date</th><th>Time</th><th>Place</th></tr>
  </thead>`
}

// one trip: a full-width purpose band, then its legs day-grouped so the date/night/food cells
// rowspan over every leg sharing the same calendar (departure) day.
function tripRowsHtml(trip) {
  let html = `<tr class="purpose"><td colspan="12">Purpose: ${esc(trip.purposeLine)}</td></tr>`
  let i = 0
  while (i < trip.legs.length) {
    let j = i
    while (j + 1 < trip.legs.length && trip.legs[j + 1].depDate === trip.legs[i].depDate) j++
    const span = j - i + 1
    const first = trip.legs[i]
    for (let k = i; k <= j; k++) {
      const leg = trip.legs[k]
      html += '<tr>'
      if (k === i) html += td(displayDate(first.depDate), { rowspan: span })
      html += td(displayTime(leg.depTime), { cls: 'time' })
      html += td(esc(leg.depPlace), { cls: 'place' })
      if (k === i) html += td(displayDate(first.arrDate), { rowspan: span })
      html += td(displayTime(leg.arrTime), { cls: 'time' })
      html += td(esc(leg.arrPlace), { cls: 'place' })
      if (k === i) {
        html += td(dashIfZero(first.nightStay), { rowspan: span })
        html += td(dashIfZero(first.foodDay), { rowspan: span })
      }
      html += td(esc(leg.mode))
      html += td(esc(leg.travelClass || '-'))
      html += td(plainAmount(leg.fare), { cls: 'money' })
      html += td(esc(leg.remarks || '-'))
      html += '</tr>'
    }
    i = j + 1
  }
  return html
}

function totalsRowsHtml(trips, totals) {
  const nightsSum = trips.reduce((s, t) => s + t.nights, 0)
  const foodSum = trips.reduce((s, t) => s + t.foodDays, 0)
  const words = `${amountInWords(Math.round(totals.net))} Only`
  return `
    <tr class="totals">
      <td colspan="6"><b>Total</b></td>
      <td><b>${dashIfZero(nightsSum)}</b></td>
      <td><b>${dashIfZero(foodSum)}</b></td>
      <td colspan="2"><b>Total Travel Allowance</b></td>
      <td class="money"><b>${plainAmount(totals.ta)}</b></td>
      <td></td>
    </tr>
    <tr>
      <td colspan="10">Total Accomodation Allowance (2,000 BDT Per Night Stay)</td>
      <td class="money">${plainAmount(totals.accommodation)}</td><td></td>
    </tr>
    <tr>
      <td colspan="10">Total Food Allowance (1,500 BDT Per Day)</td>
      <td class="money">${plainAmount(totals.food)}</td><td></td>
    </tr>
    <tr class="net">
      <td colspan="10"><b>Net claim bill TK. (In word) ${esc(words)}</b></td>
      <td class="money"><b>${plainAmount(totals.net)}</b></td><td></td>
    </tr>`
}

function footerHtml(officerName) {
  return `<div class="footer">
    <div class="sig-col">
      <div><b>Submitted By:</b></div><div class="sig-gap"></div><div class="sig-line"></div>
      <div>${esc(officerName)}</div><div>${DESIGNATION}</div><div>SICIP</div>
    </div>
    <div class="sig-col">
      <div><b>Recommended By:</b></div><div class="sig-gap"></div><div class="sig-line"></div>
    </div>
  </div>`
}

// print CSS: border hierarchy is 1pt table outline + section borders, 0.4pt inner grid.
const CSS = `
  @page { size: A4 landscape; margin: 12mm; }
  * { box-sizing: border-box; }
  body { font-family: 'Noto Sans', Roboto, sans-serif; font-size: 8pt; color: #000; margin: 0; }
  .letterhead { display: flex; align-items: flex-start; justify-content: space-between; }
  .letterhead .seal { width: 45pt; height: 40pt; object-fit: contain; }
  .letterhead .sicip { width: 75pt; height: 37pt; object-fit: contain; }
  .org-lines { flex: 1; text-align: center; }
  .org-line1 { font-size: 10pt; font-weight: bold; margin-bottom: 2pt; }
  .org-line2 { font-size: 8.5pt; margin-bottom: 1pt; }
  .band { background: #E4E4E4; padding: 3pt 6pt; font-weight: bold; }
  .band.title { text-align: center; font-size: 15pt; margin-top: 6pt; }
  .name-date { display: flex; justify-content: space-between; padding: 8pt 2pt 6pt; font-size: 9pt; }
  table.itinerary { width: 100%; border-collapse: collapse; table-layout: fixed; border: 1pt solid #000; }
  table.itinerary th, table.itinerary td {
    border: 0.4pt solid #000; padding: 2.5pt 3pt; font-size: 7.5pt; text-align: center;
    vertical-align: middle; overflow-wrap: break-word;
  }
  table.itinerary thead { display: table-header-group; }
  table.itinerary thead th { font-weight: bold; background: #fff; }
  table.itinerary td.place { text-align: left; white-space: normal; }
  table.itinerary td.money { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
  table.itinerary td.time { white-space: nowrap; }
  table.itinerary tr.purpose td { text-align: left; font-weight: bold; background: #E4E4E4; }
  table.itinerary tr.totals td { background: #E4E4E4; }
  table.itinerary tr.net td { padding-top: 4pt; padding-bottom: 4pt; }
  .footer { display: flex; justify-content: space-between; margin-top: 14pt; font-size: 9pt; }
  .footer .sig-col { width: 45%; }
  .footer .sig-gap { height: 55pt; }
  .footer .sig-line { width: 70%; border-bottom: 1pt dotted #000; margin-bottom: 6pt; }
`

export function buildBillHtml(officerName, billDate, trips, totals) {
  let html = '<!doctype html><html><head><meta charset="utf-8"><title>TA/DA Bill</title>'
  html += `<style>${CSS}</style></head><body>`
  html += `<div class="letterhead">
      <img class="seal" src="/logos/bd-govt-seal.jpg">
      <div class="org-lines">
        <div class="org-line1">Government of the People&rsquo;s Republic of Bangladesh</div>
        <div class="org-line2">Skills for Industry Competitiveness and Innovation Program (SICIP)</div>
        <div class="org-line2">Finance Division, Ministry of Finance</div>
      </div>
      <img class="sicip" src="/logos/sicip-logo.jpg">
    </div>
    <div class="band title">TA/DA Bill</div>`
  html += `<div class="name-date">
      <div><b>Name &amp; Designation:</b> ${esc(officerName)}, ${DESIGNATION}</div>
      <div><b>Date:</b> ${headerDate(billDate)}</div>
    </div>`
  html += '<div class="band">Detailed Travel Itinerary:</div>'
  html += '<table class="itinerary">'
  html += tableHeadHtml()
  html += '<tbody>'
  trips.forEach((t) => { html += tripRowsHtml(t) })
  html += totalsRowsHtml(trips, totals)
  html += '</tbody></table>'
  html += footerHtml(officerName)
  html += '</body></html>'
  return html
}

// open the built HTML in a new tab and trigger the browser's print (save-as-PDF) dialog.
export function printBillHtml(html) {
  const w = window.open('', '_blank')
  if (!w) return false
  w.document.write(html)
  w.document.close()
  w.onload = () => w.print()
  // document.write doesn't always fire load reliably across browsers -- belt-and-braces kick
  setTimeout(() => w.print(), 300)
  return true
}
