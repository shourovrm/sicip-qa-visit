// TMS course/batch/trainee shaping for the surprise report suggestions (made-up names only)
import { describe, it, expect } from 'vitest'
import {
  autoLinkCandidate, batchNumbersFor, buildCourseCatalog, courseBatchOfRef, defaultTranche, findBatch, fullCourseName,
  matchPartner, runningCourseCards, runningBatches, traineeHints,
} from './tmscatalog.js'

const aliases = [{ id: 28, name: 'Electrical Installation & Maintenance' }, { id: 228, name: 'Plumbing and Pipe Fitting' }, { id: 131, name: 'Welding' }]
const targets = [
  { id: 2214, course_name: 'Electrical Installation & Maintenance (EIM)', x_course_name_id: 28 },
  { id: 2218, course_name: 'PPF', x_course_name_id: 228 },
]
const batches = [
  { id: 664, course_info_id: 2218, batch_number: '1', start_date: '2025-07-01', end_date: '2025-10-31' },
  { id: 7483, course_info_id: 2218, batch_number: 4, start_date: '2026-07-01', end_date: '2026-10-31' },
  { id: 5336, course_info_id: 2218, batch_number: '3', start_date: '2026-03-01', end_date: '2026-06-30' },
  { id: 7549, course_info_id: 2214, batch_number: '3', start_date: '2026-07-01', end_date: '2026-10-31' },
  { id: 9001, course_info_id: 2300, batch_number: '1', start_date: '2026-09-01', end_date: '2026-12-31', course_info: { course_name: 'Advanced Welding', x_course_name_id: 131 } },
]
const catalog = buildCourseCatalog(targets, batches, aliases)

describe('fullCourseName', () => {
  it('keeps the longer of the TMS name and its alias', () => {
    expect(fullCourseName('PPF', 'Plumbing and Pipe Fitting')).toBe('Plumbing and Pipe Fitting')
    expect(fullCourseName('Advanced Welding', 'Welding')).toBe('Advanced Welding')
    expect(fullCourseName('EIM', undefined)).toBe('EIM')
  })
})

describe('buildCourseCatalog', () => {
  it('names courses from the targets and falls back to the batch course_info', () => {
    expect(catalog.courseNames).toEqual(['Electrical Installation & Maintenance (EIM)', 'Plumbing and Pipe Fitting', 'Advanced Welding'])
    expect(catalog.batches.find((b) => b.id === 9001).courseName).toBe('Advanced Welding')
    expect(catalog.batches.find((b) => b.id === 7483)).toMatchObject({ courseId: 2218, number: '4', names: ['Plumbing and Pipe Fitting', 'PPF'] })
  })
})

describe('runningBatches', () => {
  it('keeps batches whose start <= visit date <= end, in catalog course order', () => {
    expect(runningBatches(catalog, '2026-09-28').map((b) => b.id)).toEqual([7549, 7483, 9001])
    expect(runningBatches(catalog, '2026-10-31').map((b) => b.id)).toEqual([7549, 7483, 9001])
    expect(runningBatches(catalog, '2026-11-01').map((b) => b.id)).toEqual([9001])
    expect(runningBatches(catalog, '')).toEqual([])
  })
  it('becomes section A course cards', () => {
    let next = 0
    const cards = runningCourseCards(catalog, '2026-09-28', () => `id${next++}`)
    expect(cards).toEqual([
      { _id: 'id0', course: 'Electrical Installation & Maintenance (EIM)', batch: '3' },
      { _id: 'id1', course: 'Plumbing and Pipe Fitting', batch: '4' },
      { _id: 'id2', course: 'Advanced Welding', batch: '1' },
    ])
  })
})

describe('batchNumbersFor / findBatch', () => {
  it('lists every batch of a course, newest first, matching the name loosely', () => {
    expect(batchNumbersFor(catalog, 'plumbing and pipe fitting ')).toEqual(['4', '3', '1'])
    expect(batchNumbersFor(catalog, 'PPF')).toEqual(['4', '3', '1']) // TMS short name
    expect(batchNumbersFor(catalog, 'Electrical Installation & Maintenance')).toEqual(['3']) // alias
    expect(batchNumbersFor(catalog, 'Welding')).toEqual(['1']) // alias of a non-target course
    expect(batchNumbersFor(catalog, 'Electrical Installation and Maintenance')).toEqual(['3']) // "and" for "&", no code
    expect(batchNumbersFor(catalog, '')).toEqual([])
  })
  it('finds one batch by course name + number', () => {
    expect(findBatch(catalog, 'Plumbing and Pipe Fitting', '04')?.id).toBe(7483)
    expect(findBatch(catalog, 'Plumbing and Pipe Fitting', '9')).toBe(null)
  })
})

describe('courseBatchOfRef', () => {
  it('splits "Course · N" from a courseRef value', () => {
    expect(courseBatchOfRef('Plumbing and Pipe Fitting · 4')).toEqual({ course: 'Plumbing and Pipe Fitting', batch: '4' })
    expect(courseBatchOfRef('Welding')).toEqual({ course: 'Welding', batch: '' })
  })
})

describe('traineeHints', () => {
  it('keeps name and mobile only, drops nameless rows', () => {
    const rows = [
      { present: 3, trainee: { trainee_name: 'Test Trainee One', mobile: '01700000001', nid: 'x', father_name: 'y' } },
      { trainee: { trainee_name: '', mobile: '01700000002' } },
      { trainee: { trainee_name: 'Test Trainee Two' } },
    ]
    expect(traineeHints(rows)).toEqual([{ name: 'Test Trainee One', mobile: '01700000001' }, { name: 'Test Trainee Two', mobile: '' }])
  })
})

describe('link helpers', () => {
  it('matches the partner by short name and the institute by exact name', () => {
    const entities = [{ id: 5, entity_short_name: 'BACI' }, { id: 6, entity_short_name: 'BAB' }]
    expect(matchPartner(entities, ' baci ')?.id).toBe(5)
    const institutes = [{ id: 1, institute_name: 'Alpha Training Centre', short_name: 'ATC' }, { id: 2, institute_name: 'Beta Institute', short_name: 'BI' }]
    expect(autoLinkCandidate(institutes, 'alpha training centre')?.id).toBe(1)
    expect(autoLinkCandidate(institutes, 'BI')?.id).toBe(2)
    expect(autoLinkCandidate(institutes, 'Gamma')).toBe(null)
  })
  it('picks the newest active tranche', () => {
    expect(defaultTranche([{ id: 1, active_status: 1 }, { id: 2, active_status: 0 }])?.id).toBe(1)
    expect(defaultTranche([{ id: 1, active_status: 0 }, { id: 3, active_status: 0 }])?.id).toBe(3)
  })
})
