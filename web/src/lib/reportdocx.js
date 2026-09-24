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
  AlignmentType, BorderStyle, Document, Footer, Packer, PageNumber, PageOrientation, Paragraph,
  ShadingType, Tab, Table, TableCell, TableLayoutType, TableRow, TabStopType, TextRun, WidthType,
} from 'docx'
import {
  CHECKLIST_COLUMNS, CONTENT_WIDTH_TWIPS, FLAGS_COLUMNS, INTERVIEW_TICK_COLUMNS,
  MARGIN_BOTTOM_TWIPS, MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS, MARGIN_TOP_TWIPS, PAGE_HEIGHT_TWIPS,
  PAGE_WIDTH_TWIPS, TICK_CHECKED, TICK_UNCHECKED, TONE_COLOR, isStandardAnswerChoice,
  weightedWidths,
} from './reportlayout.js'
import * as reportTemplateModule from './reporttemplate.js'

// -- fonts/colours/sizes, read from the reference docx (sizes are half-points, i.e. the same
// numbers as the file's w:sz val -- docx's TextRun `size` option takes the same unit) --
const FONT = 'Arial'
const BADGE_BG = '111111'
const HEADER_FILL = 'E6E6E6'
const BORDER_COLOR = '666666'
const GRAY = '888888'
const NOTE_COLOR = '333333'
const OPTIONAL_COLOR = '8a4600' // "partial" tone -- reused as the Optional-tag colour
const PER_COURSE_TAG_COLOR = '4c4f66' // "na" tone -- reused as the Per-course-tag colour

const PROGRAM_SIZE = 16 // 8pt
const TITLE_SIZE = 26 // 13pt
const SUBTITLE_SIZE = 15 // 7.5pt
const META_SIZE = 15
const BADGE_SIZE = 18 // 9pt
const SECTION_TITLE_SIZE = 19 // 9.5pt
const NOTE_SIZE = 15 // 7.5pt
const SUBHEAD_SIZE = 17 // 8.5pt (block heading, e.g. "Persons met")
const TABLE_HEADER_SIZE = 15 // 7.5pt
const BODY_SIZE = 17 // 8.5pt
const SMALL_SIZE = 13 // 6.5pt (per-course line, tags, footer)
const LEGEND_SIZE = 14 // 7pt

function blank(v) {
  return v == null || String(v).trim() === ''
}

// W1's reporttemplate.js is gaining a normalize export in parallel (CHANGE SET 3: syncLinks +
// per-course splitting, replaces syncLinks as the one step run before rendering). Namespace
// import so this file loads fine whichever has landed; falls back to syncLinks (CHANGE SET 2),
// then to the data as given if neither exists yet. Clone first: buildReportDocx is documented
// as a pure fn and must not mutate the caller's data even if these mutate their argument.
function withNormalizedData(template, data) {
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
function fixedTable(widths, rows) {
  return new Table({
    width: { size: CONTENT_WIDTH_TWIPS, type: WidthType.DXA },
    columnWidths: widths,
    layout: TableLayoutType.FIXED,
    borders: TABLE_BORDERS,
    rows,
  })
}

function run(text, opts = {}) {
  return new TextRun({ text, font: FONT, size: BODY_SIZE, ...opts })
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

// choice fields -> bold text in the option's tone colour (blank -> empty paragraph, never "Not
// answered"); every other kind -> plain (possibly multi-line) text. Used inside card table
// cells, where a tick-box row per option (see inlineChoiceParagraph) wouldn't fit.
function fieldValueParagraph(field, rawValue) {
  if (field.kind === 'choice') {
    if (blank(rawValue)) return new Paragraph({ children: [run('')] })
    const opt = (field.options || []).find((o) => o.id === rawValue)
    const color = opt ? TONE_COLOR[opt.tone] : undefined
    const label = opt ? opt.label : String(rawValue)
    return new Paragraph({ children: [run(label, { bold: true, color })] })
  }
  return multilineParagraph(rawValue)
}

function headerCellDxa(text, widthTwips) {
  return new TableCell({
    width: { size: widthTwips, type: WidthType.DXA },
    borders: CELL_BORDERS,
    shading: { type: ShadingType.CLEAR, fill: HEADER_FILL },
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(text, { bold: true, size: TABLE_HEADER_SIZE })] })],
  })
}

function bodyCellDxa(widthTwips, paragraphs) {
  return new TableCell({ width: { size: widthTwips, type: WidthType.DXA }, borders: CELL_BORDERS, children: paragraphs })
}

function tickParagraph(checked, tone) {
  const color = checked ? TONE_COLOR[tone] : undefined
  return new Paragraph({ alignment: AlignmentType.CENTER, children: [run(checked ? TICK_CHECKED : TICK_UNCHECKED, { bold: checked, color })] })
}

