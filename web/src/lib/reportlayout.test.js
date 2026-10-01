// layout v3 (2026-10-01) helpers shared by every report output
import { describe, it, expect } from 'vitest'
import {
  CONTENT_WIDTH_TWIPS, MARGIN_BOTTOM_TWIPS, MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS, MARGIN_TOP_TWIPS,
  answeredQuestionFields, displayTime, footerTitle, isCentredField, isShortValue, narrativeColumns,
} from './reportlayout.js'

describe('page geometry', () => {
  it('uses 1 in top/bottom and 0.75 in sides', () => {
    expect([MARGIN_TOP_TWIPS, MARGIN_BOTTOM_TWIPS, MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS]).toEqual([1440, 1440, 1080, 1080])
    expect(CONTENT_WIDTH_TWIPS).toBe(11906 - 2160)
  })
})

describe('displayTime', () => {
  it('prints stored times as h:mm AM/PM', () => {
    expect(displayTime('10:43:00')).toBe('10:43 AM')
    expect(displayTime('13:15')).toBe('1:15 PM')
    expect(displayTime('00:05:00')).toBe('12:05 AM')
    expect(displayTime('12:30')).toBe('12:30 PM')
    expect(displayTime('')).toBe('')
  })
})

describe('footerTitle', () => {
  it('drops the part after a colon', () => {
    expect(footerTitle({ title: 'Surprise Visit Report: Quality Assurance' })).toBe('Surprise Visit Report')
    expect(footerTitle({ title: 'Quality Assurance Visit Report' })).toBe('Quality Assurance Visit Report')
  })
})

describe('isCentredField', () => {
  it('centres numbers, batch no., dates, times and short choices', () => {
    expect(isCentredField({ key: 'present_total', kind: 'number' })).toBe(true)
    expect(isCentredField({ key: 'batch', kind: 'text' })).toBe(true)
    expect(isCentredField({ key: 'result', kind: 'choice' })).toBe(true)
    expect(isCentredField({ key: 'visit_date', kind: 'date' })).toBe(true)
    expect(isCentredField({ key: 'course', kind: 'text' })).toBe(false)
    expect(isCentredField({ key: 'remarks', kind: 'longtext' })).toBe(false)
    // "Welding · 07" names the course, so it reads as text
    expect(isCentredField({ key: 'batch', kind: 'courseRef' })).toBe(false)
  })
})

describe('isShortValue', () => {
  it('treats counts, percentages and short codes as centred cells', () => {
    expect(isShortValue('12')).toBe(true)
    expect(isShortValue('8 (40%)')).toBe(true)
    expect(isShortValue('Yes')).toBe(true)
    expect(isShortValue('Partial')).toBe(true)
    expect(isShortValue('B-77')).toBe(true)
    expect(isShortValue('Welding')).toBe(false)
    expect(isShortValue('Electrical Installation and Maintenance')).toBe(false)
    expect(isShortValue('')).toBe(false)
  })
})

describe('answeredQuestionFields', () => {
  const block = {
    linkFrom: { fields: ['course', 'batch'] },
    fields: [
      { key: 'course' }, { key: 'batch' }, { key: 'trainees_interviewed' },
      { key: 'q1' }, { key: 'q1_note', noteFor: 'q1' },
      { key: 'q2' }, { key: 'q2_note', noteFor: 'q2' },
      { key: 'q3' }, { key: 'q3_note', noteFor: 'q3' },
    ],
  }
  it('keeps a question only when some course answered it or wrote its note', () => {
    const cards = [
      { course: 'Welding', batch: '7', trainees_interviewed: '6', q1: 'yes' },
      { course: 'EIM', batch: '3', q3_note: 'stipend late' },
    ]
    expect(answeredQuestionFields(block, cards).map((f) => f.key)).toEqual(['trainees_interviewed', 'q1', 'q3'])
  })
  it('keeps nothing when nobody answered', () => {
    expect(answeredQuestionFields(block, [{ course: 'Welding', batch: '7', q2: '  ' }])).toEqual([])
  })
})

describe('narrativeColumns', () => {
  const field = (key, kind) => ({ key, kind, label: key })
  const wide = {
    titleField: 'course',
    fields: [field('course', 'text'), field('batch', 'text'), ...['a', 'b', 'c', 'd', 'e'].map((k) => field(k, 'number')), field('remarks', 'longtext')],
  }
  it('moves long text out of a table wider than 6 columns into lines under it', () => {
    const cards = [{ course: 'EIM', batch: '3', a: '1', b: '2', c: '3', d: '4', e: '5', remarks: 'Register late' }, { course: 'PPF', batch: '4', a: '1' }]
    const { columns, notes } = narrativeColumns(wide, cards)
    expect(columns.map((f) => f.key)).toEqual(['course', 'batch', 'a', 'b', 'c', 'd', 'e'])
    expect(notes).toEqual(['EIM batch-3: Register late'])
  })
  it('keeps long text as a column in a narrow table and drops columns nobody filled', () => {
    const narrow = { titleField: 'name', fields: [field('name', 'text'), field('qty', 'number'), field('remarks', 'longtext')] }
    const { columns, notes } = narrativeColumns(narrow, [{ name: 'Grinder', remarks: 'broken' }])
    expect(columns.map((f) => f.key)).toEqual(['name', 'remarks'])
    expect(notes).toEqual([])
  })
})
