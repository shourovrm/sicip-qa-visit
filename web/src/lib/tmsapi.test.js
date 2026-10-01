import { describe, it, expect, vi } from 'vitest'
import { TmsSignedOutError, tmsGet } from './tmsapi.js'

const reply = (status, body) => ({ status, text: async () => JSON.stringify(body) })
const session = () => ({ token: 'tok', displayName: 'Officer', expiresAt: 9999999999 })

describe('tmsGet', () => {
  it('sends the bearer token and unwraps data', async () => {
    const fetchImpl = vi.fn(async () => reply(200, { status: 'success', data: [1, 2] }))
    expect(await tmsGet('batch/list?x=1', { fetchImpl, currentSession: session, signOut: () => {} })).toEqual([1, 2])
    const [url, init] = fetchImpl.mock.calls[0]
    expect(url).toBe('https://bee.sicip.gov.bd/api/batch/list?x=1')
    expect(init.headers.Authorization).toBe('Bearer tok')
  })
  it('401 clears the session and reports signed out', async () => {
    const signOut = vi.fn()
    const fetchImpl = async () => reply(401, { status: 'error', message: 'Unauthorized' })
    await expect(tmsGet('x', { fetchImpl, currentSession: session, signOut })).rejects.toBeInstanceOf(TmsSignedOutError)
    expect(signOut).toHaveBeenCalledOnce()
  })
  it('no session -> signed out without a request', async () => {
    const fetchImpl = vi.fn()
    await expect(tmsGet('x', { fetchImpl, currentSession: () => null, signOut: () => {} })).rejects.toBeInstanceOf(TmsSignedOutError)
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})
