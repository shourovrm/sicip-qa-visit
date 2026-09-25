// per-device "Writing helper model" choice for the Improve wording rewrite worker.
// Persisted to localStorage; an absent key means "use whatever the server defaults to" -- the
// Worker owns that default, so a missing/unknown key here is never an error, just "no opinion".
const KEY = 'rewriteModel'

// the saved model key, or null when the officer hasn't chosen one (server picks the default).
export function getModel() {
  return localStorage.getItem(KEY) || null
}

// key = null/'' clears the choice back to "server default".
export function setModel(key) {
  if (key) localStorage.setItem(KEY, key)
  else localStorage.removeItem(KEY)
}

// fetches the Worker's model list -- {default, models: [{key,id,label,note}]} -- or null on any
// failure (offline, or a dev server with no /api route) so the caller can show a muted
// "unavailable" message while leaving whatever choice is already saved untouched.
export async function loadModels() {
  let response
  try {
    response = await fetch('/api/models')
  } catch (e) {
    return null // fetch only rejects on a network-level failure, same convention as rewrite.js
  }
  if (!response.ok) return null

  try {
    return await response.json()
  } catch (e) {
    return null // malformed/empty body -- treat like any other failure to load
  }
}
