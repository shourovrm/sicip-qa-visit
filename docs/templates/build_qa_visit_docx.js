// builds a congested review docx of the QA visit report from shared/report-templates/qa-v1.json,
// styled like surprise-visit-report-template.docx (Arial 8.5pt, grey headers, black badges)
const fs = require('fs')
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType,
  AlignmentType, BorderStyle, Footer, PageNumber, TabStopType, VerticalAlign, PageBreak,
} = require('docx')

const path = require('path')
const template = JSON.parse(fs.readFileSync(path.join(__dirname, '../../shared/report-templates/qa-v1.json'), 'utf8'))
// run from repo root: NODE_PATH=web/node_modules node docs/templates/build_qa_visit_docx.js docs/templates/qa-visit-report-template.docx
const OUT = process.argv[2]

const FONT = 'Arial'
const SIZE = 17 // half-points = 8.5pt, as the surprise template
const SMALL = 15
const GREY = 'E7E7E7'
const LIGHT = 'F4F4F4'
const WIDTH = 10466 // A4 11906 - 2 * 720 margins
const BOX = '☐'
const border = { style: BorderStyle.SINGLE, size: 4, color: '808080' }
const borders = { top: border, bottom: border, left: border, right: border }

function run(text, opts = {}) {
  return new TextRun({ text, font: FONT, size: opts.size ?? SIZE, bold: opts.bold, italics: opts.italics, color: opts.color })
}
function para(children, opts = {}) {
  return new Paragraph({
    children: Array.isArray(children) ? children : [run(children, opts)],
    alignment: opts.align,
    spacing: { before: opts.before ?? 0, after: opts.after ?? 40 },
    keepNext: opts.keepNext,
    tabStops: opts.tabStops,
    border: opts.border,
  })
}
function cell(content, width, opts = {}) {
  const paragraphs = (Array.isArray(content) ? content : [content]).map((c) =>
    c instanceof Paragraph ? c : para(String(c ?? ''), { bold: opts.bold, align: opts.align, size: opts.size, italics: opts.italics, after: 0 }))
  return new TableCell({
    children: paragraphs,
    width: { size: width, type: WidthType.DXA },
    columnSpan: opts.span,
    borders,
    verticalAlign: VerticalAlign.CENTER,
    shading: opts.fill ? { type: ShadingType.CLEAR, color: 'auto', fill: opts.fill } : undefined,
    margins: { top: 30, bottom: 30, left: 60, right: 60 },
  })
}
function table(widths, rows) {
  return new Table({ width: { size: WIDTH, type: WidthType.DXA }, columnWidths: widths, rows })
}
function headerRow(widths, labels) {
  return new TableRow({ tableHeader: true, children: labels.map((l, i) => cell(l, widths[i], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL })) })
}
function blankRows(widths, count, firstNumber = 1) {
  return Array.from({ length: count }, (_, i) => new TableRow({
    height: { value: 320, rule: 'atLeast' },
    children: widths.map((w, c) => cell(c === 0 ? String(firstNumber + i) : '', w, { align: AlignmentType.CENTER })),
  }))
}

// black badge + title, like the surprise template's "A  Visit details"
function sectionHeading(badge, title, note) {
  const children = [
    new TextRun({ text: ` ${badge} `, font: FONT, size: 18, bold: true, color: 'FFFFFF', shading: { type: ShadingType.CLEAR, color: 'auto', fill: '000000' } }),
    run(`  ${title}`, { bold: true, size: 19 }),
  ]
  if (note) children.push(new TextRun({ children: ['\t', note], font: FONT, size: SMALL, italics: true, bold: true, color: '444444' }))
  return new Paragraph({ children, spacing: { before: 160, after: 60 }, keepNext: true, tabStops: [{ type: TabStopType.RIGHT, position: WIDTH }] })
}
function subHeading(text) {
  return para(text, { bold: true, before: 80, after: 40, keepNext: true })
}
function intro(text) {
  return para(`(${text})`, { italics: true, size: SMALL, after: 40, keepNext: true })
}
// "Label ____________" fill line using a dotted right tab to the page edge
function fillLine(label) {
  return new Paragraph({
    children: [run(label + ' '), new TextRun({ children: ['\t'], font: FONT, size: SIZE })],
    tabStops: [{ type: TabStopType.RIGHT, position: WIDTH, leader: 'underscore' }],
    spacing: { before: 60, after: 20 },
  })
}
function box(heightTwips) {
  return table([WIDTH], [new TableRow({ height: { value: heightTwips, rule: 'atLeast' }, children: [cell('', WIDTH)] })])
}
function sourceTag(src) {
  return (src ?? []).length ? ` [${src.join(', ')}]` : ''
}

