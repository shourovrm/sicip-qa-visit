// POST auth/login straight from the browser (TMS sends CORS *; the password never touches our
// server). Body shape copied from the official TMS web frontend: JSON with user_agent + the
// public IP -- a form body with an empty user_ip is rejected with message:{}.
import { TMS_BASE_URL, unwrapTmsResponse } from './tmsresponse.js'

const IP_URL = 'https://api.ipify.org?format=json'
const FALLBACK_LIFETIME_SECONDS = 12 * 3600 // unreadable exp: assume half the 24 h token life

// public IP for the login body; '' when the lookup fails (TMS may still accept it)
export async function publicIp(fetchImpl = fetch) {
  try {
    const response = await fetchImpl(IP_URL)
    const body = await response.json()
    return String(body?.ip ?? '')
  } catch (e) {
    return ''
  }
}

// `exp` (epoch seconds) from a JWT payload, unverified -- TMS is the verifier. null if unreadable.
export function jwtExpiry(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const exp = JSON.parse(atob(payload)).exp
    return Number.isFinite(exp) ? exp : null
  } catch (e) {
    return null
  }
}

// live TMS: user_info.employee.name; older keys are the fallback, then the username typed
function displayNameOf(data, username) {
  const info = data?.user_info ?? {}
  const candidates = [info.employee?.name, info.username, info.full_name, info.name, info.user_name]
  const found = candidates.find((value) => typeof value === 'string' && value.trim())
  return found ? found.trim() : username
}

// -> {token, displayName, expiresAt (epoch seconds)}; throws Error(TMS message) on refusal
export async function loginToTms(username, password, { fetchImpl = fetch, userAgent = navigator.userAgent, nowSeconds = () => Math.floor(Date.now() / 1000) } = {}) {
  const body = { username, password, user_agent: userAgent, user_ip: await publicIp(fetchImpl) }
  let response
  try {
    response = await fetchImpl(TMS_BASE_URL + 'auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
    })
  } catch (e) {
    throw new Error('Could not reach TMS. Check your connection.')
  }
  const data = unwrapTmsResponse(response.status, await response.text(), 'TMS rejected the username or password')
  if (typeof data.token !== 'string' || !data.token) throw new Error('TMS sent no token')
  return {
    token: data.token,
    displayName: displayNameOf(data, username),
    expiresAt: jwtExpiry(data.token) ?? nowSeconds() + FALLBACK_LIFETIME_SECONDS,
  }
}
