// filled QA visit report (the paper form's exact layout + a 4th Remarks column) as printable
// HTML -- spec docs/superpowers/plans/2026-09-25-qa-report.md section 7. A separate layout from
// the surprise report's reporthtml.js: the form's own heading-by-heading, table-by-table shape,
// Arial at the surprise report sizes (layout v3), program + title centred ("Annex-3" top right on v1 only). qa-v2 adds tables
// from qatables.js (registration, MoU, contracts, sample check, rooms, damaged equipment, the
// evidence list) and numbered evidence in the EVIDENCE column. Caller opens the html in a new
// window + window.print(), same pattern as reporthtml.js/billhtml.js.
import { buildRemarks, printedRemarks } from './remarks.js'
import * as reportTemplateModule from './reporttemplate.js'
import { feedbackGrid } from './feedbackgrid.js'
import { SIGNOFF_CSS, signoffHtml } from './signoff.js'
import { CELL_PADDING_CSS, PAGE_MARGIN_CSS, cellOrDash, footerTitle, isShortValue } from './reportlayout.js'
import {
  batchesTable, contractsTable, criteriaCardsTable, cumulativeTable, evidenceIndexTable, evidenceLines, headerRowsOf,
  mouCoursesTable, mouTable, officersLine, personsTable, planTable, registrationTable, withoutLineHint,
} from './qatables.js'

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
  // qa-v2 keeps officers as cards; v1 as one text box
  const officers = (data.cards?.officers ?? []).length ? officersLine(data) : f.officers
  const dateLine = `from ${ddmmyyyy(f.date_from) || '__/__/____'} to ${ddmmyyyy(f.date_to) || '__/__/____'}`
  return `<div class="kv">
    ${kvLine('Name of Training TI/TC Visited', f.ti_name)}
    ${kvLine('Name of the Association/Provider', f.provider)}
    ${kvLine('Address of Training Institute and Contact', f.address)}
    <div class="kv-line"><b>Date(s) of Visit :</b> ${esc(dateLine)}</div>
    ${kvLine("Name(s) & Designation(s) of Visiting Officer(s)", officers)}
  </div>`
}

// one body cell: counts, percentages and short answers centred, text left; blank prints "-"
function cellHtml(value) {
  const shown = cellOrDash(value)
  return `<td${isShortValue(shown) ? ' class="ctr"' : ''}>${escMultiline(shown)}</td>`
}

function headerCellHtml(cell) {
  const span = `${cell.colSpan > 1 ? ` colspan="${cell.colSpan}"` : ''}${cell.rowSpan > 1 ? ` rowspan="${cell.rowSpan}"` : ''}`
  return `<th${span}>${esc(cell.text)}</th>`
}

function colgroupHtml(weights) {
  const total = weights.reduce((sum, weight) => sum + weight, 0)
  return `<colgroup>${weights.map((weight) => `<col style="width:${((weight / total) * 100).toFixed(2)}%">`).join('')}</colgroup>`
}

