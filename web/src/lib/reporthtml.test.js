// reportHtml -- structural checks only (escaping, colours, widths, omissions). the visual
// layout/print path is browser window.print(), not JVM/node-testable here (see
// billhtml.test.js for the same convention). Visual verification (page count, look) was done
// separately by rendering the fixture through soffice --headless --convert-to pdf + magick, not
// as part of this automated suite.
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { reportHtml } from './reporthtml.js'
import { CHECKLIST_COLUMNS, FLAGS_COLUMNS } from './reportlayout.js'

// loaded via node fs (not JSON import) -- vite's server.fs.allow for ../shared is W1's
// vite.config change; this avoids depending on it having landed.
function loadJson(relPath) {
  const path = fileURLToPath(new URL(relPath, import.meta.url))
  return JSON.parse(readFileSync(path, 'utf-8'))
}

const template = loadJson('../../../shared/report-templates/surprise-v1.json')
const fixture = loadJson('../../../shared/report-templates/fixtures/progress-1.json')

const meta = { officerName: 'Mahfuzul Islam', status: 'draft' }
const EMPTY_DATA = { fields: {}, checks: {}, cards: {}, flags: [] }

it('escapes user text in fields, checklist remarks, and cards', () => {
  // attendance is a linked cards block (normalize/syncLinks copies course/batch in from the
  // "courses" cards in section A) -- give it a source card so the synced attendance entry survives.
  const data = {
    fields: { ti_name: 'A & B <script>alert(1)</script>' },
    checks: { arrival_1: { answer: 'yes', remarks: 'Fine & good <b>bold</b>' } },
    cards: { courses: [{ _id: 'c1', course: 'Weld<ing> & Fab', batch: '1' }] },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  expect(html).toContain('A &amp; B &lt;script&gt;')
  expect(html).not.toContain('<script>alert(1)</script>')
  expect(html).toContain('Fine &amp; good &lt;b&gt;bold&lt;/b&gt;')
  expect(html).toContain('Weld&lt;ing&gt; &amp; Fab')
})

// CHANGE SET 3: "Not answered" is gone everywhere -- blank fields/cards/checklist answers are
// simply empty (a line, a box, or four unticked boxes), never a placeholder string.
it('never prints "Not answered", even for a maximally blank report', () => {
  const html = reportHtml(template, EMPTY_DATA, meta)
  expect(html).not.toContain('Not answered')
})

it('checklist table uses the shared column widths (Item wide, ticks narrow)', () => {
  const html = reportHtml(template, EMPTY_DATA, meta)
  for (const col of CHECKLIST_COLUMNS) expect(html).toContain(`width:${col.weight}%`)
  // Item (44%) must be much wider than any single tick column (6%) -- the reported bug.
  const itemCol = CHECKLIST_COLUMNS.find((c) => c.key === 'item')
  const tickCol = CHECKLIST_COLUMNS.find((c) => c.key === 'yes')
  expect(itemCol.weight).toBeGreaterThan(tickCol.weight * 5)
})

it('an unanswered checklist item has four unticked boxes and an empty remarks cell', () => {
  const html = reportHtml(template, EMPTY_DATA, meta)
  const row = html.slice(html.indexOf('Centre open and training'), html.indexOf('</tr>', html.indexOf('Centre open and training')))
  expect((row.match(/☐/g) || []).length).toBe(4) // four empty ticks
  expect(row).not.toContain('☒') // none checked
  expect(row).toContain('<td class="remarks"></td>') // truly empty, not "Not answered"
})

it('a "yes" answer ticks the Yes column in its tone colour and leaves the rest unticked', () => {
  const data = { ...EMPTY_DATA, checks: { arrival_1: { answer: 'yes', remarks: '' } } }
  const html = reportHtml(template, data, meta)
  const row = html.slice(html.indexOf('Centre open and training'), html.indexOf('</tr>', html.indexOf('Centre open and training')))
  expect((row.match(/☒/g) || []).length).toBe(1)
  expect((row.match(/☐/g) || []).length).toBe(3)
  expect(row).toContain('style="color:#1c6b38"')
})

it('per-course items show a summary line and no "Per course" badge once 2+ courses exist', () => {
  const data = {
    fields: {},
    checks: { arrival_1: { answer: 'partial', remarks: '', courses: { c1: 'yes', c2: 'no' } } },
    cards: { courses: [{ _id: 'c1', course: 'Welding (SMAW)', batch: '07' }, { _id: 'c2', course: 'Electrical Installation', batch: '03' }] },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  const row = html.slice(html.indexOf('Centre open and training'), html.indexOf('</tr>', html.indexOf('Centre open and training')))
  expect(row).not.toContain('per-course-tag')
  expect(row).toContain('Welding (SMAW) 07: Yes')
  expect(row).toContain('Electrical Installation 03: No')
})

it('with fewer than 2 courses, a perCourse item behaves as a normal single-answer row (no tag)', () => {
  const data = {
    fields: {},
    checks: { arrival_1: { answer: 'yes', remarks: '', courses: { c1: 'yes' } } },
    cards: { courses: [{ _id: 'c1', course: 'Welding (SMAW)', batch: '07' }] },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  const row = html.slice(html.indexOf('Centre open and training'), html.indexOf('</tr>', html.indexOf('Centre open and training')))
  expect(row).not.toContain('per-course-tag')
  expect(row).not.toContain('per-course-line')
})

it('flags table always lists every fixed item (ticked or not), plus custom flags as ticked rows', () => {
  const data = {
    fields: {},
    checks: {},
    cards: { other_flags: [{ _id: 'f1', flag: 'Trainees sharing one ID card' }, { _id: 'f2', flag: ' ' }] },
    flags: ['flag_3'],
  }
  const html = reportHtml(template, data, meta)
  const flagsSection = html.slice(html.indexOf('>L<'), html.indexOf('>M<'))
  const flagsBlock = template.sections.find((s) => s.key === 'flags').blocks.find((b) => b.type === 'flags')
  expect((flagsSection.match(/<tr>/g) || []).length).toBe(flagsBlock.items.length + 1) // 9 fixed + 1 custom
  expect(flagsSection).toContain('possible ghost trainees') // ticked fixed flag
  expect(flagsSection).toContain('Trainer absent or replaced by an unapproved person') // unticked fixed flag, still listed
  expect(flagsSection).toContain('Trainees sharing one ID card') // custom flag, non-blank -> merged in as ticked
  for (const col of FLAGS_COLUMNS) if (col.weight) expect(flagsSection).toContain(`width:${col.weight}%`)
})

it('cards block renders one table per block, one row per card, and flags compare mismatches', () => {
  // attendance is linked to "courses" (section A) via normalize: course/batch come from the
  // source card in source order, other answers (present_total/register/tms) are the officer's
  // own and survive the sync because the attendance entry's _link matches the source _id.
  const data = {
    fields: {},
    checks: {},
    cards: {
      courses: [
        { _id: 'c1', course: 'Welding (SMAW)', batch: '07' },
        { _id: 'c2', course: 'Electrical Installation', batch: '03' },
      ],
      attendance: [
        { _id: 'attendance:c1', _link: 'c1', present_total: '17', register: '23', tms: '23' }, // mismatch: 17 vs 23
        { _id: 'attendance:c2', _link: 'c2', present_total: '22', register: '22', tms: '22' }, // matches
      ],
    },
    flags: [],
  }
  const html = reportHtml(template, data, meta)
  const cSection = html.slice(html.indexOf('>C<'), html.indexOf('>D<'))
  expect((cSection.match(/<table class="cards-table">/g) || []).length).toBe(1) // one table for the whole block
  const cardsTable = cSection.slice(cSection.indexOf('<table class="cards-table">'), cSection.indexOf('</table>') + '</table>'.length)
  const tbody = cardsTable.slice(cardsTable.indexOf('<tbody>'))
  expect((tbody.match(/<tr>/g) || []).length).toBe(2) // one row per card (2 cards), not one table per card
  expect(cardsTable).toContain('Welding (SMAW)')
  expect(cardsTable).toContain('Electrical Installation')
  expect(cSection).toContain('Headcount, register and TMS do not match')
})

describe('section I: linked interview cards (display: tabs)', () => {
  it('empty state links back to section A', () => {
    const html = reportHtml(template, EMPTY_DATA, meta)
    const iSection = html.slice(html.indexOf('>I<'), html.indexOf('>J<'))
    expect(iSection).toContain('Add courses in section A.')
  })

  it('renders one table per course with q1-q7 as tick columns, plus topic/result/feedback', () => {
    const data = {
      fields: {},
      checks: {},
      cards: {
        courses: [{ _id: 'c1', course: 'Welding (SMAW)', batch: '07' }],
        interviews: [{ _id: 'interviews:c1', _link: 'c1', course: 'Welding (SMAW)', batch: '07', trainees_interviewed: '6', q1: 'yes', q2: 'no', tech_topic: 'Electrode angle', tech_result: 'most' }],
      },
      flags: [],
    }
    const html = reportHtml(template, data, meta)
    const iSection = html.slice(html.indexOf('>I<'), html.indexOf('>J<'))
    expect(iSection).toContain('Welding (SMAW) &middot; Batch 07')
    expect(iSection).toContain('Classes run on all scheduled days and hours') // q1 label
    expect(iSection).toContain('style="color:#1c6b38"') // q1 = yes, ticked in tone colour
    expect(iSection).toContain('Electrode angle') // tech_topic
    expect(iSection).toContain('Trainees interviewed') // plain field label
  })
})

it('longtext fields render as a labelled bordered box, not an inline label:value line', () => {
  const data = { ...EMPTY_DATA, fields: { key_findings: 'Line one\nLine two' } }
  const html = reportHtml(template, data, meta)
  expect(html).toContain('<div class="field-box">')
  expect(html).toContain('Line one<br>Line two')
})

// ONE REPORT LAYOUT FOR ALL OUTPUTS: choice/select fields in a top-level "fields" block (K's
// compliance, M's rating/follow-up, ...) render as an inline ☒/☐ option row, same as docx's
// inlineChoiceParagraph -- not just a single coloured label (that's reserved for card cells,
// which don't have room for every option).
it('choice/select fields render every option inline with a tick box, matching docx', () => {
  const data = { ...EMPTY_DATA, fields: { compliance: 'none' } }
  const html = reportHtml(template, data, meta)
  const kSection = html.slice(html.indexOf('>K<'), html.indexOf('>L<'))
  expect(kSection).toContain('choice-line')
  expect(kSection).toContain('All complied')
  expect(kSection).toContain('Partly')
  expect(kSection).toContain('choice-opt checked" style="color:#b3261e">☒ Not complied')
})

it('header carries template title/program and officer meta', () => {
  const html = reportHtml(template, EMPTY_DATA, { officerName: 'Rakib Hasan', status: 'submitted', submittedAt: '2026-09-24T10:00:00Z' })
  expect(html).toContain(template.title)
  expect(html).toContain(template.program)
  expect(html).toContain('Rakib Hasan')
  expect(html).toContain('Submitted')
})

describe('fixture parity smoke test', () => {
  it('renders the shared progress fixture without throwing and includes its known content', () => {
    const html = reportHtml(template, fixture.data, { officerName: 'Test Officer', status: 'draft' })
    expect(html).toContain('Bangladesh-Korea TTC, Mirpur')
    expect(html).toContain('Theory instead of practical')
    expect(html).toContain('Rakib')
    expect(html).toContain('Md. Karim') // persons cards
    expect(html).toContain('Dropout register missing') // K unresolved cards
    expect(html).not.toContain('Not answered')
  })

  it('renders the per-course materials_1 split (Welding yes, Electrical no) as a summary line', () => {
    const html = reportHtml(template, fixture.data, { officerName: 'Test Officer', status: 'draft' })
    expect(html).toContain('Welding (SMAW) 07: Yes')
    expect(html).toContain('Electrical Installation 03: No')
  })

  it('renders the interview per-course content (Electrode angle technical topic)', () => {
    const html = reportHtml(template, fixture.data, { officerName: 'Test Officer', status: 'draft' })
    expect(html).toContain('Electrode angle')
  })
})
