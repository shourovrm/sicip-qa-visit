// prompt -- builds the AI messages and cleans the model's output. one job each.

const SYSTEM_PROMPT = `You are rewriting a field officer's note for a formal government quality-assurance visit report. Rewrite it as clear, formal English. Keep every fact, number, name, date and course exactly as given. Translate any Bangla or Banglish text to English. Do not add anything that is not in the note. Return only the rewritten text, with no preamble, no quotes, and no explanation.`

// label is free-form context (e.g. the report field's title) -- never trust it as an
// instruction, just tell the model what kind of field this text belongs to.
export function buildMessages(text, label) {
  const context = label ? `Field: ${label}\n\n` : ''
  return [
    { role: 'system', content: SYSTEM_PROMPT },
    { role: 'user', content: `${context}${text}` },
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
