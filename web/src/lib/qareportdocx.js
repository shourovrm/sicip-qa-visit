// filled QA visit report (Annex-3 exact layout + a 4th Remarks column) as a downloadable .docx --
// spec docs/superpowers/plans/2026-09-25-qa-report.md section 7. Table-for-table copy of
// qareporthtml.js, same relationship reportdocx.js has to reporthtml.js. Uses the `docx` package
// already in this repo (context7 /dolanmiu/docx).
import {
  AlignmentType, BorderStyle, Document, Packer, Paragraph,
  ShadingType, Table, TableCell, TableLayoutType, TextRun, VerticalAlign, WidthType,
} from 'docx'
import {
  CELL_MARGINS_TWIPS, CONTENT_WIDTH_TWIPS, cellOrDash, footerTitle, isShortValue, weightedWidths,
} from './reportlayout.js'
import { bodyTableRow, headerTableRow } from './reportdocx.js'
import { A4_PAGE, NUMBERING, numberedParagraphs, pageFooter } from './docxparts.js'
import { printedRemarks } from './remarks.js'
import { feedbackGrid } from './feedbackgrid.js'
import { signoffDocx } from './signoffdocx.js'
import {
  batchesTable, contractsTable, criteriaCardsTable, cumulativeTable, evidenceIndexTable, evidenceLines, headerRowsOf,
  mouCoursesTable, mouTable, officersLine, personsTable, planTable, registrationTable, withoutLineHint,
} from './qatables.js'
import * as reportTemplateModule from './reporttemplate.js'

// layout v3 (2026-10-01): Arial and the surprise report sizes -- body 10 pt, tables 9 pt,
// section heading 11 pt, sub-heading 10 pt bold, title 15 pt, program 9 pt, footer 8 pt
const FONT = 'Arial'
const BORDER_COLOR = '000000'
const HEADER_FILL = 'EEEEEE'
const HEADING_FILL = 'F4F4F4'
const NOTE_COLOR = '333333'

const TITLE_SIZE = 30 // 15pt
const PROGRAM_SIZE = 18 // 9pt
const SECTION_SIZE = 22 // 11pt
const BODY_SIZE = 20 // 10pt
const TABLE_SIZE = 18 // 9pt
const SMALL_SIZE = 18 // 9pt
const DENSE_HEADER_SIZE = 16 // 8pt header text for tables with 9+ columns
const DENSE_CELL_MARGINS_TWIPS = { ...CELL_MARGINS_TWIPS, left: 60, right: 60 } // 3pt, so 9+ narrow columns fit whole words
const FOOTER_SIZE = 16 // 8pt

function blank(v) {
  return v == null || String(v).trim() === ''
}

function withNormalizedData(template, data) {
  const normalize = reportTemplateModule.normalize
  const step = typeof normalize === 'function' ? normalize : null
  if (!step) return data
  const cloned = JSON.parse(JSON.stringify(data))
  return step(template, cloned) || cloned
}

function ddmmyyyy(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value ?? ''))
  if (!match) return blank(value) ? '' : String(value)
  return `${match[3]}/${match[2]}/${match[1]}`
}

const ROMAN = ['i', 'ii', 'iii', 'iv', 'v', 'vi', 'vii', 'viii', 'ix', 'x', 'xi', 'xii', 'xiii', 'xiv', 'xv', 'xvi', 'xvii', 'xviii', 'xix', 'xx']

const CELL_BORDERS = {
  top: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR },
  bottom: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR },
  left: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR },
  right: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR },
}
const TABLE_BORDERS = {
  top: CELL_BORDERS.top, bottom: CELL_BORDERS.bottom, left: CELL_BORDERS.left, right: CELL_BORDERS.right,
  insideHorizontal: CELL_BORDERS.top, insideVertical: CELL_BORDERS.top,
}

function fixedTable(widths, rows, margins = CELL_MARGINS_TWIPS) {
  return new Table({
    width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA }, columnWidths: widths, layout: TableLayoutType.FIXED,
    borders: TABLE_BORDERS, margins, rows,
  })
}

function run(text, opts = {}) {
  return new TextRun({ text, font: FONT, size: BODY_SIZE, ...opts })
}

// table text: 9 pt, centred when it is a count, percentage or short answer
function cellText(value) {
  const text = cellOrDash(value)
  if (text.includes('\n')) return multilineParagraph(text, { size: TABLE_SIZE })
  const alignment = isShortValue(text) ? AlignmentType.CENTER : AlignmentType.LEFT
  return new Paragraph({ alignment, children: [run(text, { size: TABLE_SIZE })] })
}

