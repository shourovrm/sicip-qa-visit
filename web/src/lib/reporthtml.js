// filled visit-report printable HTML -- layout ported from
// ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-report-template.html
// (see build_surprise_visit_pdf.py there for the blank-paper version this was built from).
// caller opens the html in a new window + window.print(), same pattern as billhtml.js.
//
// CHANGE SET 3 (2026-09-25) "ONE REPORT LAYOUT FOR ALL OUTPUTS": this file is now the reference
// layout -- reportdocx.js (Word) copies it table-for-table, android's pdf/ReportHtml.kt is a
// port of this file. Column widths live in ./reportlayout.js so the two web renderers can't
// drift apart. Rules baked in throughout: NEVER print "Not answered" (blank = empty box/line);
// an unanswered checklist item is simply four empty ticks; cards render as one table per block
// with one row per card; flags always list every fixed item (ticked or not) plus any custom
// (countsAsFlags) flags as extra ticked rows.
import {
  CHECKLIST_COLUMNS, FLAGS_COLUMNS, INTERVIEW_TICK_COLUMNS, TICK_CHECKED, TICK_UNCHECKED,
  TONE_COLOR, isStandardAnswerChoice,
  displayTime,
} from './reportlayout.js'
import * as reportTemplateModule from './reporttemplate.js'

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

// CHANGE SET 3 replaces syncLinks with normalize (syncLinks + per-course splitting) as the one
// step to run before rendering. W1's reporttemplate.js is gaining that export in parallel;
// namespace import so this file loads fine either way, falling back to syncLinks (CHANGE SET 2)
// and finally to the data as given if neither has landed yet. Clone first: reportHtml is
// documented as a pure fn and must not mutate the caller's data even if these mutate in place.
function withNormalizedData(template, data) {
  const normalize = reportTemplateModule.normalize
  const syncLinks = reportTemplateModule.syncLinks
  const step = typeof normalize === 'function' ? normalize : (typeof syncLinks === 'function' ? syncLinks : null)
  if (!step) return data
  const cloned = JSON.parse(JSON.stringify(data))
  return step(template, cloned) || cloned
}

function toneSpan(tone, label) {
  const color = TONE_COLOR[tone]
  const style = color ? ` style="color:#${color}"` : ''
  return `<span class="ans"${style}>${esc(label)}</span>`
}

// text for one field's value: choice fields render as a coloured tone label (blank = nothing,
// never "Not answered" -- CHANGE SET 3); every other kind renders as escaped plain/multiline text.
function fieldValueHtml(field, rawValue) {
  if (field.kind === 'choice') {
    if (blank(rawValue)) return ''
    const opt = (field.options || []).find((o) => o.id === rawValue)
    return opt ? toneSpan(opt.tone, opt.label) : esc(rawValue)
  }
  if (blank(rawValue)) return ''
  if (field.kind === 'time') return esc(displayTime(rawValue))
  return escMultiline(rawValue)
}

// choice/select fields print every option inline with a tick box, the chosen one filled and
// bold (in its tone colour for a choice field) -- matches the paper form's own
// "Type:  ☐ Full QA  ☐ Monitoring  ☐ Surprise" pattern (K's last_visit_type). This is the
// top-level "fields" block rendering; a card table cell uses fieldValueHtml's plain coloured
// label instead, since a full option row wouldn't fit a narrow column.
function inlineChoiceHtml(field, rawValue) {
  const options = field.kind === 'select' ? field.options.map((o) => ({ id: o, label: o })) : field.options
  const parts = options.map((opt) => {
    const checked = rawValue === opt.id
    const color = checked && opt.tone ? TONE_COLOR[opt.tone] : null
    const style = color ? ` style="color:#${color}"` : ''
    return `<span class="choice-opt${checked ? ' checked' : ''}"${style}>${checked ? TICK_CHECKED : TICK_UNCHECKED} ${esc(opt.label)}</span>`
  })
  return `<div class="line choice-line"><span class="label">${esc(field.label)}</span><span class="choices">${parts.join(' ')}</span></div>`
}

