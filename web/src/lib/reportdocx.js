// filled visit-report as a downloadable .docx, laid out to match the user's Word form
// ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-report-template.docx --
// fonts/sizes/shading/margins read straight out of that file's word/document.xml (unzip it to
// check: Arial throughout, sizes are OOXML half-points so they plug into docx's `size` option
// unconverted). Uses the `docx` npm package (context7 /dolanmiu/docx: Document/Packer.toBlob
// for the browser export path, Table/TableCell/TableRow for grids, Paragraph.border for the
// findings/instructions boxes, Footer/PageNumber for "Page x of y").
//
// CHANGE SET 3 (2026-09-25) "ONE REPORT LAYOUT FOR ALL OUTPUTS": reporthtml.js is the reference
// layout, this file copies it table-for-table. Column widths come from ./reportlayout.js so the
// two web renderers share one width table (that's also the fix for the CHANGE SET 2 bug report:
// Item column ~10% wide with huge tick columns, a 50%-wide flags tick column, and every blank
// remarks cell printing "Not answered" -- none of that happens now: "Not answered" is gone
// everywhere, blank = an empty box/line/tick, and every width below comes from reportlayout.js).
// driven purely by the template's sections/blocks -- never hardcode a question here.
import {
  AlignmentType, BorderStyle, Document, Packer, Paragraph,
  ShadingType, Table, TableCell, TableLayoutType, TableRow, TabStopType, TextRun, WidthType,
} from 'docx'
import {
  CELL_MARGINS_TWIPS, CHECKLIST_COLUMNS, CONTENT_WIDTH_TWIPS, FLAGS_COLUMNS, INTERVIEW_NOTE_COLUMNS, INTERVIEW_TICK_COLUMNS,
  TICK_CHECKED, TICK_UNCHECKED, TONE_COLOR, answeredQuestionFields, cardsColumnLayout, DENSE_CELL_MARGINS_TWIPS, DENSE_HEADER_HALF_POINTS, footerTitle, isCentredField,
  cellOrDash, isStandardAnswerChoice, weightedWidths,
  displayTime,
} from './reportlayout.js'
import { A4_PAGE, NUMBERING, numberedParagraphs, pageFooter } from './docxparts.js'
import * as reportTemplateModule from './reporttemplate.js'
import { compareMismatch, sectionHasContent } from './reporttemplate.js'
import { printedRemarkLines } from './sectionremarks.js'
import { findingLines } from './findingsbox.js'
import { signoffDocx } from './signoffdocx.js'

// -- fonts/colours/sizes, read from the reference docx (sizes are half-points, i.e. the same
// numbers as the file's w:sz val -- docx's TextRun `size` option takes the same unit) --
const FONT = 'Arial'
const BADGE_BG = '111111'
const HEADER_FILL = 'E6E6E6'
const BORDER_COLOR = '666666'
const GRAY = '888888'
const NOTE_COLOR = '333333'
const OPTIONAL_COLOR = '8a4600' // "partial" tone -- reused as the Optional-tag colour

// layout v3 (2026-10-01): body 10 pt, tables 9 pt, section heading 11 pt, sub-heading 10 pt,
// title 15 pt, program line 9 pt, footer 8 pt
const PROGRAM_SIZE = 18 // 9pt
const TITLE_SIZE = 30 // 15pt
const BADGE_SIZE = 20 // 10pt
const SECTION_TITLE_SIZE = 22 // 11pt
const NOTE_SIZE = 18 // 9pt
const SUBHEAD_SIZE = 20 // 10pt (block heading, e.g. "Persons met")
const TABLE_SIZE = 18 // 9pt, every table cell incl. headers
const BODY_SIZE = 20 // 10pt
const SMALL_SIZE = 16 // 8pt (per-course line, tags)
const FOOTER_SIZE = 16 // 8pt

function blank(v) {
  return v == null || String(v).trim() === ''
}

