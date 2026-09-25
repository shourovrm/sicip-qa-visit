import { describe, it, expect, vi, afterEach } from 'vitest'
import { isValidToken, bearerToken } from './auth.js'

const env = { SUPABASE_URL: 'https://x.supabase.co', SUPABASE_KEY: 'anon-key' }

afterEach(() => {
  vi.restoreAllMocks()
})

describe('bearerToken', () => {
  it('extracts the token from a well-formed header', () => {
    const req = new Request('https://x/', { headers: { Authorization: 'Bearer abc.123' } })
    expect(bearerToken(req)).toBe('abc.123')
  })

  it('returns null when there is no Authorization header', () => {
    const req = new Request('https://x/')
    expect(bearerToken(req)).toBeNull()
  })

  it('returns null for a non-Bearer scheme', () => {
    const req = new Request('https://x/', { headers: { Authorization: 'Basic abc' } })
    expect(bearerToken(req)).toBeNull()
  })
})

describe('isValidToken', () => {
  it('returns false with no token, without calling fetch', async () => {
    global.fetch = vi.fn()
    expect(await isValidToken(null, env)).toBe(false)
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('returns true when gotrue accepts the token', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true })
    expect(await isValidToken('good', env)).toBe(true)
    const [url, options] = global.fetch.mock.calls[0]
    expect(url).toBe('https://x.supabase.co/auth/v1/user')
    expect(options.headers.Authorization).toBe('Bearer good')
    expect(options.headers.apikey).toBe('anon-key')
  })

  it('returns false when gotrue rejects the token', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: false })
    expect(await isValidToken('bad', env)).toBe(false)
  })

  it('returns false when the network call throws', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('network down'))
    expect(await isValidToken('whatever', env)).toBe(false)
  })
})
