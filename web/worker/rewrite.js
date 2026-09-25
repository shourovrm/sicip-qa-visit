// rewrite -- POST /api/rewrite handler. one job: text in, rewritten text out.
import { isValidToken, bearerToken } from './auth.js'
import { buildMessages, cleanOutput } from './prompt.js'
import { modelFor } from './models.js'

const MAX_LEN = 1500
const MAX_LABEL_LEN = 80

function json(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

// older models answer in `response`, newer chat-completions ones in choices[0].message.content
function answerText(result) {
  if (!result) return ''
  if (typeof result.response === 'string') return result.response
  return result.choices?.[0]?.message?.content ?? ''
}

export async function handleRewrite(request, env) {
  if (request.method !== 'POST') return json({ error: 'method' }, 405)

  const token = bearerToken(request)
  if (!(await isValidToken(token, env))) return json({ error: 'auth' }, 401)

  let body
  try {
    body = await request.json()
  } catch {
    return json({ error: 'bad_request' }, 400)
  }
  if (!body || typeof body.text !== 'string') return json({ error: 'bad_request' }, 400)

  const text = body.text.trim()
  if (!text) return json({ error: 'bad_request' }, 400)
  if (text.length > MAX_LEN) return json({ error: 'too_long' }, 413)

  const label = typeof body.label === 'string' ? body.label.trim().slice(0, MAX_LABEL_LEN) : ''
  // unknown/missing key resolves to the default model -- old clients or a stale saved
  // setting must keep working, this never 400s. same for mode: anything but "remarks" (incl.
  // missing) is the default "Improve wording" rewrite -- never 400s on an old client either.
  const model = modelFor(typeof body.model === 'string' ? body.model : undefined)
  const mode = body.mode === 'remarks' ? 'remarks' : undefined

  let result
  try {
    result = await env.AI.run(model.id, {
      messages: buildMessages(text, label, mode),
      temperature: 0.2,
      max_tokens: 700,
      ...model.options,
    })
  } catch (err) {
    // 3036 = daily free Workers AI allocation used up (see Cloudflare Workers AI error table).
    const message = String(err && err.message)
    if (message.includes('3036') || message.includes('daily free allocation')) {
      return json({ error: 'quota' }, 429)
    }
    return json({ error: 'ai' }, 502)
  }

  const rewritten = cleanOutput(answerText(result), label)
  if (!rewritten) return json({ error: 'ai' }, 502)

  return json({ text: rewritten, model: model.key }, 200)
}