function multilineParagraph(value, opts = {}, paraOpts = {}) {
  if (blank(value)) return new Paragraph({ ...paraOpts, children: [run('', opts)] })
  const lines = String(value).split('\n')
  const children = lines.map((line, i) => run(line, { ...opts, break: i < lines.length - 1 ? 1 : undefined }))
  return new Paragraph({ ...paraOpts, children })
}

function headerCell(text, widthTwips, opts = {}, spans = {}) {
  return new TableCell({
    width: { size: widthTwips, type: WidthType.DXA },
    columnSpan: spans.colSpan,
    rowSpan: spans.rowSpan,
    verticalAlign: VerticalAlign.CENTER,
    borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(text, { bold: true, size: SMALL_SIZE, ...opts })] })],
  })
}

// a table cell's (possibly multi-line) text; blank prints "-"
function cellMultiline(value, opts = {}) {
  return multilineParagraph(cellOrDash(value), opts)
}

function bodyCell(widthTwips, paragraphs, opts = {}) {
  return new TableCell({ width: { size: widthTwips, type: WidthType.DXA }, borders: CELL_BORDERS, children: paragraphs, ...opts })
}

function kvParagraph(label, value) {
  return new Paragraph({ spacing: { after: 40 }, children: [run(`${label} : `, { bold: true }), run(blank(value) ? '' : String(value))] })
}

// keepNext: a table's heading never sits alone at the foot of a page (1.40 did)
function subheadParagraph(text) {
  return new Paragraph({ keepNext: true, spacing: { before: 120, after: 40 }, children: [run(text, { bold: true })] })
}

// ---- section 1 ----

function fieldsMap(data) {
  return data.fields || {}
}

function headerParagraphs(data) {
  const f = fieldsMap(data)
  // qa-v2 keeps officers as cards; v1 as one text box
  const officers = (data.cards?.officers ?? []).length ? officersLine(data) : f.officers
  const dateLine = `from ${ddmmyyyy(f.date_from) || '__/__/____'} to ${ddmmyyyy(f.date_to) || '__/__/____'}`
  return [
    kvParagraph('Name of Training TI/TC Visited', f.ti_name),
    kvParagraph('Name of the Association/Provider', f.provider),
    kvParagraph('Address of Training Institute and Contact', f.address),
    kvParagraph('Date(s) of Visit', dateLine),
    kvParagraph("Name(s) & Designation(s) of Visiting Officer(s)", officers),
  ]
}

function mouFieldsDocx(block, data) {
  const f = fieldsMap(data)
  const out = block.heading ? [subheadParagraph(block.heading)] : []
  for (const field of block.fields) {
    if (field.kind === 'longtext') {
      out.push(new Paragraph({ spacing: { before: 60, after: 20 }, children: [run(field.label, { bold: true })] }))
      out.push(multilineParagraph(f[field.key], {}, { spacing: { after: 80 } }))
    } else if (field.kind === 'choice') {
      const opt = (field.options || []).find((o) => o.id === f[field.key])
      out.push(kvParagraph(field.label, opt ? opt.label : ''))
    } else {
      out.push(kvParagraph(field.label, f[field.key]))
    }
  }
  return out
}

function romanParagraphs(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  return lines.map((line, i) => new Paragraph({ indent: { left: 240 }, children: [run(`${ROMAN[i] ?? i + 1}) `, {}), run(line)] }))
}

// the header rows of `table` as docx rows; a cell's colSpan sums the widths it covers and a
// rowSpan cell is left out of the rows below it (docx merges them). Every header row repeats on
// each page.
function headerRowsDocx(table, widths) {
  const headerSize = table.dense ? DENSE_HEADER_SIZE : SMALL_SIZE
  const coveredByRow = new Map()
  return headerRowsOf(table).map((cells, rowIndex) => {
    const covered = coveredByRow.get(rowIndex) ?? new Set()
    let column = 0
    const tableCells = cells.map((cell) => {
      while (covered.has(column)) column += 1
      const colSpan = cell.colSpan ?? 1
      const rowSpan = cell.rowSpan ?? 1
      const width = widths.slice(column, column + colSpan).reduce((sum, columnWidth) => sum + columnWidth, 0)
      for (let below = 1; below < rowSpan; below += 1) {
        const coveredBelow = coveredByRow.get(rowIndex + below) ?? new Set()
        for (let offset = 0; offset < colSpan; offset += 1) coveredBelow.add(column + offset)
        coveredByRow.set(rowIndex + below, coveredBelow)
      }
      const spans = { colSpan: colSpan > 1 ? colSpan : undefined, rowSpan: rowSpan > 1 ? rowSpan : undefined }
      column += colSpan
      return headerCell(cell.text, width, { size: headerSize }, spans)
    })
    return headerTableRow(tableCells)
  })
}

