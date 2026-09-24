import { describe, it, expect } from 'vitest'
import { templateFor, computeProgress, syncLinks, newReportData } from './reporttemplate.js'
import fixture from '../../../shared/report-templates/fixtures/progress-1.json'

// deep clone -- syncLinks mutates in place, and every test needs its own untouched copy of the
// fixture's before/expected objects.
const clone = (x) => JSON.parse(JSON.stringify(x))

describe('syncLinks', () => {
  it('matches the shared fixture exactly (android parity): before_sync -> synced', () => {
    const template = templateFor('surprise')
    const result = syncLinks(template, clone(fixture.before_sync))
    expect(result).toEqual(fixture.synced)
  })

  it('is idempotent -- syncing an already-synced report changes nothing', () => {
    const template = templateFor('surprise')
    const once = syncLinks(template, clone(fixture.synced))
    const twice = syncLinks(template, clone(once))
    expect(twice).toEqual(once)
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
    expect(data.checks).toEqual({})
    expect(data.flags).toEqual([])
  })

  it('a fresh report has no linked attendance cards yet (courses start blank)', () => {
    const template = templateFor('surprise')
    const data = newReportData(template, { institute: 'X', start_date: '2026-01-01' }, 'Officer')
    expect(data.cards.attendance).toEqual([])
  })
})