// W1's reporttemplate.js is gaining a normalize export in parallel (CHANGE SET 3: syncLinks +
// per-course splitting, replaces syncLinks as the one step run before rendering). Namespace
// import so this file loads fine whichever has landed; falls back to syncLinks (CHANGE SET 2),
// then to the data as given if neither exists yet. Clone first: buildReportDocx is documented
// as a pure fn and must not mutate the caller's data even if these mutate their argument.
export function withNormalizedData(template, data) {
  const normalize = reportTemplateModule.normalize
  const syncLinks = reportTemplateModule.syncLinks
  const step = typeof normalize === 'function' ? normalize : (typeof syncLinks === 'function' ? syncLinks : null)
  if (!step) return data
  const cloned = JSON.parse(JSON.stringify(data))
  return step(template, cloned) || cloned
}

function answerMapOf(template) {
  const map = {}
  for (const a of template.answers || []) map[a.id] = a
  return map
}

// one table's worth of borders (outer edge + inside grid lines), thin gray on all sides --
// matches the reference docx's checklist/grid tables (w:sz 4 = 0.5pt, colour 666666).
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

// full-width table with an EXPLICIT column grid, layout forced FIXED -- without this, docx
// writes a placeholder tblGrid (100 twips per column) and Word/LibreOffice auto-fits columns to
// content instead of respecting the per-cell widths below, which is what caused the CHANGE SET 2
// bug report (Item column ~10% wide, tick columns huge): the per-cell widths were being computed
// correctly but silently ignored at render time.
export function fixedTable(widths, rows, margins = CELL_MARGINS_TWIPS) {
  return new Table({
    width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA },
    columnWidths: widths,
    layout: TableLayoutType.FIXED,
    borders: TABLE_BORDERS,
    margins,
    rows,
  })
}

// header rows repeat on every page the table crosses (item 13); body rows never split. Two-level
// headers must pass BOTH rows through headerTableRow.
export function headerTableRow(cells) {
  return new TableRow({ tableHeader: true, cantSplit: true, children: cells })
}

export function bodyTableRow(cells) {
  return new TableRow({ cantSplit: true, children: cells })
}

export function run(text, opts = {}) {
  return new TextRun({ text, font: FONT, size: BODY_SIZE, ...opts })
}

// table text is a size smaller than body text
export function cellRun(text, opts = {}) {
  return run(text, { size: TABLE_SIZE, ...opts })
}

// one table-cell paragraph, centred for counts / batch no. / short answers
export function cellParagraph(children, centred = false) {
  return new Paragraph({ alignment: centred ? AlignmentType.CENTER : AlignmentType.LEFT, children })
}

// text (possibly multi-line, from a longtext field) -> a single Paragraph with explicit line
// breaks (w:br, attached to each line's own run) between lines. Blank -> one empty run, NEVER
// "Not answered" (CHANGE SET 3: that placeholder is gone from every output, including remarks --
// the bug the user reported after CHANGE SET 2).
function multilineParagraph(value, opts = {}, paraOpts = {}) {
  if (blank(value)) return new Paragraph({ ...paraOpts, children: [run('', opts)] })
  const lines = String(value).split('\n')
  const children = lines.map((line, i) => run(line, { ...opts, break: i < lines.length - 1 ? 1 : undefined }))
  return new Paragraph({ ...paraOpts, children })
}

// a table cell's (possibly multi-line) text; blank prints "-" (item 14)
export function cellMultiline(value, opts = {}, paraOpts = {}) {
  return multilineParagraph(cellOrDash(value), opts, paraOpts)
}

// choice fields -> bold text in the option's tone colour (blank -> empty paragraph, never "Not
// answered"); every other kind -> plain (possibly multi-line) text. Used inside card table
// cells, where a tick-box row per option (see inlineChoiceParagraph) wouldn't fit.
export function fieldValueParagraph(field, rawValue) {
  const alignment = isCentredField(field) ? AlignmentType.CENTER : AlignmentType.LEFT
  if (blank(rawValue)) return new Paragraph({ alignment, children: [run(cellOrDash(rawValue), { size: TABLE_SIZE })] })
  if (field.kind === 'choice' || field.kind === 'time') return new Paragraph({ alignment, children: fieldValueRuns(field, rawValue, TABLE_SIZE) })
  return multilineParagraph(rawValue, { size: TABLE_SIZE }, { alignment })
}

