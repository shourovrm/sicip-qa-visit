// three-way merge of a report's data JSON -- stops a stale copy on one platform wiping what was
// written on the other (2026-09-30: phone pushed its whole old blob over an officer's web edits).
// base = the server copy this client last saw, local = this client's current data, server = the
// row on the server right now. Per value: changed only locally -> local, changed only on the
// server -> server, changed on both -> recurse into objects / cards-style arrays (items matched
// by `_id`), else local wins. base unknown (null) -> local wins except a blank local value never
// hides a filled server one. 1:1 port of android domain/report/ReportMerge.kt (fixtures/merge-1.json).

const isObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v)
const isBlankValue = (v) => v === undefined || v === null || (typeof v === 'string' && v.trim() === '')
const idOf = (v) => (isObject(v) && typeof v._id === 'string' ? v._id : null)
const isCardList = (arr) => arr.every((v) => idOf(v) !== null)

// structural equality with key order ignored (jsonb reorders keys)
function same(a, b) {
  if (a === b) return true
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v, i) => same(v, b[i]))
  if (isObject(a) && isObject(b)) {
    const keys = Object.keys(a)
    return keys.length === Object.keys(b).length && keys.every((k) => k in b && same(a[k], b[k]))
  }
  return false
}

// undefined result = absent (key dropped)
function mergeValue(base, local, server, baseKnown) {
  if (same(local, server)) return local
  if (baseKnown && same(local, base)) return server
  if (baseKnown && same(server, base)) return local
  if (isObject(local) && isObject(server)) {
    const baseObject = isObject(base) ? base : null
    const merged = {}
    for (const key of new Set([...Object.keys(local), ...Object.keys(server)])) {
      const value = mergeValue(baseObject?.[key], local[key], server[key], baseKnown && baseObject !== null)
      if (value !== undefined) merged[key] = value
    }
    return merged
  }
  if (Array.isArray(local) && Array.isArray(server) && isCardList(local) && isCardList(server)) {
    return mergeCards(Array.isArray(base) ? base : null, local, server, baseKnown && Array.isArray(base))
  }
  if (!baseKnown && isBlankValue(local) && !isBlankValue(server)) return server
  return local
}

// cards: local order, then server-only cards. A card missing on one side is a delete only when
// the other side left it unchanged since base; otherwise it is kept (never lose an edit).
function mergeCards(base, local, server, baseKnown) {
  const baseById = new Map((base ?? []).map((c) => [idOf(c), c]))
  const serverById = new Map(server.map((c) => [idOf(c), c]))
  const localIds = new Set(local.map(idOf))
  const out = []
  for (const card of local) {
    const serverCard = serverById.get(idOf(card))
    const baseCard = baseById.get(idOf(card))
    if (serverCard === undefined) {
      if (!(baseKnown && baseCard !== undefined && same(baseCard, card))) out.push(card) // else deleted on server
      continue
    }
    const merged = mergeValue(baseCard, card, serverCard, baseKnown && baseCard !== undefined)
    if (merged !== undefined) out.push(merged)
  }
  for (const card of server) {
    if (localIds.has(idOf(card))) continue
    const baseCard = baseById.get(idOf(card))
    if (baseKnown && baseCard !== undefined && same(baseCard, card)) continue // deleted here
    out.push(card)
  }
  return out
}

export function mergeReportData(base, local, server) {
  const merged = mergeValue(base ?? undefined, local, server, base != null)
  return isObject(merged) ? merged : local
}
