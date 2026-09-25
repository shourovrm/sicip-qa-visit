import { describe, it, expect } from 'vitest'
import { buildMessages, cleanOutput } from './prompt.js'

describe('buildMessages', () => {
  it('puts the label as context ahead of the text', () => {
    const messages = buildMessages('trainer late', 'Findings')
    expect(messages[0].role).toBe('system')
    expect(messages[1].role).toBe('user')
    expect(messages[1].content).toBe('Field: Findings\n\ntrainer late')
  })

  it('omits the context line when there is no label', () => {
    const messages = buildMessages('trainer late', '')
    expect(messages[1].content).toBe('trainer late')
  })
})

describe('cleanOutput', () => {
  it('passes clean text through unchanged', () => {
    expect(cleanOutput('Trainer arrived late.')).toBe('Trainer arrived late.')
  })

  it('strips a "Here is..." preamble', () => {
    expect(cleanOutput('Here is the rewritten text: Trainer arrived late.')).toBe('Trainer arrived late.')
  })

  it('strips wrapping double quotes', () => {
    expect(cleanOutput('"Trainer arrived late."')).toBe('Trainer arrived late.')
  })

  it('strips both a preamble and wrapping quotes together', () => {
    expect(cleanOutput('Sure, "Trainer arrived late."')).toBe('Trainer arrived late.')
  })

  it('returns an empty string for empty/whitespace input', () => {
    expect(cleanOutput('   ')).toBe('')
    expect(cleanOutput(undefined)).toBe('')
  })
})
