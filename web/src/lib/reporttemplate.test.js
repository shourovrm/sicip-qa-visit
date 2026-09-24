import { describe, it, expect } from 'vitest'
import { templateFor, computeProgress, newReportData } from './reporttemplate.js'
import fixture from '../../../shared/report-templates/fixtures/progress-1.json'

describe('computeProgress', () => {
  it('matches the shared fixture exactly (android parity)', () => {
    const template = templateFor('surprise')
    expect(computeProgress(template, fixture.data)).toEqual(fixture.expected)
  })

  it('an empty report has nothing answered and nothing flagged', () => {
    const template = templateFor('surprise')
    const progress = computeProgress(template, { fields: {}, checks: {}, cards: {}, flags: [] })
    expect(progress.sectionsDone).toBe(0)
    expect(progress.unansweredCount).toBeGreaterThan(0)
    expect(progress.flagsTicked).toEqual([])
    expect(Object.values(progress.answerCounts).every((n) => n === 0)).toBe(true)
  })
})

describe('newReportData', () => {
  it('prefills fields from the visit and officer, seeds cards with `start` blank rows', () => {
    const template = templateFor('surprise')
    const visit = { institute: 'Bangladesh-Korea TTC', association: 'FLAXA', start_date: '2026-09-24' }
    const data = newReportData(template, visit, 'Jane Officer')
    expect(data.fields.ti_name).toBe('Bangladesh-Korea TTC')
    expect(data.fields.provider).toBe('FLAXA')
    expect(data.fields.visit_date).toBe('2026-09-24')
    expect(data.fields.officers).toBe('Jane Officer')
    expect(data.cards.attendance).toEqual([{}, {}]) // start: 2
    expect(data.cards.identity).toEqual([{}, {}, {}, {}, {}]) // start: 5
    expect(data.checks).toEqual({})
    expect(data.flags).toEqual([])
  })
})
