// officer directory, loaded once a session exists and shared (name lookups, filter dropdowns,
// team pages). refetches on login (session goes null -> set) since RLS requires auth.
import { writable } from 'svelte/store'
import { listOfficers } from './db.js'
import { session } from './auth.js'

export const officers = writable([])

let loaded = false
session.subscribe((s) => {
  if (s && !loaded) {
    loaded = true
    listOfficers().then((rows) => officers.set(rows)).catch(() => { loaded = false })
  }
  if (!s) { loaded = false; officers.set([]) }
})

export function officerName(id, list) {
  return list.find((o) => o.id === id)?.name ?? '—'
}