// one line of a value as runs (choice = its label, bold in its tone colour) -- for callers
// that put the value inside a sentence of their own (narrativedocx.js)
export function fieldValueRuns(field, rawValue, size = BODY_SIZE) {
  if (blank(rawValue)) return [run('', { size })]
  if (field.kind === 'time') return [run(displayTime(rawValue), { size })]
  if (field.kind !== 'choice') return [run(String(rawValue), { size })]
  const opt = (field.options || []).find((o) => o.id === rawValue)
  return [run(opt ? opt.label : String(rawValue), { size, bold: true, color: opt ? TONE_COLOR[opt.tone] : undefined })]
}

export function headerCellDxa(text, widthTwips, size = TABLE_SIZE) {
  return new TableCell({
    width: { size: widthTwips, type: WidthType.DXA },
    borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [cellRun(text, { bold: true, size })] })],
  })
}

// a cards table whose columns never break a word (reportlayout cardsColumnLayout): widths in
// twips, header text size and cell margins (8 pt header + narrow padding when dense)
export function cardsTableGeometry(fields, cards) {
  const { percents, dense } = cardsColumnLayout(fields, cards)
  return {
    widths: weightedWidths(CONTENT_WIDTH_TWIPS, percents),
    headerSize: dense ? DENSE_HEADER_HALF_POINTS : TABLE_SIZE,
    margins: dense ? DENSE_CELL_MARGINS_TWIPS : CELL_MARGINS_TWIPS,
  }
}

export function bodyCellDxa(widthTwips, paragraphs) {
  return new TableCell({ width: { size: widthTwips, type: WidthType.DXA }, borders: CELL_BORDERS, children: paragraphs })
}

function tickParagraph(checked, tone) {
  const color = checked ? TONE_COLOR[tone] : undefined
  return new Paragraph({ alignment: AlignmentType.CENTER, children: [cellRun(checked ? TICK_CHECKED : TICK_UNCHECKED, { bold: checked, color })] })
}

// block/subsection heading, e.g. "Persons met", "Attendance register" -- bold body-weight text,
// smaller and lighter than the black-badge section heading.
export function subheadParagraph(text) {
  return new Paragraph({ keepNext: true, spacing: { before: 160, after: 60 }, children: [run(text, { bold: true, size: SUBHEAD_SIZE })] })
}

function noteParagraph(text) {
  return new Paragraph({ spacing: { after: 80 }, children: [run(text, { italics: true, size: NOTE_SIZE, color: NOTE_COLOR })] })
}

// ---- fields block: choice/select fields render as an inline tick-box option row (matches the
// reference docx's "Type  ☐ Full QA  ☐ Monitoring  ☐ Surprise" pattern); every other kind
// renders as "Label: value"; longtext fields get a bordered box instead (findings/instructions/
// address/... -- anything long enough to need multiple lines gets room to show them). Blank
// values are always an empty line/box/tick -- never "Not answered". ----

function inlineChoiceParagraph(field, rawValue) {
  const options = field.kind === 'select' ? field.options.map((o) => ({ id: o, label: o })) : field.options
  const children = [run(labelWithColon(field.label), { bold: true })]
  for (const opt of options) {
    const checked = rawValue === opt.id
    const color = checked && opt.tone ? TONE_COLOR[opt.tone] : undefined
    children.push(run('   '))
    children.push(run(checked ? TICK_CHECKED : TICK_UNCHECKED, { bold: checked, color }))
    children.push(run(` ${opt.label}`, { bold: checked, color }))
  }
  return new Paragraph({ spacing: { before: 60, after: 60 }, children })
}

// a label that is already a question ("Could the trainees answer it?") gets no trailing colon
function labelWithColon(label) {
  return String(label).trim().endsWith('?') ? String(label) : `${label}:`
}

