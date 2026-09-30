// qa-v2 print tables as plain {headers, rows} (strings only) -- shared by qareporthtml.js and
// qareportdocx.js so the PDF and the Word file can't drift. 1:1 port of android
// pdf/QaTables.kt. A table with no filled row returns null (nothing printed).
import { criteriaPath, evidenceLabel, itemEvidence, usedEvidence } from './evidence.js'
import { visitingOfficers } from './signoff.js'

const text = (value) => String(value ?? '').trim()
const cardsOf = (data, key) => data.cards?.[key] ?? []
const filled = (card) => Object.entries(card).some(([key, value]) => !key.startsWith('_') && text(value))

function blockByKey(template, key) {
  for (const section of template.sections) {
    const block = section.blocks.find((b) => b.key === key)
    if (block) return block
  }
  return null
}

// a choice value -> its option label; anything else as typed
function shownValue(field, value) {
  if (field?.kind === 'choice') return field.options.find((o) => o.id === value)?.label ?? text(value)
  return text(value)
}
function fieldOf(block, key) {
  return block?.fields.find((f) => f.key === key)
}

// "2026-01-10" -> "10/01/2026" like the rest of the printed form; anything else as typed
function ddmmyyyy(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text(value))
  return match ? `${match[3]}/${match[2]}/${match[1]}` : text(value)
}

function numbered(rows) {
  return rows.map((row, index) => [`${index + 1}.`, ...row])
}

// "R. M. Shourov, Program Officer (QA); S. Akter, Program Officer"
export function officersLine(data) {
  return visitingOfficers(data)
    .filter((officer) => officer.name)
    .map((officer) => [officer.name, officer.designation].filter(Boolean).join(', '))
    .join('; ')
}

// i) status: one row per registration body the officer answered
export function registrationTable(template, data) {
  const fields = data.fields ?? {}
  const block = template.sections[0].blocks.find((b) => b.fields?.some((f) => f.key === 'bteb_registered'))
  const rows = []
  for (const [body, name] of [['bteb', 'BTEB'], ['nsda', 'NSDA']]) {
    const registered = text(fields[`${body}_registered`])
    if (!registered) continue
    const yes = registered === 'yes'
    rows.push([
      name,
      shownValue(fieldOf(block, `${body}_registered`), registered),
      yes ? text(fields[`${body}_reg_no`]) : '',
      yes ? text(fields[`${body}_courses`]) : '',
      yes ? shownValue(fieldOf(block, `${body}_uptodate`), fields[`${body}_uptodate`]) : '',
      yes ? text(fields[`${body}_remarks`]) : '',
    ])
  }
  if (rows.length === 0) return null
  return { headers: ['Body', 'Registered', 'Registration no.', 'Accredited courses', 'Up to date', 'Remarks'], rows }
}

// 1.20: one row per MoU, "Others" printed as the typed organisation name
export function mouTable(template, data) {
  const rows = cardsOf(data, 'mous').filter(filled).map((card) => [
    card.partner === 'Others' ? text(card.partner_other) || 'Others' : text(card.partner),
    ddmmyyyy(card.signed_date), text(card.target), text(card.duration), text(card.amount),
  ])
  if (rows.length === 0) return null
  return {
    headers: ['S.N.', 'Contract/MoU signed with', 'Date of signing', 'Total target', 'Duration', 'Total amount'],
    rows: numbered(rows),
  }
}

// 1.31-1.34: one row per course; a contract with no course yet still gets its own row
export function contractsTable(template, data) {
  const coursesBlock = blockByKey(template, 'contract_courses')
  const courses = cardsOf(data, 'contract_courses').filter(filled)
  const rows = courses.map((card) => [
    text(card.contract), text(card.course),
    shownValue(fieldOf(coursesBlock, 'overlap'), card.overlap),
    shownValue(fieldOf(coursesBlock, 'facilities'), card.facilities),
  ])
  for (const contract of cardsOf(data, 'contracts')) {
    const organisation = text(contract.organisation)
    if (organisation && !courses.some((card) => text(card.contract) === organisation)) rows.push([organisation, '', '', ''])
  }
  if (rows.length === 0) return null
  return {
    headers: ['S.N.', 'Organization / project', 'Training course', 'Overlaps a SICIP course', 'Facilities'],
    rows: numbered(rows),
  }
}

// cards blocks inside criteria sections (4.2 sample check, 8 rooms, 8 damaged equipment)
export function criteriaCardsTable(block, data) {
  const cards = cardsOf(data, block.key).filter(filled)
  if (cards.length === 0) return null
  if (block.key === 'rooms') {
    return {
      heading: block.heading,
      headers: ['S.N.', 'Course', 'Classroom and workshop size', 'Trainees per batch'],
      rows: numbered(cards.map((card) => [text(card.course), roomSize(card), text(card.trainees)])),
    }
  }
  return {
    heading: block.heading,
    headers: ['S.N.', ...block.fields.map((f) => f.label)],
    rows: numbered(cards.map((card) => block.fields.map((f) => shownValue(f, card[f.key])))),
  }
}

// "Classroom - 300 sft and workshop/lab - 800 sft" | "Classroom cum workshop/lab - 1000 sft"
export function roomSize(card) {
  if (card.layout === 'same') return text(card.combined_sft) ? `Classroom cum workshop/lab - ${text(card.combined_sft)} sft` : ''
  const parts = []
  if (text(card.classroom_sft)) parts.push(`Classroom - ${text(card.classroom_sft)} sft`)
  if (text(card.workshop_sft)) parts.push(`workshop/lab - ${text(card.workshop_sft)} sft`)
  return parts.join(' and ')
}

// a criterion's EVIDENCE cell: its numbered evidence, one per line
export function evidenceLines(data, itemId) {
  return itemEvidence(data, itemId).map(evidenceLabel)
}

// closing list: every evidence in number order with the criteria that cite it
export function evidenceIndexTable(template, data) {
  const used = usedEvidence(data)
  if (used.length === 0) return null
  const citedBy = new Map(used.map((entry) => [entry._id, []]))
  for (const section of template.sections) {
    for (const block of section.blocks) {
      if (block.type !== 'criteria') continue
      for (const item of block.items) {
        for (const entry of itemEvidence(data, item.id)) citedBy.get(entry._id)?.push(criteriaPath(section, block, item))
      }
    }
  }
  return {
    heading: 'List of evidence',
    headers: ['No.', 'Evidence', 'Criteria'],
    rows: used.map((entry) => [entry.no, entry.name, citedBy.get(entry._id).join(', ')]),
  }
}

