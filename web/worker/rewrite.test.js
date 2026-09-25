// rewrite handler tests -- mocked env.AI + mocked global fetch (auth).
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { handleRewrite } from './rewrite.js'
import { modelFor, DEFAULT_MODEL_KEY } from './models.js'

const env = { AI: { run: vi.fn() }, SUPABASE_URL: 'https://x.supabase.co', SUPABASE_KEY: 'anon-key' }

function req(body, { method = 'POST', auth = 'Bearer good-token' } = {}) {
  const headers = { 'content-type': 'application/json' }
  if (auth) headers.Authorization = auth
  return new Request('https://x.workers.dev/api/rewrite', {
    method,
    headers,
    body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
  })
}

beforeEach(() => {
  env.AI.run.mockReset()
  // default: auth check succeeds (gotrue returns 200)
  global.fetch = vi.fn().mockResolvedValue({ ok: true })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('handleRewrite', () => {
  it('rejects non-POST with 405', async () => {
    const res = await handleRewrite(req(undefined, { method: 'GET', auth: null }), env)
    expect(res.status).toBe(405)
    expect(await res.json()).toEqual({ error: 'method' })
  })

  it('rejects missing/invalid token with 401', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: false })
    const res = await handleRewrite(req({ text: 'hello' }), env)
    expect(res.status).toBe(401)
    expect(await res.json()).toEqual({ error: 'auth' })
  })

  it('rejects missing Authorization header with 401 without calling fetch', async () => {
    const res = await handleRewrite(req({ text: 'hello' }, { auth: null }), env)
    expect(res.status).toBe(401)
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('rejects non-JSON body with 400', async () => {
    const res = await handleRewrite(req('not json'), env)
    expect(res.status).toBe(400)
    expect(await res.json()).toEqual({ error: 'bad_request' })
  })

  it('rejects missing text field with 400', async () => {
    const res = await handleRewrite(req({ label: 'Findings' }), env)
    expect(res.status).toBe(400)
    expect(await res.json()).toEqual({ error: 'bad_request' })
  })

  it('rejects text that trims to empty with 400', async () => {
    const res = await handleRewrite(req({ text: '   ' }), env)
    expect(res.status).toBe(400)
    expect(await res.json()).toEqual({ error: 'bad_request' })
  })

  it('rejects text over 1500 chars with 413', async () => {
    const res = await handleRewrite(req({ text: 'a'.repeat(1501) }), env)
    expect(res.status).toBe(413)
    expect(await res.json()).toEqual({ error: 'too_long' })
    expect(env.AI.run).not.toHaveBeenCalled()
  })

  it('maps Workers AI quota error (3036) to 429', async () => {
    env.AI.run.mockRejectedValue(new Error('3036: You have used up your daily free allocation of 10000 neurons.'))
    const res = await handleRewrite(req({ text: 'hello' }), env)
    expect(res.status).toBe(429)
    expect(await res.json()).toEqual({ error: 'quota' })
  })

  it('maps any other AI failure to 502', async () => {
    env.AI.run.mockRejectedValue(new Error('5007: No such model'))
    const res = await handleRewrite(req({ text: 'hello' }), env)
    expect(res.status).toBe(502)
    expect(await res.json()).toEqual({ error: 'ai' })
  })

  it('maps empty AI output to 502', async () => {
    env.AI.run.mockResolvedValue({ response: '   ' })
    const res = await handleRewrite(req({ text: 'hello' }), env)
    expect(res.status).toBe(502)
    expect(await res.json()).toEqual({ error: 'ai' })
  })

  it('returns the cleaned rewritten text on success', async () => {
    env.AI.run.mockResolvedValue({ response: 'Here is the rewritten text: "Trainer arrived late."' })
    const res = await handleRewrite(req({ text: 'trainer aslo lat', label: 'Findings' }), env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ text: 'Trainer arrived late.', model: 'gemma' })

    const [modelId, options] = env.AI.run.mock.calls[0]
    expect(modelId).toContain('@cf/')
    expect(options.temperature).toBeCloseTo(0.2)
    expect(options.max_tokens).toBe(700)
    expect(options.messages[1].content).toContain('Findings')
    expect(options.messages[1].content).toContain('trainer aslo lat')
  })

  it('uses the requested model id in env.AI.run when a known key is sent', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const res = await handleRewrite(req({ text: 'hello', model: 'scout' }), env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ text: 'ok', model: 'scout' })
    const [modelId] = env.AI.run.mock.calls[0]
    expect(modelId).toBe(modelFor('scout').id)
  })

  it('falls back to the default model id for an unknown model key', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const res = await handleRewrite(req({ text: 'hello', model: 'not-a-real-model' }), env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ text: 'ok', model: DEFAULT_MODEL_KEY })
    const [modelId] = env.AI.run.mock.calls[0]
    expect(modelId).toBe(modelFor(DEFAULT_MODEL_KEY).id)
  })

  it('falls back to the default model id when no model key is sent', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const res = await handleRewrite(req({ text: 'hello' }), env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ text: 'ok', model: DEFAULT_MODEL_KEY })
    const [modelId] = env.AI.run.mock.calls[0]
    expect(modelId).toBe(modelFor(DEFAULT_MODEL_KEY).id)
  })

  it('mode:"remarks" uses the remarks system prompt (QA report AI remarks)', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const res = await handleRewrite(req({ text: 'bullet one\nbullet two', mode: 'remarks' }), env)
    expect(res.status).toBe(200)
    const [, options] = env.AI.run.mock.calls[0]
    expect(options.messages[0].content).toMatch(/one per line/i)
  })

  it('an unrecognised mode value falls back to the default prompt, never 400s', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const res = await handleRewrite(req({ text: 'hello', mode: 'not-a-real-mode' }), env)
    expect(res.status).toBe(200)
    const [, options] = env.AI.run.mock.calls[0]
    expect(options.messages[0].content).not.toMatch(/one per line/i)
  })

  it('truncates an oversized label to 80 chars without failing', async () => {
    env.AI.run.mockResolvedValue({ response: 'ok' })
    const longLabel = 'x'.repeat(200)
    const res = await handleRewrite(req({ text: 'hello', label: longLabel }), env)
    expect(res.status).toBe(200)
    const [, options] = env.AI.run.mock.calls[0]
    expect(options.messages[1].content).toContain('x'.repeat(80))
    expect(options.messages[1].content).not.toContain('x'.repeat(81))
  })
})

describe('handleRewrite response shapes and model options', () => {
  it('reads choices[0].message.content and passes model options', async () => {
    env.AI.run.mockResolvedValue({ choices: [{ message: { content: 'Remarks: Fan broken.' } }] })
    const res = await handleRewrite(req({ text: 'fan nosto', label: 'Remarks' }), env)
    expect(await res.json()).toEqual({ text: 'Fan broken.', model: 'gemma' })
    expect(env.AI.run.mock.calls[0][1].chat_template_kwargs).toEqual({ enable_thinking: false })
  })
})
