// reportHtml -- structural checks only (escaping, colours, Not answered, omissions). the
// visual layout/print path is browser window.print(), not JVM/node-testable here (see
// billhtml.test.js for the same convention).
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { reportHtml } from './reporthtml.js'

// loaded via node fs (not JSON import) -- vite's server.fs.allow for ../shared is W1's
// vite.config change and may not have landed yet when this test runs.
function loadJson(relPath) {
  const path = fileURLToPath(new URL(relPath, import.meta.url))
  return JSON.parse(readFileSync(path, 'utf-8'))
}

const template = loadJson('../../../shared/report-templates/surprise-v1.json')
const fixture = loadJson('../../../shared/report-templates/fixtures/progress-1.json')

const meta = { officerName: 'Mahfuzul Islam', status: 'draft' }

it('escapes user text in fields, checklist remarks, and cards', () => {
  const data = {
    fields: { ti_name: 'A & B <script>alert(1)</script>' },
    checks: { arrival_1: { answer: 'yes', remarks: 'Fine & good <b>bold</b>' } },
    cards: { attendance: [{ course: 'Weld<ing> & Fab' }] },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  expect(html).toContain('A &amp; B &lt;script&gt;')
  expect(html).not.toContain('<script>alert(1)</script>')
  expect(html).toContain('Fine &amp; good &lt;b&gt;bold&lt;/b&gt;')
  expect(html).toContain('Weld&lt;ing&gt; &amp; Fab')
})

it('blank checklist items print Not answered; answered ones print a coloured tone label', () => {
  const data = { fields: {}, checks: { arrival_1: { answer: 'yes', remarks: '' } }, cards: {}, flags: [] }
  const html = reportHtml(template, data, meta)
  // arrival_1 answered yes -> coloured label, not "Not answered"
  const arrivalSection = html.slice(html.indexOf('>B<'), html.indexOf('>C<'))
  expect(arrivalSection).toContain('color:#1c6b38')
  expect(arrivalSection).toContain('>Yes<')
  // arrival_2..4 unanswered -> "Not answered"
  expect((arrivalSection.match(/Not answered/g) || []).length).toBe(3)
})

it('uses the four spec tone colours for yes/no/partial/na answers', () => {
  const data = {
    fields: {},
    checks: {
      arrival_1: { answer: 'yes', remarks: '' },
      arrival_2: { answer: 'no', remarks: '' },
      arrival_3: { answer: 'partial', remarks: '' },
      arrival_4: { answer: 'na', remarks: '' },
    },
    cards: {},
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  expect(html).toContain('color:#1c6b38')
  expect(html).toContain('color:#b3261e')
  expect(html).toContain('color:#8a4600')
  expect(html).toContain('color:#4c4f66')
})

it('empty remarks render no remarks text (not a placeholder)', () => {
  const data = { fields: {}, checks: { arrival_1: { answer: 'yes', remarks: '' } }, cards: {}, flags: [] }
  const html = reportHtml(template, data, meta)
  const row = html.slice(html.indexOf('Centre open and training'))
  const rowEnd = row.indexOf('</tr>')
  expect(row.slice(0, rowEnd)).toContain('<td class="remarks"></td>')
})

it('unticked flags are omitted; ticked flags print, untouched ones do not appear as list items', () => {
  const data = { fields: {}, checks: {}, cards: {}, flags: ['flag_3'] }
  const html = reportHtml(template, data, meta)
  const flagsSection = html.slice(html.indexOf('>L<'), html.indexOf('>M<'))
  expect(flagsSection).toContain('possible ghost trainees')
  expect(flagsSection).not.toContain('Trainer absent or replaced')
  expect(flagsSection).not.toContain('none-ticked')
})

it('no ticked flags prints the None ticked placeholder', () => {
  const data = { fields: {}, checks: {}, cards: {}, flags: [] }
  const html = reportHtml(template, data, meta)
  const flagsSection = html.slice(html.indexOf('>L<'), html.indexOf('>M<'))
  expect(flagsSection).toContain('None ticked.')
})

it('cards block renders one table per entry with the title field, and flags compare mismatches', () => {
  const data = {
    fields: {},
    checks: {},
    cards: {
      attendance: [
        { course: 'Welding (SMAW)', present_total: '17', register: '23', tms: '23' }, // mismatch: 17 vs 23
        { course: 'Electrical Installation', present_total: '22', register: '22', tms: '22' }, // matches
      ],
    },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  expect(html).toContain('Course 1: Welding (SMAW)')
  expect(html).toContain('Course 2: Electrical Installation')
  const table1Start = html.lastIndexOf('<table', html.indexOf('Course 1'))
  const table2Start = html.lastIndexOf('<table', html.indexOf('Course 2'))
  const mismatchCard = html.slice(table1Start, table2Start)
  expect(mismatchCard).toContain('class="card mismatch"')
  expect(mismatchCard).toContain('Headcount, register and TMS do not match')
  const matchCard = html.slice(table2Start)
  expect(matchCard.slice(0, matchCard.indexOf('</table>'))).not.toContain('mismatch')
})

it('cards block with no entries prints an empty placeholder, not an empty table', () => {
  const data = { fields: {}, checks: {}, cards: {}, flags: [] }
  const html = reportHtml(template, data, meta)
  const graduatesSection = html.slice(html.indexOf('>J<'), html.indexOf('>K<'))
  expect(graduatesSection).toContain('No entries.')
})

it('section M renders the rating as a coloured choice and free text fields verbatim', () => {
  const data = {
    fields: { overall_rating: 'satisfactory', key_findings: 'Line one\nLine two' },
    checks: {},
    cards: {},
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  const ratingSection = html.slice(html.indexOf('>M<'))
  expect(ratingSection).toContain('color:#1c6b38')
  expect(ratingSection).toContain('>Satisfactory<')
  expect(ratingSection).toContain('Line one<br>Line two')
})

it('header carries template title/program and officer meta', () => {
  const html = reportHtml(template, { fields: {}, checks: {}, cards: {}, flags: [] }, { officerName: 'Rakib Hasan', status: 'submitted', submittedAt: '2026-09-24T10:00:00Z' })
  expect(html).toContain(template.title)
  expect(html).toContain(template.program)
  expect(html).toContain('Rakib Hasan')
  expect(html).toContain('Submitted')
})

describe('fixture parity smoke test', () => {
  it('renders the shared progress fixture without throwing and includes its known answers', () => {
    const html = reportHtml(template, fixture.data, { officerName: 'Test Officer', status: 'draft' })
    expect(html).toContain('Bangladesh-Korea TTC, Mirpur')
    expect(html).toContain('Theory instead of practical')
    expect(html).toContain('Rakib')
  })
})
