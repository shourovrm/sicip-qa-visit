import { describe, it, expect } from 'vitest'
import { templateFor, dbTypeFor, computeProgress, normalize, newReportData } from './reporttemplate.js'
import fixture from '../../../shared/report-templates/fixtures/progress-1.json'
import qaProgressFixture from '../../../shared/report-templates/fixtures/progress-qa-1.json'

// deep clone -- normalize mutates in place, and every test needs its own untouched copy of the
// fixture's before/expected objects.
const clone = (x) => JSON.parse(JSON.stringify(x))

describe('normalize', () => {
  it('matches the shared fixture exactly (android parity): before_sync -> synced', () => {
    const template = templateFor('surprise')
    const result = normalize(template, clone(fixture.before_sync))
    expect(result).toEqual(fixture.synced)
  })

  it('is idempotent -- normalizing an already-normalized report changes nothing', () => {
    const template = templateFor('surprise')
    const once = normalize(template, clone(fixture.synced))
    const twice = normalize(template, clone(once))
    expect(twice).toEqual(once)
  })

  it('leaves a perCourse item as a plain single answer with 0-1 named courses', () => {
    const template = templateFor('surprise')
    const data = normalize(template, {
      fields: {}, checks: { arrival_1: { answer: 'yes', remarks: '' } },
      cards: { courses: [{ _id: 'c1', course: 'Welding', batch: '07' }] }, flags: [],
    })
    // only one named course -- syncPerCourse is a no-op, the single answer survives untouched
    expect(data.checks.arrival_1).toEqual({ answer: 'yes', remarks: '' })
  })

  it('derives a mixed per-course answer as partial and flags no matter what the derived answer is', () => {
    const template = templateFor('surprise')
    const data = normalize(template, {
      fields: {}, checks: { arrival_1: { answer: '', remarks: '', courses: { c1: 'no', c2: 'yes' } } },
      cards: { courses: [{ _id: 'c1', course: 'Welding', batch: '07' }, { _id: 'c2', course: 'Electrical', batch: '03' }] },
      flags: [],
    })
    expect(data.checks.arrival_1.answer).toBe('partial')
    const progress = computeProgress(template, data)
    expect(progress.sections.arrival.flagged).toBe(true) // "no" is in the mix even though the derived answer isn't
  })
})

describe('computeProgress', () => {
  it('matches the shared fixture exactly (android parity), including customFlags', () => {
    const template = templateFor('surprise')
    expect(computeProgress(template, fixture.data)).toEqual(fixture.expected)
  })

  it('an empty report has nothing answered and nothing flagged', () => {
    const template = templateFor('surprise')
    const progress = computeProgress(template, { fields: {}, checks: {}, cards: {}, flags: [] })
    expect(progress.sectionsDone).toBe(0)
    expect(progress.unansweredCount).toBeGreaterThan(0)
    expect(progress.flagsTicked).toEqual([])
    expect(progress.customFlags).toEqual([])
    expect(Object.values(progress.answerCounts).every((n) => n === 0)).toBe(true)
  })

  it('excludes an optional section (K, followup) from sectionsCounted/sectionsDone even when answered', () => {
    const template = templateFor('surprise')
    const progress = computeProgress(template, fixture.data)
    expect(template.sections.find((s) => s.key === 'followup').optional).toBe(true)
    expect(progress.sections.followup.total).toBeGreaterThan(0) // has real questions...
    expect(progress.sections.followup.done).toBe(true) // ...and is fully answered in the fixture
    // ...yet every non-optional section with total > 0 is what's actually counted
    const nonOptionalWithTotal = template.sections.filter((s) => !s.optional && progress.sections[s.key].total > 0)
    expect(progress.sectionsCounted).toBe(nonOptionalWithTotal.length)
    expect(nonOptionalWithTotal.some((s) => s.key === 'followup')).toBe(false)
  })

  it('never hardcodes the checklist item count -- section I is cards-only, not a checklist', () => {
    const template = templateFor('surprise')
    const interviewSection = template.sections.find((s) => s.key === 'interview')
    expect(interviewSection.blocks.every((b) => b.type !== 'checklist')).toBe(true)
    const checklistItemCount = template.sections
      .flatMap((s) => s.blocks)
      .filter((b) => b.type === 'checklist')
      .reduce((sum, b) => sum + b.items.length, 0)
    expect(checklistItemCount).toBe(31)
  })
})

describe('newReportData', () => {
  it('prefills fields from the visit and officer, seeds cards with `start` blank rows carrying an _id', () => {
    const template = templateFor('surprise')
    const visit = { institute: 'Bangladesh-Korea TTC', association: 'FLAXA', start_date: '2026-09-24' }
    const data = newReportData(template, visit, 'Jane Officer')
    expect(data.fields.ti_name).toBe('Bangladesh-Korea TTC')
    expect(data.fields.provider).toBe('FLAXA')
    expect(data.fields.visit_date).toBe('2026-09-24')
    expect(data.fields.officers).toBe('Jane Officer')
    expect(data.cards.persons).toHaveLength(1) // start: 1
    expect(data.cards.courses).toHaveLength(1) // start: 1
    expect(data.cards.identity).toHaveLength(5) // start: 5
    for (const card of [...data.cards.persons, ...data.cards.courses, ...data.cards.identity]) {
      expect(typeof card._id).toBe('string')
      expect(card._id.length).toBeGreaterThan(0)
    }
    expect(data.checks).toEqual({}) // 0-1 named courses yet -- normalize's per-course step is a no-op
    expect(data.flags).toEqual([])
  })

  it('a fresh report has no linked attendance/interviews cards yet (courses start blank)', () => {
    const template = templateFor('surprise')
    const data = newReportData(template, { institute: 'X', start_date: '2026-01-01' }, 'Officer')
    expect(data.cards.attendance).toEqual([])
    expect(data.cards.interviews).toEqual([])
  })
})

describe('qa template', () => {
  it('templateFor resolves both "qa" (template id) and "monitoring" (DB type)', () => {
    const byId = templateFor('qa')
    expect(byId.id).toBe('qa')
    expect(templateFor('monitoring')).toBe(byId)
  })

  it('dbTypeFor maps the qa template id to the DB "monitoring" type, everything else passes through', () => {
    expect(dbTypeFor('qa')).toBe('monitoring')
    expect(dbTypeFor('surprise')).toBe('surprise')
  })

  it('computeProgress on criteria sections matches the shared fixture exactly (android parity)', () => {
    const template = templateFor('qa')
    expect(computeProgress(template, qaProgressFixture.data)).toEqual(qaProgressFixture.expected)
  })

  it('an untouched criteria section has total > 0 and done: false', () => {
    const template = templateFor('qa')
    const progress = computeProgress(template, { fields: {}, checks: {}, cards: {}, flags: [], criteria: {} })
    expect(progress.sections.s5.total).toBeGreaterThan(0)
    expect(progress.sections.s5.done).toBe(false)
    expect(progress.sections.s5.notSeen).toBeUndefined()
  })

  it('section 8 sums options across both its criteria blocks (main table + 8.1 sub-table)', () => {
    const template = templateFor('qa')
    const s8 = template.sections.find((s) => s.key === 's8')
    expect(s8.blocks.filter((b) => b.type === 'criteria')).toHaveLength(2)
  })
})
