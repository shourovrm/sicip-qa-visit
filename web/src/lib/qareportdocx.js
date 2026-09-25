// filled QA visit report (Annex-3 exact layout + a 4th Remarks column) as a downloadable .docx --
// spec docs/superpowers/plans/2026-09-25-qa-report.md section 7. Table-for-table copy of
// qareporthtml.js, same relationship reportdocx.js has to reporthtml.js. Uses the `docx` package
// already in this repo (context7 /dolanmiu/docx).
import {
  AlignmentType, BorderStyle, Document, Footer, Packer, PageNumber, PageOrientation, Paragraph,
  ShadingType, Tab, Table, TableCell, TableLayoutType, TableRow, TabStopType, TextRun, WidthType,
} from 'docx'
import {
  CONTENT_WIDTH_TWIPS, MARGIN_BOTTOM_TWIPS, MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS,
  MARGIN_TOP_TWIPS, PAGE_HEIGHT_TWIPS, PAGE_WIDTH_TWIPS, weightedWidths,
} from './reportlayout.js'
import { printedRemarks } from './remarks.js'
import { feedbackGrid } from './feedbackgrid.js'
import * as reportTemplateModule from './reporttemplate.js'

const FONT = 'Times New Roman'
const BORDER_COLOR = '000000'
const HEADER_FILL = 'EEEEEE'
const HEADING_FILL = 'F4F4F4'
const NOTE_COLOR = '333333'

const TITLE_SIZE = 28 // 14pt
const PROGRAM_SIZE = 24 // 12pt
const SECTION_SIZE = 24 // 12pt
const BODY_SIZE = 22 // 11pt
const SMALL_SIZE = 18 // 9pt

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

function fixedTable(widths, rows) {
  return new Table({ width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA }, columnWidths: widths, layout: TableLayoutType.FIXED, borders: TABLE_BORDERS, rows })
}

function run(text, opts = {}) {
  return new TextRun({ text, font: FONT, size: BODY_SIZE, ...opts })
}

function multilineParagraph(value, opts = {}, paraOpts = {}) {
  if (blank(value)) return new Paragraph({ ...paraOpts, children: [run('', opts)] })
  const lines = String(value).split('\n')
  const children = lines.map((line, i) => run(line, { ...opts, break: i < lines.length - 1 ? 1 : undefined }))
  return new Paragraph({ ...paraOpts, children })
}

function headerCell(text, widthTwips, opts = {}) {
  return new TableCell({
    width: { size: widthTwips, type: WidthType.DXA },
    borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(text, { bold: true, size: SMALL_SIZE, ...opts })] })],
  })
}

function bodyCell(widthTwips, paragraphs, opts = {}) {
  return new TableCell({ width: { size: widthTwips, type: WidthType.DXA }, borders: CELL_BORDERS, children: paragraphs, ...opts })
}

function kvParagraph(label, value) {
  return new Paragraph({ spacing: { after: 40 }, children: [run(`${label} : `, { bold: true }), run(blank(value) ? '' : String(value))] })
}

function subheadParagraph(text) {
  return new Paragraph({ spacing: { before: 120, after: 40 }, children: [run(text, { bold: true })] })
}

// ---- section 1 ----

function fieldsMap(data) {
  return data.fields || {}
}

function headerParagraphs(data) {
  const f = fieldsMap(data)
  const dateLine = `from ${ddmmyyyy(f.date_from) || '__/__/____'} to ${ddmmyyyy(f.date_to) || '__/__/____'}`
  return [
    kvParagraph('Name of Training TI/TC Visited', f.ti_name),
    kvParagraph('Name of the Association/Provider', f.provider),
    kvParagraph('Address of Training Institute and Contact', f.address),
    kvParagraph('Date(s) of Visit', dateLine),
    kvParagraph("Name(s) & Designation(s) of Visiting Officer(s)", f.officers),
  ]
}

function cardsTableDocx(fields, entries, minRows) {
  const rows = entries.length >= minRows ? entries : entries.concat(Array.from({ length: minRows - entries.length }, () => ({})))
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, fields.map(() => 1))
  const headerRow = new TableRow({ children: fields.map((f, i) => headerCell(f.label, widths[i])) })
  const bodyRows = rows.map((entry) => new TableRow({ children: fields.map((f, i) => bodyCell(widths[i], [multilineParagraph(entry[f.key])])) }))
  return fixedTable(widths, [headerRow, ...bodyRows])
}

function personsDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  return [subheadParagraph(block.heading), cardsTableDocx(block.fields, entries, block.start || 3)]
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

function pct(n, of) {
  const num = Number(n)
  const denom = Number(of)
  if (!Number.isFinite(num) || !Number.isFinite(denom) || denom <= 0) return ''
  return ` (${Math.round((num / denom) * 100)}%)`
}

function cumulativeDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const minRows = block.start || 2
  const rows = entries.length >= minRows ? entries : entries.concat(Array.from({ length: minRows - entries.length }, () => ({})))
  const cols = 11
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, Array.from({ length: cols }, () => 1))
  const spanCell = (text, width, span) => new TableCell({
    width: { size: width, type: WidthType.DXA }, columnSpan: span, borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(text, { bold: true, size: SMALL_SIZE })] })],
  })
  const headerRow1 = new TableRow({
    children: [
      headerCell('S.N.', widths[0]), headerCell('Course Name', widths[1]), headerCell('Target', widths[2]),
      spanCell('Enrolled', widths[3] + widths[4], 2), spanCell('Certified', widths[5] + widths[6], 2),
      spanCell('Job Placed with Percentage', widths[7] + widths[8], 2), spanCell('No. of dropouts with Percentage', widths[9] + widths[10], 2),
    ],
  })
  const headerRow2 = new TableRow({ children: ['T', 'F', 'T', 'F', 'T', 'F', 'T', 'F'].map((t, i) => headerCell(t, widths[3 + i])) })
  const bodyRows = rows.map((c, i) => {
    const placedT = blank(c.placed_t) ? '' : `${c.placed_t}${pct(c.placed_t, c.certified_t)}`
    const placedF = blank(c.placed_f) ? '' : `${c.placed_f}${pct(c.placed_f, c.certified_f)}`
    const dropoutT = blank(c.dropout_t) ? '' : `${c.dropout_t}${pct(c.dropout_t, c.enrolled_t)}`
    const dropoutF = blank(c.dropout_f) ? '' : `${c.dropout_f}${pct(c.dropout_f, c.enrolled_f)}`
    const cells = [String(i + 1), c.course, c.target, c.enrolled_t, c.enrolled_f, c.certified_t, c.certified_f, placedT, placedF, dropoutT, dropoutF]
    return new TableRow({ children: cells.map((text, ci) => bodyCell(widths[ci], [new Paragraph({ children: [run(blank(text) ? '' : String(text))] })])) })
  })
  return [
    subheadParagraph(block.heading),
    fixedTable(widths, [headerRow1, headerRow2, ...bodyRows]),
    new Paragraph({ spacing: { before: 40, after: 80 }, children: [run(block.note || 'T= Total and F = Female', { italics: true, size: SMALL_SIZE, color: NOTE_COLOR })] }),
  ]
}

function batchesDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  return [subheadParagraph(block.heading), cardsTableDocx(block.fields, entries, block.start || 2)]
}

function romanParagraphs(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  return lines.map((line, i) => new Paragraph({ indent: { left: 240 }, children: [run(`${ROMAN[i] ?? i + 1}) `, {}), run(line)] }))
}

function sectionOneDocx(section, data) {
  const f = fieldsMap(data)
  const out = [
    kvParagraph('i) Status of the Training Institute', f.status),
    kvParagraph('ii) Purpose(s) of this Monitoring Visit', f.purposes),
    kvParagraph('iii) Monitoring Team Members with Designations', f.team),
  ]
  for (const block of section.blocks) {
    if (block.type === 'cards' && block.key === 'persons') out.push(...personsDocx(block, data))
    else if (block.type === 'fields' && block.heading === '1.20 Contract/MoU Information') out.push(...mouFieldsDocx(block, data))
    else if (block.type === 'fields' && block.heading && block.heading.startsWith('1.30')) out.push(...mouFieldsDocx(block, data))
    else if (block.type === 'cards' && block.key === 'mou_courses') { out.push(subheadParagraph(block.heading)); out.push(cardsTableDocx(block.fields, (data.cards && data.cards[block.key]) || [], block.start || 4)) }
    else if (block.type === 'cards' && block.key === 'cumulative') out.push(...cumulativeDocx(block, data))
    else if (block.type === 'cards' && block.key === 'batches') out.push(...batchesDocx(block, data))
    else if (block.type === 'fields' && block.fields.some((fl) => fl.key === 'dropout_reasons')) {
      const reasons = block.fields.find((fl) => fl.key === 'dropout_reasons')
      const steps = block.fields.find((fl) => fl.key === 'dropout_steps')
      out.push(new Paragraph({ spacing: { before: 60 }, children: [run(reasons.label, { bold: true })] }))
      out.push(...romanParagraphs(f.dropout_reasons))
      out.push(new Paragraph({ spacing: { before: 60 }, children: [run(steps.label, { bold: true })] }))
      out.push(...romanParagraphs(f.dropout_steps))
    }
  }
  return out
}

