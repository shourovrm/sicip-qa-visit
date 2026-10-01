// surprise v2 N "Major findings" box (spec 2026-10-02 item 6): the officer ticks written lines of
// the report, adds them to ONE editable box (data[block.boxKey], one finding per line) and that box
// prints as the numbered Major findings list. Ticks live in data[block.tickedKey] (line texts).
// Reports from before the box kept a picked list data.findings = [{src, text}]; it stays as is.

const BOX_KEY = 'findingsText'
const TICKED_KEY = 'findingsTicked'

const boxKey = (block) => block?.boxKey ?? BOX_KEY
const tickedKey = (block) => block?.tickedKey ?? TICKED_KEY

const textLines = (value) => String(value ?? '').split('\n').map((line) => line.trim()).filter(Boolean)

// what prints: the box once it exists (even empty), else the old picked list
export function findingLines(block, data) {
  const box = data?.[boxKey(block)]
  if (typeof box === 'string') return textLines(box)
  return (data?.findings ?? []).map((finding) => String(finding?.text ?? '').trim()).filter(Boolean)
}

// on open: an old picked list becomes the box text; true when data changed
export function openFindingsBox(block, data) {
  if (typeof data[boxKey(block)] === 'string') return false
  const old = findingLines(block, data)
  if (old.length === 0) return false
  data[boxKey(block)] = old.join('\n')
  return true
}

export function setTicked(block, data, lines) {
  data[tickedKey(block)] = [...lines]
}

// ticked lines still written in the report, in report (candidate) order
export function tickedLines(block, data, candidates) {
  const ticked = new Set(data?.[tickedKey(block)] ?? [])
  return candidates.map((line) => line.text).filter((text) => ticked.has(text))
}

export function addSelectedText(block, data, candidates) {
  return tickedLines(block, data, candidates).join('\n')
}
