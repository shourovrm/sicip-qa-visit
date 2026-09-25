// index -- router. every request either hits /api/*, or falls through to static assets.
import { handleRewrite } from './rewrite.js'

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
    } else {
      response = new Response(JSON.stringify({ error: 'not_found' }), {
        status: 404,
        headers: { 'content-type': 'application/json' },
      })
    }
    return withCors(response, request)
  },
}
