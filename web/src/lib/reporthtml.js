// filled visit-report printable HTML -- layout ported from
// ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-report-template.html
// (see build_surprise_visit_pdf.py there for the blank-paper version this was built from).
// unlike the blank paper form, this prints only GIVEN answers as coloured labels; blank
// checklist items print "Not answered"; empty remarks and unticked flags are left out.
// driven purely by the template's sections/blocks -- never hardcode a question here.
// caller opens the html in a new window + window.print(), same pattern as billhtml.js.
//
// CHANGE SET 2 (2026-09-25): template gained linked "attendance" cards (synced from the
// "courses" cards in section A via syncLinks), an "optional" section flag (K), and
// countsAsFlags cards (L "other_flags", free-text flags an officer can add). All of it renders
// through the same generic fields/checklist/cards/flags loop below -- no new question is
// hardcoded here. See docs/superpowers/specs/2026-09-24-reports.md "CHANGE SET 2".
import * as reportTemplateModule from './reporttemplate.js'

// answer/choice colours -- must match android ui/theme/Color.kt light values.
const TONE_COLOR = { yes: '#1c6b38', no: '#b3261e', partial: '#8a4600', na: '#4c4f66' }

// W1's reporttemplate.js is gaining a syncLinks export in parallel (see spec). Namespace
// import so this file loads fine whether or not it's landed yet; if it's missing, render the
// data exactly as given -- the editor is responsible for keeping it synced in that case.
// Clone before calling: reportHtml is documented as a pure fn and must not mutate the
// caller's data even if syncLinks mutates its argument in place.
function withSyncedLinks(template, data) {
  const syncLinks = reportTemplateModule.syncLinks
  if (typeof syncLinks !== 'function') return data
  const cloned = JSON.parse(JSON.stringify(data))
  return syncLinks(template, cloned) || cloned
}

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function blank(v) {
  return v == null || String(v).trim() === ''
}

// multi-line user text -> escaped paragraphs with <br> for line breaks.
function escMultiline(s) {
  return esc(s).replace(/\n/g, '<br>')
}

function answerMapOf(template) {
  const map = {}
  for (const a of template.answers || []) map[a.id] = a
  return map
}

function toneSpan(tone, label) {
  const color = TONE_COLOR[tone] || '#111'
  return `<span class="ans" style="color:${color}">${esc(label)}</span>`
}

const NOT_ANSWERED = '<span class="not-answered">Not answered</span>'

// text for one field's value: choice fields render as a coloured tone label (or "Not
// answered" when blank); every other kind renders as escaped plain/multiline text.
function fieldValueHtml(field, rawValue) {
  if (field.kind === 'choice') {
    if (blank(rawValue)) return NOT_ANSWERED
    const opt = (field.options || []).find((o) => o.id === rawValue)
    return opt ? toneSpan(opt.tone, opt.label) : esc(rawValue)
  }
  if (blank(rawValue)) return ''
  return escMultiline(rawValue)
}

function fieldsBlockHtml(block, data) {
  const values = data.fields || {}
  const lines = block.fields
    .map((f) => `<div class="line full"><span class="label">${esc(f.label)}</span><span class="value">${fieldValueHtml(f, values[f.key])}</span></div>`)
    .join('')
  return `<div class="details">${lines}</div>`
}

function checklistBlockHtml(block, data, answerMap) {
  const checks = data.checks || {}
  const heading = block.heading ? `<h3>${esc(block.heading)}</h3>` : ''
  const rows = block.items
    .map((item, i) => {
      const entry = checks[item.id] || {}
      const answerHtml = blank(entry.answer) ? NOT_ANSWERED : (answerMap[entry.answer] ? toneSpan(answerMap[entry.answer].tone, answerMap[entry.answer].label) : esc(entry.answer))
      const remarksHtml = blank(entry.remarks) ? '' : escMultiline(entry.remarks)
      return `<tr><td class="num">${i + 1}</td><td class="question">${esc(item.text)}</td><td class="ans-cell">${answerHtml}</td><td class="remarks">${remarksHtml}</td></tr>`
    })
    .join('')
  return `${heading}<table class="checklist"><colgroup><col class="c-num"><col class="c-question"><col class="c-ans"><col class="c-remarks"></colgroup>` +
    `<thead><tr><th>#</th><th>Item</th><th>Answer</th><th>Remarks</th></tr></thead><tbody>${rows}</tbody></table>`
}

// true when >=2 of the compare fields are present and parse to unequal ints -- same rule
// as the progress spec (shared/report-templates surprise-v1.json contract, section C).
function cardMismatch(compare, entry) {
  if (!compare) return false
  const values = compare.fields.map((k) => entry[k]).filter((v) => !blank(v)).map(Number).filter((n) => !Number.isNaN(n))
  return values.length >= 2 && !values.every((v) => v === values[0])
}

function cardsBlockHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  if (entries.length === 0) return '<p class="empty">No entries.</p>'
  return entries
    .map((entry, i) => {
      const titleValue = entry[block.titleField]
      const caption = blank(titleValue) ? `${esc(block.itemLabel)} ${i + 1}` : `${esc(block.itemLabel)} ${i + 1}: ${esc(titleValue)}`
      const mismatch = cardMismatch(block.compare, entry)
      const warning = mismatch ? `<div class="mismatch-msg">${esc(block.compare.message)}</div>` : ''
      const rows = block.fields
        .map((f) => `<tr><th>${esc(f.label)}</th><td>${fieldValueHtml(f, entry[f.key])}</td></tr>`)
        .join('')
      return `<table class="card${mismatch ? ' mismatch' : ''}"><caption>${caption}</caption><tbody>${rows}</tbody></table>${warning}`
    })
    .join('')
}

// the fixed flags list AND any countsAsFlags cards blocks (free-text flags, e.g. L
// "other_flags") in the same section render together as one list -- a countsAsFlags card
// with non-blank titleField is inherently "ticked" (its presence is the flag), so it's listed
// alongside the ticked fixed items rather than as its own cards table.
function combinedFlagsHtml(section, data) {
  const flagsBlock = section.blocks.find((b) => b.type === 'flags')
  const ticked = new Set(data.flags || [])
  const items = []
  if (flagsBlock) for (const item of flagsBlock.items) if (ticked.has(item.id)) items.push(esc(item.text))
  for (const block of section.blocks) {
    if (block.type !== 'cards' || !block.countsAsFlags) continue
    const entries = (data.cards && data.cards[block.key]) || []
    for (const entry of entries) {
      const text = entry[block.titleField]
      if (!blank(text)) items.push(esc(String(text).trim()))
    }
  }
  if (items.length === 0) return '<p class="none-ticked">None ticked.</p>'
  return `<ul class="flags-list">${items.map((t) => `<li>${t}</li>`).join('')}</ul>`
}

function blockHtml(block, data, answerMap) {
  if (block.type === 'fields') return fieldsBlockHtml(block, data)
  if (block.type === 'checklist') return checklistBlockHtml(block, data, answerMap)
  if (block.type === 'cards') return cardsBlockHtml(block, data)
  return '' // unknown block type (flags/countsAsFlags cards handled in sectionHtml) -- ignore
}

function sectionHtml(section, data, answerMap) {
  const note = section.note ? `<span class="note">${esc(section.note)}</span>` : ''
  const optionalTag = section.optional ? ' <span class="optional-tag">Optional</span>' : ''
  const isFlagsRelevant = (b) => b.type === 'flags' || (b.type === 'cards' && b.countsAsFlags)
  let flagsRendered = false
  const blocks = (section.blocks || [])
    .map((b) => {
      if (isFlagsRelevant(b)) {
        if (flagsRendered) return ''
        flagsRendered = true
        return combinedFlagsHtml(section, data)
      }
      return blockHtml(b, data, answerMap)
    })
    .join('')
  return `<section class="keep"><h2><span class="letter">${esc(section.letter)}</span>${esc(section.title)}${optionalTag}${note}</h2>${blocks}</section>`
}

function statusLabel(status) {
  return status === 'submitted' ? 'Submitted' : 'Draft'
}

function metaHtml(meta) {
  const status = statusLabel(meta.status)
  const submitted = meta.status === 'submitted' && !blank(meta.submittedAt) ? ` &middot; submitted ${esc(meta.submittedAt)}` : ''
  return `<div class="meta">Officer: ${esc(meta.officerName)} &middot; ${status}${submitted}</div>`
}

function headerHtml(template, meta) {
  return `<header>
    <div>
      <div class="program">${esc(template.program)}</div>
      <h1>${esc(template.title)}</h1>
      ${metaHtml(meta)}
    </div>
    <div class="form-code">${esc(template.subtitle)}</div>
  </header>`
}

