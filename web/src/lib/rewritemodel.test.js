// getModel/setModel (localStorage) + loadModels (fetch /api/models). This vitest project has no
// jsdom/DOM environment configured (see rewrite.test.js), so localStorage is faked here with a
// plain in-memory Map -- same shape as the real Storage API, no browser needed.
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getModel, setModel, loadModels } from './rewritemodel.js'

function fakeLocalStorage() {
  const store = new Map()
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  }
}

function jsonResponse(ok, body) {
  return { ok, json: async () => body }
}

beforeEach(() => {
  globalThis.localStorage = fakeLocalStorage()
  vi.restoreAllMocks()
})

describe('getModel/setModel', () => {
  it('returns null when nothing is saved (server default)', () => {
    expect(getModel()).toBeNull()
  })

  it('returns the saved key after setModel', () => {
    setModel('llama-3.3')
    expect(getModel()).toBe('llama-3.3')
  })

  it('setModel(null) clears a previously saved key back to server default', () => {
    setModel('llama-3.3')
    setModel(null)
    expect(getModel()).toBeNull()
  })

  it("setModel('') also clears the saved key", () => {
    setModel('llama-3.3')
    setModel('')
    expect(getModel()).toBeNull()
  })
})

describe('loadModels', () => {
  it('returns the parsed body on success', async () => {
    const body = {
      default: 'llama-3.3',
      models: [{ key: 'llama-3.3', id: '@cf/meta/llama-3.3-70b-instruct-fp8-fast', label: 'Llama 3.3', note: 'Fast, free' }],
    }
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(true, body))

    expect(await loadModels()).toEqual(body)
    expect(global.fetch).toHaveBeenCalledWith('/api/models')
  })

  it('returns null on a non-ok response', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(false, {}))

    expect(await loadModels()).toBeNull()
  })

  it('returns null when fetch rejects (offline)', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    expect(await loadModels()).toBeNull()
  })

  it('returns null on malformed JSON in an ok response', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => {
        throw new SyntaxError('bad json')
      },
    })

    expect(await loadModels()).toBeNull()
  })
})
