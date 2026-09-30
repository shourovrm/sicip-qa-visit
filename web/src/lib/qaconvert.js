// QA v1 report data -> v2 shape (the editor's "Convert to new format"). 1:1 port of android
// domain/report/QaConvert.kt. Nothing is deleted: every v1 key stays in the JSON (v2 just
// doesn't show the ones it dropped, e.g. the improvement plan cards). What moves:
// - officers text -> "officers" cards; registration text -> "Other registration"
// - 1.21-1.25 fields -> first MoU card; 1.31-1.34 text -> contracts + contract course rows
// - criteria answers follow their option ids into the v2 items (a few renamed ones via
//   OPTION_SOURCES); item evidence text -> numbered evidence entries; item notes follow the item
// - 6.2 "CBLM / lesson plan / TDP / job sheet available" answered Seen -> ticked boxes
import { normalize } from './reporttemplate.js'
import { withEvidenceAdded } from './evidence.js'
import { splitOfficerLine } from './signoff.js'

// v2 option id -> the v1 option whose answer it takes over
const OPTION_SOURCES = {
  stock_register: 'ttm_registers',
  fuel_register: 'ttm_registers',
  tools_cover: 'assessment_tools',
  training_record: 'staff_records',
}
// v1 items whose evidence/note now belong to a differently numbered v2 item
const ITEM_MOVES = { s8_5: 's8_7', s8_6: 's2_3', s81_1: 's8_1e', s81_2: 's8_1e', s81_3: 's8_2', s81_4: 's8_2' }
// v1 options answered Seen that become ticked boxes on a v2 item
const TICKS_FROM_OPTIONS = { s6_2: ['cblm', 'lesson_plan', 'tdp', 'job_sheet'] }

const text = (obj, key) => String(obj?.[key] ?? '').trim()
const splitLines = (value) => String(value ?? '').split(/[\n;]/).map((l) => l.trim()).filter(Boolean)
const sameText = (a, b) => a.toLowerCase() === b.toLowerCase()

function convertProfile(v2, data, newId) {
  const fields = data.fields
  if ((data.cards.officers ?? []).length === 0) {
    data.cards.officers = splitLines(fields.officers).map((line) => ({ _id: newId(), ...splitOfficerLine(line) }))
  }
  if (!text(fields, 'other_registration') && text(fields, 'status')) fields.other_registration = text(fields, 'status')

  const mouValues = {
    signed_date: text(fields, 'signed_date'), target: text(fields, 'mou_target'),
    duration: text(fields, 'mou_duration'), amount: text(fields, 'mou_amount'),
  }
  const partner = text(fields, 'contract_with')
  if ((data.cards.mous ?? []).length === 0 && (partner || Object.values(mouValues).some(Boolean))) {
    const associations = v2.sections[0].blocks.find((b) => b.key === 'mous').fields.find((f) => f.key === 'partner').options
    const known = associations.find((name) => sameText(name, partner))
    const partnerValues = known ? { partner: known } : (partner ? { partner: 'Others', partner_other: partner } : {})
    data.cards.mous = [{ _id: newId(), ...partnerValues, ...mouValues }]
  }

  const organisations = splitLines(fields.other_orgs)
  if ((data.cards.contracts ?? []).length === 0 && organisations.length) {
    data.cards.contracts = organisations.map((organisation) => ({ _id: newId(), organisation }))
  }
  const courses = splitLines(fields.other_courses)
  const overlapping = splitLines(fields.overlapping_courses)
  if ((data.cards.contract_courses ?? []).length === 0 && (courses.length || overlapping.length)) {
    // one organisation: every course is under it; several: the officer picks per row
    const contract = organisations.length === 1 ? organisations[0] : ''
    const rows = courses.map((course) => ({
      _id: newId(), contract, course, overlap: overlapping.some((o) => sameText(o, course)) ? 'yes' : '',
    }))
    for (const course of overlapping) {
      if (!courses.some((c) => sameText(c, course))) rows.push({ _id: newId(), contract, course, overlap: 'yes' })
    }
    data.cards.contract_courses = rows
  }
  const facilities = text(fields, 'same_facilities')
  if (facilities && !text(fields, 'comments').includes(facilities)) {
    fields.comments = [text(fields, 'comments'), `Facilities: ${facilities}`].filter(Boolean).join('\n')
  }
}

function convertCriteria(v2, data, v1Criteria, newId) {
  const v1Options = {}
  for (const entry of Object.values(v1Criteria)) Object.assign(v1Options, entry?.opts ?? {})
  const criteria = {}
  for (const section of v2.sections) {
    for (const block of section.blocks) {
      if (block.type !== 'criteria') continue
      for (const item of block.items) {
        if (item.heading) continue
        const opts = {}
        for (const option of item.options) {
          const state = v1Options[OPTION_SOURCES[option.id] ?? option.id]
          if (state) opts[option.id] = { ...state }
        }
        const ticks = (TICKS_FROM_OPTIONS[item.id] ?? []).filter((id) => v1Options[id]?.v === 'seen')
        criteria[item.id] = { opts, ...(ticks.length ? { ticks } : {}) }
      }
    }
  }
  // item notes + evidence text: from the v1 item of the same id, or the one that moved here.
  // notes first on the plain object, then evidence (withEvidenceAdded returns new objects)
  const v2ItemFor = (v1ItemId) => ITEM_MOVES[v1ItemId] ?? (v1ItemId in criteria ? v1ItemId : null)
  for (const [v1ItemId, entry] of Object.entries(v1Criteria)) {
    const target = v2ItemFor(v1ItemId)
    const note = text(entry, 'note')
    if (target && note) criteria[target].note = [text(criteria[target], 'note'), note].filter(Boolean).join('\n')
  }
  data.criteria = criteria
  for (const [v1ItemId, entry] of Object.entries(v1Criteria)) {
    const target = v2ItemFor(v1ItemId)
    if (!target) continue
    for (const name of splitLines(entry?.evidence)) {
      data = withEvidenceAdded(data, target, name, newId)
    }
  }
  return data
}

export function convertQaV1ToV2(v2, v1Data, newId = () => crypto.randomUUID()) {
  let data = structuredClone(v1Data)
  data.fields = data.fields ?? {}
  data.cards = data.cards ?? {}
  // the raw v1 answers stay under their own key (v2 rebuilds data.criteria)
  const v1Criteria = data.criteria ?? {}
  data.criteriaV1 = v1Criteria
  convertProfile(v2, data, newId)
  data = convertCriteria(v2, data, v1Criteria, newId)

  // 4.2's old "number of applicants" box -> the first course row of the sample-check table
  const applicants = text(v1Criteria.s4_2?.opts?.applicants_list, 'detail')
  if (applicants) {
    const selection = data.cards.selection ?? []
    if (selection.length === 0) data.cards.selection = [{ _id: newId(), applicants }]
    else if (!text(selection[0], 'applicants')) selection[0].applicants = applicants
  }
  return normalize(v2, data)
}