function plainFieldParagraph(field, rawValue) {
  const children = [run(`${labelWithColon(field.label)} `, { bold: true })]
  const shown = field.kind === 'time' ? displayTime(rawValue) : rawValue
  children.push(run(blank(rawValue) ? '' : String(shown)))
  return new Paragraph({ spacing: { before: 60, after: 60 }, children })
}

function longtextBoxParagraphs(field, rawValue) {
  return [
    new Paragraph({ spacing: { before: 60, after: 20 }, children: [run(field.label, { bold: true })] }),
    multilineParagraph(rawValue, {}, {
      spacing: { after: 120 },
      border: {
        top: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR, space: 4 },
        bottom: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR, space: 4 },
        left: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR, space: 4 },
        right: { style: BorderStyle.SINGLE, size: 4, color: BORDER_COLOR, space: 4 },
      },
    }),
  ]
}

// shared by top-level fields blocks and the interview per-course "other fields" -- one field ->
// one or more Paragraphs, dispatched by kind.
// surprise v2 "Major findings" / "Recommendations": heading + a real Word numbered list
export function numberedListParagraphs(heading, lines) {
  const out = [subheadParagraph(heading)]
  const items = lines.map((l) => String(l ?? '').trim()).filter(Boolean)
  if (items.length === 0) return [...out, new Paragraph({ children: [run('None.', { italics: true, color: GRAY })] })]
  return [...out, ...numberedParagraphs(items, (text) => [run(text)])]
}

// section remarks: one "•" paragraph per line straight after the table, no "Remarks" label
// (issues' bullet in the "no" colour)
export function bulletParagraphs(lines) {
  return lines.filter((l) => !blank(l.text)).map((line, i) => new Paragraph({
    spacing: { before: i === 0 ? 80 : 0, after: 20 },
    indent: { left: 280, hanging: 280 },
    children: [run('•\t', { color: line.neg ? TONE_COLOR.no : undefined }), run(line.text)],
    tabStops: [{ type: TabStopType.LEFT, position: 280 }],
  }))
}

function fieldParagraphs(field, rawValue) {
  if (field.draftFrom === 'findings') return numberedListParagraphs(field.label, String(rawValue ?? '').split('\n'))
  if (field.kind === 'choice' || field.kind === 'select') return [inlineChoiceParagraph(field, rawValue)]
  if (field.kind === 'longtext') return longtextBoxParagraphs(field, rawValue)
  return [plainFieldParagraph(field, rawValue)]
}

function fieldsBlockDocx(block, data) {
  const values = data.fields || {}
  return block.fields.flatMap((field) => fieldParagraphs(field, values[field.key]))
}

// courses with a non-blank name, in section-A order -- same list normalize()/reference.py's
// course_ids() splits per-course items by, and the source for the linked interview/attendance
// cards and the per-course summary line.
function courseList(data) {
  return ((data.cards && data.cards.courses) || []).filter((c) => !blank(c.course))
}

// ---- checklist block: # | Item | Yes | No | Part | N/A | Remarks -- widths from
// reportlayout.js's CHECKLIST_COLUMNS, scaled onto the page content width. ----

function checklistColumnWidths() {
  return weightedWidths(CONTENT_WIDTH_TWIPS, CHECKLIST_COLUMNS.map((c) => c.weight))
}

// small line under a per-course item, e.g. "Welding (SMAW) 07: Yes · Electrical Installation
// 03: No" -- only courses with a non-blank per-course answer are listed; omitted entirely when
// nothing has been answered yet for any course.
function perCourseLineText(item, entry, courses, answerMap) {
  if (!item.perCourse || courses.length < 2) return null
  const per = entry.courses || {}
  const parts = courses
    .map((c) => {
      const answer = per[c._id]
      if (blank(answer)) return null
      const label = answerMap[answer] ? answerMap[answer].label : answer
      const name = [c.course, c.batch].filter((v) => !blank(v)).join(' ')
      return `${name}: ${label}`
    })
    .filter(Boolean)
  return parts.length ? parts.join(' · ') : null
}

