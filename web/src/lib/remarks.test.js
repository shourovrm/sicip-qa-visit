import { describe, it, expect } from 'vitest'
import { buildRemarks, printedRemarks, ensureStop, isEligibleForAiRemarks, outputKeepsNumbers } from './remarks.js'
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

describe('isEligibleForAiRemarks', () => {
  const item = { id: 'x', options: [{ id: 'a', label: 'A', seen: 'A is fine.', not: 'A is not fine.', na: '' }] }

  it('is false with nothing entered (no bullets)', () => {
    expect(isEligibleForAiRemarks(item, {})).toBe(false)
  })

  it('is false for a single tapped option with no typed text and no ai yet', () => {
    expect(isEligibleForAiRemarks(item, { opts: { a: { v: 'seen' } } })).toBe(false)
  })

  it('is true once the officer typed a remark, even with only one bullet', () => {
    expect(isEligibleForAiRemarks(item, { opts: { a: { v: 'seen', remark: 'looked fine' } } })).toBe(true)
  })

  it('is true once the officer typed a detail', () => {
    expect(isEligibleForAiRemarks(item, { opts: { a: { v: 'seen', detail: '12/02/2026' } } })).toBe(true)
  })

  it('is true once the officer typed evidence or a note', () => {
    expect(isEligibleForAiRemarks(item, { opts: { a: { v: 'seen' } }, evidence: 'Photo seen' })).toBe(true)
    expect(isEligibleForAiRemarks(item, { opts: { a: { v: 'seen' } }, note: 'All good' })).toBe(true)
  })

  it('is true with 3+ bullets even without any typed text', () => {
    const threeOptItem = { id: 'y', options: [{ id: 'a', seen: 'A.', not: 'not A.', na: '' }, { id: 'b', seen: 'B.', not: 'not B.', na: '' }, { id: 'c', seen: 'C.', not: 'not C.', na: '' }] }
    expect(isEligibleForAiRemarks(threeOptItem, { opts: { a: { v: 'seen' }, b: { v: 'seen' }, c: { v: 'seen' } } })).toBe(true)
  })

  it('is false when the ai result is already fresh (source matches current bullets)', () => {
    const entry = { opts: { a: { v: 'seen', remark: 'ok' } } }
    const bullets = buildRemarks(item, entry)
    entry.ai = { source: bullets.join('\n'), text: 'AI text.' }
    expect(isEligibleForAiRemarks(item, entry)).toBe(false)
  })

  it('is true again once the ai result goes stale (an edit after the ai ran)', () => {
    const entry = { opts: { a: { v: 'seen', remark: 'ok' } }, ai: { source: 'stale bullets', text: 'AI text.' } }
    expect(isEligibleForAiRemarks(item, entry)).toBe(true)
  })
})

describe('outputKeepsNumbers', () => {
  it('passes when every output number appears in the input', () => {
    expect(outputKeepsNumbers('last examined on 12/02/2026, 3 kits', 'Examined 12 02 2026, 3 kits seen')).toBe(true)
  })

  it('fails when the output invents or changes a number', () => {
    expect(outputKeepsNumbers('3 fire extinguishers seen', '5 fire extinguishers seen')).toBe(false)
  })

  it('passes trivially when neither input nor output has any digits', () => {
    expect(outputKeepsNumbers('no numbers here', 'still none here')).toBe(true)
  })
})
