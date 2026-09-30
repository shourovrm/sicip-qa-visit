// qa-v2: showIf progress (shared fixture qa-2.json, android QaV2Test.kt checks the same file),
// evidence numbering, v1 -> v2 convert
import { describe, it, expect } from 'vitest'
import qaV1 from '../../../shared/report-templates/qa-v1.json'
import qaV2 from '../../../shared/report-templates/qa-v2.json'
import fixture from '../../../shared/report-templates/fixtures/qa-2.json'
import { computeProgress, isShown, needsConversion } from './reporttemplate.js'
import { criteriaPath, evidenceSuggestions, itemEvidence, usedEvidence, withEvidenceAdded, withEvidenceRemoved } from './evidence.js'
import { convertQaV1ToV2 } from './qaconvert.js'

const counter = () => {
  let n = 0
  return () => `id-${++n}`
}
const find = (sectionKey, itemId) => {
  const section = qaV2.sections.find((s) => s.key === sectionKey)
  const block = section.blocks.find((b) => b.type === 'criteria')
  return [section, block, block.items.find((i) => i.id === itemId)]
}

describe('qa-v2 showIf', () => {
  it('hidden fields and blocks do not count (fixture)', () => {
    expect(computeProgress(qaV2, fixture.profile).sections.s1).toEqual(fixture.progress_s1)
  })
  it('card fields follow their own card', () => {
    const other = qaV2.sections[0].blocks.find((b) => b.key === 'mous').fields.find((f) => f.key === 'partner_other')
    expect(isShown(other, { partner: 'Others' })).toBe(true)
    expect(isShown(other, { partner: 'BGMEA' })).toBe(false)
  })
})

describe('qa-v2 evidence register', () => {
  it('numbers by position, reuses the same name with its first number', () => {
    const newId = counter()
    expect(criteriaPath(...find('s7', 's7_1b'))).toBe('7.1b')
    expect(criteriaPath(...find('s2', 's2_3'))).toBe('2.3')
    let data = { criteria: {} }
    data = withEvidenceAdded(data, '7.1b', 's7_1b', 'Trainers list', newId)
    data = withEvidenceAdded(data, '7.1b', 's7_1b', 'Appointment letters', newId)
    data = withEvidenceAdded(data, '8.2', 's8_2', ' trainers LIST ', newId)
    expect(itemEvidence(data, 's7_1b').map((e) => e.no)).toEqual(['7.1b.a', '7.1b.b'])
    expect(itemEvidence(data, 's8_2')).toEqual([{ _id: 'id-1', name: 'Trainers list', no: '7.1b.a' }])
    // removing from the first place keeps the number for the other
    data = withEvidenceRemoved(data, 's7_1b', 'id-1')
    expect(usedEvidence(data).map((e) => e.no)).toEqual(['7.1b.a', '7.1b.b'])
    data = withEvidenceAdded(data, '7.1b', 's7_1b', 'Pay slips', newId)
    expect(itemEvidence(data, 's7_1b').map((e) => e.no)).toEqual(['7.1b.b', '7.1b.c'])
  })
  it('suggests register, app tables and Word tables, minus what the item has', () => {
    let data = { criteria: {} }
    data = withEvidenceAdded(data, '2.3', 's2_3', 'Monitoring logbook', counter())
    const suggestions = evidenceSuggestions(qaV2, data, 's2_3')
    expect(suggestions).not.toContain('Monitoring logbook')
    expect(suggestions).toContain('Table 1.20: Contract/MoU information')
    expect(suggestions).toContain('Trainers list')
    expect(evidenceSuggestions(qaV2, data, 's8_2', 'logbook')).toEqual(['Monitoring logbook'])
  })
})

describe('qa v1 -> v2 convert', () => {
  const v1Data = {
    fields: {
      officers: 'R. M. Shourov, Program Officer (QA)\nS. Akter (Program Officer)',
      status: 'BTEB registered', contract_with: 'bgmea', signed_date: '2026-01-10', mou_target: '120',
      other_contract: 'yes', other_orgs: 'ILO', other_courses: 'Welding\nTailoring', overlapping_courses: 'Welding\nIT support',
      same_facilities: 'Same workshop',
    },
    cards: { persons: [], plan: [{ _id: 'p1', weakness: 'x' }] },
    criteria: {
      s2_1: { opts: { visit_log: { v: 'seen' }, self_appraisal: { v: 'not' } }, evidence: 'Visit register; Logbook' },
      s3_1: { opts: { ttm_registers: { v: 'not', remark: 'no fuel register' } } },
      s4_2: { opts: { applicants_list: { v: 'seen', detail: '85' } } },
      s6_2: { opts: { cblm: { v: 'seen' }, tdp: { v: 'seen' }, job_sheet: { v: 'not' }, learning_materials: { v: 'seen' } } },
      s8_6: { opts: { monitoring_logbook: { v: 'seen' } }, note: 'Kept by principal' },
    },
  }
  const data = convertQaV1ToV2(qaV2, v1Data, counter())

  it('is offered for v1 QA rows only', () => {
    expect(needsConversion({ type: 'monitoring', template_version: 1 })).toBe(true)
    expect(needsConversion({ type: 'monitoring', template_version: 2 })).toBe(false)
    expect(qaV1.version).toBe(1)
  })
  it('moves the profile', () => {
    expect(data.cards.officers.map(({ name, designation }) => [name, designation])).toEqual([
      ['R. M. Shourov', 'Program Officer (QA)'], ['S. Akter', 'Program Officer'],
    ])
    expect(data.fields.other_registration).toBe('BTEB registered')
    expect(data.cards.mous[0]).toMatchObject({ partner: 'BGMEA', signed_date: '2026-01-10', target: '120' })
    expect(data.cards.contracts.map((c) => c.organisation)).toEqual(['ILO'])
    expect(data.cards.contract_courses.map(({ contract, course, overlap }) => [contract, course, overlap])).toEqual([
      ['ILO', 'Welding', 'yes'], ['ILO', 'Tailoring', ''], ['ILO', 'IT support', 'yes'],
    ])
    expect(data.fields.comments).toBe('Facilities: Same workshop')
    expect(data.cards.selection[0].applicants).toBe('85')
  })
  it('moves criteria answers, ticks, notes and evidence', () => {
    expect(data.criteria.s2_3.opts).toEqual({ visit_log: { v: 'seen' }, monitoring_logbook: { v: 'seen' } })
    expect(data.criteria.s2_3.note).toBe('Kept by principal')
    expect(data.criteria.s8_5.opts.stock_register).toEqual({ v: 'not', remark: 'no fuel register' })
    expect(data.criteria.s8_6.opts.fuel_register.v).toBe('not')
    expect(data.criteria.s6_2.ticks).toEqual(['cblm', 'tdp'])
    expect(data.criteria.s6_2.opts.learning_materials.v).toBe('seen')
    expect(itemEvidence(data, 's2_1').map((e) => `${e.no} ${e.name}`)).toEqual(['2.1.a Visit register', '2.1.b Logbook'])
    expect(data.criteriaV1.s2_1.opts.self_appraisal.v).toBe('not')
    expect(data.cards.plan).toHaveLength(1)
  })
})
