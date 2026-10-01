// the signed-in TMS session as a Svelte store, so the Profile card, the report editor and the
// new-report link step all see a login, a logout and a 401 at once (tmssession.js persists it)
import { writable } from 'svelte/store'
import { clearTmsSession, loadTmsSession, saveTmsSession } from './tmssession.js'

export const tmsSession = writable(loadTmsSession())

export function signInTms(session) {
  saveTmsSession(session)
  tmsSession.set(loadTmsSession())
}

export function signOutTms() {
  clearTmsSession()
  tmsSession.set(null)
}

// re-read storage: drops an expired token from the store
export function refreshTmsSession() {
  const current = loadTmsSession()
  tmsSession.set(current)
  return current
}
