// filled QA visit report (Annex-3 exact layout + a 4th Remarks column) as printable HTML --
// spec docs/superpowers/plans/2026-09-25-qa-report.md section 7. A separate layout from the
// surprise report's reporthtml.js: Annex-3's own heading-by-heading, table-by-table shape,
// Times New Roman, "Annex-3" top right, program + title centred. Caller opens the html in a new
// window + window.print(), same pattern as reporthtml.js/billhtml.js.
import { buildRemarks, printedRemarks } from './remarks.js'
import * as reportTemplateModule from './reporttemplate.js'
import { feedbackGrid } from './feedbackgrid.js'

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function blank(v) {
  return v == null || String(v).trim() === ''
}

function escMultiline(s) {
  return esc(s).replace(/\n/g, '<br>')
}

// same defensive normalize()-or-syncLinks-or-passthrough fallback as reporthtml.js -- pure fn,
// never mutates the caller's data.
function withNormalizedData(template, data) {
  const normalize = reportTemplateModule.normalize
  const step = typeof normalize === 'function' ? normalize : null
  if (!step) return data
  const cloned = JSON.parse(JSON.stringify(data))
  return step(template, cloned) || cloned
}

// "2026-09-25" -> "25/09/2026" (Annex-3's own date shape); blank/malformed values pass through.
function ddmmyyyy(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value ?? ''))
  if (!match) return blank(value) ? '' : String(value)
  return `${match[3]}/${match[2]}/${match[1]}`
}

// non-blank lines of a longtext field -> lowercase roman numerals "i) ii) iii) ..." (Annex-3's
// own list style for 1.61/1.62, 14, 15).
const ROMAN = ['i', 'ii', 'iii', 'iv', 'v', 'vi', 'vii', 'viii', 'ix', 'x', 'xi', 'xii', 'xiii', 'xiv', 'xv', 'xvi', 'xvii', 'xviii', 'xix', 'xx']
function romanLines(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  return lines.map((line, i) => `<div class="roman-line"><span class="roman">${ROMAN[i] ?? i + 1})</span>${esc(line)}</div>`).join('')
}

function fieldsMap(data) {
  return data.fields || {}
}

function kvLine(label, value) {
  return `<div class="kv-line"><b>${esc(label)} :</b> ${esc(value)}</div>`
}

// the Annex-3 header block that sits ABOVE "1. General Information" -- ti_name/provider/address/
// dates/officers. Everything else in section 1's `fields` block (status/purposes/team) prints
// inside section 1 itself as i)/ii)/iii).
function headerKvHtml(data) {
  const f = fieldsMap(data)
  const dateLine = `from ${ddmmyyyy(f.date_from) || '__/__/____'} to ${ddmmyyyy(f.date_to) || '__/__/____'}`
  return `<div class="kv">
    ${kvLine('Name of Training TI/TC Visited', f.ti_name)}
    ${kvLine('Name of the Association/Provider', f.provider)}
    ${kvLine('Address of Training Institute and Contact', f.address)}
    <div class="kv-line"><b>Date(s) of Visit :</b> ${esc(dateLine)}</div>
    ${kvLine("Name(s) & Designation(s) of Visiting Officer(s)", f.officers)}
  </div>`
}

function cardsTableHtml(fields, entries, minRows) {
  const rows = entries.length >= minRows ? entries : entries.concat(Array.from({ length: minRows - entries.length }, () => ({})))
  const header = `<tr>${fields.map((f) => `<th>${esc(f.label)}</th>`).join('')}</tr>`
  const body = rows.map((entry) => `<tr>${fields.map((f) => `<td>${escMultiline(entry[f.key])}</td>`).join('')}</tr>`).join('')
  return `<table class="grid">${header}${body}</table>`
}

// 1.10 persons met.
function personsHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  return `<div class="sub-h">${esc(block.heading)}</div>${cardsTableHtml(block.fields, entries, block.start || 3)}`
}

// 1.20-1.25 + 1.30-1.34 + comments -- plain label:value lines (short text/date/number fields).
function mouFieldsHtml(block, data) {
  const f = fieldsMap(data)
  const lines = block.fields
    .map((field) => {
      if (field.kind === 'longtext') {
        return `<div class="field-box"><div class="fb-label">${esc(field.label)}</div><div class="fb-body">${escMultiline(f[field.key])}</div></div>`
      }
      if (field.kind === 'choice') {
        const opt = (field.options || []).find((o) => o.id === f[field.key])
        return `<div class="kv-line"><b>${esc(field.label)} :</b> ${esc(opt ? opt.label : '')}</div>`
      }
      return `<div class="kv-line"><b>${esc(field.label)} :</b> ${esc(f[field.key])}</div>`
    })
    .join('')
  return `${block.heading ? `<div class="sub-h">${esc(block.heading)}</div>` : ''}${lines}`
}

