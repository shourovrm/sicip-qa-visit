// "Submitted by-" block closing every surprise and QA report: signature space, then each
// visiting officer's name, designation and organisation, side by side
import { esc } from './reporthtml.js'

export const SIGNOFF_ORGANISATION = 'SICIP'

const text = (value) => String(value ?? '').trim()

// "Name, Designation" or "Name (Designation)" -- the comma wins because designations like
// "Program Officer (QA)" carry their own brackets
export function splitOfficerLine(line) {
  const comma = line.indexOf(',')
  if (comma > 0) return { name: line.slice(0, comma).trim(), designation: line.slice(comma + 1).trim() }
  const bracketed = /^(.*?)\s*\((.+)\)\s*$/.exec(line)
  if (bracketed) return { name: bracketed[1], designation: bracketed[2] }
  return { name: line, designation: '' }
}

// surprise v2 keeps officers as cards; surprise v1 and QA as free text, one per line.
// Nobody named yet -> one blank slot so the page still has a place to sign.
export function visitingOfficers(data) {
  const cards = (data.cards?.officers ?? [])
    .map((card) => ({ name: text(card.name), designation: text(card.designation) }))
    .filter((officer) => officer.name)
  if (cards.length) return cards
  const lines = String(data.fields?.officers ?? '').split(/[\n;]/).map((line) => line.trim()).filter(Boolean)
  if (lines.length) return lines.map(splitOfficerLine)
  return [{ name: '', designation: '' }]
}

export const SIGNOFF_CSS = `
  .signoff { margin-top: 18pt; break-inside: avoid; }
  .signoff-people { display: flex; flex-wrap: wrap; gap: 6pt 28pt; }
  .signoff-person { min-width: 48mm; }
  .signoff-space { height: 34pt; }
  .signoff-name { font-weight: 700; }
`

export function signoffHtml(data) {
  const people = visitingOfficers(data).map((officer) => `<div class="signoff-person">
    <div class="signoff-space"></div>
    <div class="signoff-name">${esc(officer.name)}</div>
    <div>${esc(officer.designation)}</div>
    <div>${esc(SIGNOFF_ORGANISATION)}</div>
  </div>`).join('')
  return `<div class="signoff"><div>Submitted by-</div><div class="signoff-people">${people}</div></div>`
}
