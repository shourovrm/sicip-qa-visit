import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import worker from './index.js'
import { DEFAULT_MODEL_KEY } from './models.js'

function makeEnv() {
  return {
    AI: { run: vi.fn().mockResolvedValue({ response: 'Trainer arrived late.' }) },
    ASSETS: { fetch: vi.fn().mockResolvedValue(new Response('<html></html>')) },
    SUPABASE_URL: 'https://x.supabase.co',
    SUPABASE_KEY: 'anon-key',
  }
}

beforeEach(() => {
  global.fetch = vi.fn().mockResolvedValue({ ok: true }) // gotrue auth check
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('worker router', () => {
  it('passes non-/api requests straight through to ASSETS', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/tours')
    const res = await worker.fetch(req, env)
    expect(env.ASSETS.fetch).toHaveBeenCalledWith(req)
    expect(await res.text()).toBe('<html></html>')
  })

  it('answers an OPTIONS preflight from an allowed dev origin', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/rewrite', {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:5173' },
    })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(204)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://localhost:5173')
  })

  it('omits CORS headers for an origin not on the allowlist', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/rewrite', {
      method: 'OPTIONS',
      headers: { Origin: 'https://evil.example' },
    })
    const res = await worker.fetch(req, env)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })

  it('returns 404 JSON for an unknown /api/* path', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/nope', { method: 'GET' })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(404)
    expect(await res.json()).toEqual({ error: 'not_found' })
  })

  it('serves /api/rewrite end to end and adds CORS for the deployed origin', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/rewrite', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        Authorization: 'Bearer good-token',
        Origin: 'https://sicip-qa-visit.shourovrm.workers.dev',
      },
      body: JSON.stringify({ text: 'trainer aslo lat' }),
    })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ text: 'Trainer arrived late.', model: DEFAULT_MODEL_KEY })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('https://sicip-qa-visit.shourovrm.workers.dev')
  })

  it('serves /api/rewrite with no CORS header for an Android request (no Origin)', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/rewrite', {
      method: 'POST',
      headers: { 'content-type': 'application/json', Authorization: 'Bearer good-token' },
      body: JSON.stringify({ text: 'trainer aslo lat' }),
    })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(200)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })

  it('answers GET /api/models with the default key, model list and a 5-minute cache header', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/models', {
      method: 'GET',
      headers: { Origin: 'http://localhost:5173' },
    })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(200)
    expect(res.headers.get('Cache-Control')).toBe('max-age=300')
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://localhost:5173')

    const body = await res.json()
    expect(body.default).toBe(DEFAULT_MODEL_KEY)
    expect(Array.isArray(body.models)).toBe(true)
    expect(body.models.length).toBeGreaterThan(0)
    expect(body.models[0]).toEqual(
      expect.objectContaining({ key: expect.any(String), id: expect.any(String), label: expect.any(String) }),
    )
  })

  it('rejects non-GET /api/models with 405', async () => {
    const env = makeEnv()
    const req = new Request('https://x.workers.dev/api/models', { method: 'POST' })
    const res = await worker.fetch(req, env)
    expect(res.status).toBe(405)
  })
})
