// surprise v2 templated remarks -- 1:1 port of android domain/report/SectionRemarks.kt, both
// checked against shared/report-templates/fixtures/remarks-surprise-2.json.
// A `remarks` block's lines come from the blocks above it in the same section (back to the
// previous remarks block): checklist items via item.says[answer] (per course when the courses
// answered differently), cards via block.says (one sentence per card) + choice fields' own
// says (prefixed by block.sayPrefix, followed by their `noteFor` box), fields blocks via a
// choice field's says. Every line is {text, neg}: neg = an issue (answer no/partial, a
// no/partial tone, always:"neg" or a negWhen rule).
import { ensureStop } from './remarks.js'
import { isBlank } from './reporttemplate.js'

const NEG = new Set(['no', 'partial'])
const PLACEHOLDER = /\{(\w+)\}/g
const INNER_SEGMENT = /\[([^[\]]*)\]/

const text = (card, key) => String(card?.[key] ?? '').trim()

// fill "{a} [x {b}]": innermost [..] first, kept only when all its keys are filled; a blank
// key outside any [..] -> null. Values go in as private-use markers so officer text holding
// "[" or "{" is never read as template syntax.
export function fillSays(template, lookup) {
  const values = []
  const substitute = (part) => {
    let missing = false
    const out = part.replace(PLACEHOLDER, (_m, key) => {
      const value = String(lookup(key) ?? '').trim()
      if (!value) {
        missing = true
        return ''
      }
      values.push(value)
      return `${values.length - 1}`
    })
    return missing ? null : out
  }
  let body = template
  for (let match = INNER_SEGMENT.exec(body); match; match = INNER_SEGMENT.exec(body)) {
    body = body.slice(0, match.index) + (substitute(match[1]) ?? '') + body.slice(match.index + match[0].length)
  }
  const filled = substitute(body)
  if (filled == null) return null
  const restored = filled.replace(/(\d+)/g, (_m, i) => values[Number(i)])
  return ensureStop(restored.replace(/\s+/g, ' ').trim()) || null
}

function number(value) {
  const n = Number(String(value ?? '').trim().replace(/,/g, ''))
  return String(value ?? '').trim() === '' || Number.isNaN(n) ? null : n
}

function ruleMatches(rule, card) {
  const value = number(card[rule.field])
  if (value == null) return false
  if (rule.op === 'lt') {
    const than = number(card[rule.than])
    return than != null && value < than
  }
  if (rule.op === 'gap') {
    return (rule.others ?? []).some((other) => {
      const base = number(card[other])
      return base != null && base > 0 && value < base * (1 - rule.pct / 100)
    })
  }
  return false
}

const toneOf = (field, value) => field?.options?.find((o) => o.id === value)?.tone

// "{key}" -> the card's value; a choice value -> its option's say (or label)
function cardLookup(block, card) {
  return (key) => {
    const raw = text(card, key)
    const field = block.fields.find((f) => f.key === key)
    if (field?.kind === 'choice' && raw) {
      const option = field.options.find((o) => o.id === raw)
      return option ? option.say || option.label : raw
    }
    return raw
  }
}

function cardLines(block, data) {
  const lines = []
  for (const card of data.cards?.[block.key] ?? []) {
    const lookup = cardLookup(block, card)
    if (block.says) {
      const sentence = fillSays(block.says.text, lookup)
      if (sentence) {
        const used = new Set([...block.says.text.matchAll(PLACEHOLDER)].map((m) => m[1]))
        const negTone = block.fields.some((f) => f.kind === 'choice' && used.has(f.key) && NEG.has(toneOf(f, text(card, f.key))))
        const neg = block.says.always === 'neg' || negTone || (block.says.negWhen ?? []).some((rule) => ruleMatches(rule, card))
        lines.push({ text: sentence, neg })
      }
    }
    for (const field of block.fields) {
      const value = text(card, field.key)
      const sentence = field.says?.[value]
      if (field.kind !== 'choice' || !sentence) continue
      const filledPrefix = block.sayPrefix ? fillSays(block.sayPrefix, lookup) : null
      const prefix = filledPrefix ? `${filledPrefix.replace(/\.$/, '')} ` : ''
      const noteField = block.fields.find((f) => f.noteFor === field.key)
      const note = noteField ? text(card, noteField.key) : ''
      const joined = [ensureStop(prefix + sentence), ensureStop(note)].filter(Boolean).join(' ')
      lines.push({ text: joined, neg: NEG.has(toneOf(field, value)) })
    }
  }
  return lines
}

function courseIdsOf(data) {
  return (data.cards?.courses ?? []).filter((c) => !isBlank(c.course)).map((c) => c._id)
}

const courseLabel = (card) => [text(card, 'course'), text(card, 'batch')].filter(Boolean).join(' ')

