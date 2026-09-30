// TMS API-change alerts (tms_errors table, admin-only). every failure here is silent: a missing
// table or RLS error just means "no banner".
import { supabase } from './supabase.js'

// rows = unresolved tms_errors. groups = one per endpoint+kind with a row count.
// n = number of groups ("error kinds"). null when nothing is unresolved.
export function summarise(rows) {
  if (!rows.length) return null
  const groups = new Map()
  for (const row of rows) {
    const key = `${row.endpoint}|${row.kind}`
    const group = groups.get(key)
    if (group) group.count += 1
    else groups.set(key, { endpoint: row.endpoint, kind: row.kind, count: 1 })
  }
  const latest = rows.reduce((a, b) => (b.created_at > a.created_at ? b : a))
  const firstDay = rows.reduce((min, r) => (r.day < min ? r.day : min), rows[0].day)
  return { n: groups.size, firstDay, latest, groups: [...groups.values()] }
}

export async function listUnresolvedTmsErrors() {
  const { data, error } = await supabase
    .from('tms_errors').select('*').is('resolved_at', null).order('day', { ascending: false })
  if (error) throw error
  return data
}

export async function resolveTmsErrors(ids) {
  const { error } = await supabase
    .from('tms_errors').update({ resolved_at: new Date().toISOString() }).in('id', ids)
  if (error) throw error
}