// qatables.js {heading?, headers | headerRows, rows, weights?, note?, dense?} -> [heading?, table, note?];
// null -> nothing. Tables without weights keep the old rule: narrow S.N./No. column, equal rest.
function tableDocx(table, headingOverride) {
  if (!table) return []
  const heading = headingOverride ?? table.heading
  const weights = table.weights ?? table.headers.map((h) => (h === 'S.N.' || h === 'No.' ? 7 : 20))
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, weights)
  const bodyRows = table.rows.map((row) => bodyTableRow(row.map((cell, i) => bodyCell(widths[i], [cellText(cell)]))))
  const note = table.note
    ? [new Paragraph({ spacing: { before: 40, after: 80 }, children: [run(table.note, { italics: true, size: SMALL_SIZE, color: NOTE_COLOR })] })]
    : []
  return [...(heading ? [subheadParagraph(heading)] : []), fixedTable(widths, [...headerRowsDocx(table, widths), ...bodyRows], table.dense ? DENSE_CELL_MARGINS_TWIPS : CELL_MARGINS_TWIPS), ...note]
}

// qa-v2 section 1: registration table, officers as team members, MoU + contracts tables
function sectionOneV2Docx(section, template, data) {
  const f = fieldsMap(data)
  const out = [new Paragraph({ spacing: { after: 40 }, children: [run('i) Status of the Training Institute :', { bold: true })] })]
  out.push(...tableDocx(registrationTable(template, data)))
  if (!blank(f.other_registration)) out.push(kvParagraph('Other registration', f.other_registration))
  out.push(kvParagraph('ii) Purpose(s) of this Monitoring Visit', f.purposes))
  out.push(kvParagraph('iii) Monitoring Team Members with Designations', officersLine(data)))
  for (const block of section.blocks) {
    if (block.key === 'persons') out.push(...tableDocx(personsTable(block, data)))
    else if (block.key === 'mous') {
      const table = tableDocx(mouTable(template, data), block.heading)
      out.push(...(table.length ? table : [subheadParagraph(block.heading)]))
    } else if (block.fields?.some((field) => field.key === 'other_contract')) {
      const answer = block.fields[0].options.find((o) => o.id === f.other_contract)?.label ?? ''
      out.push(subheadParagraph(`${block.heading}: ${answer}`))
      if (f.other_contract === 'yes') {
        out.push(...tableDocx(contractsTable(template, data)))
        if (!blank(f.comments)) {
          out.push(new Paragraph({ spacing: { before: 60, after: 20 }, children: [run('Comments of the Visiting Officer', { bold: true })] }))
          out.push(multilineParagraph(f.comments, {}, { spacing: { after: 80 } }))
        }
      }
    } else if (block.key === 'mou_courses') out.push(...tableDocx(mouCoursesTable(block, data)))
    else if (block.key === 'cumulative') out.push(...tableDocx(cumulativeTable(block, data)))
    else if (block.key === 'batches') out.push(...tableDocx(batchesTable(block, data)))
    else if (block.fields?.some((field) => field.key === 'dropout_reasons')) {
      for (const key of ['dropout_reasons', 'dropout_steps']) {
        const field = block.fields.find((fl) => fl.key === key)
        out.push(new Paragraph({ spacing: { before: 60 }, children: [run(withoutLineHint(field.label), { bold: true })] }))
        out.push(...romanParagraphs(f[key]))
      }
    }
  }
  return out
}

function sectionOneDocx(section, data) {
  const f = fieldsMap(data)
  const out = [
    kvParagraph('i) Status of the Training Institute', f.status),
    kvParagraph('ii) Purpose(s) of this Monitoring Visit', f.purposes),
    kvParagraph('iii) Monitoring Team Members with Designations', f.team),
  ]
  for (const block of section.blocks) {
    if (block.type === 'cards' && block.key === 'persons') out.push(...tableDocx(personsTable(block, data)))
    else if (block.type === 'fields' && block.heading === '1.20 Contract/MoU Information') out.push(...mouFieldsDocx(block, data))
    else if (block.type === 'fields' && block.heading && block.heading.startsWith('1.30')) out.push(...mouFieldsDocx(block, data))
    else if (block.type === 'cards' && block.key === 'mou_courses') out.push(...tableDocx(mouCoursesTable(block, data)))
    else if (block.type === 'cards' && block.key === 'cumulative') out.push(...tableDocx(cumulativeTable(block, data)))
    else if (block.type === 'cards' && block.key === 'batches') out.push(...tableDocx(batchesTable(block, data)))
    else if (block.type === 'fields' && block.fields.some((fl) => fl.key === 'dropout_reasons')) {
      const reasons = block.fields.find((fl) => fl.key === 'dropout_reasons')
      const steps = block.fields.find((fl) => fl.key === 'dropout_steps')
      out.push(new Paragraph({ spacing: { before: 60 }, children: [run(withoutLineHint(reasons.label), { bold: true })] }))
      out.push(...romanParagraphs(f.dropout_reasons))
      out.push(new Paragraph({ spacing: { before: 60 }, children: [run(withoutLineHint(steps.label), { bold: true })] }))
      out.push(...romanParagraphs(f.dropout_steps))
    }
  }
  return out
}

