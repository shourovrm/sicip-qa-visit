// prompt -- builds the AI messages and cleans the model's output. one job each.

const SYSTEM_PROMPT = `You are rewriting a field officer's note for a formal government quality-assurance visit report. Rewrite it as clear, formal English. Keep every fact, number, name, date and course exactly as given. Translate any Bangla or Banglish text to English. Do not add anything that is not in the note. Return only the rewritten text, with no preamble, no quotes, and no explanation.`

// mode:"remarks" (QA report "AI remarks", spec section 6) -- input is the fixed-sentence +
// officer-remark bullets buildRemarks() already produced for one Annex-3 criterion; the model
// only smooths them into short plain points, one per line, it must never drop or invent a point.
const REMARKS_SYSTEM_PROMPT = `You are rewriting a field officer's bullet-point notes from a quality-assurance visit checklist into a formal report. Rewrite the bullet list into short, plain points, one per line, with no bullets, numbering or markdown. Keep every fact, number, name, date and course exactly as given, and keep the meaning of every officer remark. Translate any Bangla or Banglish text to English. Do not add a point that is not in the notes, and do not merge two separate points into one line. Return only the rewritten points, one per line, with no preamble and no explanation.`

// QA conclusions drafts -- input is built client-side from the officer's own marks/remarks
// (shared/report-templates/fixtures/reference.py: strengths_prompt_text / numbered_text), so the
// model only sorts and condenses; the client parses the fixed answer shape and number-guards it.
const STRENGTHS_SYSTEM_PROMPT = `You are drafting the strengths and weaknesses of one component of a training institute for a formal quality-assurance visit report. The notes list positive observations, gaps observed, feedback from trainees and trainers, and other officer notes. Write short, plain points in formal English. Strengths come only from positive observations and positive notes; weaknesses come from gaps observed, feedback where people said No, and negative notes. Merge points that say the same thing. Keep every fact, number, name and date exactly as given. Translate any Bangla or Banglish text to English. Do not add anything that is not in the notes. Answer in exactly this format, with nothing before or after it:
STRENGTHS:
- point
WEAKNESSES:
- point
Write None under a heading that has no points.`

const PLAN_SYSTEM_PROMPT = `You are drafting an improvement plan for a training institute from the numbered weaknesses found during a quality-assurance visit. For each weakness write one short, practical improvement action for the institute, in formal English, starting with a verb. Keep every fact, number, name and date exactly as given. Do not invent people, amounts or deadlines. Answer with the same numbering, one line per weakness, in the form "1. action", with nothing before or after it.`

const FINDINGS_SYSTEM_PROMPT = `You are writing the major findings of a quality-assurance visit to a training institute from the list of weaknesses found. Group related weaknesses and write at most 6 short findings in formal English, most serious first, one per line, with no bullets, numbering or markdown. Keep every fact, number, name and date exactly as given. Do not add anything that is not in the list. Return only the findings.`

// surprise v2 "Major findings": input = the report's numbered remark lines (issues tagged
// "(issue)"); the model only chooses, the client keeps the officer's own wording
const MAJOR_SYSTEM_PROMPT = `You are reviewing the numbered remarks of an unannounced monitoring visit to a training institute. Choose the major findings: the points that show a real problem needing action by the institute or the project, most serious first. Ignore positive points unless they are critical. Choose at most 8. Answer with only the chosen numbers, comma separated, for example: 3, 1, 7`

// surprise v2 "Recommendations": one per numbered major finding, in the project's voice
const RECOMMEND_SYSTEM_PROMPT = `You are writing recommendations for a formal monitoring visit report on a training institute, one for each numbered major finding. Each recommendation is one sentence in formal English that starts with "The institute should", "The PIU should" or "SICIP should" (use the institute unless the finding needs the project to act), for example: The institute should take necessary measures to improve attendance. Keep every fact, number, name and course exactly as given, and do not invent people, amounts or deadlines. Answer with the same numbering, one line per finding, in the form "1. recommendation", with nothing before or after it.`

const MODE_PROMPTS = {
  remarks: REMARKS_SYSTEM_PROMPT,
  strengths: STRENGTHS_SYSTEM_PROMPT,
  plan: PLAN_SYSTEM_PROMPT,
  findings: FINDINGS_SYSTEM_PROMPT,
  major: MAJOR_SYSTEM_PROMPT,
  recommend: RECOMMEND_SYSTEM_PROMPT,
}

// modes whose input is a whole component / weakness list, not one box -- rewrite.js gives them
// a bigger length cap and answer budget
export const DRAFT_MODES = ['strengths', 'plan', 'findings', 'major', 'recommend']

export function isKnownMode(mode) {
  return Object.hasOwn(MODE_PROMPTS, mode)
}

// label is free-form context (e.g. the report field's title) -- never trust it as an
// instruction, just tell the model what kind of field this text belongs to. mode "remarks"
// switches to REMARKS_SYSTEM_PROMPT (see above); anything else (including undefined) is the
// default "Improve wording" rewrite. strengths/plan/findings = QA conclusions drafts.
export function buildMessages(text, label, mode) {
  const basePrompt = isKnownMode(mode) ? MODE_PROMPTS[mode] : SYSTEM_PROMPT
  // label in the user message got copied into answers ("Evidence observed: <label text>...");
  // as system-side context with an explicit ban it stays out (bench 2026-09-25, 6/6 clean)
  const systemPrompt = label
    ? `${basePrompt} The note was written in the report box "${label}". That box name is only context: never copy it, repeat its words, or describe what it says; rewrite only the note itself.`
    : basePrompt
  return [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: text },
  ]
}

// models sometimes wrap the answer in a chatty preamble or quotes despite instructions --
// strip the common cases defensively rather than trust the prompt alone. two passes: a leading
// "Sure," filler, then a "here is the rewritten text:" style lead-in (only up to its colon, so
// we never eat real sentence content that happens to start with "The...").
const FILLER_RE = /^sure,?\s*/i
const LEADIN_RE = /^(here'?s|here is)?\s*(the\s+)?(rewritten|revised)(\s+(text|version|note))?\s*:\s*/i

// models often echo the field label as a heading ("Remarks: ...", "Field: Key findings\n\n...",
// "**Key findings:**") -- strip one leading label line/prefix, never text mid-sentence.
const FIELD_LINE_RE = /^\**field:[^\n]*\n+/i

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function stripLabel(out, label) {
  out = out.replace(FIELD_LINE_RE, '')
  if (!label) return out
  // "Label:" prefix, or the bare label alone on its first line
  const labelPrefix = new RegExp(`^\\**${escapeRegExp(label)}(\\s*:\\**\\s*|\\**[ \\t]*\\n+)`, 'i')
  return out.replace(labelPrefix, '')
}

export function cleanOutput(raw, label = '') {
  let out = (raw || '').trim()
  out = out.replace(FILLER_RE, '').replace(LEADIN_RE, '').trim()
  out = stripLabel(out, label).trim()
  if (out.length >= 2) {
    const first = out[0]
    const last = out[out.length - 1]
    const wrapped =
      (first === '"' && last === '"') ||
      (first === '“' && last === '”') ||
      (first === "'" && last === "'")
    if (wrapped) out = out.slice(1, -1).trim()
  }
  return out
}