// short-kind fields print "label: value" on one line; choice/select fields print an inline
// tick-box option row (see inlineChoiceHtml); longtext fields print the label above a bordered
// box (matches the paper form's ruled boxes for findings/instructions/address/...). blank value
// = an empty line/box/tick, never placeholder text.
function fieldsListHtml(fields, values) {
  return fields
    .map((f) => {
      if (f.kind === 'longtext') {
        const body = blank(values[f.key]) ? '' : escMultiline(values[f.key])
        return `<div class="field-box"><div class="label">${esc(f.label)}</div><div class="box">${body}</div></div>`
      }
      if (f.kind === 'choice' || f.kind === 'select') return inlineChoiceHtml(f, values[f.key])
      return `<div class="line"><span class="label">${esc(f.label)}</span><span class="value">${fieldValueHtml(f, values[f.key])}</span></div>`
    })
    .join('')
}

function fieldsBlockHtml(block, data) {
  return `<div class="details">${fieldsListHtml(block.fields, data.fields || {})}</div>`
}

// courses with a non-blank name, in section-A order -- same list normalize()/reference.py's
// course_ids() splits per-course items by, and the source for the linked interview/attendance
// cards and the per-course summary line.
function courseList(data) {
  return ((data.cards && data.cards.courses) || []).filter((c) => !blank(c.course))
}

function tickCellHtml(checked, tone) {
  const color = checked ? TONE_COLOR[tone] : null
  const style = color ? ` style="color:#${color}"` : ''
  return `<td class="tick-cell"><span class="tick"${style}>${checked ? TICK_CHECKED : TICK_UNCHECKED}</span></td>`
}

function colgroupHtml(columns) {
  return `<colgroup>${columns.map((c) => `<col style="width:${c.weight}%">`).join('')}</colgroup>`
}

function tableHeadHtml(columns) {
  return `<thead><tr>${columns.map((c) => `<th>${esc(c.label)}</th>`).join('')}</tr></thead>`
}

// small line under a per-course item's text, e.g. "Welding (SMAW) 07: Yes · Electrical
// Installation 03: No" -- only courses with a non-blank per-course answer are listed; omitted
// entirely (not shown blank) when nothing has been answered yet for any course.
function perCourseLineHtml(item, entry, courses, answerMap) {
  if (!item.perCourse || courses.length < 2) return ''
  const per = entry.courses || {}
  const parts = courses
    .map((c) => {
      const answer = per[c._id]
      if (blank(answer)) return null
      const label = answerMap[answer] ? answerMap[answer].label : answer
      const name = [c.course, c.batch].filter((v) => !blank(v)).join(' ')
      return `${esc(name)}: ${esc(label)}`
    })
    .filter(Boolean)
  return parts.length ? `<div class="per-course-line">${parts.join(' &middot; ')}</div>` : ''
}

function checklistBlockHtml(block, data, template, answerMap) {
  const checks = data.checks || {}
  const courses = courseList(data)
  const answerIds = template.answers.map((a) => a.id)
  const heading = block.heading ? `<h3>${esc(block.heading)}</h3>` : ''
  const rows = block.items
    .map((item, i) => {
      const entry = checks[item.id] || {}
      const tickCells = answerIds.map((id) => tickCellHtml(entry.answer === id, answerMap[id]?.tone)).join('')
      // no "Per course" badge on paper: the course line under the item already says it
      const itemHtml = `${esc(item.text)}${perCourseLineHtml(item, entry, courses, answerMap)}`
      const remarksHtml = blank(entry.remarks) ? '' : escMultiline(entry.remarks)
      return `<tr><td class="num">${i + 1}</td><td class="question">${itemHtml}</td>${tickCells}<td class="remarks">${remarksHtml}</td></tr>`
    })
    .join('')
  return `${heading}<table class="checklist">${colgroupHtml(CHECKLIST_COLUMNS)}${tableHeadHtml(CHECKLIST_COLUMNS)}<tbody>${rows}</tbody></table>`
}

// true when >=2 of the compare fields are present and parse to unequal ints -- same rule
// as the progress spec (shared/report-templates surprise-v1.json contract, section C).
function cardMismatch(compare, entry) {
  if (!compare) return false
  const values = compare.fields.map((k) => entry[k]).filter((v) => !blank(v)).map(Number).filter((n) => !Number.isNaN(n))
  return values.length >= 2 && !values.every((v) => v === values[0])
}

