import { describe, expect, it } from 'vitest'
import { templateFor } from './reporttemplate.js'
import { applyTmsPrefill, tmsRowValues, useTmsValue } from './tmsprefill.js'

const surprise = templateFor('surprise')
const qa = templateFor('qa')

const snapshot = {
  courses: [
    { name: 'Plumbing and Pipe Fitting', tmsName: 'PPF', duration: '60 days / 300 h', batchSize: 25, targetBatches: 105,
      enrolledTotal: 100, enrolledFemale: 10, certifiedTotal: 75, certifiedFemale: 8, placedTotal: 50, placedFemale: 5, dropoutTotal: 2, dropoutFemale: null },
  ],
  runningBatches: [
    { course: 'Plumbing and Pipe Fitting', tmsCourse: 'PPF', batchNumber: '4', startDate: '2026-09-07', endDate: '2026-11-24',
      enrolled: 25, female: 3, attendanceToday: 20, attendance7day: 18.4, averageFrom: '2026-09-18', averageTo: '2026-09-28', averageClassDays: 7 },
  ],
}
const tmsData = { snapshot, institute: { address: '1 Test Road' } }
let next = 0
const newId = () => `n${next++}`

describe('TMS value names', () => {
  it('course rows: 1.40 + 1.50 values, target batches never (partner-wide)', () => {
    const [row] = tmsRowValues(snapshot, 'courses')
    expect(row.values).toMatchObject({
      courseName: 'Plumbing and Pipe Fitting', courseDuration: '60 days / 300 h', courseBatchSize: '25',
      courseEnrolled: '100', courseEnrolledFemale: '10', courseCertified: '75', coursePlacedFemale: '5',
      courseDropouts: '2', courseDropoutsFemale: '',
    })
    expect(Object.values(row.values)).not.toContain('105')
  })
  it('running batch rows: counts, dd/mm/yyyy span, mean and its note', () => {
    const [row] = tmsRowValues(snapshot, 'runningBatches')
    expect(row.values).toMatchObject({
      batchCourse: 'Plumbing and Pipe Fitting', batchNumber: '4', batchStartEnd: '07/09/2026 – 24/11/2026',
      batchEnrolled: '25', batchEnrolledFemale: '3', batchPresentToday: '20', batchAvg7: '18.4',
      batchAvg7Note: 'TMS avg 18–28 Sep 2026 (7 class days)',
    })
  })
})

describe('surprise prefill', () => {
  it('fills A address and the matching C card (folded course name, "04" == "4") only where empty', () => {
    const data = {
      fields: { address: '' },
      cards: {
        attendance: [
          { _id: 'a1', _link: 'c1', course: 'plumbing & pipe fitting (PPF)', batch: '04', enrolled_total: '', enrolled_female: '2' },
          { _id: 'a2', _link: 'c2', course: 'Welding', batch: '1' },
        ],
      },
    }
    const result = applyTmsPrefill(surprise, data, tmsData, newId)
    expect(data.fields.address).toBe('1 Test Road')
    expect(data.cards.attendance[0]).toMatchObject({
      enrolled_total: '25', enrolled_female: '2', tms_avg7: '18.4', remarks: 'TMS avg 18–28 Sep 2026 (7 class days)',
    })
    expect(data.cards.attendance).toHaveLength(2) // linked block: never adds cards
    expect(data.cards.attendance[1].tms_avg7).toBeUndefined()
    expect(result.filled).toBe(4)
    expect(result.differences).toEqual([
      expect.objectContaining({ blockKey: 'attendance', cardId: 'a1', fieldKey: 'enrolled_female', current: '2', value: '3' }),
    ])
  })

  it('never offers the remarks note over typed remarks', () => {
    const data = { fields: {}, cards: { attendance: [{ _id: 'a1', course: 'Plumbing and Pipe Fitting', batch: '4', remarks: 'Mine' }] } }
    const result = applyTmsPrefill(surprise, data, tmsData, newId)
    expect(data.cards.attendance[0].remarks).toBe('Mine')
    expect(result.differences.some((d) => d.fieldKey === 'remarks')).toBe(false)
  })

  it('Use writes one TMS value', () => {
    const data = { fields: { address: 'Old' }, cards: {} }
    const { differences } = applyTmsPrefill(surprise, data, tmsData, newId)
    expect(differences).toEqual([expect.objectContaining({ fieldKey: 'address', current: 'Old', value: '1 Test Road' })])
    useTmsValue(data, differences[0])
    expect(data.fields.address).toBe('1 Test Road')
  })
})

describe('QA prefill (1.40 / 1.50 / 1.60)', () => {
  it('reuses the blank seed card, adds missing ones and leaves Target / No. of Batches blank', () => {
    const data = { fields: {}, cards: { mou_courses: [{ _id: 'seed' }], cumulative: [], batches: [] } }
    applyTmsPrefill(qa, data, tmsData, newId)
    expect(data.cards.mou_courses).toEqual([
      { _id: 'seed', course: 'Plumbing and Pipe Fitting', duration: '60 days / 300 h', batch_size: '25' },
    ])
    expect(data.cards.cumulative[0]).toMatchObject({ course: 'Plumbing and Pipe Fitting', enrolled_t: '100', certified_f: '8', dropout_t: '2' })
    expect(data.cards.cumulative[0].target).toBeUndefined()
    expect(data.cards.cumulative[0].dropout_f).toBeUndefined()
    expect(data.cards.batches[0]).toMatchObject({
      course: 'Plumbing and Pipe Fitting', batch: '4', start_end: '07/09/2026 – 24/11/2026', enrolled: '25', female: '3',
      attendance_today: '20', attendance_7day: '18.4',
    })
    expect(data.cards.batches[0].tms_mismatch).toBeUndefined()
    expect(data.cards.batches[0].dropouts).toBeUndefined()
  })

  it('matches a card typed with the TMS short name and only fills its blanks', () => {
    const data = { fields: {}, cards: { batches: [{ _id: 'b1', course: 'PPF', batch: '4', enrolled: '24' }] } }
    const { differences } = applyTmsPrefill(qa, data, tmsData, newId)
    expect(data.cards.batches).toHaveLength(1)
    expect(data.cards.batches[0]).toMatchObject({ course: 'PPF', enrolled: '24', female: '3' })
    expect(differences.map((d) => [d.fieldKey, d.current, d.value])).toContainEqual(['enrolled', '24', '25'])
    // identity fields that matched are not "differences"
    expect(differences.some((d) => d.fieldKey === 'course')).toBe(false)
  })

  it('equal numbers are not differences ("25" vs "25.0")', () => {
    const data = { fields: {}, cards: { batches: [{ _id: 'b1', course: 'Plumbing and Pipe Fitting', batch: '4', enrolled: '25.0' }] } }
    const { differences } = applyTmsPrefill(qa, data, tmsData, newId)
    expect(differences.some((d) => d.fieldKey === 'enrolled')).toBe(false)
  })
})
