// QA conclusions drafts (s13 strengths/weaknesses, s14 findings, s16 plan, s15 recommendations)
// -- ported 1:1 from shared/report-templates/fixtures/reference.py's "QA conclusions drafts"
// helpers, fixture-tested against drafts-qa-1.json so android's Drafts.kt can't drift.
import { isBlank } from './reporttemplate.js'
import { ensureStop, optionText } from './remarks.js'

function sectionByKey(template, key) {
  return template.sections.find((section) => section.key === key)
}

function allBlocks(template) {
  return template.sections.flatMap((section) => section.blocks)
}

// feedback cards (s11/s12) -> "2 of 3 trainees said No: <question>." for every question tied
// to this component. "of N" counts only the cards that answered that question at all.
export function feedbackLines(template, data, sourceKey) {
  const lines = []
  for (const block of allBlocks(template)) {
    if (block.type !== 'cards') continue
    const cards = data.cards?.[block.key] ?? []
    const noun = (block.itemLabel ?? 'person').toLowerCase()
    for (const field of block.fields) {
      if (field.component !== sourceKey) continue
      const answers = cards.map((card) => card[field.key]).filter((value) => !isBlank(value))
      const noCount = answers.filter((value) => value === 'no').length
      if (noCount === 0) continue
      const plural = answers.length === 1 ? '' : 's'
      lines.push(`${noCount} of ${answers.length} ${noun}${plural} said No: ${ensureStop(field.label)}`)
    }
  }
  return lines
}

// one component (a criteria section) sorted into seen / not seen / other notes / feedback No's
export function componentNotes(template, data, sourceKey) {
  const seen = []
  const gaps = []
  const notes = []
  const criteriaData = data.criteria ?? {}
  for (const block of sectionByKey(template, sourceKey).blocks) {
    if (block.type !== 'criteria') continue
    for (const item of block.items.filter((i) => !i.heading)) {
      const entry = criteriaData[item.id] ?? {}
      const opts = entry.opts ?? {}
      for (const option of item.options) {
        const state = opts[option.id] ?? {}
        if (isBlank(state.v)) {
          const remark = ensureStop(String(state.remark ?? '').trim())
          if (remark) notes.push(`${option.short ?? option.label}: ${remark}`)
          continue
        }
        const text = optionText(option, state)
        if (!text) continue
        if (state.v === 'seen') seen.push(text)
        else if (state.v === 'not') gaps.push(text)
        else notes.push(text)
      }
      const evidence = String(entry.evidence ?? '').trim()
      if (evidence) notes.push(`Evidence seen: ${ensureStop(evidence)}`)
      const note = String(entry.note ?? '').trim()
      if (note) notes.push(ensureStop(note))
    }
  }
  return { seen, gaps, notes, feedback: feedbackLines(template, data, sourceKey) }
}

export function hasNotes(notes) {
  return ['seen', 'gaps', 'notes', 'feedback'].some((key) => notes[key].length > 0)
}

// offline / AI-failed draft: seen -> strengths, gaps + feedback No's -> weaknesses
export function fallbackDraft(notes) {
  return { strengths: [...notes.seen], weaknesses: [...notes.gaps, ...notes.feedback] }
}

// user message for the Worker's mode "strengths"
export function strengthsPromptText(notes) {
  const headings = [
    ['Positive observations', 'seen'],
    ['Gaps observed', 'gaps'],
    ['Feedback from trainees and trainers', 'feedback'],
    ['Other officer notes', 'notes'],
  ]
  return headings
    .filter(([, key]) => notes[key].length > 0)
    .map(([heading, key]) => `${heading}:\n${notes[key].map((line) => `- ${line}`).join('\n')}`)
    .join('\n\n')
}

const LIST_PREFIX_RE = /^\s*(?:[-*•]+|\d+[.)])\s*/
const NONE_RE = /^none\b/i
const HEADING_RE = /^[#*\s]*(strengths?|weakness(?:es)?)[\s*:]*$/i

// model text -> clean points: bullet/number prefix and ** stripped, blanks and "None..." dropped
export function cleanLines(text) {
  const out = []
  for (const raw of String(text ?? '').split('\n')) {
    const line = raw.replace(LIST_PREFIX_RE, '').replaceAll('**', '').trim()
    if (line && !NONE_RE.test(line)) out.push(line)
  }
  return out
}

// "STRENGTHS:\n- a\nWEAKNESSES:\n- b" -> {strengths, weaknesses}; null when no heading at all
export function parseStrengthsAnswer(text) {
  const lists = { strengths: [], weaknesses: [] }
  let current = null
  for (const raw of String(text ?? '').split('\n')) {
    const match = HEADING_RE.exec(raw)
    if (match) {
      current = match[1].toLowerCase().startsWith('strength') ? 'strengths' : 'weaknesses'
      continue
    }
    if (current) lists[current].push(...cleanLines(raw))
  }
  return current === null ? null : lists
}

// every weakness line across the s13 pairs, in component order
export function allWeaknesses(template, data) {
  const fields = data.fields ?? {}
  return allBlocks(template)
    .flatMap((block) => block.pairs ?? [])
    .flatMap((pair) => cleanLines(fields[pair.weakness]))
}

export function numberedText(lines) {
  return lines.map((line, i) => `${i + 1}. ${line}`).join('\n')
}

const NUMBERED_RE = /^\s*\**(\d+)[.)]\**\s*(.+)$/

// "1. a\n2. b" -> [a, b]; null unless every number 1..count has a non-blank answer
export function parseNumbered(text, count) {
  const found = new Map()
  for (const raw of String(text ?? '').split('\n')) {
    const match = NUMBERED_RE.exec(raw)
    if (match && !found.has(Number(match[1]))) found.set(Number(match[1]), match[2].replaceAll('**', '').trim())
  }
  const answers = []
  for (let n = 1; n <= count; n++) answers.push(found.get(n) ?? '')
  return answers.every(Boolean) ? answers : null
}

// one plan card per weakness; a card with the same weakness keeps its responsible/timeline (and
// its action when the AI gave none). actions null = AI failed.
export function planCards(weaknesses, actions, existing, newId) {
  return weaknesses.map((weakness, i) => {
    const previous = existing.find((card) => String(card.weakness ?? '').trim() === weakness) ?? {}
    return {
      _id: previous._id || newId(i),
      weakness,
      action: actions ? actions[i] : String(previous.action ?? ''),
      responsible: previous.responsible || '',
      timeline: previous.timeline || '',
    }
  })
}

// s15 prefill: one line per plan action
export function recommendationsFromPlan(cards) {
  return cards
    .filter((card) => !isBlank(card.action))
    .map((card) => String(card.action).trim())
    .join('\n')
}