// one table per cards block, one row per card, columns = the block's fields in template order.
function cardsBlockHtml(block, data) {
  const entries = (data.cards && data.cards[block.key]) || []
  const heading = block.heading ? `<h3>${esc(block.heading)}</h3>` : ''
  const note = block.note ? `<p class="block-note">${esc(block.note)}</p>` : ''
  if (entries.length === 0) return `${heading}${note}<p class="empty">No entries.</p>`
  const headerRow = block.fields.map((f) => `<th>${esc(f.label)}</th>`).join('')
  const mismatchedRows = []
  const rows = entries
    .map((entry, i) => {
      if (cardMismatch(block.compare, entry)) mismatchedRows.push(i + 1)
      return `<tr>${block.fields.map((f) => `<td>${fieldValueHtml(f, entry[f.key])}</td>`).join('')}</tr>`
    })
    .join('')
  let warning = ''
  if (mismatchedRows.length > 0 && block.compare) {
    const which = mismatchedRows.length === entries.length ? '' : ` (row${mismatchedRows.length > 1 ? 's' : ''} ${mismatchedRows.join(', ')})`
    warning = `<p class="mismatch-msg">${esc(block.compare.message)}${which}</p>`
  }
  return `${heading}${note}<table class="cards-table"><thead><tr>${headerRow}</tr></thead><tbody>${rows}</tbody></table>${warning}`
}

// linked cards block with display:"tabs" (section I "interviews"): tabs are an editor-only
// concept -- exports print one small table per course instead. Fields whose choice options are
// exactly the template's answer ids (e.g. q1-q7) render as a checklist-style tick sub-table;
// every other field (trainees_interviewed, tech_topic, tech_result, feedback) renders through
// the normal fields renderer. Linked fields (course/batch) are shown in the caption, not twice.
function tabsCardsBlockHtml(block, data, template, answerMap) {
  const entries = (data.cards && data.cards[block.key]) || []
  if (entries.length === 0) return '<p class="empty">Add courses in section A.</p>'
  const linkedKeys = new Set((block.linkFrom && block.linkFrom.fields) || [])
  const tickFields = block.fields.filter((f) => !linkedKeys.has(f.key) && isStandardAnswerChoice(f, template))
  const tickFieldKeys = new Set(tickFields.map((f) => f.key))
  const otherFields = block.fields.filter((f) => !linkedKeys.has(f.key) && !tickFieldKeys.has(f.key))
  const answerIds = template.answers.map((a) => a.id)
  return entries
    .map((entry) => {
      const linkedFields = (block.linkFrom && block.linkFrom.fields) || []
      const extra = linkedFields.filter((k) => k !== block.titleField).map((k) => entry[k]).filter((v) => !blank(v)).join(' ')
      const caption = `${esc(entry[block.titleField])}${extra ? ` &middot; Batch ${esc(extra)}` : ''}`
      const tickRows = tickFields
        .map((f) => `<tr><td class="question">${esc(f.label)}</td>${answerIds.map((id) => tickCellHtml(entry[f.key] === id, answerMap[id]?.tone)).join('')}</tr>`)
        .join('')
      const tickTable = tickFields.length
        ? `<table class="checklist interview-ticks">${colgroupHtml(INTERVIEW_TICK_COLUMNS)}${tableHeadHtml(INTERVIEW_TICK_COLUMNS)}<tbody>${tickRows}</tbody></table>`
        : ''
      const otherHtml = `<div class="details">${fieldsListHtml(otherFields, entry)}</div>`
      return `<div class="interview-card"><h3>${caption}</h3>${tickTable}${otherHtml}</div>`
    })
    .join('')
}

// the fixed flags list AND any countsAsFlags cards blocks (free-text flags, e.g. L
// "other_flags") in the same section render together as one 2-column tick/text table: every
// fixed item always prints (ticked or not), a countsAsFlags card with non-blank titleField is
// inherently "ticked" (its presence is the flag) and appears as an extra checked row.
function combinedFlagsHtml(section, data) {
  const flagsBlock = section.blocks.find((b) => b.type === 'flags')
  const ticked = new Set(data.flags || [])
  const rows = []
  if (flagsBlock) {
    for (const item of flagsBlock.items) {
      const checked = ticked.has(item.id)
      rows.push(`<tr>${tickCellHtml(checked, 'no')}<td class="flag-text${checked ? ' checked' : ''}">${esc(item.text)}</td></tr>`)
    }
  }
  for (const block of section.blocks) {
    if (block.type !== 'cards' || !block.countsAsFlags) continue
    const entries = (data.cards && data.cards[block.key]) || []
    for (const entry of entries) {
      const text = entry[block.titleField]
      if (blank(text)) continue
      rows.push(`<tr>${tickCellHtml(true, 'no')}<td class="flag-text checked">${esc(String(text).trim())}</td></tr>`)
    }
  }
  if (rows.length === 0) return '<p class="empty">None ticked.</p>'
  return `<table class="flags-table">${colgroupHtml(FLAGS_COLUMNS)}<tbody>${rows.join('')}</tbody></table>`
}

