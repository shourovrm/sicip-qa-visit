// surprise v2 "narrative" Word file: the Word twin of narrativehtml.js -- each section prints as
// its remark sentences (bullets, issues marked) with tables only for cards blocks. Page, header
// and footer come from reportdocx.js (packDocx/headerParagraphs) so both Word files match.
import { Paragraph } from 'docx'
import {
  CONTENT_WIDTH_TWIPS, answeredQuestionFields, footerTitle, narrativeColumns, weightedWidths,
} from './reportlayout.js'
import {
  bodyCellDxa, bodyTableRow, bulletParagraphs, cardsTableGeometry, cellParagraph, cellRun, fieldValueParagraph, fieldValueRuns, fixedTable, headerCellDxa,
  headerParagraphs, headerTableRow, numberedListParagraphs, packDocx, reportFilename, run, saveDocx, sectionHeadingParagraph,
  subheadParagraph, withNormalizedData,
} from './reportdocx.js'
import { answeredCards, sectionHasContent } from './reporttemplate.js'
import { findingLines } from './findingsbox.js'
import { signoffDocx } from './signoffdocx.js'
import { courseBatchLabel, printedRemarkLines } from './sectionremarks.js'

const value = (obj, key) => String(obj?.[key] ?? '').trim()

// date and time fields that follow each other share one line ("Date of visit: ... Arrival
// time: ... Departure time: ..."); every other filled field gets its own line
const INLINE_KINDS = new Set(['date', 'time'])

function fieldRuns(field, data, leadingGap) {
  const gap = leadingGap ? '    ' : ''
  return [run(`${gap}${field.label.replace(/\?$/, '')}: `, { bold: true }), ...fieldValueRuns(field, value(data.fields, field.key))]
}

// filled plain fields as "Label: value" lines; findings-drafted fields as numbered lists
function fieldsDocx(block, data) {
  const lines = []
  let previousInline = false
  for (const field of block.fields.filter((f) => !f.draftFrom && value(data.fields, f.key))) {
    const inline = INLINE_KINDS.has(field.kind)
    if (inline && previousInline) lines[lines.length - 1].push(...fieldRuns(field, data, true))
    else lines.push(fieldRuns(field, data, false))
    previousInline = inline
  }
  const out = lines.map((children) => new Paragraph({ spacing: { after: 40 }, children }))
  for (const f of block.fields.filter((field) => field.draftFrom === 'findings')) {
    out.push(...numberedListParagraphs(f.label, value(data.fields, f.key).split('\n')))
  }
  return out
}

function table(headers, rows, weights) {
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, weights)
  const head = headerTableRow(headers.map((h, i) => headerCellDxa(h, widths[i])))
  const body = rows.map((cells) => bodyTableRow(cells.map((p, i) => bodyCellDxa(widths[i], Array.isArray(p) ? p : [p]))))
  return fixedTable(widths, [head, ...body])
}

// one table per cards block; columns nobody filled are left out
function cardsDocx(block, data) {
  const cards = data.cards?.[block.key] ?? []
  if (cards.length === 0 || block.narrative === 'bullets') return []
  const { columns, notes } = narrativeColumns(block, cards)
  if (columns.length === 0) return []
  const out = block.heading ? [subheadParagraph(block.heading)] : []
  const { widths, headerSize, margins } = cardsTableGeometry(columns, cards)
  const head = headerTableRow(columns.map((f, i) => headerCellDxa(f.label, widths[i], headerSize)))
  const body = cards.map((c) => bodyTableRow(columns.map((f, i) => bodyCellDxa(widths[i], [fieldValueParagraph(f, value(c, f.key))]))))
  out.push(fixedTable(widths, [head, ...body], margins))
  out.push(...bulletParagraphs(notes.map((text) => ({ text, neg: false }))))
  return out
}

// interviews: questions down, courses across; a question nobody answered is left out, a
// question's remarks follow its answer as a small left-aligned line
function interviewDocx(block, data) {
  const cards = answeredCards(block, data)
  if (cards.length === 0) return []
  const rows = answeredQuestionFields(block, cards).map((f) => {
    const note = block.fields.find((n) => n.noteFor === f.key)
    return [
      [cellParagraph([cellRun(f.label)])],
      ...cards.map((c) => {
        const noteText = note ? value(c, note.key) : ''
        const answer = fieldValueParagraph(f, value(c, f.key))
        if (!noteText) return [answer]
        const noteParagraph = cellParagraph([cellRun(noteText, { size: 16 })])
        // an unanswered question whose remarks are written shows just the remarks
        return value(c, f.key) ? [answer, noteParagraph] : [noteParagraph]
      }),
    ]
  })
  if (rows.length === 0) return []
  const headers = ['Question', ...cards.map((c) => courseBatchLabel(value(c, 'course'), value(c, 'batch')))]
  const courseWeight = cards.length === 1 ? 2 : 1
  return [table(headers, rows, [2, ...cards.map(() => courseWeight)])]
}

function blockDocx(block, section, data, template) {
  if (block.type === 'fields') return fieldsDocx(block, data)
  if (block.type === 'cards') return block.display === 'tabs' ? interviewDocx(block, data) : cardsDocx(block, data)
  if (block.type === 'remarks') return bulletParagraphs(printedRemarkLines(template, section, block, data))
  if (block.type === 'findings') return numberedListParagraphs(block.heading ?? 'Major findings', findingLines(block, data))
  return [] // checklist answers print as the remarks bullets
}

export function buildNarrativeDocx(template, data, meta) {
  const normalized = withNormalizedData(template, data)
  const children = headerParagraphs(template)
  for (const section of template.sections) {
    if (section.optional && !sectionHasContent(section, normalized)) continue
    children.push(sectionHeadingParagraph(section), ...section.blocks.flatMap((b) => blockDocx(b, section, normalized, template)))
  }
  children.push(...signoffDocx(normalized))
  return packDocx(children, footerTitle(template))
}

export async function downloadNarrativeDocx(template, data, meta) {
  const filename = reportFilename(template, data, meta).replace(/\.docx$/, '-narrative.docx')
  return saveDocx(await buildNarrativeDocx(template, data, meta), filename)
}
