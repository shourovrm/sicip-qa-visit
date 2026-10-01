// spec 2026-10-02 items 9-print, 10-14: repeat header rows, "-" for blank cells, QA 1.40/1.50/1.60
// column sets with fixed widths, no "one point per line" hint. Shared by HTML and Word outputs.
import { describe, it, expect, vi } from 'vitest'
import JSZip from 'jszip'
import { templateFor } from './reporttemplate.js'
import { reportHtml } from './reporthtml.js'
import { narrativeHtml } from './narrativehtml.js'
import { qaReportHtml } from './qareporthtml.js'
import { buildReportDocx } from './reportdocx.js'
import { buildNarrativeDocx } from './narrativedocx.js'
import { buildQaReportDocx } from './qareportdocx.js'
import { cellOrDash } from './reportlayout.js'
import { batchesTable, cumulativeTable, headerRowsOf, mouCoursesTable } from './qatables.js'
import fixture from '../../../shared/report-templates/fixtures/remarks-surprise-2.json'

vi.setConfig({ testTimeout: 30000 })

const qaTemplate = templateFor('qa', 2)
const qaTemplateV1 = templateFor('qa', 1)
const surpriseTemplate = templateFor('surprise', 2)
const EMPTY_QA = { fields: {}, checks: {}, cards: {}, flags: [], criteria: {} }
const COURSE = 'Electrical Installation and Maintenance (Domestic)'
const FILLED_QA = {
  ...EMPTY_QA,
  cards: {
    mou_courses: [{ _id: 'm1', course: COURSE, target: '30', duration: '3 months', batches: '2', batch_size: '15' }],
    cumulative: [{ _id: 'c1', course: COURSE, target: '30', enrolled_t: '28', enrolled_f: '10',
      certified_t: '20', certified_f: '8', placed_t: '15', placed_f: '6', dropout_t: '4', dropout_f: '1' }],
    batches: [{ _id: 'b1', course: COURSE, batch: '07', start_end: '07/09/2026 – 24/11/2026', enrolled: '28', female: '10' }],
  },
}

const documentXml = async (blob) => (await JSZip.loadAsync(await blob.arrayBuffer())).file('word/document.xml').async('string')

// the <w:tbl> that follows `heading` in the document xml
function tableAfter(xml, heading) {
  const start = xml.indexOf(heading)
  expect(start).toBeGreaterThan(-1)
  const tableStart = xml.indexOf('<w:tbl>', start)
  return xml.slice(tableStart, xml.indexOf('</w:tbl>', tableStart))
}
const gridWidths = (tableXml) => [...tableXml.matchAll(/<w:gridCol w:w="(\d+)"/g)].map((m) => Number(m[1]))
const headerRowCount = (tableXml) => (tableXml.match(/<w:tblHeader\/>/g) ?? []).length
const cellText = (xml) => [...xml.matchAll(/<w:t[^>]*>([^<]*)<\/w:t>/g)].map((m) => m[1])

describe('cellOrDash', () => {
  it('turns blank values into a dash and keeps real ones', () => {
    expect(cellOrDash('')).toBe('-')
    expect(cellOrDash('  ')).toBe('-')
    expect(cellOrDash(null)).toBe('-')
    expect(cellOrDash(undefined)).toBe('-')
    expect(cellOrDash('12')).toBe('12')
    expect(cellOrDash(0)).toBe('0')
  })
})