function itemSentence(item, answer, answers) {
  if (item.says?.[answer]) return item.says[answer]
  return ensureStop(`${item.text}: ${answers.find((a) => a.id === answer)?.label ?? answer}`)
}

function checklistLines(block, data, answers) {
  const courseCards = Object.fromEntries((data.cards?.courses ?? []).map((c) => [c._id, c]))
  const ids = courseIdsOf(data)
  const lines = []
  for (const item of block.items) {
    const check = data.checks?.[item.id] ?? {}
    const remarks = ensureStop(String(check.remarks ?? '').trim())
    const itemLines = []
    const perCourse = item.perCourse && ids.length >= 2 ? check.courses ?? {} : {}
    const answered = ids.filter((id) => !isBlank(perCourse[id])).map((id) => [id, perCourse[id]])
    if (new Set(answered.map(([, a]) => a)).size > 1) {
      // courses differ: one line per course, "Welding (SMAW) 07: <sentence>"
      for (const [id, answer] of answered) {
        if (answer === 'na') continue
        itemLines.push({ text: `${courseLabel(courseCards[id])}: ${itemSentence(item, answer, answers)}`, neg: NEG.has(answer) })
      }
    } else {
      const answer = check.answer ?? ''
      if (!isBlank(answer) && answer !== 'na') itemLines.push({ text: itemSentence(item, answer, answers), neg: NEG.has(answer) })
    }
    if (remarks) {
      if (itemLines.length === 0) itemLines.push({ text: `${item.text}: ${remarks}`, neg: false })
      else itemLines[itemLines.length - 1] = { ...itemLines[itemLines.length - 1], text: `${itemLines[itemLines.length - 1].text} ${remarks}` }
    }
    lines.push(...itemLines)
  }
  return lines
}

function fieldsLines(block, data) {
  const lines = []
  for (const field of block.fields) {
    const value = String(data.fields?.[field.key] ?? '')
    const sentence = field.kind === 'choice' ? field.says?.[value] : null
    if (sentence) lines.push({ text: sentence, neg: NEG.has(toneOf(field, value)) })
  }
  return lines
}

// the blocks a remarks block summarises: those above it back to the previous remarks block
function coveredBlocks(section, remarksBlock) {
  const before = section.blocks.slice(0, Math.max(section.blocks.indexOf(remarksBlock), 0))
  let last = -1
  before.forEach((b, i) => { if (b.type === 'remarks') last = i })
  return before.slice(last + 1)
}

export function buildRemarkLines(template, section, remarksBlock, data) {
  return coveredBlocks(section, remarksBlock).flatMap((block) => {
    if (block.type === 'checklist') return checklistLines(block, data, template.answers ?? [])
    if (block.type === 'cards') return cardLines(block, data)
    if (block.type === 'fields') return fieldsLines(block, data)
    return []
  })
}

// what prints: the officer's edit while still fresh (source == built lines), else the built
// lines. An edited line keeps neg only when it is word-for-word a built issue line.
export function printedRemarkLines(template, section, remarksBlock, data) {
  const built = buildRemarkLines(template, section, remarksBlock, data)
  const entry = data.remarks?.[remarksBlock.key] ?? {}
  const edited = String(entry.text ?? '')
  if (!edited.trim() || entry.source !== built.map((l) => l.text).join('\n')) return built
  const negTexts = new Set(built.filter((l) => l.neg).map((l) => l.text))
  return edited.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => ({ text: l, neg: negTexts.has(l) }))
}

// every remarks line of the report, issues first (template order within each group), no repeats
export function findingCandidates(template, data) {
  const seen = new Set()
  const all = []
  for (const section of template.sections) {
    for (const block of section.blocks) {
      if (block.type !== 'remarks') continue
      for (const line of printedRemarkLines(template, section, block, data)) {
        if (seen.has(line.text)) continue
        seen.add(line.text)
        all.push(line)
      }
    }
  }
  return [...all.filter((l) => l.neg), ...all.filter((l) => !l.neg)]
}

// AI pre-select fallback: every issue line
export function fallbackMajorFindings(candidates) {
  return candidates.filter((l) => l.neg).map((l) => ({ src: l.text, text: l.text }))
}

// "3, 1, 7" -> candidate indexes [2, 0, 6] (1-based, in range, first occurrence order); none, or
// an answer with words in it (an old worker rewriting the list instead of choosing) -> null
export function parseMajorAnswer(answer, count) {
  if (/[A-Za-z]{3,}/.test(String(answer ?? ''))) return null
  const picked = []
  for (const m of String(answer ?? '').matchAll(/\d+/g)) {
    const n = Number(m[0])
    if (n >= 1 && n <= count && !picked.includes(n - 1)) picked.push(n - 1)
  }
  return picked.length ? picked : null
}

// recommendations fallback: one generic line per finding
export function fallbackRecommendations(findings) {
  return findings.map((f) => `The institute should take necessary measures to address this: ${f.trim().replace(/\.$/, '')}.`)
}
