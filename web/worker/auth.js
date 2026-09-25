// auth -- bearer token check against supabase, one job only.

// validates a supabase access token by asking gotrue who it belongs to.
// returns true/false; never throws (network/parse errors count as invalid).
export async function isValidToken(token, env) {
  if (!token) return false
  try {
    const res = await fetch(`${env.SUPABASE_URL}/auth/v1/user`, {
      headers: {
        Authorization: `Bearer ${token}`,
        apikey: env.SUPABASE_KEY,
      },
    })
    return res.ok
  } catch {
    return false
  }
}

// pulls "Bearer <token>" out of the Authorization header, or null.
export function bearerToken(request) {
  const header = request.headers.get('Authorization') || ''
  const match = header.match(/^Bearer (.+)$/)
  return match ? match[1] : null
}