// ---- sections 2-10 + 8.1: criteria blocks ----

function criteriaRowDocx(item, data, widths) {
  if (item.heading) {
    return new TableRow({
      children: [
        bodyCell(widths[0], [new Paragraph({ children: [run(item.no)] })], { shading: { type: ShadingType.CLEAR, fill: HEADING_FILL } }),
        new TableCell({
          columnSpan: 3, width: { size: widths[1] + widths[2] + widths[3], type: WidthType.DXA }, borders: CELL_BORDERS,
          shading: { type: ShadingType.CLEAR, fill: HEADING_FILL },
          children: [new Paragraph({ children: [run(item.text, { bold: true })] })],
        }),
      ],
    })
  }
  const entry = (data.criteria && data.criteria[item.id]) || {}
  const bullets = printedRemarks(item, entry)
  const remarksParagraphs = bullets.length
    ? bullets.map((b) => new Paragraph({ bullet: { level: 0 }, children: [run(b)] }))
    : [new Paragraph({ children: [run('')] })]
  return new TableRow({
    children: [
      bodyCell(widths[0], [new Paragraph({ children: [run(item.no)] })]),
      bodyCell(widths[1], [new Paragraph({ children: [run(item.text)] })]),
      bodyCell(widths[2], [multilineParagraph(item.evidence)]),
      bodyCell(widths[3], remarksParagraphs),
    ],
  })
}

function criteriaBlockDocx(block, data) {
  const out = []
  if (block.heading) out.push(subheadParagraph(block.heading))
  if (block.intro) out.push(new Paragraph({ spacing: { after: 60 }, children: [run(`(${block.intro})`, { italics: true })] }))
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [7, 31, 31, 31])
  const headerRow = new TableRow({ children: ['Sl.', 'QUALITY CRITERIA', 'EVIDENCE', 'REMARKS'].map((t, i) => headerCell(t, widths[i])) })
  const rows = block.items.map((item) => criteriaRowDocx(item, data, widths))
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
    const headerRow = new TableRow({ children: table.headers.map((h, i) => headerCell(h, widths[i])) })
    const rows = table.rows.map((row) => new TableRow({
      children: row.map((value, i) => bodyCell(widths[i], [new Paragraph({ alignment: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER, children: [run(value)] })])),
    }))
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
  const headerRow = new TableRow({ children: ['S.N.', 'Component', 'Strengths', 'Weakness'].map((t, i) => headerCell(t, widths[i])) })
  const rows = block.pairs.map((pair, i) => new TableRow({
    children: [
      bodyCell(widths[0], [new Paragraph({ children: [run(`${i + 1}.`)] })]),
      bodyCell(widths[1], [new Paragraph({ children: [run(pair.component)] })]),
      bodyCell(widths[2], [multilineParagraph(f[pair.strength])]),
      bodyCell(widths[3], [multilineParagraph(f[pair.weakness])]),
    ],
  }))
  return [fixedTable(widths, [headerRow, ...rows])]
}

// ---- section 16: improvement plan, minimum 3 rows ----

function planDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const rows = entries.length >= 3 ? entries : entries.concat(Array.from({ length: 3 - entries.length }, () => ({})))
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [6, 30, 34, 15, 15])
  const headerRow = new TableRow({ children: ['S.N.', ...block.fields.map((field) => field.label)].map((t, i) => headerCell(t, widths[i])) })
  const bodyRows = rows.map((entry, i) => new TableRow({
    children: [
      bodyCell(widths[0], [new Paragraph({ children: [run(`${i + 1}.`)] })]),
      ...block.fields.map((field, j) => bodyCell(widths[j + 1], [multilineParagraph(entry[field.key])])),
    ],
  }))
  return [fixedTable(widths, [headerRow, ...bodyRows])]
}

// ---- sections 14/15 ----

