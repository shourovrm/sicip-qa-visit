// same fixture + expectations as android TmsSnapshotBuilderTest (visit date 2026-05-13)
import { describe, expect, it } from 'vitest'
import sample from '../../../shared/report-templates/fixtures/tms-sample.json'
import {
  attendanceDates, attendanceOf, averageNote, buildSnapshot, formatMean, presentCountOf,
} from './tmssnapshot.js'

const VISIT = '2026-05-13'
const data = (endpoint) => sample[endpoint].data
const input = (withAliases = false) => ({
  targets: data('institutetarget/all-list'),
  batches: data('batch/list'),
  summary: data('enrollment/batch_summary'),
  aliases: withAliases ? data('configurations/alias_name/list') : [],
})
// batch id -> Map(date -> present count | null)
function fixtureAttendance() {
  const byBatch = new Map()
  for (const [batchId, days] of Object.entries(sample._date_wise_by_batch)) {
    byBatch.set(Number(batchId), new Map(Object.entries(days).map(([date, reply]) => [date, presentCountOf(reply.data)])))
  }
  return byBatch
}

describe('tms snapshot (android parity)', () => {
  const snapshot = buildSnapshot(input(), VISIT, fixtureAttendance())
  const courseA = snapshot.courses.find((c) => c.name === 'Course A')
  const courseB = snapshot.courses.find((c) => c.name === 'Course B')

  it('course facts come from targets and the latest batch', () => {
    expect(snapshot.courses).toHaveLength(2)
    expect(courseA).toMatchObject({ targetBatches: 10, batchSize: 20, duration: '60 days / 300 h' })
    expect(courseB.duration).toBe('48 days / 240 h')
  })

  it('display name is the longer of course_name and alias', () => {
    const named = buildSnapshot(input(true), VISIT, fixtureAttendance())
    expect(named.courses.map((c) => c.name)).toEqual(['Course A Advanced Diploma', 'Course B'])
    expect(named.runningBatches[0]).toMatchObject({ course: 'Course A Advanced Diploma', tmsCourse: 'Course A' })
  })

  it('cumulative counts parse numbers and strings', () => {
    expect(courseA).toMatchObject({ enrolledTotal: 22, enrolledFemale: 9, certifiedTotal: 8, placedFemale: 2 })
    expect(courseB.enrolledTotal).toBe(10)
  })

  it('dropouts count only ended batches with assessment data', () => {
    expect(courseA).toMatchObject({ dropoutTotal: 2, dropoutFemale: 1 })
    expect(courseB.dropoutTotal).toBeNull()
    expect(courseB.dropoutFemale).toBeNull()
  })

  it('lists only batches running on the visit date, with per-batch counts', () => {
    expect(snapshot.runningBatches).toHaveLength(1)
    expect(snapshot.runningBatches[0]).toMatchObject({
      course: 'Course A', batchNumber: '2', startDate: '2026-04-12', endDate: '2026-07-09', enrolled: 12, female: 5, attendanceToday: 4,
    })
  })

  it('7 class-day mean skips days without rows and keeps its range', () => {
    // class days 13,12,11,10,7,6,5 May -> 29 / 7 = 4.1; 9 and 8 May have no rows
    expect(snapshot.runningBatches[0]).toMatchObject({ attendance7day: 4.1, averageFrom: '2026-05-05', averageTo: '2026-05-13', averageClassDays: 7 })
  })

  it('no attendance rows anywhere gives nulls', () => {
    const empty = buildSnapshot(input(), VISIT, new Map())
    expect(empty.runningBatches[0]).toMatchObject({ attendanceToday: null, attendance7day: null, averageFrom: null, averageClassDays: 0 })
  })

  it('presentCountOf: null for no rows, else is_present count', () => {
    expect(presentCountOf([])).toBeNull()
    expect(presentCountOf(data('trainee/date_wise_attendanceReport'))).toBe(4)
  })
})

describe('7 class-day average rule', () => {
  it('asks for the visit date back 14 calendar days, never before the batch start', () => {
    const dates = attendanceDates('2026-09-28', '2026-01-01')
    expect(dates).toHaveLength(15)
    expect(dates[0]).toBe('2026-09-28')
    expect(dates[14]).toBe('2026-09-14')
    expect(attendanceDates('2026-09-28', '2026-09-26')).toEqual(['2026-09-28', '2026-09-27', '2026-09-26'])
  })

  it('stops at 7 class days and counts fewer when the lookback runs out', () => {
    const counts = new Map([['2026-09-28', 20], ['2026-09-27', 18], ['2026-09-25', 19]])
    const result = attendanceOf(attendanceDates('2026-09-28', '2026-09-20'), counts)
    expect(result).toEqual({ today: 20, mean: 19, from: '2026-09-25', to: '2026-09-28', classDays: 3 })
  })

  it('a visit day without rows still averages the earlier class days', () => {
    const counts = new Map([['2026-09-27', 10], ['2026-09-26', 11]])
    expect(attendanceOf(['2026-09-28', '2026-09-27', '2026-09-26'], counts)).toMatchObject({ today: null, mean: 10.5, classDays: 2 })
  })

  it('formats the mean with one decimal, whole numbers bare', () => {
    expect(formatMean(4.142857)).toBe('4.1')
    expect(formatMean(25)).toBe('25')
    expect(formatMean(null)).toBe('')
  })

  it('writes the range note', () => {
    expect(averageNote({ from: '2026-09-18', to: '2026-09-28', classDays: 7 })).toBe('TMS avg 18–28 Sep 2026 (7 class days)')
    expect(averageNote({ from: '2026-08-28', to: '2026-09-03', classDays: 5 })).toBe('TMS avg 28 Aug – 3 Sep 2026 (5 class days)')
    expect(averageNote({ from: '2025-12-29', to: '2026-01-02', classDays: 4 })).toBe('TMS avg 29 Dec 2025 – 2 Jan 2026 (4 class days)')
    expect(averageNote({ from: '2026-09-28', to: '2026-09-28', classDays: 1 })).toBe('TMS avg 28 Sep 2026 (1 class day)')
    expect(averageNote({ from: null, to: null, classDays: 0 })).toBe('')
  })
})
