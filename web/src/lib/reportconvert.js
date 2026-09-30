// surprise v1 report data -> v2 shape (the editor's "Convert to new format"). 1:1 port of
// android domain/report/ReportConvert.kt. Nothing is deleted: every v1 key stays in the JSON
// (v2 just doesn't show the ones it dropped). What moves:
// - officers text -> "officers" cards ("Name (Designation)" split when written that way)
// - H3 monitoring logbook answer -> K's followup_1
// - C attendance trainers_present -> the new C "trainers" card of the same course
// - J graduate "Course / batch" text -> course; Employment ids renamed to v2's
// - M key findings -> major findings; instructions + follow-up -> recommendations
import { normalize } from './reporttemplate.js'

const NAME_WITH_DESIGNATION = /^(.*?)\s*\((.+)\)\s*$/
const EMPLOYMENT_V1_TO_V2 = { same: 'employed', other: 'other_job', none: 'not_employed' }

const text = (obj, key) => String(obj?.[key] ?? '').trim()
const splitLines = (value) => String(value ?? '').split(/[\n;]/).map((l) => l.trim()).filter(Boolean)

export function convertSurpriseV1ToV2(v2, v1Data, newId = () => crypto.randomUUID()) {
  const data = structuredClone(v1Data)
  data.fields = data.fields ?? {}
  data.checks = data.checks ?? {}
  data.cards = data.cards ?? {}

  // A: visiting officers
  if ((data.cards.officers ?? []).length === 0) {
    data.cards.officers = splitLines(data.fields.officers).map((line) => {
      const match = NAME_WITH_DESIGNATION.exec(line)
      return { _id: newId(), name: match ? match[1] : line, designation: match ? match[2] : '' }
    })
  }

  // H3 -> K followup_1
  const logbook = data.checks.registers_3 ?? {}
  if ((text(logbook, 'answer') || text(logbook, 'remarks')) && !text(data.checks.followup_1, 'answer')) {
    data.checks.followup_1 = { ...(data.checks.followup_1 ?? {}), answer: logbook.answer ?? '', remarks: logbook.remarks ?? '' }
  }

  // J graduates: course/batch text -> course, employment ids
  data.cards.graduate = (data.cards.graduate ?? []).map((card) => {
    const merged = { ...card }
    if (!text(card, 'course') && text(card, 'batch')) {
      merged.course = text(card, 'batch')
      merged.batch = ''
    }
    const employment = EMPLOYMENT_V1_TO_V2[text(card, 'confirmed')]
    if (employment) merged.confirmed = employment
    return merged
  })

  // M -> L findings + recommendations
  if ((data.findings ?? []).length === 0) {
    data.findings = splitLines(data.fields.key_findings).map((line) => ({ src: '', text: line }))
  }
  if (!text(data.fields, 'recommendations')) {
    const followUp = text(data.fields, 'follow_up')
    const lines = splitLines(data.fields.instructions_given)
    if (followUp && followUp !== 'No further action') lines.push(`Recommended follow-up: ${followUp}`)
    data.fields.recommendations = lines.join('\n')
  }

  // C: trainers present per course -- linked cards are created by normalize, then filled
  const trainersPresent = Object.fromEntries((data.cards.attendance ?? []).map((c) => [text(c, '_link'), text(c, 'trainers_present')]))
  normalize(v2, data)
  data.cards.trainers = (data.cards.trainers ?? []).map((card) => {
    const present = trainersPresent[text(card, '_link')] ?? ''
    return !present || text(card, 'present') ? card : { ...card, present }
  })
  return data
}