// qatables.js {heading?, headers | headerRows, rows, weights?, note?, dense?} -> a grid table with
// its header rows in <thead> (repeats on every page); null -> nothing
function tableHtml(table, headingOverride) {
  if (!table) return ''
  const heading = headingOverride ?? table.heading
  const head = headerRowsOf(table).map((cells) => `<tr>${cells.map(headerCellHtml).join('')}</tr>`).join('')
  const body = table.rows.map((row) => `<tr>${row.map(cellHtml).join('')}</tr>`).join('')
  const classes = `grid${table.dense ? ' dense' : ''}${table.weights ? ' fixed' : ''}`
  const colgroup = table.weights ? colgroupHtml(table.weights) : ''
  const note = table.note ? `<p class="note-line">${esc(table.note)}</p>` : ''
  return `${heading ? `<div class="sub-h">${esc(heading)}</div>` : ''}<table class="${classes}">${colgroup}<thead>${head}</thead><tbody>${body}</tbody></table>${note}`
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

function longtextBox(label, value) {
  return `<div class="field-box"><div class="fb-label">${esc(label)}</div><div class="fb-body">${escMultiline(value)}</div></div>`
}

// qa-v2 section 1: registration table, officers as team members, MoU + contracts tables
function sectionOneV2Html(section, template, data) {
  const f = fieldsMap(data)
  const out = []
  out.push('<div class="kv-line"><b>i) Status of the Training Institute :</b></div>')
  out.push(tableHtml(registrationTable(template, data)))
  if (!blank(f.other_registration)) out.push(`<div class="kv-line">Other registration: ${escMultiline(f.other_registration)}</div>`)
  out.push(`<div class="kv-line"><b>ii) Purpose(s) of this Monitoring Visit :</b> ${escMultiline(f.purposes)}</div>`)
  out.push(`<div class="kv-line"><b>iii) Monitoring Team Members with Designations :</b> ${esc(officersLine(data))}</div>`)
  for (const block of section.blocks) {
    if (block.key === 'persons') out.push(tableHtml(personsTable(block, data)))
    else if (block.key === 'mous') out.push(tableHtml(mouTable(template, data), block.heading) || `<div class="sub-h">${esc(block.heading)}</div>`)
    else if (block.fields?.some((field) => field.key === 'other_contract')) {
      const answer = block.fields[0].options.find((o) => o.id === f.other_contract)?.label ?? ''
      out.push(`<div class="sub-h">${esc(block.heading)}: ${esc(answer)}</div>`)
      if (f.other_contract === 'yes') {
        out.push(tableHtml(contractsTable(template, data)))
        if (!blank(f.comments)) out.push(longtextBox('Comments of the Visiting Officer', f.comments))
      }
    }
    else if (block.key === 'mou_courses') out.push(tableHtml(mouCoursesTable(block, data)))
    else if (block.key === 'cumulative') out.push(tableHtml(cumulativeTable(block, data)))
    else if (block.key === 'batches') out.push(tableHtml(batchesTable(block, data)))
    else if (block.fields?.some((field) => field.key === 'dropout_reasons')) {
      for (const key of ['dropout_reasons', 'dropout_steps']) {
        const field = block.fields.find((fl) => fl.key === key)
        out.push(`<div class="kv-line"><b>${esc(withoutLineHint(field.label))} :</b></div>${romanLines(f[key])}`)
      }
    }
  }
  return out.join('')
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
    if (block.type === 'cards' && block.key === 'persons') out.push(tableHtml(personsTable(block, data)))
    else if (block.type === 'fields' && block.heading === '1.20 Contract/MoU Information') out.push(mouFieldsHtml(block, data))
    else if (block.type === 'fields' && block.heading && block.heading.startsWith('1.30')) out.push(mouFieldsHtml(block, data))
    else if (block.type === 'cards' && block.key === 'mou_courses') out.push(tableHtml(mouCoursesTable(block, data)))
    else if (block.type === 'cards' && block.key === 'cumulative') out.push(tableHtml(cumulativeTable(block, data)))
    else if (block.type === 'cards' && block.key === 'batches') out.push(tableHtml(batchesTable(block, data)))
    else if (block.type === 'fields' && block.fields.some((fl) => fl.key === 'dropout_reasons')) {
      const reasons = block.fields.find((fl) => fl.key === 'dropout_reasons')
      const steps = block.fields.find((fl) => fl.key === 'dropout_steps')
      out.push(`<div class="kv-line"><b>${esc(withoutLineHint(reasons.label))} :</b></div>${romanLines(f.dropout_reasons)}`)
      out.push(`<div class="kv-line"><b>${esc(withoutLineHint(steps.label))} :</b></div>${romanLines(f.dropout_steps)}`)
    }
  }
  return out.join('')
}

// ---- sections 2-10 + 8.1: `criteria` blocks -- Sl. | QUALITY CRITERIA | EVIDENCE | REMARKS ----