const body = []

// title block
body.push(para([run(template.program, { size: SMALL })], { after: 0 }))
body.push(new Paragraph({
  children: [run(template.title, { bold: true, size: 26 }), new TextRun({ children: ['\t', `${template.annex} · review copy of the app's QA visit form`], font: FONT, size: SMALL, color: '444444' })],
  tabStops: [{ type: TabStopType.RIGHT, position: WIDTH }],
  border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: '000000', space: 2 } },
  spacing: { after: 80 },
}))
body.push(para([run('How to read this copy: ', { bold: true, size: SMALL }), run('each criterion (grey row) shows the Annex-3 evidence text in italics, then the points the officer marks Seen / Not seen / N/A. Tags: A3 = Annex-3 evidence, CL = Monitoring Checklist, FC = flowchart. A point with "(detail: …)" gets a small box in the app. The fixed sentence each answer writes into Remarks is listed in the Annex at the end.', { size: SMALL })], { after: 60 }))

const criteriaWidths = [520, 4800, 560, 700, 560, 3326]

for (const section of template.sections) {
  body.push(sectionHeading(section.number, section.title))
  for (const block of section.blocks) {
    if (block.type === 'fields') {
      if (block.heading) body.push(subHeading(block.heading))
      if (section.key === 's13') continue // drawn as a table below
      for (const field of block.fields) {
        if (field.kind === 'choice') {
          const choices = (field.options ?? field.choices ?? []).map((o) => `${BOX} ${o.label ?? o}`).join('    ')
          body.push(para([run(field.label + ':  '), run(choices || `${BOX} Yes    ${BOX} N/A`)], { before: 60 }))
        } else if (['findings', 'recommendations', 'dropout_reasons', 'dropout_steps', 'comments'].includes(field.key)) {
          body.push(para(field.label, { before: 60, keepNext: true }))
          body.push(box(field.key === 'comments' ? 500 : 800))
        } else {
          body.push(fillLine(field.label))
        }
      }
    } else if (block.type === 'cards') {
      if (block.heading) body.push(subHeading(block.heading))
      const rows = Math.max(block.start ?? 2, 2)
      if (block.key === 'cumulative') {
        const w = [420, 2400, 820, 850, 850, 850, 850, 850, 850, 863, 863]
        body.push(table(w, [
          new TableRow({ tableHeader: true, children: [
            cell('#', w[0], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL }),
            cell('Course name', w[1], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL }),
            cell('Target', w[2], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL }),
            cell('Enrolled', w[3] + w[4], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL, span: 2 }),
            cell('Certified', w[5] + w[6], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL, span: 2 }),
            cell('Job placed (%)', w[7] + w[8], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL, span: 2 }),
            cell('Dropouts (%)', w[9] + w[10], { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL, span: 2 }),
          ] }),
          new TableRow({ tableHeader: true, children: w.map((width, i) => cell(i < 3 ? '' : (i % 2 ? 'T' : 'F'), width, { bold: true, fill: GREY, align: AlignmentType.CENTER, size: SMALL })) }),
          ...blankRows(w, rows),
        ]))
        body.push(para('T = Total, F = Female. The app calculates placed % of certified and dropout % of enrolled.', { size: SMALL, italics: true }))
        continue
      }
      const labels = { name: 'Name', designation: 'Designation', contact: 'Contact number', course: 'Course name', target: 'Target', duration: 'Duration',
        batches: 'No. of batches', batch_size: 'Batch size', batch: 'Batch no.', start_end: 'Start and end date', enrolled: 'Total enrolled', female: 'Female',
        attendance_today: 'Attendance on visit date', attendance_7day: 'Attendance (7-day avg)', tms_mismatch: 'Attendance mismatch with TMS', dropouts: 'No. of dropouts',
        trade: 'Trade', feedback: 'Feedback' }
      const fields = block.fields
      const numberWidth = 420
      const feedbackIndex = fields.findIndex((f) => f.key === 'feedback')
      let widths = fields.map(() => Math.floor((WIDTH - numberWidth) / fields.length))
      if (feedbackIndex >= 0) {
        const other = 1700
        widths = fields.map((f, i) => (i === feedbackIndex ? WIDTH - numberWidth - other * (fields.length - 1) : other))
      }
      widths[widths.length - 1] += WIDTH - numberWidth - widths.reduce((a, b) => a + b, 0)
      const all = [numberWidth, ...widths]
      body.push(table(all, [headerRow(all, ['#', ...fields.map((f) => labels[f.key] ?? f.label)]), ...blankRows(all, Math.max(rows, 3))]))
    } else if (block.type === 'criteria') {
      if (block.heading) body.push(subHeading(block.heading))
      if (block.intro) body.push(intro(block.intro))
      const rows = [headerRow(criteriaWidths, ['Sl.', 'Quality criteria / point checked', 'Seen', 'Not seen', 'N/A', 'Remarks'])]
      for (const item of block.items) {
        if (item.heading) {
          rows.push(new TableRow({ children: [
            cell(item.no, criteriaWidths[0], { bold: true, fill: GREY }),
            cell(item.text, WIDTH - criteriaWidths[0], { bold: true, fill: GREY, span: 5 }),
          ] }))
          continue
        }
        const evidence = (item.evidence ?? '').split('\n').filter((l) => l.trim())
        const criterionParas = [para([run(item.text, { bold: true })], { after: 0 })]
        if (evidence.length) criterionParas.push(para([run('Evidence: ', { size: SMALL, italics: true, bold: true }), run(evidence.join(' '), { size: SMALL, italics: true })], { after: 0 }))
        rows.push(new TableRow({ cantSplit: true, children: [
          cell(item.no, criteriaWidths[0], { bold: true, fill: LIGHT, align: AlignmentType.CENTER }),
          new TableCell({ children: criterionParas, width: { size: WIDTH - criteriaWidths[0], type: WidthType.DXA }, columnSpan: 5, borders,
            shading: { type: ShadingType.CLEAR, color: 'auto', fill: LIGHT }, margins: { top: 30, bottom: 30, left: 60, right: 60 } }),
        ] }))
        item.options.forEach((option, index) => {
          const detail = option.detail ? `  (detail: ${option.detail} ________)` : ''
          rows.push(new TableRow({ cantSplit: true, children: [
            cell(`${String.fromCharCode(97 + index)}`, criteriaWidths[0], { align: AlignmentType.CENTER, size: SMALL }),
            cell([para([run(option.label), run(sourceTag(option.src), { size: SMALL, color: '555555' }), run(detail, { size: SMALL })], { after: 0 })], criteriaWidths[1]),
            cell(BOX, criteriaWidths[2], { align: AlignmentType.CENTER }),
            cell(BOX, criteriaWidths[3], { align: AlignmentType.CENTER }),
            cell(BOX, criteriaWidths[4], { align: AlignmentType.CENTER }),
            cell('', criteriaWidths[5]),
          ] }))
        })
        rows.push(new TableRow({ cantSplit: true, height: { value: 300, rule: 'atLeast' }, children: [
          cell('', criteriaWidths[0]),
          cell([para([run('Evidence seen: ', { size: SMALL, bold: true })], { after: 0 })], criteriaWidths[1]),
          cell([para([run('Other remarks: ', { size: SMALL, bold: true })], { after: 0 })], WIDTH - criteriaWidths[0] - criteriaWidths[1], { span: 4 }),
        ] }))
      }
      body.push(table(criteriaWidths, rows))
    }
  }
  if (section.key === 's13') {
    const w = [420, 3200, 3423, 3423]
    const components = ['Quality Management System', 'Budgeting', 'Selection and Enrolment of Trainees', 'Job Placement or Employment Support',
      'Standards, Learning Material and Assessment Tools', 'Trainers', 'Physical Resources', 'Training & Learning Approach', 'Assessment and Certification']
    body.push(table(w, [headerRow(w, ['S.N.', 'Component', 'Strengths', 'Weakness']),
      ...components.map((c, i) => new TableRow({ height: { value: 360, rule: 'atLeast' }, children: [cell(String(i + 1), w[0], { align: AlignmentType.CENTER }), cell(c, w[1]), cell('', w[2]), cell('', w[3])] }))]))
  }
}

