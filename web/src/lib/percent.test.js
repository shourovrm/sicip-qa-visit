// QA 1.50 "with Percentage": same cases as android (shared fixture)
import { describe, expect, it } from 'vitest'
import fixture from '../../../shared/report-templates/fixtures/qa-percent-1.json'
import { countWithPercent, percentFields, percentOnly } from './percent.js'
import { templateFor } from './reporttemplate.js'

describe('countWithPercent', () => {
  for (const c of fixture.cases) {
    it(`${JSON.stringify(c.count)} of ${JSON.stringify(c.base)} -> ${JSON.stringify(c.printed)}`, () => {
      expect(countWithPercent(c.count, c.base)).toBe(c.printed)
    })
  }

  it('the qa-v2 1.50 card prints placed % of certified and dropouts % of enrolled', () => {
    const cumulative = templateFor('qa').sections.flatMap((s) => s.blocks).find((b) => b.key === 'cumulative')
    const printed = Object.fromEntries(percentFields(cumulative).map((f) => [f.key, countWithPercent(fixture.card.fields[f.key], fixture.card.fields[f.percentOf])]))
    expect(printed).toEqual(fixture.card.printed)
  })

  it('editor hint: the percent alone, "" when it cannot be worked out', () => {
    expect(percentOnly('12', '25')).toBe('48%')
    expect(percentOnly('12', '0')).toBe('')
    expect(percentOnly('', '25')).toBe('')
  })
})
