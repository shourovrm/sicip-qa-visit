// index -- router. every request either hits /api/*, or falls through to static assets.
import { handleRewrite } from './rewrite.js'
import { MODELS, DEFAULT_MODEL_KEY } from './models.js'

const ALLOWED_ORIGINS = new Set([
  'https://sicip-qa-visit.shourovrm.workers.dev',
  'http://localhost:5173',
  'http://127.0.0.1:5173',
])

// only the browser sends Origin; android sends none, so it never gets (or needs) these headers.
function corsHeaders(request) {
  const origin = request.headers.get('Origin')
  if (!origin || !ALLOWED_ORIGINS.has(origin)) return {}
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  }
}

function withCors(response, request) {
  const headers = new Headers(response.headers)
  for (const [key, value] of Object.entries(corsHeaders(request))) headers.set(key, value)
  return new Response(response.body, { status: response.status, headers })
}

// GET /api/models -- public list of rewrite models for the UI's picker. no auth: it's just
// key/label/note, and the raw model id is harmless to expose too.
function handleModels(request) {
  if (request.method !== 'GET') {
    return new Response(JSON.stringify({ error: 'method' }), {
      status: 405,
      headers: { 'content-type': 'application/json' },
    })
  }
  const body = {
    default: DEFAULT_MODEL_KEY,
    models: MODELS.map(({ key, id, label, note }) => ({ key, id, label, note })),
  }
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json', 'Cache-Control': 'max-age=300' },
  })
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    if (!url.pathname.startsWith('/api/')) return env.ASSETS.fetch(request)

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(request) })
    }

    let response
    if (url.pathname === '/api/rewrite') {
      response = await handleRewrite(request, env)
    } else if (url.pathname === '/api/models') {
      response = handleModels(request)
    } else {
      response = new Response(JSON.stringify({ error: 'not_found' }), {
        status: 404,
        headers: { 'content-type': 'application/json' },
      })
    }
    return withCors(response, request)
  },
}