function checklistBlockDocx(block, data, template, answerMap) {
  const checks = data.checks || {}
  const courses = courseList(data)
  const widths = checklistColumnWidths()
  const out = []
  if (block.heading) out.push(subheadParagraph(block.heading))
  const answerIds = template.answers.map((a) => a.id)
  const headerRow = headerTableRow(CHECKLIST_COLUMNS.map((c, i) => headerCellDxa(c.label, widths[i])))
  const rows = block.items.map((item, i) => {
    const entry = checks[item.id] || {}
    const tickCells = answerIds.map((id, ci) => bodyCellDxa(widths[2 + ci], [tickParagraph(entry.answer === id, answerMap[id]?.tone)]))
    const itemChildren = [cellRun(item.text)]
    const itemParas = [new Paragraph({ children: itemChildren })]
    const perCourseText = perCourseLineText(item, entry, courses, answerMap)
    if (perCourseText) itemParas.push(new Paragraph({ children: [run(perCourseText, { size: SMALL_SIZE, color: NOTE_COLOR })] }))
    return bodyTableRow([
      bodyCellDxa(widths[0], [cellParagraph([cellRun(String(i + 1))], true)]),
      bodyCellDxa(widths[1], itemParas),
      ...tickCells,
      bodyCellDxa(widths[6], [cellMultiline(entry.remarks, { size: TABLE_SIZE })]),
    ])
  })
  out.push(fixedTable(widths, [headerRow, ...rows]))
  return out
}

// ---- cards block: one table per block, one ROW per card -- columns = block.fields in template
// order (e.g. attendance: Course | Batch | Enrolled T | F | Headcount T | F | Register | TMS |
// Trainers present | Remarks). ----

// same mismatch rule as reporthtml.js / the progress spec: >=2 compare fields present, parse
// to ints, not all equal.
// print:false = app-only warning (surprise v2 headcount gap)
function cardMismatch(compare, entry) {
  return Boolean(compare) && compare.print !== false && compareMismatch(compare, entry)
}

function cardsBlockDocx(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const out = []
  if (block.heading) out.push(subheadParagraph(block.heading))
  if (block.note) out.push(noteParagraph(block.note))
  if (entries.length === 0) {
    out.push(new Paragraph({ children: [run('No entries.', { italics: true, color: GRAY })] }))
    return out
  }
  const { widths, headerSize, margins } = cardsTableGeometry(block.fields, entries)
  const headerRow = headerTableRow(block.fields.map((f, i) => headerCellDxa(f.label, widths[i], headerSize)))
  const mismatchedRows = []
  const rows = entries.map((entry, i) => {
    if (cardMismatch(block.compare, entry)) mismatchedRows.push(i + 1)
    return bodyTableRow(block.fields.map((f, i2) => bodyCellDxa(widths[i2], [fieldValueParagraph(f, entry[f.key])])))
  })
  out.push(fixedTable(widths, [headerRow, ...rows], margins))
  if (mismatchedRows.length > 0 && block.compare) {
    const which = mismatchedRows.length === entries.length ? '' : ` (row${mismatchedRows.length > 1 ? 's' : ''} ${mismatchedRows.join(', ')})`
    out.push(new Paragraph({ children: [run(`${block.compare.message}${which}`, { bold: true, color: TONE_COLOR.no })] }))
  }
  return out
}

// ---- linked cards block with display:"tabs" (section I "interviews"): tabs are an editor-only
// concept -- exports print one small table per course instead. Fields whose choice options are
// exactly the template's answer ids (e.g. q1-q7) render as a checklist-style tick sub-table;
// every other field (trainees_interviewed, tech_topic, tech_result, feedback) renders through
// the normal field renderer. Linked fields (course/batch) show in the caption, not twice. ----

function interviewTickWidths(columns) {
  return weightedWidths(CONTENT_WIDTH_TWIPS, columns.map((c) => c.weight))
}