function blockHtml(block, data, template, answerMap) {
  if (block.type === 'fields') return fieldsBlockHtml(block, data)
  if (block.type === 'checklist') return checklistBlockHtml(block, data, template, answerMap)
  if (block.type === 'cards') return block.display === 'tabs' ? tabsCardsBlockHtml(block, data, template, answerMap) : cardsBlockHtml(block, data)
  return '' // unknown block type (flags/countsAsFlags cards handled in sectionHtml) -- ignore
}

function sectionHtml(section, data, template, answerMap) {
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
      return blockHtml(b, data, template, answerMap)
    })
    .join('')
  return `<section><h2><span class="letter">${esc(section.letter)}</span>${esc(section.title)}${optionalTag}${note}</h2>${blocks}</section>`
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

// print CSS -- A4 portrait, margins top 10mm/sides 11mm/bottom 12mm (paper form geometry),
// page numbers via @page counters. Column widths for checklist/interview-tick/flags tables come
// from ./reportlayout.js (colgroupHtml), not hardcoded here.
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

  .details { margin: 0 0 2pt; }
  .details .line { display: flex; align-items: baseline; gap: 4pt; min-height: 12pt; padding: 0.5pt 0; }
  .details .label { white-space: nowrap; font-weight: 700; }
  .details .label::after { content: ':'; }
  .details .value { flex: 1; border-bottom: 0.6pt solid #ccc; }
  .field-box { margin: 2pt 0 5pt; }
  .field-box .label { font-weight: 700; display: block; margin-bottom: 1pt; }
  .field-box .box { border: 0.6pt solid #666; min-height: 20pt; padding: 2pt 3pt; }
  .choice-line .choices { display: flex; flex-wrap: wrap; gap: 2pt 8pt; }
  .choice-opt { white-space: nowrap; }
  .choice-opt.checked { font-weight: 700; }

  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  th, td { border: 0.6pt solid #666; padding: 2pt 3pt; vertical-align: middle; }
  th { background: #e6e6e6; font-weight: 700; font-size: 7.4pt; text-align: center; line-height: 1.15; }
  tr { break-inside: avoid; }
  td.num, th.num { text-align: center; }

  .checklist td { height: 14pt; }
  .checklist td.num { color: #333; text-align: center; }
  .checklist td.question { text-align: left; }
  .per-course-line { font-size: 6.8pt; font-weight: 400; color: #444; margin-top: 1pt; }
  .per-course-tag { font-weight: 700; font-size: 6.2pt; text-transform: uppercase; letter-spacing: 0.02em; color: #4c4f66; border: 0.6pt solid #4c4f66; border-radius: 3pt; padding: 0 2pt; margin-left: 3pt; }

  .tick-cell { text-align: center; }
  .tick { font-size: 9pt; font-weight: 700; }

  .cards-table th { font-size: 7pt; }
  .cards-table td { font-size: 7.6pt; }
  .mismatch-msg { color: #b3261e; font-weight: 700; font-size: 7.6pt; margin: -2pt 0 5pt; }
  p.empty, .block-note { color: #666; font-style: italic; margin: 2pt 0 6pt; font-size: 7.4pt; }

  .flags-table td.flag-text { text-align: left; }
  .flags-table td.flag-text.checked { font-weight: 700; color: #b3261e; }

  .interview-card { margin-bottom: 6pt; break-inside: avoid; }
  .interview-ticks td.question { text-align: left; }

  .ans { font-weight: 700; }

  footer.legend { margin-top: 6pt; font-size: 7pt; color: #333; }
`

// pure fn: template + report data + {officerName, submittedAt?, status} -> full print HTML.
export function reportHtml(template, data, meta) {
  const normalizedData = withNormalizedData(template, data)
  const answerMap = answerMapOf(template)
  const sections = (template.sections || []).map((s) => sectionHtml(s, normalizedData, template, answerMap)).join('')
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
