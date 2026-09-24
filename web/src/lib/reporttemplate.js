// visit report template: loads shared/report-templates/surprise-v1.json (the ONLY source of
// questions -- never hardcode them here), plus the progress rules and new-report prefill logic
// shared 1:1 with android (see docs/superpowers/specs/2026-09-24-reports.md "Contract").
import surpriseV1 from '../../../shared/report-templates/surprise-v1.json'

export const TEMPLATES = { surprise: surpriseV1 }

export function templateFor(type) {
  return TEMPLATES[type] ?? null
}

// blank = null or trimmed empty string (numbers are stored as strings, spec "Report data")
function isBlank(value) {
  return value == null || (typeof value === 'string' && value.trim() === '')
}

function toneOf(field, value) {
  return field.options?.find((o) => o.id === value)?.tone
}

// section-by-section {answered, total, done, flagged} plus report-level rollups. Rules live in
// the spec "Progress rules" -- fixture shared/report-templates/fixtures/progress-1.json proves
// this matches android's ReportProgress.kt bit for bit. Kept as one function (not split per
// block type) so the accumulation order (sections -> blocks -> items, in template order) is
// obvious and matches how unansweredCount/firstUnanswered are collected.
export function computeProgress(template, data) {
  const fields = data.fields ?? {}
  const checks = data.checks ?? {}
  const cards = data.cards ?? {}
  const flagsTicked = data.flags ?? []

  const answerCounts = {}
  for (const answer of template.answers) answerCounts[answer.id] = 0

  const unansweredChecklistIds = [] // template order, across all sections
  const sections = {}

  for (const section of template.sections) {
    let answered = 0
    let total = 0
    let flagged = false

    for (const block of section.blocks) {
      if (block.type === 'checklist') {
        for (const item of block.items) {
          total += 1
          const answer = checks[item.id]?.answer
          if (isBlank(answer)) {
            unansweredChecklistIds.push(item.id)
            continue
          }
          answered += 1
          if (answer in answerCounts) answerCounts[answer] += 1
          if (answer === 'no') flagged = true
        }
      } else if (block.type === 'fields') {
        for (const field of block.fields) {
          if (!field.required && field.kind !== 'choice') continue
          total += 1
          const value = fields[field.key]
          if (isBlank(value)) continue
          answered += 1
          if (field.kind === 'choice' && toneOf(field, value) === 'no') flagged = true
        }
      } else if (block.type === 'cards') {
        const entries = cards[block.key] ?? []
        for (const card of entries) {
          for (const field of block.fields) {
            if (field.kind !== 'choice') continue
            total += 1
            const value = card[field.key]
            if (isBlank(value)) continue
            answered += 1
            if (toneOf(field, value) === 'no') flagged = true
          }
          if (block.compare) {
            const present = block.compare.fields.map((key) => card[key]).filter((v) => !isBlank(v)).map(Number)
            if (present.length >= 2 && !present.every((n) => n === present[0])) flagged = true
          }
        }
      } else if (block.type === 'flags') {
        if (block.items.some((item) => flagsTicked.includes(item.id))) flagged = true
      }
    }

    sections[section.key] = { answered, total, done: total > 0 && answered === total, flagged }
  }

  const sectionValues = Object.values(sections)
  return {
    sections,
    sectionsDone: sectionValues.filter((s) => s.total > 0 && s.done).length,
    sectionsCounted: sectionValues.filter((s) => s.total > 0).length,
    answerCounts,
    unansweredCount: unansweredChecklistIds.length,
    firstUnanswered: unansweredChecklistIds[0] ?? null,
    flagsTicked,
  }
}

// new report: prefill fields from the visit + officer, seed cards[key] with `start` empty rows.
// visit = a visits row, officerName = the field officer's own name (no designation column exists
// yet, so "officers" prefill is name-only).
export function newReportData(template, visit, officerName) {
  const fields = {}
  const cards = {}
  for (const section of template.sections) {
    for (const block of section.blocks) {
      if (block.type === 'fields') {
        for (const field of block.fields) {
          if (field.prefill === 'institute') fields[field.key] = visit?.institute ?? ''
          else if (field.prefill === 'association') fields[field.key] = visit?.association ?? ''
          else if (field.prefill === 'visit_date') fields[field.key] = visit?.start_date ?? ''
          else if (field.prefill === 'officers') fields[field.key] = officerName ?? ''
        }
      } else if (block.type === 'cards') {
        cards[block.key] = Array.from({ length: block.start ?? 0 }, () => ({}))
      }
    }
  }
  return { fields, checks: {}, cards, flags: [] }
}
