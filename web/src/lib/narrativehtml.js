// surprise v2 "narrative" print: same page/header as reporthtml.js, but each section prints as
// its remark sentences (bullets, issues marked) with tables only where rows compare better
// (cards blocks). Checklists print nothing themselves -- their answers are the bullets.
// Port target: android pdf/NarrativeHtml.kt; keep the two in lockstep.
import {
  cardsColgroupHtml, esc, fieldCellHtml, fieldValueHtml, headerHtml, numberedListHtml, openPrintWindow, pageCss,
  remarkBulletsHtml, withNormalizedData,
} from './reporthtml.js'
import { answeredQuestionFields, narrativeColumns } from './reportlayout.js'
import { answeredCards, sectionHasContent } from './reporttemplate.js'
import { SIGNOFF_CSS, signoffHtml } from './signoff.js'
import { courseBatchLabel, printedRemarkLines } from './sectionremarks.js'

const NARRATIVE_CSS = `
  .kv { margin: 0 0 4pt; }
  .kv div { margin: 0 0 2pt; }
  .kv b::after { content: ':'; }
  .kv span + b { margin-left: 12pt; }
  td.l, th.l { text-align: left; }
  td .note { display: block; text-align: left; color: #444; font-size: 8pt; margin-top: 2pt; }
`

// date and time fields that follow each other share one line ("Date of visit: ... Arrival
// time: ... Departure time: ..."); every other filled field gets its own line
const INLINE_KINDS = new Set(['date', 'time'])

const value = (obj, key) => String(obj?.[key] ?? '').trim()

function fieldsHtml(block, data) {
  const lines = []
  let previousInline = false
  for (const f of block.fields.filter((field) => !field.draftFrom && value(data.fields, field.key))) {
    const pair = `<b>${esc(f.label.replace(/\?$/, ''))}</b> <span>${fieldValueHtml(f, value(data.fields, f.key))}</span>`
    const inline = INLINE_KINDS.has(f.kind)
    if (inline && previousInline) lines[lines.length - 1] += pair
    else lines.push(pair)
    previousInline = inline
  }
  const kv = lines.length ? `<div class="kv">${lines.map((l) => `<div>${l}</div>`).join('')}</div>` : ''
  const lists = block.fields
    .filter((f) => f.draftFrom === 'findings')
    .map((f) => numberedListHtml(f.label, value(data.fields, f.key).split('\n')))
    .join('')
  return kv + lists
}

// one table per cards block; columns nobody filled are left out
function cardsTableHtml(block, data) {
  const cards = data.cards?.[block.key] ?? []
  if (cards.length === 0 || block.narrative === 'bullets') return ''
  const { columns, notes } = narrativeColumns(block, cards)
  if (columns.length === 0) return ''
  const head = columns.map((f) => `<th>${esc(f.label)}</th>`).join('')
  const rows = cards.map((c) => `<tr>${columns.map((f) => fieldCellHtml(f, value(c, f.key))).join('')}</tr>`).join('')
  const heading = block.heading ? `<h3>${esc(block.heading)}</h3>` : ''
  const noteList = notes.length ? remarkBulletsHtml(notes.map((text) => ({ text, neg: false }))) : ''
  return `${heading}<table class="cards-table">${cardsColgroupHtml(columns)}<thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table>${noteList}`
}

// interviews: questions down, courses across; a question nobody answered is left out, its
// remarks sit under the answer
function interviewTableHtml(block, data) {
  const cards = answeredCards(block, data)
  if (cards.length === 0) return ''
  const rows = answeredQuestionFields(block, cards)
  if (rows.length === 0) return ''
  const head = '<th class="l">Question</th>' + cards
    .map((c) => `<th>${esc(courseBatchLabel(value(c, 'course'), value(c, 'batch')))}</th>`)
    .join('')
  const body = rows.map((f) => {
    const note = block.fields.find((n) => n.noteFor === f.key)
    const cells = cards.map((c) => {
      const noteText = note ? value(c, note.key) : ''
      return `<td class="c">${fieldValueHtml(f, value(c, f.key))}${noteText ? `<span class="note">${esc(noteText)}</span>` : ''}</td>`
    }).join('')
    return `<tr><td class="l">${esc(f.label)}</td>${cells}</tr>`
  }).join('')
  const courseWeight = cards.length === 1 ? 2 : 1
  const colgroup = `<colgroup><col style="width:${(200 / (2 + courseWeight * cards.length)).toFixed(2)}%">${cards.map(() => '<col>').join('')}</colgroup>`
  return `<table class="cards-table">${colgroup}<thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
}

function blockHtml(block, section, data, template) {
  if (block.type === 'fields') return fieldsHtml(block, data)
  if (block.type === 'cards') return block.display === 'tabs' ? interviewTableHtml(block, data) : cardsTableHtml(block, data)
  if (block.type === 'remarks') {
    const lines = printedRemarkLines(template, section, block, data)
    return lines.length ? remarkBulletsHtml(lines) : ''
  }
  if (block.type === 'findings') return numberedListHtml(block.heading ?? 'Major findings', (data.findings ?? []).map((f) => f.text))
  return '' // checklist answers print as the remarks bullets
}

export function narrativeHtml(template, data, meta) {
  const normalized = withNormalizedData(template, data)
  const sections = template.sections
    .filter((s) => !s.optional || sectionHasContent(s, normalized))
    .map((s) => `<h2><span class="letter">${esc(s.letter)}</span>${esc(s.title)}</h2>${s.blocks.map((b) => blockHtml(b, s, normalized, template)).join('')}`)
    .join('')
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(template.title)}</title><style>${pageCss(template)}\n${NARRATIVE_CSS}\n${SIGNOFF_CSS}</style></head><body>` +
    headerHtml(template) + sections + signoffHtml(normalized) + '</body></html>'
}

export function openNarrativePrint(template, data, meta) {
  return openPrintWindow(narrativeHtml(template, data, meta))
}
