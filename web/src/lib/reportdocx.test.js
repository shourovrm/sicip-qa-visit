// buildReportDocx / downloadReportDocx -- structural checks: non-empty blob, valid zip
// (docx files are zip archives) containing word/document.xml, and that xml carries the
// institute name and other expected content. JSZip is a transitive dependency of docx
// itself (bundled in node_modules), used here only to unzip and inspect the output.
import { it, expect, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import JSZip from 'jszip'
import { buildReportDocx } from './reportdocx.js'
// downloadReportDocx itself isn't exercised here: it's a two-line wrapper (URL.createObjectURL
// + throwaway <a>.click()) around buildReportDocx, and this vitest project has no jsdom/DOM
// environment configured (see billhtml.test.js, which likewise only tests the pure builder).

function loadJson(relPath) {
  const path = fileURLToPath(new URL(relPath, import.meta.url))
  return JSON.parse(readFileSync(path, 'utf-8'))
}

const template = loadJson('../../../shared/report-templates/surprise-v1.json')
const fixture = loadJson('../../../shared/report-templates/fixtures/progress-1.json')

const meta = { officerName: 'Mahfuzul Islam', status: 'draft' }

async function documentXml(blob) {
  const buf = await blob.arrayBuffer()
  const zip = await JSZip.loadAsync(buf)
  const entry = zip.file('word/document.xml')
  expect(entry).toBeTruthy()
  return entry.async('string')
}

// building a docx zip is slow on a loaded machine (parallel gradle): allow more than the 5s default
vi.setConfig({ testTimeout: 20000 })

it('produces a non-empty docx blob', async () => {
  const blob = await buildReportDocx(template, fixture.data, meta)
  expect(blob).toBeInstanceOf(Blob)
  expect(blob.size).toBeGreaterThan(0)
})

it('document.xml contains the institute name from data.fields', async () => {
  const blob = await buildReportDocx(template, fixture.data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('Bangladesh-Korea TTC, Mirpur')
})

it('document.xml carries the report title, program, and officer', async () => {
  const blob = await buildReportDocx(template, fixture.data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain(template.title)
  expect(xml).toContain(template.program)
  expect(xml).toContain('Mahfuzul Islam')
})

it('escapes xml-significant characters in user text', async () => {
  const data = { fields: { ti_name: 'A & B <script>' }, checks: {}, cards: {}, flags: [] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).not.toContain('<script>')
  expect(xml).toContain('A &amp; B')
})

// CHANGE SET 2 WORD EXPORT LAYOUT: checklist rows are Yes/No/Part/N/A tick columns (chosen =
// FILLED_BOX ☒, others EMPTY_BOX ☐), not a colour-label "Answer" column (that was CHANGE SET 1).
it('checklist ticks the chosen column in its tone colour and leaves the others empty', async () => {
  const data = { fields: {}, checks: { arrival_1: { answer: 'yes', remarks: '' } }, cards: {}, flags: [] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('1c6b38') // tone colour run property on the chosen "Yes" tick
  expect(xml).toContain('☒') // FILLED_BOX somewhere (the chosen tick)
  expect(xml).toContain('☐') // EMPTY_BOX somewhere (the other three columns)
  expect(xml).toContain('>Yes<') // column header text, still present regardless of any answer
})

// the fixed 9-item flags list always prints every item (paper-form style: ☒ or ☐ per row), and
// countsAsFlags cards (L "other_flags" free text) merge into the SAME list as always-ticked rows.
it('flags list shows every fixed item (ticked or not) plus custom flags as ticked rows', async () => {
  const data = {
    fields: {},
    checks: {},
    cards: { other_flags: [{ _id: 'f1', flag: 'Trainees sharing one ID card' }, { _id: 'f2', flag: ' ' }] },
    flags: ['flag_3'],
  }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('possible ghost trainees') // flag_3, ticked
  expect(xml).toContain('Trainer absent or replaced by an unapproved person') // unticked fixed flag, still listed
  expect(xml).toContain('Trainees sharing one ID card') // custom flag, non-blank -> merged in as ticked
})

it('renders CHANGE SET 2 content: persons, courses, attendance trainers present, K compliance/unresolved (Optional), custom flags', async () => {
  const blob = await buildReportDocx(template, fixture.data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('Md. Karim') // persons cards (section A)
  expect(xml).toContain('Principal')
  expect(xml).toContain('Welding (SMAW)') // courses cards, linked into attendance
  expect(xml).toContain('Trainers present') // attendance card table column header (was "trainer" choice pre-CHANGE-SET-2)
  expect(xml).toContain('(Optional)') // section K carries the optional tag
  expect(xml).toContain('Not complied') // K compliance: "none" -> tone "no" option label
  expect(xml).toContain('b3261e') // its tone colour
  expect(xml).toContain('Dropout register missing') // K unresolved cards
})

it('longtext fields (e.g. key findings) render as a bordered box, not an inline label:value line', async () => {
  const data = { fields: { key_findings: 'Line one\nLine two' }, checks: {}, cards: {}, flags: [] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('Key findings')
  expect(xml).toContain('Line one')
  expect(xml).toContain('Line two')
  expect(xml).toContain('<w:br/>') // explicit line break between the two lines
})

it('choice fields elsewhere (e.g. overall rating) render as an inline tick-box option row', async () => {
  const data = { fields: { overall_rating: 'satisfactory' }, checks: {}, cards: {}, flags: [] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('Overall rating')
  expect(xml).toContain('Satisfactory')
  expect(xml).toContain('Needs improvement') // unchosen options still print, ☐
  expect(xml).toContain('1c6b38') // chosen option's tone colour
})
