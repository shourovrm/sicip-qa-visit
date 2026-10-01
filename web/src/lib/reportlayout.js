// shared layout constants for the visit-report exports -- CHANGE SET 3 "ONE REPORT LAYOUT FOR
// ALL OUTPUTS" (2026-09-25): reporthtml.js (print/PDF) and reportdocx.js (Word) must look like
// the same filled-in paper form (~/MEGA/SICIP/20260924-visit-templates-checklists/
// surprise-visit-report-template.pdf), so the column-width table lives here ONCE instead of
// drifting apart between the two renderers (android's pdf/ReportHtml.kt, a port of
// reporthtml.js, should match this table too). Pure data -- no docx/DOM imports here.

import { courseBatchLabel } from './sectionremarks.js'

// answer/choice tone colours -- hex, no '#' (docx colour fields want bare hex; reporthtml.js
// prepends '#' for CSS). Must match android ui/theme/Color.kt light values.
export const TONE_COLOR = { yes: '1c6b38', no: 'b3261e', partial: '8a4600', na: '4c4f66' }

export const TICK_CHECKED = '☒' // ☒ BALLOT BOX WITH X
export const TICK_UNCHECKED = '☐' // ☐ BALLOT BOX

// checklist table: # | Item | Yes | No | Part | N/A | Remarks. Weights sum to 100 -- used
// directly as CSS % (reporthtml.js) or scaled to twips (reportdocx.js) via weightedWidths().
// This is the fix for the "Item column ~10%, tick columns huge" bug reported after CHANGE SET 2.
export const CHECKLIST_COLUMNS = [
  { key: 'num', label: '#', weight: 4 },
  { key: 'item', label: 'Item', weight: 38 },
  { key: 'yes', label: 'Yes', weight: 7.5 },
  { key: 'no', label: 'No', weight: 7.5 },
  { key: 'partial', label: 'Part', weight: 7.5 },
  { key: 'na', label: 'N/A', weight: 7.5 },
  { key: 'remarks', label: 'Remarks', weight: 28 },
]

// interview per-course sub-table (section I): Item | Yes | No | Part | N/A -- no #, no Remarks
// column (interview questions carry no per-question remarks, only a shared feedback field).
export const INTERVIEW_TICK_COLUMNS = [
  { key: 'item', label: 'Item', weight: 70 },
  { key: 'yes', label: 'Yes', weight: 7.5 },
  { key: 'no', label: 'No', weight: 7.5 },
  { key: 'partial', label: 'Part', weight: 7.5 },
  { key: 'na', label: 'N/A', weight: 7.5 },
]

// flags: narrow tick column + wide text column (CHANGE SET 2 shipped this as a 50/50 split by
// mistake -- fixed here so both renderers share the same narrow tick width).
// surprise v2 interviews: questions carry their own remarks box (`noteFor` fields)
export const INTERVIEW_NOTE_COLUMNS = [
  { label: 'Item', weight: 42 },
  { label: 'Yes', weight: 7.5 },
  { label: 'No', weight: 7.5 },
  { label: 'Part', weight: 7.5 },
  { label: 'N/A', weight: 7.5 },
  { label: 'Remarks', weight: 28 },
]

export const FLAGS_COLUMNS = [
  { key: 'tick', label: '', weight: 6 },
  { key: 'text', label: '', weight: 94 },
]

// page geometry (A4 portrait, twips = 1/20 pt = 1/1440 in): Word "Moderate" margins, 1 in
// top/bottom and 0.75 in sides (layout v3, 2026-10-01) -- the html @page rules use the same
// numbers in mm (PAGE_MARGIN_CSS) so print and Word wrap alike.
export const PAGE_WIDTH_TWIPS = 11906 // 210mm
export const PAGE_HEIGHT_TWIPS = 16838 // 297mm
export const MARGIN_TOP_TWIPS = 1440 // 25.4mm
export const MARGIN_RIGHT_TWIPS = 1080 // 19.05mm
export const MARGIN_BOTTOM_TWIPS = 1440
export const MARGIN_LEFT_TWIPS = 1080
export const PAGE_MARGIN_CSS = '25.4mm 19.05mm'
// table cell padding: 4 pt top/bottom, 6 pt left/right (Word cell margins in twips)
export const CELL_MARGINS_TWIPS = { top: 80, bottom: 80, left: 120, right: 120 }
export const CELL_PADDING_CSS = '4pt 6pt'
export const CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS // 10658

