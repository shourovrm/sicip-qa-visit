// the linked institute's TMS trainers as typing hints for C "Trainers present" (spec 2026-10-02
// item 3). entity/trainer/list (the TMS "trainer list" page's own call) is reduced at once to
// name + designation + mapped courses + the batches they are master / associate trainer of;
// phone, NID, addresses and certificates never leave trainerHints(). Memory only.

const text = (value) => String(value ?? '').trim()
const ids = (rows, key) => [...new Set((rows ?? []).map((r) => Number(r?.[key])).filter((id) => id > 0))]

export function trainerHints(rows) {
  return (rows ?? [])
    .map((row) => ({
      name: text(row?.employee_info?.name),
      designation: text(row?.employee_info?.designation),
      courseIds: ids(row?.map_entity_institute_course_trainer, 'course_info_id'),
      masterBatchIds: ids(row?.batch_info_details_master_trainer, 'batch_info_id'),
      associateBatchIds: ids(row?.batch_info_details_associate_trainer, 'batch_info_id'),
    }))
    .filter((hint) => hint.name)
}

// the card's batch trainers first (master, then associates), then its course's, then the rest;
// batch = tmscatalog batch ref {id, courseId} or null
function rank(hint, batch) {
  if (!batch) return 0
  if (hint.masterBatchIds.includes(batch.id)) return 0
  if (hint.associateBatchIds.includes(batch.id)) return 1
  if (hint.courseIds.includes(batch.courseId)) return 2
  return 3
}

export function trainersFor(hints, batch) {
  return [...(hints ?? [])].sort((a, b) => rank(a, batch) - rank(b, batch))
}

// TMS has no designation for most trainers; their role in the card's batch is the next best
function designationOf(hint, batch) {
  if (hint.designation) return hint.designation
  if (batch && hint.masterBatchIds.includes(batch.id)) return 'Master Trainer'
  if (batch && hint.associateBatchIds.includes(batch.id)) return 'Associate Trainer'
  return ''
}

// picking a trainer name fills the card's empty designation field (still editable); null = leave it
export function designationFill(fields, card, fieldKey, value, hints, batch) {
  const field = fields.find((f) => f.key === fieldKey)
  if (field?.suggest !== 'tmsTrainer') return null
  if (!fields.some((f) => f.key === 'designation') || text(card.designation)) return null
  const hint = (hints ?? []).find((h) => h.name === value)
  const designation = hint ? designationOf(hint, batch) : ''
  return designation || null
}
