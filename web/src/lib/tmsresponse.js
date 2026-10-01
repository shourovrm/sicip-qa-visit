// TMS API envelope {status, data, message}: one job -- (http status, body text) -> `data`,
// or an Error carrying TMS's own message. `message` may be a string, an object of field
// errors ({username:["..."]}) or an empty object.

export const TMS_BASE_URL = 'https://bee.sicip.gov.bd/api/'

// every string inside a TMS `message`, in order; [] when it holds none
function messageStrings(message) {
  if (message == null) return []
  if (typeof message === 'string') return message.trim() ? [message.trim()] : []
  if (Array.isArray(message)) return message.flatMap(messageStrings)
  if (typeof message === 'object') return Object.values(message).flatMap(messageStrings)
  return [String(message)]
}

export function tmsMessageText(message, fallback) {
  const parts = messageStrings(message)
  return parts.length ? parts.join(' ') : fallback
}

export function unwrapTmsResponse(httpStatus, bodyText, fallbackMessage = 'TMS returned an error') {
  if (httpStatus >= 500) throw new Error(`TMS server error ${httpStatus}, try again later`)
  let envelope = null
  try {
    envelope = JSON.parse(bodyText)
  } catch (e) {
    envelope = null
  }
  if (!envelope || typeof envelope !== 'object') throw new Error(`TMS sent an unexpected reply (HTTP ${httpStatus})`)
  if (envelope.status === 'success' && envelope.data != null) return envelope.data
  throw new Error(tmsMessageText(envelope.message, fallbackMessage))
}