// block/subsection heading, e.g. "Persons met", "Attendance register" -- bold body-weight text,
// smaller and lighter than the black-badge section heading.
function subheadParagraph(text) {
  return new Paragraph({ spacing: { before: 100, after: 40 }, children: [run(text, { bold: true, size: SUBHEAD_SIZE })] })
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
  const children = [run(`${field.label}:`, { bold: true })]
  for (const opt of options) {
    const checked = rawValue === opt.id
    const color = checked && opt.tone ? TONE_COLOR[opt.tone] : undefined
    children.push(run('   '))
    children.push(run(checked ? TICK_CHECKED : TICK_UNCHECKED, { bold: checked, color }))
    children.push(run(` ${opt.label}`, { bold: checked, color }))
  }
  return new Paragraph({ spacing: { before: 60, after: 60 }, children })
}

function plainFieldParagraph(field, rawValue) {
  const children = [run(`${field.label}: `, { bold: true })]
  children.push(run(blank(rawValue) ? '' : String(rawValue)))
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
function fieldParagraphs(field, rawValue) {
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
  const headerRow = new TableRow({ children: CHECKLIST_COLUMNS.map((c, i) => headerCellDxa(c.label, widths[i])) })
  const rows = block.items.map((item, i) => {
    const entry = checks[item.id] || {}
    const tickCells = answerIds.map((id, ci) => bodyCellDxa(widths[2 + ci], [tickParagraph(entry.answer === id, answerMap[id]?.tone)]))
    const itemChildren = [run(item.text)]
    if (item.perCourse && courses.length >= 2) itemChildren.push(run('  Per course', { bold: true, size: SMALL_SIZE, color: PER_COURSE_TAG_COLOR }))
    const itemParas = [new Paragraph({ children: itemChildren })]
    const perCourseText = perCourseLineText(item, entry, courses, answerMap)
    if (perCourseText) itemParas.push(new Paragraph({ children: [run(perCourseText, { size: SMALL_SIZE, color: NOTE_COLOR })] }))
    return new TableRow({
      children: [
        bodyCellDxa(widths[0], [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(String(i + 1))] })]),
        bodyCellDxa(widths[1], itemParas),
        ...tickCells,
        bodyCellDxa(widths[6], [multilineParagraph(entry.remarks)]),
      ],
    })
  })
  out.push(fixedTable(widths, [headerRow, ...rows]))
  return out
}

// ---- cards block: one table per block, one ROW per card -- columns = block.fields in template
// order (e.g. attendance: Course | Batch | Enrolled T | F | Headcount T | F | Register | TMS |
// Trainers present | Remarks). ----

