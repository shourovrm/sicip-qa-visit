// filled visit-report as a downloadable .docx -- same section/block content as reporthtml.js
// (only given answers, bold coloured text for choice answers) but laid out as Word tables
// instead of print HTML. Uses the `docx` npm package (see context7 /dolanmiu/docx docs --
// Document/Packer.toBlob for the browser export path, Table/TableCell/TableRow for grids).
// driven purely by the template's sections/blocks -- never hardcode a question here.
import {
  AlignmentType, BorderStyle, Document, HeadingLevel, Packer, PageOrientation, Paragraph,
  ShadingType, Table, TableCell, TableRow, TextRun, WidthType, convertMillimetersToTwip,
} from 'docx'

// answer/choice colours -- must match reporthtml.js and android ui/theme/Color.kt (hex, no '#').
const TONE_COLOR = { yes: '1c6b38', no: 'b3261e', partial: '8a4600', na: '4c4f66' }
const GRAY = '888888'
const HEADER_FILL = 'E6E6E6'
const BORDER_COLOR = '999999'

function blank(v) {
  return v == null || String(v).trim() === ''
}

function answerMapOf(template) {
  const map = {}
  for (const a of template.answers || []) map[a.id] = a
  return map
}

// one table's worth of borders (outer edge + inside grid lines), same thin gray on all sides.
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

// text (possibly multi-line, from a longtext field) -> one Paragraph per line; blank -> one
// empty paragraph so the table cell still renders with correct row height.
function textParagraphs(value, runOptions = {}) {
  const lines = blank(value) ? [''] : String(value).split('\n')
  return lines.map((line) => new Paragraph({ children: [new TextRun({ text: line, ...runOptions })] }))
}

function notAnsweredParagraph() {
  return new Paragraph({ children: [new TextRun({ text: 'Not answered', italics: true, color: GRAY })] })
}

// choice fields render as bold text in the option's tone colour (or "Not answered" if blank);
// every other kind renders as plain (possibly multi-line) text.
function fieldValueParagraphs(field, rawValue) {
  if (field.kind === 'choice') {
    if (blank(rawValue)) return [notAnsweredParagraph()]
    const opt = (field.options || []).find((o) => o.id === rawValue)
    const color = opt ? (TONE_COLOR[opt.tone] || '111111') : '111111'
    const label = opt ? opt.label : String(rawValue)
    return [new Paragraph({ children: [new TextRun({ text: label, bold: true, color })] })]
  }
  return textParagraphs(rawValue)
}

function labelCell(text, widthPercent) {
  return new TableCell({
    width: { size: widthPercent, type: WidthType.PERCENTAGE },
    borders: CELL_BORDERS,
    children: [new Paragraph({ children: [new TextRun({ text, bold: true })] })],
  })
}

function valueCell(paragraphs, widthPercent) {
  return new TableCell({
    width: { size: widthPercent, type: WidthType.PERCENTAGE },
    borders: CELL_BORDERS,
    children: paragraphs,
  })
}

function headerCell(text, widthPercent) {
  return new TableCell({
    width: { size: widthPercent, type: WidthType.PERCENTAGE },
    borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text, bold: true, size: 16 })] })],
  })
}

function fieldsBlockDocx(block, data) {
  const values = data.fields || {}
  const rows = block.fields.map((f) => new TableRow({
    children: [labelCell(f.label, 30), valueCell(fieldValueParagraphs(f, values[f.key]), 70)],
  }))
  return [new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, borders: TABLE_BORDERS, rows })]
}

function checklistBlockDocx(block, data, answerMap) {
  const checks = data.checks || {}
  const out = []
  if (block.heading) out.push(new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(block.heading)] }))
  const headerRow = new TableRow({
    children: [headerCell('#', 6), headerCell('Item', 48), headerCell('Answer', 16), headerCell('Remarks', 30)],
  })
  const rows = block.items.map((item, i) => {
    const entry = checks[item.id] || {}
    let answerParas
    if (blank(entry.answer)) {
      answerParas = [notAnsweredParagraph()]
    } else {
      const ans = answerMap[entry.answer]
      answerParas = ans
        ? [new Paragraph({ children: [new TextRun({ text: ans.label, bold: true, color: TONE_COLOR[ans.tone] || '111111' })] })]
        : [new Paragraph({ children: [new TextRun(String(entry.answer))] })]
    }
    return new TableRow({
      children: [
        valueCell([new Paragraph(String(i + 1))], 6),
        valueCell([new Paragraph(item.text)], 48),
        valueCell(answerParas, 16),
        valueCell(textParagraphs(entry.remarks), 30),
      ],
    })
  })
  out.push(new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, borders: TABLE_BORDERS, rows: [headerRow, ...rows] }))
  return out
}

// same mismatch rule as reporthtml.js / the progress spec: >=2 compare fields present, parse
// to ints, not all equal.
function cardMismatch(compare, entry) {
  if (!compare) return false
  const values = compare.fields.map((k) => entry[k]).filter((v) => !blank(v)).map(Number).filter((n) => !Number.isNaN(n))
  return values.length >= 2 && !values.every((v) => v === values[0])
}

function cardsBlockDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  if (entries.length === 0) return [new Paragraph({ children: [new TextRun({ text: 'No entries.', italics: true, color: GRAY })] })]
  const out = []
  entries.forEach((entry, i) => {
    const titleValue = entry[block.titleField]
    const caption = blank(titleValue) ? `${block.itemLabel} ${i + 1}` : `${block.itemLabel} ${i + 1}: ${titleValue}`
    out.push(new Paragraph({ heading: HeadingLevel.HEADING_4, children: [new TextRun(caption)] }))
    const rows = block.fields.map((f) => new TableRow({
      children: [labelCell(f.label, 35), valueCell(fieldValueParagraphs(f, entry[f.key]), 65)],
    }))
    out.push(new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, borders: TABLE_BORDERS, rows }))
    if (cardMismatch(block.compare, entry)) {
      out.push(new Paragraph({ children: [new TextRun({ text: block.compare.message, bold: true, color: TONE_COLOR.no })] }))
    }
  })
  return out
}

function flagsBlockDocx(block, data) {
  const ticked = new Set(data.flags || [])
  const items = block.items.filter((i) => ticked.has(i.id))
  if (items.length === 0) return [new Paragraph({ children: [new TextRun({ text: 'None ticked.', italics: true, color: GRAY })] })]
  const rows = items.map((i) => new TableRow({
    children: [valueCell([new Paragraph({ children: [new TextRun({ text: i.text, bold: true, color: TONE_COLOR.no })] })], 100)],
  }))
  return [new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, borders: TABLE_BORDERS, rows })]
}

function blockDocx(block, data, answerMap) {
  if (block.type === 'fields') return fieldsBlockDocx(block, data)
  if (block.type === 'checklist') return checklistBlockDocx(block, data, answerMap)
  if (block.type === 'cards') return cardsBlockDocx(block, data)
  if (block.type === 'flags') return flagsBlockDocx(block, data)
  return [] // unknown block type -- ignore rather than crash on future template additions
}

function sectionDocx(section, data, answerMap) {
  const out = [new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(`${section.letter}. ${section.title}`)] })]
  if (section.note) out.push(new Paragraph({ children: [new TextRun({ text: section.note, italics: true, size: 16 })] }))
  for (const block of section.blocks || []) out.push(...blockDocx(block, data, answerMap))
  return out
}

function statusLabel(status) {
  return status === 'submitted' ? 'Submitted' : 'Draft'
}

function metaParagraph(meta) {
  let text = `Officer: ${meta.officerName || ''}   |   Status: ${statusLabel(meta.status)}`
  if (meta.status === 'submitted' && !blank(meta.submittedAt)) text += `   |   Submitted: ${meta.submittedAt}`
  return new Paragraph({ children: [new TextRun({ text, size: 16, color: '333333' })], spacing: { after: 200 } })
}

// pure fn: template + report data + {officerName, submittedAt?, status} -> Promise<Blob> (.docx).
export function buildReportDocx(template, data, meta) {
  const answerMap = answerMapOf(template)
  const children = [
    new Paragraph({ children: [new TextRun({ text: template.program, size: 16 })] }),
    new Paragraph({ heading: HeadingLevel.TITLE, children: [new TextRun(template.title)] }),
    new Paragraph({ children: [new TextRun({ text: template.subtitle, italics: true, size: 18 })], spacing: { after: 120 } }),
    metaParagraph(meta),
  ]
  for (const section of template.sections || []) children.push(...sectionDocx(section, data, answerMap))

  const doc = new Document({
    sections: [{
      properties: {
        page: {
          size: { orientation: PageOrientation.PORTRAIT, width: convertMillimetersToTwip(210), height: convertMillimetersToTwip(297) },
          margin: {
            top: convertMillimetersToTwip(10), right: convertMillimetersToTwip(11),
            bottom: convertMillimetersToTwip(12), left: convertMillimetersToTwip(11),
          },
        },
      },
      children,
    }],
  })
  return Packer.toBlob(doc)
}

// field whose prefill matches (e.g. "institute") -> its data.fields value, searched across all
// sections/blocks so the filename logic stays driven by the template, not a hardcoded field key.
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

// download filename: Surprise-visit-<institute>-<yyyy-mm-dd>.docx (sanitized institute name);
// falls back to the visit date field, then submittedAt, then today if no date is on record.
function reportFilename(template, data, meta) {
  const institute = sanitizeFilenamePart(prefillValue(template, data, 'institute'))
  const visitDate = prefillValue(template, data, 'visit_date')
  const date = !blank(visitDate) ? visitDate : (!blank(meta.submittedAt) ? String(meta.submittedAt).slice(0, 10) : todayIso())
  return `Surprise-visit-${institute}-${date}.docx`
}

// build the docx and trigger a browser download -- object-URL + throwaway <a>, no extra
// dependency (docx's own docs suggest file-saver, but that's one more package for one line).
export async function downloadReportDocx(template, data, meta) {
  const blob = await buildReportDocx(template, data, meta)
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