// split `total` across `weights` (need not sum to 100 -- scaled proportionally); the last
// column absorbs the rounding remainder so the widths always add back up to `total` exactly.
// Used for both docx twips (total = CONTENT_WIDTH_TWIPS) and generic even card-table columns
// (total = 100, one weight of 1 per field).
export function weightedWidths(total, weights) {
  const sum = weights.reduce((a, b) => a + b, 0)
  const widths = weights.map((w) => Math.floor((w / sum) * total))
  const used = widths.reduce((a, b) => a + b, 0)
  widths[widths.length - 1] += total - used
  return widths
}

// a field counts as a "standard yes/no/partial/na choice" (renders with the same 4-tick-column
// style as a checklist row, e.g. section I's q1-q7) when its option ids are exactly the
// template's answer ids -- checked by id set, not by key, so this stays template-driven.
export function isStandardAnswerChoice(field, template) {
  if (field.kind !== 'choice') return false
  const answerIds = template.answers.map((a) => a.id)
  const optionIds = (field.options || []).map((o) => o.id)
  return optionIds.length === answerIds.length && answerIds.every((id) => optionIds.includes(id))
}

// "13:29" or "13:29:00" -> "1:29 PM" for print; anything else passes through unchanged
export function displayTime(value) {
  const match = /^(\d{1,2}):(\d{2})/.exec(String(value ?? ''))
  if (!match) return value
  const hour24 = Number(match[1])
  const suffix = hour24 < 12 ? 'AM' : 'PM'
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12
  return `${hour12}:${match[2]} ${suffix}`
}

// footer text: the title up to any colon ("Surprise Visit Report: Quality Assurance" ->
// "Surprise Visit Report")
export function footerTitle(template) {
  return String(template.title ?? '').split(':')[0].trim()
}

// table cells that hold a count, batch no., date, time or a short choice answer are centred;
// names and sentences stay left. A courseRef "Welding · 07" names a course, so it is text.
const CENTRED_KINDS = new Set(['number', 'choice', 'date', 'time', 'phone'])
export function isCentredField(field) {
  if (field.kind === 'courseRef') return false
  return CENTRED_KINDS.has(field.kind) || field.key === 'batch'
}

// item 14: a blank table cell prints "-" in every output (tick-box cells keep their glyphs and
// never pass through here, nor do header and heading rows)
export const BLANK_CELL_TEXT = '-'
export function cellOrDash(value) {
  const text = String(value ?? '').trim()
  return text === '' ? BLANK_CELL_TEXT : String(value)
}

// for tables built from plain strings (QA): a number, "8 (40%)", "Yes" -- not a name or sentence
export function isShortValue(value) {
  const text = String(value ?? '').trim()
  if (!text) return false
  if (/^[\d\s.,%()/-]+$/.test(text)) return true
  if (/^(yes|no|n\/a|partial)$/i.test(text)) return true
  // a code such as "B-77" or "BTEB"
  return text.length <= 6 && !text.includes(' ')
}

// interview questions worth printing: a row (with its note) appears only when at least one
// course answered it; linked course/batch fields and the note fields themselves are not rows
export function answeredQuestionFields(block, cards) {
  const linked = new Set(block.linkFrom?.fields ?? [])
  const filled = (card, key) => key != null && String(card?.[key] ?? '').trim() !== ''
  return block.fields
    .filter((field) => !linked.has(field.key) && !field.noteFor)
    .filter((field) => {
      const note = block.fields.find((n) => n.noteFor === field.key)
      return cards.some((card) => filled(card, field.key) || filled(card, note?.key))
    })
}

// relative column width of a field in a cards table: names and sentences get twice the room of
// a count; a choice label ("Not employed") needs a little more than a count
export function columnWeight(field) {
  if (field.kind === 'choice') return 1.4
  if (field.kind === 'phone') return 1.5
  if (isCentredField(field)) return 1
  return 2
}

// narrative cards table: columns somebody filled (no note boxes). A table wider than 6 columns
// cannot fit a sentence column at 9 pt without breaking words, so its long-text fields print as
// "<row>: <text>" lines under the table instead.
const MAX_COLUMNS_WITH_TEXT = 6
export function narrativeColumns(block, cards) {
  const filled = (card, key) => String(card?.[key] ?? '').trim() !== ''
  const used = block.fields.filter((f) => !f.noteFor && cards.some((c) => filled(c, f.key)))
  if (used.length <= MAX_COLUMNS_WITH_TEXT) return { columns: used, notes: [] }
  const textFields = used.filter((f) => f.kind === 'longtext')
  const notes = []
  for (const card of cards) {
    const rowLabel = block.titleField === 'course'
      ? courseBatchLabel(card.course, card.batch)
      : String(card[block.titleField] ?? '').trim()
    for (const f of textFields) {
      if (filled(card, f.key)) notes.push(`${rowLabel}: ${String(card[f.key]).trim()}`)
    }
  }
  return { columns: used.filter((f) => f.kind !== 'longtext'), notes }
}

