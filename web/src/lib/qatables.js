// qa-v2 print tables as plain {headers, rows} (strings only) -- shared by qareporthtml.js and
// qareportdocx.js so the PDF and the Word file can't drift. 1:1 port of android
// pdf/QaTables.kt. A conditional table (registration, MoU, contracts, ...) with no filled row
// returns null (nothing printed); the 1.10/1.40/1.50/1.60/plan card tables always print, as ONE
// blank row (S.N. "1.") when nobody filled them. Blank cells are '' here -- the renderers print
// them as "-" through reportlayout.js cellOrDash.
// A table may carry `weights` (relative column widths, fixed layout), `headerRows` (two-level
// header, see headerRowsOf), `note` (printed under it) and `dense` (8 pt headers for 9+ columns).
import { attachmentName, criteriaPath, evidenceLabel, itemEvidence, usedEvidence } from './evidence.js'
import { visitingOfficers } from './signoff.js'
import { tableColumnLayout } from './reportlayout.js'
import { countWithPercent } from './percent.js'

// qa-v1 has no `percentOf`; its 1.50 card uses the same keys
const PERCENT_OF_V1 = { placed_t: 'certified_t', placed_f: 'certified_f', dropout_t: 'enrolled_t', dropout_f: 'enrolled_f' }

// a 1.50 count as "n (p%)" of its template `percentOf` field (lib/percent.js)
function withPercent(block, card, key) {
  const base = block.fields.find((f) => f.key === key)?.percentOf ?? PERCENT_OF_V1[key]
  return countWithPercent(card[key], card[base])
}

const defaultWeight = (header) => (header === 'S.N.' || header === 'No.' ? 7 : 20)

// section 13 S.N. | Component | Strengths | Weakness widths ({weights, dense})
export function strengthsLayout(block, fields) {
  const rows = block.pairs.map((pair, i) => [`${i + 1}.`, pair.component, text(fields[pair.strength]), text(fields[pair.weakness])])
  return fittedTable({ headers: ['S.N.', 'Component', 'Strengths', 'Weakness'], rows, weights: [6, 24, 35, 35] })
}

// a one-header-row table with widths that never break a word (reportlayout columnLayout):
// weights become percents and a crowded table turns dense. Two-level headers keep their
// hand-set weights.
export function fittedTable(table) {
  if (!table || table.headerRows) return table
  const layout = tableColumnLayout(table.headers, table.rows, table.weights ?? table.headers.map(defaultWeight))
  return { ...table, weights: layout.percents, dense: Boolean(table.dense) || layout.dense }
}

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

// closing list: every attachment in number order with the criteria that cite it
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
    heading: 'List of attachments',
    headers: ['Attachment', 'Evidence', 'Criteria'],
    rows: used.map((entry) => [attachmentName(entry), entry.name, citedBy.get(entry._id).join(', ')]),
  }
}



// header rows as arrays of {text, colSpan?, rowSpan?}; a plain table has the single row `headers`
export function headerRowsOf(table) {
  return table.headerRows ?? [table.headers.map((header) => ({ text: header }))]
}

// the form's own "(one point per line)" hint is an editor instruction, never printed
export function withoutLineHint(label) {
  return String(label ?? '').replace(/\s*\(?one point per line\)?/i, '').trim()
}

const filledCards = (data, key) => cardsOf(data, key).filter(filled)

// a card table nobody filled still prints one row of blank cells so the paper form has its line
function numberedOrBlank(rows, cellCount) {
  return numbered(rows.length ? rows : [Array(cellCount).fill('')])
}

function cardRow(card, keys) {
  return keys.map((key) => text(card[key]))
}

// 1.10 persons met: the template's own columns, no S.N.
export function personsTable(block, data) {
  const cards = filledCards(data, block.key)
  const blankRow = block.fields.map(() => '')
  return {
    heading: block.heading,
    headers: block.fields.map((field) => field.label),
    rows: cards.length ? cards.map((card) => cardRow(card, block.fields.map((f) => f.key))) : [blankRow],
  }
}

// 1.40 courses in the MoU: S.N. | Training Course | Target | Duration | No. of Batches | Batch Size
export function mouCoursesTable(block, data) {
  const rows = filledCards(data, block.key).map((card) => cardRow(card, ['course', 'target', 'duration', 'batches', 'batch_size']))
  return {
    heading: block.heading,
    headers: ['S.N.', 'Training Course', 'Target', 'Duration', 'No. of Batches', 'Batch Size'],
    weights: [7, 40, 12, 16, 12, 13],
    rows: numberedOrBlank(rows, 5),
  }
}


const CUMULATIVE_NOTE = 'T= Total and F = Female'

// 1.50 cumulative implementation: compact two-level header like the template. Placed % is of
// certified, dropout % of enrolled.
export function cumulativeTable(block, data) {
  const rows = filledCards(data, block.key).map((card) => [
    text(card.course), text(card.target),
    text(card.enrolled_t), text(card.enrolled_f), text(card.certified_t), text(card.certified_f),
    ...['placed_t', 'placed_f', 'dropout_t', 'dropout_f'].map((key) => withPercent(block, card, key)),
  ])
  const tall = (label) => ({ text: label, rowSpan: 2 })
  const pair = (label) => ({ text: label, colSpan: 2 })
  return {
    heading: block.heading,
    headerRows: [
      [tall('S.N.'), tall('Course Name'), tall('Target'), pair('Enrolled'), pair('Certified'),
        pair('Job Placed with Percentage'), pair('No. of dropouts with Percentage')],
      ['T', 'F', 'T', 'F', 'T', 'F', 'T', 'F'].map((label) => ({ text: label })),
    ],
    // dense tables use 3 pt cell padding; the course column is the widest and every other column
    // fits its header word and a value such as "75 (75%)" (which may wrap at its space)
    weights: [5, 24, 7.5, 6, 6, 6, 6, 10, 10, 10, 10],
    dense: true,
    note: CUMULATIVE_NOTE,
    rows: numberedOrBlank(rows, 10),
  }
}

// 1.60 current batches, the template's ten columns
export function batchesTable(block, data) {
  const keys = ['course', 'batch', 'start_end', 'enrolled', 'female', 'attendance_today', 'attendance_7day', 'tms_mismatch', 'dropouts']
  const rows = filledCards(data, block.key).map((card) => cardRow(card, keys))
  return {
    heading: block.heading,
    headers: ['S.N.', 'Course Name', 'Batch No.', 'Start and End Date', 'Total Number of Enrolled Trainees',
      'Number of Female Trainees', 'Attendance on Visit Date', 'Attendance (07-day average)',
      'No. of Attendance Data-Mismatch with TMS', 'No. of Dropouts'],
    // the course column fits "Entrepreneurship" unbroken; "07/09/2026 – 24/11/2026" wraps on two
    // lines in the date column, never inside a date
    weights: [5, 16, 6, 11.5, 8, 8, 10, 10, 10, 8.5],
    dense: true,
    rows: numberedOrBlank(rows, 9),
  }
}

// 16 improvement plan: S.N. + the template's columns
export function planTable(block, data) {
  const rows = filledCards(data, block.key).map((card) => cardRow(card, block.fields.map((f) => f.key)))
  return {
    headers: ['S.N.', ...block.fields.map((field) => field.label)],
    weights: [7, 30, 33, 15, 15],
    rows: numberedOrBlank(rows, block.fields.length),
  }
}
