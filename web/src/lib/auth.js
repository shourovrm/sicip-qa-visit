// session + officer profile (name/role) stores. supabase client auto-persists/refreshes session.
import { writable, derived } from 'svelte/store'
import { supabase } from './supabase.js'

export const session = writable(undefined) // undefined = loading, null = logged out
export const officer = writable(null) // officers row for the current user (has .role)
export const isAdmin = derived(officer, (o) => o?.role === 'admin')

async function loadOfficer(userId) {
  if (!userId) { officer.set(null); return }
  const { data } = await supabase.from('officers').select('*').eq('id', userId).single()
  officer.set(data ?? null)
}

supabase.auth.getSession().then(({ data }) => {
  session.set(data.session)
  loadOfficer(data.session?.user?.id)
})

supabase.auth.onAuthStateChange((_event, sess) => {
  session.set(sess)
  loadOfficer(sess?.user?.id)
})

export async function signIn(email, password) {
  const { error } = await supabase.auth.signInWithPassword({ email, password })
  return error
}

export async function signOut() {
  await supabase.auth.signOut()
}

export async function requestPasswordReset(email) {
  const { error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: `${location.origin}/reset`,
  })
  return error
}

export async function updatePassword(newPassword) {
  const { error } = await supabase.auth.updateUser({ password: newPassword })
  return error
}