// ---- cards table column widths that never break a word (2026-10-02) ----
// Word and the browser break a word that is wider than its column ("Enrolle d"). Each column
// gets at least the width of its longest header word and its longest short value token; a
// table that cannot fit that at the 9 pt header drops the header to 8 pt with 3 pt side
// padding (`dense`). Widths are estimated from average Arial glyph widths (em fractions).
export const CONTENT_WIDTH_PT = CONTENT_WIDTH_TWIPS / 20
const BOLD_EM = 0.6
const REGULAR_EM = 0.56
const BODY_PT = 9
const MAX_TOKEN_CHARS = 18 // "Entrepreneurship" fits; anything longer may still wrap
const PADDING_PT = { normal: 6, dense: 3 }
const HEADER_PT = { normal: 9, dense: 8 }

const longestWord = (text) => String(text ?? '').split(/\s+/).reduce((max, word) => Math.max(max, word.length), 0)

function printedText(field, value) {
  if (field.kind === 'choice') return field.options?.find((o) => o.id === value)?.label ?? String(value ?? '')
  return String(value ?? '')
}

// the narrowest a column may be, in pt: its longest header word or body word
function minTextPt(label, texts, dense) {
  const headerPt = dense ? HEADER_PT.dense : HEADER_PT.normal
  const padding = dense ? PADDING_PT.dense : PADDING_PT.normal
  const header = longestWord(label) * BOLD_EM * headerPt
  const bodyChars = Math.min(MAX_TOKEN_CHARS, Math.max(1, ...texts.map(longestWord)))
  return Math.max(header, bodyChars * REGULAR_EM * BODY_PT) + 2 * padding + 1
}

export function minColumnPt(field, cards, dense) {
  return minTextPt(field.label, (cards ?? []).map((card) => printedText(field, card?.[field.key])), dense)
}

// weights -> pt widths where no column is under its minimum; null when the minimums don't fit
function fitWidths(minimums, weights, total) {
  if (minimums.reduce((a, b) => a + b, 0) > total) return null
  const pinned = new Set()
  for (;;) {
    const free = total - [...pinned].reduce((sum, i) => sum + minimums[i], 0)
    const weightSum = weights.reduce((sum, w, i) => (pinned.has(i) ? sum : sum + w), 0)
    const widths = weights.map((w, i) => (pinned.has(i) ? minimums[i] : (w / weightSum) * free))
    const short = widths.map((w, i) => i).filter((i) => !pinned.has(i) && widths[i] < minimums[i])
    if (short.length === 0) return widths
    short.forEach((i) => pinned.add(i))
  }
}

// columns = [{label, texts (printed cell strings), weight}] -> {percents (of the content width,
// sum 100), dense}
export function columnLayout(columns) {
  const weights = columns.map((c) => c.weight)
  for (const dense of [false, true]) {
    const widths = fitWidths(columns.map((c) => minTextPt(c.label, c.texts, dense)), weights, CONTENT_WIDTH_PT)
    if (widths) return { percents: widths.map((w) => (w / CONTENT_WIDTH_PT) * 100), dense }
  }
  const total = weights.reduce((a, b) => a + b, 0)
  return { percents: weights.map((w) => (w / total) * 100), dense: true }
}

export function cardsColumnLayout(fields, cards) {
  return columnLayout(fields.map((field) => ({
    label: field.label,
    texts: (cards ?? []).map((card) => printedText(field, card?.[field.key])),
    weight: columnWeight(field),
  })))
}

// a plain {headers, rows} table (QA prints): headers + string rows + starting weights
export function tableColumnLayout(headers, rows, weights) {
  return columnLayout(headers.map((label, i) => ({ label, texts: rows.map((row) => String(row[i] ?? '')), weight: weights[i] })))
}

// Word: dense tables use 3 pt side padding; dense header text is 8 pt (half-points 16)
export const DENSE_CELL_MARGINS_TWIPS = { top: 80, bottom: 80, left: 60, right: 60 }
export const DENSE_HEADER_HALF_POINTS = 16