function bulletParagraphs(text) {
  const lines = String(text ?? '').split('\n').map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) return [new Paragraph({ children: [run('')] })]
  return lines.map((line) => new Paragraph({ bullet: { level: 0 }, children: [run(line)] }))
}

function sectionDocx(section, data) {
  const out = [new Paragraph({ keepNext: true, spacing: { before: 200, after: 60 }, children: [run(`${section.number}. ${section.title}`, { bold: true, size: SECTION_SIZE })] })]
  if (section.key === 's1') {
    out.push(...sectionOneDocx(section, data))
  } else if (section.blocks.some((b) => b.type === 'criteria')) {
    for (const block of section.blocks) out.push(...criteriaBlockDocx(block, data))
  } else if (section.key === 's11' || section.key === 's12') {
    out.push(...feedbackDocx(section.blocks[0], data))
  } else if (section.key === 's13') {
    out.push(...strengthsWeaknessesDocx(section.blocks[0], data))
  } else if (section.key === 's14') {
    out.push(...bulletParagraphs(fieldsMap(data).findings))
  } else if (section.key === 's15') {
    out.push(...bulletParagraphs(fieldsMap(data).recommendations))
  } else if (section.key === 's16') {
    out.push(...planDocx(section.blocks[0], data))
  }
  return out
}

function officerLines(text) {
  const raw = String(text ?? '').split(/\n|;/).map((s) => s.trim()).filter(Boolean)
  return raw.length ? raw : ['']
}

function signatureTable(data) {
  const names = officerLines(fieldsMap(data).officers)
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, [70, 30])
  const leftLines = [
    new Paragraph({ children: [run('Name (s) of the Visiting Officer(s) with designation', { bold: true })] }),
    ...names.map((n, i) => new Paragraph({ children: [run(`${i + 1}) ${n}`)] })),
  ]
  const rightLines = [
    new Paragraph({ alignment: AlignmentType.RIGHT, children: [run('Signatures', { bold: true })] }),
    ...names.map(() => new Paragraph({ alignment: AlignmentType.RIGHT, children: [run('……………………………')] })),
  ]
  return new Table({
    width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA }, columnWidths: widths, layout: TableLayoutType.FIXED,
    borders: { top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE }, left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE }, insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE } },
    rows: [new TableRow({
      children: [
        new TableCell({ width: { size: widths[0], type: WidthType.DXA }, children: leftLines }),
        new TableCell({ width: { size: widths[1], type: WidthType.DXA }, children: rightLines }),
      ],
    })],
  })
}

function pageFooter() {
  return new Footer({
    children: [
      new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH_TWIPS }],
        children: [
          run('SICIP Quality Assurance Visit Report', { size: SMALL_SIZE, color: NOTE_COLOR }),
          new TextRun({ children: [new Tab()], font: FONT, size: SMALL_SIZE, color: NOTE_COLOR }),
          run('Page ', { size: SMALL_SIZE, color: NOTE_COLOR }),
          new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: SMALL_SIZE, color: NOTE_COLOR }),
          run(' of ', { size: SMALL_SIZE, color: NOTE_COLOR }),
          new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FONT, size: SMALL_SIZE, color: NOTE_COLOR }),
        ],
      }),
    ],
  })
}

// pure fn: template + report data + meta -> Promise<Blob> (.docx).
export function buildQaReportDocx(template, data, _meta) {
  const normalizedData = withNormalizedData(template, data)
  const children = [
    new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(template.annex || 'Annex-3', { bold: true })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 20 }, children: [run(template.program, { bold: true, size: PROGRAM_SIZE })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, children: [run(template.title, { bold: true, size: TITLE_SIZE })] }),
    ...headerParagraphs(normalizedData),
  ]
  for (const section of template.sections || []) children.push(...sectionDocx(section, normalizedData))
  children.push(signatureTable(normalizedData))

  const doc = new Document({
    styles: { default: { document: { run: { font: FONT, size: BODY_SIZE } } } },
    sections: [{
      properties: {
        page: {
          size: { orientation: PageOrientation.PORTRAIT, width: PAGE_WIDTH_TWIPS, height: PAGE_HEIGHT_TWIPS },
          margin: { top: MARGIN_TOP_TWIPS, right: MARGIN_RIGHT_TWIPS, bottom: MARGIN_BOTTOM_TWIPS, left: MARGIN_LEFT_TWIPS },
        },
      },
      footers: { default: pageFooter() },
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
