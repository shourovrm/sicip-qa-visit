import { describe, it, expect } from 'vitest'
import { buildRemarks, printedRemarks, ensureStop } from './remarks.js'
import fixture from '../../../shared/report-templates/fixtures/remarks-1.json'

describe('ensureStop', () => {
  it('leaves an empty string empty', () => {
    expect(ensureStop('')).toBe('')
  })
  it('appends a period when there is no terminal punctuation', () => {
    expect(ensureStop('No waiting list was needed')).toBe('No waiting list was needed.')
  })
  it('leaves existing . ! or ? alone', () => {
    expect(ensureStop('Is this correct?')).toBe('Is this correct?')
    expect(ensureStop('Already done.')).toBe('Already done.')
    expect(ensureStop('Watch out!')).toBe('Watch out!')
  })
})

describe('buildRemarks / printedRemarks (fixture parity with reference.py)', () => {
  for (const testCase of fixture) {
    it(testCase.case, () => {
      expect(buildRemarks(testCase.item, testCase.entry)).toEqual(testCase.bullets)
      expect(printedRemarks(testCase.item, testCase.entry)).toEqual(testCase.printed)
    })
  }
})
