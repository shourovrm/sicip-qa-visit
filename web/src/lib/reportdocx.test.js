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

it('choice answers appear as their option label (colour is a run property, not text)', async () => {
  const data = { fields: {}, checks: { arrival_1: { answer: 'yes', remarks: '' } }, cards: {}, flags: [] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('1c6b38') // tone colour run property for a "yes" answer
  expect(xml).toContain('Yes')
})

it('unticked flags do not appear as run text; ticked ones do', async () => {
  const data = { fields: {}, checks: {}, cards: {}, flags: ['flag_3'] }
  const blob = await buildReportDocx(template, data, meta)
  const xml = await documentXml(blob)
  expect(xml).toContain('possible ghost trainees')
  expect(xml).not.toContain('Trainer absent or replaced')
})
