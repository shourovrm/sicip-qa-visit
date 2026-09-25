// QA-report `criteria` block remarks builder -- ported 1:1 from
// shared/report-templates/fixtures/reference.py's build_remarks/printed_remarks (spec section 4).
// Fixture-tested against shared/report-templates/fixtures/remarks-1.json (remarks.test.js) so
// this file and android's Remarks.kt can never drift from the reference implementation.
import { isBlank } from './reporttemplate.js'

// "" stays ""; otherwise append "." unless the text already ends with . ! or ?
export function ensureStop(text) {
  if (text === '') return ''
  return /[.!?]$/.test(text) ? text : `${text}.`
}

// an option's fixed sentence can carry ONE `{...}` group with a `$` placeholder for its detail
// box (e.g. "Fire extinguishers are available{, last examined on $}."); this matches the first
// (only) such group.
const DETAIL_GROUP_RE = /\{([^}]*)\}/

// one criteria item ({id, text, options:[{id,label,short?,seen,not,na}]}) + its
// data.criteria[item.id] entry ({opts:{optId:{v,detail,remark}}, evidence, note, ai}) -> the
// ordered list of Remarks bullets built from fixed sentences + officer text, before any AI
// rewrite. Order: every option in template order, then "Evidence seen: ...", then the free note.
export function buildRemarks(item, entry) {
  const bullets = []
  const opts = entry.opts ?? {}
  for (const option of item.options ?? []) {
    const state = opts[option.id] ?? {}
    const v = state.v ?? ''
    const remark = ensureStop(String(state.remark ?? '').trim())
    if (isBlank(v)) {
      // a remark typed on an option nobody marked still needs its own bullet, named by the
      // option's short label (falling back to its full label) so the point stays identifiable.
      if (remark) bullets.push(`${option.short ?? option.label}: ${remark}`)
      continue
    }
    let sentence = option[v] ?? ''
    const detail = String(state.detail ?? '').trim()
    // the `{...}` group is kept (with $ substituted) only when its detail box has text; blank
    // detail drops the WHOLE group, including its own leading punctuation/spacing.
    sentence = sentence.replace(DETAIL_GROUP_RE, (_match, part) => (detail ? part.replace('$', detail) : ''))
    const joined = [sentence, remark].filter(Boolean).join(' ')
    if (joined) bullets.push(joined)
  }
  const evidence = String(entry.evidence ?? '').trim()
  if (evidence) bullets.push(`Evidence seen: ${ensureStop(evidence)}`)
  const note = String(entry.note ?? '').trim()
  if (note) bullets.push(ensureStop(note))
  return bullets
}

// what actually prints in the Remarks cell/preview: the AI rewrite (entry.ai.text) replaces the
// fixed bullets ONLY while it is still fresh -- entry.ai.source must equal the CURRENT bullets
// joined by "\n". Any edit to an option/remark/detail/evidence/note since the AI ran changes that
// join, so the fixed bullets show again until "AI remarks" is re-run for this item.
export function printedRemarks(item, entry) {
  const bullets = buildRemarks(item, entry)
  const ai = entry.ai ?? {}
  const aiText = String(ai.text ?? '')
  const aiSource = String(ai.source ?? '')
  if (aiText.trim() && aiSource === bullets.join('\n')) {
    return aiText.split('\n').map((line) => line.trim()).filter(Boolean)
  }
  return bullets
}