// same mismatch rule as reporthtml.js / the progress spec: >=2 compare fields present, parse
// to ints, not all equal.
function cardMismatch(compare, entry) {
  if (!compare) return false
  const values = compare.fields.map((k) => entry[k]).filter((v) => !blank(v)).map(Number).filter((n) => !Number.isNaN(n))
  return values.length >= 2 && !values.every((v) => v === values[0])
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
  const widths = weightedWidths(CONTENT_WIDTH_TWIPS, block.fields.map(() => 1))
  const headerRow = new TableRow({ children: block.fields.map((f, i) => headerCellDxa(f.label, widths[i])) })
  const mismatchedRows = []
  const rows = entries.map((entry, i) => {
    if (cardMismatch(block.compare, entry)) mismatchedRows.push(i + 1)
    return new TableRow({ children: block.fields.map((f, i2) => bodyCellDxa(widths[i2], [fieldValueParagraph(f, entry[f.key])])) })
  })
  out.push(fixedTable(widths, [headerRow, ...rows]))
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

function interviewTickWidths() {
  return weightedWidths(CONTENT_WIDTH_TWIPS, INTERVIEW_TICK_COLUMNS.map((c) => c.weight))
}

function tabsCardsBlockDocx(block, data, template, answerMap) {
  const entries = (data.cards && data.cards[block.key]) || []
  if (entries.length === 0) return [new Paragraph({ children: [run('Add courses in section A.', { italics: true, color: GRAY })] })]
  const linkedKeys = new Set((block.linkFrom && block.linkFrom.fields) || [])
  const tickFields = block.fields.filter((f) => !linkedKeys.has(f.key) && isStandardAnswerChoice(f, template))
  const tickFieldKeys = new Set(tickFields.map((f) => f.key))
  const otherFields = block.fields.filter((f) => !linkedKeys.has(f.key) && !tickFieldKeys.has(f.key))
  const answerIds = template.answers.map((a) => a.id)
  const widths = interviewTickWidths()
  const out = []
  for (const entry of entries) {
    const linkedFields = (block.linkFrom && block.linkFrom.fields) || []
    const extra = linkedFields.filter((k) => k !== block.titleField).map((k) => entry[k]).filter((v) => !blank(v)).join(' ')
    const caption = `${entry[block.titleField] || ''}${extra ? ` · Batch ${extra}` : ''}`
    out.push(subheadParagraph(caption))
    if (tickFields.length > 0) {
      const headerRow = new TableRow({ children: INTERVIEW_TICK_COLUMNS.map((c, i) => headerCellDxa(c.label, widths[i])) })
      const rows = tickFields.map((f) => new TableRow({
        children: [
          bodyCellDxa(widths[0], [new Paragraph({ children: [run(f.label)] })]),
          ...answerIds.map((id, ci) => bodyCellDxa(widths[1 + ci], [tickParagraph(entry[f.key] === id, answerMap[id]?.tone)])),
        ],
      }))
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
  return new TableRow({
    children: [
      bodyCellDxa(widths[0], [tickParagraph(checked, 'no')]),
      bodyCellDxa(widths[1], [new Paragraph({ children: [run(text, { bold: checked, color })] })]),
    ],
  })
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

function blockDocx(block, data, template, answerMap) {
  if (block.type === 'fields') return fieldsBlockDocx(block, data)
  if (block.type === 'checklist') return checklistBlockDocx(block, data, template, answerMap)
  if (block.type === 'cards') return block.display === 'tabs' ? tabsCardsBlockDocx(block, data, template, answerMap) : cardsBlockDocx(block, data)
  return [] // unknown block type (flags/countsAsFlags cards handled in sectionDocx) -- ignore
}

// black letter-badge heading, e.g. " C  Attendance in each course" -- matches the reference
// docx's badge run (bold white-on-black) + heading run (bold, two leading spaces).
function sectionHeadingParagraph(section) {
  const children = [
    run(` ${section.letter} `, { bold: true, color: 'FFFFFF', size: BADGE_SIZE, shading: { type: ShadingType.CLEAR, fill: BADGE_BG } }),
    run(`  ${section.title}`, { bold: true, size: SECTION_TITLE_SIZE }),
  ]
  if (section.optional) children.push(run('   (Optional)', { bold: true, italics: true, size: NOTE_SIZE, color: OPTIONAL_COLOR }))
  return new Paragraph({ keepNext: true, spacing: { before: 200, after: 60 }, children })
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
    out.push(...blockDocx(block, data, template, answerMap))
  }
  return out
}

function statusLabel(status) {
  return status === 'submitted' ? 'Submitted' : 'Draft'
}

// program line + title/subtitle line (bottom border, subtitle right-tabbed to the content edge,
// same as the reference docx) + a meta line (officer/status/submitted -- not in the paper form,
// but useful on an exported copy).
function headerParagraphs(template, meta) {
  const titleLine = new Paragraph({
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: BADGE_BG, space: 4 } },
    tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH_TWIPS }],
    spacing: { after: 120 },
    children: [
      run(template.title, { bold: true, size: TITLE_SIZE }),
      new TextRun({ children: [new Tab()], font: FONT, size: SUBTITLE_SIZE, color: NOTE_COLOR }),
      run(template.subtitle, { size: SUBTITLE_SIZE, color: NOTE_COLOR }),
    ],
  })
  let metaText = `Officer: ${meta.officerName || ''}    Status: ${statusLabel(meta.status)}`
  if (meta.status === 'submitted' && !blank(meta.submittedAt)) metaText += `    Submitted: ${meta.submittedAt}`
  return [
    new Paragraph({ children: [run(template.program, { size: PROGRAM_SIZE })] }),
    titleLine,
    new Paragraph({ spacing: { after: 160 }, children: [run(metaText, { size: META_SIZE, color: NOTE_COLOR })] }),
  ]
}

function legendParagraph() {
  const text = 'T = total, F = female, TMS = Training Management System, TDP = training delivery plan, CS = competency standard, ' +
    'CBLM = competency-based learning material, PPE = personal protective equipment, OHS = occupational health and safety.'
  return new Paragraph({ spacing: { before: 160 }, children: [run(text, { italics: true, size: LEGEND_SIZE, color: NOTE_COLOR })] })
}

// footer: "SICIP Surprise Visit Report" bottom-left, "Page x of y" bottom-right -- mirrors
// reporthtml.js's @bottom-left/@bottom-right @page rules.
function pageFooter() {
  return new Footer({
    children: [
      new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH_TWIPS }],
        children: [
          run('SICIP Surprise Visit Report', { size: SMALL_SIZE, color: NOTE_COLOR }),
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

// pure fn: template + report data + {officerName, submittedAt?, status} -> Promise<Blob> (.docx).
export function buildReportDocx(template, data, meta) {
  const normalizedData = withNormalizedData(template, data)
  const answerMap = answerMapOf(template)
  const children = headerParagraphs(template, meta)
  for (const section of template.sections || []) children.push(...sectionDocx(section, normalizedData, template, answerMap))
  children.push(legendParagraph())

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
