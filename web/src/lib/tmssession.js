// TMS session kept in localStorage: token, display name, expiry. NEVER the password -- an
// expired session just means "log in again" (no silent re-login on the web).
const KEY = 'tmsSession'

const nowSecondsDefault = () => Math.floor(Date.now() / 1000)

// the stored session, or null when there is none, it is unreadable, or it has expired
export function loadTmsSession(nowSeconds = nowSecondsDefault()) {
  let session
  try {
    session = JSON.parse(localStorage.getItem(KEY))
  } catch (e) {
    session = null
  }
  if (!session?.token || !(session.expiresAt > nowSeconds)) return null
  return session
}

export function saveTmsSession({ token, displayName, expiresAt }) {
  localStorage.setItem(KEY, JSON.stringify({ token, displayName, expiresAt }))
}

// logout, expiry, or a 401 from TMS
export function clearTmsSession() {
  localStorage.removeItem(KEY)
}
