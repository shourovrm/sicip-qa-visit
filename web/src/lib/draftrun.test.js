import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./rewrite.js', () => ({ draftText: vi.fn() }))
const { draftText } = await import('./rewrite.js')
const { draftComponent, draftFindings, draftPlan } = await import('./draftrun.js')
import template from '../../../shared/report-templates/qa-v1.json'
import fixture from '../../../shared/report-templates/fixtures/drafts-qa-1.json'

const s8 = template.sections.flatMap((s) => s.blocks).flatMap((b) => b.pairs ?? []).find((p) => p.source === 's8')

describe('draftrun', () => {
  beforeEach(() => {
    draftText.mockReset()
  })

  it('uses the parsed AI answer', async () => {
    draftText.mockResolvedValue('STRENGTHS:\n- Extinguisher checked 12/02/2026.\nWEAKNESSES:\n- No PPE list.')
    const draft = await draftComponent(template, fixture.data, s8)
    expect(draft).toEqual({ strengths: ['Extinguisher checked 12/02/2026.'], weaknesses: ['No PPE list.'], usedFallback: false })
    expect(draftText.mock.calls[0]).toEqual([fixture.prompt_s8, 'strengths'])
  })

  it('falls back when the AI changes a number', async () => {
    draftText.mockResolvedValue('STRENGTHS:\n- Checked 13/02/2026.\nWEAKNESSES:\n- None')
    const draft = await draftComponent(template, fixture.data, s8)
    expect(draft).toEqual({ ...fixture.fallback_s8, usedFallback: true })
  })

  it('falls back when offline', async () => {
    draftText.mockImplementation(async () => {
      throw new Error('You are offline')
    })
    expect((await draftFindings(template, fixture.data)).lines).toEqual(fixture.weaknesses)
    const plan = await draftPlan(template, fixture.data, fixture.data.cards.plan)
    expect(plan.usedFallback).toBe(true)
    expect(plan.cards.map((c) => c.action)).toEqual(fixture.plan_failed.map((c) => c.action))
  })

  it('maps numbered plan actions onto cards', async () => {
    draftText.mockResolvedValue(fixture.actions.map((a, i) => `${i + 1}. ${a}`).join('\n'))
    const plan = await draftPlan(template, fixture.data, fixture.data.cards.plan)
    expect(plan.usedFallback).toBe(false)
    expect(plan.cards.map((c) => c.action)).toEqual(fixture.actions)
    expect(plan.cards[1]).toEqual(fixture.plan_ai[1])
  })
})