function tabsCardsBlockDocx(block, data, template, answerMap) {
  const entries = (data.cards && data.cards[block.key]) || []
  if (entries.length === 0) return [new Paragraph({ children: [run('Add courses in section A.', { italics: true, color: GRAY })] })]
  // a question nobody answered for any course is left out
  const questionFields = answeredQuestionFields(block, entries)
  const tickFields = questionFields.filter((f) => isStandardAnswerChoice(f, template))
  const noteFields = block.fields.filter((f) => f.noteFor)
  const otherFields = questionFields.filter((f) => !isStandardAnswerChoice(f, template))
  const answerIds = template.answers.map((a) => a.id)
  const columns = noteFields.length ? INTERVIEW_NOTE_COLUMNS : INTERVIEW_TICK_COLUMNS
  const widths = interviewTickWidths(columns)
  const out = []
  for (const entry of entries) {
    const linkedFields = (block.linkFrom && block.linkFrom.fields) || []
    const extra = linkedFields.filter((k) => k !== block.titleField).map((k) => entry[k]).filter((v) => !blank(v)).join(' ')
    const caption = `${entry[block.titleField] || ''}${extra ? ` · Batch ${extra}` : ''}`
    out.push(subheadParagraph(caption))
    if (tickFields.length > 0) {
      const headerRow = headerTableRow(columns.map((c, i) => headerCellDxa(c.label, widths[i])))
      const rows = tickFields.map((f) => {
        const note = noteFields.find((n) => n.noteFor === f.key)
        const noteCell = noteFields.length ? [bodyCellDxa(widths[1 + answerIds.length], [cellMultiline(note ? entry[note.key] : '', { size: TABLE_SIZE })])] : []
        return bodyTableRow([
          bodyCellDxa(widths[0], [cellParagraph([cellRun(f.label)])]),
          ...answerIds.map((id, ci) => bodyCellDxa(widths[1 + ci], [tickParagraph(entry[f.key] === id, answerMap[id]?.tone)])),
          ...noteCell,
        ])
      })
      out.push(fixedTable(widths, [headerRow, ...rows]))
    }
    out.push(...otherFields.flatMap((f) => fieldParagraphs(f, entry[f.key])))
  }
  return out
}

// ---- flags: fixed items always print (ticked or not) as a 2-column tick/text list; a
// countsAsFlags cards block (free-text flags, e.g. L "other_flags") merges into the SAME list --
// a non-blank card is inherently "ticked" (its presence is the flag), so it's listed alongside
// the fixed items rather than as its own cards table. ----

function flagsColumnWidths() {
  return weightedWidths(CONTENT_WIDTH_TWIPS, FLAGS_COLUMNS.map((c) => c.weight))
}

function flagRow(checked, text, widths) {
  const color = checked ? TONE_COLOR.no : undefined
  return bodyTableRow([
    bodyCellDxa(widths[0], [tickParagraph(checked, 'no')]),
    bodyCellDxa(widths[1], [cellParagraph([cellRun(text, { bold: checked, color })])]),
  ])
}

function combinedFlagsDocx(section, data) {
  const flagsBlock = section.blocks.find((b) => b.type === 'flags')
  const ticked = new Set(data.flags || [])
  const widths = flagsColumnWidths()
  const rows = []
  if (flagsBlock) for (const item of flagsBlock.items) rows.push(flagRow(ticked.has(item.id), item.text, widths))
  for (const block of section.blocks) {
    if (block.type !== 'cards' || !block.countsAsFlags) continue
    const entries = (data.cards && data.cards[block.key]) || []
    for (const entry of entries) {
      const text = entry[block.titleField]
      if (!blank(text)) rows.push(flagRow(true, String(text).trim(), widths))
    }
  }
  if (rows.length === 0) return [new Paragraph({ children: [run('None ticked.', { italics: true, color: GRAY })] })]
  return [fixedTable(widths, rows)]
}

