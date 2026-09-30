// visit report template: loads shared/report-templates/{surprise,qa}-v1.json (the ONLY source of
// questions -- never hardcode them here), plus card-link syncing, progress rules and new-report
// prefill logic ported 1:1 from shared/report-templates/fixtures/reference.py (the source of
// truth for these rules -- android matches it too, see fixtures/progress-1.json,
// fixtures/remarks-1.json, fixtures/progress-qa-1.json).
import surpriseV1 from '../../../shared/report-templates/surprise-v1.json'
import surpriseV2 from '../../../shared/report-templates/surprise-v2.json'
import qaV1 from '../../../shared/report-templates/qa-v1.json'
import qaV2 from '../../../shared/report-templates/qa-v2.json'

// the `reports.type` DB column predates the QA template (check constraint 'surprise'|'monitoring'
// -- see supabase/migrations/010_reports.sql) -- 'monitoring' is qa-v1's DB-side type, so a QA
// report's row keeps type:'monitoring' while its template id/short/visitType are 'qa'. templateFor
// accepts either name so callers can pass a DB row's `type` directly.
// the LATEST template per type -- what a new report is created with
export const TEMPLATES = { surprise: surpriseV2, qa: qaV2, monitoring: qaV2 }

// older versions an existing report row may still be on (template_version column)
const OLDER_VERSIONS = { surprise: { 1: surpriseV1 }, qa: { 1: qaV1 }, monitoring: { 1: qaV1 } }

// version omitted = latest; a report row passes its own template_version
export function templateFor(type, version) {
  const latest = TEMPLATES[type] ?? null
  if (version == null || latest?.version === version) return latest
  return OLDER_VERSIONS[type]?.[version] ?? latest
}

// an old-format report the editor offers to convert (lib/reportconvert.js, lib/qaconvert.js)
export function needsConversion(report) {
  const latest = TEMPLATES[report?.type]
  return Boolean(latest) && Number(report.template_version) < latest.version
}

// the DB `type` column value for a template id (inverse of templateFor's 'monitoring' alias).
export function dbTypeFor(templateId) {
  return templateId === 'qa' ? 'monitoring' : templateId
}

// blank = null or trimmed empty string (numbers are stored as strings too, so this is the one
// check every field/card/checklist value needs)
export function isBlank(value) {
  return value == null || String(value).trim() === ''
}

