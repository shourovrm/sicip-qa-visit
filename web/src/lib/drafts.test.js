import { describe, it, expect } from 'vitest'
import {
  componentNotes, hasNotes, fallbackDraft, strengthsPromptText, parseStrengthsAnswer,
  allWeaknesses, numberedText, parseNumbered, planCards, recommendationsFromPlan,
} from './drafts.js'
import template from '../../../shared/report-templates/qa-v1.json'
import fixture from '../../../shared/report-templates/fixtures/drafts-qa-1.json'

describe('QA drafts (fixture parity with reference.py)', () => {
  const data = fixture.data
  const newId = (i) => `new-${i}`

  it('sorts one component into seen / gaps / notes / feedback', () => {
    const notes = componentNotes(template, data, 's8')
    expect(notes).toEqual(fixture.component_s8)
    expect(fallbackDraft(notes)).toEqual(fixture.fallback_s8)
    expect(strengthsPromptText(notes)).toBe(fixture.prompt_s8)
    expect(hasNotes(componentNotes(template, data, 's3'))).toBe(fixture.has_notes_s3)
  })

  it('parses strengths answers, null without headings', () => {
    for (const c of fixture.parse_strengths) expect(parseStrengthsAnswer(c.text)).toEqual(c.expected)
  })

  it('collects weaknesses and numbers them', () => {
    expect(allWeaknesses(template, data)).toEqual(fixture.weaknesses)
    expect(numberedText(fixture.weaknesses)).toBe(fixture.plan_prompt)
  })

  it('parses numbered answers, null when one is missing', () => {
    for (const c of fixture.parse_numbered) expect(parseNumbered(c.text, c.count)).toEqual(c.expected)
  })

  it('builds plan cards keeping responsible/timeline, and recommendations', () => {
    const existing = data.cards.plan
    expect(planCards(fixture.weaknesses, fixture.actions, existing, newId)).toEqual(fixture.plan_ai)
    expect(planCards(fixture.weaknesses, null, existing, newId)).toEqual(fixture.plan_failed)
    expect(recommendationsFromPlan(fixture.plan_ai)).toBe(fixture.recommendations)
  })
})