function blockDocx(block, section, data, template, answerMap) {
  if (block.type === 'fields') return fieldsBlockDocx(block, data)
  if (block.type === 'checklist') return checklistBlockDocx(block, data, template, answerMap)
  if (block.type === 'cards') return block.display === 'tabs' ? tabsCardsBlockDocx(block, data, template, answerMap) : cardsBlockDocx(block, data)
  if (block.type === 'remarks') return bulletParagraphs(printedRemarkLines(template, section, block, data))
  if (block.type === 'findings') return numberedListParagraphs(block.heading ?? 'Major findings', findingLines(block, data))
  return [] // unknown block type (flags/countsAsFlags cards handled in sectionDocx) -- ignore
}

// black letter-badge heading, e.g. " C  Attendance in each course" -- matches the reference
// docx's badge run (bold white-on-black) + heading run (bold, two leading spaces).
export function sectionHeadingParagraph(section) {
  const children = [
    run(` ${section.letter} `, { bold: true, color: 'FFFFFF', size: BADGE_SIZE, shading: { type: ShadingType.CLEAR, fill: BADGE_BG } }),
    run(`  ${section.title}`, { bold: true, size: SECTION_TITLE_SIZE }),
  ]
  if (section.optional) children.push(run('   (Optional)', { bold: true, italics: true, size: NOTE_SIZE, color: OPTIONAL_COLOR }))
  return new Paragraph({ keepNext: true, spacing: { before: 240, after: 80 }, children })
}

function sectionDocx(section, data, template, answerMap) {
  const out = [sectionHeadingParagraph(section)]
  if (section.note) out.push(noteParagraph(section.note))
  const isFlagsRelevant = (b) => b.type === 'flags' || (b.type === 'cards' && b.countsAsFlags)
  let flagsRendered = false
  for (const block of section.blocks || []) {
    if (isFlagsRelevant(block)) {
      if (flagsRendered) continue
      flagsRendered = true
      out.push(...combinedFlagsDocx(section, data))
      continue
    }
    out.push(...blockDocx(block, section, data, template, answerMap))
  }
  return out
}

// program line + title over a rule; no subtitle and no officer/status line (layout v3)
export function headerParagraphs(template) {
  return [
    new Paragraph({ children: [run(template.program, { size: PROGRAM_SIZE })] }),
    new Paragraph({
      border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: BADGE_BG, space: 4 } },
      spacing: { after: 160 },
      children: [run(template.title, { bold: true, size: TITLE_SIZE })],
    }),
  ]
}

// pure fn: template + report data + {officerName, submittedAt?, status} -> Promise<Blob> (.docx).
export function buildReportDocx(template, data, meta) {
  const normalizedData = withNormalizedData(template, data)
  const answerMap = answerMapOf(template)
  const children = headerParagraphs(template)
  for (const section of template.sections || []) {
    // an optional section (K) nobody touched is left out of the report entirely
    if (section.optional && !sectionHasContent(section, normalizedData)) continue
    children.push(...sectionDocx(section, normalizedData, template, answerMap))
  }
  children.push(...signoffDocx(normalizedData))
  return packDocx(children, footerTitle(template))
}

// A4 page, margins, numbering and footer shared by the form and narrative (narrativedocx.js)
export function packDocx(children, title) {
  const doc = new Document({
    styles: { default: { document: { run: { font: FONT, size: BODY_SIZE } } } },
    numbering: NUMBERING,
    sections: [{
      properties: { page: A4_PAGE },
      footers: { default: pageFooter(title, { font: FONT, size: FOOTER_SIZE, color: NOTE_COLOR }) },
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
export function reportFilename(template, data, meta) {
  const institute = sanitizeFilenamePart(prefillValue(template, data, 'institute'))
  const visitDate = prefillValue(template, data, 'visit_date')
  const date = !blank(visitDate) ? visitDate : (!blank(meta.submittedAt) ? String(meta.submittedAt).slice(0, 10) : todayIso())
  return `Surprise-visit-${institute}-${date}.docx`
}

// build the docx and trigger a browser download -- object-URL + throwaway <a>, no extra
// dependency (docx's own docs suggest file-saver, but that's one more package for one line).
export async function downloadReportDocx(template, data, meta) {
  return saveDocx(await buildReportDocx(template, data, meta), reportFilename(template, data, meta))
}

export function saveDocx(blob, filename) {
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
