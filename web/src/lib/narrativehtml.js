// surprise v2 "narrative" print: same page/header as reporthtml.js, but each section prints as
// its remark sentences (bullets, issues marked) with tables only where rows compare better
// (cards blocks). Checklists print nothing themselves -- their answers are the bullets.
// Port target: android pdf/NarrativeHtml.kt; keep the two in lockstep.
import {
  CSS, esc, fieldValueHtml, headerHtml, numberedListHtml, openPrintWindow, remarkBulletsHtml, withNormalizedData,
} from './reporthtml.js'
import { sectionHasContent } from './reporttemplate.js'
import { printedRemarkLines } from './sectionremarks.js'

const NARRATIVE_CSS = `
  .kv { display: grid; grid-template-columns: 34mm 1fr 30mm 1fr; gap: 1pt 6pt; margin: 0 0 4pt; }
  .kv b::after { content: ':'; }
  td.l, th.l { text-align: left; }
  td small { color: #444; font-size: 6.8pt; }
`

const value = (obj, key) => String(obj?.[key] ?? '').trim()

function fieldsHtml(block, data) {
  const cells = block.fields
    .filter((f) => !f.draftFrom && value(data.fields, f.key))
    .map((f) => `<b>${esc(f.label.replace(/\?$/, ''))}</b><span>${fieldValueHtml(f, value(data.fields, f.key))}</span>`)
    .join('')
  const kv = cells ? `<div class="kv">${cells}</div>` : ''
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
  const columns = block.fields.filter((f) => !f.noteFor && cards.some((c) => value(c, f.key)))
  if (columns.length === 0) return ''
  const head = columns.map((f) => `<th>${esc(f.label)}</th>`).join('')
  const rows = cards.map((c) => `<tr>${columns.map((f) => `<td>${fieldValueHtml(f, value(c, f.key))}</td>`).join('')}</tr>`).join('')
  const heading = block.heading ? `<h3>${esc(block.heading)}</h3>` : ''
  return `${heading}<table class="cards-table"><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table>`
}

// interviews: questions down, courses across; a question's remarks sit under its answer
function interviewTableHtml(block, data) {
  const cards = data.cards?.[block.key] ?? []
  if (cards.length === 0) return ''
  const linked = new Set(block.linkFrom?.fields ?? [])
  const rows = block.fields.filter((f) => !linked.has(f.key) && !f.noteFor)
  const head = '<th class="l">Question</th>' + cards
    .map((c) => `<th>${esc([value(c, 'course'), value(c, 'batch')].filter(Boolean).join(' '))}</th>`)
    .join('')
  const body = rows.map((f) => {
    const note = block.fields.find((n) => n.noteFor === f.key)
    const cells = cards.map((c) => {
      const noteText = note ? value(c, note.key) : ''
      return `<td>${fieldValueHtml(f, value(c, f.key))}${noteText ? `<br><small>${esc(noteText)}</small>` : ''}</td>`
    }).join('')
    return `<tr><td class="l">${esc(f.label)}</td>${cells}</tr>`
  }).join('')
  return `<table class="cards-table"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
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
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(template.title)}</title><style>${CSS}\n${NARRATIVE_CSS}</style></head><body>` +
    headerHtml(template, meta) + sections + '</body></html>'
}

export function openNarrativePrint(template, data, meta) {
  return openPrintWindow(narrativeHtml(template, data, meta))
}
