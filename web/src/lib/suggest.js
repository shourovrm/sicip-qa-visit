// field "suggest" -> the typing hints a report field offers. Suggestions never restrict input:
// they only feed a datalist. Context = {catalog (tmscatalog.js) | null, trainees: Map of
// "courseId:batchId" -> [{name, mobile}], equipment: [names]} -- all in memory only.
import { batchNumbersFor, courseBatchOfRef, findBatch } from './tmscatalog.js'

// the TMS batch a trainee field points at: the card's own course + batch (L graduate) or its
// courseRef "Course · N" batch field (D identity)
export function traineeBatchOf(card, catalog) {
  if (!catalog) return null
  if (String(card?.course ?? '').trim()) return findBatch(catalog, card.course, card.batch)
  const { course, batch } = courseBatchOfRef(card?.batch)
  return findBatch(catalog, course, batch)
}

export function traineeKey(batch) {
  return batch ? `${batch.courseId}:${batch.id}` : ''
}

export function suggestionsFor(field, card, context) {
  const kind = field.suggest
  if (kind === 'shared:equipment') return context.equipment ?? []
  if (!context.catalog) return []
  if (kind === 'tmsCourse') return context.catalog.courseNames
  if (kind === 'tmsBatch') return batchNumbersFor(context.catalog, card?.course)
  if (kind === 'tmsTrainee') {
    const hints = context.trainees.get(traineeKey(traineeBatchOf(card, context.catalog))) ?? []
    return hints.map((hint) => hint.name)
  }
  return []
}

// picking a trainee name fills the card's empty phone field (still editable); null = leave it
export function phoneFill(fields, card, fieldKey, value, hints) {
  const field = fields.find((f) => f.key === fieldKey)
  if (field?.suggest !== 'tmsTrainee') return null
  if (!fields.some((f) => f.key === 'phone') || String(card.phone ?? '').trim()) return null
  const hint = (hints ?? []).find((h) => h.name === value)
  return hint?.mobile ? hint.mobile : null
}

// two option lists as one datalist: first-seen order, no blanks, no case-insensitive repeats
export function mergeOptions(first, second) {
  const seen = new Set()
  const merged = []
  for (const option of [...(first ?? []), ...(second ?? [])]) {
    const value = String(option ?? '').trim()
    if (!value || seen.has(value.toLowerCase())) continue
    seen.add(value.toLowerCase())
    merged.push(value)
  }
  return merged
}
