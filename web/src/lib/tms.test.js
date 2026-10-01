import { describe, it, expect, vi, beforeEach } from 'vitest'
import { unwrapTmsResponse, tmsMessageText } from './tmsresponse.js'
import { loginToTms, jwtExpiry, publicIp } from './tmslogin.js'
import { loadTmsSession, saveTmsSession, clearTmsSession } from './tmssession.js'

const base64url = (object) => Buffer.from(JSON.stringify(object)).toString('base64url')
const jwt = (payload) => `${base64url({ alg: 'HS256' })}.${base64url(payload)}.sig`
const jsonReply = (status, body) => ({ status, json: async () => body, text: async () => JSON.stringify(body) })

// fetch fake: ipify answers first, auth/login second
function fakeFetch(loginReply, ip = '203.0.113.7') {
  return vi.fn(async (url) => {
    if (url.startsWith('https://api.ipify.org')) return jsonReply(200, { ip })
    return loginReply
  })
}

describe('tmsresponse', () => {
  it('returns data on success', () => {
    expect(unwrapTmsResponse(200, JSON.stringify({ status: 'success', data: { a: 1 } }))).toEqual({ a: 1 })
  })
  it('uses the TMS string message', () => {
    expect(() => unwrapTmsResponse(200, JSON.stringify({ status: 'error', message: 'UserName or Password Not Match!' })))
      .toThrow('UserName or Password Not Match!')
  })
  it('flattens an object message and falls back on an empty one', () => {
    expect(tmsMessageText({ username: ['Required.'], password: ['Too short.'] }, 'x')).toBe('Required. Too short.')
    expect(() => unwrapTmsResponse(200, JSON.stringify({ status: 'error', message: {} }), 'fallback text')).toThrow('fallback text')
  })
  it('reports 5xx and non-JSON bodies plainly', () => {
    expect(() => unwrapTmsResponse(502, '<html>')).toThrow('TMS server error 502')
    expect(() => unwrapTmsResponse(200, '<html>')).toThrow('unexpected reply')
  })
})

describe('tmslogin', () => {
  it('reads exp from a JWT, null when unreadable', () => {
    expect(jwtExpiry(jwt({ exp: 1790000000 }))).toBe(1790000000)
    expect(jwtExpiry('garbage')).toBeNull()
  })

  it('posts the JSON body the TMS web frontend sends', async () => {
    const token = jwt({ exp: 1790000000 })
    const fetchImpl = fakeFetch(jsonReply(200, { status: 'success', data: { token, user_info: { employee: { name: 'Riad' } } } }))
    const session = await loginToTms('riad', 'secret', { fetchImpl, userAgent: 'UA/1' })
    expect(session).toEqual({ token, displayName: 'Riad', expiresAt: 1790000000 })
    const [url, init] = fetchImpl.mock.calls[1]
    expect(url).toBe('https://bee.sicip.gov.bd/api/auth/login')
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ username: 'riad', password: 'secret', user_agent: 'UA/1', user_ip: '203.0.113.7' })
  })

  it('falls back to the username and a 12 h expiry', async () => {
    const fetchImpl = fakeFetch(jsonReply(200, { status: 'success', data: { token: 'opaque', user_info: {} } }))
    const session = await loginToTms('riad', 'pw', { fetchImpl, userAgent: 'UA', nowSeconds: () => 1000 })
    expect(session.displayName).toBe('riad')
    expect(session.expiresAt).toBe(1000 + 12 * 3600)
  })

  it('throws the TMS refusal message', async () => {
    const fetchImpl = fakeFetch(jsonReply(200, { status: 'error', message: {} }))
    await expect(loginToTms('riad', 'bad', { fetchImpl, userAgent: 'UA' })).rejects.toThrow('TMS rejected the username or password')
  })

  it('says so when TMS is unreachable', async () => {
    const fetchImpl = vi.fn(async () => { throw new TypeError('Failed to fetch') })
    await expect(loginToTms('riad', 'pw', { fetchImpl, userAgent: 'UA' })).rejects.toThrow('Could not reach TMS')
  })

  it('sends an empty IP when the lookup fails', async () => {
    expect(await publicIp(async () => { throw new Error('offline') })).toBe('')
  })
})

describe('tmssession', () => {
  beforeEach(() => {
    const store = new Map()
    globalThis.localStorage = {
      getItem: (key) => (store.has(key) ? store.get(key) : null),
      setItem: (key, value) => store.set(key, String(value)),
      removeItem: (key) => store.delete(key),
    }
  })

  it('stores token, name and expiry but never a password', () => {
    saveTmsSession({ token: 't', displayName: 'Riad', expiresAt: 2000, password: 'secret' })
    expect(localStorage.getItem('tmsSession')).not.toContain('secret')
    expect(loadTmsSession(1000)).toEqual({ token: 't', displayName: 'Riad', expiresAt: 2000 })
  })

  it('treats an expired or missing session as logged out', () => {
    expect(loadTmsSession(1000)).toBeNull()
    saveTmsSession({ token: 't', displayName: 'Riad', expiresAt: 2000 })
    expect(loadTmsSession(2000)).toBeNull()
  })

  it('clears on logout', () => {
    saveTmsSession({ token: 't', displayName: 'Riad', expiresAt: 2000 })
    clearTmsSession()
    expect(loadTmsSession(1000)).toBeNull()
  })
})