// 1.40 MoU courses.
function mouCoursesHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  return `<div class="sub-h">${esc(block.heading)}</div>${cardsTableHtml(block.fields, entries, block.start || 4)}`
}

// 1.50 cumulative implementation -- T/F sub-header row, "Job Placed with Percentage" and
// "No. of dropouts with Percentage" print "n (p%)" (placed% of certified, dropout% of enrolled).
function pct(n, of) {
  const num = Number(n)
  const denom = Number(of)
  if (!Number.isFinite(num) || !Number.isFinite(denom) || denom <= 0) return ''
  return ` (${Math.round((num / denom) * 100)}%)`
}
function cumulativeHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const rows = entries.length >= (block.start || 2) ? entries : entries.concat(Array.from({ length: (block.start || 2) - entries.length }, () => ({})))
  const body = rows
    .map((c, i) => {
      const placedT = blank(c.placed_t) ? '' : `${c.placed_t}${pct(c.placed_t, c.certified_t)}`
      const placedF = blank(c.placed_f) ? '' : `${c.placed_f}${pct(c.placed_f, c.certified_f)}`
      const dropoutT = blank(c.dropout_t) ? '' : `${c.dropout_t}${pct(c.dropout_t, c.enrolled_t)}`
      const dropoutF = blank(c.dropout_f) ? '' : `${c.dropout_f}${pct(c.dropout_f, c.enrolled_f)}`
      return `<tr><td>${i + 1}.</td><td>${esc(c.course)}</td><td>${esc(c.target)}</td>` +
        `<td>${esc(c.enrolled_t)}</td><td>${esc(c.enrolled_f)}</td>` +
        `<td>${esc(c.certified_t)}</td><td>${esc(c.certified_f)}</td>` +
        `<td>${esc(placedT)}</td><td>${esc(placedF)}</td>` +
        `<td>${esc(dropoutT)}</td><td>${esc(dropoutF)}</td></tr>`
    })
    .join('')
  return `<div class="sub-h">${esc(block.heading)}</div>
    <table class="grid cumulative">
      <tr><th rowspan="2">S.N.</th><th rowspan="2">Course Name</th><th rowspan="2">Target</th>
        <th colspan="2">Enrolled</th><th colspan="2">Certified</th>
        <th colspan="2">Job Placed with Percentage</th><th colspan="2">No. of dropouts with Percentage</th></tr>
      <tr><th>T</th><th>F</th><th>T</th><th>F</th><th>T</th><th>F</th><th>T</th><th>F</th></tr>
      ${body}
    </table>
    <p class="note-line">${esc(block.note || 'T= Total and F = Female')}</p>`
}

// 1.60 current batches.
function batchesHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  return `<div class="sub-h">${esc(block.heading)}</div>${cardsTableHtml(block.fields, entries, block.start || 2)}`
}

// section 1's whole body, in Annex-3 order: i)/ii)/iii), 1.10, 1.20-1.25, 1.30-1.34+comments,
// 1.40, 1.50, 1.60, 1.61/1.62.
function sectionOneBodyHtml(section, data) {
  const f = fieldsMap(data)
  const out = []
  out.push(`<div class="kv-line"><b>i) Status of the Training Institute :</b> ${esc(f.status)}</div>`)
  out.push(`<div class="kv-line"><b>ii) Purpose(s) of this Monitoring Visit :</b> ${esc(f.purposes)}</div>`)
  out.push(`<div class="kv-line"><b>iii) Monitoring Team Members with Designations :</b> ${esc(f.team)}</div>`)
  for (const block of section.blocks) {
    if (block.type === 'cards' && block.key === 'persons') out.push(personsHtml(block, data))
    else if (block.type === 'fields' && block.heading === '1.20 Contract/MoU Information') out.push(mouFieldsHtml(block, data))
    else if (block.type === 'fields' && block.heading && block.heading.startsWith('1.30')) out.push(mouFieldsHtml(block, data))
    else if (block.type === 'cards' && block.key === 'mou_courses') out.push(mouCoursesHtml(block, data))
    else if (block.type === 'cards' && block.key === 'cumulative') out.push(cumulativeHtml(block, data))
    else if (block.type === 'cards' && block.key === 'batches') out.push(batchesHtml(block, data))
    else if (block.type === 'fields' && block.fields.some((fl) => fl.key === 'dropout_reasons')) {
      const reasons = block.fields.find((fl) => fl.key === 'dropout_reasons')
      const steps = block.fields.find((fl) => fl.key === 'dropout_steps')
      out.push(`<div class="kv-line"><b>${esc(reasons.label)} :</b></div>${romanLines(f.dropout_reasons)}`)
      out.push(`<div class="kv-line"><b>${esc(steps.label)} :</b></div>${romanLines(f.dropout_steps)}`)
    }
  }
  return out.join('')
}

