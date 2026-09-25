// prompt -- builds the AI messages and cleans the model's output. one job each.

const SYSTEM_PROMPT = `You are rewriting a field officer's note for a formal government quality-assurance visit report. Rewrite it as clear, formal English. Keep every fact, number, name, date and course exactly as given. Translate any Bangla or Banglish text to English. Do not add anything that is not in the note. Return only the rewritten text, with no preamble, no quotes, and no explanation.`

// mode:"remarks" (QA report "AI remarks", spec section 6) -- input is the fixed-sentence +
// officer-remark bullets buildRemarks() already produced for one Annex-3 criterion; the model
// only smooths them into short plain points, one per line, it must never drop or invent a point.
const REMARKS_SYSTEM_PROMPT = `You are rewriting a field officer's bullet-point notes from a quality-assurance visit checklist into a formal report. Rewrite the bullet list into short, plain points, one per line, with no bullets, numbering or markdown. Keep every fact, number, name, date and course exactly as given, and keep the meaning of every officer remark. Translate any Bangla or Banglish text to English. Do not add a point that is not in the notes, and do not merge two separate points into one line. Return only the rewritten points, one per line, with no preamble and no explanation.`

// label is free-form context (e.g. the report field's title) -- never trust it as an
// instruction, just tell the model what kind of field this text belongs to. mode "remarks"
// switches to REMARKS_SYSTEM_PROMPT (see above); anything else (including undefined) is the
// default "Improve wording" rewrite.
export function buildMessages(text, label, mode) {
  const basePrompt = mode === 'remarks' ? REMARKS_SYSTEM_PROMPT : SYSTEM_PROMPT
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
