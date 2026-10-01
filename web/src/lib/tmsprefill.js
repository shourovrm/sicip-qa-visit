// TMS values -> report fields, driven by the template (spec 2026-10-02 items 1, 2, 7, 9-11):
// a field's `tmsFill` names the TMS value it takes, a cards block's `tmsRows` says which TMS rows
// its cards follow ("courses" = QA 1.40/1.50, "runningBatches" = surprise C, QA 1.60).
// Only EMPTY fields are written; a filled field whose value differs from TMS is returned as a
// difference the officer may "Use". A linked block (linkFrom) never gets new cards; other blocks
// get one card per TMS row they are missing. Target and No. of Batches have no tmsFill: TMS's
// target is partner-wide (DECISIONS).
import { courseKey } from './tmscatalog.js'
import { averageNote, formatMean } from './tmssnapshot.js'

// values the officer may keep even when TMS differs: the note is only a starting text
const FILL_ONLY = new Set(['batchAvg7Note'])
// the values that say which row a card is; matched, so never a difference
const IDENTITY = new Set(['courseName', 'batchCourse', 'batchNumber'])

const text = (value) => String(value ?? '').trim()
const numberText = (value) => (value == null ? '' : String(value))
const positiveText = (value) => (Number(value) > 0 ? String(value) : '')

// "2026-09-07" -> "07/09/2026" like the printed QA form
function ddmmyyyy(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text(value))
  return match ? `${match[3]}/${match[2]}/${match[1]}` : text(value)
}

function courseValues(course) {
  return {
    courseName: course.name,
    courseDuration: course.duration,
    courseBatchSize: positiveText(course.batchSize),
    courseEnrolled: numberText(course.enrolledTotal),
    courseEnrolledFemale: numberText(course.enrolledFemale),
    courseCertified: numberText(course.certifiedTotal),
    courseCertifiedFemale: numberText(course.certifiedFemale),
    coursePlaced: numberText(course.placedTotal),
    coursePlacedFemale: numberText(course.placedFemale),
    courseDropouts: numberText(course.dropoutTotal),
    courseDropoutsFemale: numberText(course.dropoutFemale),
  }
}

function batchValues(batch) {
  return {
    batchCourse: batch.course,
    batchNumber: batch.batchNumber,
    batchStartEnd: batch.startDate && batch.endDate ? `${ddmmyyyy(batch.startDate)} – ${ddmmyyyy(batch.endDate)}` : '',
    batchEnrolled: numberText(batch.enrolled),
    batchEnrolledFemale: numberText(batch.female),
    batchPresentToday: numberText(batch.attendanceToday),
    batchAvg7: formatMean(batch.attendance7day),
    batchAvg7Note: averageNote({ from: batch.averageFrom, to: batch.averageTo, classDays: batch.averageClassDays }),
  }
}

// one entry per TMS row: {names (every spelling of its course), batch (null for course rows), values}
export function tmsRowValues(snapshot, rowsKind) {
  if (rowsKind === 'courses') {
    return (snapshot?.courses ?? []).map((course) => ({ names: [course.name, course.tmsName], batch: null, values: courseValues(course) }))
  }
  if (rowsKind === 'runningBatches') {
    return (snapshot?.runningBatches ?? []).map((batch) => ({ names: [batch.course, batch.tmsCourse], batch: batch.batchNumber, values: batchValues(batch) }))
  }
  return []
}

function sameBatch(a, b) {
  if (text(a) === '' || text(b) === '') return false
  const numberA = Number(text(a))
  const numberB = Number(text(b))
  return Number.isFinite(numberA) && Number.isFinite(numberB) ? numberA === numberB : text(a).toLowerCase() === text(b).toLowerCase()
}

function sameValue(a, b) {
  const numberA = Number(text(a))
  const numberB = Number(text(b))
  if (text(a) !== '' && text(b) !== '' && Number.isFinite(numberA) && Number.isFinite(numberB)) return numberA === numberB
  return text(a).toLowerCase() === text(b).toLowerCase()
}

