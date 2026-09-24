// visit report template: loads shared/report-templates/surprise-v1.json (the ONLY source of
// questions -- never hardcode them here), plus card-link syncing, progress rules and new-report
// prefill logic ported 1:1 from shared/report-templates/fixtures/reference.py (the source of
// truth for these rules -- android matches it too, see fixture progress-1.json).
import surpriseV1 from '../../../shared/report-templates/surprise-v1.json'

export const TEMPLATES = { surprise: surpriseV1 }

export function templateFor(type) {
  return TEMPLATES[type] ?? null
}

// blank = null or trimmed empty string (numbers are stored as strings, spec "Report data")
export function isBlank(value) {
  return value == null || String(value).trim() === ''
}

function toneOf(field, value) {
  return field.options?.find((o) => o.id === value)?.tone
}

function* allBlocks(template) {
  for (const section of template.sections) {
    for (const block of section.blocks) yield { section, block }
  }
}

// linked cards (reference.py sync_links): a `cards` block with `linkFrom: {cards, fields}` keeps
// exactly one target card per source card that has any of `fields` filled, in source order.
// target._id = "<blockKey>:<source._id>" so every platform derives the same id; a target already
// synced to that source keeps its other answers (re-fetched via target._link), a target whose
// source disappeared is dropped. Mutates data.cards in place and returns data -- call this after
// every edit and once when a report opens (idempotent, see ReportEditor).
export function syncLinks(template, data) {
  data.cards = data.cards ?? {}
  for (const { block } of allBlocks(template)) {
    const link = block.linkFrom
    if (block.type !== 'cards' || !link) continue
    const sourceCards = data.cards[link.cards] ?? []
    const sources = sourceCards.filter((source) => link.fields.some((key) => !isBlank(source[key])))
    const existingByLink = {}
    for (const card of data.cards[block.key] ?? []) existingByLink[card._link] = card
    const synced = []
    for (const source of sources) {
      const target = { ...(existingByLink[source._id] ?? {}) }
      target._id = `${block.key}:${source._id}`
      target._link = source._id
      for (const key of link.fields) target[key] = source[key] ?? ''
      synced.push(target)
    }
    data.cards[block.key] = synced
  }
  return data
}

// counts toward progress totals if required, or if it's a segmented choice (an unmade choice is
// always a real gap, required or not -- reference.py `counts`)
function counts(field) {
  return Boolean(field.required) || field.kind === 'choice'
}

function compareMismatch(compare, card) {
  const values = compare.fields.map((key) => card[key]).filter((v) => !isBlank(v)).map(Number)
  return values.length >= 2 && new Set(values).size > 1
}

// one section's {answered, total, done, flagged} plus any customFlags text it contributed
// (countsAsFlags cards -- free-text flags, contribute 0 to totals but flag the section).
function sectionProgress(section, data) {
  let answered = 0
  let total = 0
  let flagged = false
  const customFlags = []

  for (const block of section.blocks) {
    if (block.type === 'checklist') {
      for (const item of block.items) {
        total += 1
        const answer = data.checks?.[item.id]?.answer
        if (!isBlank(answer)) answered += 1
        if (answer === 'no') flagged = true
      }
    } else if (block.type === 'fields') {
      for (const field of block.fields) {
        if (!counts(field)) continue
        total += 1
        const value = data.fields?.[field.key]
        if (!isBlank(value)) answered += 1
        if (field.kind === 'choice' && toneOf(field, value) === 'no') flagged = true
      }
    } else if (block.type === 'cards') {
      const entries = data.cards?.[block.key] ?? []
      if (block.countsAsFlags) {
        for (const card of entries) {
          const text = card[block.titleField]
          if (!isBlank(text)) {
            customFlags.push(String(text).trim())
            flagged = true
          }
        }
        continue
      }
      for (const card of entries) {
        for (const field of block.fields) {
          if (!counts(field)) continue
          total += 1
          const value = card[field.key]
          if (!isBlank(value)) answered += 1
          if (field.kind === 'choice' && toneOf(field, value) === 'no') flagged = true
        }
        if (block.compare && compareMismatch(block.compare, card)) flagged = true
      }
    } else if (block.type === 'flags') {
      const ticked = new Set(data.flags ?? [])
      if (block.items.some((item) => ticked.has(item.id))) flagged = true
    }
  }

  return { progress: { answered, total, done: total > 0 && answered === total, flagged }, customFlags }
}

// report-level progress: per-section rollups + sectionsDone/sectionsCounted (optional sections
// excluded, same as an empty section), answerCounts/unanswered over checklist items only,
// flagsTicked + customFlags (free-text flags from countsAsFlags cards, template order).
export function computeProgress(template, data) {
  const sections = {}
  let customFlags = []
  for (const section of template.sections) {
    const { progress, customFlags: found } = sectionProgress(section, data)
    sections[section.key] = progress
    customFlags = customFlags.concat(found)
  }

  const counted = template.sections
    .filter((section) => !section.optional && sections[section.key].total > 0)
    .map((section) => sections[section.key])

  const answerCounts = {}
  for (const answer of template.answers) answerCounts[answer.id] = 0
  const unanswered = []
  for (const { block } of allBlocks(template)) {
    if (block.type !== 'checklist') continue
    for (const item of block.items) {
      const answer = data.checks?.[item.id]?.answer
      if (isBlank(answer)) unanswered.push(item.id)
      else answerCounts[answer] = (answerCounts[answer] ?? 0) + 1
    }
  }

  return {
    sections,
    sectionsDone: counted.filter((s) => s.done).length,
    sectionsCounted: counted.length,
    answerCounts,
    unansweredCount: unanswered.length,
    firstUnanswered: unanswered[0] ?? null,
    flagsTicked: data.flags ?? [],
    customFlags,
  }
}

// new report: prefill fields from the visit + officer, seed every cards block with `start` blank
// rows (each carrying a fresh _id -- linked blocks always start at 0 and are populated by
// syncLinks instead), then run syncLinks once so a freshly created report is already consistent.
export function newReportData(template, visit, officerName) {
  const fields = {}
  const cards = {}
  for (const { block } of allBlocks(template)) {
    if (block.type === 'fields') {
      for (const field of block.fields) {
        if (field.prefill === 'institute') fields[field.key] = visit?.institute ?? ''
        else if (field.prefill === 'association') fields[field.key] = visit?.association ?? ''
        else if (field.prefill === 'visit_date') fields[field.key] = visit?.start_date ?? ''
        else if (field.prefill === 'officers') fields[field.key] = officerName ?? ''
      }
    } else if (block.type === 'cards') {
      cards[block.key] = Array.from({ length: block.start ?? 0 }, () => ({ _id: crypto.randomUUID() }))
    }
  }
  return syncLinks(template, { fields, checks: {}, cards, flags: [] })
}
