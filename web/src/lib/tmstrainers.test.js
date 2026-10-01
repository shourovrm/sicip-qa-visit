// TMS trainer hints for C "Trainers present" (made-up names only)
import { describe, expect, it } from 'vitest'
import { designationFill, trainerHints, trainersFor } from './tmstrainers.js'

// entity/trainer/list row shape (only the keys the reduction reads, plus ones it must drop)
const row = (id, name, { designation = null, courses = [], master = [], associate = [] } = {}) => ({
  id,
  employee_info: { name, designation, mobile: '01700000000', nid: '123' },
  map_entity_institute_course_trainer: [...courses.map((c) => ({ course_info_id: c })), { course_info_id: null }],
  batch_info_details_master_trainer: master.map((b) => ({ batch_info_id: b, master_trainer_id: id })),
  batch_info_details_associate_trainer: associate.map((b) => ({ batch_info_id: b, associate_trainer_id: id })),
})
const rows = [
  row(1, 'Trainer Other Course', { courses: [10], master: [100] }),
  row(2, 'Trainer Associate', { courses: [20], associate: [200, 201] }),
  row(3, 'Trainer Master', { courses: [20], master: [200] }),
  row(4, 'Trainer Same Course', { courses: [20], designation: 'Instructor' }),
]
const hints = trainerHints(rows)
const batch = { id: 200, courseId: 20 }

describe('trainer hints', () => {
  it('keeps name, designation, courses and batch roles only', () => {
    expect(hints[0]).toEqual({ name: 'Trainer Other Course', designation: '', courseIds: [10], masterBatchIds: [100], associateBatchIds: [] })
    expect(JSON.stringify(hints)).not.toContain('0170')
    expect(trainerHints([{ employee_info: { name: ' ' } }])).toEqual([])
  })

  it('orders the batch master first, then its associates, its course, then the rest', () => {
    expect(trainersFor(hints, batch).map((h) => h.name)).toEqual(['Trainer Master', 'Trainer Associate', 'Trainer Same Course', 'Trainer Other Course'])
    expect(trainersFor(hints, null).map((h) => h.name)).toEqual(hints.map((h) => h.name))
  })

  it('designation: TMS designation, else the role in the card batch, only into an empty field', () => {
    const fields = [{ key: 'batch' }, { key: 'name', suggest: 'tmsTrainer' }, { key: 'designation' }]
    expect(designationFill(fields, {}, 'name', 'Trainer Master', hints, batch)).toBe('Master Trainer')
    expect(designationFill(fields, {}, 'name', 'Trainer Associate', hints, batch)).toBe('Associate Trainer')
    expect(designationFill(fields, {}, 'name', 'Trainer Same Course', hints, batch)).toBe('Instructor')
    expect(designationFill(fields, {}, 'name', 'Trainer Other Course', hints, batch)).toBeNull()
    expect(designationFill(fields, { designation: 'Typed' }, 'name', 'Trainer Master', hints, batch)).toBeNull()
    expect(designationFill(fields, {}, 'name', 'Someone typed', hints, batch)).toBeNull()
    expect(designationFill(fields, {}, 'designation', 'Trainer Master', hints, batch)).toBeNull()
  })
})