describe('QA 1.40 / 1.50 / 1.60 column sets', () => {
  const block = (key) => qaTemplate.sections[0].blocks.find((b) => b.key === key)

  it('1.40 has the six template columns, numbered rows, course widest', () => {
    const table = mouCoursesTable(block('mou_courses'), FILLED_QA)
    expect(table.headers).toEqual(['S.N.', 'Training Course', 'Target', 'Duration', 'No. of Batches', 'Batch Size'])
    expect(table.rows).toEqual([['1.', COURSE, '30', '3 months', '2', '15']])
    expect(table.weights[1]).toBe(Math.max(...table.weights))
  })

  it('1.50 has the compact two-level header, keeps the 1.50 heading, course widest', () => {
    const table = cumulativeTable(block('cumulative'), FILLED_QA)
    expect(table.heading.startsWith('1.50 ')).toBe(true)
    const [top, bottom] = headerRowsOf(table)
    expect(top.map((cell) => cell.text)).toEqual(['S.N.', 'Course Name', 'Target', 'Enrolled', 'Certified', 'Job Placed with Percentage', 'No. of dropouts with Percentage'])
    expect(bottom.map((cell) => cell.text)).toEqual(['T', 'F', 'T', 'F', 'T', 'F', 'T', 'F'])
    expect(table.note).toMatch(/^T\s?= Total and F = Female$/)
    expect(table.rows[0]).toEqual(['1.', COURSE, '30', '28', '10', '20', '8', '15 (75%)', '6 (75%)', '4 (14%)', '1 (10%)'])
    expect(table.weights).toHaveLength(11)
    expect(table.weights[1]).toBe(Math.max(...table.weights))
  })

  it('1.60 has the ten template columns', () => {
    const table = batchesTable(block('batches'), FILLED_QA)
    expect(table.heading.startsWith('1.60 ')).toBe(true)
    expect(table.headers).toEqual(['S.N.', 'Course Name', 'Batch No.', 'Start and End Date', 'Total Number of Enrolled Trainees',
      'Number of Female Trainees', 'Attendance on Visit Date', 'Attendance (07-day average)',
      'No. of Attendance Data-Mismatch with TMS', 'No. of Dropouts'])
    expect(table.weights).toHaveLength(10)
  })

  it('a table nobody filled prints one blank row numbered 1.', () => {
    const table = batchesTable(block('batches'), EMPTY_QA)
    expect(table.rows).toHaveLength(1)
    expect(table.rows[0][0]).toBe('1.')
    expect(table.rows[0].slice(1).every((cell) => cell === '')).toBe(true)
  })

  it('filled tables print only filled rows (no padding)', () => {
    expect(mouCoursesTable(block('mou_courses'), FILLED_QA).rows).toHaveLength(1)
  })
})

describe('QA Word widths and wrapping', () => {
  it('1.50 and 1.60 have unequal fixed column widths with the course column widest', async () => {
    const xml = await documentXml(await buildQaReportDocx(qaTemplate, FILLED_QA, {}))
    for (const heading of ['1.50 Cumulative', '1.60 Enrolment']) {
      const widths = gridWidths(tableAfter(xml, heading))
      expect(new Set(widths).size).toBeGreaterThan(2)
      expect(widths[1]).toBe(Math.max(...widths.slice(1)))
    }
    expect(gridWidths(tableAfter(xml, '1.50 Cumulative'))).toHaveLength(11)
  })

  it('1.50 repeats BOTH header rows, 1.40 and 1.60 repeat their header row', async () => {
    const xml = await documentXml(await buildQaReportDocx(qaTemplate, FILLED_QA, {}))
    expect(headerRowCount(tableAfter(xml, '1.50 Cumulative'))).toBe(2)
    expect(headerRowCount(tableAfter(xml, '1.40 Name'))).toBe(1)
    expect(headerRowCount(tableAfter(xml, '1.60 Enrolment'))).toBe(1)
  })
})

describe('repeat header rows', () => {
  it('every QA Word table has a header row flagged to repeat', async () => {
    const xml = await documentXml(await buildQaReportDocx(qaTemplate, FILLED_QA, {}))
    const tables = xml.split('<w:tbl>').slice(1).map((part) => part.slice(0, part.indexOf('</w:tbl>')))
    // the signature table is borderless layout, not a data table
    const dataTables = tables.filter((table) => !table.includes('Submitted'))
    expect(dataTables.length).toBeGreaterThan(5)
    for (const table of dataTables.slice(0, -1)) expect(headerRowCount(table)).toBeGreaterThanOrEqual(1)
  })

  it('HTML tables put the header in a thead and the css repeats it', () => {
    const html = qaReportHtml(qaTemplate, FILLED_QA, {})
    expect(html).toContain('<thead>')
    expect(html).toContain('thead { display: table-header-group; }')
    expect(html).toContain('page-break-inside: avoid')
    expect(html).not.toMatch(/<table[^>]*>\s*<tr><th/)
    for (const html2 of [reportHtml(surpriseTemplate, fixture.data, {}), narrativeHtml(surpriseTemplate, fixture.data, {})]) {
      expect(html2).toContain('<thead>')
      expect(html2).toContain('thead { display: table-header-group; }')
    }
  })

  it('surprise form and narrative Word tables repeat their header rows', async () => {
    for (const build of [buildReportDocx, buildNarrativeDocx]) {
      const xml = await documentXml(await build(surpriseTemplate, fixture.data, {}))
      expect(headerRowCount(xml)).toBeGreaterThan(2)
    }
  })

  it('Word body rows do not split across pages', async () => {
    const xml = await documentXml(await buildQaReportDocx(qaTemplate, FILLED_QA, {}))
    expect(tableAfter(xml, '1.40 Name')).toContain('<w:cantSplit/>')
  })
})

