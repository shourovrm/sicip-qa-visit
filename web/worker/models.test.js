// models tests -- modelFor lookup/fallback only, MODELS/DEFAULT_MODEL_KEY are plain data.
import { describe, it, expect } from 'vitest'
import { MODELS, DEFAULT_MODEL_KEY, modelFor } from './models.js'

describe('modelFor', () => {
  it('has a default key that points at a real entry in MODELS', () => {
    expect(MODELS.some((model) => model.key === DEFAULT_MODEL_KEY)).toBe(true)
  })

  it('returns the matching entry for a known key', () => {
    const entry = modelFor(DEFAULT_MODEL_KEY)
    expect(entry.key).toBe(DEFAULT_MODEL_KEY)
    expect(entry.id).toBe(MODELS.find((model) => model.key === DEFAULT_MODEL_KEY).id)
  })

  it('falls back to the default entry for an unknown key', () => {
    expect(modelFor('not-a-real-key')).toEqual(modelFor(DEFAULT_MODEL_KEY))
  })

  it('falls back to the default entry for a missing key', () => {
    expect(modelFor(undefined)).toEqual(modelFor(DEFAULT_MODEL_KEY))
  })
})