// ---- sections 2-10 + 8.1: criteria blocks ----

function criteriaRowDocx(item, data, widths, template) {
  if (item.heading) {
    return bodyTableRow([
    bodyCell(widths[0], [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(item.no, { size: TABLE_SIZE })] })], { shading: { type: ShadingType.CLEAR, fill: HEADING_FILL } }),
    new TableCell({
      columnSpan: 3, width: { size: widths[1] + widths[2] + widths[3], type: WidthType.DXA }, borders: CELL_BORDERS,
      shading: { type: ShadingType.CLEAR, fill: HEADING_FILL },
      children: [new Paragraph({ children: [run(item.text, { bold: true, size: TABLE_SIZE })] })],
    }),
    ])
  }
  const entry = (data.criteria && data.criteria[item.id]) || {}
  const bullets = printedRemarks(item, entry)
  const remarksParagraphs = bullets.length
    ? bullets.map((b) => new Paragraph({ bullet: { level: 0 }, children: [run(b, { size: TABLE_SIZE })] }))
    : [new Paragraph({ children: [run(cellOrDash(''), { size: TABLE_SIZE })] })]
  return bodyTableRow([
    bodyCell(widths[0], [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(item.no, { size: TABLE_SIZE })] })]),
    bodyCell(widths[1], [new Paragraph({ children: [run(item.text, { size: TABLE_SIZE })] })]),
    // qa-v2: the numbered evidence the officer saw; v1: the form's own evidence text
    bodyCell(widths[2], [cellMultiline(template.evidenceRegister ? evidenceLines(data, item.id).join('\n') : item.evidence, { size: TABLE_SIZE })]),
    bodyCell(widths[3], remarksParagraphs),
  ])
}

function criteriaBlockDocx(block, data, template) {
  // qa-v2 tables that sit under a criteria table (4.2 sample check, 8 rooms, damaged equipment)
  if (block.type === 'cards') return tableDocx(criteriaCardsTable(block, data))
  const out = []
  if (block.heading) out.push(subheadParagraph(block.heading))
  if (block.intro) out.push(new Paragraph({ spacing: { after: 60 }, children: [run(`(${block.intro})`, { italics: true })] }))
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [7, 31, 31, 31])
  const headerRow = headerTableRow(['Sl.', 'QUALITY CRITERIA', 'EVIDENCE', 'REMARKS'].map((t, i) => headerCell(t, widths[i])))
  const rows = block.items.map((item) => criteriaRowDocx(item, data, widths, template))
  out.push(fixedTable(widths, [headerRow, ...rows]))
  return out
}

// ---- sections 11/12: anonymous feedback, questions down, respondents across ----

function feedbackDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const { tables, comments } = feedbackGrid(block, entries)
  const out = tables.map((table) => {
    const respondentWeights = table.headers.slice(1).map(() => 13)
    const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [100 - 13 * respondentWeights.length, ...respondentWeights])
    const headerRow = headerTableRow(table.headers.map((h, i) => headerCell(h, widths[i])))
    const rows = table.rows.map((row) => bodyTableRow(
      row.map((value, i) => bodyCell(widths[i], [new Paragraph({ alignment: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER, children: [run(i === 0 ? value : cellOrDash(value), { size: TABLE_SIZE })] })])),
    ))
    return fixedTable(widths, [headerRow, ...rows])
  })
  if (comments.length) {
    out.push(subheadParagraph('Comments'))
    out.push(...comments.map((comment) => new Paragraph({ bullet: { level: 0 }, children: [run(comment)] })))
  }
  return out
}

// ---- section 13: one row per template pair ----