// ---- sections 2-10 + 8.1: `criteria` blocks -- Sl. | QUALITY CRITERIA | EVIDENCE | REMARKS ----

function criteriaRowHtml(item, data, no) {
  if (item.heading) {
    return `<tr class="heading-row"><td>${esc(item.no)}</td><td colspan="3">${esc(item.text)}</td></tr>`
  }
  const entry = (data.criteria && data.criteria[item.id]) || {}
  const bullets = printedRemarks(item, entry)
  const remarksHtml = bullets.length ? `<ul>${bullets.map((b) => `<li>${esc(b)}</li>`).join('')}</ul>` : ''
  return `<tr><td>${esc(item.no)}</td><td>${esc(item.text)}</td><td>${escMultiline(item.evidence)}</td><td class="rm">${remarksHtml}</td></tr>`
}

function criteriaBlockHtml(block, data) {
  const heading = block.heading ? `<div class="sub-h">${esc(block.heading)}</div>` : ''
  const intro = block.intro ? `<p class="intro">(${esc(block.intro)})</p>` : ''
  const rows = block.items.map((item) => criteriaRowHtml(item, data)).join('')
  return `${heading}${intro}<table class="grid criteria">
    <colgroup><col style="width:7%"><col style="width:31%"><col style="width:31%"><col style="width:31%"></colgroup>
    <tr><th>Sl.</th><th>QUALITY CRITERIA</th><th>EVIDENCE</th><th>REMARKS</th></tr>
    ${rows}
  </table>`
}

// ---- sections 11/12: anonymous feedback, questions down, respondents across ----
function feedbackTableHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const { tables, comments } = feedbackGrid(block, entries)
  const tablesHtml = tables.map((table) => {
    const header = `<tr>${table.headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr>`
    const body = table.rows.map((row) => `<tr><td>${esc(row[0])}</td>${row.slice(1).map((v) => `<td class="c">${esc(v)}</td>`).join('')}</tr>`).join('')
    return `<table class="grid">${header}${body}</table>`
  }).join('')
  const commentsHtml = comments.length ? `<div class="sub-h">Comments</div><ul>${comments.map((c) => `<li>${esc(c)}</li>`).join('')}</ul>` : ''
  return `${tablesHtml}${commentsHtml}`
}

// ---- section 13: component-wise strengths & weaknesses, one row per template pair ----
function strengthsWeaknessesHtml(block, data) {
  const f = fieldsMap(data)
  const rows = block.pairs.map((pair, i) =>
    `<tr><td>${i + 1}.</td><td>${esc(pair.component)}</td><td>${escMultiline(f[pair.strength])}</td><td>${escMultiline(f[pair.weakness])}</td></tr>`,
  ).join('')
  return `<table class="grid"><tr><th>S.N.</th><th>Component</th><th>Strengths</th><th>Weakness</th></tr>${rows}</table>`
}

// ---- section 16: improvement plan, minimum 3 rows ----
function planTableHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const rows = entries.length >= 3 ? entries : entries.concat(Array.from({ length: 3 - entries.length }, () => ({})))
  const body = rows.map((entry, i) => `<tr><td>${i + 1}.</td>${block.fields.map((field) => `<td>${escMultiline(entry[field.key])}</td>`).join('')}</tr>`).join('')
  return `<table class="grid"><tr><th style="width:5%">S.N.</th>${block.fields.map((field) => `<th>${esc(field.label)}</th>`).join('')}</tr>${body}</table>`
}

// ---- sections 14/15: bullet lists from lines ----
function bulletListHtml(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) return '<ul><li class="empty-li"></li></ul>'
  return `<ul>${lines.map((l) => `<li>${esc(l)}</li>`).join('')}</ul>`
}

// section.title is stored verbatim from Annex-3 (spec section 2) -- some are fully upper-case
// ("QUALITY MANAGEMENT SYSTEM"), others are mixed-case after an em dash ("TRAINING DELIVERY
// SYSTEM – Physical Resources") or a lower-case parenthetical ("(full appraisal)"); never
// re-case it here.
function sectionHtml(section, data) {
  const heading = `<div class="sh">${esc(section.number)}. ${esc(section.title)}</div>`
  let body = ''
  if (section.key === 's1') {
    body = sectionOneBodyHtml(section, data)
  } else if (section.blocks.some((b) => b.type === 'criteria')) {
    // each block prints its own intro (block.intro) / sub-heading (block.heading, e.g. "8.1 ...")
    body = section.blocks.map((b) => criteriaBlockHtml(b, data)).join('')
  } else if (section.key === 's11' || section.key === 's12') {
    body = feedbackTableHtml(section.blocks[0], data)
  } else if (section.key === 's13') {
    body = strengthsWeaknessesHtml(section.blocks[0], data)
  } else if (section.key === 's14') {
    body = bulletListHtml(fieldsMap(data).findings)
  } else if (section.key === 's15') {
    body = bulletListHtml(fieldsMap(data).recommendations)
  } else if (section.key === 's16') {
    body = planTableHtml(section.blocks[0], data)
  }
  return `${heading}${body}`
}

