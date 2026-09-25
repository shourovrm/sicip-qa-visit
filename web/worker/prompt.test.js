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

  it('mode "remarks" uses the remarks system prompt, not the default one', () => {
    const remarksMessages = buildMessages('bullet one\nbullet two', 'Findings', 'remarks')
    const defaultMessages = buildMessages('bullet one\nbullet two', 'Findings')
    expect(remarksMessages[0].content).not.toBe(defaultMessages[0].content)
    expect(remarksMessages[0].content).toMatch(/one per line/i)
    expect(remarksMessages[1]).toEqual(defaultMessages[1]) // user content unaffected by mode
  })

  it('any mode other than "remarks" (including undefined) uses the default prompt', () => {
    const a = buildMessages('x', '', undefined)
    const b = buildMessages('x', '', 'improve')
    expect(a[0].content).toBe(b[0].content)
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

  it('strips an echoed field label heading', () => {
    expect(cleanOutput('Remarks: The trainer was absent.', 'Remarks')).toBe('The trainer was absent.')
    expect(cleanOutput('**Key findings:** Three trainees had no ID.', 'Key findings')).toBe('Three trainees had no ID.')
    expect(cleanOutput('Field: Key findings\n\nTool store not locked.', 'Key findings')).toBe('Tool store not locked.')
    expect(cleanOutput('Key findings\n\nTool store not locked.', 'Key findings')).toBe('Tool store not locked.')
  })

  it('keeps the label word when it is real sentence content', () => {
    expect(cleanOutput('Remarks were recorded in the register.', 'Remarks')).toBe('Remarks were recorded in the register.')
  })
})