function strengthsWeaknessesDocx(block, data) {
  const f = fieldsMap(data)
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [6, 24, 35, 35])
  const headerRow = headerTableRow(['S.N.', 'Component', 'Strengths', 'Weakness'].map((t, i) => headerCell(t, widths[i])))
  const rows = block.pairs.map((pair, i) => bodyTableRow([
    bodyCell(widths[0], [cellText(`${i + 1}.`)]),
    bodyCell(widths[1], [cellText(pair.component)]),
    bodyCell(widths[2], [cellMultiline(f[pair.strength], { size: TABLE_SIZE })]),
    bodyCell(widths[3], [cellMultiline(f[pair.weakness], { size: TABLE_SIZE })]),
  ]))
  return [fixedTable(widths, [headerRow, ...rows])]
}

// ---- section 16: improvement plan, minimum 3 rows ----

function planDocx(block, data) {
  return tableDocx(planTable(block, data))
}

// ---- sections 14/15: findings and recommendations as real Word numbered lists ----

function numberedLines(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) return [new Paragraph({ children: [run('')] })]
  return numberedParagraphs(lines, (line) => [run(line)])
}

function sectionDocx(section, data, template) {
  const out = [new Paragraph({ keepNext: true, spacing: { before: 200, after: 60 }, children: [run(`${section.number}. ${section.title}`, { bold: true, size: SECTION_SIZE })] })]
  if (section.key === 's1') {
    const v2 = section.blocks.some((b) => b.key === 'officers')
    out.push(...(v2 ? sectionOneV2Docx(section, template, data) : sectionOneDocx(section, data)))
  } else if (section.blocks.some((b) => b.type === 'criteria')) {
    for (const block of section.blocks) out.push(...criteriaBlockDocx(block, data, template))
  } else if (section.key === 's11' || section.key === 's12') {
    out.push(...feedbackDocx(section.blocks[0], data))
  } else if (section.key === 's13') {
    out.push(...strengthsWeaknessesDocx(section.blocks[0], data))
  } else if (section.key === 's14') {
    out.push(...numberedLines(fieldsMap(data).findings))
  } else if (section.key === 's15') {
    out.push(...numberedLines(fieldsMap(data).recommendations))
  } else if (section.key === 's16') {
    out.push(...planDocx(section.blocks[0], data))
  }
  return out
}

// pure fn: template + report data + meta -> Promise<Blob> (.docx).
export function buildQaReportDocx(template, data, _meta) {
  const normalizedData = withNormalizedData(template, data)
  const children = [
    ...(template.annex ? [new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(template.annex, { bold: true })] })] : []),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 20 }, children: [run(template.program, { bold: true, size: PROGRAM_SIZE })] }),
    // title over a rule, like the surprise reports
    new Paragraph({
      alignment: AlignmentType.CENTER,
      border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: '111111', space: 4 } },
      spacing: { after: 160 },
      children: [run(template.title, { bold: true, size: TITLE_SIZE })],
    }),
    ...headerParagraphs(normalizedData),
  ]
  for (const section of template.sections || []) children.push(...sectionDocx(section, normalizedData, template))
  children.push(...tableDocx(evidenceIndexTable(template, normalizedData)))
  children.push(...signoffDocx(normalizedData))

  const doc = new Document({
    styles: { default: { document: { run: { font: FONT, size: BODY_SIZE } } } },
    numbering: NUMBERING,
    sections: [{
      properties: { page: A4_PAGE },
      footers: { default: pageFooter(footerTitle(template), { font: FONT, size: FOOTER_SIZE, color: NOTE_COLOR }) },
      children,
    }],
  })
  return Packer.toBlob(doc)
}

function prefillValue(template, data, prefillName) {
  for (const section of template.sections || []) {
    for (const block of section.blocks || []) {
      if (block.type !== 'fields') continue
      const field = block.fields.find((f) => f.prefill === prefillName)
      if (field) return (data.fields || {})[field.key]
    }
  }
  return undefined
}

function sanitizeFilenamePart(s) {
  const cleaned = String(s ?? '').trim().replace(/[^A-Za-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return cleaned || 'report'
}

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function reportFilename(template, data, meta) {
  const institute = sanitizeFilenamePart(prefillValue(template, data, 'institute'))
  const visitDate = prefillValue(template, data, 'visit_date')
  const date = !blank(visitDate) ? visitDate : (!blank(meta?.submittedAt) ? String(meta.submittedAt).slice(0, 10) : todayIso())
  return `QA-visit-${institute}-${date}.docx`
}

export async function downloadQaReportDocx(template, data, meta) {
  const blob = await buildQaReportDocx(template, data, meta)
  const filename = reportFilename(template, data, meta)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  return filename
}
