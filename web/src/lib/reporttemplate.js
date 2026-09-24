// visit report template: loads shared/report-templates/surprise-v1.json (the ONLY source of
// questions -- never hardcode them here), plus card-link syncing, progress rules and new-report
// prefill logic ported 1:1 from shared/report-templates/fixtures/reference.py (the source of
// truth for these rules -- android matches it too, see fixture progress-1.json).
import surpriseV1 from '../../../shared/report-templates/surprise-v1.json'

export const TEMPLATES = { surprise: surpriseV1 }

export function templateFor(type) {
  return TEMPLATES[type] ?? null
}

// blank = null or trimmed empty string (numbers are stored as strings too, so this is the one
// check every field/card/checklist value needs)
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
// source disappeared is dropped. Mutates data.cards in place and returns data. Internal helper --
// normalize() below is the one every caller (editor, exports) should run; kept as its own
// function only because reference.py factors it out the same way.
// exported anyway (not just `normalize`) because reporthtml.js/reportdocx.js already do a
// defensive `reportTemplateModule.syncLinks` lookup -- removing the export would silently stop
// their card-linking without a test failure to catch it.
function syncLinks(template, data) {
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
export { syncLinks }

// ids of today's courses (section A `courses` cards) with a non-blank course name, in card
// order -- what per-course checklist items and progress both split by.
function courseIds(data) {
  return (data.cards?.courses ?? []).filter((c) => !isBlank(c.course)).map((c) => c._id)
}

// all-blank -> "" (nothing entered yet); "na" ignored in a mix; all real values equal -> that
// value; anything else -> "partial". Only ever called once every course has *some* value.
function deriveAnswer(values) {
  if (values.some((v) => isBlank(v))) return ''
  const real = values.filter((v) => v !== 'na')
  if (real.length === 0) return 'na'
  return real.every((v) => v === real[0]) ? real[0] : 'partial'
}

// per-course checklist items (template `perCourse: true`): with 2+ named courses in section A,
// every such item is answered once per course instead of once overall. Keeps checks[item].courses
// trimmed to today's course ids, derives checks[item].answer from them ("" until every course has
// an answer), and creates a blank entry for a perCourse item that had none yet -- this can
// overwrite a stale single-course answer from before a 2nd course existed, matching reference.py.
// With 0-1 named courses this is a no-op (the item stays a plain single answer, courses ignored).
function syncPerCourse(template, data) {
  const ids = courseIds(data)
  if (ids.length < 2) return data
  data.checks = data.checks ?? {}
  for (const { block } of allBlocks(template)) {
    if (block.type !== 'checklist') continue
    for (const item of block.items) {
      if (!item.perCourse) continue
      const check = data.checks[item.id] ?? (data.checks[item.id] = { answer: '', remarks: '' })
      let previous = check.courses ?? {}
      // answered before a 2nd course existed: that answer belonged to the first course, so it
      // moves there instead of vanishing (new courses start blank)
      if (Object.keys(previous).length === 0 && !isBlank(check.answer)) previous = { [ids[0]]: check.answer }
      const courses = {}
      for (const id of ids) if (id in previous) courses[id] = previous[id]
      check.courses = courses
      check.answer = deriveAnswer(ids.map((id) => courses[id] ?? ''))
      if (check.remarks === undefined) check.remarks = ''
    }
  }
  return data
}

// the one normalisation step every client runs after each edit and once when a report opens
// (idempotent) -- reference.py `normalize`. Use this everywhere, not syncLinks/syncPerCourse
// directly.
export function normalize(template, data) {
  syncLinks(template, data)
  syncPerCourse(template, data)
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
  const courseCount = courseIds(data).length // computed once; perCourse items only split at 2+

  for (const block of section.blocks) {
    if (block.type === 'checklist') {
      for (const item of block.items) {
        total += 1
        const check = data.checks?.[item.id] ?? {}
        if (!isBlank(check.answer)) answered += 1
        if (check.answer === 'no') flagged = true
        // a mixed per-course answer (e.g. one course "no", another "yes") derives to "partial"
        // overall -- still flag the section, since a real "no" was recorded somewhere.
        const perCourseNo = item.perCourse && courseCount >= 2 && Object.values(check.courses ?? {}).includes('no')
        if (perCourseNo) flagged = true
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
    sectionsWithContent: template.sections.filter((section) => sectionHasContent(section, data)).map((section) => section.key),
  }
}

// optional sections print only when the officer filled something in them
export function sectionHasContent(section, data) {
  const checks = data.checks ?? {}
  for (const block of section.blocks) {
    if (block.type === 'fields' && block.fields.some((f) => !isBlank(data.fields?.[f.key]))) return true
    if (block.type === 'checklist') {
      for (const item of block.items) {
        const check = checks[item.id] ?? {}
        if (!isBlank(check.answer) || !isBlank(check.remarks) || Object.keys(check.courses ?? {}).length > 0) return true
      }
    }
    if (block.type === 'cards') {
      for (const card of data.cards?.[block.key] ?? []) {
        if (Object.entries(card).some(([key, value]) => !key.startsWith('_') && !isBlank(value))) return true
      }
    }
    if (block.type === 'flags' && block.items.some((i) => (data.flags ?? []).includes(i.id))) return true
  }
  return false
}

// new report: prefill fields from the visit + officer, seed every cards block with `start` blank
// rows (each carrying a fresh _id -- linked blocks always start at 0 and are populated by
// normalize instead), then normalize once so a freshly created report is already consistent.
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
  return normalize(template, { fields, checks: {}, cards, flags: [] })
}
