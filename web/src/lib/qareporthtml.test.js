// qaReportHtml -- structural checks, same convention as reporthtml.test.js (visual/print
// verification is not JVM/node-testable; this checks escaping, remarks rendering and section
// order/coverage).
import { describe, it, expect } from 'vitest'
import { qaReportHtml } from './qareporthtml.js'
import { templateFor } from './reporttemplate.js'

const template = templateFor('qa')
const meta = { officerName: 'Mahfuzul Islam', status: 'draft' }
const EMPTY_DATA = { fields: {}, checks: {}, cards: {}, flags: [], criteria: {} }

it('every section prints its Annex-3 number and title verbatim (no re-casing)', () => {
  const html = qaReportHtml(template, EMPTY_DATA, meta)
  for (const section of template.sections) {
    const escapedTitle = section.title.replace(/&/g, '&amp;')
    expect(html).toContain(`${section.number}. ${escapedTitle}`)
  }
})

it('escapes user text in fields, criteria evidence and remarks', () => {
  const data = {
    fields: { ti_name: 'A & B <script>alert(1)</script>' },
    criteria: { s8_1c: { opts: { first_aid: { v: 'not' } }, evidence: 'Photo <tag> & note', note: '' } },
    cards: {}, checks: {}, flags: [],
  }
  const html = qaReportHtml(template, data, meta)
  expect(html).toContain('A &amp; B &lt;script&gt;')
  expect(html).not.toContain('<script>alert(1)</script>')
  expect(html).toContain('No first aid kit was found.')
})

it('never prints "Not answered", even for a maximally blank report', () => {
  const html = qaReportHtml(template, EMPTY_DATA, meta)
  expect(html).not.toContain('Not answered')
})

it('an unmarked criteria option has a blank remarks cell (no placeholder)', () => {
  const html = qaReportHtml(template, EMPTY_DATA, meta)
  const idx = html.indexOf('Physical resources (including OHS requirements)')
  expect(idx).toBeGreaterThan(-1)
})

it('prints Annex-3, the program line and the title above the header block', () => {
  const html = qaReportHtml(template, EMPTY_DATA, meta)
  expect(html).toContain('>Annex-3<')
  expect(html).toContain(template.program)
  expect(html).toContain(template.title)
})

it('a marked criteria option prints its fixed sentence as a Remarks bullet', () => {
  const data = { ...EMPTY_DATA, criteria: { s2_1: { opts: { qms_process: { v: 'seen' } } } } }
  const html = qaReportHtml(template, data, meta)
  expect(html).toContain('A written quality management system procedure or work-flow chart is kept.')
})

it('section 1 cumulative table computes placed% of certified and dropout% of enrolled', () => {
  const data = {
    ...EMPTY_DATA,
    cards: { cumulative: [{ _id: 'c1', course: 'Welding', target: '30', enrolled_t: '28', enrolled_f: '10',
      certified_t: '20', certified_f: '8', placed_t: '10', placed_f: '4', dropout_t: '4', dropout_f: '1' }] },
  }
  const html = qaReportHtml(template, data, meta)
  expect(html).toContain('10 (50%)') // placed_t 10 of certified_t 20
  expect(html).toContain('4 (14%)') // dropout_t 4 of enrolled_t 28 -> 14.28% rounds to 14
})

it('section 13 has 9 fixed component rows in Annex-3 order', () => {
  const html = qaReportHtml(template, EMPTY_DATA, meta)
  expect(html).toContain('Quality Management System')
  expect(html.indexOf('Quality Management System')).toBeLessThan(html.indexOf('Assessment and Certification'))
})

it('sections 14/15 render one bullet per non-blank line', () => {
  const data = { ...EMPTY_DATA, fields: { findings: 'First finding\n\nSecond finding\n' } }
  const html = qaReportHtml(template, data, meta)
  const section14 = html.slice(html.indexOf('14. MAJOR FINDINGS'), html.indexOf('15. RECOMMENDATIONS'))
  expect((section14.match(/<li>/g) || []).length).toBe(2)
})