function criteriaRowHtml(item, data, template) {
  if (item.heading) {
    return `<tr class="heading-row"><td>${esc(item.no)}</td><td colspan="3">${esc(item.text)}</td></tr>`
  }
  const entry = (data.criteria && data.criteria[item.id]) || {}
  const bullets = printedRemarks(item, entry)
  const remarksHtml = bullets.length ? `<ul>${bullets.map((b) => `<li>${esc(b)}</li>`).join('')}</ul>` : esc(cellOrDash(''))
  // qa-v2: the numbered evidence the officer saw; v1: the form's own evidence text
  const evidence = template.evidenceRegister ? evidenceLines(data, item.id).join('\n') : item.evidence
  return `<tr><td>${esc(item.no)}</td><td>${esc(item.text)}</td><td>${escMultiline(cellOrDash(evidence))}</td><td class="rm">${remarksHtml}</td></tr>`
}

function criteriaBlockHtml(block, data, template) {
  // qa-v2 tables that sit under a criteria table (4.2 sample check, 8 rooms, damaged equipment)
  if (block.type === 'cards') return tableHtml(criteriaCardsTable(block, data))
  const heading = block.heading ? `<div class="sub-h">${esc(block.heading)}</div>` : ''
  const intro = block.intro ? `<p class="intro">(${esc(block.intro)})</p>` : ''
  const rows = block.items.map((item) => criteriaRowHtml(item, data, template)).join('')
  return `${heading}${intro}<table class="grid criteria">
    <colgroup><col style="width:7%"><col style="width:31%"><col style="width:31%"><col style="width:31%"></colgroup>
    <thead><tr><th>Sl.</th><th>QUALITY CRITERIA</th><th>EVIDENCE</th><th>REMARKS</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>`
}

// ---- sections 11/12: anonymous feedback, questions down, respondents across ----
function feedbackTableHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const { tables, comments } = feedbackGrid(block, entries)
  const tablesHtml = tables.map((table) => {
    const header = `<tr>${table.headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr>`
    const body = table.rows.map((row) => `<tr><td>${esc(row[0])}</td>${row.slice(1).map((v) => `<td class="c">${esc(cellOrDash(v))}</td>`).join('')}</tr>`).join('')
    return `<table class="grid"><thead>${header}</thead><tbody>${body}</tbody></table>`
  }).join('')
  const commentsHtml = comments.length ? `<div class="sub-h">Comments</div><ul>${comments.map((c) => `<li>${esc(c)}</li>`).join('')}</ul>` : ''
  return `${tablesHtml}${commentsHtml}`
}

// ---- section 13: component-wise strengths & weaknesses, one row per template pair ----
function strengthsWeaknessesHtml(block, data) {
  const f = fieldsMap(data)
  const rows = block.pairs.map((pair, i) =>
    `<tr><td>${i + 1}.</td><td>${esc(pair.component)}</td><td>${escMultiline(cellOrDash(f[pair.strength]))}</td><td>${escMultiline(cellOrDash(f[pair.weakness]))}</td></tr>`,
  ).join('')
  return `<table class="grid"><thead><tr><th>S.N.</th><th>Component</th><th>Strengths</th><th>Weakness</th></tr></thead><tbody>${rows}</tbody></table>`
}

// ---- section 16: improvement plan ----
function planTableHtml(block, data) {
  return tableHtml(planTable(block, data))
}

// ---- sections 14/15: numbered lists from lines ----
function numberedListHtml(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) return '<ol><li class="empty-li"></li></ol>'
  return `<ol>${lines.map((l) => `<li>${esc(l)}</li>`).join('')}</ol>`
}

