// tiny hash router -- app has ~9 flat routes, no nesting/guards needed beyond "logged in?"
// which App.svelte already checks. skipped: a router library, add if routes grow nested.
import { writable } from 'svelte/store'

function currentHash() {
  return location.hash.slice(1) || '/'
}

export const route = writable(currentHash())

window.addEventListener('hashchange', () => route.set(currentHash()))

export function nav(path) {
  location.hash = path
}
