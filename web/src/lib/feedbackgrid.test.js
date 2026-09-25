import { describe, it, expect } from 'vitest'
import { feedbackGrid } from './feedbackgrid.js'
import template from '../../../shared/report-templates/qa-v1.json'

const traineeBlock = template.sections.find((s) => s.key === 's11').blocks[0]

describe('feedbackGrid', () => {
  it('prints a blank 2-column form when nobody was interviewed', () => {
    const { tables, comments } = feedbackGrid(traineeBlock, [])
    expect(tables).toHaveLength(1)
    expect(tables[0].headers).toEqual(['Question', 'Trainee 1', 'Trainee 2'])
    expect(tables[0].rows[0]).toEqual(['Trade', '', ''])
    expect(comments).toEqual([])
  })

  it('shows answer labels, never names, and splits after 4 respondents', () => {
    const cards = Array.from({ length: 5 }, (_, i) => ({ _id: `t${i}`, name: 'Secret', trade: 'Welding', tq1: i === 0 ? 'no' : 'yes' }))
    cards[4].feedback = 'Need more practice'
    const { tables, comments } = feedbackGrid(traineeBlock, cards)
    expect(tables.map((t) => t.headers.length)).toEqual([5, 2])
    const tq1 = tables[0].rows.find((row) => row[0].startsWith('Trainer explains'))
    expect(tq1.slice(1)).toEqual(['No', 'Yes', 'Yes', 'Yes'])
    expect(JSON.stringify(tables)).not.toContain('Secret')
    expect(comments).toEqual(['Trainee 5: Need more practice'])
  })
})