// signatures
body.push(para('', { before: 160 }))
const sw = [5233, 5233]
body.push(table(sw, [headerRow(sw, ['Name(s) of the Visiting Officer(s) with designation', 'Signature']), ...[1, 2].map((n) => new TableRow({ height: { value: 440, rule: 'atLeast' }, children: [cell(`${n})`, sw[0]), cell('', sw[1])] }))]))

// annex: every fixed sentence, for review
body.push(new Paragraph({ children: [new PageBreak()] }))
body.push(sectionHeading('A', 'Annex: fixed sentences written into Remarks', '— = prints nothing · {…$…} = detail box text (dropped if empty)'))
const aw = [620, 2300, 2700, 2700, 2146]
const annexRows = [headerRow(aw, ['Ref', 'Point', 'Seen →', 'Not seen →', 'N/A →'])]
for (const section of template.sections) {
  for (const block of section.blocks) {
    if (block.type !== 'criteria') continue
    annexRows.push(new TableRow({ children: [cell(section.number + (block.heading ? '.1' : ''), aw[0], { bold: true, fill: GREY }), cell(block.heading ?? section.title, WIDTH - aw[0], { bold: true, fill: GREY, span: 4 })] }))
    for (const item of block.items) {
      if (item.heading) continue
      item.options.forEach((option, index) => {
        annexRows.push(new TableRow({ cantSplit: true, children: [
          cell(`${item.no} ${String.fromCharCode(97 + index)}`, aw[0], { size: SMALL }),
          cell(option.label, aw[1], { size: SMALL }),
          cell(option.seen ?? '', aw[2], { size: SMALL }),
          cell(option.not ?? '', aw[3], { size: SMALL }),
          cell(option.na || '—', aw[4], { size: SMALL }),
        ] }))
      })
    }
  }
}
body.push(table(aw, annexRows))

const doc = new Document({
  styles: { default: { document: { run: { font: FONT, size: SIZE } } } },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 620, bottom: 720, left: 720, right: 720 } } },
    footers: { default: new Footer({ children: [new Paragraph({
      tabStops: [{ type: TabStopType.RIGHT, position: WIDTH }],
      children: [run('SICIP Quality Assurance Visit Report · review copy', { size: 14, color: '666666' }),
        new TextRun({ children: ['\tPage ', PageNumber.CURRENT, ' of ', PageNumber.TOTAL_PAGES], font: FONT, size: 14, color: '666666' })],
    })] }) },
    children: body,
  }],
})
Packer.toBuffer(doc).then((buffer) => { fs.writeFileSync(OUT, buffer); console.log('wrote', OUT) })
