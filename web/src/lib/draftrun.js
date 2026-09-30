// QA conclusions drafts: one Worker call each, falling back to the officer's own marks whenever
// the AI is unavailable, errors, answers in the wrong shape or changes a number. Callers get
// {..., usedFallback} and decide how to show/apply it; nothing here writes report data.
import { draftText } from './rewrite.js'
import { outputKeepsNumbers } from './remarks.js'
import {
  componentNotes, fallbackDraft, strengthsPromptText, parseStrengthsAnswer,
  allWeaknesses, cleanLines, numberedText, parseNumbered, planCards,
} from './drafts.js'
import { fallbackMajorFindings, fallbackRecommendations, parseMajorAnswer } from './sectionremarks.js'

// any throw (offline, quota, auth, 5xx) or a changed number -> null, caller falls back
async function askModel(input, mode) {
  try {
    const answer = await draftText(input, mode)
    return outputKeepsNumbers(input, answer) ? answer : null
  } catch {
    return null
  }
}

// s13: one component pair -> {strengths:[], weaknesses:[], usedFallback}
export async function draftComponent(template, data, pair) {
  const notes = componentNotes(template, data, pair.source)
  const parsed = parseStrengthsAnswer(await askModel(strengthsPromptText(notes), 'strengths'))
  if (parsed) return { ...parsed, usedFallback: false }
  return { ...fallbackDraft(notes), usedFallback: true }
}

// s14: weaknesses -> a few major findings
export async function draftFindings(template, data) {
  const weaknesses = allWeaknesses(template, data)
  const input = weaknesses.map((line) => `- ${line}`).join('\n')
  const lines = cleanLines(await askModel(input, 'findings'))
  if (lines.length > 0) return { lines, usedFallback: false }
  return { lines: weaknesses, usedFallback: true }
}

// s16: weaknesses -> plan cards, keeping responsible/timeline of matching existing cards
export async function draftPlan(template, data, existingCards) {
  const weaknesses = allWeaknesses(template, data)
  const actions = parseNumbered(await askModel(numberedText(weaknesses), 'plan'), weaknesses.length)
  const cards = planCards(weaknesses, actions, existingCards, () => crypto.randomUUID())
  return { cards, usedFallback: actions === null }
}

// surprise v2: AI picks the major findings out of every remarks line (issues first) ->
// {findings:[{src,text}], usedFallback}; fallback = every issue line
export async function draftMajorFindings(candidates) {
  const input = numberedText(candidates.map((l) => (l.neg ? `(issue) ${l.text}` : l.text)))
  const picked = parseMajorAnswer(await askModel(input, 'major'), candidates.length)
  if (picked) return { findings: picked.map((i) => ({ src: candidates[i].text, text: candidates[i].text })), usedFallback: false }
  return { findings: fallbackMajorFindings(candidates), usedFallback: true }
}

// surprise v2: "The institute/PIU/SICIP should ..." per major finding
export async function draftRecommendations(findings) {
  const lines = cleanLines(await askModel(numberedText(findings), 'recommend'))
  if (lines.length > 0) return { lines, usedFallback: false }
  return { lines: fallbackRecommendations(findings), usedFallback: true }
}

export const FALLBACK_NOTICE = 'AI unavailable — drafted from your marks'