// linked cards with something typed beyond the copied course/batch -- a course nobody was
// interviewed in prints no blank column
export function answeredCards(block, data) {
  const linked = new Set(block.linkFrom?.fields ?? [])
  const cards = data.cards?.[block.key] ?? []
  return cards.filter((card) => block.fields.some((f) => !linked.has(f.key) && !isBlank(card[f.key])))
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

// suggestions for a courseRef field, from its `optionsFrom` cards block:
// - optionsField: that key's distinct values (qa-v2 contracts' organisation, batches' batch),
//   narrowed by filterBy {field, value} to source cards whose `field` equals this card's `value`
//   (feedback Batch -> only the batches of the card's Trade)
// - optionsPart "course": distinct course names only
// - otherwise "course · batch" per source card with a course name
export function refOptions(field, data, card = {}) {
  let sources = data.cards?.[field.optionsFrom] ?? []
  const distinct = (values) => [...new Set(values.map((v) => String(v ?? '').trim()).filter(Boolean))]
  if (field.optionsField) {
    const filter = field.filterBy
    const wanted = filter ? String(card[filter.value] ?? '').trim() : ''
    if (filter && wanted) sources = sources.filter((source) => String(source[filter.field] ?? '').trim() === wanted)
    return distinct(sources.map((source) => source[field.optionsField]))
  }
  if (field.optionsPart === 'course') return distinct(sources.map((source) => source.course))
  return sources
    .filter((source) => !isBlank(source.course))
    .map((source) => (source.batch ? `${source.course} · ${source.batch}` : source.course))
}

// qa-v2 "showIf": {field, in:[...]} -- a field/block only shows (and only counts) while the
// named value (top-level field, or the same card's field) is one of `in`
export function isShown(thing, values) {
  const rule = thing.showIf
  if (!rule) return true
  return rule.in.includes(String(values?.[rule.field] ?? ''))
}

// gapPct set (surprise v2): only when fields[0] (headcount) is more than gapPct% below any
// other field (7-day averages); otherwise any difference
export function compareMismatch(compare, card) {
  if (compare.gapPct != null) {
    if (isBlank(card[compare.fields[0]])) return false
    const first = Number(card[compare.fields[0]])
    return compare.fields.slice(1).some((key) => {
      if (isBlank(card[key])) return false
      const other = Number(card[key])
      return other > 0 && first < other * (1 - compare.gapPct / 100)
    })
  }
  const values = compare.fields.map((key) => card[key]).filter((v) => !isBlank(v)).map(Number)
  return values.length >= 2 && new Set(values).size > 1
}

// items in a `criteria` block that are actual questions (heading:true rows carry no options).
function criteriaItems(block) {
  return block.items.filter((item) => !item.heading)
}

// a section with any `criteria` block (QA report) is counted purely by its options -- every
// other block-type rule for that section is ignored (spec section 5). total/answered span every
// criteria block in the section (section 8 has two: its main table + the 8.1 sub-table), notSeen
// counts options marked "not" and is only attached when > 0 (hub subtitle "· K not seen").
function criteriaSectionProgress(section, data) {
  let answered = 0
  let total = 0
  let notSeen = 0
  const criteriaData = data.criteria ?? {}
  for (const block of section.blocks) {
    if (block.type !== 'criteria') continue
    for (const item of criteriaItems(block)) {
      const entry = criteriaData[item.id] ?? {}
      const opts = entry.opts ?? {}
      for (const option of item.options) {
        total += 1
        const v = opts[option.id]?.v ?? ''
        if (!isBlank(v)) answered += 1
        if (v === 'not') notSeen += 1
      }
    }
  }
  const progress = { answered, total, done: total > 0 && answered === total, flagged: notSeen > 0 }
  if (notSeen) progress.notSeen = notSeen
  return { progress, customFlags: [] }
}

// one section's {answered, total, done, flagged} plus any customFlags text it contributed
// (countsAsFlags cards -- free-text flags, contribute 0 to totals but flag the section).
function sectionProgress(section, data) {
  if (section.blocks.some((b) => b.type === 'criteria')) return criteriaSectionProgress(section, data)

  let answered = 0
  let total = 0
  let flagged = false
  const customFlags = []
  const courseCount = courseIds(data).length // computed once; perCourse items only split at 2+

  for (const block of section.blocks) {
    if (!isShown(block, data.fields)) continue
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
        if (!counts(field) || !isShown(field, data.fields)) continue
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
          if (!counts(field) || !isShown(field, card)) continue
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
    } else if (block.type === 'findings') {
      // one item: at least one major finding picked (remarks blocks count nothing)
      total += 1
      if ((data.findings ?? []).some((f) => !isBlank(f.text))) answered += 1
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
  for (const answer of template.answers ?? []) answerCounts[answer.id] = 0
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
    if (block.type === 'criteria') {
      const criteriaData = data.criteria ?? {}
      for (const item of criteriaItems(block)) {
        const entry = criteriaData[item.id]
        if (!entry) continue
        const opts = entry.opts ?? {}
        if (Object.values(opts).some((o) => !isBlank(o?.v) || !isBlank(o?.remark) || !isBlank(o?.detail))) return true
        if (!isBlank(entry.evidence) || !isBlank(entry.note)) return true
        if ((entry.ticks ?? []).length > 0 || (entry.evidenceRefs ?? []).length > 0) return true
      }
    }
    if (block.type === 'flags' && block.items.some((i) => (data.flags ?? []).includes(i.id))) return true
    if (block.type === 'remarks' && !isBlank(data.remarks?.[block.key]?.text)) return true
    if (block.type === 'findings' && (data.findings ?? []).length > 0) return true
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
        else if (field.prefill === 'visit_end') fields[field.key] = visit?.end_date ?? ''
        else if (field.prefill === 'officers') fields[field.key] = officerName ?? ''
      }
    } else if (block.type === 'cards') {
      cards[block.key] = Array.from({ length: block.start ?? 0 }, () => ({ _id: crypto.randomUUID() }))
      // surprise v2 visiting officers: the first card is the officer writing it
      if (block.prefill === 'officers' && cards[block.key].length > 0) cards[block.key][0].name = officerName ?? ''
    }
  }
  return normalize(template, { fields, checks: {}, cards, flags: [], criteria: {} })
}
