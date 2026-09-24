// shared layout constants for the visit-report exports -- CHANGE SET 3 "ONE REPORT LAYOUT FOR
// ALL OUTPUTS" (2026-09-25): reporthtml.js (print/PDF) and reportdocx.js (Word) must look like
// the same filled-in paper form (~/MEGA/SICIP/20260924-visit-templates-checklists/
// surprise-visit-report-template.pdf), so the column-width table lives here ONCE instead of
// drifting apart between the two renderers (android's pdf/ReportHtml.kt, a port of
// reporthtml.js, should match this table too). Pure data -- no docx/DOM imports here.

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
  { key: 'item', label: 'Item', weight: 44 },
  { key: 'yes', label: 'Yes', weight: 6 },
  { key: 'no', label: 'No', weight: 6 },
  { key: 'partial', label: 'Part', weight: 6 },
  { key: 'na', label: 'N/A', weight: 6 },
  { key: 'remarks', label: 'Remarks', weight: 28 },
]

// interview per-course sub-table (section I): Item | Yes | No | Part | N/A -- no #, no Remarks
// column (interview questions carry no per-question remarks, only a shared feedback field).
export const INTERVIEW_TICK_COLUMNS = [
  { key: 'item', label: 'Item', weight: 76 },
  { key: 'yes', label: 'Yes', weight: 6 },
  { key: 'no', label: 'No', weight: 6 },
  { key: 'partial', label: 'Part', weight: 6 },
  { key: 'na', label: 'N/A', weight: 6 },
]

// flags: narrow tick column + wide text column (CHANGE SET 2 shipped this as a 50/50 split by
// mistake -- fixed here so both renderers share the same narrow tick width).
export const FLAGS_COLUMNS = [
  { key: 'tick', label: '', weight: 6 },
  { key: 'text', label: '', weight: 94 },
]

// page geometry (A4 portrait, twips = 1/20 pt = 1/1440 in): margins top 10mm/sides 11mm/bottom
// 12mm, matching the paper form and both the html @page rule and the docx section margins.
export const PAGE_WIDTH_TWIPS = 11906 // 210mm
export const PAGE_HEIGHT_TWIPS = 16838 // 297mm
export const MARGIN_TOP_TWIPS = 567 // 10mm
export const MARGIN_RIGHT_TWIPS = 624 // 11mm
export const MARGIN_BOTTOM_TWIPS = 680 // 12mm
export const MARGIN_LEFT_TWIPS = 624 // 11mm
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
