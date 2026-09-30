// QA v2 evidence register: one list per report (data.evidence = [{_id, name, no}]), each criteria
// item points at entries by id (data.criteria[item].evidenceRefs). An entry's number is fixed
// where it is first added ("7.1b.a" = section 7, criterion 1 b), first evidence there) and never
// changes, so documents the officer already labelled at the institute keep matching the report.
// 1:1 port of android domain/report/Evidence.kt.

const clean = (value) => String(value ?? '').trim()
const sameName = (a, b) => clean(a).toLowerCase() === clean(b).toLowerCase()

export function evidenceList(data) {
  return data.evidence ?? []
}

export function evidenceRefs(data, itemId) {
  return data.criteria?.[itemId]?.evidenceRefs ?? []
}

// "2.3" for a numbered criterion, "7.1b" for sub-criterion b) under 7's "1."
export function criteriaPath(section, block, item) {
  let main = ''
  for (const candidate of block.items) {
    const number = /^(\d+)\./.exec(clean(candidate.no))
    if (number) main = number[1]
    if (candidate.id !== item.id) continue
    const letter = /^([a-z])\)/.exec(clean(candidate.no))
    return letter ? `${section.number}.${main}${letter[1]}` : `${section.number}.${main}`
  }
  return String(section.number ?? '')
}

// first free suffix under this path: a, b, ... z, then 27, 28, ... (26+ items on one criterion
// never happens in practice; numbers just keep it unique)
function nextNumber(list, path) {
  const taken = new Set(list.map((entry) => entry.no))
  for (let index = 0; ; index++) {
    const suffix = index < 26 ? String.fromCharCode(97 + index) : String(index + 1)
    const candidate = `${path}.${suffix}`
    if (!taken.has(candidate)) return candidate
  }
}

function withRefs(data, itemId, refs) {
  const criteria = { ...(data.criteria ?? {}) }
  criteria[itemId] = { ...(criteria[itemId] ?? {}), evidenceRefs: refs }
  return { ...data, criteria }
}

// add evidence `name` to an item: an entry with the same name (any case) is reused with its
// number; otherwise a new entry is numbered at this item's path
export function withEvidenceAdded(data, path, itemId, name, newId = () => crypto.randomUUID()) {
  const trimmed = clean(name)
  if (!trimmed) return data
  const list = evidenceList(data)
  let entry = list.find((candidate) => sameName(candidate.name, trimmed))
  let nextData = data
  if (!entry) {
    entry = { _id: newId(), name: trimmed, no: nextNumber(list, path) }
    nextData = { ...data, evidence: [...list, entry] }
  }
  const refs = evidenceRefs(nextData, itemId)
  if (refs.includes(entry._id)) return nextData
  return withRefs(nextData, itemId, [...refs, entry._id])
}

// removing from one item keeps the register entry (and its number) for any later re-use
export function withEvidenceRemoved(data, itemId, evidenceId) {
  return withRefs(data, itemId, evidenceRefs(data, itemId).filter((id) => id !== evidenceId))
}

// the item's evidence in the order it was added, as {_id, name, no}
export function itemEvidence(data, itemId) {
  const byId = new Map(evidenceList(data).map((entry) => [entry._id, entry]))
  return evidenceRefs(data, itemId).map((id) => byId.get(id)).filter(Boolean)
}

export function evidenceLabel(entry) {
  return `${entry.no} – ${entry.name}`
}

// names offered while typing: this report's register (not already on the item), the app's own
// tables (cards blocks with evidenceName), then the template's Word-table names; deduped by name
export function evidenceSuggestions(template, data, itemId, query = '') {
  const onItem = new Set(itemEvidence(data, itemId).map((entry) => clean(entry.name).toLowerCase()))
  const tableNames = template.sections.flatMap((section) => section.blocks)
    .filter((block) => block.evidenceName)
    .map((block) => block.evidenceName)
  const names = [...evidenceList(data).map((entry) => entry.name), ...tableNames, ...(template.evidenceSuggestions ?? [])]
  const needle = clean(query).toLowerCase()
  const seen = new Set()
  const out = []
  for (const name of names) {
    const key = clean(name).toLowerCase()
    if (!key || seen.has(key) || onItem.has(key)) continue
    seen.add(key)
    if (needle && !key.includes(needle)) continue
    out.push(name)
  }
  return out
}

// every entry some item still points at, in number order -- the report's closing evidence list
export function usedEvidence(data) {
  const used = new Set(Object.values(data.criteria ?? {}).flatMap((entry) => entry?.evidenceRefs ?? []))
  return evidenceList(data)
    .filter((entry) => used.has(entry._id))
    .sort((a, b) => a.no.localeCompare(b.no, undefined, { numeric: true }))
}
