// surprise v2: remark lines + finding candidates must match the shared fixture android's
// SurpriseV2Test.kt is also checked against; plus the v1 -> v2 converter and the two prints.
import { describe, it, expect } from 'vitest'
import v2 from '../../../shared/report-templates/surprise-v2.json'
import fixture from '../../../shared/report-templates/fixtures/remarks-surprise-2.json'
import { buildRemarkLines, fillSays, findingCandidates, parseMajorAnswer } from './sectionremarks.js'
import { compareMismatch, templateFor, needsConversion } from './reporttemplate.js'
import { convertSurpriseV1ToV2, revertSurpriseV2ToV1 } from './reportconvert.js'
import { reportHtml } from './reporthtml.js'
import { narrativeHtml } from './narrativehtml.js'

describe('surprise v2 remarks', () => {
  it('remark lines match the shared fixture', () => {
    for (const section of v2.sections) {
      for (const block of section.blocks) {
        if (block.type !== 'remarks') continue
        expect(buildRemarkLines(v2, section, block, fixture.data), block.key).toEqual(fixture.built[block.key])
      }
    }
  })
  it('finding candidates match the shared fixture', () => {
    expect(findingCandidates(v2, fixture.data)).toEqual(fixture.candidates)
  })
  it('fills optional segments and drops a sentence with a blank key', () => {
    const lookup = (k) => ({ a: 'A', b: '' })[k]
    expect(fillSays('{a}[ y {b}] x', lookup)).toBe('A x.')
    expect(fillSays('{b} x', lookup)).toBe(null)
    expect(fillSays('{a}[ ({a}[ {b}])]', lookup)).toBe('A (A).')
  })
  it('parses the AI major pick', () => {
    expect(parseMajorAnswer('3, 1, 9, 3', 4)).toEqual([2, 0])
    expect(parseMajorAnswer('none', 4)).toBe(null)
    expect(parseMajorAnswer('1. No 3 phase line.\n2. Earthing missing.', 4)).toBe(null)
  })
  it('gap compare warns only on a large shortfall and never prints', () => {
    const compare = v2.sections.find((s) => s.key === 'attendance').blocks[0].compare
    expect(compareMismatch(compare, { present_total: '22', register_avg7: '23.4' })).toBe(false)
    expect(compareMismatch(compare, { present_total: '14', tms_avg7: '24' })).toBe(true)
    expect(compare.print).toBe(false)
  })
})

describe('template versions', () => {
  it('new reports get v2, old rows keep v1', () => {
    expect(templateFor('surprise').version).toBe(2)
    expect(templateFor('surprise', 1).version).toBe(1)
    expect(needsConversion({ type: 'surprise', template_version: 1 })).toBe(true)
    expect(needsConversion({ type: 'surprise', template_version: 2 })).toBe(false)
  })
})

describe('convertSurpriseV1ToV2', () => {
  it('moves v1 answers into v2 and keeps every old key', () => {
    const v1Data = {
      fields: { officers: 'Rafiq (QA Specialist)\nNusrat', key_findings: 'Low attendance\nNo CBLM', instructions_given: 'Fix register', follow_up: 'Full QA visit' },
      checks: { registers_3: { answer: 'no', remarks: 'none kept' } },
      cards: {
        courses: [{ _id: 'c1', course: 'Welding', batch: '07' }],
        attendance: [{ _id: 'attendance:c1', _link: 'c1', course: 'Welding', batch: '07', trainers_present: '1' }],
        graduate: [{ _id: 'g1', batch: 'Welding 05', confirmed: 'same' }],
      },
      flags: ['flag_1'],
    }
    let next = 0
    const out = convertSurpriseV1ToV2(v2, v1Data, () => `id${next++}`)
    expect(out.cards.officers.map((o) => o.name)).toEqual(['Rafiq', 'Nusrat'])
    expect(out.cards.officers[0].designation).toBe('QA Specialist')
    expect(out.checks.followup_1.answer).toBe('no')
    expect(out.cards.trainers[0].present).toBe('1')
    expect(out.cards.graduate[0]).toMatchObject({ course: 'Welding 05', batch: '', confirmed: 'employed' })
    expect(out.findings.map((f) => f.text)).toEqual(['Low attendance', 'No CBLM'])
    expect(out.fields.recommendations).toBe('Fix register\nRecommended follow-up: Full QA visit')
    expect(out.flags).toEqual(['flag_1'])
    expect(v1Data.findings).toBeUndefined() // input untouched

    // revert copies v2 answers back to their v1 places
    out.findings = [{ src: '', text: 'Edited finding' }]
    out.cards.trainers[0].present = '2'
    const back = revertSurpriseV2ToV1(templateFor('surprise', 1), out)
    expect(back.fields.officers).toBe('Rafiq (QA Specialist)\nNusrat')
    expect(back.fields.key_findings).toBe('Edited finding')
    expect(back.checks.registers_3.answer).toBe('no')
    expect(back.cards.attendance[0].trainers_present).toBe('2')
    expect(back.cards.graduate[0]).toMatchObject({ batch: 'Welding 05', course: '', confirmed: 'same' })
  })
})

describe('surprise v2 prints', () => {
  const data = { ...structuredClone(fixture.data), findings: [{ src: '', text: 'Low attendance in EIM 03' }] }
  data.fields.recommendations = 'The institute should fix it.'
  const meta = { officerName: 'Officer', status: 'draft' }
  for (const [name, build] of [['form', reportHtml], ['narrative', narrativeHtml]]) {
    it(`${name}: remarks as bullets, findings numbered, no legend or gap warning`, () => {
      const html = build(v2, data, meta)
      expect(html).toContain('<li class="neg">The training calendar was not displayed')
      expect(html).toContain('<ol class="findings"><li>Low attendance in EIM 03</li></ol>')
      expect(html).toContain('<li>The institute should fix it.</li>')
      expect(html).not.toContain('Training Management System')
      expect(html).not.toContain('Headcount is far below')
    })
  }
})

describe('surprise v2 Word files', async () => {
  const JSZip = (await import('jszip')).default
  const { buildReportDocx } = await import('./reportdocx.js')
  const { buildNarrativeDocx } = await import('./narrativedocx.js')
  const data = { ...structuredClone(fixture.data), findings: [{ src: '', text: 'Low attendance in EIM 03' }] }
  data.fields.recommendations = 'The institute should fix it.'
  const meta = { officerName: 'Officer', status: 'draft' }
  for (const [name, build] of [['form', buildReportDocx], ['narrative', buildNarrativeDocx]]) {
    it(`${name}: has remarks, findings and recommendations, no legend`, async () => {
      const zip = await JSZip.loadAsync(await (await build(v2, data, meta)).arrayBuffer())
      const xml = await zip.file('word/document.xml').async('string')
      expect(xml).toContain('The training calendar was not displayed')
      expect(xml).toContain('Low attendance in EIM 03')
      expect(xml).toContain('The institute should fix it.')
      expect(xml).not.toContain('Training Management System')
    })
  }
})
