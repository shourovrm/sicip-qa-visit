// qa-v2 tick boxes: must match shared/report-templates/fixtures/qa-2.json, which
// android's RemarksQa2Test.kt is checked against too
import { describe, it, expect } from 'vitest'
import fixture from '../../../shared/report-templates/fixtures/qa-2.json'
import qaV2 from '../../../shared/report-templates/qa-v2.json'
import { buildRemarks, printedRemarks } from './remarks.js'
import { componentNotes } from './drafts.js'

describe('qa-v2 tick remarks', () => {
  for (const c of fixture.cases) {
    it(c.case, () => {
      expect(buildRemarks(c.item, c.entry)).toEqual(c.bullets)
      expect(printedRemarks(c.item, c.entry)).toEqual(c.printed)
    })
  }
  it('component notes use the ticks sentence', () => {
    expect(componentNotes(qaV2, fixture.data, 's6')).toEqual(fixture.component_s6)
  })
})
