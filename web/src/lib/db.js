// data access -- thin wrappers over supabase-js. RLS (read-all / write-own, admin bypass)
// does the real access control; these just shape queries. soft delete = set deleted=true.
import { supabase } from './supabase.js'

const notDeleted = (q) => q.eq('deleted', false)

export async function listOfficers() {
  const { data, error } = await supabase.from('officers').select('*').order('name')
  if (error) throw error
  return data
}

// admin-only (RLS officers_admin): name/role/active. auth-user delete/reset stays out of the
// browser (needs the service key) -- see tools/ scripts.
export async function updateOfficer(id, patch) {
  const { data, error } = await supabase.from('officers').update(patch).eq('id', id).select().single()
  if (error) throw error
  return data
}

// ---- visits ----
export async function listVisits() {
  const { data, error } = await notDeleted(supabase.from('visits').select('*')).order('start_date', { ascending: false })
  if (error) throw error
  return data
}

export async function createVisit(row) {
  const { data, error } = await supabase.from('visits').insert(row).select().single()
  if (error) throw error
  return data
}

export async function updateVisit(id, patch) {
  const { data, error } = await supabase.from('visits').update(patch).eq('id', id).select().single()
  if (error) throw error
  return data
}

export async function softDeleteVisit(id) {
  return updateVisit(id, { deleted: true })
}

// ---- trips ----
export async function listTrips() {
  const { data, error } = await notDeleted(supabase.from('trips').select('*')).order('started_at', { ascending: false })
  if (error) throw error
  return data
}

export async function updateTrip(id, patch) {
  const { data, error } = await supabase.from('trips').update(patch).eq('id', id).select().single()
  if (error) throw error
  return data
}

// ---- travel legs ----
export async function listLegsForTrips(tripIds) {
  if (!tripIds.length) return []
  const { data, error } = await notDeleted(supabase.from('travel_legs').select('*').in('trip_id', tripIds)).order('dep_date').order('dep_time')
  if (error) throw error
  return data
}

export async function createLeg(row) {
  const { data, error } = await supabase.from('travel_legs').insert(row).select().single()
  if (error) throw error
  return data
}

export async function updateLeg(id, patch) {
  const { data, error } = await supabase.from('travel_legs').update(patch).eq('id', id).select().single()
  if (error) throw error
  return data
}

export async function softDeleteLeg(id) {
  return updateLeg(id, { deleted: true })
}

// distinct dep/arr places across every synced travel leg (not just the caller's own trips) --
// global autosuggest source, same reasoning as android's TravelLegDao.distinctPlaces().
export async function listTravelPlaces() {
  const { data, error } = await notDeleted(supabase.from('travel_legs').select('dep_place, arr_place'))
  if (error) throw error
  return [...new Set(data.flatMap((r) => [r.dep_place, r.arr_place]))].filter(Boolean).sort()
}

// ---- leaves ----
export async function listLeaves() {
  const { data, error } = await notDeleted(supabase.from('leaves').select('*')).order('start_date', { ascending: false })
  if (error) throw error
  return data
}

export async function createLeave(row) {
  const { data, error } = await supabase.from('leaves').insert(row).select().single()
  if (error) throw error
  return data
}

export async function updateLeave(id, patch) {
  const { data, error } = await supabase.from('leaves').update(patch).eq('id', id).select().single()
  if (error) throw error
  return data
}

export async function cancelLeave(id) {
  return updateLeave(id, { status: 'cancelled' })
}

// scheduled -> started -> completed lifecycle (see migration 007).
export async function updateLeaveStatus(id, status) {
  return updateLeave(id, { status })
}

export async function softDeleteLeave(id) {
  return updateLeave(id, { deleted: true })
}

// ---- bills ----
export async function listBills(officerId) {
  const { data, error } = await notDeleted(supabase.from('bills').select('*').eq('officer_id', officerId)).order('bill_date', { ascending: false })
  if (error) throw error
  return data
}

export async function createBill(row) {
  const { data, error } = await supabase.from('bills').insert(row).select().single()
  if (error) throw error
  return data
}

// ---- app_meta ----
export async function getAppMeta() {
  const { data, error } = await supabase.from('app_meta').select('*')
  if (error) throw error
  return Object.fromEntries(data.map((r) => [r.key, r.value]))
}

export async function setAppMeta(key, value) {
  const { error } = await supabase.from('app_meta').update({ value }).eq('key', key)
  if (error) throw error
}
