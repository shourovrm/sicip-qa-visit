// anonymous trainee/trainer feedback (qa s11/s12) as print tables: questions down, respondents
// across ("Trainee 1", "Trainee 2" -- never names), max 4 respondents per table so columns stay
// readable on A4, min 2 columns so a blank form still has room. Longtext answers print as a
// "Trainee 1: ..." list under the tables instead of squeezing into a narrow cell.
const PER_TABLE = 4
const MIN_COLUMNS = 2

function answerLabel(field, value) {
  if (value === undefined || value === null || String(value).trim() === '') return ''
  if (field.kind !== 'choice') return String(value)
  return field.options.find((option) => option.id === value)?.label ?? String(value)
}

// -> {tables: [{headers: [..], rows: [[label, ...answers]]}], comments: ["Trainee 1: ..."]}
export function feedbackGrid(block, entries) {
  const respondentCount = Math.max(entries.length, MIN_COLUMNS)
  const respondents = Array.from({ length: respondentCount }, (_, i) => ({ name: `${block.itemLabel} ${i + 1}`, card: entries[i] ?? {} }))
  const rowFields = block.fields.filter((field) => field.kind !== 'longtext')
  const commentFields = block.fields.filter((field) => field.kind === 'longtext')

  const tables = []
  for (let start = 0; start < respondents.length; start += PER_TABLE) {
    const group = respondents.slice(start, start + PER_TABLE)
    tables.push({
      headers: ['Question', ...group.map((respondent) => respondent.name)],
      rows: rowFields.map((field) => [field.label, ...group.map((respondent) => answerLabel(field, respondent.card[field.key]))]),
    })
  }

  const comments = []
  for (const respondent of respondents) {
    for (const field of commentFields) {
      const text = String(respondent.card[field.key] ?? '').trim()
      if (text) comments.push(`${respondent.name}: ${text}`)
    }
  }
  return { tables, comments }
}
