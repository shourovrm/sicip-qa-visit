// labelled suggestion options (made-up names only)
import { describe, it, expect } from 'vitest'
import { instituteOptions, labelled, mergeLabelled, sourceLabelFor } from './suggestoptions.js'

describe('sourceLabelFor', () => {
  it('names the source of each suggest kind', () => {
    expect(sourceLabelFor('tmsCourse')).toBe('TMS')
    expect(sourceLabelFor('tmsTrainee')).toBe('TMS')
    expect(sourceLabelFor('shared:equipment')).toBe('Shared')
    expect(sourceLabelFor(undefined)).toBe('')
  })
})

describe('mergeLabelled', () => {
  it('keeps order, drops blanks and case repeats, first label wins', () => {
    const merged = mergeLabelled(labelled(['Alpha', ' '], 'TMS'), labelled(['alpha', 'Beta'], 'Past visits'))
    expect(merged).toEqual([{ value: 'Alpha', label: 'TMS' }, { value: 'Beta', label: 'Past visits' }])
  })
})

describe('instituteOptions', () => {
  const tms = [{ institute_name: 'Zeta Institute' }, { institute_name: 'Alpha Institute' }, { institute_name: '' }]
  it('lists TMS institutes first (sorted), then past visits (sorted)', () => {
    expect(instituteOptions(tms, ['Mid Place', 'Beta Place'])).toEqual([
      { value: 'Alpha Institute', label: 'TMS' },
      { value: 'Zeta Institute', label: 'TMS' },
      { value: 'Beta Place', label: 'Past visits' },
      { value: 'Mid Place', label: 'Past visits' },
    ])
  })
  it('lists an institute once, as TMS, when past visits also name it', () => {
    expect(instituteOptions(tms, ['alpha institute'])).toEqual([
      { value: 'Alpha Institute', label: 'TMS' },
      { value: 'Zeta Institute', label: 'TMS' },
    ])
  })
  it('falls back to past visits alone without TMS', () => {
    expect(instituteOptions(null, ['B', 'A', 'A'])).toEqual([{ value: 'A', label: 'Past visits' }, { value: 'B', label: 'Past visits' }])
  })
})