// print CSS -- same page geometry/typography as the blank paper template, but the tick-box
// columns are replaced by a single coloured "Answer" column since only given answers print.
const CSS = `
  @page {
    size: A4 portrait;
    margin: 10mm 11mm 12mm;
    @bottom-left { content: "SICIP Surprise Visit Report"; font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }
    @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 7pt "Noto Sans", Arial, sans-serif; color: #555; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "Noto Sans", "Segoe UI", Arial, sans-serif; font-size: 8.4pt; line-height: 1.25; color: #111; }

  header { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 1.6pt solid #111; padding-bottom: 4pt; margin-bottom: 6pt; }
  header .program { font-size: 8pt; }
  header h1 { margin: 1pt 0 0; font-size: 13pt; font-weight: 700; letter-spacing: 0.01em; }
  header .form-code { font-size: 7.5pt; text-align: right; color: #333; }
  .meta { margin-top: 2pt; font-size: 7.5pt; color: #333; }

  h2 { display: flex; align-items: baseline; gap: 5pt; margin: 8pt 0 3pt; font-size: 9.2pt; font-weight: 700; break-after: avoid; }
  h2 .letter { display: inline-block; min-width: 13pt; padding: 0.5pt 0; text-align: center; background: #111; color: #fff; font-size: 8pt; }
  h2 .note { margin-left: auto; font-weight: 400; font-size: 7.4pt; font-style: italic; color: #333; }
  h2 .optional-tag { font-weight: 700; font-size: 6.6pt; text-transform: uppercase; letter-spacing: 0.03em; color: #8a4600; border: 0.6pt solid #8a4600; border-radius: 3pt; padding: 0.5pt 3pt; }
  h3 { margin: 4pt 0 2pt; font-size: 8.2pt; font-weight: 700; }

  .details { display: grid; grid-template-columns: 1fr 1fr; column-gap: 12pt; row-gap: 3pt; }
  .details .line { display: flex; align-items: baseline; gap: 4pt; min-height: 12pt; }
  .details .line.full { grid-column: 1 / -1; }
  .details .label { white-space: nowrap; font-weight: 700; }
  .details .label::after { content: ':'; }
  .details .value { flex: 1; border-bottom: 0.6pt solid #ccc; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  th, td { border: 0.6pt solid #666; padding: 2pt 3pt; vertical-align: middle; }
  th { background: #e6e6e6; font-weight: 700; font-size: 7.4pt; text-align: center; line-height: 1.15; }
  tr { break-inside: avoid; }
  td.num, th.num { width: 16pt; text-align: center; }

  .checklist col.c-num { width: 16pt; }
  .checklist col.c-question { width: 46%; }
  .checklist col.c-ans { width: 16%; }
  .checklist td { height: 14pt; }
  .checklist td.ans-cell { text-align: center; font-weight: 700; }
  .checklist td.num { color: #333; }
  .not-answered { color: #888; font-style: italic; font-weight: 400; }

  .card { margin: 2pt 0 4pt; }
  .card caption { text-align: left; font-weight: 700; font-size: 8pt; padding: 2pt 0; caption-side: top; }
  .card th { width: 32%; text-align: left; background: #f2f2f2; font-size: 7.6pt; }
  .card.mismatch { outline: 1pt solid #b3261e; }
  .mismatch-msg { color: #b3261e; font-weight: 700; font-size: 7.6pt; margin: -2pt 0 5pt; }
  p.empty, p.none-ticked { color: #666; font-style: italic; margin: 2pt 0 6pt; }

  .flags-list { columns: 2; column-gap: 14pt; margin: 0; padding: 0 0 4pt; list-style: none; }
  .flags-list li { padding: 1.8pt 0; break-inside: avoid; }
  .flags-list li::before { content: '\\2713  '; font-weight: 700; color: #b3261e; }

  .ans { font-weight: 700; }

  .keep { break-inside: avoid; }
  footer.legend { margin-top: 6pt; font-size: 7pt; color: #333; }
`

// pure fn: template + report data + {officerName, submittedAt?, status} -> full print HTML.
export function reportHtml(template, data, meta) {
  data = withSyncedLinks(template, data)
  const answerMap = answerMapOf(template)
  const sections = (template.sections || []).map((s) => sectionHtml(s, data, answerMap)).join('')
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(template.title)}</title><style>${CSS}</style></head><body>` +
    headerHtml(template, meta) + sections +
    '<footer class="legend">T = total, F = female, TMS = Training Management System, TDP = training delivery plan, CS = competency standard, CBLM = competency-based learning material, PPE = personal protective equipment, OHS = occupational health and safety.</footer>' +
    '</body></html>'
}

// open the built HTML in a new tab and trigger the browser's print (save-as-PDF) dialog --
// mirrors printBillHtml in billhtml.js.
export function openReportPrint(template, data, meta) {
  const html = reportHtml(template, data, meta)
  const w = window.open('', '_blank')
  if (!w) return false
  w.document.write(html)
  w.document.close()
  w.onload = () => w.print()
  // document.write doesn't always fire load reliably across browsers -- belt-and-braces kick
  setTimeout(() => w.print(), 300)
  return true
}