// officer signature block: one row per officer named in the free-text "officers" field
// (one per line, or "; "-separated), minimum one blank row.
function officerLines(text) {
  const raw = String(text ?? '').split(/\n|;/).map((s) => s.trim()).filter(Boolean)
  return raw.length ? raw : ['']
}
function signatureHtml(data) {
  const names = officerLines(fieldsMap(data).officers)
  const left = names.map((n, i) => `<div>${i + 1}) ${esc(n)}</div>`).join('')
  const right = names.map(() => '<div>……………………………</div>').join('')
  return `<div class="sign">
    <div>Name (s) of the Visiting Officer(s) with designation${left}</div>
    <div class="sign-right">Signatures${right}</div>
  </div>`
}

const CSS = `
  @page {
    size: A4 portrait;
    margin: 12mm 14mm 14mm;
    @bottom-left { content: "SICIP Quality Assurance Visit Report"; font: 8pt "Times New Roman", Times, serif; color: #333; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 8pt "Times New Roman", Times, serif; color: #333; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "Times New Roman", Times, serif; font-size: 11pt; line-height: 1.3; color: #000; background: #fff; }
  .annex { text-align: right; font-weight: 700; }
  .t1, .t2 { text-align: center; font-weight: 700; }
  .t2 { margin-bottom: 8pt; }
  .kv { margin: 6pt 0 10pt; }
  .kv-line { margin: 2pt 0; }
  .sh { font-weight: 700; text-transform: uppercase; margin: 12pt 0 2pt; }
  .sub-h { font-weight: 700; margin: 8pt 0 2pt; }
  .intro { font-style: italic; margin: 0 0 4pt; }
  .roman-line { margin: 1pt 0 1pt 12pt; }
  .roman { display: inline-block; min-width: 22pt; }
  table.grid { width: 100%; border-collapse: collapse; margin: 4pt 0 8pt; }
  table.grid th, table.grid td { border: 0.75pt solid #000; padding: 2pt 4pt; vertical-align: top; font-size: 10pt; }
  table.grid th { font-weight: 700; text-align: center; background: #eee; }
  table.grid td.c { text-align: center; width: 13%; }
  table.criteria th { text-align: left; }
  table.criteria .heading-row td { font-weight: 700; background: #f4f4f4; }
  table.criteria td.rm ul { margin: 0; padding-left: 14pt; }
  table.criteria td.rm li { margin: 1pt 0; }
  .note-line { font-size: 9pt; font-style: italic; margin: -4pt 0 8pt; }
  .field-box { margin: 4pt 0; }
  .fb-label { font-weight: 700; }
  .fb-body { border: 0.75pt solid #666; min-height: 14pt; padding: 2pt 4pt; }
  .sign { display: flex; justify-content: space-between; margin-top: 18pt; gap: 20pt; }
  .sign-right { text-align: right; }
  ul { margin: 2pt 0; padding-left: 16pt; }
  .empty-li { list-style: none; }
`

// pure fn: template + report data + {officerName, submittedAt?, status} -> full print HTML.
export function qaReportHtml(template, data, meta) {
  const normalizedData = withNormalizedData(template, data)
  const sections = (template.sections || []).map((s) => sectionHtml(s, normalizedData)).join('')
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(template.title)}</title><style>${CSS}</style></head><body>` +
    `<div class="annex">${esc(template.annex || 'Annex-3')}</div>` +
    `<div class="t1">${esc(template.program)}</div>` +
    `<div class="t2">${esc(template.title)}</div>` +
    headerKvHtml(normalizedData) +
    sections +
    signatureHtml(normalizedData) +
    '</body></html>'
}

// unused meta kept for interface parity with reportHtml (officer/status not shown on the
// Annex-3 layout -- the paper form itself has no such fields).
export function openQaReportPrint(template, data, meta) {
  const html = qaReportHtml(template, data, meta)
  const w = window.open('', '_blank')
  if (!w) return false
  w.document.write(html)
  w.document.close()
  w.onload = () => w.print()
  setTimeout(() => w.print(), 300)
  return true
}