function matchesRow(card, row) {
  const cardCourse = courseKey(card.course)
  if (!cardCourse || !row.names.some((name) => courseKey(name) === cardCourse)) return false
  return row.batch == null || sameBatch(card.batch, row.batch)
}

const hasNoIdentity = (card, row) => text(card.course) === '' && (row.batch == null || text(card.batch) === '')

// write `value` into an empty slot, or note it as a difference; returns 1 when written
function offer(target, fieldKey, value, fill, where, differences) {
  if (text(value) === '') return 0
  const current = target[fieldKey]
  if (text(current) === '') {
    target[fieldKey] = value
    return 1
  }
  if (!FILL_ONLY.has(fill) && !IDENTITY.has(fill) && !sameValue(current, value)) {
    differences.push({ ...where, fieldKey, current: text(current), value })
  }
  return 0
}

function fillCards(block, data, tmsData, newId, differences) {
  const rows = tmsRowValues(tmsData.snapshot, block.tmsRows)
  if (!rows.length) return 0
  const cards = [...(data.cards?.[block.key] ?? [])]
  const claimed = new Set()
  let filled = 0
  for (const row of rows) {
    let index = cards.findIndex((card) => matchesRow(card, row))
    if (index < 0 && !block.linkFrom) {
      // reuse an untouched blank card (the template's seeded first row) before adding one
      index = cards.findIndex((card, i) => !claimed.has(i) && hasNoIdentity(card, row))
      if (index < 0) {
        cards.push({ _id: newId() })
        index = cards.length - 1
      }
      claimed.add(index)
    }
    if (index < 0) continue
    const card = { ...cards[index] }
    for (const field of block.fields.filter((f) => f.tmsFill)) {
      const where = { blockKey: block.key, cardId: card._id, label: field.label, context: [text(card.course), text(card.batch)].filter(Boolean).join(' · ') }
      filled += offer(card, field.key, row.values[field.tmsFill] ?? '', field.tmsFill, where, differences)
    }
    cards[index] = card
  }
  data.cards = { ...(data.cards ?? {}), [block.key]: cards }
  return filled
}

const FIELD_VALUES = { instituteAddress: (tmsData) => text(tmsData.institute?.address) }

function fillFields(block, data, tmsData, differences) {
  let filled = 0
  data.fields = data.fields ?? {}
  for (const field of block.fields.filter((f) => FIELD_VALUES[f.tmsFill])) {
    const where = { blockKey: null, cardId: null, label: field.label, context: '' }
    filled += offer(data.fields, field.key, FIELD_VALUES[field.tmsFill](tmsData), field.tmsFill, where, differences)
  }
  return filled
}

// tmsData = {snapshot (tmssnapshot.js) | null, institute: TMS institute row | null}; mutates
// data, returns {filled: how many empty fields got a value, differences: [{blockKey, cardId,
// fieldKey, label, context (the card's course · batch), current, value}]}
export function applyTmsPrefill(template, data, tmsData, newId = () => crypto.randomUUID()) {
  const differences = []
  let filled = 0
  for (const block of template.sections.flatMap((section) => section.blocks)) {
    if (block.type === 'cards' && block.tmsRows) filled += fillCards(block, data, tmsData, newId, differences)
    else if (block.type === 'fields') filled += fillFields(block, data, tmsData, differences)
  }
  return { filled, differences }
}

// the officer pressed Use on one difference
export function useTmsValue(data, difference) {
  if (difference.blockKey == null) {
    data.fields = { ...(data.fields ?? {}), [difference.fieldKey]: difference.value }
    return
  }
  const cards = data.cards?.[difference.blockKey] ?? []
  data.cards = {
    ...data.cards,
    [difference.blockKey]: cards.map((card) => (card._id === difference.cardId ? { ...card, [difference.fieldKey]: difference.value } : card)),
  }
}

// a template that takes any TMS value at all (both report types today)
export function usesTmsFill(template) {
  return template.sections.some((section) => section.blocks.some((block) => block.tmsRows || (block.fields ?? []).some((f) => f.tmsFill)))
}