// section.title is stored verbatim from Annex-3 (spec section 2) -- some are fully upper-case
// ("QUALITY MANAGEMENT SYSTEM"), others are mixed-case after an em dash ("TRAINING DELIVERY
// SYSTEM – Physical Resources") or a lower-case parenthetical ("(full appraisal)"); never
// re-case it here.
function sectionHtml(section, data, template) {
  const heading = `<div class="sh">${esc(section.number)}. ${esc(section.title)}</div>`
  let body = ''
  if (section.key === 's1') {
    const v2 = section.blocks.some((b) => b.key === 'officers')
    body = v2 ? sectionOneV2Html(section, template, data) : sectionOneBodyHtml(section, data)
  } else if (section.blocks.some((b) => b.type === 'criteria')) {
    // each block prints its own intro (block.intro) / sub-heading (block.heading, e.g. "8.1 ...")
    body = section.blocks.map((b) => criteriaBlockHtml(b, data, template)).join('')
  } else if (section.key === 's11' || section.key === 's12') {
    body = feedbackTableHtml(section.blocks[0], data)
  } else if (section.key === 's13') {
    body = strengthsWeaknessesHtml(section.blocks[0], data)
  } else if (section.key === 's14') {
    body = numberedListHtml(fieldsMap(data).findings)
  } else if (section.key === 's15') {
    body = numberedListHtml(fieldsMap(data).recommendations)
  } else if (section.key === 's16') {
    body = planTableHtml(section.blocks[0], data)
  }
  return `${heading}${body}`
}


function pageCss(template) {
  return `
  @page {
    size: A4 portrait;
    margin: ${PAGE_MARGIN_CSS};
    @bottom-left { content: "${footerTitle(template).replace(/"/g, '')}"; font: 8pt Arial, sans-serif; color: #333; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 8pt Arial, sans-serif; color: #333; }
  }`
}

// layout v3: Arial, body 10 pt, tables 9 pt, section heading 11 pt, sub-heading 10 pt,
// title 15 pt over a rule, program line 9 pt
const CSS = `
  * { box-sizing: border-box; }
  body { margin: 0; font-family: Arial, "Noto Sans", sans-serif; font-size: 10pt; line-height: 1.3; color: #000; background: #fff; }
  .annex { text-align: right; font-weight: 700; }
  .t1, .t2 { text-align: center; font-weight: 700; }
  .t1 { font-size: 9pt; }
  .t2 { font-size: 15pt; border-bottom: 1.5pt solid #111; padding-bottom: 4pt; margin-bottom: 8pt; }
  .kv { margin: 6pt 0 10pt; }
  .kv-line { margin: 2pt 0; }
  .sh { font-weight: 700; font-size: 11pt; text-transform: uppercase; margin: 12pt 0 3pt; }
  .sub-h { font-weight: 700; font-size: 10pt; margin: 8pt 0 3pt; }
  .intro { font-style: italic; margin: 0 0 4pt; }
  .roman-line { margin: 1pt 0 1pt 12pt; }
  .roman { display: inline-block; min-width: 22pt; }
  table.grid { width: 100%; border-collapse: collapse; margin: 4pt 0 8pt; }
  table.grid th, table.grid td { border: 0.75pt solid #000; padding: ${CELL_PADDING_CSS}; vertical-align: top; font-size: 9pt; }
  table.grid.fixed { table-layout: fixed; }
  table.grid td, table.grid th { overflow-wrap: break-word; }
  table.grid.dense th { font-size: 8pt; }
  table.grid.dense th, table.grid.dense td { padding: 4pt 3pt; }
  table.grid td.ctr { text-align: center; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; page-break-inside: avoid; }
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
  ul, ol { margin: 2pt 0; padding-left: 16pt; }
  .empty-li { list-style: none; }
`

// pure fn: template + report data + {officerName, submittedAt?, status} -> full print HTML.
export function qaReportHtml(template, data, meta) {
  const normalizedData = withNormalizedData(template, data)
  const sections = (template.sections || []).map((s) => sectionHtml(s, normalizedData, template)).join('')
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(template.title)}</title><style>${pageCss(template)}\n${CSS}\n${SIGNOFF_CSS}</style></head><body>` +
    (template.annex ? `<div class="annex">${esc(template.annex)}</div>` : '') +
    `<div class="t1">${esc(template.program)}</div>` +
    `<div class="t2">${esc(template.title)}</div>` +
    headerKvHtml(normalizedData) +
    sections +
    tableHtml(evidenceIndexTable(template, normalizedData)) +
    signoffHtml(normalizedData) +
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
