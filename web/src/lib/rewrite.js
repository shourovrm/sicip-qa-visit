// calls the Worker's POST /api/rewrite to improve one piece of report text ("Improve wording").
// Pure fetch, no UI here -- ImproveWording.svelte owns loading state / display, and never
// auto-replaces anything with the result.
import { supabase } from './supabase.js'

// short, user-facing text for every error code the Worker can return, plus a local "network"
// code for a fetch failure (offline) -- never show a raw error code to the officer.
const ERROR_MESSAGES = {
  auth: 'Session expired — sign in again',
  too_long: 'Too long to improve (max 1500 characters)',
  quota: 'Daily limit reached — try again tomorrow',
  ai: 'Could not improve wording — try again',
  network: 'You are offline',
}

function messageFor(code) {
  // bad_request and anything unrecognised fall back to the generic "ai" message -- the officer
  // can't act differently on those anyway.
  return ERROR_MESSAGES[code] ?? ERROR_MESSAGES.ai
}

// text = the field's current value, label = the field's own label (e.g. "Key findings"), sent
// so the Worker's prompt can add a little context. Returns the rewritten string, or throws an
// Error whose message is already the short user-facing text.
export async function rewriteText(text, label) {
  const {
    data: { session },
  } = await supabase.auth.getSession()
  if (!session) throw new Error(messageFor('auth'))

  let response
  try {
    response = await fetch('/api/rewrite', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.access_token}`,
      },
      body: JSON.stringify({ text, label }),
    })
  } catch (e) {
    throw new Error(messageFor('network')) // fetch only rejects on a network-level failure
  }

  let body
  try {
    body = await response.json()
  } catch (e) {
    throw new Error(messageFor('ai')) // malformed/empty body -- treat like any other server error
  }

  if (!response.ok) throw new Error(messageFor(body.error))
  return body.text
}
