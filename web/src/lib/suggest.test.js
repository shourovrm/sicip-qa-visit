// field "suggest" resolution for the surprise report (made-up names only)
import { describe, it, expect } from 'vitest'
import { buildCourseCatalog } from './tmscatalog.js'
import { mergeOptions, phoneFill, suggestionsFor, traineeBatchOf, traineeKey } from './suggest.js'

const catalog = buildCourseCatalog(
  [{ id: 2218, course_name: 'Plumbing and Pipe Fitting' }],
  [
    { id: 664, course_info_id: 2218, batch_number: '1', start_date: '2025-07-01', end_date: '2025-10-31' },
    { id: 7483, course_info_id: 2218, batch_number: '4', start_date: '2026-07-01', end_date: '2026-10-31' },
  ],
  [],
)
const hints = [{ name: 'Test Trainee One', mobile: '01700000001' }, { name: 'Test Trainee Two', mobile: '' }]
const trainers = [
  { name: 'Test Trainer Elsewhere', designation: '', courseIds: [], masterBatchIds: [], associateBatchIds: [] },
  { name: 'Test Trainer Batch Four', designation: '', courseIds: [2218], masterBatchIds: [7483], associateBatchIds: [] },
]
const context = { catalog, trainees: new Map([['2218:7483', hints]]), trainers, equipment: ['Grinder', 'Pipe threader'] }

describe('traineeBatchOf', () => {
  it('reads course + batch fields (L) or a courseRef "Course · N" (D)', () => {
    expect(traineeBatchOf({ course: 'Plumbing and Pipe Fitting', batch: '4' }, catalog)?.id).toBe(7483)
    expect(traineeBatchOf({ batch: 'Plumbing and Pipe Fitting · 1' }, catalog)?.id).toBe(664)
    expect(traineeBatchOf({ batch: 'Unknown · 1' }, catalog)).toBe(null)
    expect(traineeKey(traineeBatchOf({ batch: 'Plumbing and Pipe Fitting · 4' }, catalog))).toBe('2218:7483')
  })
})

describe('suggestionsFor', () => {
  it('resolves each suggest kind', () => {
    expect(suggestionsFor({ suggest: 'tmsCourse' }, {}, context)).toEqual(['Plumbing and Pipe Fitting'])
    expect(suggestionsFor({ suggest: 'tmsBatch' }, { course: 'plumbing and pipe fitting' }, context)).toEqual(['4', '1'])
    expect(suggestionsFor({ suggest: 'tmsTrainee' }, { batch: 'Plumbing and Pipe Fitting · 4' }, context)).toEqual(['Test Trainee One', 'Test Trainee Two'])
    expect(suggestionsFor({ suggest: 'tmsTrainer' }, { batch: 'Plumbing and Pipe Fitting · 4' }, context)).toEqual(['Test Trainer Batch Four', 'Test Trainer Elsewhere'])
    expect(suggestionsFor({ suggest: 'shared:equipment' }, {}, context)).toEqual(['Grinder', 'Pipe threader'])
  })
  it('gives nothing without TMS data or a suggest key', () => {
    const empty = { catalog: null, trainees: new Map(), equipment: [] }
    expect(suggestionsFor({ suggest: 'tmsCourse' }, {}, empty)).toEqual([])
    expect(suggestionsFor({ suggest: 'tmsTrainee' }, { batch: 'X · 1' }, empty)).toEqual([])
    expect(suggestionsFor({}, {}, context)).toEqual([])
  })
})

describe('phoneFill', () => {
  it('fills an empty phone from the picked trainee only', () => {
    const fields = [{ key: 'name', suggest: 'tmsTrainee' }, { key: 'phone' }]
    expect(phoneFill(fields, { phone: '' }, 'name', 'Test Trainee One', hints)).toBe('01700000001')
    expect(phoneFill(fields, { phone: '0180' }, 'name', 'Test Trainee One', hints)).toBe(null)
    expect(phoneFill(fields, { phone: '' }, 'name', 'Someone else', hints)).toBe(null)
    expect(phoneFill([{ key: 'name', suggest: 'tmsTrainee' }], {}, 'name', 'Test Trainee One', hints)).toBe(null)
    expect(phoneFill(fields, { phone: '' }, 'name', 'Test Trainee Two', hints)).toBe(null)
  })
})

describe('mergeOptions', () => {
  it('keeps order, drops blanks and case-insensitive repeats', () => {
    expect(mergeOptions(['EIM', ' '], ['eim', 'PPF'])).toEqual(['EIM', 'PPF'])
  })
})
