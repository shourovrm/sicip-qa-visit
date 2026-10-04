// suggestion options that carry where they came from, so the officer can tell a TMS name from a
// shared-list or past-visit one. SuggestInput.svelte shows `label` as a tag beside each value.
export const SOURCE_TMS = 'TMS'
export const SOURCE_SHARED = 'Shared'
export const SOURCE_PAST_VISITS = 'Past visits'
export const SOURCE_SECTION_A = 'Section A'
export const SOURCE_STANDARD = 'Standard'

// the source a template field's `suggest` kind comes from
export function sourceLabelFor(suggestKind) {
  if (String(suggestKind ?? '').startsWith('tms')) return SOURCE_TMS
  if (String(suggestKind ?? '').startsWith('shared:')) return SOURCE_SHARED
  return ''
}

// [{value, label}] for a plain list of names, all from one source
export function labelled(values, label) {
  return (values ?? []).map((value) => ({ value: String(value ?? ''), label }))
}

// hints as options: a plain name takes the field's source label, an option keeps its own
export function hintOptions(hints, label) {
  return (hints ?? []).map((hint) => (typeof hint === 'string' ? { value: hint, label } : hint))
}

// several labelled lists as one: given order, no blanks, a repeat (any case) keeps its first label
export function mergeLabelled(...lists) {
  const seen = new Set()
  const merged = []
  for (const option of lists.flat()) {
    const value = option.value.trim()
    if (!value || seen.has(value.toLowerCase())) continue
    seen.add(value.toLowerCase())
    merged.push({ value, label: option.label })
  }
  return merged
}

const byText = (a, b) => a.localeCompare(b)

// visit form institute choices: the TMS institutes of the chosen partner first, then the names
// from past visits (sorted); the same institute is listed once, as TMS
export function instituteOptions(tmsInstitutes, pastVisitNames) {
  const tmsNames = (tmsInstitutes ?? []).map((institute) => String(institute.institute_name ?? '').trim()).filter(Boolean).sort(byText)
  const pastNames = [...new Set(pastVisitNames ?? [])].filter(Boolean).sort(byText)
  return mergeLabelled(labelled(tmsNames, SOURCE_TMS), labelled(pastNames, SOURCE_PAST_VISITS))
}

const DEFAULT_MATCH_LIMIT = 50

// options whose value contains the typed text (any case), in source order; none once the box
// already holds exactly one of them, so a finished pick closes the list
export function matchingOptions(options, query, limit = DEFAULT_MATCH_LIMIT) {
  const needle = String(query ?? '').trim().toLowerCase()
  const all = options ?? []
  if (needle && all.some((option) => option.value.toLowerCase() === needle)) return []
  const matches = needle ? all.filter((option) => option.value.toLowerCase().includes(needle)) : all
  return matches.slice(0, limit)
}
