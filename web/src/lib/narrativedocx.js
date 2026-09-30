// surprise v2 "narrative" Word file: the Word twin of narrativehtml.js -- each section prints as
// its remark sentences (bullets, issues marked) with tables only for cards blocks. Page, header
// and footer come from reportdocx.js (packDocx/headerParagraphs) so both Word files match.
import { Paragraph, ShadingType, TableRow } from 'docx'
import { CONTENT_WIDTH_TWIPS, weightedWidths } from './reportlayout.js'
import {
  bodyCellDxa, fieldValueParagraph, fieldValueRuns, fixedTable, headerCellDxa, headerParagraphs, listParagraphs,
  packDocx, reportFilename, run, saveDocx, subheadParagraph, withNormalizedData,
} from './reportdocx.js'
import { sectionHasContent } from './reporttemplate.js'
import { printedRemarkLines } from './sectionremarks.js'

const value = (obj, key) => String(obj?.[key] ?? '').trim()

function sectionHeading(section) {
  return new Paragraph({
    keepNext: true,
    spacing: { before: 200, after: 60 },
    children: [
      run(` ${section.letter} `, { bold: true, color: 'FFFFFF', size: 18, shading: { type: ShadingType.CLEAR, fill: '111111' } }),
      run(`  ${section.title}`, { bold: true, size: 19 }),
    ],
  })
}

// filled plain fields as "Label: value" lines; findings-drafted fields as numbered lists
function fieldsDocx(block, data) {
  const out = block.fields
    .filter((f) => !f.draftFrom && value(data.fields, f.key))
    .map((f) => {
      return new Paragraph({ children: [run(`${f.label.replace(/\?$/, '')}: `, { bold: true }), ...fieldValueRuns(f, value(data.fields, f.key))] })
    })
  for (const f of block.fields.filter((field) => field.draftFrom === 'findings')) {
    out.push(...listParagraphs(f.label, value(data.fields, f.key).split('\n').map((text) => ({ text })), true))
  }
  return out
}

function table(headers, rows) {
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, headers.map(() => 1))
  const head = new TableRow({ children: headers.map((h, i) => headerCellDxa(h, widths[i])) })
  const body = rows.map((cells) => new TableRow({ children: cells.map((p, i) => bodyCellDxa(widths[i], [p])) }))
  return fixedTable(widths, [head, ...body])
}

// one table per cards block; columns nobody filled are left out
function cardsDocx(block, data) {
  const cards = data.cards?.[block.key] ?? []
  if (cards.length === 0 || block.narrative === 'bullets') return []
  const columns = block.fields.filter((f) => !f.noteFor && cards.some((c) => value(c, f.key)))
  if (columns.length === 0) return []
  const out = block.heading ? [subheadParagraph(block.heading)] : []
  out.push(table(columns.map((f) => f.label), cards.map((c) => columns.map((f) => fieldValueParagraph(f, value(c, f.key))))))
  return out
}

// interviews: questions down, courses across; a question's remarks follow its answer
function interviewDocx(block, data) {
  const cards = data.cards?.[block.key] ?? []
  if (cards.length === 0) return []
  const linked = new Set(block.linkFrom?.fields ?? [])
  const rows = block.fields.filter((f) => !linked.has(f.key) && !f.noteFor).map((f) => {
    const note = block.fields.find((n) => n.noteFor === f.key)
    return [
      new Paragraph({ children: [run(f.label)] }),
      ...cards.map((c) => {
        const noteText = note ? value(c, note.key) : ''
        return new Paragraph({ children: [...fieldValueRuns(f, value(c, f.key)), ...(noteText ? [run(` — ${noteText}`, { size: 14 })] : [])] })
      }),
    ]
  })
  const headers = ['Question', ...cards.map((c) => [value(c, 'course'), value(c, 'batch')].filter(Boolean).join(' '))]
  return [table(headers, rows)]
}

function blockDocx(block, section, data, template) {
  if (block.type === 'fields') return fieldsDocx(block, data)
  if (block.type === 'cards') return block.display === 'tabs' ? interviewDocx(block, data) : cardsDocx(block, data)
  if (block.type === 'remarks') {
    const lines = printedRemarkLines(template, section, block, data)
    return lines.length ? listParagraphs(block.heading ?? 'Remarks', lines, false) : []
  }
  if (block.type === 'findings') return listParagraphs(block.heading ?? 'Major findings', (data.findings ?? []).map((f) => ({ text: f.text })), true)
  return [] // checklist answers print as the remarks bullets
}

export function buildNarrativeDocx(template, data, meta) {
  const normalized = withNormalizedData(template, data)
  const children = headerParagraphs(template, meta)
  for (const section of template.sections) {
    if (section.optional && !sectionHasContent(section, normalized)) continue
    children.push(sectionHeading(section), ...section.blocks.flatMap((b) => blockDocx(b, section, normalized, template)))
  }
  return packDocx(children)
}

export async function downloadNarrativeDocx(template, data, meta) {
  const filename = reportFilename(template, data, meta).replace(/\.docx$/, '-narrative.docx')
  return saveDocx(await buildNarrativeDocx(template, data, meta), filename)
}