describe('blank cells print a dash', () => {
  it('QA html: an unfilled 1.60 table prints one row of dashes', () => {
    const html = qaReportHtml(qaTemplate, EMPTY_QA, {})
    const start = html.indexOf('1.60 Enrolment')
    const table = html.slice(start, html.indexOf('</table>', start))
    expect(table.match(/<td[^>]*>-<\/td>/g)).toHaveLength(9)
    expect(table).toContain('>1.<')
    expect(table.match(/<tr>/g)).toHaveLength(2) // header + one row
  })

  it('QA html: blank cells in a filled row print a dash', () => {
    const html = qaReportHtml(qaTemplate, FILLED_QA, {})
    const start = html.indexOf('1.60 Enrolment')
    const table = html.slice(start, html.indexOf('</table>', start))
    expect(table.match(/<td[^>]*>-<\/td>/g)).toHaveLength(4) // attendance x2, mismatch, dropouts
  })

  it('QA Word: an unfilled 1.50 table prints dashes', async () => {
    const xml = await documentXml(await buildQaReportDocx(qaTemplate, EMPTY_QA, {}))
    const table = tableAfter(xml, '1.50 Cumulative')
    expect(cellText(table).filter((text) => text === '-')).toHaveLength(10)
  })

  it('surprise html: a blank checklist remarks cell prints a dash, tick cells keep their boxes', () => {
    const html = reportHtml(surpriseTemplate, fixture.data, {})
    expect(html).toContain('<td class="remarks">-</td>')
    expect(html).toContain('☐')
    expect(html).not.toContain('<td class="remarks"></td>')
  })

  it('surprise word: blank remarks cells carry a dash', async () => {
    const xml = await documentXml(await buildReportDocx(surpriseTemplate, fixture.data, {}))
    expect(cellText(xml)).toContain('-')
    expect(xml).toContain('☐')
  })
})

describe('no "one point per line" hint in print', () => {
  it('QA html and Word of v2 and v1', async () => {
    for (const template of [qaTemplate, qaTemplateV1]) {
      const html = qaReportHtml(template, EMPTY_QA, {})
      expect(html.toLowerCase()).not.toContain('one point per line')
      const xml = await documentXml(await buildQaReportDocx(template, EMPTY_QA, {}))
      expect(xml.toLowerCase()).not.toContain('one point per line')
    }
  })
})

describe('major findings print from the box', () => {
  const data = structuredClone(fixture.data)
  data.findingsText = 'Box finding one\nBox finding two'
  data.findings = [{ src: '', text: 'Old picked finding' }]

  it('html form and narrative', () => {
    for (const html of [reportHtml(surpriseTemplate, data, {}), narrativeHtml(surpriseTemplate, data, {})]) {
      expect(html).toContain('<li>Box finding one</li>')
      expect(html).toContain('<li>Box finding two</li>')
      expect(html).not.toContain('Old picked finding')
    }
  })

  it('word form and narrative', async () => {
    for (const build of [buildReportDocx, buildNarrativeDocx]) {
      const xml = await documentXml(await build(surpriseTemplate, data, {}))
      expect(xml).toContain('Box finding two')
      expect(xml).not.toContain('Old picked finding')
    }
  })
})
