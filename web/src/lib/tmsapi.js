// TMS GET from the browser (TMS sends CORS *): Bearer token from the stored session. No session
// or an expired one -> TmsSignedOutError; a 401 also clears the session so every TMS card shows
// the signed-out state. Replies go through tmsresponse.js's {status,data,message} unwrap.
import { TMS_BASE_URL, unwrapTmsResponse } from './tmsresponse.js'
import { refreshTmsSession, signOutTms } from './tmsstore.js'

export class TmsSignedOutError extends Error {
  constructor(message = 'Signed out of TMS. Sign in again on the Profile page.') {
    super(message)
    this.name = 'TmsSignedOutError'
  }
}

export async function tmsGet(path, { fetchImpl = fetch, currentSession = refreshTmsSession, signOut = signOutTms } = {}) {
  const session = currentSession()
  if (!session) throw new TmsSignedOutError()
  let response
  try {
    response = await fetchImpl(TMS_BASE_URL + path, {
      headers: { Accept: 'application/json', Authorization: `Bearer ${session.token}` },
    })
  } catch (e) {
    throw new Error('Could not reach TMS. Check your connection.')
  }
  if (response.status === 401) {
    signOut()
    throw new TmsSignedOutError()
  }
  return unwrapTmsResponse(response.status, await response.text(), `TMS could not answer ${path.split('?')[0]}`)
}

// a list endpoint: always an array
export async function tmsList(path, options) {
  const data = await tmsGet(path, options)
  return Array.isArray(data) ? data : []
}
