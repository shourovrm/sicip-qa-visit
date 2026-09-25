// rewriteText -- only the error-code -> short user message mapping and the auth/network paths
// are worth testing here; the actual Worker endpoint doesn't exist in this test environment.
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./supabase.js', () => ({
  supabase: { auth: { getSession: vi.fn() } },
}))

import { supabase } from './supabase.js'
import { rewriteText } from './rewrite.js'

const SESSION = { data: { session: { access_token: 'tok-123' } } }
const NO_SESSION = { data: { session: null } }

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

beforeEach(() => {
  vi.restoreAllMocks()
})

it('returns the rewritten text on 200', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(200, { text: 'Rewritten.' }))

  const result = await rewriteText('raw text', 'Remarks')

  expect(result).toBe('Rewritten.')
  expect(global.fetch).toHaveBeenCalledWith(
    '/api/rewrite',
    expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer tok-123' }),
      body: JSON.stringify({ text: 'raw text', label: 'Remarks' }),
    })
  )
})

it('throws the auth message with no session, before calling fetch', async () => {
  supabase.auth.getSession.mockResolvedValue(NO_SESSION)
  global.fetch = vi.fn()

  await expect(rewriteText('raw text', 'Remarks')).rejects.toThrow('Session expired — sign in again')
  expect(global.fetch).not.toHaveBeenCalled()
})

it('maps 401 {error:"auth"} to the session-expired message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(401, { error: 'auth' }))

  await expect(rewriteText('raw text', 'Remarks')).rejects.toThrow('Session expired — sign in again')
})

it('maps 413 {error:"too_long"} to the length message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(413, { error: 'too_long' }))

  await expect(rewriteText('x'.repeat(2000), 'Remarks')).rejects.toThrow('Too long to improve (max 1500 characters)')
})

it('maps 429 {error:"quota"} to the daily-limit message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(429, { error: 'quota' }))

  await expect(rewriteText('raw text', 'Remarks')).rejects.toThrow('Daily limit reached — try again tomorrow')
})

it('maps 502 {error:"ai"} to the generic could-not-improve message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(502, { error: 'ai' }))

  await expect(rewriteText('raw text', 'Remarks')).rejects.toThrow('Could not improve wording — try again')
})

it('maps an unrecognised error code (e.g. 400 bad_request) to the generic message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockResolvedValue(jsonResponse(400, { error: 'bad_request' }))

  await expect(rewriteText('', 'Remarks')).rejects.toThrow('Could not improve wording — try again')
})

it('maps a fetch rejection (offline) to the offline message', async () => {
  supabase.auth.getSession.mockResolvedValue(SESSION)
  global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

  await expect(rewriteText('raw text', 'Remarks')).rejects.toThrow('You are offline')
})
