import { describe, expect, it } from 'vitest'
import { addSelectedText, findingLines, openFindingsBox, setTicked, tickedLines } from './findingsbox.js'

const block = { type: 'findings', key: 'findings', boxKey: 'findingsText', tickedKey: 'findingsTicked' }
const candidates = [{ text: 'A ok.', neg: false }, { text: 'B bad.', neg: true }, { text: 'C ok.', neg: false }]

describe('findings box', () => {
  it('prints the box lines, falling back to the old picked list', () => {
    expect(findingLines(block, { findingsText: ' One \n\nTwo ' })).toEqual(['One', 'Two'])
    expect(findingLines(block, { findingsText: '', findings: [{ text: 'old' }] })).toEqual([])
    expect(findingLines(block, { findings: [{ src: '', text: 'old' }, { text: ' ' }] })).toEqual(['old'])
    expect(findingLines(block, {})).toEqual([])
  })

  it('moves an old picked list into the box on open and keeps the old data', () => {
    const data = { findings: [{ src: 'x', text: 'First' }, { src: '', text: 'Second' }] }
    expect(openFindingsBox(block, data)).toBe(true)
    expect(data.findingsText).toBe('First\nSecond')
    expect(data.findings).toHaveLength(2)
    expect(openFindingsBox(block, data)).toBe(false) // already a box
    const fresh = {}
    expect(openFindingsBox(block, fresh)).toBe(false)
    expect(fresh.findingsText).toBeUndefined()
  })

  it('ticking keeps every line in place; select all / none', () => {
    const data = {}
    setTicked(block, data, ['C ok.'])
    expect(tickedLines(block, data, candidates)).toEqual(['C ok.'])
    setTicked(block, data, candidates.map((c) => c.text))
    expect(tickedLines(block, data, candidates)).toEqual(['A ok.', 'B bad.', 'C ok.'])
    setTicked(block, data, [])
    expect(tickedLines(block, data, candidates)).toEqual([])
    expect(data.findingsTicked).toEqual([])
  })

  it('a ticked line that is no longer written does not count', () => {
    const data = { findingsTicked: ['gone', 'B bad.'] }
    expect(tickedLines(block, data, candidates)).toEqual(['B bad.'])
  })

  it('selected lines go into the box in report order, one per line', () => {
    const data = { findingsTicked: ['C ok.', 'A ok.'] }
    expect(addSelectedText(block, data, candidates)).toBe('A ok.\nC ok.')
  })
})
